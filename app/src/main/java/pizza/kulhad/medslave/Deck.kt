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

/* The imported deck lives in the app's own files: deck.db (SQLite) and media/ (one file per image). Only one deck at a time.
   deck.db: info(k, v) holds meta, paths, pc and subject names as JSON; notes(id, k, p, ords, cids, flds, tags, subj, op) one row per
   note, fields as a JSON array. Notes are loaded without their fields; a card's fields are read when it's drawn. */

/** a note: n[0] id, n[1] kind b/c/o, n[2] path index, n[3] card ordinals, n[6] Anki card ids (fields and tags stay in the db) */
class Note(val id: Long, val k: Char, val p: Int, val ords: IntArray, val cids: LongArray)
class Subject(val name: String, val notes: List<Note>)
class Meta(val name: String, val notes: Int, val cards: Int, val media: Int, val bytes: Long, val imported: String) {
    fun json(): String = JSONObject().put("name", name).put("notes", notes).put("cards", cards).put("media", media).put("bytes", bytes).put("imported", imported).toString()
    companion object {
        fun of(s: String?) = s?.let { runCatching { JSONObject(it).let { o -> Meta(o.optString("name"), o.optInt("notes"), o.optInt("cards"), o.optInt("media"), o.optLong("bytes"), o.optString("imported")) } }.getOrNull() }
    }
}
/** a deck tree node (deck.js init): name, path segments, path string, kids, card count */
class Node(val id: Int, val name: String, val segs: List<String>) {
    val ps: String = segs.joinToString(" › ")
    val kids = mutableListOf<Node>()
    var cnt = 0
}

class Deck(val paths: List<String>, val pc: IntArray, val subjects: List<Subject>, val meta: Meta?) {
    val empty get() = subjects.isEmpty()
    val nodes = mutableListOf<Node>()
    val top = mutableListOf<Node>()

    init {
        val pm = paths.map { p ->
            val sg = p.split(" › "); var lst = top
            sg.indices.map { i ->
                var n = lst.find { it.name == sg[i] }
                if (n == null) { n = Node(nodes.size, sg[i], sg.subList(0, i + 1)); nodes.add(n); lst.add(n) }
                lst = n.kids; n
            }
        }
        pc.forEachIndexed { pi, c -> pm[pi].forEach { it.cnt += c } }
        fun srt(l: MutableList<Node>) { l.sortWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }; l.forEach { srt(it.kids) } }
        top.forEach { srt(it.kids) }
    }

    /** the notes under a node: its own path and every path below it */
    fun notesFor(n: Node): List<Note> {
        val s = subjects.find { it.name == n.segs[0] } ?: return emptyList()
        val ok = BooleanArray(paths.size) { paths[it] == n.ps || paths[it].startsWith(n.ps + " › ") }
        return s.notes.filter { ok[it.p] }
    }

    companion object {
        val EMPTY = Deck(emptyList(), IntArray(0), emptyList(), null)
    }
}

object DeckStore {
    fun dbFile(ctx: Context) = File(ctx.filesDir, "deck.db")
    fun mediaDir(ctx: Context) = File(ctx.filesDir, "media")

    /** an image's file: its name URL-encoded, or a hash when that's too long for a file name */
    fun mediaName(key: String): String {
        val e = URLEncoder.encode(key, "UTF-8")
        if (e.length <= 160) return e
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return h + "." + key.substringAfterLast('.', "bin").take(5)
    }

    fun create(f: File): SQLiteDatabase {
        f.delete(); File(f.path + "-journal").delete()
        val db = SQLiteDatabase.openOrCreateDatabase(f, null)
        db.execSQL("CREATE TABLE info(k TEXT PRIMARY KEY, v TEXT)")
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, k TEXT, p INTEGER, ords TEXT, cids TEXT, flds TEXT, tags TEXT, subj INTEGER, op INTEGER)")
        return db
    }

    private var db: SQLiteDatabase? = null
    private fun open(ctx: Context): SQLiteDatabase? {
        db?.let { if (it.isOpen) return it }
        val f = dbFile(ctx); if (!f.exists()) return null
        return runCatching { SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS) }.getOrNull().also { db = it }
    }
    fun close() { db?.close(); db = null }

    fun load(ctx: Context): Deck {
        val d = open(ctx) ?: return Deck.EMPTY
        return try {
            val info = HashMap<String, String>()
            d.rawQuery("SELECT k, v FROM info", null).use { c -> while (c.moveToNext()) info[c.getString(0)] = c.getString(1) }
            val paths = JSONArray(info["paths"] ?: "[]").let { a -> List(a.length()) { a.getString(it) } }
            val pc = JSONArray(info["pc"] ?: "[]").let { a -> IntArray(a.length()) { a.getInt(it) } }
            val names = JSONArray(info["subjects"] ?: "[]").let { a -> List(a.length()) { a.getString(it) } }
            val by = List(names.size) { ArrayList<Note>() }
            d.rawQuery("SELECT id, k, p, ords, cids, subj FROM notes ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    val n = Note(c.getLong(0), c.getString(1)[0], c.getInt(2), ints(c.getString(3)), longs(c.getString(4)))
                    by.getOrNull(c.getInt(5))?.add(n)
                }
            }
            Deck(paths, pc, names.mapIndexed { i, s -> Subject(s, by[i]) }, Meta.of(info["meta"]))
        } catch (e: Exception) { Deck.EMPTY }
    }

    /** the note as the card page wants it: [id, kind, path, ords, fields, tags, cids] */
    fun noteJson(ctx: Context, n: Note): String {
        var f = "[]"; var t = "[]"
        open(ctx)?.let { d -> bigQuery(d, "SELECT flds, tags FROM notes WHERE id=?", arrayOf(n.id.toString())).use { c -> if (c.moveToFirst()) { f = c.getString(0); t = c.getString(1) } } }
        return "[${n.id},\"${n.k}\",${n.p},[${n.ords.joinToString(",")}],$f,$t,[${n.cids.joinToString(",")}]]"
    }

    fun delete(ctx: Context) { close(); dbFile(ctx).delete(); File(dbFile(ctx).path + "-journal").delete(); mediaDir(ctx).deleteRecursively() }

    fun bytes(ctx: Context): Long = dbFile(ctx).length() + (mediaDir(ctx).listFiles()?.sumOf { it.length() } ?: 0L)

    fun ints(s: String) = if (s.isEmpty()) IntArray(0) else s.split(',').map { it.toInt() }.toIntArray()
    fun longs(s: String) = if (s.isEmpty()) LongArray(0) else s.split(',').map { it.toLong() }.toLongArray()
}

/** a query whose rows may be bigger than Android's 2 MB cursor window (fields with inline images, big note type JSON) */
fun bigQuery(db: SQLiteDatabase, sql: String, args: Array<String>? = null): Cursor {
    val c = db.rawQuery(sql, args)
    if (Build.VERSION.SDK_INT >= 28 && c is SQLiteCursor) c.setWindow(CursorWindow("big", 20L * 1024 * 1024))
    return c
}
