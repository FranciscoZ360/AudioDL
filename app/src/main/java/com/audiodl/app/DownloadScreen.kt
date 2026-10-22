package com.audiodl.app

import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadScreen(private val act: MainActivity) {

    val view: View

    private val chip = StatusChip(act)
    private val urlInput: EditText
    private val pasteButton: PillButton
    private val clearButton: PillButton
    private val progressCard: LinearLayout
    private val percentText: TextView
    private val progressText: TextView
    private val progressBar: ProgressBar
    private val resultCard: LinearLayout
    private val resultTile: FrameLayout
    private val resultTitle: TextView
    private val resultSub: TextView
    private val resultActions: LinearLayout
    private val downloadButton: PillButton
    private val updateButton: PillButton

    private var busy = false
    private var lastTrack: Track? = null
    private val urlRegex = Regex("""https?://\S+""")

    init {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(24), 0, act.dp(24), act.dp(24))
        }

        // Espacio para que se vea la imagen de fondo antes del título
        val heroSpace = (act.resources.displayMetrics.heightPixels * 0.34f).toInt()
        col.addView(View(act), LinearLayout.LayoutParams(MATCH_PARENT, heroSpace))
        col.addView(act.eyebrow("El Sebastián DJ").apply {
            setShadowLayer(act.dpf(8f), 0f, act.dpf(1f), 0xCC000000.toInt())
        })
        col.addView(act.headline("Tu música,\nen MP3.").apply {
            setShadowLayer(act.dpf(14f), 0f, act.dpf(2f), 0xCC000000.toInt())
        }, act.lp(12))
        col.addView(chip, act.lp(20, width = WRAP_CONTENT))

        // --- Tarjeta del enlace ---
        val linkCard = act.card()
        linkCard.addView(act.label("Enlace del video"))
        urlInput = EditText(act).apply {
            hint = "https://youtu.be/…"
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.TEXT)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            background = act.roundRect(Palette.SURFACE_2, 16)
            setPadding(act.dp(16), act.dp(15), act.dp(16), act.dp(15))
            textCursorDrawable = GradientDrawable().apply {
                setColor(Palette.EYEBROW)
                setSize(act.dp(2), 0)
            }
            highlightColor = 0x667B5CFF
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) { startDownload(); true } else false
            }
        }
        linkCard.addView(urlInput, act.lp(12))

        val linkRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        pasteButton = PillButton(act, "Pegar", Glyph.PASTE, PillStyle.SECONDARY, 46, 15f).apply {
            setOnClickListener { pasteFromClipboard() }
        }
        clearButton = PillButton(act, "Limpiar", Glyph.CLEAR, PillStyle.SECONDARY, 46, 15f).apply {
            setOnClickListener {
                urlInput.setText("")
                progressCard.visibility = View.GONE
                resultCard.visibility = View.GONE
                setIdle()
            }
        }
        linkRow.addView(pasteButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = act.dp(6) })
        linkRow.addView(clearButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = act.dp(6) })
        linkCard.addView(linkRow, act.lp(12))
        col.addView(linkCard, act.lp(28))

        // --- Tarjeta de progreso ---
        progressCard = act.card().apply { visibility = View.GONE }
        val progHeader = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progHeader.addView(act.label("Progreso"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        percentText = act.body("", 26f, Palette.TEXT, bold = true)
        progHeader.addView(percentText)
        progressCard.addView(progHeader)
        progressBar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressDrawable = act.gradientProgress()
            indeterminateTintList = ColorStateList.valueOf(Palette.EYEBROW)
            minHeight = act.dp(8)
            maxHeight = act.dp(8)
        }
        progressCard.addView(progressBar, act.lp(12))
        progressText = act.body("", 14f, Palette.MUTED)
        progressCard.addView(progressText, act.lp(10))
        col.addView(progressCard, act.lp(16))

        // --- Tarjeta de resultado ---
        resultCard = act.card().apply { visibility = View.GONE }
        val resultTop = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        resultTile = FrameLayout(act)
        resultTop.addView(resultTile, LinearLayout.LayoutParams(act.dp(56), act.dp(56)))
        val resultTexts = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        resultTitle = act.body("", 16f, Palette.TEXT, bold = true, maxLines = 2)
        resultSub = act.body("", 13f, Palette.MUTED, maxLines = 4)
        resultTexts.addView(resultTitle)
        resultTexts.addView(resultSub, act.lp(4))
        resultTop.addView(resultTexts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = act.dp(14) })
        resultCard.addView(resultTop)

        resultActions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val playButton = PillButton(act, "Reproducir", Glyph.PLAY, PillStyle.SECONDARY, 48, 15f).apply {
            setOnClickListener { lastTrack?.let { act.playTrack(it) } }
        }
        val trimButton = PillButton(act, "Recortar", Glyph.SCISSORS, PillStyle.SECONDARY, 48, 15f).apply {
            setOnClickListener { lastTrack?.let { act.openEditor(it) } }
        }
        resultActions.addView(playButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = act.dp(6) })
        resultActions.addView(trimButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = act.dp(6) })
        resultCard.addView(resultActions, act.lp(14))
        col.addView(resultCard, act.lp(16))

        // --- Acción principal ---
        downloadButton = PillButton(act, "Descargar MP3", Glyph.DOWNLOAD, PillStyle.PRIMARY, 60, 17f).apply {
            setOnClickListener { startDownload() }
        }
        col.addView(downloadButton, act.lp(24))

        updateButton = PillButton(act, "Actualizar yt-dlp", Glyph.REFRESH, PillStyle.GHOST, 44, 14f).apply {
            setOnClickListener { updateYtDlp() }
        }
        col.addView(updateButton, act.lp(10, width = WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })

        col.addView(act.body("Úsalo solo con contenido propio o de licencia libre.", 12f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
        }, act.lp(6))

        val scroll = ScrollView(act).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(col, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        // Fondo con imagen + degradado oscuro para que el texto se lea
        val backgroundImage = ImageView(act).apply {
            setImageResource(R.drawable.bg_home)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val scrim = View(act).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0x2609080F, 0x1A09080F, 0x5909080F,
                    0xC709080F.toInt(), 0xF209080F.toInt(), Palette.BG
                )
            )
        }
        view = FrameLayout(act).apply {
            addView(backgroundImage, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(scrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        setIdle()
    }

    // ------------------------------------------------------------------

    private fun setIdle() = chip.set("Listo para descargar", Palette.GREEN)

    fun showInitError() {
        chip.set("yt-dlp no se pudo iniciar", Palette.RED)
        showError("No se pudo iniciar yt-dlp", App.initError ?: "Error desconocido")
    }

    fun setUrl(url: String) {
        urlInput.setText(url)
        urlInput.setSelection(url.length)
        resultCard.visibility = View.GONE
        progressCard.visibility = View.GONE
        chip.set("Enlace recibido, listo para descargar", Palette.GREEN)
    }

    private fun pasteFromClipboard() {
        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(act)?.toString().orEmpty()
        val url = urlRegex.find(text)?.value
        if (url != null) {
            setUrl(url)
        } else {
            chip.set("El portapapeles no tiene un enlace", Palette.RED)
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        downloadButton.isEnabled = !value
        updateButton.isEnabled = !value
        pasteButton.isEnabled = !value
        clearButton.isEnabled = !value
        urlInput.isEnabled = !value
        downloadButton.setLabel(if (value) "Trabajando…" else "Descargar MP3")
    }

    private fun showProgress(percent: Int?, message: String) {
        progressCard.visibility = View.VISIBLE
        if (percent == null) {
            progressBar.isIndeterminate = true
            percentText.text = ""
        } else {
            progressBar.isIndeterminate = false
            progressBar.progress = percent
            percentText.text = "$percent%"
        }
        progressText.text = message
    }

    private fun showSuccess(track: Track) {
        lastTrack = track
        progressCard.visibility = View.GONE
        resultTile.removeAllViews()
        resultTile.background = null
        resultTile.addView(act.artTile(track.title, 16, 26), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        resultTitle.text = track.title
        val parts = mutableListOf("Guardado en Música › AudioDL")
        if (track.durationMs > 0) parts += formatTime(track.durationMs)
        if (track.sizeBytes > 0) parts += formatSize(track.sizeBytes)
        resultSub.text = parts.joinToString(" · ")
        resultActions.visibility = View.VISIBLE
        resultCard.visibility = View.VISIBLE
        chip.set("Descarga completa", Palette.GREEN)
    }

    private fun showError(title: String, detail: String) {
        progressCard.visibility = View.GONE
        resultTile.removeAllViews()
        resultTile.background = act.roundRect(0x33FF5C7A, 16)
        resultTile.addView(ImageView(act).apply {
            setImageDrawable(GlyphDrawable(Glyph.CLEAR, Palette.RED))
        }, FrameLayout.LayoutParams(act.dp(26), act.dp(26), Gravity.CENTER))
        resultTitle.text = title
        resultSub.text = detail
        resultActions.visibility = View.GONE
        resultCard.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------

    private fun startDownload() {
        if (busy) return
        val url = urlRegex.find(urlInput.text.toString().trim())?.value
        if (url == null) {
            chip.set("Pega un enlace válido (http/https)", Palette.RED)
            return
        }
        if (!App.initOk) {
            showInitError()
            return
        }

        setBusy(true)
        resultCard.visibility = View.GONE
        chip.set("Descargando…", Palette.AMBER)
        showProgress(null, "Preparando descarga…")

        act.lifecycleScope.launch {
            try {
                val track = withContext(Dispatchers.IO) { downloadAndSave(url) }
                showSuccess(track)
                act.onTrackSaved()
            } catch (e: Exception) {
                chip.set("Algo salió mal", Palette.RED)
                showError("No se pudo descargar", e.message ?: e.toString())
            } finally {
                setBusy(false)
            }
        }
    }

    private suspend fun downloadAndSave(url: String): Track {
        val tmpDir = File(act.cacheDir, "dl")
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
            act.runOnUiThread {
                if (progress > 0f) {
                    showProgress(progress.toInt().coerceIn(0, 100), "Descargando audio…")
                } else if (line.contains("ExtractAudio") || line.contains("ffmpeg", ignoreCase = true)) {
                    showProgress(null, "Convirtiendo a MP3…")
                }
            }
        }

        val mp3 = tmpDir.listFiles { f -> f.extension.equals("mp3", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("No se generó el archivo MP3.")

        withContext(Dispatchers.Main) { showProgress(null, "Guardando en Música › AudioDL…") }

        try {
            val duration = AudioRepo.durationOf(mp3)
            val size = mp3.length()
            val uri = AudioRepo.saveToMusic(act, mp3, mp3.name)
            return Track(uri, mp3.nameWithoutExtension, duration, size)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun updateYtDlp() {
        if (busy) return
        if (!App.initOk) {
            showInitError()
            return
        }
        setBusy(true)
        chip.set("Actualizando yt-dlp…", Palette.AMBER)

        act.lifecycleScope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(act.applicationContext, YoutubeDL.UpdateChannel._STABLE)
                }
                chip.set(
                    when (status) {
                        YoutubeDL.UpdateStatus.DONE -> "yt-dlp actualizado"
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp ya está al día"
                        else -> "Actualización terminada"
                    },
                    Palette.GREEN
                )
            } catch (e: Exception) {
                chip.set("No se pudo actualizar yt-dlp", Palette.RED)
                showError("Error al actualizar", e.message ?: e.toString())
            } finally {
                setBusy(false)
            }
        }
    }
}
