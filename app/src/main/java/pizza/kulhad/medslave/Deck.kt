package pizza.kulhad.medslave

import android.content.Context
import android.database.Cursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/* Imported decks live in the app's own files, one folder each: decks/<id>/deck.db (SQLite) and decks/<id>/media/ (one file per image);
   <id> is the import time in ms, so the folders sort in import order. The app works on all of them merged (Deck), with each deck's
   own tree kept as a DeckPart (the Menu's pages, Credits' pages, Settings › Decks).
   deck.db: info(k, v) holds meta, paths, pc and subject names as JSON; notes(id, k, p, ords, cids, flds, tags, subj, op) one row per
   note, fields as a JSON array. Notes are loaded without their fields; a card's fields are read when it's drawn. */

/** a note: n[0] id, n[1] kind b/c/o, n[2] path index, n[3] card ordinals, n[6] Anki card ids (fields and tags stay in the db) */
class Note(val id: Long, val k: Char, val p: Int, val ords: IntArray, val cids: LongArray, val src: Int = 0) { var mcq = false }   // src: which deck (its DeckPart index)   // mcq: an imported MCQ (its options in the fields)
class Subject(val name: String, val notes: List<Note>)
class Meta(val name: String, val notes: Int, val cards: Int, val media: Int, val bytes: Long, val imported: String) {
    fun json(): String = JSONObject().put("name", name).put("notes", notes).put("cards", cards).put("media", media).put("bytes", bytes).put("imported", imported).toString()
    companion object {
        fun of(s: String?) = s?.let { runCatching { JSONObject(it).let { o -> Meta(o.optString("name"), o.optInt("notes"), o.optInt("cards"), o.optInt("media"), o.optLong("bytes"), o.optString("imported")) } }.getOrNull() }
    }
}
/** a deck tree node (deck.js init): name, path segments, path string, kids, card count. src: the deck it belongs to (-1: every deck) */
class Node(val id: Int, val name: String, val segs: List<String>, all: Boolean = false, val src: Int = -1, pre: String = "") {
    /** the deck's key for saved progress; "*" for All decks (no real path is "*"). one deck's nodes have its id in front ("<id>/…") */
    val ps: String = pre + if (all) "*" else segs.joinToString(" › ")
    val kids = mutableListOf<Node>()
    var cnt = 0
}

/** one imported deck: its folder id, meta, size on disk, and its own tree (subjects without notes; its nodes study only its cards).
    name: its parent deck's (the main deck the importer dropped), else the file's */
class DeckPart(val id: String, val meta: Meta?, val bytes: Long, val tree: Deck, root: String? = null) { val name = root?.ifBlank { null } ?: meta?.name?.ifBlank { null } ?: "Deck" }

class Deck(val paths: List<String>, val pc: IntArray, val subjects: List<Subject>, val meta: Meta?, val parts: List<DeckPart> = emptyList(), src: Int = -1, pre: String = "") {
    val empty get() = subjects.isEmpty()
    val hasMcq by lazy { subjects.any { s -> s.notes.any { it.mcq } } }
    val nodes = mutableListOf<Node>()
    val top = mutableListOf<Node>()

    init {
        val pm = paths.map { p ->
            val sg = p.split(" › "); var lst = top
            sg.indices.map { i ->
                var n = lst.find { it.name == sg[i] }
                if (n == null) { n = Node(nodes.size, sg[i], sg.subList(0, i + 1), src = src, pre = pre); nodes.add(n); lst.add(n) }
                lst = n.kids; n
            }
        }
        pc.forEachIndexed { pi, c -> pm[pi].forEach { it.cnt += c } }
        fun srt(l: MutableList<Node>) { l.sortWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }; l.forEach { srt(it.kids) } }
        top.forEach { srt(it.kids) }
    }

    /** the notes under a node: its own path and every path below it */
    /** All decks: the first row on the Menu, every card in the deck */
    val all = Node(-1, "All decks", emptyList(), all = true, src = src, pre = pre)

    /** the notes under a node (of this deck or of one of its parts): its own path and every path below it, only its deck's when it has one */
    fun notesFor(n: Node): List<Note> {
        val mine = { x: Note -> n.src < 0 || x.src == n.src }
        if (n.segs.isEmpty()) return subjects.flatMap { it.notes }.filter(mine)
        val s = subjects.find { it.name == n.segs[0] } ?: return emptyList()
        val ps = n.segs.joinToString(" › ")
        val ok = BooleanArray(paths.size) { paths[it] == ps || paths[it].startsWith("$ps › ") }
        return s.notes.filter { ok[it.p] && mine(it) }
    }
    /** a node by its progress key, in this deck or any of its parts */
    fun nodeOf(ps: String): Node? = if (ps == all.ps) all else nodes.find { it.ps == ps } ?: parts.firstNotNullOfOrNull { p -> p.tree.nodeOf(ps) }

    companion object {
        val EMPTY = Deck(emptyList(), IntArray(0), emptyList(), null)
    }
}

