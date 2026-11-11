package app.checkerschreck

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sucht im GitHub-Release "apk-latest" nach einer neueren APK, lädt sie herunter und
 * übergibt sie dem Android-Installer. Verglichen wird die Build-Nummer (versionCode).
 */
object Updater {
    data class Info(val versionCode: Int, val versionName: String, val sha: String)

    private val base get() = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/apk-latest"
    val releasePage get() = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/tag/apk-latest"

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "CheckerSchreck-Updater")
        c.setRequestProperty("Cache-Control", "no-cache")
        if (c.responseCode != 200) {
            val code = c.responseCode
            c.disconnect()
            throw IOException(if (code == 404) "Kein Release gefunden (HTTP 404)" else "HTTP $code")
        }
        return c
    }

    /** Netzwerkzugriff: nicht auf dem Haupt-Thread aufrufen. */
    fun fetchLatest(): Info {
        val c = open("$base/version.json")
        try {
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            return Info(json.getInt("versionCode"), json.optString("versionName", "?"), json.optString("sha", ""))
        } finally {
            c.disconnect()
        }
    }

    fun isNewer(info: Info) = info.versionCode > BuildConfig.VERSION_CODE

    /** Lädt die APK in den Cache. Netzwerkzugriff: nicht auf dem Haupt-Thread aufrufen. */
    fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "checker-schreck-${info.versionCode}.apk")
        val c = open("$base/checker-schreck.apk")
        try {
            val total = c.contentLengthLong
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            if (target.length() < 100_000) throw IOException("Download unvollständig")
            return target
        } finally {
            c.disconnect()
        }
    }

    /** Öffnet den Android-Installer für die heruntergeladene APK. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
