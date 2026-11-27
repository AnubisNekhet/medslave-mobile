package pizza.kulhad.medslave

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import pizza.kulhad.medslave.ui.AddDeck
import pizza.kulhad.medslave.ui.Credits
import pizza.kulhad.medslave.ui.DialogBox
import pizza.kulhad.medslave.ui.Home
import pizza.kulhad.medslave.ui.ImportPanel
import pizza.kulhad.medslave.ui.LocalPal
import pizza.kulhad.medslave.ui.Picker
import pizza.kulhad.medslave.ui.ProgressPanel
import pizza.kulhad.medslave.ui.SettingsPanel
import pizza.kulhad.medslave.ui.Sheets
import pizza.kulhad.medslave.ui.Study
import pizza.kulhad.medslave.ui.Tutorial
import pizza.kulhad.medslave.ui.accented
import pizza.kulhad.medslave.ui.pal

class MainActivity : ComponentActivity() {
    private lateinit var rs: Rs
    private var openThen: ((Uri) -> Unit)? = null
    private var saveData: ByteArray? = null

    private val opener = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        val f = openThen
        openThen = null
        if (u != null) f?.invoke(u)
    }
    private val saver = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        val d = saveData
        saveData = null
        if (u != null && d != null) runCatching { contentResolver.openOutputStream(u)?.use { it.write(d) } }.onFailure { rs.dialog = Dlg("Couldn’t save the file.") }
    }

    override fun onCreate(b: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        pizza.kulhad.medslave.ui.You.load(this) // Material You colours (they change with the wallpaper; the activity is recreated then)
        rs = (lastNonConfigurationInstance as? Rs) ?: Rs(applicationContext).also { it.loadDeck() }
        rs.activity = this
        if (b == null) rs.reels.prune() // tidy the cache when the app opens
        rs.updater.check() // a newer release? (in the background, once per start)
        rs.openFile = { mimes, then ->
            openThen = then
            runCatching { opener.launch(mimes) }
        }
        rs.saveFile = { name, _, data ->
            saveData = data
            runCatching { saver.launch(name) }
        }
        handle(intent)
        /* the system splash (the big icon) stays until the deck is loaded, so the app's smaller spinner never takes over from it on launch
           (it looked like the icon shrank as it started spinning). a cap of 3 s in case loading stalls */
        if (!rs.loaded) {
            val t0 = android.os.SystemClock.uptimeMillis()
            val content = findViewById<android.view.View>(android.R.id.content)
            content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = rs.loaded || android.os.SystemClock.uptimeMillis() - t0 > 3000
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            })
        }
        setContent {
            val sys = isSystemInDarkTheme()
            val dark = when (rs.set.mode) {
                "light" -> false
                "dark" -> true
                else -> sys
            } // Settings › Appearance › Mode
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t), if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t))
            }
            val p = remember(rs.set.theme, dark, rs.set.accent) { pal(rs.set.theme, dark).accented(pizza.kulhad.medslave.ui.accentOf(rs.set.theme, rs.set.accent, dark)) }
            rs.scope = rememberCoroutineScope()
            LaunchedEffect(p.hi) {
                val h = String.format("#%06x", p.hi.toArgb() and 0xffffff)
                rs.web.accent(h)
                rs.feed.accHex = h
            } // the pages' accent (exam tags)
            LaunchedEffect(p.hi, rs.set.redlike) { rs.feed.likeHex = if (rs.set.redlike) "#ff3040" else String.format("#%06x", p.hi.toArgb() and 0xffffff) } // the feed's likes
            val dn = LocalDensity.current
            val cf = LocalConfiguration.current
            SideEffect {
                rs.density = dn.density
                rs.screenW = cf.screenWidthDp * dn.density
                rs.screenH = cf.screenHeightDp * dn.density
            }
            LaunchedEffect(rs.set.theme, dark) {
                rs.dark = dark
                rs.web.theme(rs.set.theme, dark)
                rs.feed.theme(rs.set.theme, dark)
            }
            /* an open story fills the screen: the status and navigation bars hide (a swipe from the edge shows them for a moment) */
            LaunchedEffect(rs.feed.storyOpen) {
                val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (rs.feed.storyOpen) c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) else c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            LaunchedEffect(rs.busy) { if (rs.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            CompositionLocalProvider(LocalPal provides p) {
                Box(Modifier.fillMaxSize().background(p.bg)) {
                    if (rs.study) Study(rs) else Home(rs)
                    rs.picker?.let { Picker(rs, it) }
                    if (rs.creditsOpen) Credits(rs)
                    if (rs.heatOpen) pizza.kulhad.medslave.ui.HeatPanel(rs)
                    if (rs.settingsOpen) SettingsPanel(rs)
                    if (rs.progressOpen) ProgressPanel(rs)
                    Sheets(rs)
                    if (rs.tutorial) Tutorial(rs)
                    pizza.kulhad.medslave.ui.Tour(rs) // the app tour (over the app, under dialogs)
                    if (rs.addDeck) AddDeck(rs)
                    if (rs.feed.pickOpen) pizza.kulhad.medslave.ui.FilterPick(rs)
                    rs.importTypes?.let { ImportPanel(rs, it) }
                    if (rs.importing) pizza.kulhad.medslave.ui.ImportProgress(rs)
                    rs.dialog?.let { DialogBox(rs, it) }
                    if (rs.updater.offer != null) pizza.kulhad.medslave.ui.UpdateBox(rs)
                    if (rs.updater.notes != null && rs.updater.offer == null) pizza.kulhad.medslave.ui.NotesBox(rs) // What's new, after an update
                }
                /* the back gesture closes the topmost popup, then leaves the deck (the site's pOn/pOff history entries) */
                BackHandler(rs.study) { rs.home() }
                /* Reels Mode's home: back leaves the Menu view for the feed, and closes an open story */
                BackHandler(!rs.study && rs.set.reels && rs.tab != "feed") { rs.tab = "feed" }
                BackHandler(!rs.study && rs.set.reels && rs.feed.profileOpen && !rs.feed.storyOpen) { rs.feed.web.closeProfile() }
                BackHandler(!rs.study && rs.set.reels && rs.feed.storyOpen) { rs.feed.web.close() }
                BackHandler(rs.picker != null || rs.heatOpen || rs.creditsOpen || rs.settingsOpen || rs.progressOpen) {
                    rs.picker = null
                    rs.heatOpen = false
                    rs.creditsOpen = false
                    rs.settingsOpen = false
                    rs.progressOpen = false
                }
                BackHandler(rs.reels.sheet != null) { if (rs.reels.qr) rs.reels.qr = false else rs.reels.close() }
                BackHandler(rs.tutorial) { rs.tutorial = false }
                BackHandler(rs.importTypes != null || rs.importing) { rs.importCancel() }
                BackHandler(rs.updater.notes != null && rs.updater.offer == null) { rs.updater.notes = null }
                BackHandler(rs.dialog != null) { if (rs.dialog?.cancel == true) rs.dialog = null }
            }
        }
    }

    @Deprecated("keeps the app state across rotation")
    override fun onRetainCustomNonConfigurationInstance(): Any = rs

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        handle(i)
    }

    /* the volume keys while reviewing (Reels or not, a card on screen, nothing open over it): down = knew it, up = missed it; on the
       question side either one shows the answer. anywhere else they're the volume */
    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        val k = e.keyCode
        if ((k == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || k == android.view.KeyEvent.KEYCODE_VOLUME_UP) &&
            rs.set.volkeys &&
            rs.study &&
            rs.cur != null &&
            !rs.progressOpen &&
            !rs.settingsOpen &&
            rs.dialog == null &&
            rs.reels.sheet == null &&
            !rs.tutorial
        ) {
            if (e.action == android.view.KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                if (rs.stage == 0) {
                    rs.advance(true)
                } else if (k == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                    rs.advance(true, true)
                } else {
                    rs.wrong()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    /** "Open with medslave" / share to medslave: an .apkg from a file manager or a chat app */
    private fun handle(i: Intent?) {
        /* a deck link, medslave://import?url=…: downloaded like a pasted link (a download that isn't an .apkg is refused) */
        val d = i?.data
        if (i?.action == Intent.ACTION_VIEW && d?.scheme.equals("medslave", true)) {
            i?.action = null
            val link = if (d?.host.equals("import", true)) runCatching { d?.getQueryParameter("url") }.getOrNull() else null
            if (link.isNullOrBlank()) {
                rs.dialog = Dlg("That medslave link has no deck in it.")
            } else {
                rs.study = false
                rs.importUrl(link)
            }
            return
        }
        val u: Uri? = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                i.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (u != null) {
            i?.action = null
            rs.study = false
            rs.importUri(u)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!rs.study) rs.feed.opened()
    }
    override fun onPause() {
        super.onPause()
        rs.reels.pause(true)
    }
    override fun onResume() {
        super.onResume()
        rs.reels.pause(false)
    }
    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            rs.reels.release()
            rs.web.destroy()
            rs.feed.destroy()
            rs.activity = null
        } else {
            rs.web.detach()
        }
    }
}
