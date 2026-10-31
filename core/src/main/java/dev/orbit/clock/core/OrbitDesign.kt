package dev.orbit.clock.core

import android.content.Context
import android.graphics.*
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.*

/** Material color roles, including wallpaper colors supplied by DynamicColors. */
class OrbitPalette(private val context: Context) {
    private fun role(attr: Int, fallback: Long) = MaterialColors.getColor(context, attr, fallback.toInt())
    val surface = role(com.google.android.material.R.attr.colorSurface, 0xfffffbfe)
    val onSurface = role(com.google.android.material.R.attr.colorOnSurface, 0xff1c1b1f)
    val primary = role(com.google.android.material.R.attr.colorPrimary, 0xff6750a4)
    val onPrimary = role(com.google.android.material.R.attr.colorOnPrimary, 0xffffffff)
    val primaryContainer = role(com.google.android.material.R.attr.colorPrimaryContainer, 0xffeaddff)
    val onPrimaryContainer = role(com.google.android.material.R.attr.colorOnPrimaryContainer, 0xff21005d)
    val secondaryContainer = role(com.google.android.material.R.attr.colorSecondaryContainer, 0xffe8def8)
    val onSecondaryContainer = role(com.google.android.material.R.attr.colorOnSecondaryContainer, 0xff1d192b)
    val muted = role(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xff49454f)
    val outline = role(com.google.android.material.R.attr.colorOutline, 0xff79747e)
    val container = MaterialColors.layer(surface, primary, .055f)
    val containerHigh = MaterialColors.layer(surface, primary, .09f)
}

/** Scalable clock artwork; no bitmap assets and no dependency on screen resolution. */
class OrbitDial(context: Context, private val colors: OrbitPalette) : View(context) {
    var hour = 7
    var minute = 0
    var second = 0
    var progress = -1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val radius = min(width, height) * .47f
        paint.style = Paint.Style.FILL; paint.color = colors.primaryContainer
        canvas.drawCircle(cx, cy, radius, paint)
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
