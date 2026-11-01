package dev.orbit.clock.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import java.util.WeakHashMap
import kotlin.math.sin

/** Finite, non-interactive light cues. Never moves controls or dispatches an action. */
object VisualHints {
    private val sessions = mutableSetOf<String>()
    private val active = WeakHashMap<View, ValueAnimator>()
    private fun prefs(c: Context) = c.getSharedPreferences("visual_hints", Context.MODE_PRIVATE)
    fun learn(c: Context, key: String) { prefs(c).edit().putBoolean("$key.learned", true).apply() }
    fun cancel(view: View) { active.remove(view)?.cancel() }
    fun bind(view: View, key: String, directional: Boolean = false) {
        val p = prefs(view.context)
        if (p.getBoolean("$key.learned", false) || key in sessions || p.getInt("$key.shown", 0) >= 3) return
        val light = Sweep(directional)
        val visible = Rect()
        var observer: ViewTreeObserver? = null
        var animator: ValueAnimator? = null
        fun clear() { animator?.cancel(); view.overlay.remove(light); active.remove(view) }
        val preDraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!OrbitMotion.enabled() || !view.hasWindowFocus() || view.isPressed || p.getBoolean("$key.learned", false)) {
                    clear(); return true
                }
                if (key !in sessions && view.isShown && view.width > 0 && view.height > 0 &&
                    view.getGlobalVisibleRect(visible) && visible.height() >= view.height / 2) {
                    sessions.add(key)
                    p.edit().putInt("$key.shown", p.getInt("$key.shown", 0) + 1).apply()
                    light.setBounds(0, 0, view.width, view.height)
                    view.overlay.add(light)
                    animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        startDelay = 500; duration = 1800
                        addUpdateListener {
                            if (!OrbitMotion.enabled() || !view.hasWindowFocus() || !view.isShown || view.isPressed || p.getBoolean("$key.learned", false)) clear()
                            else { light.phase = it.animatedValue as Float; light.invalidateSelf() }
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) { view.overlay.remove(light); active.remove(view) }
                        })
                        active[view] = this; start()
                    }
                }
                return true
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { observer = v.viewTreeObserver; observer?.addOnPreDrawListener(preDraw) }
            override fun onViewDetachedFromWindow(v: View) { clear(); observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw); observer = null }
        })
    }
    private class Sweep(private val directional: Boolean) : Drawable() {
        var phase = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            if (w <= 0 || h <= 0) return
            val progress = (phase * 2f) % 1f
            val strength = sin(progress * Math.PI).toFloat().coerceAtLeast(0f)
            val band = w * .16f
            val centers = if (directional) listOf(w * .5f - progress * w * .5f, w * .5f + progress * w * .5f)
                else listOf(-band + progress * (w + 2 * band))
            val checkpoint = canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), h.coerceAtMost(w) / 2, h.coerceAtMost(w) / 2, Path.Direction.CW) })
            for (x in centers) {
                paint.shader = LinearGradient(x-band, 0f, x+band, 0f, intArrayOf(Color.TRANSPARENT, Color.argb((36*strength).toInt(), 160, 255, 232), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(x-band, 0f, x+band, h, paint)
            }
            paint.shader = null
            canvas.restoreToCount(checkpoint)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
