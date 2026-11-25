package pizza.kulhad.medslave

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.github.luben.zstd.ZstdInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.text.Collator
import java.time.Instant
import java.util.zip.ZipFile

/* Anki .apkg → a medslave deck, the same steps as the site's js/apkg.js: scan() reads the package's note types and guesses what each
   becomes (kind + slot plan); the import window lets the person change that; build() writes the deck with those choices.
   The package is copied into the app's cache first (a content URI can't be opened as a zip), then read one entry at a time. */

class ImportType(val id: String, val name: String, val n: Int, val fields: List<String>, val sample: List<String>, val kind: String, val plan: Map<String, IntArray>)
class Choice(val k: String, val m: IntArray)
class ImportError(msg: String) : Exception(msg)

private class NT(val name: String, var names: MutableList<String>, val cloze: Boolean)

/* each card kind's slots, in field order; the import window labels them (* = needed) */
val ISL = mapOf(
    "e" to listOf("Image*", "Question mask*", "Answer mask*", "Header", "Footer", "Remarks", "Sources", "Extra 1", "Extra 2"),
    "b" to listOf("Front*", "Back*", "Extra", "Title", "Source", "Hint"),
    "c" to listOf("Text*", "Extra", "Title", "Source", "Hint", "Left align", "Sequential", "Show all clozes"),
    "o" to listOf("Occlusions*", "Header", "Image*", "Footer"))
val IKS = linkedMapOf("b" to "Basic", "c" to "Cloze", "o" to "Image occlusion", "e" to "Image occlusion (Enhanced add-on)")

private fun rx(s: String) = Regex(s, RegexOption.IGNORE_CASE)
private val SLOT = mapOf(
    "b" to listOf("^(front|question|q)$", "^(back|answer|a)$", "extra|note|explan", "^title|heading", "source|ref", "hint"),
    "c" to listOf("^(text|cloze)", "extra|back", "^title|heading", "source|ref", "hint", "left", "sequen|one.?by.?one", "show.?all|reveal.?all"),
    "o" to listOf("occlusion", "header", "^image", "footer|back.?extra"),
    "e" to listOf("^image$", "question.?mask", "answer.?mask", "^header", "^footer", "remark", "source", "extra.?1", "extra.?2")
).mapValues { (_, l) -> l.map { Regex(it) } }
private val OPTS = rx("^(options?|choices?|mcq.?options?)$") // PYQ MCQs: an unmapped options field stays on the front
private val IMX = rx("\\.(png|jpe?g|gif|webp|svg|avif|bmp|ico)$")
private val CLZ = Regex("\\{\\{c\\d+::")
private val PXF = Regex("\\[\\[rect\\d*::\\s*[\\d.]+,")
private fun isIOE(names: List<String>) = names.any { rx("question.?mask").containsMatchIn(it) } && names.any { rx("answer.?mask").containsMatchIn(it) }

/* shared decks come from anywhere and their fields go into the page as HTML: no scripts, styles, embeds, event handlers, javascript: links */
private val C1 = rx("<(script|style|iframe|object|embed|template)\\b[\\s\\S]*?</\\1\\s*>")
private val C2 = rx("</?(script|style|iframe|object|embed|link|meta|base|form)\\b[^>]*>")
private val C3 = rx("\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")
private val C4 = rx("\\b(href|src|action)\\s*=\\s*([\"']?)\\s*javascript:")
private val SND = Regex("\\[sound:[^\\]]*\\]")
private val IMG = rx("<img\\b([^>]*?)\\bsrc\\s*=\\s*([\"']?)([^\"'\\s>]+)\\2")
private fun clean(s: String) = s.replace(C1, "").replace(C2, "").replace(C3, "").replace(C4) { "${it.groupValues[1]}=${it.groupValues[2]}#" }
/** fields: images become <img data-m="file"> (the card page loads them from the app's media folder), [sound:…] is dropped */
private fun html(s: String, used: MutableSet<String>) = clean(s).replace(SND, "").replace(IMG) { m ->
    val (pre, _, src) = m.destructured
    if (rx("^(https?:|data:|blob:|//)").containsMatchIn(src)) m.value
    else {
        var k = src.replace("&amp;", "&"); k = runCatching { URLDecoder.decode(k.replace("+", "%2B"), "UTF-8") }.getOrDefault(k)
        used.add(k); "<img${pre}data-m=\"${k.replace("\"", "&quot;")}\""
    }
}

