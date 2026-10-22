package com.audiodl.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

class EditorScreen(private val act: MainActivity, private val track: Track) {

    val view: View

    private val chip = StatusChip(act)
    private val trim = TrimView(act)
    private val startText: TextView
    private val lengthText: TextView
    private val endText: TextView
    private val fadeSwitch: SwitchCompat
    private val listenButton: PillButton
    private val saveButton: PillButton
    private val resultText: TextView
    private val nudgeChips = mutableListOf<TextView>()

    private val workDir = File(act.cacheDir, "editor")
    private var source: File? = null
    private var duration = 0L
    private var preview: MediaPlayer? = null
    private var previewReady = false
    private var busy = false
    private var released = false

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val p = preview ?: return
            if (!previewReady || !p.isPlaying) return
            val pos = p.currentPosition.toLong()
            trim.playheadMs = pos
            if (pos >= trim.endMs) {
                stopPreview()
                trim.playheadMs = trim.endMs
                return
            }
            handler.postDelayed(this, 40)
        }
    }

    init {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(24), act.dp(20), act.dp(24), act.dp(32))
        }

        // --- Barra superior ---
        val topRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = CircleButton(act, Glyph.BACK, 44, iconDp = 20).apply { setOnClickListener { act.closeEditor() } }
        topRow.addView(back, LinearLayout.LayoutParams(back.sizePx, back.sizePx))
        topRow.addView(act.eyebrow("Editor"), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            marginStart = act.dp(16)
        })
        col.addView(topRow)

        col.addView(act.headline(track.title, 28f).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, act.lp(20))
        col.addView(chip, act.lp(16, width = WRAP_CONTENT))

        // --- Forma de onda ---
        val waveCard = act.card()
        waveCard.addView(act.label("Selección"))
        trim.onRangeChanged = { _, _, finished ->
            updateTimes()
            resultText.visibility = View.GONE
            if (finished && isPreviewPlaying()) preview?.seekTo(trim.startMs.toInt())
        }
        trim.onSeek = { ms ->
            trim.playheadMs = ms
            if (isPreviewPlaying()) preview?.seekTo(ms.toInt())
        }
        waveCard.addView(trim, act.lp(14, height = act.dp(150)))

        val timesRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        startText = act.body("0:00.0", 18f, Palette.TEXT, bold = true)
        lengthText = act.body("0:00.0", 18f, Palette.TEXT, bold = true)
        endText = act.body("0:00.0", 18f, Palette.TEXT, bold = true)
        timesRow.addView(timeColumn("Inicio", startText, Gravity.START), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        timesRow.addView(timeColumn("Duración", lengthText, Gravity.CENTER_HORIZONTAL), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        timesRow.addView(timeColumn("Fin", endText, Gravity.END), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        waveCard.addView(timesRow, act.lp(16))
        col.addView(waveCard, act.lp(24))

        // --- Ajuste fino ---
        val fineCard = act.card()
        fineCard.addView(act.label("Ajuste fino"))
        fineCard.addView(nudgeRow("Inicio", true), act.lp(14))
        fineCard.addView(nudgeRow("Fin", false), act.lp(10))
        col.addView(fineCard, act.lp(16))

        // --- Opciones ---
        val optCard = act.card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val optTexts = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        optTexts.addView(act.body("Fundido suave", 16f, Palette.TEXT, bold = true))
        optTexts.addView(act.body("Entrada y salida graduales del volumen", 13f, Palette.MUTED), act.lp(2))
        optCard.addView(optTexts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        fadeSwitch = SwitchCompat(act).apply {
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Palette.TEXT_SOFT))
            trackTintList = ColorStateList(states, intArrayOf(Palette.GRAD_END, Palette.STROKE))
        }
        optCard.addView(fadeSwitch)
        col.addView(optCard, act.lp(16))

        // --- Acciones ---
        val actions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        listenButton = PillButton(act, "Escuchar", Glyph.PLAY, PillStyle.SECONDARY, 58, 16f).apply {
            setOnClickListener { togglePreview() }
        }
        saveButton = PillButton(act, "Guardar", Glyph.CHECK, PillStyle.PRIMARY, 58, 16f).apply {
            setOnClickListener { save() }
        }
        actions.addView(listenButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = act.dp(6) })
        actions.addView(saveButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.25f).apply { marginStart = act.dp(6) })
        col.addView(actions, act.lp(24))

        resultText = act.body("", 14f, Palette.GREEN).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        col.addView(resultText, act.lp(14))

        view = ScrollView(act).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isClickable = true
            background = act.appBackground()
            addView(col, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        load()
    }

    // ------------------------------------------------------------------

    private fun timeColumn(title: String, value: TextView, gravity: Int): LinearLayout =
        LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            this.gravity = gravity
            addView(act.label(title))
            addView(value, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = act.dp(4) })
        }

    private fun nudgeRow(title: String, isStart: Boolean): LinearLayout {
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(act.body(title, 15f, Palette.TEXT_SOFT), LinearLayout.LayoutParams(act.dp(58), WRAP_CONTENT))
        listOf("−1 s" to -1000L, "−0.1" to -100L, "+0.1" to 100L, "+1 s" to 1000L).forEachIndexed { i, (label, delta) ->
            val chipView = act.body(label, 14f, Palette.TEXT, bold = true).apply {
                gravity = Gravity.CENTER
                background = ripple(act.roundRect(Palette.SURFACE_2, 100, Palette.STROKE))
                setPadding(0, act.dp(10), 0, act.dp(10))
                isClickable = true
                setOnClickListener { nudge(isStart, delta) }
            }
            nudgeChips += chipView
            row.addView(chipView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = act.dp(6)
            })
        }
        return row
    }

    private fun nudge(isStart: Boolean, delta: Long) {
        if (duration <= 0L) return
        val gap = min(500L, duration / 2)
        val s = trim.startMs
        val e = trim.endMs
        if (isStart) {
            trim.setRange((s + delta).coerceIn(0L, (e - gap).coerceAtLeast(0L)), e)
        } else {
            trim.setRange(s, (e + delta).coerceIn((s + gap).coerceAtMost(duration), duration))
        }
        updateTimes()
        resultText.visibility = View.GONE
        if (isPreviewPlaying()) preview?.seekTo(trim.startMs.toInt())
    }

    private fun updateTimes() {
        startText.text = formatTimePrecise(trim.startMs)
        endText.text = formatTimePrecise(trim.endMs)
        lengthText.text = formatTimePrecise(trim.endMs - trim.startMs)
    }

    private fun setControlsEnabled(enabled: Boolean) {
        trim.isEnabled = enabled
        listenButton.isEnabled = enabled
        saveButton.isEnabled = enabled
        fadeSwitch.isEnabled = enabled
        nudgeChips.forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.45f
        }
    }

    // ------------------------------------------------------------------

    private fun load() {
        setControlsEnabled(false)
        chip.set("Preparando el audio…", Palette.AMBER)
        act.lifecycleScope.launch {
            try {
                val (file, dur) = withContext(Dispatchers.IO) {
                    workDir.deleteRecursively()
                    workDir.mkdirs()
                    val f = AudioRepo.copyToFile(act, track.uri, File(workDir, "source.mp3"))
                    f to AudioRepo.durationOf(f)
                }
                if (released) return@launch
                if (dur <= 0L) throw IllegalStateException("No se pudo leer la duración del audio.")
                source = file
                duration = dur
                trim.durationMs = dur
                trim.setRange(0L, dur)
                updateTimes()
                preparePreview(file)
                setControlsEnabled(true)
                chip.set("Arrastra los extremos para recortar", Palette.GREEN)

                val peaks = withContext(Dispatchers.IO) {
                    runCatching { FfmpegTool.peaks(act, file, 400) }.getOrNull()
                }
                if (!released && peaks != null) trim.peaks = peaks
            } catch (e: Exception) {
                if (!released) chip.set("Error: ${e.message ?: e}", Palette.RED)
            }
        }
    }

    private fun preparePreview(file: File) {
        preview = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { previewReady = true }
            setOnCompletionListener { stopPreview() }
            prepareAsync()
        }
    }

    private fun isPreviewPlaying(): Boolean = previewReady && preview?.isPlaying == true

    private fun togglePreview() {
        val p = preview ?: return
        if (!previewReady) return
        if (p.isPlaying) {
            stopPreview()
            return
        }
        val from = if (trim.playheadMs >= trim.startMs && trim.playheadMs < trim.endMs - 200) {
            trim.playheadMs
        } else {
            trim.startMs
        }
        p.seekTo(from.toInt())
        p.start()
        trim.playheadMs = from
        listenButton.setLabel("Detener")
        listenButton.setGlyph(Glyph.PAUSE)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun stopPreview() {
        if (isPreviewPlaying()) preview?.pause()
        handler.removeCallbacks(tick)
        listenButton.setLabel("Escuchar")
        listenButton.setGlyph(Glyph.PLAY)
    }

    private fun save() {
        if (busy) return
        val src = source ?: return
        stopPreview()
        busy = true
        setControlsEnabled(false)
        saveButton.setLabel("Guardando…")
        resultText.visibility = View.GONE
        chip.set("Recortando…", Palette.AMBER)

        val s = trim.startMs
        val e = trim.endMs
        val fade = fadeSwitch.isChecked

        act.lifecycleScope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    val out = File(workDir, "recorte.mp3")
                    out.delete()
                    FfmpegTool.trim(act, src, out, s, e, fade)
                    val displayName = "${track.title} (recorte).mp3"
                    AudioRepo.saveToMusic(act, out, displayName)
                    out.delete()
                    displayName.removeSuffix(".mp3")
                }
                if (released) return@launch
                chip.set("Recorte guardado", Palette.GREEN)
                resultText.setTextColor(Palette.GREEN)
                resultText.text = "✓ Guardado en Música › AudioDL\n$name"
                resultText.visibility = View.VISIBLE
                act.onTrackSaved()
            } catch (ex: Exception) {
                if (released) return@launch
                chip.set("No se pudo recortar", Palette.RED)
                resultText.setTextColor(Palette.RED)
                resultText.text = ex.message ?: ex.toString()
                resultText.visibility = View.VISIBLE
            } finally {
                busy = false
                if (!released) {
                    setControlsEnabled(true)
                    saveButton.setLabel("Guardar")
                }
            }
        }
    }

    fun release() {
        released = true
        handler.removeCallbacks(tick)
        preview?.release()
        preview = null
        previewReady = false
        workDir.deleteRecursively()
    }
}
