package com.audiodl.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Paleta
// ---------------------------------------------------------------------------

object Palette {
    val BG = 0xFF09080F.toInt()
    val SURFACE = 0xE615131F.toInt()
    val SURFACE_2 = 0xFF1F1C2D.toInt()
    val STROKE = 0xFF2C293E.toInt()
    val TEXT = 0xFFF6F5FB.toInt()
    val TEXT_SOFT = 0xFFC9C6D8.toInt()
    val MUTED = 0xFF9E9AB4.toInt()
    val EYEBROW = 0xFF9D8CFF.toInt()
    val GRAD_START = 0xFF7B5CFF.toInt()
    val GRAD_END = 0xFFE45BC6.toInt()
    val GREEN = 0xFF3DDC97.toInt()
    val AMBER = 0xFFFFB547.toInt()
    val RED = 0xFFFF5C7A.toInt()
    val WAVE_OFF = 0xFF3A3652.toInt()

    private val tiles = listOf(
        intArrayOf(0xFF7B5CFF.toInt(), 0xFFE45BC6.toInt()),
        intArrayOf(0xFF3E7BFA.toInt(), 0xFF7B5CFF.toInt()),
        intArrayOf(0xFFFF7A59.toInt(), 0xFFE45BC6.toInt()),
        intArrayOf(0xFF1FC8A9.toInt(), 0xFF3E7BFA.toInt()),
        intArrayOf(0xFFFFB547.toInt(), 0xFFFF5C7A.toInt()),
    )

    fun tileColors(key: String): IntArray = tiles[abs(key.hashCode() % tiles.size)]
}

// ---------------------------------------------------------------------------
// Medidas y drawables
// ---------------------------------------------------------------------------

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.roundRect(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radiusDp.toFloat())
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

fun Context.gradient(
    radiusDp: Int = 100,
    colors: IntArray = intArrayOf(Palette.GRAD_START, Palette.GRAD_END),
    orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT,
): GradientDrawable = GradientDrawable(orientation, colors).apply {
    cornerRadius = dpf(radiusDp.toFloat())
}

fun ripple(content: Drawable, mask: Drawable? = null): Drawable =
    RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), content, mask)

fun Context.appBackground(): Drawable {
    val glowTop = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(0x993A2C96.toInt(), 0x00000000)
        gradientRadius = dpf(420f)
        setGradientCenter(1.0f, 0.0f)
    }
    val glowBottom = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(0x66862A72, 0x00000000)
        gradientRadius = dpf(360f)
        setGradientCenter(0.0f, 0.8f)
    }
    return LayerDrawable(arrayOf(ColorDrawable(Palette.BG), glowTop, glowBottom))
}

fun Context.gradientProgress(): Drawable {
    val bg = roundRect(Palette.STROKE, 100)
    val fg = ClipDrawable(gradient(), Gravity.START, ClipDrawable.HORIZONTAL)
    return LayerDrawable(arrayOf(bg, fg)).apply {
        setId(0, android.R.id.background)
        setId(1, android.R.id.progress)
    }
}

// ---------------------------------------------------------------------------
// Texto y contenedores
// ---------------------------------------------------------------------------

fun Context.lp(top: Int = 0, height: Int = WRAP_CONTENT, width: Int = MATCH_PARENT) =
    LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top) }

fun Context.eyebrow(text: String) = TextView(this).apply {
    this.text = text.uppercase(Locale.getDefault())
    textSize = 13f
    letterSpacing = 0.32f
    setTextColor(Palette.EYEBROW)
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

fun Context.headline(text: String, size: Float = 40f) = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(Palette.TEXT)
    typeface = Typeface.create("sans-serif", Typeface.BOLD)
    letterSpacing = -0.02f
    setLineSpacing(0f, 0.95f)
}

fun Context.label(text: String) = TextView(this).apply {
    this.text = text.uppercase(Locale.getDefault())
    textSize = 12f
    letterSpacing = 0.14f
    setTextColor(Palette.MUTED)
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

fun Context.body(
    text: String = "",
    size: Float = 15f,
    color: Int = Palette.TEXT,
    bold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(color)
    typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
    if (maxLines != Int.MAX_VALUE) {
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
    }
}

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundRect(Palette.SURFACE, 24, Palette.STROKE)
    setPadding(dp(18), dp(18), dp(18), dp(18))
}

