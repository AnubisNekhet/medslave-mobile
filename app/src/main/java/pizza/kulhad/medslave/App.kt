package pizza.kulhad.medslave

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings.Global
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect as CRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** a pass's queue entry: a note (its ordinal picked when it comes up), or a missed card coming back (rq) */
class Item(val n: Note, val rq: Boolean, val o: Int = 0)
class Session(val notes: List<Note>, val name: String, val key: String, val q: MutableList<Item>, var i: Int, var ri: Int, var first: Int?) { var wr = 0 }
class Cur(val n: Note, val o: Int, val again: Boolean, val json: String)
class Fin(val name: String, val count: Int, val wr: Int)
class Dlg(val text: String, val ok: String = "OK", val cancel: Boolean = false, val danger: Boolean = false, val onOk: () -> Unit = {})
class Ghost(val img: ImageBitmap, val d: Int) { val y = Animatable(0f) }

val EASE_OUTISH: Easing = CubicBezierEasing(.2f, .7f, .3f, 1f)
val EASE_IN_OUT: Easing = CubicBezierEasing(.42f, 0f, .58f, 1f)
val EASE_IN: Easing = CubicBezierEasing(.42f, 0f, 1f, 1f)
val EASE: Easing = CubicBezierEasing(.25f, .1f, .25f, 1f)

class Rs(val ctx: Context) {
    val store = Store(ctx)
    val set = Settings(store)
    val web = CardWeb(ctx)
    val reels = Reels(this)
    lateinit var scope: CoroutineScope
    var activity: Activity? = null
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    // deck
    var deck by mutableStateOf(Deck.EMPTY)
    var loaded by mutableStateOf(false)
    // screens and popups
    var study by mutableStateOf(false)
    var picker by mutableStateOf<Node?>(null)
    var settingsOpen by mutableStateOf(false)
    var progressOpen by mutableStateOf(false)
    var corrOpen by mutableStateOf(false)
    var creditsOpen by mutableStateOf(false)
    var heatOpen by mutableStateOf(false)
    var tutorial by mutableStateOf(false)
    var dialog by mutableStateOf<Dlg?>(null)
    var heatTick by mutableIntStateOf(0)
    // study
    var S: Session? = null; private set
    var cur by mutableStateOf<Cur?>(null)
    var stage by mutableIntStateOf(0)
    var fin by mutableStateOf<Fin?>(null)
    private var tut = store.firstVisit
    private var last: Note? = null
    // import
    var busy by mutableStateOf(false)
    var dkMsg by mutableStateOf("")
    var importTypes by mutableStateOf<List<ImportType>?>(null)
    private var importer: Importer? = null
    // file pickers, set by the activity
    var saveFile: (name: String, mime: String, data: ByteArray) -> Unit = { _, _, _ -> }
    var openFile: (mimes: Array<String>, then: (Uri) -> Unit) -> Unit = { _, _ -> }

    /* the card's motion: Tinder drag/fly, Flip leave, New Card Reveal; the Reels overlay rides with the card in Scroll up */
    val tx = Animatable(0f); val ty = Animatable(0f); val rot = Animatable(0f); val alpha = Animatable(1f); val scale = Animatable(1f)
    val oy = Animatable(0f)
    var ghost by mutableStateOf<Ghost?>(null)
    var leaving = false
    var stageRect = CRect.Zero      // the card area in the window (px)
    var stageH = 0f                 // its height (px)
    var screenW = 0f; var screenH = 0f; var density = 1f

