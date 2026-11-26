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
    @JavascriptInterface fun seen(ai: Int) = h.post { f.seen(ai) }
    @JavascriptInterface fun imp() = h.post { f.rs.deckPick() }
    @JavascriptInterface fun settings() = h.post { f.rs.settingsSec = null; f.rs.settingsOpen = true }
    @JavascriptInterface fun credits() = h.post { f.rs.creditsOpen = true }
    @JavascriptInterface fun refresh() = h.post { f.refresh() }
    @JavascriptInterface fun profile(ai: Int) = h.post { f.profile(ai) }
    @JavascriptInterface fun pmore() = h.post { f.profileMore() }
    @JavascriptInterface fun pfOpen(open: Boolean) = h.post { f.profileOpen = open; if (!open) f.stopSong() }
    @JavascriptInterface fun saved() = h.post { f.savedProfile() }
    @JavascriptInterface fun save(id: String, o: Int, on: Boolean) = h.post { f.save(id.toLong(), o, on) }
    @JavascriptInterface fun hide(id: String, o: Int, text: String, path: String) = h.post { f.hide(id.toLong(), o, text, path) }
    @JavascriptInterface fun play(k: String) = h.post { f.playSong(k) }
    @JavascriptInterface fun mute() = h.post { f.rs.reels.tapMusic() }
}

