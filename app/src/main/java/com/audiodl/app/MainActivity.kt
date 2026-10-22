package com.audiodl.app

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var pasteButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var percentText: TextView
    private lateinit var statusText: TextView
    private lateinit var statusCard: LinearLayout

    private var busy = false

    private val urlRegex = Regex("""https?://\S+""")

    // ---------- Paleta ----------
    private val isDark by lazy {
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
    private val cBackground get() = if (isDark) 0xFF121214.toInt() else 0xFFF4F4F6.toInt()
    private val cSurface get() = if (isDark) 0xFF1E1E22.toInt() else Color.WHITE
    private val cField get() = if (isDark) 0xFF2A2A30.toInt() else 0xFFF0F0F3.toInt()
    private val cText get() = if (isDark) 0xFFF2F2F4.toInt() else 0xFF17171A.toInt()
    private val cTextMuted get() = if (isDark) 0xFF9A9AA3.toInt() else 0xFF6B6B73.toInt()
    private val cOutline get() = if (isDark) 0xFF3A3A42.toInt() else 0xFFDADAE0.toInt()
    private val cAccent = 0xFFE5484D.toInt()
    private val cSuccess = 0xFF30A46C.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.statusBarColor = cBackground
        window.navigationBarColor = cBackground
        val content = buildUi()
        setContentView(content)
        WindowCompat.getInsetsController(window, content).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        if (!App.initOk) {
            showStatus("No se pudo iniciar yt-dlp: ${App.initError ?: "error desconocido"}", State.ERROR)
        }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    // ---------- UI ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun lp(topMarginDp: Int = 0, height: Int = WRAP_CONTENT) =
        LinearLayout.LayoutParams(MATCH_PARENT, height).apply { topMargin = dp(topMarginDp) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cSurface, 20, if (isDark) null else cOutline)
        setPadding(dp(18), dp(18), dp(18), dp(18))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(cTextMuted)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.04f
    }

    private fun styledButton(text: String, filled: Boolean): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        minHeight = dp(52)
        if (filled) {
            background = rounded(cAccent, 14)
            setTextColor(Color.WHITE)
        } else {
            background = rounded(Color.TRANSPARENT, 14, cOutline)
            setTextColor(cText)
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        // Encabezado
        root.addView(TextView(this).apply {
            text = "AudioDL"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cText)
        }, lp(8))
        root.addView(TextView(this).apply {
            text = "Extrae el audio de un video en MP3"
            textSize = 15f
            setTextColor(cTextMuted)
        }, lp(2))

        // Tarjeta del enlace
        val linkCard = card()
        linkCard.addView(label("ENLACE DEL VIDEO"))

        urlInput = EditText(this).apply {
            hint = "https://youtu.be/…"
            setHintTextColor(cTextMuted)
            setTextColor(cText)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(cField, 12)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) { startDownload(); true } else false
            }
        }
        linkCard.addView(urlInput, lp(10))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pasteButton = styledButton("Pegar", filled = false).apply {
            textSize = 14f
            minHeight = dp(44)
            setOnClickListener { pasteFromClipboard() }
        }
        val clearButton = styledButton("Limpiar", filled = false).apply {
            textSize = 14f
            minHeight = dp(44)
            setOnClickListener {
                urlInput.setText("")
                resetProgress()
                showStatus("Pega un enlace o compártelo desde YouTube.", State.IDLE)
            }
        }
        row.addView(pasteButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        row.addView(clearButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        linkCard.addView(row, lp(10))

        downloadButton = styledButton("Descargar MP3", filled = true).apply {
            minHeight = dp(56)
            setOnClickListener { startDownload() }
        }
        linkCard.addView(downloadButton, lp(14))

        root.addView(linkCard, lp(24))

        // Tarjeta de estado
        statusCard = card()
        val progressHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progressHeader.addView(label("ESTADO"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        percentText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cTextMuted)
        }
        progressHeader.addView(percentText)
        statusCard.addView(progressHeader)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(cAccent)
            progressBackgroundTintList = ColorStateList.valueOf(cOutline)
            indeterminateTintList = ColorStateList.valueOf(cAccent)
        }
        statusCard.addView(progressBar, lp(10, dp(8)))

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(cText)
            setLineSpacing(0f, 1.15f)
        }
        statusCard.addView(statusText, lp(12))
        root.addView(statusCard, lp(16))
        showStatus("Pega un enlace o compártelo desde YouTube.", State.IDLE)

        // Pie
        root.addView(TextView(this).apply {
            text = "Los archivos se guardan en Música › AudioDL."
            textSize = 13f
            setTextColor(cTextMuted)
            gravity = Gravity.CENTER
        }, lp(20))

        updateButton = styledButton("Actualizar yt-dlp", filled = false).apply {
            textSize = 14f
            minHeight = dp(46)
            setOnClickListener { updateYtDlp() }
        }
        root.addView(updateButton, lp(12))

        root.addView(TextView(this).apply {
            text = "Úsalo solo con contenido propio o de licencia libre."
            textSize = 12f
            setTextColor(cTextMuted)
            gravity = Gravity.CENTER
        }, lp(12))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(cBackground)
            addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        // Respeta barra de estado, barra de navegación, notch y teclado (Android 15 es edge-to-edge).
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        return scroll
    }

    private enum class State { IDLE, WORKING, SUCCESS, ERROR }

    private fun showStatus(message: String, state: State) {
        statusText.text = message
        statusText.setTextColor(
            when (state) {
                State.SUCCESS -> cSuccess
                State.ERROR -> cAccent
                else -> cText
            }
        )
        statusCard.background = when (state) {
            State.SUCCESS -> rounded(cSurface, 20, cSuccess)
            State.ERROR -> rounded(cSurface, 20, cAccent)
            else -> rounded(cSurface, 20, if (isDark) null else cOutline)
        }
    }

    private fun resetProgress() {
        progressBar.isIndeterminate = false
        progressBar.progress = 0
        percentText.text = ""
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val url = urlRegex.find(text)?.value
        if (url != null) {
            urlInput.setText(url)
            urlInput.setSelection(url.length)
            showStatus("Enlace listo. Pulsa \"Descargar MP3\".", State.IDLE)
        } else {
            showStatus("El portapapeles no contiene un enlace.", State.ERROR)
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = urlRegex.find(text)?.value
        if (url != null) {
            urlInput.setText(url)
            showStatus("Enlace recibido. Pulsa \"Descargar MP3\".", State.IDLE)
        } else {
            showStatus("No se encontró un enlace en el texto compartido.", State.ERROR)
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        for (b in listOf(downloadButton, updateButton, pasteButton)) {
            b.isEnabled = !value
            b.alpha = if (value) 0.5f else 1f
        }
        urlInput.isEnabled = !value
        downloadButton.text = if (value) "Trabajando…" else "Descargar MP3"
    }

    // ---------- Descarga ----------

    private fun startDownload() {
        if (busy) return
        val raw = urlInput.text.toString().trim()
        val url = urlRegex.find(raw)?.value
        if (url == null) {
            showStatus("Pega un enlace válido (http/https).", State.ERROR)
            return
        }
        if (!App.initOk) {
            showStatus("No se pudo iniciar yt-dlp: ${App.initError ?: "error desconocido"}", State.ERROR)
            return
        }

        setBusy(true)
        progressBar.isIndeterminate = true
        percentText.text = ""
        showStatus("Preparando descarga…", State.WORKING)

        lifecycleScope.launch {
            try {
                val title = withContext(Dispatchers.IO) { downloadAndSave(url) }
                progressBar.isIndeterminate = false
                progressBar.progress = 100
                percentText.text = "100%"
                showStatus("✔ Guardado en Música › AudioDL\n$title", State.SUCCESS)
            } catch (e: Exception) {
                resetProgress()
                showStatus("Error: ${e.message ?: e.toString()}", State.ERROR)
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
                    val p = progress.toInt().coerceIn(0, 100)
                    progressBar.isIndeterminate = false
                    progressBar.progress = p
                    percentText.text = "$p%"
                    statusText.text = "Descargando audio…"
                } else if (line.contains("ExtractAudio") || line.contains("ffmpeg", ignoreCase = true)) {
                    progressBar.isIndeterminate = true
                    percentText.text = ""
                    statusText.text = "Convirtiendo a MP3…"
                }
            }
        }

        val mp3 = tmpDir.listFiles { f -> f.extension.equals("mp3", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("No se generó el archivo MP3.")

        withContext(Dispatchers.Main) {
            progressBar.isIndeterminate = true
            percentText.text = ""
            statusText.text = "Guardando en Música › AudioDL…"
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
            ?: throw IllegalStateException("No se pudo crear el archivo en Música › AudioDL.")

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

    // ---------- Actualizar yt-dlp ----------

    private fun updateYtDlp() {
        if (busy) return
        if (!App.initOk) {
            showStatus("No se pudo iniciar yt-dlp: ${App.initError ?: "error desconocido"}", State.ERROR)
            return
        }
        setBusy(true)
        progressBar.isIndeterminate = true
        percentText.text = ""
        showStatus("Actualizando yt-dlp…", State.WORKING)

        lifecycleScope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        applicationContext,
                        YoutubeDL.UpdateChannel._STABLE
                    )
                }
                val msg = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "✔ yt-dlp actualizado."
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "✔ yt-dlp ya está al día."
                    else -> "Actualización terminada."
                }
                showStatus(msg, State.SUCCESS)
            } catch (e: Exception) {
                showStatus("Error al actualizar: ${e.message ?: e.toString()}", State.ERROR)
            } finally {
                resetProgress()
                setBusy(false)
            }
        }
    }
}
