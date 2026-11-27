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
    @JavascriptInterface fun pfOpen(open: Boolean) = h.post { f.profileOpen = open; if (!open) { f.stopSong(); f.backToReels() } }
    @JavascriptInterface fun saved() = h.post { f.savedProfile() }
    @JavascriptInterface fun save(id: String, o: Int, on: Boolean) = h.post { f.save(id.toLong(), o, on) }
    @JavascriptInterface fun hide(id: String, o: Int, text: String, path: String) = h.post { f.hide(id.toLong(), o, text, path) }
    @JavascriptInterface fun block(ai: Int) = h.post { f.block(ai) }
    @JavascriptInterface fun rects(j: String) = h.post { f.gotRects(j) }
    @JavascriptInterface fun follow(handle: String, on: Boolean) = h.post { f.follow(handle, on) }
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
    fun avatars(solid: Boolean) = js("R.avatars($solid)")   // Settings › Appearance › Profile style
    fun fake(v: Boolean) = js("R.fake($v)")
    /** a like's colour (#hex): the accent, or Instagram red */
    fun like(hex: String) = js("document.documentElement.style.setProperty('--like','$hex')")
    fun songsOn(j: String) = js("R.songsOn($j)")
    fun opts(source: Boolean, expl: Boolean, tags: Boolean) = js("R.opts($source,$expl,$tags)")
    fun savedSet(j: String) = js("R.savedSet($j)")
    fun replay() = js("R.replay()")
    fun profile(j: String) = js("R.profile($j)")
    fun openAccount(ai: Int, post: String) = js("R.openAccount($ai,$post)")
    fun pfShell(ai: Int) = js("R.pfShell($ai)")
    fun profilePosts(j: String) = js("R.pposts($j)")
    fun closeProfile() = js("R.closeProfile()")
    /** the app tour: where these controls are (App.rects), and the feed held still while it shows */
    fun rects(j: String) = js("R.rects($j)")
    fun tourLock(v: Boolean) = js("R.tourLock($v)")
    /** the hidden subjects (names) changed: posts and stories from them leave the page */
    fun blocked(j: String) = js("R.blocked($j)")
    /** the status and navigation bar heights (CSS px): the page runs behind the status bar and pads itself */
    fun insets(top: Float, bottom: Float) = js("R.insets($top,$bottom)")
}

/** the built-in Following feed filter's id (only the accounts followed) */
const val FOLLOW_ID = "following"
/** countdown.timer: its handle, and its entry in the hidden subjects when blocked */
const val CD_HANDLE = "countdown.timer"
const val CD_NAME = "Countdown timer"
const val AK_HANDLE = "daily.heatmap"
/** hydration.break: the same for the water reminder */
const val HY_HANDLE = "hydration.break"
const val HY_NAME = "Hydration break"

class Feed(val rs: Rs) {
    private var made: FeedWeb? = null
    val web: FeedWeb get() = made ?: FeedWeb(rs.ctx, this).also { made = it; it.theme(rs.set.theme, rs.dark); it.like(likeHex); if (accHex.isNotEmpty()) it.accent(accHex) }
    fun theme(t: String, dark: Boolean) { made?.theme(t, dark) }
    var accHex = ""   // the accent (#hex), for the exam tags' tint
        set(v) { field = v; made?.accent(v) }
    var likeHex = "#ff3040"
        set(v) { field = v; made?.like(v) }
    var storyOpen by mutableStateOf(false)

/** exam tags: NEET, AIIMS or INICET anywhere in a tag's full name */
private val EXAM = Regex("neet|aiims|inicet", RegexOption.IGNORE_CASE)

    /** an account: handle, icon, colour, the subjects it posts (its niche) and, for a subject's account given one deck, that deck (-1: any) */
    private class Acc(val h: String, val ic: String, val c: String, var src: Int = -1) { val subs = ArrayList<Int>() }
    private val bySrc = java.util.concurrent.ConcurrentHashMap<Long, List<Note>>()   // a subject's notes from one deck (or any), "subject * 4096 + deck + 1"
    /** a subject's notes for the feed: one deck's (src) or any, and only what the feed filter that's on lets through */
    private fun notesOf(d: Deck, s: Int, src: Int): List<Note> { val f = fsrc; val name = d.subjects[s].name
        if (src < 0 && f == null) return d.subjects[s].notes
        return bySrc.getOrPut(s * 4096L + src + 1) { d.subjects[s].notes.filter { (src < 0 || it.src == src) && (f == null || f.ok(it.src, name)) } } }

