package dev.orbit.clock.core

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Shared black, charcoal and amber values for native views and widget artwork. */
object OrbitColors {
    val background = 0xff050505.toInt()
    val surface = 0xff191919.toInt()
    val surfaceHigh = 0xff252525.toInt()
    val text = 0xfff5f3f0.toInt()
    val muted = 0xffc6c4c1.toInt()
    val amber = 0xffffc15a.toInt()
    val onAmber = 0xff291900.toInt()
    val amberContainer = 0xff382800.toInt()
    val outline = 0xff66615a.toInt()
}

/** Fixed Material roles keep the approved theme consistent across system modes. */
@Suppress("UNUSED_PARAMETER")
class OrbitPalette(context: Context) {
    val surface = OrbitColors.background
    val onSurface = OrbitColors.text
    val primary = OrbitColors.amber
    val onPrimary = OrbitColors.onAmber
    val primaryContainer = OrbitColors.amberContainer
    val onPrimaryContainer = OrbitColors.amber
    val secondaryContainer = OrbitColors.surfaceHigh
    val onSecondaryContainer = OrbitColors.text
    val muted = OrbitColors.muted
    val outline = OrbitColors.outline
    val container = OrbitColors.surface
    val containerHigh = OrbitColors.surfaceHigh
}

/** Scalable clock artwork; no bitmap assets and no dependency on screen resolution. */
class OrbitDial(context: Context, private val colors: OrbitPalette) : View(context) {
    var hour = 7
    var minute = 0
    var second = 0
    var progress = -1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hatch: OrbitHatchDrawable? = null
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val radius = min(w, h) * .47f
        hatch = OrbitHatchDrawable(Color.TRANSPARENT, radius, resources.displayMetrics.density).apply {
            setBounds((w / 2f - radius).roundToInt(), (h / 2f - radius).roundToInt(),
                (w / 2f + radius).roundToInt(), (h / 2f + radius).roundToInt())
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val radius = min(width, height) * .47f
        paint.style = Paint.Style.FILL; paint.color = colors.primaryContainer
        canvas.drawCircle(cx, cy, radius, paint)
        hatch?.draw(canvas)
        for (i in 0 until 12) {
            val angle = i * PI / 6
            val outer = radius * .87f; val inner = radius * .79f
            paint.color = colors.onPrimaryContainer; paint.alpha = if (i % 3 == 0) 190 else 65
            paint.strokeWidth = radius * .025f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx + sin(angle).toFloat() * inner, cy - cos(angle).toFloat() * inner,
                cx + sin(angle).toFloat() * outer, cy - cos(angle).toFloat() * outer, paint)
        }
        paint.alpha = 255
        if (progress >= 0) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .035f
            paint.color = colors.primary
            canvas.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, -90f, progress * 360, false, paint)
            paint.style = Paint.Style.FILL
            return
        }
        fun hand(angle: Double, length: Float, width: Float, color: Int) {
            paint.color = color; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, cy, cx + sin(angle).toFloat() * length, cy - cos(angle).toFloat() * length, paint)
        }
        hand((hour % 12 + minute / 60.0) * PI / 6, radius * .44f, radius * .08f, colors.onPrimaryContainer)
        hand((minute + second / 60.0) * PI / 30, radius * .64f, radius * .055f, colors.onPrimaryContainer)
        paint.color = colors.primary; canvas.drawCircle(cx, cy, radius * .065f, paint)
    }
}
