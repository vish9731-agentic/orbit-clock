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
        val feather = minOf(8f * density, height * .15f)
        val horizontal = FloatArray(width) { x ->
            val t = ((x.toFloat() / (width - 1) - start) / (1f - start)).coerceIn(0f, 1f)
            val r = 1f - t
            val transmission = if (wide) r*r*r*r else r*r*r*r*r
            (1f - transmission) * smooth((width - 1 - x) / feather)
        }
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val edge = smooth(minOf(y.toFloat(), (height - 1 - y).toFloat()) / feather)
            for (x in 0 until width) {
                val alpha = (228f * horizontal[x] * edge).toInt()
                pixels[y * width + x] = Color.argb(alpha, red, green, blue)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