    /* ---- feed filters (Settings › Feed › Feed filters; a long press on Home picks one): named sets of decks; with one on, the feed's posts,
       stories and profiles come only from its decks. kept by deck folder id */
    class Filter(val id: String, val name: String, val decks: List<String>)
    var filters by mutableStateOf(loadFilters())
    var active by mutableStateOf(rs.store.get(FACTIVE))   // the filter that's on (null: none)
    var pickOpen by mutableStateOf(false)                 // the Home long press's picker
    /* ---- following (a profile's Follow button, kept by handle); the built-in Following filter shows only those accounts */
    var follows by mutableStateOf(runCatching { JSONArray(rs.store.get(FOLLOWING) ?: "[]").let { a -> List(a.length()) { a.getString(it) }.toSet() } }.getOrDefault(emptySet()))
    fun follow(h: String, on: Boolean) { follows = if (on) follows + h else follows - h; rs.store.set(FOLLOWING, JSONArray(follows.toList()).toString()) }
    private val followOnly get() = active == FOLLOW_ID
    @Volatile private var fsrc: Sel? = null               // what the filter that's on lets through (null: everything)
    /** what a filter lets through: whole decks (Note.src; filters made before subjects could be picked) and single subjects ("src/subject") */
    class Sel(val whole: Set<Int>, val subs: Set<String>) { fun ok(src: Int, subject: String) = src in whole || "$src/$subject" in subs }
    private fun loadFilters(): List<Filter> = runCatching { JSONArray(rs.store.get(FILTERS) ?: "[]").let { a -> List(a.length()) { a.getJSONObject(it).let { o ->
        Filter(o.getString("id"), o.optString("name"), o.optJSONArray("decks")?.let { d -> List(d.length()) { d.getString(it) } }.orEmpty()) } } } }.getOrDefault(emptyList())
    private fun saveFilters() { rs.store.set(FILTERS, JSONArray(filters.map { f -> JSONObject().put("id", f.id).put("name", f.name).put("decks", JSONArray(f.decks)) }).toString()) }
    /** what the active filter lets through in the loaded deck (null when none is on or nothing of it is still here). a filter's entries are
        "<deck id>/<subject>" (a subject: a first subdeck of that deck) or a bare "<deck id>" (the whole deck) */
    private fun filterSrcs(d: Deck): Sel? { val f = filters.find { it.id == active } ?: return null
        val idx = d.parts.withIndex().associate { it.value.id to it.index }; val whole = HashSet<Int>(); val subs = HashSet<String>()
        f.decks.forEach { k -> val i = idx[k.substringBefore('/')] ?: return@forEach; if ('/' in k) subs.add("$i/${k.substringAfter('/')}") else whole.add(i) }
        return if (whole.isEmpty() && subs.isEmpty()) null else Sel(whole, subs) }
    /** Settings › Reviewing › Feed filter in reels: whether a card's deck is in the filter that's on (always, with the option off or no filter) */
    fun reelsFilter(): ((Note) -> Boolean)? { if (!rs.set.reels || !rs.set.ffreels) return null
        if (followOnly) { val fo = follows; return { n -> acc.getOrNull(accIndex(n))?.h?.let { it in fo } ?: false } }   // Following: cards whose account is followed
        val f = filterSrcs(rs.deck) ?: return null
        return { n -> val name = subIx(n.id)?.let { rs.deck.subjects.getOrNull(it)?.name }; name == null || f.ok(n.src, name) } }
    fun putFilter(id: String?, name: String, decks: List<String>) {
        val nf = Filter(id ?: System.currentTimeMillis().toString(), name.trim().ifEmpty { "Filter" }, decks)
        filters = if (id != null && filters.any { it.id == id }) filters.map { if (it.id == id) nf else it } else filters + nf
        saveFilters(); if (active == nf.id) refilter()
    }
    fun dropFilter(id: String) { filters = filters.filter { it.id != id }; saveFilters(); if (active == id) useFilter(null) }
    fun useFilter(id: String?) { if (active == id) return; active = id; rs.store.set(FACTIVE, id); refilter() }
    /** the filter changed: the feed is made again (from the top) */
    private fun refilter() { if (made != null && deck != null) build() }
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
        val d = rs.deck; deck = d; page = 0; salt = System.currentTimeMillis(); subOf = null; fsrc = filterSrcs(d); bySrc.clear()
        seed = (d.meta?.imported ?: "").hashCode().toLong()
        val hs = rs.reels.handles; val sj = rs.reels.subj
        /* a subject named like one of the 19 MBBS subjects (first match, General Medicine last) gets that subject's accounts; subjects
           matching the same one share them. the rest share the general handles equally, as before */
        val byS = LinkedHashMap<Int, ArrayList<Int>>(); val rest = ArrayList<Int>()
        d.subjects.forEachIndexed { i, sb -> val g = sj.indexOfFirst { it.first.containsMatchIn(sb.name) }; if (g >= 0) byS.getOrPut(g) { ArrayList() }.add(i) else rest.add(i) }
        val l = ArrayList<Acc>()
        /* a subject in several decks: its accounts go to the decks first, one each (in deck order); the ones left post from any deck. with
           more decks than accounts, the last account stays on every deck so none is left without */
        byS.forEach { (g, subs) ->
            val hl = sj[g].second
            val srcs = subs.flatMap { s -> d.subjects[s].notes.asSequence().map { it.src }.distinct().toList() }.distinct().sorted()
            val ded = if (srcs.size <= 1) 0 else if (srcs.size <= hl.size) srcs.size else hl.size - 1
            hl.forEachIndexed { k, (h, ic) ->
                val a = Acc(h, ic, "")
                if (k < ded) { a.src = srcs[k]; a.subs.addAll(subs.filter { s -> d.subjects[s].notes.any { it.src == a.src } }) } else a.subs.addAll(subs)
                l.add(a)
            }
        }
        if (rest.isNotEmpty()) {
            val gen = hs.indices.shuffled(Random(seed)).map { Acc(hs[it].first, hs[it].second, "") }; val s = rest.size
            if (s <= gen.size) gen.forEachIndexed { i, a -> a.subs.add(rest[i % s]) } else rest.forEachIndexed { j, x -> gen[j % gen.size].subs.add(x) }
            l.addAll(gen)
        }
        acc = l.mapIndexed { i, a -> Acc(a.h, a.ic, hex(DCOL[i % DCOL.size].second.toArgb()), a.src).also { it.subs.addAll(a.subs) } }
        val b = slot()
        io.execute {
            val j = JSONObject().put("paths", JSONArray(d.paths)).put("source", rs.set.source).put("expl", rs.set.expl).put("tags", rs.set.tags)
                .put("acc", JSONArray(acc.map { a -> JSONObject().put("h", a.h).put("ic", a.ic).put("c", a.c) }))
                .put("posts", posts(0)).put("hcm", cms(Random(System.nanoTime()), AK_HANDLE)).put("groups", stories(b)).put("act", JSONObject(act())).put("saved", saved()).put("fol", JSONArray(follows.toList()))
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
    /* ---- the app tour's feed part: the page is asked where its controls are; they become root px for the overlay */
    private val TOUR_SEL = JSONObject().put("feed.arch", "#arch").put("feed.story", "#stories .f-st .f-ring").put("feed.heat", "#posts [data-i=\"h\"] .f-hmb")
        .put("feed.more", "#posts .f-post:not([data-i=\"h\"]) .f-more").put("feed.acc", "#posts .f-post:not([data-i=\"h\"]) .f-ph .f-pn b")
    fun askRects() { web.rects(TOUR_SEL.toString()) }
    fun gotRects(j: String) {
        val o = runCatching { JSONObject(j) }.getOrNull() ?: return; val d = rs.density; val at = rs.feedOrigin
        TOUR_SEL.keys().forEach { rs.tourRects.remove(it) }
        o.keys().forEach { k -> val a = o.getJSONArray(k)
            rs.tourRects[k] = androidx.compose.ui.geometry.Rect(at.x + a.getDouble(0).toFloat() * d, at.y + a.getDouble(1).toFloat() * d,
                at.x + (a.getDouble(0) + a.getDouble(2)).toFloat() * d, at.y + (a.getDouble(1) + a.getDouble(3)).toFloat() * d) }
        if (rs.tour == null && rs.tourClear() && o.length() > 0) rs.tour = "feed" else web.tourLock(false)
    }
    fun stopSong() { if (!rs.study) rs.reels.stop() }

    fun comments(handle: String) { rs.reels.name = handle; rs.reels.open("com") }
    /** a story was watched (by account: the page re-sorts the row, unwatched first) */
    fun seen(ai: Int) { (if (ai == -3) CD_HANDLE else acc.getOrNull(ai)?.h)?.let { seen.add("$bucket:$it") } }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    private val CAPS = listOf("high yield 🔥", "save this for exam day", "you WILL see this again", "tag someone who got this wrong", "remember this one",
        "this one's a classic", "pin this 📌", "read it twice", "no way you forgot this", "daily reminder")

    /** a random card from one of the account's niches: [note json, ordinal, subject] */
    private var lastMcq = false   // whether card() just picked an MCQ (carousels never have one)
    private fun card(r: Random, a: Acc, noMcq: Boolean = false, sub: Int = -1): Triple<JSONArray, Int, Int>? {
        val d = deck ?: return null
        val live = a.subs.filter { !subHidden(it) && notesOf(d, it, a.src).isNotEmpty() }   // never a hidden subject, nor one with nothing in the filter
        val s = (if (sub >= 0) sub.takeIf { !subHidden(it) } else live.getOrNull(r.nextInt(maxOf(1, live.size)))) ?: return null
        val notes = notesOf(d, s, a.src)   // an account given one deck posts only that deck's cards
        if (notes.isEmpty()) return null
        var n: Note; var o: Int; var k = 0
        do { n = notes[r.nextInt(notes.size)]; o = n.ords[r.nextInt(n.ords.size)] } while ((isHidden(n.id, o) || noMcq && n.mcq) && ++k < 12)   // never a hidden card
        if (isHidden(n.id, o) || noMcq && n.mcq) return null
        lastMcq = n.mcq
        return Triple(JSONArray(DeckStore.noteJson(rs.ctx, n)), o, s)
    }

    /** one post: a random card from account ai's niche, with counts, a caption from the card's tags and two comments */
    private fun post(r: Random, ai: Int): JSONObject? {
        val (n, o, _) = card(r, acc[ai]) ?: return null; val mcq = lastMcq
        val tags = n.optJSONArray(5)?.let { t -> (0 until t.length()).map { t.getString(it).split("::").last().replace(Regex("[^\\p{L}\\p{N}_]"), "") } }.orEmpty().filter { it.isNotEmpty() }
        val hash = tags.take(3).joinToString(" ") { "#" + it.lowercase() }   // the card's own tags; no deck paths in the feed
        /* exam tags (NEET, AIIMS anywhere in the full tag): their hashtags get a slight accent tint */
        val ex = n.optJSONArray(5)?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty().filter { EXAM.containsMatchIn(it) }
            .map { "#" + it.split("::").last().replace(Regex("[^\\p{L}\\p{N}_]"), "").lowercase() }
        return JSONObject().put("a", ai).put("n", n).put("o", o).put("likes", r.nextInt(120, 98000)).put("coms", r.nextInt(4, 2400))
            .put("cap", (CAPS[r.nextInt(CAPS.size)] + " " + hash).trim()).put("ex", JSONArray(ex))
            .put("cm", cms(r, acc[ai].h))
            .put("t", listOf("2 minutes ago", "18 minutes ago", "1 hour ago", "3 hours ago", "7 hours ago", "yesterday", "2 days ago")[r.nextInt(7)])
            .also { song(r, it) }
            .also { o ->   /* a carousel: 10% of posts have 3 cards, another 23% have 2 (the page drops it if a card would need 'more'); never with MCQs */
                val roll = r.nextFloat(); val extra = if (roll < .10f) 2 else if (roll < .33f) 1 else 0
                if (extra > 0 && !mcq) { val m = JSONArray(); repeat(extra) { card(r, acc[ai], noMcq = true)?.let { (n2, o2, _) -> m.put(JSONObject().put("n", n2).put("o", o2)) } }; if (m.length() > 0) o.put("more", m) }
            }
    }

    private fun posts(p: Int): JSONArray {
        val out = JSONArray(); if (acc.isEmpty()) return out
        val r = Random(seed * 31 + salt + p * 7919L)
        val d = deck ?: return out
        val fo = follows; val fol = followOnly
        val live = acc.indices.filter { ai -> (!fol || acc[ai].h in fo) && acc[ai].subs.any { !subHidden(it) && notesOf(d, it, acc[ai].src).isNotEmpty() } }   // a blocked account (or one outside the filter) doesn't post
        if (live.isEmpty()) return out
        repeat(8) { val roll = r.nextFloat()   // 1% countdown.timer, 1% hydration.break
            ((if (roll < .01f && (!fol || CD_HANDLE in fo)) countdown(r) else if (roll >= .01f && roll < .02f && (!fol || HY_HANDLE in fo)) hydration(r) else null) ?: post(r, live[r.nextInt(live.size)]))?.let { out.put(it) } }
        return out
    }

    /* ---- countdown.timer (account -3): about 1 post in 100 is the days left to the nearest NEET-PG or INICET. blockable like an
       account (its entry in the hidden subjects is CD_NAME). dates: INICET (July 2027 session) per the published notice; NEET-PG
       2027 isn't announced yet, the end of August is the expected date (update these when NBEMS / AIIMS publish them) */
    private val EXAMS = listOf("2027-05-22" to "INICET", "2027-08-29" to "NEET-PG", "2027-11-07" to "INICET")
    /** the exams still to come, nearest first: (days left, name). named with the year, INICET by its session: "NEET-PG 2027", "INICET-May 2027" */
    private fun upcoming(): List<Pair<Long, String>> { val today = java.time.LocalDate.now()
        return EXAMS.map { java.time.LocalDate.parse(it.first) to it.second }.filter { it.first.isAfter(today) }.sortedBy { it.first }
            .map { (d, ex) -> java.time.temporal.ChronoUnit.DAYS.between(today, d) to
                if (ex == "INICET") "INICET-${if (d.monthValue <= 7) "May" else "Nov"} ${d.year}" else "$ex ${d.year}" } }
    private fun countdown(r: Random): JSONObject? {
        if (CD_NAME in hiddenNames()) return null
        val (days, ex) = upcoming().firstOrNull() ?: return null
        return cdPost(r, days, ex, "today")
    }
    private fun cdPost(r: Random, days: Long, ex: String, t: String) =
        JSONObject().put("a", -3).put("cd", days).put("exam", ex).put("likes", r.nextInt(400, 60000)).put("coms", r.nextInt(10, 1800))
            .put("cap", listOf("it's closer than you think ⏳", "the clock doesn't stop", "one more card today", "make every day count")[r.nextInt(4)] + " #" + ex.substringBefore(' ').lowercase().replace("-", "").removeSuffix("may").removeSuffix("nov") + " #countdown")
            .put("cm", cms(r, CD_HANDLE)).put("t", t).also { song(r, it) }
    /* ---- hydration.break (account -4): about 1 post in 100 is a reminder to drink water; its profile has just that one post */
    private fun hydration(r: Random): JSONObject? {
        if (HY_NAME in hiddenNames()) return null
        return JSONObject().put("a", -4).put("hy", 1).put("likes", r.nextInt(800, 90000)).put("coms", r.nextInt(20, 2400))
            .put("cap", listOf("a gentle reminder 💧", "your kidneys will thank you", "cards can wait 30 seconds", "sip sip 💧")[r.nextInt(4)] + " #hydrate #selfcare")
            .put("cm", cms(r, HY_HANDLE)).put("t", "just now").also { song(r, it) }
    }
    /** two comments for a post, from the account's own comments (the special accounts) or everyone's */
    private fun cms(r: Random, handle: String = ""): JSONArray { val reels = rs.reels; val pool = reels.commentsFor(handle)
        return JSONArray(List(2) { JSONArray().put(reels.names[r.nextInt(reels.names.size)] + reels.suffixes[r.nextInt(reels.suffixes.size)]).put(pool[r.nextInt(pool.size)]) }) }
    /** the special accounts' profiles: countdown.timer has a post per exam still to come (3), hydration.break the one reminder */
    private fun specialProfile(ai: Int) {
        pfAi = ai; pfLeft = 0
        val r = Random(System.nanoTime()); val st = Random(seed * 977 + ai)
        val cd = ai == -3
        val list = JSONArray()
        if (cd) upcoming().take(3).forEachIndexed { i, (days, ex) -> list.put(cdPost(r, days, ex, listOf("today", "2 days ago", "last week")[i])) }
        else hydration(r)?.let { list.put(it) } ?: list.put(JSONObject().put("a", -4).put("hy", 1).put("likes", r.nextInt(800, 90000)).put("coms", r.nextInt(20, 2400)).put("cap", "").put("cm", JSONArray()).put("t", ""))
        val j = JSONObject().put("a", ai).put("name", if (cd) "Countdown Timer" else "Hydration Break")
            .put("bio", if (cd) "every day counts ⏳ NEET-PG · INICET" else "your reminder to drink water 💧")
            .put("posts", list.length()).put("followers", st.nextInt(40_000, 900_000)).put("following", if (cd) 2 else 1)
            .put("hl", JSONArray()).put("list", list)
        web.profile(j.toString())
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

    /* ---- Reels uses the feed's accounts: a card shows the account that posts its subject, and tapping it opens that profile here */
    private var subOf: Map<Long, Int>? = null
    private var subOfD: Deck? = null
    /** a note's subject (an index into the deck's subjects) */
    private fun subIx(id: Long): Int? {
        val d = deck ?: rs.deck
        val m = subOf?.takeIf { subOfD === d } ?: HashMap<Long, Int>().also { m -> d.subjects.forEachIndexed { i, s -> s.notes.forEach { m[it.id] = i } } }.also { subOf = it; subOfD = d }
        return m[id]
    }
    private fun accIndex(n: Note): Int {
        val s = subIx(n.id) ?: return -1
        /* any of the accounts that post this subject, picked by the card (so a card always shows the same one, and its profile matches) */
        val l = acc.indices.filter { s in acc[it].subs && (acc[it].src < 0 || acc[it].src == n.src) }; if (l.isEmpty()) return -1   // not another deck's account
        return l[Math.floorMod(n.id.hashCode(), l.size)]
    }
    /** the feed account for a card: handle, icon, colour (#hex); null before the feed is built */
    fun accountOf(n: Note): Triple<String, String, String>? = acc.getOrNull(accIndex(n))?.let { Triple(it.h, it.ic, it.c) }
    /** from Reels: home to the feed, with that card's account open (the card as its latest post) */
    /** the profile was opened from Reels: closing it (back) returns there */
    private var fromReels = false
    fun backToReels() { if (fromReels) { fromReels = false; if (rs.S != null && rs.cur != null) { rs.study = true; rs.reels.onCard() } } }
    fun openFromReels(n: Note, o: Int) {
        val ai = accIndex(n); if (ai < 0) return
        /* the profile is up (its name and a spinner, no slide) before the feed shows, so the feed never flashes; it fills in when made */
        web.pfShell(ai); fromReels = true
        main.post { rs.home(); rs.tab = "feed" }
        io.execute {
            val r = Random(System.nanoTime())
            val p = JSONObject().put("a", ai).put("n", JSONArray(DeckStore.noteJson(rs.ctx, n))).put("o", o).put("likes", r.nextInt(120, 98000)).put("coms", r.nextInt(4, 2400))
                .put("cap", CAPS[r.nextInt(CAPS.size)])
                .put("cm", cms(r, acc[ai].h))
                .put("t", "just now")
            main.post { web.openAccount(ai, p.toString()) }
        }
    }
    fun profile(ai: Int) {
        if (ai == -3 || ai == -4) return specialProfile(ai)
        if (ai !in acc.indices) return
        val d = deck ?: return; pfAi = ai
        io.execute {
            val a = acc[ai]; val st = Random(seed * 977 + ai); val r = Random(System.nanoTime())
            val subs = a.subs.map { d.subjects[it] }
            val kids = subs.flatMap { s -> d.top.find { it.name == s.name }?.let { n -> if (n.kids.isEmpty()) listOf(n) else n.kids } ?: emptyList() }.shuffled(r)
            val icons = rs.reels.handles.map { it.second }.distinct()
            val hl = JSONArray()
            kids.take(3 + r.nextInt(4)).forEach { k ->
                val f = fsrc; val notes = d.notesFor(k).filter { (a.src < 0 || it.src == a.src) && (f == null || f.ok(it.src, k.segs[0])) }; if (notes.isEmpty()) return@forEach
                val items = JSONArray()
                repeat(2 + r.nextInt(4)) { val n = notes[r.nextInt(notes.size)]; val o = n.ords[r.nextInt(n.ords.size)]; if (isHidden(n.id, o) || n.mcq) return@repeat   // highlights are stories: no MCQs
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
        return "$id:$o" in k || subIx(id)?.let { subHidden(it) } == true   // a hidden subject hides all its cards
    }
    fun hidden(): List<Hidden> = hiddenList().let { a -> (0 until a.length()).map { a.getJSONArray(it).let { e -> Hidden(e.getLong(0), e.getInt(1), e.optString(2), e.optString(3)) } } }
    private fun setHidden(l: List<JSONArray>) { rs.store.set(HIDDEN, JSONArray(l).toString()); hiddenKeys = null; hiddenTick++ }
    fun hide(id: Long, o: Int, text: String, path: String) {
        val a = hiddenList(); val l = (0 until a.length()).map { a.getJSONArray(it) }.filter { !(it.getLong(0) == id && it.getInt(1) == o) }.toMutableList()
        l.add(0, JSONArray().put(id).put(o).put(text).put(path)); setHidden(l)
    }
    fun unhide(id: Long, o: Int) { val a = hiddenList(); setHidden((0 until a.length()).map { a.getJSONArray(it) }.filter { !(it.getLong(0) == id && it.getInt(1) == o) }) }
    /* ---- hidden subjects (Block on an account: a profile's ⋯, a post's or a reel's menu): every subject the account posts is hidden,
       which works like hiding each of its cards. kept by name; listed in Settings › Feed › Hidden subjects */
    class HiddenSubj(val name: String, val by: String, val cards: Int)
    private fun subjList(): JSONArray = runCatching { JSONArray(rs.store.get(HIDSUB) ?: "[]") }.getOrDefault(JSONArray())
    @Volatile private var subjNames: Set<String>? = null
    private fun hiddenNames(): Set<String> = subjNames ?: subjList().let { a -> (0 until a.length()).map { a.getJSONArray(it).getString(0) }.toSet() }.also { subjNames = it }
    fun subHidden(s: Int): Boolean { val d = deck ?: rs.deck; return d.subjects.getOrNull(s)?.name?.let { it in hiddenNames() } == true }
    fun hiddenSubjects(): List<HiddenSubj> = subjList().let { a -> (0 until a.length()).map { a.getJSONArray(it).let { e ->
        HiddenSubj(e.getString(0), e.optString(1), rs.deck.subjects.find { it.name == e.getString(0) }?.notes?.sumOf { it.ords.size } ?: 0) } } }
    private fun setSubj(l: List<JSONArray>) {
        rs.store.set(HIDSUB, JSONArray(l).toString()); subjNames = null; hiddenKeys = null; hiddenTick++
        made?.blocked(JSONArray(hiddenNames().toList()).toString())
    }
    private fun hideSubjects(names: List<String>, by: String) {
        val a = subjList(); val l = (0 until a.length()).map { a.getJSONArray(it) }.filter { it.getString(0) !in names }.toMutableList()
        names.reversed().forEach { l.add(0, JSONArray().put(it).put(by)) }; setSubj(l)
    }
    /** Block: the account's subjects are hidden */
    fun block(ai: Int) { if (ai == -3) return hideSubjects(listOf(CD_NAME), CD_HANDLE); if (ai == -4) return hideSubjects(listOf(HY_NAME), HY_HANDLE); val a = acc.getOrNull(ai) ?: return; val d = deck ?: return; hideSubjects(a.subs.mapNotNull { d.subjects.getOrNull(it)?.name }, a.h) }
    /** Block from Reels: the card's account (or, before the feed exists, just the card's subject) */
    fun blockNote(n: Note) {
        val ai = accIndex(n); if (ai >= 0) return block(ai)
        subIx(n.id)?.let { rs.deck.subjects.getOrNull(it) }?.let { hideSubjects(listOf(it.name), "") }
    }
    fun unhideSubject(name: String) { val a = subjList(); setSubj((0 until a.length()).map { a.getJSONArray(it) }.filter { it.getString(0) != name }) }
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
        if (r.nextFloat() >= .93f) return   // 7% of posts and stories have no song
        val ok = rs.reels.songs.withIndex().filter { (_, s) -> !s.dead && s.genre !in rs.set.goff }; if (ok.isEmpty()) return
        val s = ok[r.nextInt(ok.size)].value; o.put("s", rs.reels.key(s)).put("st", rs.reels.key(s))   // a song is known by "title · artist" (indexes move when playlists change)
    }
    var profileOpen by mutableStateOf(false)

    private fun stories(b: Long): JSONArray {
        val out = JSONArray(); if (acc.isEmpty()) { groups = emptyList(); return out }
        val r = Random(seed * 131 + b)
        /* every subject on the home page has a story: one of its accounts each (its cards from that subject), then random accounts up to 12 */
        val fo = follows; val fol = followOnly
        val sh = acc.indices.shuffled(r).filter { !fol || acc[it].h in fo }; val pick = LinkedHashMap<Int, Int>()   // Following: only followed accounts   // account → the subject its story is drawn from (-1: any of its niche)
        deck?.subjects?.indices?.shuffled(r)?.forEach { s -> if (notesOf(deck!!, s, -1).isNotEmpty() && !subHidden(s) && pick.values.none { it == s }) sh.firstOrNull { it !in pick && s in acc[it].subs }?.let { pick[it] = s } }
        sh.forEach { if (pick.size < 12 && it !in pick) pick[it] = -1 }
        val times = listOf("1m", "4m", "12m", "38m", "1h", "2h", "3h", "5h", "9h", "14h", "20h")
        val g = ArrayList<Int>()
        pick.keys.shuffled(r).forEach { ai -> val sub = pick[ai] ?: -1
            val items = JSONArray(); repeat(1 + r.nextInt(3)) { card(r, acc[ai], noMcq = true, sub = sub)?.let { (n, o, _) -> items.put(JSONObject().put("n", n).put("o", o).also { song(r, it) }) } }
            if (items.length() == 0) return@forEach
            g.add(ai)
            out.put(JSONObject().put("a", ai).put("t", times[r.nextInt(times.size)]).put("items", items).put("seen", "$b:${acc[ai].h}" in seen))
        }
        /* countdown.timer's story: always there (unless blocked), at the back of the row; the page puts the watched ones after it */
        if (CD_NAME !in hiddenNames() && (!fol || CD_HANDLE in fo)) upcoming().take(3).takeIf { it.isNotEmpty() }?.let { l ->
            out.put(JSONObject().put("a", -3).put("t", "1h").put("items", JSONArray(l.map { (days, ex) -> JSONObject().put("cd", days).put("exam", ex).also { song(r, it) } }))
                .put("seen", "$b:$CD_HANDLE" in seen))
        }
        groups = g
        return out
    }
}