    fun rm() = Global.getFloat(ctx.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    private fun post(f: () -> Unit) = main.post(f)

    private var pending: Uri? = null
    fun loadDeck() {
        io.execute {
            val d = DeckStore.load(ctx)
            post { deck = d; loaded = true; web.init(d.paths, set.source); pending?.let { pending = null; importUri(it) } }
        }
    }

    /* ---------------------------------------------------------------- saved progress (core.js) */
    private fun lastOf(k: String): Pair<Long, Int>? = store.obj(LK).optJSONObject(k)?.let { it.optLong("n") to it.optInt("o") }
    private fun remember() { val s = S ?: return; val c = cur ?: return; store.mapSet(LK, s.key, JSONObject().put("n", c.n.id).put("o", c.o)) }
    private fun saveAgain() {
        val s = S ?: return; val l = JSONArray()
        for (j in maxOf(s.ri + 1, s.i) until s.q.size) { val x = s.q[j]; if (x.rq) l.put(JSONArray().put(x.n.id).put(x.o).put(j - s.ri)) }
        store.mapSet(AGK, s.key, if (l.length() > 0) l else null)
    }
    private fun requeue(c: Cur) {
        val s = S ?: return
        val at = minOf(s.q.size, s.i + 1 + Order.RQMIN + (Math.random() * (Order.RQMAX - Order.RQMIN + 1)).toInt())
        s.q.add(at, Item(c.n, true, c.o)); saveAgain()
    }
    fun setOrdered(v: Boolean) { set.ordered = v; last = null; set.save(); store.set(LK, null); store.set(AGK, null); if (!v) store.set(OK, null) }
    fun resetProgress() { last = null; store.dropProgress() }

    /* ---------------------------------------------------------------- study (deck.js) */
    fun go(n: Node) { picker = null; study = true; store.set(RK, n.ps); begin(deck.notesFor(n), n.name, n.ps) }
    /** the home bar's middle button: back to the last card reviewed, in the deck it was in */
    fun recent(): Node? = store.get(RK)?.let { k -> deck.nodes.find { it.ps == k } }

    fun begin(notes: List<Note>, name: String, key: String, resume: Boolean = true) {
        val sv = if (resume) lastOf(key) else null
        val p = if (set.ordered) Order.planOrdered(notes, sv) else {
            val seed = if (resume) store.obj(OK).optString(key, "").ifEmpty { null } else null
            Order.planPass(notes, seed, sv, last).also { if (it.seed != seed) store.mapSet(OK, key, it.seed) }
        }
        val s = Session(notes, name, key, p.q.map { Item(it, false) }.toMutableList(), p.start - 1, p.start, p.first)
        S = s
        if (resume) {
            val ag = store.obj(AGK).optJSONArray(key)
            if (ag != null && ag.length() > 0) {
                val by = notes.associateBy { it.id }
                for (i in 0 until ag.length()) { val a = ag.optJSONArray(i) ?: continue; val n = by[a.optLong(0)] ?: continue; val o = a.optInt(1)
                    if (o in n.ords) s.q.add(minOf(s.q.size, p.start + a.optInt(2)), Item(n, true, o)) }
            }
        } else store.mapSet(AGK, key, null)
        fin = null; progressOpen = false; corrOpen = false
        next(); showT()
    }

    /** the next card. d: where it comes from in Scroll up, 1 = below, -1 = above */
    fun next(d: Int = 1) {
        val s = S ?: return
        s.i++
        if (s.i >= s.q.size) return finish()
        val it = s.q[s.i]; val n = it.n
        val o = if (it.rq) it.o else (s.first ?: n.ords[(Math.random() * n.ords.size).toInt()]).also { s.first = null }
        val c = Cur(n, o, it.rq, DeckStore.noteJson(ctx, n))
        cur = c; stage = 0
        if (!it.rq) { s.ri = s.i; remember() }
        saveAgain()
        web.seqLeft = 0
        val m = set.rev; val anim = m != "none" && !rm()
        if (anim) prepEnter(m, d)
        web.show(c.json, o, 0, set.flip) { if (cur === c) { if (anim) runEnter(m, d) else scope.launch { pose() } } }
        reels.onCard()
    }

    private fun finish() {
        val s = S ?: return
        ghost = null; leaving = false
        s.q.lastOrNull()?.let { last = it.n }
        store.mapSet(AGK, s.key, null); cur = null; stage = 0
        store.mapSet(LK, s.key, null)
        if (!set.ordered) store.mapSet(OK, s.key, Order.planPass(s.notes, null, null, last).seed)
        progressOpen = false; corrOpen = false
        fin = Fin(s.name, s.q.count { !it.rq }, s.wr)
        web.clear(); scope.launch { pose() }
        reels.onCard()
    }
    fun redo() { val s = S ?: return; begin(s.notes, s.name, s.key, false) }
    fun home() { study = false; reels.sheet = null; reels.onCard(); heatTick++ }

    private fun showT() { if (!tut) return; tut = false; store.set(SEEN, "1"); tutorial = true }

    /* ---------------------------------------------------------------- going forward (review.js) */
    /** question side: reveal (or the next sequential cloze step). answer side: ok false = missed it (requeued) */
    fun advance(sw: Boolean = false, ok: Boolean = true) {
        val c = cur ?: return; if (leaving) return
        if (stage == 1) {
            if (!ok) { requeue(c); S?.let { it.wr++ } }
            return if (set.flip) leave(if (ok) -1 else 1, ok) else next()
        }
        if (!sw && web.seqLeft > web.seqN) { web.step(); return }
        stage = 1; store.actBump(); heatTick++
        if (!set.flip) {
            val fade = sw && !rm()
            web.show(c.json, c.o, 1, false) { if (cur === c) scope.launch { pose(if (fade) 0f else 1f); if (fade) alpha.animateTo(1f, tween(260, easing = EASE_OUTISH)) } }
            return
        }
        web.answer(rm())
    }
    fun wrong() { if (cur == null) return; if (stage == 0) return advance(true); if (set.flip) advance(true, false) else fly(-1) }

    /** Tinder: the card flies off to side d, then the direction is the verdict on the answer side */
    fun fly(d: Int, dy: Float = 0f) {
        if (leaving || cur == null) return
        if (rm()) return advance(true, d > 0)
        leaving = true; if (stage == 1) web.glow(d.toFloat())
        scope.launch {
            val t = tween<Float>(250, easing = EASE_IN)
            launch { tx.animateTo(d * screenW, t) }; launch { ty.animateTo(dy * .2f, t) }; launch { rot.animateTo(d * 20f, t) }; launch { alpha.animateTo(0f, t) }
            delay(230); leaving = false; advance(true, d > 0)
        }
    }

    /** Flip: leaving an answer moves the card up (knew it, d = -1) or down (missed it, d = 1). Scroll up: a picture of the old card rides
        off while the next one comes in from the other side; otherwise the card slides away, then the next one is drawn */
    fun leave(d: Int, ok: Boolean = d < 0) {
        if (leaving) return
        if (rm()) return next(-d)
        if (!set.reels) web.glow(if (ok) 1f else -1f)
        leaving = true
        if (set.rev == "up") {
            capture { bmp ->
                if (bmp != null) ghost = Ghost(bmp.asImageBitmap(), d)
                next(-d)
                scope.launch { delay(450); leaving = false }
            }
            return
        }
        scope.launch {
            val t = tween<Float>(250, easing = EASE_IN)
            launch { ty.animateTo(d * screenH * .6f, t) }; launch { alpha.animateTo(0f, t) }
            delay(230); leaving = false; next(-d)
        }
    }

    fun repost(after: () -> Unit) {
        if (cur == null || leaving) return
        scope.launch {
            delay(if (rm()) 0 else 420)
            val c = cur
            if (c != null && !leaving) { if (stage == 0) { store.actBump(); heatTick++ }; requeue(c); S?.let { it.wr++ }; leave(-1, false) }
            after()
        }
    }

    private fun capture(then: (Bitmap?) -> Unit) {
        val w = activity?.window; val r = stageRect
        if (w == null || r.width < 1 || r.height < 1) return then(null)
        val b = Bitmap.createBitmap(r.width.roundToInt(), r.height.roundToInt(), Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(w, Rect(r.left.roundToInt(), r.top.roundToInt(), r.left.roundToInt() + b.width, r.top.roundToInt() + b.height), b,
                { res -> then(if (res == PixelCopy.SUCCESS) b else null) }, main)
        } catch (e: Exception) { then(null) }
    }

    /** snaps the card back to rest (alpha a) */
    suspend fun pose(a: Float = 1f) { tx.snapTo(0f); ty.snapTo(0f); rot.snapTo(0f); scale.snapTo(1f); alpha.snapTo(a); oy.snapTo(0f) }

    private var enterJob: Job? = null
    /** New Card Reveal, before the card is drawn: the start of the animation */
    private fun prepEnter(m: String, d: Int) {
        enterJob?.cancel()
        enterJob = scope.launch {
            tx.snapTo(0f); rot.snapTo(0f)
            if (m == "up") { ty.snapTo(d * stageH); oy.snapTo(d * stageH); alpha.snapTo(1f); scale.snapTo(1f) }
            else { ty.snapTo(0f); oy.snapTo(0f); scale.snapTo(.8f); alpha.snapTo(0f) }
        }
    }
    /** …and once it's on screen. pop: from 80% and transparent, overshooting to 101%. up: rises from below, overshooting 4dp */
    private fun runEnter(m: String, d: Int) {
        enterJob?.cancel()
        enterJob = scope.launch {
            val g = ghost
            if (g != null) launch {
                val h = stageH; val k = 4 * density
                g.y.animateTo(g.d * h, keyframes { durationMillis = 450; 0f at 0 using EASE_OUTISH; g.d * (h + k) at 315 using EASE_IN_OUT; g.d * h at 450 })
                if (ghost === g) ghost = null
            }
            if (m == "up") {
                val h = stageH; val k = 4 * density
                val kf = keyframes<Float> { durationMillis = 450; d * h at 0 using EASE_OUTISH; -d * k at 315 using EASE_IN_OUT; 0f at 450 }
                launch { oy.animateTo(0f, kf) }
                ty.animateTo(0f, kf)
            } else {
                launch { alpha.animateTo(1f, keyframes { durationMillis = 380; 0f at 0 using EASE_OUTISH; 1f at 247 using EASE_IN_OUT; 1f at 380 }) }
                scale.animateTo(1f, keyframes { durationMillis = 380; .8f at 0 using EASE_OUTISH; 1.01f at 247 using EASE_IN_OUT; 1f at 380 })
            }
        }
    }

    /* ---------------------------------------------------------------- corrections (panels.js) */
    fun corrections(): JSONArray = try { JSONArray(store.get(CK) ?: "[]") } catch (e: Exception) { JSONArray() }
    fun addCorrection(c: Cur, text: String) {
        val a = corrections()
        a.put(JSONObject().put("card_id", c.n.cids.getOrElse(c.n.ords.indexOf(c.o)) { 0L }).put("note_id", c.n.id)
            .put("deck", deck.paths.getOrElse(c.n.p) { "" }.split(" › ").joinToString(" / ")).put("correction", text).put("submitted", Instant.now().toString()))
        store.set(CK, a.toString())
    }
    fun copy(text: String) = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("corrections.json", text))