object DeckStore {
    fun root(ctx: Context) = File(ctx.filesDir, "decks")
    /** the deck folders, oldest first */
    fun dirs(ctx: Context): List<File> = root(ctx).listFiles()?.filter { File(it, "deck.db").exists() }?.sortedBy { it.name.toLongOrNull() ?: Long.MAX_VALUE }.orEmpty()
    /** a new deck's folder */
    fun newDir(ctx: Context): File = File(root(ctx), System.currentTimeMillis().toString())

    /** the one deck from before there could be several (deck.db and media/ in filesDir) moves into its own folder; returns its id */
    fun migrate(ctx: Context): String? {
        val old = File(ctx.filesDir, "deck.db"); if (!old.exists()) return null
        val d = File(root(ctx), old.lastModified().toString()); d.mkdirs()
        if (!old.renameTo(File(d, "deck.db"))) return null
        File(ctx.filesDir, "media").let { if (it.exists()) it.renameTo(File(d, "media")) }
        File(old.path + "-journal").delete()
        return d.name
    }

    /** an image's file: its name URL-encoded, or a hash when that's too long for a file name */
    fun mediaName(key: String): String {
        val e = URLEncoder.encode(key, "UTF-8")
        if (e.length <= 160) return e
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return h + "." + key.substringAfterLast('.', "bin").take(5)
    }
    /** an image by its name, from whichever deck has it */
    fun mediaFile(key: String): File? { val n = mediaName(key); return srcs.firstNotNullOfOrNull { d -> File(d, "media/$n").takeIf { it.exists() } } }

