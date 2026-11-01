package dev.orbit.clock.core

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Static corner light, not a full-screen tint. All decoration stays clear of time and actions. */
class RingAtmosphereDrawable(minute: Int) : Drawable() {
    private data class Light(val shader: Shader, val area: RectF)
    private data class Celestial(val scene: RingAtmosphere.Scene, val opacity: Float)
    private var currentMinute = Math.floorMod(minute, 1440)
    private var lights = emptyList<Light>()
    private var celestial = emptyList<Celestial>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255
    private var filter: ColorFilter? = null
    private val moon = Path()
    private val cutout = Path()

    fun updateTime(minute: Int) {
        val next = Math.floorMod(minute, 1440)
        if (next == currentMinute) return
        currentMinute = next
        rebuild()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) { rebuild() }

    private fun rebuild() {
        if (bounds.isEmpty) return
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val frame = RingAtmosphere.sample(currentMinute)
        val nextLights = mutableListOf<Light>()
        val nextCelestial = mutableListOf<Celestial>()
        fun addLight(color: Int, strength: Float, cx: Float, cy: Float, rx: Float, ry: Float,
                     core: Boolean = false) {
            if (strength < .001f) return
            fun rgba(alpha: Float) = (color and 0x00ffffff) or ((255 * alpha * strength).toInt().coerceIn(0, 255) shl 24)
            val shader = RadialGradient(0f, 0f, 1f,
                if (core) intArrayOf(rgba(.66f), rgba(.28f), rgba(.035f), rgba(0f))
                else intArrayOf(rgba(.34f), rgba(.21f), rgba(.055f), rgba(0f)),
                if (core) floatArrayOf(0f, .22f, .62f, 1f) else floatArrayOf(0f, .22f, .64f, 1f),
                Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply {
                setScale(rx, ry)
                postTranslate(bounds.left + cx, bounds.top + cy)
            })
            // A CLAMP gradient is fully transparent beyond its unit circle.
            // Restrict GPU work to that ellipse's clipped rectangle instead of
            // shading the whole display for every small corner layer.
            val area = RectF(bounds.left + cx - rx, bounds.top + cy - ry,
                bounds.left + cx + rx, bounds.top + cy + ry)
            if (area.intersect(bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.bottom.toFloat())) {
                nextLights += Light(shader, area)
            }
        }
        fun scene(scene: RingAtmosphere.Scene, weight: Float) {
            if (weight < .001f) return
            val right = scene.cornerX > .5f
            val inward = if (right) -1f else 1f
            val x = scene.cornerX * w
            val y = scene.cornerY * h
            // Broad sky haze is faint; a small sunlit core supplies the visible time-of-day cue.
            addLight(scene.skyColor, weight, x, y, w * .76f, h * .30f)
            addLight(scene.glowColor, weight * .86f, x + inward * w * .065f,
                y + h * .10f, w * .55f, h * .18f)
            addLight(scene.sunColor, weight * scene.sun, x + inward * w * .035f,
                y + h * .055f, w * .14f, h * .07f, core = true)
            // Three feathered horizontal wisps stay within the lit corner.
            // Cached gradients give cloud texture without a frame loop or hard outlines.
            if (scene.sun > 0f) {
                val cloud = 0xffdce4e9.toInt()
                addLight(cloud, weight * .22f, x + inward * w * .13f,
                    y + h * .13f, w * .22f, h * .014f)
                addLight(cloud, weight * .16f, x + inward * w * .24f,
                    y + h * .15f, w * .18f, h * .010f)
                addLight(cloud, weight * .12f, x + inward * w * .09f,
                    y + h * .19f, w * .16f, h * .012f)
            }
            if (scene.stars > 0f || scene.moon > 0f) nextCelestial += Celestial(scene, weight)
        }
        if (frame.from === frame.to) scene(frame.from, 1f)
        else { scene(frame.from, 1f - frame.blend); scene(frame.to, frame.blend) }
        lights = nextLights
        celestial = nextCelestial
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val checkpoint = canvas.save()
        canvas.clipRect(bounds)
        paint.shader = null; paint.colorFilter = filter
        paint.color = RingAtmosphere.tint(currentMinute); paint.alpha = opacity
        canvas.drawRect(bounds, paint)
        for (light in lights) {
            paint.shader = light.shader; paint.alpha = opacity
            canvas.drawRect(light.area, paint)
        }
        paint.shader = null
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        for ((scene, weight) in celestial) {
            val right = scene.cornerX > .5f
            fun x(fraction: Float) = bounds.left + (if (right) fraction else 1f - fraction) * w
            fun y(fraction: Float) = bounds.top + fraction * h
            paint.color = 0xffadc3e3.toInt()
            paint.alpha = (opacity * .54f * scene.stars * weight).toInt()
            // Five fixed pinpoints: no twinkle, random sparkle or recurring animation loop.
            canvas.drawCircle(x(.76f), y(.047f), w * .00165f, paint)
            paint.alpha = (opacity * .36f * scene.stars * weight).toInt()
            canvas.drawCircle(x(.943f), y(.174f), w * .00115f, paint)
            canvas.drawCircle(x(.69f), y(.204f), w * .00135f, paint)
            paint.alpha = (opacity * .25f * scene.stars * weight).toInt()
            canvas.drawCircle(x(.858f), y(.27f), w * .0011f, paint)
            canvas.drawCircle(x(.974f), y(.067f), w * .001f, paint)
            val radius = w * .024f
            val cx = x(.864f); val cy = y(.106f)
            moon.rewind(); cutout.rewind()
            moon.addCircle(cx, cy, radius, Path.Direction.CW)
            cutout.addCircle(cx + (if (right) 1 else -1) * radius * .45f,
                cy - radius * .20f, radius * .96f, Path.Direction.CW)
            moon.op(cutout, Path.Op.DIFFERENCE)
            paint.color = 0xffbecce2.toInt()
            paint.alpha = (opacity * .52f * scene.moon * weight).toInt()
            canvas.drawPath(moon, paint)
        }
        paint.alpha = 255
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = if (opacity == 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
}
