package pizza.kulhad.medslave

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/* Progress and settings, kept in the app's SharedPreferences: one JSON value per key, the same shapes the web version kept in
   localStorage (so ReelSlave progress backups still import). The deck itself is in the app's files (Deck.kt). */
const val LK = "medslave:last" // resume point per deck {key: {n, o}}
const val OK = "medslave:order" // seed per deck
const val AGK = "medslave:again" // missed cards still to come back per deck [[id, o, k], …]
const val SK = "medslave:settings"
const val AK = "medslave:activity" // answers revealed per day {yyyy-mm-dd: n}
const val SEEN = "medslave:seen"
const val TOUR = "medslave:tour" // the app tour's parts seen {"menu": 1, "feed": 1} (ui/Tour.kt)
const val HIDDEN = "medslave:hidden" // hidden cards [[note id, ordinal, text, deck path], …] (a post's menu › Hide)
const val HIDSUB = "medslave:hiddenSubjects" // hidden subjects [[subject name, the handle blocked for it], …] (Block on an account)
const val FILTERS = "medslave:filters" // feed filters [{id, name, decks: [deck folder ids]}] (Settings › Feed › Feed filters)
const val FOLLOWING = "medslave:following" // the accounts followed (their handles; a profile's Follow button)
const val FACTIVE = "medslave:filter" // the feed filter that's on (its id; none: no filter)
const val SAVED = "medslave:saved" // saved cards [[note id, ordinal], …] (the feed's save button)
const val PLAYLISTS = "medslave:playlists" // the user's playlists [{name, songs: [[title, artist, preview, art]]}]
const val VK = "app:version" // the version that last ran (release notes after an update); not medslave:*, so backups neither carry nor wipe it
const val RK = "medslave:recent" // the deck reviewed last (its path), for the home bar's resume button

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("medslave", Context.MODE_PRIVATE)

    /** no medslave data at all yet: the first card opened shows the tutorial */
    val firstVisit = keys().isEmpty()

    fun get(k: String): String? = p.getString(k, null)
    fun set(k: String, v: String?) {
        p.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply()
    }
    fun keys(): List<String> = p.all.keys.filter { it.startsWith("medslave:") }
    fun obj(k: String): JSONObject = try {
        JSONObject(get(k) ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }
    fun mapSet(k: String, key: String, v: Any?) {
        val m = obj(k)
        if (v == null) m.remove(key) else m.put(key, v)
        set(k, m.toString())
    }

    /** progress import: every medslave:* key replaced by the file's */
    fun replaceAll(data: Map<String, String>) {
        val e = p.edit()
        keys().forEach { e.remove(it) }
        data.forEach { (k, v) -> e.putString(k, v) }
        e.commit()
    }

    /** the one deck from before there could be several moved into folder id: its saved places, orders, missed cards and recent deck
     get the id in front of their keys, like every deck's now ("<id>/Anatomy"; All decks is "<id>/" then a star) */
    fun prefixProgress(id: String) {
        val mine = Regex("^\\d+/") // already a deck's key
        listOf(LK, OK, AGK).forEach { k ->
            val o = obj(k)
            val n = JSONObject()
            o.keys().forEach { n.put(if (mine.containsMatchIn(it)) it else "$id/$it", o.get(it)) }
            if (o.length() > 0) set(k, n.toString())
        }
        get(RK)?.let { if (!mine.containsMatchIn(it)) set(RK, "$id/$it") }
    }

    /** one deck's saved places, orders and missed cards (keys starting with pre) go */
    fun dropProgressOf(pre: String) {
        listOf(LK, OK, AGK).forEach { k ->
            val o = obj(k)
            o.keys().asSequence().toList().filter { it.startsWith(pre) }.forEach { o.remove(it) }
            set(k, o.toString())
        }
        if (get(RK)?.startsWith(pre) == true) set(RK, null)
    }
    fun dropProgress() {
        val e = p.edit()
        e.remove(LK)
        e.remove(OK)
        e.remove(AGK)
        e.remove(RK)
        e.apply()
    }

    fun actMap(): Map<String, Int> {
        val o = obj(AK)
        return o.keys().asSequence().associateWith { o.optInt(it) }
    }
    fun actBump() {
        val k = LocalDate.now().toString()
        val m = obj(AK)
        m.put(k, m.optInt(k) + 1)
        set(AK, m.toString())
    }
}

