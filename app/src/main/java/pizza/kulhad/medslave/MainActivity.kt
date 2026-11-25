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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import pizza.kulhad.medslave.ui.CorrectionsPanel
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
import pizza.kulhad.medslave.ui.pal

class MainActivity : ComponentActivity() {
    private lateinit var rs: Rs
    private var openThen: ((Uri) -> Unit)? = null
    private var saveData: ByteArray? = null

    private val opener = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> val f = openThen; openThen = null; if (u != null) f?.invoke(u) }
    private val saver = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        val d = saveData; saveData = null
        if (u != null && d != null) runCatching { contentResolver.openOutputStream(u)?.use { it.write(d) } }.onFailure { rs.dialog = Dlg("Couldn’t save the file.") }
    }

    override fun onCreate(b: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        rs = (lastNonConfigurationInstance as? Rs) ?: Rs(applicationContext).also { it.loadDeck() }
        rs.activity = this
        rs.openFile = { mimes, then -> openThen = then; runCatching { opener.launch(mimes) } }
        rs.saveFile = { name, _, data -> saveData = data; runCatching { saver.launch(name) } }
        handle(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            val p = remember(rs.set.theme, dark) { pal(rs.set.theme, dark) }
            rs.scope = rememberCoroutineScope()
            val dn = LocalDensity.current; val cf = LocalConfiguration.current
            SideEffect { rs.density = dn.density; rs.screenW = cf.screenWidthDp * dn.density; rs.screenH = cf.screenHeightDp * dn.density }
            LaunchedEffect(rs.set.theme, dark) { rs.web.theme(rs.set.theme, dark) }
            LaunchedEffect(rs.busy) { if (rs.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            CompositionLocalProvider(LocalPal provides p) {
                Box(Modifier.fillMaxSize().background(p.bg)) {
                    if (rs.study) Study(rs) else Home(rs)
                    rs.picker?.let { Picker(rs, it) }
                    if (rs.creditsOpen) Credits(rs)
                    if (rs.settingsOpen) SettingsPanel(rs)
                    if (rs.progressOpen) ProgressPanel(rs)
                    if (rs.corrOpen) CorrectionsPanel(rs)
                    Sheets(rs)
                    if (rs.tutorial) Tutorial(rs)
                    rs.importTypes?.let { ImportPanel(rs, it) }
                    rs.dialog?.let { DialogBox(rs, it) }
                }
                /* the back gesture closes the topmost popup, then leaves the deck (the site's pOn/pOff history entries) */
                BackHandler(rs.study) { rs.home() }
                BackHandler(rs.picker != null || rs.creditsOpen || rs.settingsOpen || rs.progressOpen || rs.corrOpen) {
                    rs.picker = null; rs.creditsOpen = false; rs.settingsOpen = false; rs.progressOpen = false; rs.corrOpen = false
                }
                BackHandler(rs.reels.sheet != null) { rs.reels.close() }
                BackHandler(rs.tutorial) { rs.tutorial = false }
                BackHandler(rs.importTypes != null) { rs.importCancel() }
                BackHandler(rs.dialog != null) { if (rs.dialog?.cancel == true) rs.dialog = null }
            }
        }
    }

    @Deprecated("keeps the app state across rotation") override fun onRetainCustomNonConfigurationInstance(): Any = rs

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handle(i) }

    /** "Open with medslave" / share to medslave: an .apkg from a file manager or a chat app */
    private fun handle(i: Intent?) {
        val u: Uri? = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (u != null) { i?.action = null; rs.study = false; rs.importUri(u) }
    }

    override fun onPause() { super.onPause(); rs.reels.pause(true) }
    override fun onResume() { super.onResume(); rs.reels.pause(false) }
    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) { rs.reels.release(); rs.web.detach(); rs.activity = null }
        else { rs.web.detach() }
    }
}
