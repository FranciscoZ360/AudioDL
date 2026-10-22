package com.audiodl.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** Reproductor sencillo basado en MediaPlayer. */
class AudioPlayer(private val ctx: Context) {

    private var mp: MediaPlayer? = null
    private var prepared = false

    var current: Track? = null
        private set
    var onChange: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val isPlaying: Boolean get() = prepared && mp?.isPlaying == true
    val positionMs: Long get() = if (prepared) (mp?.currentPosition ?: 0).toLong() else 0L
    val durationMs: Long
        get() = if (prepared) (mp?.duration ?: 0).toLong() else (current?.durationMs ?: 0L)

    fun play(track: Track) {
        release()
        current = track
        val player = MediaPlayer()
        mp = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        player.setOnPreparedListener {
            prepared = true
            it.start()
            onChange?.invoke()
        }
        player.setOnCompletionListener { onChange?.invoke() }
        player.setOnErrorListener { _, what, extra ->
            prepared = false
            onError?.invoke("No se pudo reproducir ($what/$extra)")
            onChange?.invoke()
            true
        }
        try {
            player.setDataSource(ctx, track.uri)
            player.prepareAsync()
        } catch (e: Exception) {
            release()
            onError?.invoke(e.message ?: "No se pudo abrir el audio.")
        }
        onChange?.invoke()
    }

    fun toggle() {
        val p = mp
        if (p == null) {
            current?.let { play(it) }
            return
        }
        if (!prepared) return
        if (p.isPlaying) p.pause() else p.start()
        onChange?.invoke()
    }

    fun pause() {
        if (prepared && mp?.isPlaying == true) {
            mp?.pause()
            onChange?.invoke()
        }
    }

    fun seekTo(ms: Long) {
        if (prepared) mp?.seekTo(ms.toInt())
    }

    fun skip(deltaMs: Long) {
        if (!prepared) return
        seekTo((positionMs + deltaMs).coerceIn(0L, durationMs))
    }

    fun release() {
        mp?.release()
        mp = null
        prepared = false
    }
}
