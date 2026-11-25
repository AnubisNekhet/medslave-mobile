package pizza.kulhad.medslave

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import pizza.kulhad.medslave.ui.DCOL
import java.util.concurrent.Executors
import kotlin.random.Random

/* Reels Mode's home: a feed of posts and stories made from the deck (assets/web/feed.html draws it).
   Accounts are the Reels handles (reels.json). Each subject gets an equal share of them, its niche (when there are more subjects
   than handles, a handle covers several). A post or story is a random card's answer from its account's niche.
   Stories: 12 accounts at a time, picked anew every 5 minutes (seeded by the deck and the 5-minute slot, so they stay put within it). */
class FeedBridge(private val f: Feed) {
    private val h = Handler(Looper.getMainLooper())
    @JavascriptInterface fun more() = h.post { f.more() }
    @JavascriptInterface fun com(handle: String, n: Int) = h.post { f.comments(handle) }
    @JavascriptInterface fun share() = h.post { f.rs.reels.open("share") }
    @JavascriptInterface fun story(open: Boolean) = h.post { f.storyOpen = open }
    @JavascriptInterface fun seen(g: Int) = h.post { f.seen(g) }
    @JavascriptInterface fun imp() = h.post { f.rs.deckPick() }
    @JavascriptInterface fun settings() = h.post { f.rs.settingsSec = null; f.rs.settingsOpen = true }
    @JavascriptInterface fun credits() = h.post { f.rs.creditsOpen = true }
}

class FeedWeb(ctx: Context, f: Feed) : Page(ctx, "feed.html", false, { FeedBridge(f) }) {
    fun feed(j: String) = js("R.feed($j)")
    fun posts(j: String) = js("R.posts($j)")
    fun stories(j: String) = js("R.stories($j)")
    fun close() = js("R.close()")
    fun top(instant: Boolean = false) = js("R.top($instant)")
    fun heat(j: String) = js("R.heat($j)")
    /** the status and navigation bar heights (CSS px): the page runs behind the status bar and pads itself */
    fun insets(top: Float, bottom: Float) = js("R.insets($top,$bottom)")
}

class Feed(val rs: Rs) {
    private var made: FeedWeb? = null
    val web: FeedWeb get() = made ?: FeedWeb(rs.ctx, this).also { made = it; it.theme(rs.set.theme, rs.dark) }
    fun theme(t: String, dark: Boolean) { made?.theme(t, dark) }
    var storyOpen by mutableStateOf(false)

    private class Acc(val h: String, val ic: String, val c: String) { val subs = ArrayList<Int>() }
    private var deck: Deck? = null
    private var acc = listOf<Acc>()
    private var seed = 0L
    private var salt = 0L
    private var page = 0
    private var bucket = -1L
    private var groups = listOf<Int>()          // the story row's accounts, in order
    private val seen = HashSet<String>()        // "slot:handle"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun slot() = System.currentTimeMillis() / 300_000

    /** the feed is on screen: rebuilt for a new deck, stories renewed when the 5-minute slot has changed */
    fun show() {
        made?.heat(act())   // the heatmap post follows the reviews done since
        if (deck !== rs.deck) build()
        else if (slot() != bucket) { val b = slot(); io.execute { val g = stories(b); main.post { bucket = b; web.stories(g.toString()) } } }
    }

    private fun build() {
        val d = rs.deck; deck = d; page = 0; salt = System.currentTimeMillis()
        seed = (d.meta?.imported ?: "").hashCode().toLong()
        val hs = rs.reels.handles; val s = d.subjects.size
        val order = hs.indices.shuffled(Random(seed))
        acc = order.mapIndexed { i, hi -> Acc(hs[hi].first, hs[hi].second, hex(DCOL[i % DCOL.size].second.toArgb())) }
        if (s > 0) { if (s <= acc.size) acc.forEachIndexed { i, a -> a.subs.add(i % s) } else for (j in 0 until s) acc[j % acc.size].subs.add(j) }
        if (s == 0) acc = emptyList()
        val b = slot()
        io.execute {
            val j = JSONObject().put("paths", JSONArray(d.paths)).put("source", rs.set.source)
                .put("acc", JSONArray(acc.map { a -> JSONObject().put("h", a.h).put("ic", a.ic).put("c", a.c) }))
                .put("posts", posts(0)).put("groups", stories(b)).put("act", JSONObject(act()))
            main.post { if (deck === d) { bucket = b; web.feed(j.toString()) } }
        }
    }

