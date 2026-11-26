package pizza.kulhad.medslave

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/* Progress and settings, kept in the app's SharedPreferences: one JSON value per key, the same shapes the web version kept in
   localStorage (so ReelSlave progress backups still import). The deck itself is in the app's files (Deck.kt). */
const val LK = "medslave:last"        // resume point per deck {key: {n, o}}
const val OK = "medslave:order"       // seed per deck
const val AGK = "medslave:again"      // missed cards still to come back per deck [[id, o, k], …]
const val SK = "medslave:settings"
const val AK = "medslave:activity"    // answers revealed per day {yyyy-mm-dd: n}
const val SEEN = "medslave:seen"
const val HIDDEN = "medslave:hidden"   // hidden cards [[note id, ordinal, text, deck path], …] (a post's menu › Hide)
const val SAVED = "medslave:saved"     // saved cards [[note id, ordinal], …] (the feed's save button)
const val PLAYLISTS = "medslave:playlists"   // the user's playlists [{name, songs: [[title, artist, preview, art]]}]
const val RK = "medslave:recent"      // the deck reviewed last (its path), for the home bar's resume button

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("medslave", Context.MODE_PRIVATE)
    /** no medslave data at all yet: the first card opened shows the tutorial */
    val firstVisit = keys().isEmpty()

    fun get(k: String): String? = p.getString(k, null)
    fun set(k: String, v: String?) { p.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply() }
    fun keys(): List<String> = p.all.keys.filter { it.startsWith("medslave:") }
    fun obj(k: String): JSONObject = try { JSONObject(get(k) ?: "{}") } catch (e: Exception) { JSONObject() }
    fun mapSet(k: String, key: String, v: Any?) { val m = obj(k); if (v == null) m.remove(key) else m.put(key, v); set(k, m.toString()) }
    /** progress import: every medslave:* key replaced by the file's */
    fun replaceAll(data: Map<String, String>) {
        val e = p.edit(); keys().forEach { e.remove(it) }; data.forEach { (k, v) -> e.putString(k, v) }; e.commit()
    }
    fun dropProgress() { val e = p.edit(); e.remove(LK); e.remove(OK); e.remove(AGK); e.remove(RK); e.apply() }

    fun actMap(): Map<String, Int> { val o = obj(AK); return o.keys().asSequence().associateWith { o.optInt(it) } }
    fun actBump() { val k = LocalDate.now().toString(); val m = obj(AK); m.put(k, m.optInt(k) + 1); set(AK, m.toString()) }
}

val THEMES = listOf("default", "you", "ctp", "tokyo", "nord", "dracula")   // you: Material You (Android 12+)

/* settings, as JSON under medslave:settings */
class Settings(private val st: Store) {
    var ordered by mutableStateOf(false)
    var style by mutableStateOf("tinder")      // tinder | flip (older saves: doom)
    var reveal by mutableStateOf("pop")        // none | pop | up
    var theme by mutableStateOf("default")
    var mode by mutableStateOf("auto")            // light or dark: auto (the phone's) | light | dark (Settings › Appearance › Mode)
    var reels by mutableStateOf(true)          // on for a first visit
    var source by mutableStateOf(false)
    var goff by mutableStateOf(setOf<String>()) // music genres switched off
    var nocom by mutableStateOf(false)           // Reels Mode: hide comments (Settings › Appearance)
    var expl by mutableStateOf(true)             // an MCQ's explanation is always shown (Hide explanation puts it on the back)
    var tags by mutableStateOf(false)            // show a card's tags at its bottom (Settings › Reviewing)
    var hidex by mutableStateOf(true)           // an MCQ's explanation on the back of its answer card, on by default (Settings › Reviewing; stored as exback)
    var volkeys by mutableStateOf(false)         // the volume keys answer cards (Settings › Reviewing)

    init { load() }
    fun load() {
        val o = st.obj(SK)
        ordered = o.optBoolean("ordered", false); style = o.optString("style", "tinder"); reveal = o.optString("reveal", "pop")
        theme = o.optString("theme", "default").let { if (it in THEMES) it else "default" }
        mode = o.optString("mode", "auto").let { if (it in listOf("auto", "light", "dark")) it else "auto" }
        reels = o.optBoolean("reels", true); source = o.optBoolean("source", false); nocom = o.optBoolean("nocom", false); expl = true; tags = o.optBoolean("tags", false); volkeys = o.optBoolean("volkeys", false); hidex = o.optBoolean("exback", true)
        goff = o.optJSONArray("goff")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
    }
    fun save() {
        st.set(SK, JSONObject().put("ordered", ordered).put("style", style).put("reveal", reveal).put("theme", theme).put("mode", mode)
            .put("reels", reels).put("source", source).put("nocom", nocom).put("expl", expl).put("tags", tags).put("volkeys", volkeys).put("exback", hidex).put("goff", JSONArray(goff.toList())).toString())
    }
    /** Reels Mode forces Flip + Scroll up */
    val flip get() = reels || style == "flip" || style == "doom"
    val rev get() = if (reels) "up" else reveal
}