class FeedWeb(ctx: Context, f: Feed) : Page(ctx, "feed.html", false, { FeedBridge(f) }) {
    fun feed(j: String) = js("R.feed($j)")
    fun posts(j: String) = js("R.posts($j)")
    fun stories(j: String) = js("R.stories($j)")
    fun close() = js("R.close()")
    fun top(instant: Boolean = false) = js("R.top($instant)")
    fun heat(j: String) = js("R.heat($j)")
    fun refreshed(j: String) = js("R.refreshed($j)")
    fun nocom(v: Boolean) = js("R.nocom($v)")
    fun songsOn(j: String) = js("R.songsOn($j)")
    fun opts(source: Boolean, expl: Boolean, tags: Boolean) = js("R.opts($source,$expl,$tags)")
    fun savedSet(j: String) = js("R.savedSet($j)")
    fun replay() = js("R.replay()")
    fun profile(j: String) = js("R.profile($j)")
    fun profilePosts(j: String) = js("R.pposts($j)")
    fun closeProfile() = js("R.closeProfile()")
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
            val j = JSONObject().put("paths", JSONArray(d.paths)).put("source", rs.set.source).put("expl", rs.set.expl).put("tags", rs.set.tags)
                .put("acc", JSONArray(acc.map { a -> JSONObject().put("h", a.h).put("ic", a.ic).put("c", a.c) }))
                .put("posts", posts(0)).put("groups", stories(b)).put("act", JSONObject(act())).put("saved", saved())
            main.post { if (deck === d) { bucket = b; web.feed(j.toString()); web.nocom(rs.set.nocom) } }
        }
    }

    /** pull to refresh: a new set of posts (the stories stay) */
    fun refresh() { if (acc.isEmpty()) return web.refreshed("[]"); val d = deck; salt = System.currentTimeMillis(); page = 0
        io.execute { val l = posts(0); main.post { if (deck === d) web.refreshed(l.toString()) } } }

    fun more() { if (acc.isEmpty()) return; val p = ++page; val d = deck; io.execute { val l = posts(p); main.post { if (deck === d) web.posts(l.toString()) } } }

    /** answers revealed per day (medslave:activity), for the heatmap post */
    private fun act(): String = rs.store.get(AK) ?: "{}"
    /** the app was opened: the feed starts at the top, on the heatmap post */
    fun opened() { made?.top(true) }

    /** the song of the post or story on screen (-1: none) */
    fun playSong(k: String) { if (k.isEmpty()) stopSong() else rs.reels.byKey(k)?.let { rs.reels.playSong(it) } ?: stopSong() }
    /** the songs changed (a genre or playlist switched, a playlist edited or removed): the page swaps any post's or story's song that's
        no longer on for one that is, and plays again */
    /** Settings › Reviewing changed what cards show */
    fun opts() { made?.opts(rs.set.source, rs.set.expl, rs.set.tags) }
    fun songsChanged() { made?.let { w -> w.songsOn(JSONArray(rs.reels.songs.filter { !it.dead && it.genre !in rs.set.goff }.map { rs.reels.key(it) }).toString()) } }
    fun stopSong() { if (!rs.study) rs.reels.stop() }

    fun comments(handle: String) { rs.reels.name = handle; rs.reels.open("com") }
    /** a story was watched (by account: the page re-sorts the row, unwatched first) */
    fun seen(ai: Int) { acc.getOrNull(ai)?.let { seen.add("$bucket:${it.h}") } }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    private val CAPS = listOf("high yield 🔥", "save this for exam day", "you WILL see this again", "tag someone who got this wrong", "remember this one",
        "this one's a classic", "pin this 📌", "read it twice", "no way you forgot this", "daily reminder")

    /** a random card from one of the account's niches: [note json, ordinal, subject] */
    private fun card(r: Random, a: Acc): Triple<JSONArray, Int, Int>? {
        val d = deck ?: return null; val s = a.subs[r.nextInt(a.subs.size)]; val notes = d.subjects[s].notes
        if (notes.isEmpty()) return null
        var n: Note; var o: Int; var k = 0
        do { n = notes[r.nextInt(notes.size)]; o = n.ords[r.nextInt(n.ords.size)] } while (isHidden(n.id, o) && ++k < 12)   // never a hidden card
        if (isHidden(n.id, o)) return null
        return Triple(JSONArray(DeckStore.noteJson(rs.ctx, n)), o, s)
    }

    /** one post: a random card from account ai's niche, with counts, a caption from the card's tags and two comments */
    private fun post(r: Random, ai: Int): JSONObject? {
        val (n, o, _) = card(r, acc[ai]) ?: return null; val reels = rs.reels
        val tags = n.optJSONArray(5)?.let { t -> (0 until t.length()).map { t.getString(it).split("::").last().replace(Regex("[^\\p{L}\\p{N}_]"), "") } }.orEmpty().filter { it.isNotEmpty() }
        val hash = tags.take(3).joinToString(" ") { "#" + it.lowercase() }   // the card's own tags; no deck paths in the feed
        return JSONObject().put("a", ai).put("n", n).put("o", o).put("likes", r.nextInt(120, 98000)).put("coms", r.nextInt(4, 2400))
            .put("cap", (CAPS[r.nextInt(CAPS.size)] + " " + hash).trim())
            .put("cm", JSONArray(List(2) { JSONArray().put(reels.names[r.nextInt(reels.names.size)] + reels.suffixes[r.nextInt(reels.suffixes.size)]).put(reels.comments[r.nextInt(reels.comments.size)]) }))
            .put("t", listOf("2 minutes ago", "18 minutes ago", "1 hour ago", "3 hours ago", "7 hours ago", "yesterday", "2 days ago")[r.nextInt(7)])
            .also { song(r, it) }
    }

    private fun posts(p: Int): JSONArray {
        val out = JSONArray(); if (acc.isEmpty()) return out
        val r = Random(seed * 31 + salt + p * 7919L)
        repeat(8) { post(r, r.nextInt(acc.size))?.let { out.put(it) } }
        return out
    }

    /* ---- an account's profile (tapping an account in a post or a story; a facsimile, not a real account).
       the same every time for an account: its name (the id, properly capitalised), the id, followers, following and a post count,
       which is also how many posts the profile has. new each time: the highlights (named after subdecks of the niche, each a few
       random cards played like stories) and the posts (the card it was opened from first, then random ones from the niche) */
    private var pfAi = -1
    private var pfLeft = 0
    private val BIOS = listOf("your daily dose of %s", "%s, one card at a time 📚", "high yield %s only", "posting %s till boards",
        "%s notes you'll thank me for", "%s · no fluff", "%s for the night before")
    private fun title(h: String) = h.split('.', '_').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    fun profile(ai: Int) {
        if (ai !in acc.indices) return
        val d = deck ?: return; pfAi = ai
        io.execute {
            val a = acc[ai]; val st = Random(seed * 977 + ai); val r = Random(System.nanoTime())
            val subs = a.subs.map { d.subjects[it] }
            val kids = subs.flatMap { s -> d.top.find { it.name == s.name }?.let { n -> if (n.kids.isEmpty()) listOf(n) else n.kids } ?: emptyList() }.shuffled(r)
            val icons = rs.reels.handles.map { it.second }.distinct()
            val hl = JSONArray()
            kids.take(3 + r.nextInt(4)).forEach { k ->
                val notes = d.notesFor(k); if (notes.isEmpty()) return@forEach
                val items = JSONArray()
                repeat(2 + r.nextInt(4)) { val n = notes[r.nextInt(notes.size)]; val o = n.ords[r.nextInt(n.ords.size)]; if (isHidden(n.id, o)) return@repeat
                    items.put(JSONObject().put("n", JSONArray(DeckStore.noteJson(rs.ctx, n))).put("o", o).also { song(r, it) }) }
                hl.put(JSONObject().put("t", k.name).put("ic", icons[r.nextInt(icons.size)]).put("items", items))
            }
            val count = st.nextInt(9, 240); pfLeft = count - 1   // the card it was opened from is the first post
            val niche = subs.joinToString(" & ") { it.name }
            val j = JSONObject().put("a", ai).put("name", title(a.h)).put("bio", BIOS[st.nextInt(BIOS.size)].format(niche))
                .put("posts", count).put("followers", st.nextInt(2_400, 980_000)).put("following", st.nextInt(60, 1_200))
                .put("hl", hl).put("list", postsFor(ai))
            main.post { if (pfAi == ai) web.profile(j.toString()) }
        }
    }
    fun profileMore() { val ai = pfAi; if (ai < 0) return; io.execute { val l = if (ai >= 0) postsFor(ai) else JSONArray(); main.post { if (pfAi == ai) web.profilePosts(l.toString()) } } }
    private fun postsFor(ai: Int): JSONArray {
        val out = JSONArray(); val r = Random(System.nanoTime())
        repeat(minOf(8, pfLeft)) { post(r, ai)?.let { out.put(it); pfLeft-- } }
        return out
    }

    /* ---- saved cards (the save button on a post): kept on the device as [[note id, ordinal], …] under medslave:saved, shown as the
       saved.cards profile (the archive button, top left of the feed) */
    private fun savedList(): JSONArray = runCatching { JSONArray(rs.store.get(SAVED) ?: "[]") }.getOrDefault(JSONArray())
    fun saved(): JSONArray = JSONArray(List(savedList().length()) { savedList().getJSONArray(it).let { "${it.getLong(0)}:${it.getInt(1)}" } })
    fun save(id: Long, o: Int, on: Boolean) {
        val a = savedList(); val l = (0 until a.length()).map { a.getJSONArray(it) }.filter { !(it.getLong(0) == id && it.getInt(1) == o) }.toMutableList()
        if (on) l.add(0, JSONArray().put(id).put(o))
        rs.store.set(SAVED, JSONArray(l).toString())
    }
    /* ---- hidden cards (a post's menu › Hide): never picked for posts, stories or reels; listed in Settings › Hidden cards */
    class Hidden(val id: Long, val o: Int, val text: String, val path: String)
    private fun hiddenList(): JSONArray = runCatching { JSONArray(rs.store.get(HIDDEN) ?: "[]") }.getOrDefault(JSONArray())
    private var hiddenKeys: Set<String>? = null
    var hiddenTick by androidx.compose.runtime.mutableIntStateOf(0)   // bumps when the list changes
    fun isHidden(id: Long, o: Int): Boolean {
        val k = hiddenKeys ?: hiddenList().let { a -> (0 until a.length()).map { a.getJSONArray(it).let { e -> "${e.getLong(0)}:${e.getInt(1)}" } }.toSet() }.also { hiddenKeys = it }
        return "$id:$o" in k
    }
    fun hidden(): List<Hidden> = hiddenList().let { a -> (0 until a.length()).map { a.getJSONArray(it).let { e -> Hidden(e.getLong(0), e.getInt(1), e.optString(2), e.optString(3)) } } }
    private fun setHidden(l: List<JSONArray>) { rs.store.set(HIDDEN, JSONArray(l).toString()); hiddenKeys = null; hiddenTick++ }
    fun hide(id: Long, o: Int, text: String, path: String) {
        val a = hiddenList(); val l = (0 until a.length()).map { a.getJSONArray(it) }.filter { !(it.getLong(0) == id && it.getInt(1) == o) }.toMutableList()
        l.add(0, JSONArray().put(id).put(o).put(text).put(path)); setHidden(l)
    }
    fun unhide(id: Long, o: Int) { val a = hiddenList(); setHidden((0 until a.length()).map { a.getJSONArray(it) }.filter { !(it.getLong(0) == id && it.getInt(1) == o) }) }
    fun isSaved(id: Long, o: Int): Boolean { val a = savedList(); return (0 until a.length()).any { a.getJSONArray(it).let { e -> e.getLong(0) == id && e.getInt(1) == o } } }
    /** saved from Reels: the feed's bookmarks follow */
    fun saveFromReels(id: Long, o: Int, on: Boolean) { save(id, o, on); made?.savedSet(saved().toString()) }
    fun savedProfile() {
        val d = deck ?: return; pfAi = -2; pfLeft = 0
        io.execute {
            val by = HashMap<Long, Note>(); d.subjects.forEach { s -> s.notes.forEach { by[it.id] = it } }
            val r = Random(System.nanoTime()); val list = JSONArray(); val a = savedList()
            for (i in 0 until a.length()) { val e = a.getJSONArray(i); val n = by[e.getLong(0)] ?: continue
                list.put(JSONObject().put("a", -2).put("n", JSONArray(DeckStore.noteJson(rs.ctx, n))).put("o", e.getInt(1)).put("likes", r.nextInt(120, 98000))
                    .put("coms", r.nextInt(4, 2400)).put("cap", "").put("cm", JSONArray()).put("t", "")) }   // no time line in the archive
            val j = JSONObject().put("a", -2).put("name", "Saved Cards").put("bio", "").put("posts", list.length()).put("followers", -1).put("following", -1)
                .put("hl", JSONArray()).put("list", list)
            main.post { if (pfAi == -2) web.profile(j.toString()) }
        }
    }

    /** a song on about 80% of posts and stories: its index and "title · artist" (from the genres that are on) */
    private fun song(r: Random, o: JSONObject) {
        if (r.nextFloat() >= .8f) return
        val ok = rs.reels.songs.withIndex().filter { (_, s) -> !s.dead && s.genre !in rs.set.goff }; if (ok.isEmpty()) return
        val s = ok[r.nextInt(ok.size)].value; o.put("s", rs.reels.key(s)).put("st", rs.reels.key(s))   // a song is known by "title · artist" (indexes move when playlists change)
    }
    var profileOpen by mutableStateOf(false)

    private fun stories(b: Long): JSONArray {
        val out = JSONArray(); if (acc.isEmpty()) { groups = emptyList(); return out }
        val r = Random(seed * 131 + b)
        val pick = acc.indices.shuffled(r).take(12)
        val times = listOf("1m", "4m", "12m", "38m", "1h", "2h", "3h", "5h", "9h", "14h", "20h")
        val g = ArrayList<Int>()
        pick.forEach { ai ->
            val items = JSONArray(); repeat(1 + r.nextInt(3)) { card(r, acc[ai])?.let { (n, o, _) -> items.put(JSONObject().put("n", n).put("o", o).also { song(r, it) }) } }
            if (items.length() == 0) return@forEach
            g.add(ai)
            out.put(JSONObject().put("a", ai).put("t", times[r.nextInt(times.size)]).put("items", items).put("seen", "$b:${acc[ai].h}" in seen))
        }
        groups = g
        return out
    }
}