fun Context.artTile(key: String, radiusDp: Int, iconDp: Int): FrameLayout {
    val ctx = this
    return FrameLayout(ctx).apply {
        background = gradient(radiusDp, Palette.tileColors(key), GradientDrawable.Orientation.TL_BR)
        addView(
            ImageView(ctx).apply {
                setImageDrawable(GlyphDrawable(Glyph.MUSIC, Color.WHITE))
                alpha = 0.92f
            },
            FrameLayout.LayoutParams(ctx.dp(iconDp), ctx.dp(iconDp), Gravity.CENTER)
        )
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

fun formatTimePrecise(ms: Long): String {
    val v = ms.coerceAtLeast(0)
    val s = v / 1000
    return String.format(Locale.US, "%d:%02d.%d", s / 60, s % 60, (v % 1000) / 100)
}

fun formatSize(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)

// ---------------------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------------------

enum class PillStyle { PRIMARY, SECONDARY, GHOST }

class PillButton(
    ctx: Context,
    label: String,
    glyph: Glyph?,
    style: PillStyle = PillStyle.SECONDARY,
    heightDp: Int = 56,
    textSp: Float = 16f,
) : LinearLayout(ctx) {

    private val iconView = ImageView(ctx)
    private val textView = TextView(ctx)
    private val fg = if (style == PillStyle.GHOST) Palette.MUTED else Palette.TEXT

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = ctx.dp(heightDp)
        setPadding(ctx.dp(18), 0, ctx.dp(18), 0)
        background = when (style) {
            PillStyle.PRIMARY -> ripple(ctx.gradient())
            PillStyle.SECONDARY -> ripple(ctx.roundRect(Palette.SURFACE, 100, Palette.STROKE))
            PillStyle.GHOST -> ripple(ColorDrawable(Color.TRANSPARENT), ctx.roundRect(Color.WHITE, 100))
        }
        isClickable = true
        isFocusable = true

        addView(iconView, LayoutParams(ctx.dp(20), ctx.dp(20)).apply { marginEnd = ctx.dp(10) })
        textView.textSize = textSp
        textView.setTextColor(fg)
        textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textView.maxLines = 1
        textView.ellipsize = TextUtils.TruncateAt.END
        addView(textView)

        setLabel(label)
        setGlyph(glyph)
    }

    fun setLabel(text: String) {
        textView.text = text
    }

    fun setGlyph(glyph: Glyph?) {
        if (glyph == null) {
            iconView.visibility = GONE
        } else {
            iconView.visibility = VISIBLE
            iconView.setImageDrawable(GlyphDrawable(glyph, fg))
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.45f
    }
}

class CircleButton(
    ctx: Context,
    glyph: Glyph,
    sizeDp: Int,
    primary: Boolean = false,
    iconDp: Int = 22,
) : FrameLayout(ctx) {

    val sizePx = ctx.dp(sizeDp)
    private val image = ImageView(ctx)

    init {
        val bg = if (primary) {
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Palette.GRAD_START, Palette.GRAD_END)
            ).apply { shape = GradientDrawable.OVAL }
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.SURFACE_2)
                setStroke(ctx.dp(1), Palette.STROKE)
            }
        }
        background = ripple(bg)
        isClickable = true
        isFocusable = true
        addView(image, LayoutParams(ctx.dp(iconDp), ctx.dp(iconDp), Gravity.CENTER))
        setGlyph(glyph)
    }

    fun setGlyph(glyph: Glyph) {
        image.setImageDrawable(GlyphDrawable(glyph, Palette.TEXT))
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }
}

class StatusChip(ctx: Context) : LinearLayout(ctx) {
    private val dot = View(ctx)
    private val text = TextView(ctx)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ctx.roundRect(0x14FFFFFF, 100, Palette.STROKE)
        setPadding(ctx.dp(16), ctx.dp(9), ctx.dp(18), ctx.dp(9))
        addView(dot, LayoutParams(ctx.dp(9), ctx.dp(9)).apply { marginEnd = ctx.dp(11) })
        text.textSize = 14f
        text.setTextColor(Palette.TEXT_SOFT)
        text.maxLines = 2
        text.ellipsize = TextUtils.TruncateAt.END
        addView(text)
        set("", Palette.GREEN)
    }

    fun set(message: String, color: Int) {
        text.text = message
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }
}

