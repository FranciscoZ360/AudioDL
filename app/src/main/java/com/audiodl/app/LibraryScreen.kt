package com.audiodl.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryScreen(private val act: MainActivity) {

    val view: View

    private val chip = StatusChip(act)
    private val nowTile: FrameLayout
    private val nowTitle: TextView
    private val nowSub: TextView
    private val seek: SeekBar
    private val posText: TextView
    private val durText: TextView
    private val playButton: CircleButton
    private val rewindButton: CircleButton
    private val forwardButton: CircleButton
    private val trimButton: PillButton
    private val permissionHint: TextView
    private val emptyCard: LinearLayout
    private val listContainer: LinearLayout

    private var tracks: List<Track> = emptyList()
    private var userSeeking = false
    private var visible = false
    private var askedPermission = false
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            if (visible) handler.postDelayed(this, 300)
        }
    }

    init {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(24), act.dp(28), act.dp(24), act.dp(24))
        }

        col.addView(act.eyebrow("Biblioteca"))
        col.addView(act.headline("Tus audios."), act.lp(14))
        col.addView(chip, act.lp(20, width = WRAP_CONTENT))

        // --- Reproductor ---
        val nowCard = act.card().apply { setPadding(act.dp(20), act.dp(20), act.dp(20), act.dp(20)) }
        val top = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nowTile = FrameLayout(act)
        top.addView(nowTile, LinearLayout.LayoutParams(act.dp(72), act.dp(72)))
        val texts = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        nowTitle = act.body("", 17f, Palette.TEXT, bold = true, maxLines = 2)
        nowSub = act.body("", 13f, Palette.MUTED, maxLines = 1)
        texts.addView(nowTitle)
        texts.addView(nowSub, act.lp(4))
        top.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = act.dp(16) })
        nowCard.addView(top)

        seek = SeekBar(act).apply {
            progressDrawable = act.gradientProgress()
            thumb = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setSize(act.dp(16), act.dp(16))
            }
            splitTrack = false
            minHeight = act.dp(6)
            maxHeight = act.dp(6)
            setPadding(act.dp(8), 0, act.dp(8), 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) posText.text = formatTime(progress.toLong())
                }
                override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    act.player.seekTo(sb.progress.toLong())
                    userSeeking = false
                }
            })
        }
        nowCard.addView(seek, act.lp(20, height = act.dp(28)))

        val times = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        posText = act.body("0:00", 12f, Palette.MUTED)
        durText = act.body("0:00", 12f, Palette.MUTED).apply { gravity = Gravity.END }
        times.addView(posText, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        times.addView(durText, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        times.setPadding(act.dp(8), 0, act.dp(8), 0)
        nowCard.addView(times, act.lp(2))

        val controls = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rewindButton = CircleButton(act, Glyph.REWIND, 52).apply { setOnClickListener { act.player.skip(-10_000L) } }
        playButton = CircleButton(act, Glyph.PLAY, 74, primary = true, iconDp = 30).apply {
            setOnClickListener {
                if (act.player.current == null) tracks.firstOrNull()?.let { play(it) } else act.player.toggle()
            }
        }
        forwardButton = CircleButton(act, Glyph.FORWARD, 52).apply { setOnClickListener { act.player.skip(10_000L) } }
        controls.addView(rewindButton, LinearLayout.LayoutParams(rewindButton.sizePx, rewindButton.sizePx))
        controls.addView(playButton, LinearLayout.LayoutParams(playButton.sizePx, playButton.sizePx).apply {
            marginStart = act.dp(28); marginEnd = act.dp(28)
        })
        controls.addView(forwardButton, LinearLayout.LayoutParams(forwardButton.sizePx, forwardButton.sizePx))
        nowCard.addView(controls, act.lp(14))

        trimButton = PillButton(act, "Recortar este audio", Glyph.SCISSORS, PillStyle.SECONDARY, 48, 15f).apply {
            setOnClickListener { act.player.current?.let { act.openEditor(it) } }
        }
        nowCard.addView(trimButton, act.lp(18))
        col.addView(nowCard, act.lp(24))

        // --- Lista ---
        col.addView(act.label("Todos los audios"), act.lp(28))

        permissionHint = act.body(
            "Toca aquí para ver también los audios descargados antes de reinstalar la app.",
            13f, Palette.EYEBROW
        ).apply {
            background = ripple(act.roundRect(0x147B5CFF, 16, 0x557B5CFF))
            setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(12))
            isClickable = true
            setOnClickListener { act.requestAudioPermission() }
            visibility = View.GONE
        }
        col.addView(permissionHint, act.lp(12))

        emptyCard = act.card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(act.artTile("vacío", 18, 28), LinearLayout.LayoutParams(act.dp(60), act.dp(60)))
            addView(act.body("Aún no hay audios", 16f, Palette.TEXT, bold = true).apply { gravity = Gravity.CENTER }, act.lp(14))
            addView(act.body("Descarga uno desde la pestaña Descargar y aparecerá aquí.", 14f, Palette.MUTED).apply {
                gravity = Gravity.CENTER
            }, act.lp(4))
            visibility = View.GONE
        }
        col.addView(emptyCard, act.lp(12))

        listContainer = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listContainer, act.lp(2))

        view = ScrollView(act).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(col, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        act.player.onChange = {
            updatePlayerUi()
            renderList()
        }
        act.player.onError = { msg -> chip.set(msg, Palette.RED) }
        updatePlayerUi()
    }

    // ------------------------------------------------------------------

    fun onShown() {
        visible = true
        if (!askedPermission && !act.hasAudioPermission()) {
            askedPermission = true
            act.requestAudioPermission()
        }
        refresh()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    fun onHidden() {
        visible = false
        handler.removeCallbacks(ticker)
    }

    fun refresh() {
        act.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { AudioRepo.list(act) }.getOrDefault(emptyList())
            }
            tracks = list
            renderList()
        }
    }

    fun play(track: Track) {
        act.player.play(track)
    }

    // ------------------------------------------------------------------

    private fun renderList() {
        val current = act.player.current
        chip.set(
            when (tracks.size) {
                0 -> "Biblioteca vacía"
                1 -> "1 audio · Música › AudioDL"
                else -> "${tracks.size} audios · Música › AudioDL"
            },
            if (tracks.isEmpty()) Palette.MUTED else Palette.GREEN
        )
        permissionHint.visibility = if (act.hasAudioPermission()) View.GONE else View.VISIBLE
        emptyCard.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE

        listContainer.removeAllViews()
        for (track in tracks) {
            val isCurrent = current?.uri == track.uri
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(act.dp(12), act.dp(12), act.dp(12), act.dp(12))
                background = ripple(
                    act.roundRect(
                        if (isCurrent) Palette.SURFACE_2 else Palette.SURFACE, 20,
                        if (isCurrent) 0x887B5CFF.toInt() else Palette.STROKE
                    )
                )
                isClickable = true
                setOnClickListener { if (isCurrent) act.player.toggle() else play(track) }
            }
            row.addView(act.artTile(track.title, 14, 20), LinearLayout.LayoutParams(act.dp(48), act.dp(48)))

            val texts = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(act.body(track.title, 15f, if (isCurrent) Palette.EYEBROW else Palette.TEXT, bold = true, maxLines = 1))
            val meta = mutableListOf<String>()
            if (isCurrent) meta += if (act.player.isPlaying) "Sonando" else "En pausa"
            if (track.durationMs > 0) meta += formatTime(track.durationMs)
            if (track.sizeBytes > 0) meta += formatSize(track.sizeBytes)
            texts.addView(act.body(meta.joinToString(" · "), 13f, Palette.MUTED, maxLines = 1), act.lp(3))
            row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = act.dp(14); marginEnd = act.dp(10)
            })

            val cut = CircleButton(act, Glyph.SCISSORS, 40, iconDp = 18).apply {
                setOnClickListener { act.openEditor(track) }
            }
            row.addView(cut, LinearLayout.LayoutParams(cut.sizePx, cut.sizePx))
            listContainer.addView(row, act.lp(10))
        }
    }

    private fun updatePlayerUi() {
        val t = act.player.current
        nowTile.removeAllViews()
        nowTile.addView(
            act.artTile(t?.title ?: "AudioDL", 20, 32),
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )
        if (t == null) {
            nowTitle.text = "Elige un audio"
            nowSub.text = "Toca una canción de la lista"
        } else {
            nowTitle.text = t.title
            nowSub.text = if (act.player.isPlaying) "Reproduciendo" else "En pausa"
        }
        playButton.setGlyph(if (act.player.isPlaying) Glyph.PAUSE else Glyph.PLAY)
        val has = t != null
        seek.isEnabled = has
        rewindButton.isEnabled = has
        forwardButton.isEnabled = has
        trimButton.isEnabled = has
        updateProgress()
    }

    private fun updateProgress() {
        if (userSeeking) return
        val d = act.player.durationMs
        val p = act.player.positionMs
        seek.max = d.coerceAtLeast(1L).toInt()
        seek.progress = p.toInt()
        posText.text = formatTime(p)
        durText.text = formatTime(d)
    }
}
