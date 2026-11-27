package dev.orbit.clock.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import dev.orbit.clock.core.OrbitColors
import dev.orbit.clock.core.OrbitHatchDrawable
import kotlin.math.roundToInt

/** The app's textured charcoal surface, faded as one layer into the launcher. */
object WidgetBackdrop {
    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    @Suppress("UNUSED_PARAMETER") // All reminder shapes/states share this surface.
    fun render(c: Context, wide: Boolean, widthDp: Int, heightDp: Int, bedtime: Boolean): Bitmap {
        val logicalWidth = widthDp.coerceAtLeast(1)
        val logicalHeight = heightDp.coerceAtLeast(1)
        // One uniform raster scale preserves the app's hatch pitch and angle even
        // when a large launcher allocation needs a smaller RemoteViews bitmap.
        val density = minOf(c.resources.displayMetrics.density,
            1600f / logicalWidth, 900f / logicalHeight)
        val width = (logicalWidth * density).roundToInt().coerceIn(1, 1600)
        val height = (logicalHeight * density).roundToInt().coerceIn(1, 900)
        val backdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(backdrop)
        // Widget feathering below owns the perimeter; do not add a second rounded
        // clip or a separately tinted hatch on top of this shared app artwork.
        OrbitHatchDrawable(OrbitColors.surfaceHigh, 0f, density,
            hatchWidthFraction = .85f, hatchMaxWidthDp = null).apply {
            setBounds(0, 0, width, height)
            draw(canvas)
        }
        val feather = minOf(10f * density, height * .18f)
        val radius = minOf(24f * density, (height - 1) / 2f)
        val horizontal = FloatArray(width) { x ->
            val t = x.toFloat() / (width - 1).coerceAtLeast(1)
            // Spread readability evenly across the full surface: 25% at left,
            // 55% at centre and 80% at right, before the soft perimeter mask.
            .25f + .65f * t - .10f * t * t
        }
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val dy = maxOf(radius - y, y - (height - 1 - radius), 0f)
            for (x in 0 until width) {
                val dx = maxOf(radius - x, x - (width - 1 - radius), 0f)
                val distance = if (dx > 0f && dy > 0f) radius - kotlin.math.sqrt(dx * dx + dy * dy)
                    else minOf(x.toFloat(), (width - 1 - x).toFloat(), y.toFloat(), (height - 1 - y).toFloat())
                val alpha = (255f * horizontal[x] * smooth(distance / feather)).toInt()
                pixels[y * width + x] = Color.argb(alpha, 0, 0, 0)
            }
        }
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        // Fade the complete app surface once: line weight, color and intrinsic
        // right-edge transparency remain identical to the app's renderer.
        canvas.drawBitmap(mask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
        mask.recycle()
        return backdrop
    }
}
