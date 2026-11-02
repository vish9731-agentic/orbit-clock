package dev.orbit.clock.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

/** An edge-feathered readability scrim; no opaque card or clipped capsule perimeter. */
object WidgetBackdrop {
    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    fun render(c: Context, wide: Boolean, widthDp: Int, heightDp: Int, bedtime: Boolean): Bitmap {
        val density = c.resources.displayMetrics.density
        val width = (widthDp * density).toInt().coerceIn(64, 1600)
        val height = (heightDp * density).toInt().coerceIn(64, 900)
        val tone = c.getColor(if (bedtime) R.color.widget_bedtime_tone else R.color.widget_alarm_tone)
        val red = (Color.red(tone) * .62f).toInt()
        val green = (Color.green(tone) * .62f).toInt()
        val blue = (Color.blue(tone) * .62f).toInt()
        val start = if (wide) { if (widthDp < 264) .15f else .23f } else 0f
        val feather = minOf(10f * density, height * .18f)
        val radius = minOf(24f * density, (height - 1) / 2f)
        val horizontal = FloatArray(width) { x ->
            val t = ((x.toFloat() / (width - 1) - start) / (1f - start)).coerceIn(0f, 1f)
            val r = 1f - t
            val transmission = if (wide) r*r*r*r else r*r*r*r*r
            1f - transmission
        }
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val dy = maxOf(radius - y, y - (height - 1 - radius), 0f)
            for (x in 0 until width) {
                val dx = maxOf(radius - x, x - (width - 1 - radius), 0f)
                val distance = if (dx > 0f && dy > 0f) radius - kotlin.math.sqrt(dx * dx + dy * dy)
                    else minOf(x.toFloat(), (width - 1 - x).toFloat(), y.toFloat(), (height - 1 - y).toFloat())
                val alpha = (212f * horizontal[x] * smooth(distance / feather)).toInt()
                pixels[y * width + x] = Color.argb(alpha, red, green, blue)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
