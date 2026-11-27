package dev.orbit.clock.core

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Quiet diagonal detail borrowed from the watch alarm rail. The patch occupies
 * only the right edge, leaving the main text area calm. Live views retain this
 * drawable so paths and shaders are rebuilt only when its bounds change.
 */
class OrbitHatchDrawable(
    fill: Int,
    private val cornerRadius: Float,
    density: Float,
    hatchColor: Int = OrbitColors.amber
) : Drawable() {
    private val fillAlpha = Color.alpha(fill)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val outline = Path()
    private val area = RectF()
    private val hatch = HatchInk(density, hatchColor)

    override fun onBoundsChange(bounds: Rect) {
        area.set(bounds)
        outline.rewind()
        outline.addRoundRect(area, cornerRadius, cornerRadius, Path.Direction.CW)
        hatch.setBounds(area)
    }

    override fun draw(canvas: Canvas) {
        if (area.isEmpty) return
        val save = canvas.save()
        canvas.clipPath(outline)
        canvas.drawRect(area, fillPaint)
        hatch.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = fillAlpha * alpha.coerceIn(0, 255) / 255
        hatch.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        hatch.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadius)
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** One-shot bitmap artwork for widgets; use the cached drawable for live views. */
object OrbitHatch {
    fun draw(canvas: Canvas, bounds: RectF, density: Float, color: Int = OrbitColors.amber) {
        if (bounds.isEmpty) return
        val hatch = HatchInk(density, color)
        hatch.setBounds(bounds)
        hatch.draw(canvas)
    }
}

private class HatchInk(density: Float, private val color: Int) {
    private val scale = density.coerceAtLeast(.1f)
    private val patch = RectF()
    private val lines = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = .7f * scale
        strokeCap = Paint.Cap.BUTT
    }
    var alpha: Int
        get() = paint.alpha
        set(value) { paint.alpha = value }
    var colorFilter: ColorFilter?
        get() = paint.colorFilter
        set(value) { paint.colorFilter = value }

    fun setBounds(bounds: RectF) {
        patch.set(bounds)
        patch.left = bounds.right - min(bounds.width() * .38f, 104f * scale)
        lines.rewind()
        if (patch.isEmpty) return
        val transparent = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        val quiet = Color.argb(10, Color.red(color), Color.green(color), Color.blue(color))
        val edge = Color.argb(42, Color.red(color), Color.green(color), Color.blue(color))
        paint.shader = LinearGradient(patch.left, 0f, patch.right, 0f,
            intArrayOf(transparent, quiet, edge), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        val rise = patch.height()
        var x = patch.left - rise
        while (x <= patch.right + rise) {
            lines.moveTo(x + rise * .55f, patch.top)
            lines.lineTo(x, patch.bottom)
            x += 5f * scale
        }
    }

    fun draw(canvas: Canvas) {
        if (patch.isEmpty) return
        val save = canvas.save()
        canvas.clipRect(patch)
        canvas.drawPath(lines, paint)
        canvas.restoreToCount(save)
    }
}
