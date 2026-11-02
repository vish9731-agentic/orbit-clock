package dev.orbit.clock.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import java.util.WeakHashMap
import kotlin.math.sin

/** Incoming-call-inspired, finite rail glints; no text tips, input interception or actions. */
object VisualHints {
    private val active = WeakHashMap<View, ValueAnimator>()
    private val consumed = WeakHashMap<View, Boolean>()
    private fun prefs(c: Context) = c.getSharedPreferences("visual_hints", Context.MODE_PRIVATE)
    fun learn(c: Context, key: String) { prefs(c).edit().putBoolean("$key.learned", true).apply() }
    fun cancel(view: View) {
        if (view.isAttachedToWindow && view.hasWindowFocus()) consumed[view] = true
        active.remove(view)?.cancel()
    }
    fun bind(view: View, key: String, directional: Boolean = false) {
        if (key != "ring_controls") return
        consumed.remove(view)
        val light = Sweep(directional)
        val visible = Rect()
        var observer: ViewTreeObserver? = null
        var animator: ValueAnimator? = null
        var started = false
        fun clear() { animator?.cancel(); view.overlay.remove(light); active.remove(view) }
        val preDraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (consumed[view] == true || !OrbitMotion.enabled() || !view.hasWindowFocus() || view.isPressed) {
                    clear(); return true
                }
                if (!started && view.isShown && view.width > 0 && view.height > 0 &&
                    view.getGlobalVisibleRect(visible) && visible.height() >= view.height / 2) {
                    started = true
                    light.setBounds(0, 0, view.width, view.height)
                    view.overlay.add(light)
                    animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        startDelay = 450; duration = 1800
                        interpolator = android.view.animation.LinearInterpolator()
                        addUpdateListener {
                            if (!OrbitMotion.enabled() || !view.hasWindowFocus() || !view.isShown || view.isPressed) clear()
                            else { light.phase = it.animatedValue as Float; light.invalidateSelf() }
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                view.overlay.remove(light); active.remove(view)
                            }
                        })
                        active[view] = this; start()
                    }
                }
                return true
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                observer = v.viewTreeObserver; observer?.addOnPreDrawListener(preDraw)
            }
            override fun onViewDetachedFromWindow(v: View) {
                clear(); observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw); observer = null
            }
        })
    }
    private class Sweep(private val directional: Boolean) : Drawable() {
        var phase = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val path = Path()
        private val matrix = Matrix()
        private var gradient: LinearGradient? = null
        private var band = 0f
        private var opacity = 255
        override fun onBoundsChange(bounds: Rect) {
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            path.rewind()
            path.addRoundRect(RectF(bounds), h.coerceAtMost(w) / 2, h.coerceAtMost(w) / 2, Path.Direction.CW)
            band = w * .12f
            gradient = if (w > 0) LinearGradient(-band, 0f, band, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(24, 131, 238, 212),
                    Color.argb(88, 224, 255, 245), Color.argb(24, 131, 238, 212), Color.TRANSPARENT),
                floatArrayOf(0f, .32f, .56f, .76f, 1f), Shader.TileMode.CLAMP) else null
        }
        override fun draw(canvas: Canvas) {
            val shader = gradient ?: return
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            val progress = (phase.coerceIn(0f, 1f) * 2f) % 1f
            val ease = progress * progress * (3f - 2f * progress)
            paint.alpha = (opacity * sin(progress * Math.PI).toFloat().coerceAtLeast(0f)).toInt()
            val save = canvas.save(); canvas.clipPath(path)
            for (i in 0 until (if (directional) 2 else 1)) {
                val sign = if (i == 0) -1f else 1f
                val x = bounds.left + if (directional) w * .5f + sign * w * (.10f + ease * .48f)
                    else -band + ease * (w + band * 2)
                matrix.setTranslate(x, bounds.top.toFloat()); shader.setLocalMatrix(matrix)
                paint.shader = shader
                canvas.drawRect(x - band, bounds.top.toFloat(), x + band, bounds.top + h, paint)
            }
            paint.shader = null; canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