/** which field goes in each slot, by name (the main text falls back to the first unused field) */
private fun plan(names: List<String>, k: String): IntArray {
    val l = names.map { it.lowercase().trim() }; val used = HashSet<Int>()
    val m = SLOT[k]!!.map { r -> val i = l.indices.firstOrNull { it !in used && r.containsMatchIn(l[it]) } ?: -1; if (i >= 0) used.add(i); i }.toIntArray()
    fun fill(s: Int) { if (m[s] < 0) { val i = l.indices.firstOrNull { it !in used } ?: -1; if (i >= 0) { m[s] = i; used.add(i) } } }
    when (k) { "b" -> { fill(0); fill(1) }; "c" -> fill(0); "o" -> { fill(0); fill(2) } }
    return m
}

private val ENT = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
/** a field's preview for the import window: plain text, clozes as their answer, images named */
private fun strip(x: String): String {
    if (Regex("image-occlusion:|\\[\\[rect\\d*::").containsMatchIn(x)) return "(occlusion shapes)"
    var t = x.replace(Regex("\\{\\{c\\d+::([\\s\\S]*?)(?:::[\\s\\S]*?)?\\}\\}"), "$1").replace(rx("<img\\b[^>]*>"), " (image) ")
        .replace(rx("<br\\s*/?>|</(div|p|li)>"), " ").replace(Regex("<[^>]*>"), "").replace(SND, " (audio) ")
    t = t.replace(rx("&(#x[0-9a-f]+|#\\d+|[a-z]+);")) { m ->
        val e = m.groupValues[1]
        if (e[0] == '#') runCatching { String(Character.toChars(if (e[1] == 'x' || e[1] == 'X') e.substring(2).toInt(16) else e.substring(1).toInt())) }.getOrDefault(m.value)
        else ENT[e.lowercase()] ?: m.value
    }.replace(Regex("\\s+"), " ").trim()
    return if (t.length > 70) t.substring(0, 69) + "…" else t
}

/** protobuf, just enough for the media list and a notetype's kind: [(field, Long | ByteArray)] */
private fun pb(u: ByteArray): List<Pair<Int, Any>> {
    val o = ArrayList<Pair<Int, Any>>(); var p = 0
    fun v(): Long { var r = 0L; var s = 0; var b: Int; do { b = u[p++].toInt() and 0xff; r = r or ((b and 127).toLong() shl s); s += 7 } while (b and 128 != 0); return r }
    while (p < u.size) {
        val t = v(); val f = (t ushr 3).toInt(); val w = (t and 7).toInt()
        when (w) { 0 -> o.add(f to v()); 2 -> { val l = v().toInt(); o.add(f to u.copyOfRange(p, p + l)); p += l }; 1 -> p += 8; 5 -> p += 4; else -> return o }
    }
    return o
}

class Importer(private val ctx: Context, private val say: (String, Float?) -> Unit) {
    @Volatile var cancelled = false
    var fileName = "deck"; private set
    private val pkg = File(ctx.cacheDir, "import.apkg")
    private val colFile = File(ctx.cacheDir, "import-col.db")
    private var zip: ZipFile? = null
    private var col: SQLiteDatabase? = null
    private val nt = HashMap<Long, NT>()
    private val dk = HashMap<Long, String>()

    private fun check() { if (cancelled) throw InterruptedException() }

    /** a zip entry's bytes as a stream, unzstd'd when it's zstd */
    private fun entry(name: String): InputStream {
        val z = zip!!; val e = z.getEntry(name) ?: throw ImportError("Missing $name in the package.")
        val s = BufferedInputStream(z.getInputStream(e), 1 shl 16); s.mark(8)
        val h = ByteArray(4); var r = 0; while (r < 4) { val x = s.read(h, r, 4 - r); if (x < 0) break; r += x }
        s.reset()
        return if (r == 4 && h[0] == 0x28.toByte() && h[1] == 0xb5.toByte() && h[2] == 0x2f.toByte() && h[3] == 0xfd.toByte()) BufferedInputStream(ZstdInputStream(s), 1 shl 16) else s
    }

    private fun rows(sql: String, args: Array<String>? = null, f: (android.database.Cursor) -> Unit) = bigQuery(col!!, sql, args).use { c -> while (c.moveToNext()) { f(c) } }