// ---------------------------------------------------------------------------
// Íconos dibujados en código (cuadrícula de 24 × 24)
// ---------------------------------------------------------------------------

enum class Glyph {
    PLAY, PAUSE, DOWNLOAD, MUSIC, SCISSORS, PASTE, CLEAR, CHECK, BACK, REFRESH, REWIND, FORWARD
}

class GlyphDrawable(private val glyph: Glyph, color: Int) : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
        this.color = color
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(1.6f)
        this.color = color
    }
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = min(b.width(), b.height()).toFloat()
        if (size <= 0f) return
        val s = size / 24f
        canvas.save()
        canvas.translate(b.exactCenterX() - 12f * s, b.exactCenterY() - 12f * s)
        canvas.scale(s, s)
        path.reset()

        when (glyph) {
            Glyph.PLAY -> {
                path.moveTo(8f, 5f); path.lineTo(19.5f, 12f); path.lineTo(8f, 19f); path.close()
                canvas.drawPath(path, fill)
            }
            Glyph.PAUSE -> {
                rect.set(6.5f, 5f, 10f, 19f); canvas.drawRoundRect(rect, 1.4f, 1.4f, fill)
                rect.set(14f, 5f, 17.5f, 19f); canvas.drawRoundRect(rect, 1.4f, 1.4f, fill)
            }
            Glyph.DOWNLOAD -> {
                canvas.drawLine(12f, 4f, 12f, 15f, stroke)
                poly(canvas, 7f, 10.5f, 12f, 15.5f, 17f, 10.5f)
                canvas.drawLine(5f, 20f, 19f, 20f, stroke)
            }
            Glyph.MUSIC -> {
                poly(canvas, 9f, 17.5f, 9f, 6f, 19f, 4f, 19f, 15.5f)
                canvas.drawCircle(6.8f, 17.5f, 2.4f, fill)
                canvas.drawCircle(16.8f, 15.5f, 2.4f, fill)
            }
            Glyph.SCISSORS -> {
                canvas.drawCircle(6.5f, 7f, 2.8f, stroke)
                canvas.drawCircle(6.5f, 17f, 2.8f, stroke)
                canvas.drawLine(8.9f, 8.6f, 20f, 17.5f, stroke)
                canvas.drawLine(8.9f, 15.4f, 20f, 6.5f, stroke)
            }
            Glyph.PASTE -> {
                rect.set(5.5f, 5f, 18.5f, 21f); canvas.drawRoundRect(rect, 2.5f, 2.5f, stroke)
                rect.set(9f, 3f, 15f, 7f); canvas.drawRoundRect(rect, 1.2f, 1.2f, fill)
            }
            Glyph.CLEAR -> {
                canvas.drawLine(7f, 7f, 17f, 17f, stroke)
                canvas.drawLine(17f, 7f, 7f, 17f, stroke)
            }
            Glyph.CHECK -> poly(canvas, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
            Glyph.BACK -> poly(canvas, 15f, 5f, 8f, 12f, 15f, 19f)
            Glyph.REFRESH -> {
                rect.set(5f, 5f, 19f, 19f)
                canvas.drawArc(rect, 20f, 300f, false, stroke)
                poly(canvas, 18f, 3.8f, 18f, 8f, 13.8f, 8f)
            }
            Glyph.REWIND -> {
                tri(canvas, 20f, 6.5f, 12.5f, 12f, 20f, 17.5f)
                tri(canvas, 11.5f, 6.5f, 4f, 12f, 11.5f, 17.5f)
            }
            Glyph.FORWARD -> {
                tri(canvas, 4f, 6.5f, 11.5f, 12f, 4f, 17.5f)
                tri(canvas, 12.5f, 6.5f, 20f, 12f, 12.5f, 17.5f)
            }
        }
        canvas.restore()
    }

    private fun poly(canvas: Canvas, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1]); i += 2
        }
        canvas.drawPath(path, stroke)
    }

    private fun tri(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset()
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        canvas.drawPath(path, fill)
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
