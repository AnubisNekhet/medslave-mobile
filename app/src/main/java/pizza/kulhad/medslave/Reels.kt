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
import org.json.JSONObject
import pizza.kulhad.medslave.ui.DCOL
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/* Reels Mode (Settings › Appearance): a joke overlay on the review screen, as on the site. Nothing here is saved or sent anywhere.
   A made-up account per card, likes, comments (a sheet of mean comments), Repost (= missed it), share (made-up contacts) and a
   30-second Apple Music preview per card on loop. The data (reels.json) is the site's reels.js lists. */
class Song(val title: String, val artist: String, var url: String, val art: String, val genre: String) { var dead = false; var re = false }
class Comment(val user: String, val time: String, val text: String, likes: Int, val color: Color) { var likes by mutableIntStateOf(likes); var liked by mutableStateOf(false) }
class Burst(val x: Float, val y: Float, val r: Float, val id: Long)

class Reels(private val rs: Rs) {
    private val j by lazy { JSONObject(rs.ctx.assets.open("reels.json").use { String(it.readBytes()) }) }
    private fun strs(k: String) = j.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }
    private val ico by lazy { j.getJSONObject("ico") }
    val handles by lazy { j.getJSONArray("handles").let { a -> List(a.length()) { a.getJSONArray(it).let { h -> h.getString(0) to ico.optString(h.getString(1)) } } } }
    val songs by lazy { j.getJSONArray("songs").let { a -> List(a.length()) { a.getJSONArray(it).let { s -> Song(s.getString(0), s.getString(1), s.getString(2), s.getString(3), s.getString(4)) } } } }
    val genres by lazy { j.getJSONArray("genres").let { a -> List(a.length()) { a.getJSONArray(it).let { g -> g.getString(0) to g.getString(1) } } } }
    private val comments by lazy { strs("comments") }
    private val names by lazy { strs("names") }
    private val suffixes by lazy { strs("suffixes") }
    private val times by lazy { strs("times") }
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
    private val colors = HashMap<String, Color>()
    val bursts = mutableStateListOf<Burst>()
    var whoTop by mutableFloatStateOf(0f)          // the account row's top in the card area (px): tall cards end above it
    val excl = arrayOf(Rect.Zero, Rect.Zero)       // the account row and the buttons (window px): no double-tap like there
    fun setExcl(i: Int, r: Rect) { excl[i] = r }
    // sheets
    var sheet by mutableStateOf<String?>(null)  // com | share | music
    val list = mutableStateListOf<Comment>()
    var loading by mutableStateOf(false)
    private var gen = 0
    // music
    var song by mutableStateOf<Song?>(null)
    var muted by mutableStateOf(false)
    private var want = false
    private var paused = false        // the app is in the background
    private var prepared = false
    private val mp = MediaPlayer().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        isLooping = true
        setOnPreparedListener { prepared = true; isLooping = true; if (want && !paused) { vol(); it.start() } }
        setOnErrorListener { _, _, _ -> prepared = false; onError(); true }
    }

    fun on() = rs.set.reels && rs.cur != null && rs.study

    /** a new card: a new account, counts and song; the same card: keep playing */
    fun onCard() {
        if (!on()) { stop(); sheet = null; return }
        val c = rs.cur
        if (card !== c) {
            card = c; liked = false; likes = rnd(800, 98000); coms = rnd(40, 2400); reposting = false
            val hd = pick(handles); name = hd.first; icon = hd.second; color = colors.getOrPut(name) { pick(DCOL).second }
            set(pick())
        } else if (!want && song != null) play()
    }

    fun like(v: Boolean) { liked = v; if (v) likePop++ }
    fun burst(x: Float, y: Float) { bursts.add(Burst(x, y, (Math.random() * 30 - 15).toFloat(), System.nanoTime())); if (!liked) like(true) }

    /* ---- music */
    private fun gOn(s: Song) = s.genre !in rs.set.goff
    fun pick(): Song {
        val ok = songs.filter { !it.dead && it !== song && gOn(it) }; if (ok.isNotEmpty()) return pick(ok)
        val any = songs.filter { !it.dead && gOn(it) }; return if (any.isNotEmpty()) pick(any) else pick(songs)
    }
    private fun set(s: Song) {
        song = s; prepared = false
        try { mp.reset(); mp.setDataSource(s.url); mp.prepareAsync() } catch (e: Exception) { onError() }
        want = true
    }
    private fun vol() { val v = if (muted) 0f else 1f; runCatching { mp.setVolume(v, v) } }
    fun play() { want = true; vol(); if (prepared && !paused) runCatching { if (!mp.isPlaying) mp.start() } }
    fun stop() { want = false; if (prepared) runCatching { if (mp.isPlaying) mp.pause() } }
    fun tapMusic() { muted = !muted; play() }
    fun pause(p: Boolean) { paused = p; if (p) { if (prepared) runCatching { if (mp.isPlaying) mp.pause() } } else if (want) play() }
    fun genreChanged() { song?.let { if (!gOn(it)) set(pick()) } }

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
                (0 until r.length()).map { r.getJSONObject(it) }.firstOrNull { it.has("previewUrl") }?.getString("previewUrl")
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
            val q = comments.shuffled().take(40); var i = 0
            fun more() {
                if (g != gen) return
                loading = false
                val n = minOf(q.size - i, rnd(1, 4))
                repeat(n) { list.add(Comment(pick(names) + pick(suffixes), pick(times), q[i], if (Math.random() < .3) 0 else rnd(1, 9000), pick(DCOL).second)); i++ }
                if (i < q.size) h.postDelayed({ more() }, rnd(60, 260).toLong())
            }
            h.postDelayed({ more() }, rnd(350, 900).toLong())
        }
    }
    fun post(text: String) { loading = false; list.add(0, Comment("you", "now", text, 0, pick(DCOL).second)) }
    fun close() { sheet = null; gen++ }

    fun release() { runCatching { mp.release() } }

    /* ---- cover art, loaded once each */
    private val art = LruCache<String, ImageBitmap>(48)
    fun art(url: String, then: (ImageBitmap) -> Unit) {
        art.get(url)?.let { return then(it) }
        thread {
            val b = runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            if (b != null) h.post { art.put(url, b); then(b) }
        }
    }
}