    fun scan(uri: Uri): List<ImportType> {
        say("Opening the package…", 0f)
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { fileName = it } }
        if (Regex("\\.colpkg$", RegexOption.IGNORE_CASE).containsMatchIn(fileName))
            throw ImportError("That’s a whole Anki collection (.colpkg). medslave imports single decks: in Anki, use Export › Anki Deck Package (.apkg).")
        (ctx.contentResolver.openInputStream(uri) ?: throw ImportError("Couldn’t open that file.")).use { i -> pkg.outputStream().use { o ->
            val b = ByteArray(1 shl 16); var n: Int; var t = 0L
            while (i.read(b).also { n = it } >= 0) { o.write(b, 0, n); t += n; if (t % (8L shl 20) < n) { check(); say("Copying the package… ${t shr 20} MB", null) } }
        } }
        zip = try { ZipFile(pkg) } catch (e: Exception) { throw ImportError("This isn’t an Anki package (no zip directory found).") }
        val cn = listOf("collection.anki21b", "collection.anki21", "collection.anki2").find { zip!!.getEntry(it) != null }
            ?: throw ImportError("No Anki collection inside this file. Export the deck from Anki as a .apkg.")
        say("Unpacking cards…", 3f)
        entry(cn).use { i -> colFile.outputStream().use { i.copyTo(it, 1 shl 16) } }
        check()
        File(colFile.path + "-wal").delete(); File(colFile.path + "-journal").delete()
        col = SQLiteDatabase.openDatabase(colFile.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        val tabs = HashSet<String>(); rows("SELECT name FROM sqlite_master WHERE type='table'") { tabs.add(it.getString(0)) }
        /* notetypes (name, field names, cloze or not) and deck names: new schema (tables) or old (JSON in col) */
        if ("notetypes" in tabs) {
            rows("SELECT id, name, config FROM notetypes") { c -> nt[c.getLong(0)] = NT(c.getString(1), mutableListOf(), pb(c.getBlob(2) ?: ByteArray(0)).any { it.first == 1 && it.second == 1L }) }
            rows("SELECT ntid, ord, name FROM fields ORDER BY ntid, ord") { c ->
                val t = nt.getOrPut(c.getLong(0)) { NT("Note type", mutableListOf(), false) }; val o = c.getInt(1)
                while (t.names.size <= o) t.names.add(""); t.names[o] = c.getString(2)
            }
            rows("SELECT id, name FROM decks") { c -> dk[c.getLong(0)] = c.getString(1).split('\u001f').joinToString(" › ") }
        } else {
            rows("SELECT models, decks FROM col") { c ->
                val mj = JSONObject(c.getString(0)); val dj = JSONObject(c.getString(1))
                mj.keys().forEach { k -> val m = mj.getJSONObject(k); val fl = m.getJSONArray("flds")
                    val names = (0 until fl.length()).map { fl.getJSONObject(it) }.sortedBy { it.optInt("ord") }.map { it.optString("name") }
                    nt[m.optLong("id", k.toLongOrNull() ?: 0)] = NT(m.optString("name"), names.toMutableList(), m.optInt("type") == 1) }
                dj.keys().forEach { k -> val d = dj.getJSONObject(k); dk[d.optLong("id", k.toLongOrNull() ?: 0)] = d.optString("name").split("::").joinToString(" › ") }
            }
        }
        check()
        val io = HashSet<Long>(); rows("SELECT DISTINCT mid FROM notes WHERE flds LIKE '%image-occlusion:%'") { io.add(it.getLong(0)) }
        /* IO one by one: note type → the field holding its pixel boxes */
        val px = HashMap<Long, Int>()
        rows("SELECT mid, max(flds) FROM notes WHERE flds LIKE '%[[rect%::%' GROUP BY mid") { c -> val i = c.getString(1).split('\u001f').indexOfFirst { PXF.containsMatchIn(it) }; if (i >= 0) px[c.getLong(0)] = i }
        val types = ArrayList<ImportType>()
        rows("SELECT mid, count(*), max(flds) FROM notes GROUP BY mid") { c ->
            val mid = c.getLong(0); val n = c.getInt(1); val f = c.getString(2).split('\u001f')
            val t = nt[mid] ?: NT("Note type $mid", f.indices.map { "Field ${it + 1}" }.toMutableList(), false)
            if (t.names.isEmpty()) t.names = f.indices.map { "Field ${it + 1}" }.toMutableList()
            nt[mid] = t; val names = t.names
            val kind = if (mid in io || mid in px) "o" else if (isIOE(names)) "e" else if (t.cloze || f.any { CLZ.containsMatchIn(it) }) "c" else "b"
            val pl = listOf("b", "c", "o", "e").associateWith { plan(names, it) }
            px[mid]?.let { j -> pl["o"]!!.let { o -> for (s in o.indices) if (s == 0) o[s] = j else if (o[s] == j) o[s] = -1 } }
            /* sample of each field: its first non-empty value among up to 200 notes of that type */
            val sm = arrayOfNulls<String>(names.size)
            bigQuery(col!!, "SELECT flds FROM notes WHERE mid=? ORDER BY id LIMIT 200", arrayOf(mid.toString())).use { s ->
                while (s.moveToNext()) s.getString(0).split('\u001f').forEachIndexed { i, x -> if (i < sm.size && sm[i] == null) strip(x).takeIf { it.isNotEmpty() }?.let { sm[i] = it } }
            }
            types.add(ImportType(mid.toString(), t.name, n, names, sm.map { it ?: "" }, kind, pl))
        }
        if (types.isEmpty()) throw ImportError("No cards found in this package.")
        return types
    }

