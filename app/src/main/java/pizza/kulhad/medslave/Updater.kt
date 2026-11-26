package pizza.kulhad.medslave

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/* Updates from GitHub. Each time the app starts it asks for the latest release in the background; when that's newer than this
   version, a popup offers it. Update downloads the APK (medslave-Universal.apk) with a progress bar and hands it to Android's
   installer, which asks the user to confirm. The downloaded file is deleted as soon as the installer has it, and whatever is
   left in the update folder is cleared on the next start. */
class Updater(private val rs: Rs) {
    init { cur = this }
    companion object { var cur: Updater? = null }
    /** the installer's answer (InstallReceiver): its screen is up, so the app quits; or it failed, and the popup says why */
    fun answered(st: Int, msg: String?) = h.post {
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) { offer = null; progress = -1f; status = ""; rs.activity?.finishAndRemoveTask() }
        else if (st != PackageInstaller.STATUS_SUCCESS) { progress = -1f; status = "The update couldn't be installed" + (msg?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") }
    }
    class Offer(val version: String, val url: String, val size: Long)

    var offer by mutableStateOf<Offer?>(null)     // a newer release (shows the popup)
    var progress by mutableFloatStateOf(-1f)      // -1: not downloading; 0–1 while it downloads
    var status by mutableStateOf("")              // a line under the bar: what it's doing, or what went wrong
    private val h = Handler(Looper.getMainLooper())
    private val dir get() = File(rs.ctx.cacheDir, "update")
    private var checked = false

    private fun current(): String = runCatching { rs.ctx.packageManager.getPackageInfo(rs.ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** "1.10" > "1.9": the numbers compared one by one */
    private fun newer(a: String, b: String): Boolean {
        val x = a.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }; val y = b.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
        return false
    }

    /** once per start: clear an old download, then look for a newer release */
    fun check() {
        if (checked) return; checked = true
        thread {
            runCatching { dir.deleteRecursively() }
            runCatching {
                val c = URL("https://api.github.com/repos/AnubisNekhet/medslave-mobile/releases/latest").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 10000; c.setRequestProperty("Accept", "application/vnd.github+json")
                if (c.responseCode != 200) return@thread
                val j = JSONObject(c.inputStream.use { String(it.readBytes()) })
                val tag = j.optString("tag_name"); if (tag.isEmpty() || !newer(tag, current())) return@thread
                val assets = j.optJSONArray("assets") ?: return@thread
                val all = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name").endsWith(".apk") }
                val a = all.firstOrNull { it.optString("name") == "medslave-Universal.apk" } ?: all.firstOrNull() ?: return@thread
                val o = Offer(tag.trimStart('v'), a.getString("browser_download_url"), a.optLong("size"))
                h.post { offer = o }
            }
        }
    }

    fun later() { if (progress < 0f) offer = null }

    /** downloads the APK (with progress), then installs it */
    fun update() {
        val o = offer ?: return; if (progress >= 0f) return
        if (Build.VERSION.SDK_INT >= 26 && !rs.ctx.packageManager.canRequestPackageInstalls()) {
            /* Android asks once whether medslave may install apps; Update again after allowing it */
            status = "Allow medslave to install updates, then tap Update again"
            runCatching { rs.activity?.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + rs.ctx.packageName))) }
            return
        }
        progress = 0f; status = "Downloading…"
        thread {
            val f = File(dir.apply { mkdirs() }, "medslave-Universal.apk")
            val ok = runCatching {
                val c = URL(o.url).openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 20000; c.instanceFollowRedirects = true
                val total = c.contentLengthLong.takeIf { it > 0 } ?: o.size
                c.inputStream.use { i -> f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var got = 0L; var last = 0L
                    while (true) { val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n); got += n
                        val now = System.currentTimeMillis(); if (total > 0 && now - last > 50) { last = now; val p = got.toFloat() / total; h.post { progress = p } } }
                } }
                f.length() > 100_000
            }.getOrDefault(false)
            if (!ok) { f.delete(); h.post { progress = -1f; status = "The download didn't finish. Try again" }; return@thread }
            h.post { progress = 1f; status = "Installing…" }
            val sent = runCatching { install(f) }.isSuccess
            f.delete()   // the installer has its own copy now
            h.post { if (!sent) { progress = -1f; status = "Couldn't start the install. Try again" } else status = "Waiting for the installer…" }
        }
    }

    /** gives the APK to Android's package installer; it asks the user to confirm (see InstallReceiver) */
    private fun install(f: File) {
        val pi = rs.ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(rs.ctx.packageName) }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            f.inputStream().use { i -> s.openWrite("medslave", 0, f.length()).use { o -> i.copyTo(o); s.fsync(o) } }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val p = PendingIntent.getBroadcast(rs.ctx, id, Intent(rs.ctx, InstallReceiver::class.java), flags)
            s.commit(p.intentSender)
        }
    }
}

/** the installer's answer: it needs the user to confirm, so its screen is shown */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -99)
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION") val confirm = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
            runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Updater.cur?.answered(st, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
