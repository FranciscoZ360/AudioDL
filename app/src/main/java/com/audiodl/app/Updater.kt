package com.audiodl.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Busca el release más reciente en GitHub, descarga su APK y abre el instalador de Android.
 * Los releases se llaman "build-N"; N es el número de compilación de GitHub Actions,
 * que también es el versionCode de la app.
 */
object Updater {

    private const val LATEST_URL =
        "https://api.github.com/repos/FranciscoZ360/AudioDL/releases/latest"

    data class Release(val build: Int, val apkUrl: String, val sizeBytes: Long)

    val currentBuild: Int get() = BuildConfig.BUILD_NUMBER

    /** Devuelve el release más reciente, o null si no tiene un APK. Llamar fuera del hilo principal. */
    fun fetchLatest(): Release? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AudioDL-Updater")
        }
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("GitHub respondió ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val build = json.optString("tag_name").removePrefix("build-").toIntOrNull()
                ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(build, a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Descarga el APK a la caché, informando el avance (0–100, o -1 si se desconoce). */
    fun download(ctx: Context, release: Release, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "AudioDL-build-${release.build}.apk")

        var url = URL(release.apkUrl)
        var conn: HttpURLConnection
        // GitHub redirige a otro dominio para la descarga; se sigue a mano por si acaso.
        var redirects = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AudioDL-Updater")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                url = URL(url, loc)
                redirects++
                continue
            }
            if (code != 200) {
                conn.disconnect()
                throw IllegalStateException("La descarga respondió $code")
            }
            break
        }

        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -2
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return out
    }

    /** ¿Android ya permite que esta app instale actualizaciones? */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Abre los ajustes donde se activa "Permitir desde esta fuente". */
    fun openInstallPermissionSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        )
    }

    /** Abre el instalador de Android con el APK descargado. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
