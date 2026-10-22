package com.audiodl.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Forma de onda con dos tiradores para elegir el tramo a conservar.
 * Tocar fuera de los tiradores mueve el cabezal de reproducción.
 */
class TrimView(ctx: Context) : View(ctx) {

    var durationMs = 0L
        set(value) { field = value; invalidate() }
    var startMs = 0L
        private set
    var endMs = 0L
        private set
    var playheadMs = -1L
        set(value) { field = value; invalidate() }
    var peaks: FloatArray? = null
        set(value) { field = value; invalidate() }

    var onRangeChanged: ((Long, Long, Boolean) -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null

    private val barW = ctx.dpf(3f)
    private val barGap = ctx.dpf(2f)
    private val handleW = ctx.dpf(14f)
    private val vPad = ctx.dpf(8f)
    private val corner = ctx.dpf(10f)
    private val touchRadius = ctx.dpf(32f)
    private val gripHalf = ctx.dpf(8f)
    private val gripOffset = ctx.dpf(2.2f)
    private val headDot = ctx.dpf(4.5f)

    private val barOn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.WAVE_OFF }
    private val selFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF }
    private val selStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dpf(1.5f)
        color = 0x80FFFFFF.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.BG
        strokeWidth = ctx.dpf(1.6f)
        strokeCap = Paint.Cap.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = ctx.dpf(2f)
    }
    private val rect = RectF()
    private var dragging = 0 // 0 = nada, 1 = inicio, 2 = fin

    private val trackLeft get() = handleW
    private val trackRight get() = width - handleW

    fun setRange(start: Long, end: Long) {
        val d = durationMs.coerceAtLeast(0L)
        val s = start.coerceIn(0L, d)
        val e = end.coerceIn(s, d)
        startMs = s
        endMs = e
        invalidate()
    }

    private fun minGap(): Long = min(500L, durationMs / 2)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        barOn.shader = LinearGradient(
            handleW, 0f, w - handleW, 0f,
            Palette.GRAD_START, Palette.GRAD_END, Shader.TileMode.CLAMP
        )
    }

    private fun xAt(ms: Long): Float {
        if (durationMs <= 0L) return trackLeft
        return trackLeft + (trackRight - trackLeft) * (ms.toFloat() / durationMs)
    }

    private fun timeAt(x: Float): Long {
        if (durationMs <= 0L) return 0L
        val f = ((x - trackLeft) / (trackRight - trackLeft)).coerceIn(0f, 1f)
        return (f * durationMs).toLong()
    }

    override fun onDraw(canvas: Canvas) {
        val l = trackLeft
        val r = trackRight
        if (r <= l) return
        val t = vPad
        val b = height - vPad
        val mid = (t + b) / 2f
        val h = b - t
        val hasRange = durationMs > 0L
        val sx = if (hasRange) xAt(startMs) else l
        val ex = if (hasRange) xAt(endMs) else r

        if (hasRange) {
            rect.set(sx, t, ex, b)
            canvas.drawRoundRect(rect, corner, corner, selFill)
        }

        val step = barW + barGap
        val n = ((r - l) / step).toInt().coerceAtLeast(1)
        val p = peaks
        for (i in 0 until n) {
            val x = l + i * step
            val v = if (p != null && p.isNotEmpty()) {
                val from = i * p.size / n
                val to = ((i + 1) * p.size / n).coerceAtLeast(from + 1).coerceAtMost(p.size)
                var m = 0f
                for (k in from until to) if (p[k] > m) m = p[k]
                m
            } else {
                0.25f + 0.18f * sin(i * 0.55f) * cos(i * 0.21f)
            }
            val bh = max(barW, v * h * 0.86f)
            val cx = x + barW / 2f
            val inside = hasRange && cx >= sx && cx <= ex
            rect.set(x, mid - bh / 2f, x + barW, mid + bh / 2f)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, if (inside) barOn else barOff)
        }

        if (!hasRange) return

        rect.set(sx, t, ex, b)
        canvas.drawRoundRect(rect, corner, corner, selStroke)
        drawHandle(canvas, sx, t, b, mid)
        drawHandle(canvas, ex, t, b, mid)

        if (playheadMs in 0L..durationMs) {
            val px = xAt(playheadMs)
            canvas.drawLine(px, t, px, b, headPaint)
            canvas.drawCircle(px, t, headDot, headPaint)
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, t: Float, b: Float, mid: Float) {
        rect.set(x - handleW / 2f, t, x + handleW / 2f, b)
        canvas.drawRoundRect(rect, handleW / 2f, handleW / 2f, handlePaint)
        canvas.drawLine(x - gripOffset, mid - gripHalf, x - gripOffset, mid + gripHalf, gripPaint)
        canvas.drawLine(x + gripOffset, mid - gripHalf, x + gripOffset, mid + gripHalf, gripPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L || !isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val ds = abs(event.x - xAt(startMs))
                val de = abs(event.x - xAt(endMs))
                dragging = when {
                    ds <= touchRadius && ds <= de -> 1
                    de <= touchRadius -> 2
                    else -> 0
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                if (dragging == 0) onSeek?.invoke(timeAt(event.x))
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging != 0) {
                    val tm = timeAt(event.x)
                    val gap = minGap()
                    if (dragging == 1) {
                        startMs = tm.coerceIn(0L, (endMs - gap).coerceAtLeast(0L))
                    } else {
                        endMs = tm.coerceIn((startMs + gap).coerceAtMost(durationMs), durationMs)
                    }
                    invalidate()
                    onRangeChanged?.invoke(startMs, endMs, false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging != 0) onRangeChanged?.invoke(startMs, endMs, true)
                dragging = 0
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