    fun build(map: Map<String, Choice>): Meta {
        say("Reading cards…", 8f)
        class CD(val o: ArrayList<Int> = ArrayList(), val id: ArrayList<Long> = ArrayList(), var did: Long = 0)
        val cd = HashMap<Long, CD>()
        rows("SELECT nid, ord, id, CASE WHEN odid THEN odid ELSE did END FROM cards ORDER BY nid, ord") { c ->
            val x = cd.getOrPut(c.getLong(0)) { CD().also { it.did = c.getLong(3) } }; x.o.add(c.getInt(1)); x.id.add(c.getLong(2))
        }
        val tmpDb = File(ctx.filesDir, "deck.db.tmp"); val tmpMedia = File(ctx.filesDir, "media.tmp")
        tmpMedia.deleteRecursively(); tmpMedia.mkdirs()
        val out = DeckStore.create(tmpDb)
        try {
            val paths = ArrayList<String>(); val pi = HashMap<String, Int>(); val used = HashSet<String>(); val perPath = ArrayList<Int>()
            var nn = 0; var nc = 0
            val ins = out.compileStatement("INSERT OR REPLACE INTO notes(id, k, p, ords, cids, flds, tags, subj, op) VALUES (?,?,?,?,?,?,?,0,?)")
            out.beginTransaction()
            rows("SELECT id, mid, flds, tags FROM notes ORDER BY id") { r ->
                val id = r.getLong(0); val mid = r.getLong(1); val c = cd[id]; val p = map[mid.toString()]
                if (c == null || p == null || p.k.isEmpty()) return@rows
                val f = r.getString(2).split('\u001f'); val k = p.k; val names = nt[mid]?.names ?: emptyList<String>()
                val g = { i: Int -> if (i >= 0 && i < f.size) html(f[i], used) else "" }
                var o: List<Int> = c.o; var ids: List<Long> = c.id
                if (k == "b" || k == "e") { val keep = c.o.indices.filter { c.o[it] < 2 }; o = keep.map { c.o[it] }; ids = keep.map { c.id[it] }; if (o.isEmpty()) return@rows }
                var fields = p.m.map(g).toMutableList()
                when (k) {
                    /* IOE → a basic card: front = image under the question mask, back = image under the answer mask; field 6 'ioe' */
                    "e" -> { val (im, qm, am, hd, ft) = fields; val rm = fields[5]; val sr = fields[6]; val x1 = fields[7]; val x2 = fields[8]
                        val ov = { m: String -> "<div class=\"ioe\">$im$m</div>" + (if (ft.isNotEmpty()) "<div class=\"ioe-f\">$ft</div>" else "") }
                        fields = mutableListOf(ov(qm), ov(am), listOf(rm, x1, x2).filter { it.isNotBlank() }.joinToString("<br><br>"), hd, sr, "", "ioe") }
                    "b" -> { val j = names.indices.firstOrNull { OPTS.containsMatchIn(names[it]) && it !in p.m } ?: -1
                        if (j >= 0 && j < f.size && f[j].isNotBlank()) fields[0] += "<br><br>" + g(j) }
                }
                val path = dk[c.did] ?: "Default"
                val x = pi.getOrPut(path) { paths.add(path); perPath.add(0); paths.size - 1 }; perPath[x]++
                ins.clearBindings()
                ins.bindLong(1, id); ins.bindString(2, if (k == "e") "b" else k); ins.bindLong(3, x.toLong())
                ins.bindString(4, o.joinToString(",")); ins.bindString(5, ids.joinToString(","))
                ins.bindString(6, JSONArray(fields).toString())
                ins.bindString(7, JSONArray(r.getString(3).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }).toString())
                ins.bindLong(8, x.toLong()); ins.executeInsert()
                nn++; nc += o.size
                if (nn % 2000 == 0) { check(); out.setTransactionSuccessful(); out.endTransaction(); out.beginTransaction(); say("Reading cards… %,d".format(nn), 8f + minOf(20f, nn / 1000f)) }
            }
            out.setTransactionSuccessful(); out.endTransaction()
            col?.close(); col = null; colFile.delete()
            if (nn == 0) throw ImportError("No cards to import with these settings.")

            /* media: name ↔ zip entry, then only the images the cards use */
            val mm = ArrayList<Pair<String, String>>()
            if (zip!!.getEntry("media") != null) {
                val u = entry("media").use { it.readBytes() }
                if (u.isNotEmpty() && u[0] == '{'.code.toByte()) JSONObject(String(u)).let { j -> j.keys().forEach { z -> mm.add(j.getString(z) to z) } }
                else pb(u).forEachIndexed { i, (k, v) -> if (k == 1 && v is ByteArray) pb(v).find { it.first == 1 }?.let { n -> mm.add(String(n.second as ByteArray) to i.toString()) } }
            }
            val want = mm.filter { (n, z) -> n in used && IMX.containsMatchIn(n) && zip!!.getEntry(z) != null }
            var bytes = 0L
            want.forEachIndexed { i, (n, z) ->
                val dst = File(tmpMedia, DeckStore.mediaName(n))
                entry(z).use { s -> dst.outputStream().use { bytes += s.copyTo(it, 1 shl 16) } }
                if (i % 50 == 0) { check(); say("Saving images… %,d / %,d".format(i + 1, want.size), 30f + 65f * (i + 1) / want.size) }
            }
            say("Saving deck…", 97f)
            /* subjects = top-level decks. when everything sits under one main deck, the main deck is dropped and its subdecks become the
               subjects; cards in the main deck itself form one more subject named after it */
            val tops = paths.map { it.split(" › ")[0] }.toSet(); val root = if (tops.size == 1) tops.first() else null; val rp = "$root › "
            val p2 = if (root != null) paths.map { if (it.startsWith(rp)) it.substring(rp.length) else it } else paths
            val np = ArrayList<String>(); val ni = HashMap<String, Int>()
            val remap = p2.map { p -> ni.getOrPut(p) { np.add(p); np.size - 1 } }
            val coll = Collator.getInstance()
            val names = np.map { it.split(" › ")[0] }.distinct().sortedWith { a, b -> coll.compare(a, b) }
            val pc = IntArray(np.size); remap.forEachIndexed { i, j -> pc[j] += perPath[i] }
            out.beginTransaction()
            remap.forEachIndexed { i, j -> out.execSQL("UPDATE notes SET p=?, subj=? WHERE op=?", arrayOf<Any>(j, names.indexOf(np[j].split(" › ")[0]), i)) }
            val meta = Meta(fileName.replace(Regex("\\.apkg$", RegexOption.IGNORE_CASE), ""), nn, nc, want.size, bytes, Instant.now().toString())
            fun info(k: String, v: String) = out.execSQL("INSERT OR REPLACE INTO info(k, v) VALUES (?, ?)", arrayOf(k, v))
            info("paths", JSONArray(np).toString()); info("pc", JSONArray(pc.toList()).toString()); info("subjects", JSONArray(names).toString()); info("meta", meta.json())
            out.setTransactionSuccessful(); out.endTransaction(); out.close()
            check()
            DeckStore.delete(ctx)
            if (!tmpDb.renameTo(DeckStore.dbFile(ctx)) || !tmpMedia.renameTo(DeckStore.mediaDir(ctx))) throw ImportError("Couldn’t save the deck.")
            return meta
        } catch (e: Throwable) {
            runCatching { if (out.inTransaction()) out.endTransaction(); out.close() }
            tmpDb.delete(); tmpMedia.deleteRecursively(); throw e
        }
    }

    fun close() {
        runCatching { col?.close() }; col = null
        runCatching { zip?.close() }; zip = null
        pkg.delete(); colFile.delete(); File(colFile.path + "-journal").delete()
    }
}
