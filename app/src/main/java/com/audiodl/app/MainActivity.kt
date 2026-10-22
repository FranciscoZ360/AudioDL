package com.audiodl.app

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var updateButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var busy = false

    private val urlRegex = Regex("""https?://\S+""")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AudioDL"
        setContentView(buildUi())

        if (!App.initOk) {
            statusText.text = "Error al iniciar yt-dlp: ${App.initError ?: "desconocido"}"
        }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi(): ScrollView {
        val pad = dp(16)
        val gap = dp(12)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        urlInput = EditText(this).apply {
            hint = "Pega aquí el enlace de YouTube"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        root.addView(urlInput, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        downloadButton = Button(this).apply {
            text = "Descargar MP3"
            setOnClickListener { startDownload() }
        }
        root.addView(downloadButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = gap
        })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        root.addView(progressBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = gap
        })

        statusText = TextView(this).apply {
            text = "Listo."
            textSize = 15f
            gravity = Gravity.START
        }
        root.addView(statusText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = gap
        })

        updateButton = Button(this).apply {
            text = "Actualizar yt-dlp"
            setOnClickListener { updateYtDlp() }
        }
        root.addView(updateButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(32)
        })

        return ScrollView(this).apply {
            isFillViewport = true
            addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = urlRegex.find(text)?.value
        if (url != null) {
            urlInput.setText(url)
            statusText.text = "Enlace recibido. Pulsa \"Descargar MP3\"."
        } else {
            statusText.text = "No se encontró un enlace en el texto compartido."
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        downloadButton.isEnabled = !value
        updateButton.isEnabled = !value
    }

    private fun startDownload() {
        if (busy) return
        val raw = urlInput.text.toString().trim()
        val url = urlRegex.find(raw)?.value
        if (url == null) {
            statusText.text = "Pega un enlace válido (http/https)."
            return
        }
        if (!App.initOk) {
            statusText.text = "yt-dlp no se pudo iniciar: ${App.initError ?: "desconocido"}"
            return
        }

        setBusy(true)
        progressBar.isIndeterminate = true
        statusText.text = "Preparando descarga…"

        lifecycleScope.launch {
            try {
                val title = withContext(Dispatchers.IO) { downloadAndSave(url) }
                progressBar.isIndeterminate = false
                progressBar.progress = 100
                statusText.text = "✔ Guardado en Music/AudioDL:\n$title"
            } catch (e: Exception) {
                progressBar.isIndeterminate = false
                progressBar.progress = 0
                statusText.text = "Error: ${e.message ?: e.toString()}"
            } finally {
                setBusy(false)
            }
        }
    }

    /** Descarga con yt-dlp a cacheDir/dl, copia el MP3 a Music/AudioDL y devuelve el título. */
    private suspend fun downloadAndSave(url: String): String {
        val tmpDir = File(cacheDir, "dl")
        tmpDir.deleteRecursively()
        tmpDir.mkdirs()

        val request = YoutubeDLRequest(url).apply {
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "0")
            addOption("--no-playlist")
            addOption("-o", "${tmpDir.absolutePath}/%(title)s.%(ext)s")
        }

        YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
            runOnUiThread {
                if (progress > 0f) {
                    progressBar.isIndeterminate = false
                    progressBar.progress = progress.toInt().coerceIn(0, 100)
                    statusText.text = "Descargando… ${progress.toInt()}%"
                } else if (line.contains("ExtractAudio") || line.contains("ffmpeg", ignoreCase = true)) {
                    progressBar.isIndeterminate = true
                    statusText.text = "Convirtiendo a MP3…"
                }
            }
        }

        val mp3 = tmpDir.listFiles { f -> f.extension.equals("mp3", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("No se generó el archivo MP3.")

        withContext(Dispatchers.Main) {
            progressBar.isIndeterminate = true
            statusText.text = "Guardando en Music/AudioDL…"
        }

        try {
            saveToMusic(mp3)
        } finally {
            tmpDir.deleteRecursively()
        }
        return mp3.nameWithoutExtension
    }

    private fun saveToMusic(file: File) {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AudioDL")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("No se pudo crear el archivo en Music/AudioDL.")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("No se pudo escribir el archivo.")

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun updateYtDlp() {
        if (busy) return
        if (!App.initOk) {
            statusText.text = "yt-dlp no se pudo iniciar: ${App.initError ?: "desconocido"}"
            return
        }
        setBusy(true)
        progressBar.isIndeterminate = true
        statusText.text = "Actualizando yt-dlp…"

        lifecycleScope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        applicationContext,
                        YoutubeDL.UpdateChannel._STABLE
                    )
                }
                statusText.text = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "✔ yt-dlp actualizado."
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp ya está al día."
                    else -> "Actualización terminada."
                }
            } catch (e: Exception) {
                statusText.text = "Error al actualizar: ${e.message ?: e.toString()}"
            } finally {
                progressBar.isIndeterminate = false
                progressBar.progress = 0
                setBusy(false)
            }
        }
    }
}
