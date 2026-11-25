package pizza.kulhad.medslave

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/* The card face: one WebView showing assets/web/card.html (the site's card layout, style.css and MathJax), served from
   https://appassets.androidplatform.net/ so it behaves like the site. /media/<name> serves the deck's images from the app's files.
   The app drives it through R.* (card.js) and hears back through CardBridge. */
class CardBridge(private val w: CardWeb) {
    private val h = Handler(Looper.getMainLooper())
    @JavascriptInterface fun st(left: Int, n: Int) = h.post { w.seqLeft = left; w.seqN = n }
    @JavascriptInterface fun tap() = h.post { w.onTap() }
    @JavascriptInterface fun h(px: Float) = h.post { w.contentH = px }
    @JavascriptInterface fun ready(seq: Int) = h.post { w.ready(seq) }
    @JavascriptInterface fun sc(top: Boolean, bot: Boolean) = h.post { w.top = top; w.bot = bot }
}

/** one of the app's pages (an html file in assets/web) in a WebView, served from https://appassets.androidplatform.net/ so it behaves like a
    website; /media/<name> serves the deck's images from the app's files. Calls made before the page has loaded wait for it */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
open class Page(protected val ctx: Context, file: String, transparent: Boolean, bridge: (Page) -> Any) {
    private var loaded = false
    private val queue = ArrayList<String>()
    private val loader = WebViewAssetLoader.Builder().addPathHandler("/", WebViewAssetLoader.PathHandler { path -> serve(path) }).build()

    val view: WebView = WebView(ctx).apply {
        if (transparent) setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.textZoom = 100
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER
        /* the pages' console goes to logcat (adb logcat -s medslave-web); debug builds can be inspected from desktop Chromium */
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.println(if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) android.util.Log.ERROR else android.util.Log.DEBUG,
                    "medslave-web", "$file:${m.lineNumber()} ${m.message()}")
                return true
            }
        }
        if (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        addJavascriptInterface(bridge(this@Page), "App")   // the bridge only calls back once the page runs, after construction
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(r.url)
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true   // links on cards go nowhere
            override fun onPageFinished(v: WebView, url: String) { loaded = true; queue.forEach { evaluateJavascript(it, null) }; queue.clear() }
        }
        loadUrl("https://appassets.androidplatform.net/$file")
    }

    private fun serve(path: String): WebResourceResponse? {
        val type = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"; "js" -> "text/javascript"; "css" -> "text/css"; "woff2" -> "font/woff2"; "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"; "svg" -> "image/svg+xml"; "avif" -> "image/avif"
            "bmp" -> "image/bmp"; "ico" -> "image/x-icon"; else -> "application/octet-stream"
        }
        return try {
            if (path.startsWith("media/")) {
                val key = path.removePrefix("media/")   // WebView hands the path over already decoded
                val f = File(DeckStore.mediaDir(ctx), DeckStore.mediaName(key))
                if (f.exists()) WebResourceResponse(type, null, FileInputStream(f)) else WebResourceResponse(type, null, 404, "Not found", emptyMap(), null)
            } else WebResourceResponse(type, if (type.startsWith("text")) "utf-8" else null, ctx.assets.open("web/$path"))
        } catch (e: Exception) { null }
    }

    protected fun js(s: String) { if (loaded) view.evaluateJavascript(s, null) else queue.add(s) }
    protected fun q(s: String) = JSONObject.quote(s)
    fun theme(t: String, dark: Boolean) = js("R.theme(${q(t)},$dark)")
    fun detach() { (view.parent as? ViewGroup)?.removeView(view) }
}

/** the reviewer's card */
class CardWeb(ctx: Context) : Page(ctx, "card.html", true, { CardBridge(it as CardWeb) }) {
    var seqLeft by mutableIntStateOf(0)
    var seqN by mutableIntStateOf(1)
    var contentH by mutableFloatStateOf(0f)   // the card's full height in CSS px (= dp)
    var onTap: () -> Unit = {}
    /** the card's own scroll (a card taller than the view scrolls inside itself, the page never does) */
    internal var top = true
    internal var bot = true
    private var seq = 0
    private val waits = HashMap<Int, () -> Unit>()

    fun init(paths: List<String>, source: Boolean) = js("R.init(${JSONArray(paths)},$source)")
    fun source(v: Boolean) = js("R.source($v)")
    /** draws a card; onReady runs once it's on screen */
    fun show(noteJson: String, o: Int, stage: Int, flip: Boolean, onReady: () -> Unit = {}) {
        val s = ++seq; waits.clear(); waits[s] = onReady; top = true; bot = true
        js("R.show($s,$noteJson,$o,$stage,$flip)")
    }
    fun clear() { waits.clear(); js("R.clear()") }
    fun answer(rm: Boolean) = js("R.answer($rm)")
    fun step() = js("R.step()")
    fun glow(v: Float) = js("R.glow(${"%.3f".format(java.util.Locale.US, v)})")
    fun long(v: Boolean) = js("R.long($v)")

    internal fun ready(s: Int) {
        val f = waits.remove(s) ?: return
        view.postVisualStateCallback(s.toLong(), object : WebView.VisualStateCallback() { override fun onComplete(id: Long) { f() } })
    }

    fun atTop() = top
    fun atBottom() = bot
}