val THEMES = listOf("default", "you", "ctp", "tokyo", "nord", "dracula") // you: Material You (Android 12+)

/* settings, as JSON under medslave:settings */
class Settings(private val st: Store) {
    var ordered by mutableStateOf(false)
    var style by mutableStateOf("tinder") // tinder | flip (older saves: doom)
    var reveal by mutableStateOf("pop") // none | pop | up
    var theme by mutableStateOf("default")
    var accent by mutableStateOf(-1) // the accent: an index into the theme's accents(); -1 = the theme's blue, the default (Settings › Appearance)
    var redlike by mutableStateOf(true) // likes in Instagram red instead of the accent, on by default (Settings › Reviewing, Reels Mode; stored as likered)
    var fakeload by mutableStateOf(true) // pretend-online waits: post placeholders, comment and refresh delays (Settings › Appearance)
    var mode by mutableStateOf("auto") // light or dark: auto (the phone's) | light | dark (Settings › Appearance › Mode)
    var reels by mutableStateOf(true) // on for a first visit
    var source by mutableStateOf(false)
    var goff by mutableStateOf(setOf<String>()) // music genres switched off
    var nocom by mutableStateOf(false) // Reels Mode: hide comments (Settings › Appearance)
    var tags by mutableStateOf(false) // show a card's tags at its bottom (Settings › Reviewing)
    var hidex by mutableStateOf(true) // an MCQ's explanation on the back of its answer card, on by default (Settings › Reviewing; stored as exback)
    var volkeys by mutableStateOf(false) // the volume keys answer cards (Settings › Reviewing)
    var ffreels by mutableStateOf(false) // Reels Mode: the feed filter that's on also limits reels (Settings › Reviewing)
    var avatar by mutableStateOf("waves") // Reels Mode's account avatars: waves | solid (Settings › Appearance › Profile style)

    init {
        load()
    }
    fun load() {
        val o = st.obj(SK)
        ordered = o.optBoolean("ordered", false)
        style = o.optString("style", "tinder")
        reveal = o.optString("reveal", "pop")
        theme = o.optString("theme", "default").let { if (it in THEMES) it else "default" }
        accent = o.optInt("accent", -1)
        redlike = o.optBoolean("likered", true)
        fakeload = o.optBoolean("fakeload", true)
        mode = o.optString("mode", "auto").let { if (it in listOf("auto", "light", "dark")) it else "auto" }
        reels = o.optBoolean("reels", true)
        source = o.optBoolean("source", false)
        nocom = o.optBoolean("nocom", false)
        tags = o.optBoolean("tags", false)
        volkeys = o.optBoolean("volkeys", false)
        hidex = o.optBoolean("exback", true)
        ffreels = o.optBoolean("ffreels", false)
        avatar = o.optString("avatar", "waves").let { if (it == "solid") it else "waves" }
        goff = o.optJSONArray("goff")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
    }
    fun save() {
        st.set(
            SK,
            JSONObject().put("ordered", ordered).put("style", style).put("reveal", reveal).put("theme", theme).put("mode", mode).put("fakeload", fakeload).put("likered", redlike).put("accent", accent)
                .put("reels", reels).put("source", source).put("nocom", nocom).put("tags", tags).put("volkeys", volkeys).put("exback", hidex).put("avatar", avatar).put("ffreels", ffreels).put("goff", JSONArray(goff.toList())).toString()
        )
    }

    /** Reels Mode forces Flip + Scroll up */
    val flip get() = reels || style == "flip" || style == "doom"
    val rev get() = if (reels) "up" else reveal
}
