package dev.orbit.clock.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.*

/** Quiet Material You scalloped clock face. No animation competes with alarm controls. */
class RingingFace(context: Context, private val colors: OrbitPalette) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val radius = min(w, h) * .48f
        shape.reset()
        for (i in 0..360) {
            val angle = i * PI / 180
            val r = radius * (.96 + .04 * cos(12 * angle))
            val x = w / 2f + (r * sin(angle)).toFloat()
            val y = h / 2f - (r * cos(angle)).toFloat()
            if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
        }
        shape.close()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = colors.primaryContainer
        canvas.drawPath(shape, paint)
    }
}