    fun more() { if (acc.isEmpty()) return; val p = ++page; val d = deck; io.execute { val l = posts(p); main.post { if (deck === d) web.posts(l.toString()) } } }

    /** answers revealed per day (medslave:activity), for the heatmap post */
    private fun act(): String = rs.store.get(AK) ?: "{}"
    /** the app was opened: the feed starts at the top, on the heatmap post */
    fun opened() { made?.top(true) }

    fun comments(handle: String) { rs.reels.name = handle; rs.reels.open("com") }
    fun seen(g: Int) { groups.getOrNull(g)?.let { seen.add("$bucket:${acc[it].h}") } }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    private val CAPS = listOf("high yield 🔥", "save this for exam day", "you WILL see this again", "tag someone who got this wrong", "remember this one",
        "this one's a classic", "pin this 📌", "read it twice", "no way you forgot this", "daily reminder")

    /** a random card from one of the account's niches: [note json, ordinal, subject] */
    private fun card(r: Random, a: Acc): Triple<JSONArray, Int, Int>? {
        val d = deck ?: return null; val s = a.subs[r.nextInt(a.subs.size)]; val notes = d.subjects[s].notes
        if (notes.isEmpty()) return null
        val n = notes[r.nextInt(notes.size)]
        return Triple(JSONArray(DeckStore.noteJson(rs.ctx, n)), n.ords[r.nextInt(n.ords.size)], s)
    }

    private fun posts(p: Int): JSONArray {
        val out = JSONArray(); if (acc.isEmpty()) return out
        val d = deck ?: return out; val r = Random(seed * 31 + salt + p * 7919L); val reels = rs.reels
        repeat(8) {
            val ai = r.nextInt(acc.size); val (n, o, _) = card(r, acc[ai]) ?: return@repeat
            val tags = n.optJSONArray(5)?.let { t -> (0 until t.length()).map { t.getString(it).split("::").last().replace(Regex("[^\\p{L}\\p{N}_]"), "") } }.orEmpty().filter { it.isNotEmpty() }
            val hash = tags.take(3).joinToString(" ") { "#" + it.lowercase() }   // the card's own tags; no deck paths in the feed
            out.put(JSONObject().put("a", ai).put("n", n).put("o", o).put("likes", r.nextInt(120, 98000)).put("coms", r.nextInt(4, 2400))
                .put("cap", (CAPS[r.nextInt(CAPS.size)] + " " + hash).trim())
                .put("cm", JSONArray(List(2) { JSONArray().put(reels.names[r.nextInt(reels.names.size)] + reels.suffixes[r.nextInt(reels.suffixes.size)]).put(reels.comments[r.nextInt(reels.comments.size)]) }))
                .put("t", listOf("2 minutes ago", "18 minutes ago", "1 hour ago", "3 hours ago", "7 hours ago", "yesterday", "2 days ago")[r.nextInt(7)]))
        }
        return out
    }

    private fun stories(b: Long): JSONArray {
        val out = JSONArray(); if (acc.isEmpty()) { groups = emptyList(); return out }
        val r = Random(seed * 131 + b)
        val pick = acc.indices.shuffled(r).take(12)
        val times = listOf("1m", "4m", "12m", "38m", "1h", "2h", "3h", "5h", "9h", "14h", "20h")
        val g = ArrayList<Int>()
        pick.forEach { ai ->
            val items = JSONArray(); repeat(1 + r.nextInt(3)) { card(r, acc[ai])?.let { (n, o, _) -> items.put(JSONObject().put("n", n).put("o", o)) } }
            if (items.length() == 0) return@forEach
            g.add(ai)
            out.put(JSONObject().put("a", ai).put("t", times[r.nextInt(times.size)]).put("items", items).put("seen", "$b:${acc[ai].h}" in seen))
        }
        groups = g
        return out
    }
}
