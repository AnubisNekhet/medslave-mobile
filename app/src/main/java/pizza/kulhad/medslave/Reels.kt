package pizza.kulhad.medslave

import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import pizza.kulhad.medslave.ui.DCOL
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/* Reels Mode (Settings › Appearance): a joke overlay on the review screen, as on the site. Nothing here is saved or sent anywhere.
   A made-up account per card, likes, comments (a sheet of mean comments), Repost (= missed it), share (made-up contacts), save (into saved.cards, shared with the feed) and a
   30-second Apple Music preview per card on loop. The data (reels.json) is the site's reels.js lists. */
class Song(val title: String, val artist: String, var url: String, val art: String, val genre: String) { var dead = false; var re = false }
class Comment(val user: String, val time: String, val text: String, likes: Int, val color: Color) { var likes by mutableIntStateOf(likes); var liked by mutableStateOf(false) }
class Burst(val x: Float, val y: Float, val r: Float, val id: Long)

class Reels(private val rs: Rs) {
    private val j by lazy { JSONObject(rs.ctx.assets.open("reels.json").use { String(it.readBytes()) }) }
    private fun strs(k: String) = j.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }
    private val ico by lazy { j.getJSONObject("ico") }
    /** the MBBS subjects' own accounts, in match order (General Medicine last): a deck subject whose name matches re gets these handles */
    val subj by lazy { j.getJSONArray("subj").let { a -> List(a.length()) { a.getJSONObject(it).let { o -> Regex(o.getString("re"), RegexOption.IGNORE_CASE) to o.getJSONArray("handles").let { h -> List(h.length()) { h.getJSONArray(it).let { x -> x.getString(0) to ico.optString(x.getString(1)) } } } } } } }
    val handles by lazy { j.getJSONArray("handles").let { a -> List(a.length()) { a.getJSONArray(it).let { h -> h.getString(0) to ico.optString(h.getString(1)) } } } }
    private val base by lazy { j.getJSONArray("songs").let { a -> List(a.length()) { a.getJSONArray(it).let { s -> Song(s.getString(0), s.getString(1), s.getString(2), s.getString(3), s.getString(4)) } } } }
    /** every song: the built-in ones, then the user's playlists' (genre "pl:<name>"); the user's are appended so indexes stay put */
    val songs: List<Song> get() = base + lists.flatMap { it.songs }

    /* ---- the user's playlists (Settings: the music sheet). made from pasted "Song – Artist" lines or a public Spotify / Apple Music /
       YouTube playlist link: the track names are read, then each is matched to its 30-second Apple preview, a couple of seconds apart
       (Apple's search allows about 20 lookups a minute). kept under medslave:playlists as [{name, songs: [[title, artist, url, art]]}];
       switched off like a genre (its key "pl:<name>" in goff) */
    inner class Playlist(name: String) { var name by mutableStateOf(name); val songs = mutableStateListOf<Song>(); var status by mutableStateOf(""); val key get() = "pl:$name"
        var added = System.currentTimeMillis(); var lastOn = 0L
        /** its cover: the covers of the first 4 albums loaded into it ([album id, artwork url]), each album once; set only as albums are added */
        val covers = mutableStateListOf<Pair<String, String>>()
        fun cover(a: Album) { if (a.cover.isNotEmpty() && covers.size < 4 && covers.none { it.first == a.id }) covers.add(a.id to a.cover) } }
    /** an album read from Apple: its songs, and what the playlist cover needs */
    class Album(val name: String, val id: String, val cover: String, val songs: List<Song>)   // lastOn: when it was last seen switched on (its songs' cache is kept until a week after)
    fun key(s: Song) = "${s.title} · ${s.artist}"
    fun byKey(k: String) = songs.firstOrNull { key(it) == k && !it.dead }
    private fun changed() = genreChanged()
    val lists = mutableStateListOf<Playlist>()
    private fun loadLists() = runCatching {
        val a = JSONArray(rs.store.get(PLAYLISTS) ?: "[]")
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); val p = Playlist(o.getString("name")); val ss = o.getJSONArray("songs"); p.added = o.optLong("added", System.currentTimeMillis()); p.lastOn = o.optLong("on", 0L)
            o.optJSONArray("covers")?.let { c -> for (k in 0 until c.length()) c.getJSONArray(k).let { p.covers.add(it.getString(0) to it.getString(1)) } }
            for (k in 0 until ss.length()) ss.getJSONArray(k).let { p.songs.add(Song(it.getString(0), it.getString(1), it.getString(2), it.getString(3), p.key)) }
            lists.add(p) }
    }
    private fun saveLists() = rs.store.set(PLAYLISTS, JSONArray(lists.map { p -> JSONObject().put("name", p.name).put("added", p.added).put("on", p.lastOn).put("covers", JSONArray(p.covers.map { JSONArray().put(it.first).put(it.second) })).put("songs", JSONArray(p.songs.map { JSONArray().put(it.title).put(it.artist).put(it.url).put(it.art) })) }).toString())
    /** notes which playlists are switched on now (called as the app opens and before a switch changes) */
    fun markLists() { val now = System.currentTimeMillis(); var ch = false; lists.forEach { if (it.key !in rs.set.goff) { it.lastOn = now; ch = true } }; if (ch) saveLists() }
    fun removePlaylist(p: Playlist) { lists.remove(p); rs.set.goff = rs.set.goff - p.key; rs.set.save(); saveLists(); changed() }
    /** editing (a long press on the playlist): a new name, the found songs still kept (the pills), and any new lines or a link to add.
        the removed ones go at once; the new ones are looked up and added after them as they're found */
    fun editPlaylist(p: Playlist, name0: String, kept: List<Song>, text: String) {
        val nn = name0.trim().ifEmpty { p.name }.let { n -> var x = n; var k = 2; while (lists.any { it !== p && it.name == x }) x = "$n ${k++}"; x }
        val old = p.key; p.name = nn
        if (old != p.key && old in rs.set.goff) { rs.set.goff = rs.set.goff - old + p.key; rs.set.save() }
        val ks = kept.map { Song(it.title, it.artist, it.url, it.art, p.key) }
        p.songs.clear(); p.songs.addAll(ks); saveLists(); changed()
        val t = text.trim(); if (t.isEmpty()) { p.status = if (p.songs.isEmpty()) "No previews found" else ""; return }
        p.status = "Reading the list…"
        val have = ks.map { key(it).lowercase() }.toMutableSet()
        fun add(s: Song) { if (have.add(key(s).lowercase())) p.songs.add(s) }
        thread {
            val link = Regex("https?://").containsMatchIn(t)
            val al = if (link) runCatching { linkSongs(t, p.key) }.getOrNull() else null
            if (al != null && al.any { it.songs.isNotEmpty() }) { h.post { al.forEach { a -> p.cover(a); a.songs.forEach(::add) }; p.status = ""; saveLists(); changed() }; return@thread }
            val tracks = runCatching { tracksOf(t) }.getOrDefault(emptyList())
            if (tracks.isEmpty()) { h.post { p.status = if (p.songs.isEmpty()) "Couldn't read any songs from that" else "" }; return@thread }
            tracks.forEachIndexed { i, (ti, ar) ->
                h.post { p.status = "Finding songs… ${i + 1} / ${tracks.size}" }
                var hit = lookup("$ti $ar"); if (hit == null) { Thread.sleep(60_000); hit = lookup("$ti $ar") }
                hit?.let { (tt, aa, url, art) -> h.post { add(Song(tt, aa, url, art, p.key)); saveLists() } }
                Thread.sleep(2600)
            }
            h.post { p.status = if (p.songs.isEmpty()) "No previews found" else ""; saveLists(); changed() }
        }
    }
    fun addPlaylist(name0: String, text: String) {
        val name = name0.trim().ifEmpty { "My playlist" }.let { n -> var x = n; var k = 2; while (lists.any { it.name == x }) x = "$n ${k++}"; x }
        val p = Playlist(name); p.status = "Reading the list…"; lists.add(p)
        thread {
            /* an album link: its songs straight from Apple, all at once */
            val al = runCatching { linkSongs(text.trim(), p.key) }.getOrNull()
            val albumName = al?.firstOrNull { it.songs.isNotEmpty() }?.name.orEmpty()
            if (al != null && al.any { it.songs.isNotEmpty() }) { h.post { al.forEach { p.cover(it) }
                /* no name typed: the playlist takes the album's name */
                if (name0.isBlank() && albumName.isNotBlank()) p.name = albumName.trim().let { n -> var x = n; var k = 2; while (lists.any { it !== p && it.name == x }) x = "$n ${k++}"; x }
                p.songs.addAll(al.flatMap { it.songs }.distinctBy { key(it).lowercase() }.map { Song(it.title, it.artist, it.url, it.art, p.key) }); p.status = ""; saveLists(); changed() }; return@thread }
            val tracks = runCatching { tracksOf(text.trim()) }.getOrDefault(emptyList())
            if (tracks.isEmpty()) { h.post { p.status = "Couldn't read any songs from that" }; return@thread }
            tracks.forEachIndexed { i, (t, ar) ->
                h.post { p.status = "Finding songs… ${i + 1} / ${tracks.size}" }
                var hit = lookup("$t $ar")
                if (hit == null) { Thread.sleep(60_000); hit = lookup("$t $ar") }   // rate-limited: wait a minute, try once more
                hit?.let { (tt, aa, url, art) -> h.post { p.songs.add(Song(tt, aa, url, art, p.key)); saveLists() } }
                Thread.sleep(2600)
            }
            h.post { p.status = if (p.songs.isEmpty()) "No previews found" else ""; saveLists(); changed() }
        }
    }
    /** a 30-second Apple preview for "title artist": [title, artist, preview, artwork] */
    private fun lookup(q: String): List<String>? = runCatching {
        val c = URL("https://itunes.apple.com/search?entity=song&limit=8&term=" + URLEncoder.encode(q.take(120), "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        if (c.responseCode != 200) return null
        val r = JSONObject(c.inputStream.use { String(it.readBytes()) }).getJSONArray("results")
        uncut((0 until r.length()).map { r.getJSONObject(it) })?.let { listOf(it.optString("trackName"), it.optString("artistName"), it.getString("previewUrl"), it.optString("artworkUrl100")) }
    }.getOrNull()
    private fun get(u: String) = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 10000; readTimeout = 15000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"); setRequestProperty("Accept-Language", "en") }.inputStream.use { String(it.readBytes()) }
    /** a Spotify playlist or album as its public embed page describes it: name, subtitle (the artist), trackList */
    private fun spotifyEntity(kind: String, id: String): JSONObject? {
        val html = get("https://open.spotify.com/embed/$kind/$id")
        val js = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: return null
        return JSONObject(js).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state").getJSONObject("data").getJSONObject("entity")
    }
    /** Apple lists clean (censored) editions beside the originals: 1 for a clean one, so sorting by this puts the uncensored first */
    /** Apple's best match with a preview, or the uncensored edition of that same song (same title and artist) when the best is the clean one */
    private fun uncut(l: List<JSONObject>): JSONObject? {
        val p = l.filter { it.has("previewUrl") }; val top = p.firstOrNull() ?: return null
        fun id(x: JSONObject) = (x.optString("trackName") + "|" + x.optString("artistName")).lowercase()
        return if (clean(top) == 1) p.firstOrNull { id(it) == id(top) && clean(it) == 0 } ?: top else top
    }
    private fun clean(x: JSONObject) = if (x.optString("trackExplicitness", x.optString("collectionExplicitness")) == "cleaned") 1 else 0
    /** every album and song link in the text, each straight from Apple (as albumSongs / songOf): null when there are none, so the text is
        read as a playlist or lines instead. a song link brings that one song, and its album's cover for the playlist like an album does */
    private fun linkSongs(t: String, key: String): List<Album>? {
        val links = Regex("https?://\\S+").findAll(t).map { it.value }.filter { Regex("music\\.apple\\.com/\\w\\w/(album|song)/|open\\.spotify\\.com/(?:intl-\\w+/)?(album|track)/").containsMatchIn(it) }.toList()
        if (links.isEmpty()) return null
        return links.mapNotNull { u -> runCatching { songOf(u, key) ?: albumSongs(u, key) }.getOrNull() }
    }
    /** a song link: an Apple Music song (…/song/…/id, or an album link with ?i=id) looked up by its id, a Spotify track matched to Apple by
        its title and artist. an Album of that one song (its album's id and cover). null: not a song link, or nothing on Apple matches */
    private fun songOf(u: String, key: String): Album? {
        fun json(q: String) = JSONObject(get(q)).getJSONArray("results").let { r -> (0 until r.length()).map { r.getJSONObject(it) } }
        fun album(x: JSONObject) = Album(x.optString("trackName"), x.optLong("collectionId").toString(), x.optString("artworkUrl100").replace("100x100bb", "300x300bb"),
            listOf(Song(x.optString("trackName"), x.optString("artistName"), x.getString("previewUrl"), x.optString("artworkUrl100"), key)))
        val am = Regex("music\\.apple\\.com/(\\w\\w)/(?:song/(?:[^/?\\s]+/)?(\\d+)|album/[^?\\s]*\\?(?:[^\\s]*&)?i=(\\d+))").find(u)
        if (am != null) { val id = am.groupValues[2].ifEmpty { am.groupValues[3] }
            return json("https://itunes.apple.com/lookup?id=$id&country=${am.groupValues[1]}").firstOrNull { it.optString("wrapperType") == "track" && it.has("previewUrl") }?.let(::album) }
        val sp = Regex("open\\.spotify\\.com/(?:intl-\\w+/)?track/(\\w+)").find(u) ?: return null
        val e = spotifyEntity("track", sp.groupValues[1]) ?: return null
        val name = e.optString("name").ifEmpty { e.optString("title") }
        val artist = e.optString("subtitle").ifEmpty { e.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty() }
        val ms = e.optLong("duration", 0L)   // Spotify's length (ms), when the embed gives it
        /* the same song on Apple, matched as closely as an Apple link's id: the title (without "feat. …", bracketed or " - Remastered"-style
           parts) and an artist that overlaps; the length within 3 s when known; the original before the clean edit. searched by the first
           artist and the title, then the whole artist line, then the title alone, until one matches */
        fun norm(s: String) = s.lowercase().replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").replace(Regex("\\s+(feat|ft)\\.?\\s.*$"), "").replace(Regex(" - .*$"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
        val nn = norm(name); val arts = artist.split(",", "&").map { norm(it) }.filter { it.isNotEmpty() }; val a0 = artist.split(",", "&").first().trim()
        fun artOk(x: JSONObject) = norm(x.optString("artistName")).let { a -> a.isNotEmpty() && arts.any { it == a || it in a || a in it } }
        fun titleOk(x: JSONObject) = norm(x.optString("trackName")).let { t -> t == nn || (t.length > 3 && nn.length > 3 && (t in nn || nn in t)) }
        fun lenOk(x: JSONObject) = ms <= 0 || x.optLong("trackTimeMillis", 0L).let { it <= 0 || kotlin.math.abs(it - ms) <= 3000 }
        var first: List<JSONObject>? = null
        for (q in listOf("$a0 $name", "$artist $name", name).distinct()) {
            val hits = json("https://itunes.apple.com/search?entity=song&limit=20&term=" + URLEncoder.encode(q.take(120), "UTF-8")).filter { it.has("previewUrl") }.sortedBy(::clean)
            if (first == null) first = hits
            (hits.firstOrNull { norm(it.optString("trackName")) == nn && artOk(it) && lenOk(it) } ?: hits.firstOrNull { titleOk(it) && artOk(it) && lenOk(it) }
                ?: hits.firstOrNull { titleOk(it) && artOk(it) })?.let { return album(it) }
        }
        /* an artist spelt differently on each (transliterated names): the exact title, its length matching when known */
        return first?.firstOrNull { norm(it.optString("trackName")) == nn && (ms > 0 && lenOk(it)) }?.let(::album)
    }
    /** an album link's songs, straight from Apple in one request: an Apple Music album as it is, a Spotify album matched to Apple by its
        name, artist and track count. null: not an album link, or no Apple album clearly matches (the songs are then searched one by one) */
    private fun albumSongs(t: String, key: String): Album? {
        val am = Regex("music\\.apple\\.com/(\\w\\w)/album/(?:[^/?\\s]+/)?(\\d+)").find(t)
        if (am != null) return appleAlbum(am.groupValues[2], am.groupValues[1], key)
        val sp = Regex("open\\.spotify\\.com/(?:intl-\\w+/)?album/(\\w+)").find(t) ?: return null
        val e = spotifyEntity("album", sp.groupValues[1]) ?: return null
        val name = e.optString("name"); val artist = e.optString("subtitle"); val n = e.optJSONArray("trackList")?.length() ?: 0
        fun norm(s: String) = s.lowercase().replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").replace(Regex(" - (single|ep)$"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
        val nn = norm(name); val na = norm(artist)
        fun pick(r: JSONArray) = (0 until r.length()).map { r.getJSONObject(it) }
            .filter { it.has("collectionId") && norm(it.optString("collectionName")) == nn && norm(it.optString("artistName")).let { a -> a.isNotEmpty() && (a == na || a in na || na in a) } }
            .maxByOrNull { (if (it.optInt("trackCount") == n) 2 else 0) + (if (clean(it) == 0) 1 else 0) }   // the right length, then not the clean edit
        fun json(u: String) = JSONObject(get(u)).getJSONArray("results")
        /* Apple's album search first; it misses some albums, so then the artist's own album list */
        val best = pick(json("https://itunes.apple.com/search?entity=album&limit=15&term=" + URLEncoder.encode("$artist $name".take(120), "UTF-8")))
            ?: json("https://itunes.apple.com/search?entity=musicArtist&limit=1&term=" + URLEncoder.encode(artist.split(",").first().trim().take(80), "UTF-8"))
                .optJSONObject(0)?.optLong("artistId")?.let { pick(json("https://itunes.apple.com/lookup?entity=album&limit=200&id=$it")) }
            ?: return null
        return appleAlbum(best.optLong("collectionId").toString(), "us", key)
    }
    /** an Apple album's songs with previews, in album order */
    private fun appleAlbum(id: String, cc: String, key: String): Album {
        val r = JSONObject(get("https://itunes.apple.com/lookup?entity=song&id=$id&country=$cc")).getJSONArray("results")
        val col = (0 until r.length()).map { r.getJSONObject(it) }.firstOrNull { it.optString("wrapperType") == "collection" }
        val cover = col?.optString("artworkUrl100").orEmpty().replace("100x100bb", "300x300bb")
        return Album(col?.optString("collectionName").orEmpty(), id, cover, (0 until r.length()).map { r.getJSONObject(it) }.filter { it.optString("wrapperType") == "track" && it.has("previewUrl") }
            .sortedWith(compareBy({ it.optInt("discNumber") }, { it.optInt("trackNumber") }))
            .map { Song(it.optString("trackName"), it.optString("artistName"), it.getString("previewUrl"), it.optString("artworkUrl100"), key) })
    }
    /** track names (title, artist) from pasted lines or a playlist link */
    private fun tracksOf(t: String): List<Pair<String, String>> {
        val sp = Regex("open\\.spotify\\.com/(?:intl-\\w+/)?(playlist|album)/(\\w+)").find(t)
        if (sp != null) {   // Spotify: the public embed page carries the track list
            val tl = spotifyEntity(sp.groupValues[1], sp.groupValues[2])?.getJSONArray("trackList") ?: return emptyList()
            return (0 until tl.length()).map { tl.getJSONObject(it).let { x -> x.optString("title") to x.optString("subtitle") } }
        }
        if (Regex("music\\.apple\\.com/").containsMatchIn(t)) {   // Apple Music: the page's structured data lists the tracks
            val u = Regex("https?://music\\.apple\\.com/\\S+").find(t)!!.value; val html = get(u)
            return Regex("<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL).findAll(html).flatMap { m ->
                runCatching { val o = JSONObject(m.groupValues[1]); val tr = o.optJSONArray("track") ?: JSONArray()
                    (0 until tr.length()).map { tr.getJSONObject(it).let { x -> x.optString("name") to (x.optJSONObject("byArtist")?.optString("name") ?: "") } } }.getOrDefault(emptyList()).asSequence()
            }.toList()
        }
        val yt = Regex("[?&]list=([\\w-]+)").find(t)
        if (yt != null && t.contains("youtu")) {   // YouTube: the playlist page's video titles (usually "Artist - Song (…)")
            val html = get("https://www.youtube.com/playlist?list=${yt.groupValues[1]}")
            return Regex("\"playlistVideoRenderer\":\\{\"videoId\":\"[^\"]+\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"\\}", RegexOption.DOT_MATCHES_ALL).findAll(html).map { m ->
                val x = m.groupValues[1].replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").replace("\\u0026", "&")
                x.split(" - ", " – ").let { if (it.size > 1) it[1].trim() to it[0].trim() else x to "" } }.toList()
        }
        /* pasted lines: "Song – Artist", "Song - Artist", "Song by Artist" or just a song */
        return t.lines().map { it.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "") }.filter { it.isNotEmpty() && !it.startsWith("http") }.map { l ->
            Regex("\\s+(?:[-–—]|by)\\s+").split(l, 2).let { if (it.size > 1) it[0] to it[1] else l to "" } }
    }
    val genres by lazy { j.getJSONArray("genres").let { a -> List(a.length()) { a.getJSONArray(it).let { g -> g.getString(0) to g.getString(1) } } } }
    internal val comments by lazy { strs("comments") }
    /** the special accounts' own comments (daily.heatmap, countdown.timer, hydration.break); the rest share `comments` */
    private val acomments by lazy { j.optJSONObject("acomments")?.let { o -> o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } } } }.orEmpty() }
    /** a subject account's handle → its subject's own comments (reels.json scomments, by the subj group's name) */
    private val scomments by lazy {
        val sc = j.optJSONObject("scomments") ?: return@lazy emptyMap<String, List<String>>()
        val g = j.getJSONArray("subj"); val m = HashMap<String, List<String>>()
        for (i in 0 until g.length()) { val o = g.getJSONObject(i); val l = sc.optJSONArray(o.optString("name")) ?: continue
            val c = List(l.length()) { l.getString(it) }; val hs = o.getJSONArray("handles"); for (k in 0 until hs.length()) m[hs.getJSONArray(k).getString(0)] = c }
        m
    }
    /** an account's comments: the special accounts' own; a subject account's 20% its subject's, 80% everyone's (1 to 4); the rest everyone's */
    fun commentsFor(handle: String): List<String> = acomments[handle] ?: scomments[handle]?.let { s -> val k = minOf(s.size, comments.size / 4)
        s.shuffled().take(k) + comments.shuffled().take(k * 4) } ?: comments
    internal val names by lazy { strs("names") }
    internal val suffixes by lazy { strs("suffixes") }
    internal val times by lazy { strs("times") }
    val qrUrl by lazy { j.getString("qrUrl") }       // the share sheet's QR Code contact: the latest release's medslave-Universal.apk (qrPath is drawn from it, 4-module border)
    val qrPath by lazy { j.getString("qrPath") }
    val qrSize by lazy { j.getInt("qrSize") }
    val share by lazy { j.getJSONArray("share").let { a -> List(a.length()) { a.getJSONArray(it).let { s -> Triple(s.getString(0), s.getString(1), s.getString(2)) } } } }

    private val h = Handler(Looper.getMainLooper())
    private fun rnd(a: Int, b: Int) = a + (Math.random() * (b - a + 1)).toInt()
    private fun <T> pick(l: List<T>) = l[(Math.random() * l.size).toInt()]

    // the card on screen
    private var card: Cur? = null
    var liked by mutableStateOf(false)
    var likes by mutableIntStateOf(0)
    var coms by mutableIntStateOf(0)
    var name by mutableStateOf("")
    var icon by mutableStateOf("")
    var color by mutableStateOf(Color.White)
    var likePop by mutableIntStateOf(0)
    var repostAt by mutableIntStateOf(0)       // bumps when Repost fires (its animation)
    var reposting by mutableStateOf(false)
    var saved by mutableStateOf(false)         // this card is in saved.cards (the Save button)
    var savePop by mutableIntStateOf(0)        // bumps on every Save tap (its drop animation)
    private val colors = HashMap<String, Color>()
    val bursts = mutableStateListOf<Burst>()
    var whoTop by mutableFloatStateOf(0f)          // the account row's top in the card area (px): tall cards end above it
    val excl = arrayOf(Rect.Zero, Rect.Zero)       // the account row and the buttons (window px): no double-tap like there
    fun setExcl(i: Int, r: Rect) { excl[i] = r }
    // sheets
    var sheet by mutableStateOf<String?>(null)  // com | share | music
    var qr by mutableStateOf(false)
    val list = mutableStateListOf<Comment>()
    var loading by mutableStateOf(false)
    private var gen = 0
    // music
    var song by mutableStateOf<Song?>(null)
    var muted by mutableStateOf(false)
    @Volatile private var want = false
    @Volatile private var paused = false        // the app is in the background
    @Volatile private var prepared = false
    /* the music player runs on its own thread: starting a song (reset, data source, prepare) used to stall the screen while the feed
       scrolled. everything that touches mp goes through au() */
    private val audio = android.os.HandlerThread("medslave-audio").apply { start() }
    private val ah = Handler(audio.looper)
    private fun au(f: () -> Unit) { ah.post { runCatching(f) } }
    /* audio focus: when a song starts (or comes back with the app) with the sound on, other apps' music stops; if another app takes the
       focus back, the song here pauses (it plays again with the next card or when the app comes back) */
    private val am by lazy { rs.ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    private val focusReq by lazy {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { f -> if (f < 0) au { if (mp.isPlaying) mp.pause() } }.build()
    }
    private fun focus() { if (!muted) runCatching { am.requestAudioFocus(focusReq) } }
    private val mp = MediaPlayer().apply {
        setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        isLooping = true
        setOnPreparedListener { prepared = true; isLooping = true; if (want && !paused) { val v = if (muted) 0f else 1f; it.setVolume(v, v); h.post { focus() }; it.start() } }
        setOnErrorListener { _, _, _ -> prepared = false; h.post { onError() }; true }
    }

    init { loadLists() }
    fun on() = rs.set.reels && rs.cur != null && rs.study

    /** a new card: a new account, counts and song; the same card: keep playing */
    fun onCard() {
        if (!on()) { stop(); sheet = null; return }
        val c = rs.cur
        if (card !== c) {
            card = c; liked = false; likes = rnd(800, 98000); coms = rnd(40, 2400); reposting = false; saved = c != null && rs.feed.isSaved(c.n.id, c.o)
            /* the feed's account for the card's subject (so tapping it opens that profile); a random one before the feed exists */
            val fa = c?.let { rs.feed.accountOf(it.n) }
            if (fa != null) { name = fa.first; icon = fa.second; color = Color(android.graphics.Color.parseColor(fa.third)) }
            else { val hd = pick(handles); name = hd.first; icon = hd.second; color = colors.getOrPut(name) { pick(DCOL).second } }
            set(pick())
        } else if (!want && song != null) play()
    }

    /** a short buzz, like the site's navigator.vibrate(12) (Samsung often ignores the haptic-feedback route) */
    fun buzz() {
        runCatching {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31) rs.ctx.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
                    else @Suppress("DEPRECATION") rs.ctx.getSystemService(android.os.Vibrator::class.java)
            v.vibrate(android.os.VibrationEffect.createOneShot(15, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    fun like(v: Boolean) { liked = v; if (v) likePop++ }
    fun burst(x: Float, y: Float) { bursts.add(Burst(x, y, (Math.random() * 30 - 15).toFloat(), System.nanoTime())); if (!liked) like(true) }

    /* ---- music */
    private fun gOn(s: Song) = s.genre !in rs.set.goff
    fun pick(): Song {
        val ok = songs.filter { !it.dead && it !== song && gOn(it) }; if (ok.isNotEmpty()) return pick(ok)
        val any = songs.filter { !it.dead && gOn(it) }; return if (any.isNotEmpty()) pick(any) else pick(songs)
    }
    private var setGen = 0
    private fun set(s: Song) {
        song = s; prepared = false
        val f = cached(s); want = true; val g = ++setGen
        fun start(src: String) = au { if (g != setGen) return@au; try { mp.reset(); mp.setDataSource(src); mp.prepareAsync() } catch (e: Exception) { h.post { if (g == setGen) onError() } } }
        if (f.exists()) { f.setLastModified(System.currentTimeMillis()); start(f.path); return }
        if (!gOn(s)) { start(s.url); return }   // a song from a genre or playlist that's switched off streams but isn't saved
        /* not saved yet: it's downloaded once and played from the file. if that takes over 1.2 s it streams meanwhile (the download still
           lands in the cache), so a slow connection never waits */
        var begun = false
        fetch(s) { ok -> h.post { if (g == setGen && !begun) { begun = true; start(if (ok && f.exists()) f.path else s.url) } } }
        h.postDelayed({ if (g == setGen && !begun) { begun = true; start(s.url) } }, 1200)
    }
    /** the feed's and stories' song: keeps playing if it's already the one */
    fun playSong(s: Song) { if (song === s && want) return play(); set(s) }

    /* ---- the song cache (cache/songs): a preview is saved the first time it plays and played from the phone after that.
       only songs from genres and playlists that are on are saved. prune(), when the app opens: songs of genres that are off go after a
       week unplayed; songs of playlists that are off go a week after the playlist was last on; the built-in songs stay under 100 MB
       (oldest first); and an import that didn't finish leaves nothing behind */
    private val songDir by lazy { java.io.File(rs.ctx.cacheDir, "songs").apply { mkdirs() } }
    private fun cached(s: Song) = java.io.File(songDir, Integer.toHexString(s.url.hashCode()) + "-" + Integer.toHexString((s.title + s.artist).hashCode()) + ".m4a")
    private val fetching = HashSet<String>()
    /** saves a preview to the cache; done(true) once it's there (not called when it's already downloading: set() then streams after 1.2 s) */
    private fun fetch(s: Song, done: ((Boolean) -> Unit)? = null) {
        val f = cached(s); val url = s.url
        synchronized(fetching) { if (!fetching.add(url)) return }
        thread {
            val ok = runCatching {
                val tmp = java.io.File(songDir, f.name + ".part")
                (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 15000 }.inputStream.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                if (tmp.length() > 10_000) tmp.renameTo(f) else { tmp.delete(); false }
            }.getOrDefault(false)
            synchronized(fetching) { fetching.remove(url) }
            done?.invoke(ok)
        }
    }
    fun prune() = thread {
        runCatching {
            val week = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            /* your playlists' songs: kept while the playlist is on, and until a week after it was last on (or added, if it never was).
               the built-in songs: a genre that's on keeps its songs (under 100 MB, below); a genre that's off loses them after a week unplayed */
            h.post { markLists() }
            val now = System.currentTimeMillis(); val off = rs.set.goff
            val keep = lists.toList().filter { now - maxOf(it.lastOn, it.added) < 7L * 24 * 3600 * 1000 || it.key !in off }.flatMap { it.songs.toList() }.map { cached(it).name }.toSet()
            val on = songs.toList().filter { !it.genre.startsWith("pl:") && it.genre !in off }.map { cached(it).name }.toSet()
            songDir.listFiles()?.forEach { if (it.name.endsWith(".part") || (it.name !in keep && it.name !in on && it.lastModified() < week)) it.delete() }
            var total = songDir.listFiles()?.filter { it.name !in keep }?.sumOf { it.length() } ?: 0L
            songDir.listFiles()?.filter { it.name !in keep }?.sortedBy { it.lastModified() }?.forEach { if (total > 100L shl 20) { total -= it.length(); it.delete() } }
            val fortnight = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
            artDir.listFiles()?.forEach { if (it.name.endsWith(".part") || it.lastModified() < fortnight) it.delete() }
            val c = rs.ctx.cacheDir; val f = rs.ctx.filesDir
            if (!rs.busy) listOf(java.io.File(c, "import.apkg"), java.io.File(c, "import-col.db"), java.io.File(c, "import-col.db-journal"), java.io.File(f, "deck.db.tmp")).forEach { it.delete() }
            if (!rs.busy) java.io.File(f, "media.tmp").deleteRecursively()
        }
    }
    private fun vol() { val v = if (muted) 0f else 1f; au { mp.setVolume(v, v) }; if (!muted && want && prepared && !paused) focus() }   // unmuted: other music stops
    fun play() { want = true; vol(); if (prepared && !paused) { focus(); au { if (!mp.isPlaying) mp.start() } } }
    fun stop() { want = false; if (prepared) au { if (mp.isPlaying) mp.pause() } }
    fun tapMusic() { muted = !muted; play() }
    fun pause(p: Boolean) { paused = p; if (p) { if (prepared) au { if (mp.isPlaying) mp.pause() } } else if (want) play() }
    /** the songs changed: the reviewer swaps its song if that one's off or gone; the feed and stories swap theirs (Feed.songsChanged) */
    fun genreChanged() { if (rs.study) song?.let { if (!gOn(it) || songs.none { s -> s === it }) set(pick()) }; rs.feed.songsChanged() }

    /** a preview that fails is looked up again once by title + artist; if that fails too, the song is skipped for this visit */
    private fun onError() {
        val s = song ?: return
        if (s.re) return dead(s)
        s.re = true
        thread {
            val u = try {
                val c = URL("https://itunes.apple.com/search?entity=song&limit=5&term=" + URLEncoder.encode(s.title + " " + s.artist, "UTF-8")).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val r = JSONObject(c.inputStream.use { String(it.readBytes()) }).getJSONArray("results")
                uncut((0 until r.length()).map { r.getJSONObject(it) })?.getString("previewUrl")
            } catch (e: Exception) { null }
            h.post { if (u == null) dead(s) else if (song === s) { s.url = u; set(s) } }
        }
    }
    private fun dead(s: Song) { s.dead = true; if (song === s && songs.any { !it.dead }) set(pick()) }

    /* ---- sheets */
    fun open(kind: String) {
        sheet = kind
        if (kind == "com") {
            val g = ++gen; list.clear(); loading = true
            val q = commentsFor(name).shuffled().take(40); var i = 0
            fun more() {
                if (g != gen) return
                loading = false
                val n = if (rs.set.fakeload) minOf(q.size - i, rnd(1, 4)) else q.size - i   // no fake loading: all at once
                repeat(n) { list.add(Comment(pick(names) + pick(suffixes), pick(times), q[i], if (Math.random() < .3) 0 else rnd(1, 9000), pick(DCOL).second)); i++ }
                if (i < q.size) h.postDelayed({ more() }, rnd(60, 260).toLong())
            }
            if (rs.set.fakeload) h.postDelayed({ more() }, rnd(350, 900).toLong()) else more()
        }
    }
    fun post(text: String) { loading = false; list.add(0, Comment("you", "now", text, 0, pick(DCOL).second)) }
    fun close() { sheet = null; qr = false; gen++; stopPreview() }

    /* ---- previews (the music sheet: a tap on a playlist plays one of its songs; Edit playlist: a tap on a song plays it). their own player,
       once through (no loop), the song underneath paused meanwhile and back after. from the cache when it's there, otherwise streamed and
       not saved. preview: what's playing (a playlist's key, or "song:" + title|artist); null: nothing */
    var preview by mutableStateOf<String?>(null)
    private var pv: MediaPlayer? = null
    fun previewSong(s: Song, key: String) {
        if (preview == key) return stopPreview()
        val f = cached(s); val src = if (f.exists()) f.path else s.url
        preview = key
        au {
            pv?.release(); pv = null
            if (mp.isPlaying) mp.pause()
            pv = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(src)
                setOnPreparedListener { if (pv === it) { runCatching { am.requestAudioFocus(focusReq) }; it.start() } }
                setOnCompletionListener { h.post { if (preview == key) stopPreview() } }   // once through, then it stops
                setOnErrorListener { _, _, _ -> h.post { if (preview == key) stopPreview() }; true }
                prepareAsync()
            }
        }
    }
    /** a playlist's preview: a random song of it (another tap on it stops it) */
    fun previewList(p: Playlist) { if (preview == p.key) return stopPreview(); p.songs.filter { !it.dead }.takeIf { it.isNotEmpty() }?.let { previewSong(it.random(), p.key) } }
    fun stopPreview() {
        if (preview == null && pv == null) return
        preview = null
        au { pv?.release(); pv = null }
        if (want) play()   // the song that was playing carries on
    }

    fun release() { au { mp.release() }; audio.quitSafely() }

    /* ---- cover art: in memory, and saved to the phone (cache/art) the first time, so it's there offline and after a restart.
       prune() clears covers unused for two weeks */
    private val art = LruCache<String, ImageBitmap>(48)
    private val artDir by lazy { java.io.File(rs.ctx.cacheDir, "art").apply { mkdirs() } }
    private fun artFile(url: String) = java.io.File(artDir, Integer.toHexString(url.hashCode()) + "-" + url.length + ".img")
    /* covers load on a small shared pool, and a cover already loading is waited on, not fetched again */
    private val artPool = java.util.concurrent.Executors.newFixedThreadPool(3)
    private val artWait = HashMap<String, MutableList<(ImageBitmap) -> Unit>>()   // main thread only
    fun art(url: String, then: (ImageBitmap) -> Unit) {
        if (url.isEmpty()) return
        art.get(url)?.let { return then(it) }
        artWait[url]?.let { it.add(then); return }
        artWait[url] = mutableListOf(then)
        artPool.execute {
            val f = artFile(url)
            val bytes = runCatching { if (f.length() > 0) f.readBytes().also { f.setLastModified(System.currentTimeMillis()) } else null }.getOrNull()
                ?: runCatching { URL(url).openStream().use { it.readBytes() } }.getOrNull()?.also { d -> runCatching { val t = java.io.File(artDir, f.name + ".part"); t.writeBytes(d); t.renameTo(f) } }
            val b = bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
            h.post { val l = artWait.remove(url); if (b != null) { art.put(url, b); l?.forEach { it(b) } } }
        }
    }
}