    /* ---------------------------------------------------------------- progress backup: every medslave:* key as JSON {app, v, exported, data}.
       backups made by ReelSlave (app 'reelslave', reelslave:* keys) still import */
    fun exportProgress() {
        val d = JSONObject(); store.keys().forEach { d.put(it, store.get(it)) }
        val j = JSONObject().put("app", "medslave").put("v", 1).put("exported", Instant.now().toString()).put("data", d)
        saveFile("medslave-progress-${LocalDate.now()}.json", "application/json", j.toString(1).toByteArray())
    }
    fun importProgress() = openFile(arrayOf("application/json", "text/plain", "*/*")) { uri ->
        val j = runCatching { JSONObject(ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }) }.getOrNull()
            ?: return@openFile run { dialog = Dlg("That file isn't valid JSON.") }
        val d = if (j.optString("app") in setOf("medslave", "reelslave")) j.optJSONObject("data") else null
        val m = d?.keys()?.asSequence()?.associate { it.replaceFirst(Regex("^reelslave:"), "medslave:") to d.opt(it) }
        if (m == null || m.any { (k, v) -> !k.startsWith("medslave:") || v !is String }) return@openFile run { dialog = Dlg("That isn't a medslave progress file.") }
        val ex = j.optString("exported", "").take(10)
        dialog = Dlg("Replace all progress on this device with this file" + (if (ex.isNotEmpty()) " (exported $ex)" else "") + "?", "Replace", cancel = true) {
            store.replaceAll(m.mapValues { it.value as String }); set.load(); last = null; S = null; cur = null; fin = null
            settingsOpen = false; study = false; heatTick++; web.init(deck.paths, set.source)
        }
    }

    /* ---------------------------------------------------------------- deck import / delete (Settings › Data) */
    fun deckPick() {
        if (!deck.empty) { dialog = Dlg("Delete the current deck first (Settings › Data)."); return }
        if (!busy) openFile(arrayOf("*/*")) { importUri(it) }
    }
    fun importUri(uri: Uri) {
        if (!loaded) { pending = uri; return }
        if (!deck.empty) { dialog = Dlg("Delete the current deck first (Settings › Data)."); return }
        if (busy) return
        busy = true; dkMsg = "Reading note types…"
        val imp = Importer(ctx) { m, p -> post { if (importer != null) dkMsg = m + (p?.let { " · ${it.roundToInt()}%" } ?: "") } }
        importer = imp
        io.execute {
            try { val t = imp.scan(uri); post { if (importer === imp) { dkMsg = "Choose the fields"; settingsOpen = false; importTypes = t } } }
            catch (e: Throwable) { post { importFail(imp, e) } }
        }
    }
    fun importGo(map: Map<String, Choice>) {
        val imp = importer ?: return
        importTypes = null; dkMsg = "Importing…"
        io.execute {
            try { imp.build(map); imp.close(); post { if (importer === imp) { importer = null; busy = false; store.dropProgress(); last = null; dkMsg = "Imported ✓"; loadDeck() } } }
            catch (e: Throwable) { post { importFail(imp, e) } }
        }
    }
    fun importCancel() {
        val imp = importer ?: return
        imp.cancelled = true; importer = null; importTypes = null; busy = false; dkMsg = ""
        io.execute { imp.close() }
    }
    private fun importFail(imp: Importer, e: Throwable) {
        io.execute { imp.close() }
        if (importer !== imp) return
        importer = null; importTypes = null; busy = false; dkMsg = ""
        if (e is InterruptedException) return
        val m = when (e) { is ImportError -> e.message; is OutOfMemoryError -> "the importer stopped (the deck may be too big for this device)"
            is android.database.sqlite.SQLiteFullException -> "Not enough storage space on this device for this deck."; else -> e.message ?: e.toString() }
        dialog = Dlg("Import failed: $m")
    }
    fun deleteDeck() {
        if (busy || deck.empty) return
        dialog = Dlg("Delete " + (deck.meta?.let { "“${it.name}”" } ?: "the current deck") + " from this device? Your place and card order in it are cleared too. Your activity history stays.", "Delete", cancel = true, danger = true) {
            busy = true
            io.execute { DeckStore.delete(ctx); post { store.dropProgress(); last = null; S = null; cur = null; fin = null; busy = false; dkMsg = ""; deck = Deck.EMPTY; web.init(emptyList(), set.source) } }
        }
    }
}