    fun create(f: File): SQLiteDatabase {
        f.delete(); File(f.path + "-journal").delete()
        val db = SQLiteDatabase.openOrCreateDatabase(f, null)
        db.execSQL("CREATE TABLE info(k TEXT PRIMARY KEY, v TEXT)")
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, k TEXT, p INTEGER, ords TEXT, cids TEXT, flds TEXT, tags TEXT, subj INTEGER, op INTEGER)")
        return db
    }

    @Volatile private var srcs: List<File> = emptyList()   // the loaded decks' folders, by Note.src
    private val dbs = HashMap<Int, SQLiteDatabase>()
    @Synchronized private fun open(src: Int): SQLiteDatabase? {
        dbs[src]?.let { if (it.isOpen) return it }
        val f = File(srcs.getOrNull(src) ?: return null, "deck.db"); if (!f.exists()) return null
        return runCatching { SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS) }.getOrNull()?.also { dbs[src] = it }
    }
    @Synchronized fun close() { dbs.values.forEach { runCatching { it.close() } }; dbs.clear() }

    /** every deck, merged: paths one after another (each note's p moved along), subjects of the same name together. a note already in
        an earlier deck is skipped */
    fun load(ctx: Context): Deck {
        close()
        val ds = dirs(ctx); srcs = ds
        val paths = ArrayList<String>(); val pc = ArrayList<Int>(); val parts = ArrayList<DeckPart>()
        val bySub = LinkedHashMap<String, ArrayList<Note>>(); val seen = HashSet<Long>()
        ds.forEachIndexed { src, dir ->
            val d = open(src) ?: return@forEachIndexed
            try {
                val info = HashMap<String, String>()
                d.rawQuery("SELECT k, v FROM info", null).use { c -> while (c.moveToNext()) info[c.getString(0)] = c.getString(1) }
                val ps = JSONArray(info["paths"] ?: "[]").let { a -> List(a.length()) { a.getString(it) } }
                val pcs = JSONArray(info["pc"] ?: "[]").let { a -> IntArray(a.length()) { a.getInt(it) } }
                val names = JSONArray(info["subjects"] ?: "[]").let { a -> List(a.length()) { a.getString(it) } }
                val off = paths.size
                val mcq = HashSet<Long>(); d.rawQuery("SELECT id FROM notes WHERE flds LIKE '%mcq-o%' OR flds LIKE '%mt-src%'", null).use { c -> while (c.moveToNext()) mcq.add(c.getLong(0)) }
                d.rawQuery("SELECT id, k, p, ords, cids, subj FROM notes ORDER BY id", null).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0); if (!seen.add(id)) continue
                        val sn = names.getOrNull(c.getInt(5)) ?: continue
                        val n = Note(id, c.getString(1)[0], c.getInt(2) + off, ints(c.getString(3)), longs(c.getString(4)), src)
                        if (id in mcq) n.mcq = true
                        bySub.getOrPut(sn) { ArrayList() }.add(n)
                    }
                }
                paths.addAll(ps); pcs.forEach { pc.add(it) }
                val meta = Meta.of(info["meta"])
                val size = File(dir, "deck.db").length() + (File(dir, "media").listFiles()?.sumOf { it.length() } ?: 0L)
                parts.add(DeckPart(dir.name, meta, size, Deck(ps, pcs, names.map { Subject(it, emptyList()) }, meta, src = src, pre = "${dir.name}/"), info["root"]))
            } catch (e: Exception) { }
        }
        if (parts.isEmpty()) return Deck.EMPTY
        val coll = java.text.Collator.getInstance()
        val subs = bySub.keys.sortedWith { a, b -> coll.compare(a, b) }.map { Subject(it, bySub[it]!!) }
        val ms = parts.mapNotNull { it.meta }
        val meta = if (parts.size == 1) parts[0].meta else Meta("${parts.size} decks", ms.sumOf { it.notes }, ms.sumOf { it.cards }, ms.sumOf { it.media }, ms.sumOf { it.bytes }, ms.joinToString(",") { it.imported })
        return Deck(paths, pc.toIntArray(), subs, meta, parts)
    }

    /** the note as the card page wants it: [id, kind, path, ords, fields, tags, cids] */
    fun noteJson(ctx: Context, n: Note): String {
        var f = "[]"; var t = "[]"
        open(n.src)?.let { d -> bigQuery(d, "SELECT flds, tags FROM notes WHERE id=?", arrayOf(n.id.toString())).use { c -> if (c.moveToFirst()) { f = c.getString(0); t = c.getString(1) } } }
        return "[${n.id},\"${n.k}\",${n.p},[${n.ords.joinToString(",")}],$f,$t,[${n.cids.joinToString(",")}]]"
    }
    /** every note id in a deck folder (an import checks it isn't a deck that's already here) */
    fun ids(ctx: Context): Set<Long> { val out = HashSet<Long>()
        dirs(ctx).forEach { d -> runCatching { SQLiteDatabase.openDatabase(File(d, "deck.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db -> db.rawQuery("SELECT id FROM notes", null).use { c -> while (c.moveToNext()) out.add(c.getLong(0)) } } } }
        return out }

    /** one deck's folder goes */
    fun delete(ctx: Context, id: String) { close(); File(root(ctx), id).deleteRecursively() }

    fun bytes(ctx: Context): Long = root(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun ints(s: String) = if (s.isEmpty()) IntArray(0) else s.split(',').map { it.toInt() }.toIntArray()
    fun longs(s: String) = if (s.isEmpty()) LongArray(0) else s.split(',').map { it.toLong() }.toLongArray()
}

/** a query whose rows may be bigger than Android's 2 MB cursor window (fields with inline images, big note type JSON) */
fun bigQuery(db: SQLiteDatabase, sql: String, args: Array<String>? = null): Cursor {
    val c = db.rawQuery(sql, args)
    if (Build.VERSION.SDK_INT >= 28 && c is SQLiteCursor) c.setWindow(CursorWindow("big", 20L * 1024 * 1024))
    return c
}
