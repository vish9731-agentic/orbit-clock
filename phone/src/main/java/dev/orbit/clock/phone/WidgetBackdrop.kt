package dev.orbit.clock.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/** One full-viewport alpha field, behind all native widget content. */
object WidgetBackdrop {
    fun render(c: Context, wide: Boolean, widthDp: Int, heightDp: Int, bedtime: Boolean): Bitmap {
        val density = c.resources.displayMetrics.density
        val width = (widthDp * density).toInt().coerceIn(64, 1600)
        val height = (heightDp * density).toInt().coerceIn(64, 900)
        val tone = c.getColor(if (bedtime) R.color.widget_bedtime_tone else R.color.widget_alarm_tone)
        // A deep wallpaper-accent tone keeps white type readable even on bright wallpaper.
        val red = (Color.red(tone) * .62f).toInt()
        val green = (Color.green(tone) * .62f).toInt()
        val blue = (Color.blue(tone) * .62f).toInt()
        val start = if (wide) { if (widthDp < 264) .28f else .32f } else 0f
        val colors = IntArray(257) { index ->
            val t = ((index / 256f - start) / (1f - start)).coerceIn(0f, 1f)
            val remaining = 1f - t
            // The fade continues to the outer edge, with no early opaque plateau.
            val transmission = if (wide) remaining * remaining * remaining
                else remaining * remaining * remaining * remaining * remaining
            val alpha = (255f * (1f - transmission)).toInt()
            // Identical RGB at every stop, including transparent ones: no grey/black fringe.
            Color.argb(alpha, red, green, blue)
        }
        val positions = FloatArray(colors.size) { it / 256f }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), 0f, colors, positions, Shader.TileMode.CLAMP)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val radius = minOf(24f * density, height / 2f)
            Canvas(bitmap).drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        }
    }
}
