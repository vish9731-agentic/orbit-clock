package dev.orbit.clock.core

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/** Finite, interruptible motion. Values and actions change immediately, never in an end callback. */
object OrbitMotion {
    private val ease = PathInterpolator(.2f, 0f, 0f, 1f)
    fun enabled() = ValueAnimator.areAnimatorsEnabled()
    private fun px(view: View, dp: Float) = dp * view.resources.displayMetrics.density
    fun bind(view: View) {
        if (view.getTag(R.id.orbit_motion_bound) == true) return
        view.setTag(R.id.orbit_motion_bound, true)
        if (view is MaterialButton || view is FloatingActionButton || view is MaterialCardView && view.isClickable) {
            fun scale(to: Float, time: Long) = AnimatorSet().apply {
                playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, to), ObjectAnimator.ofFloat(view, View.SCALE_Y, to))
                duration = time; interpolator = ease
            }
            view.stateListAnimator = StateListAnimator().apply {
                addState(intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled), scale(.97f, 80))
                addState(intArrayOf(), scale(1f, 180))
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                v.animate().cancel(); v.stateListAnimator?.jumpToCurrentState(); (v.getTag(R.id.orbit_motion_tint) as? ValueAnimator)?.cancel()
                v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; v.translationX = 0f; v.translationY = 0f
            }
        })
    }
    fun bindTree(view: View) {
        if (view is com.google.android.material.bottomnavigation.BottomNavigationView || view is com.google.android.material.materialswitch.MaterialSwitch) return
        if (view is MaterialButton || view is FloatingActionButton || view is MaterialCardView && view.isClickable) bind(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) bindTree(view.getChildAt(i))
    }
    fun enter(view: View, delay: Long = 0, direction: Int = 0) {
        bind(view); view.animate().cancel()
        if (!enabled()) { view.alpha = 1f; view.translationX = 0f; view.translationY = 0f; return }
        // Keep the incoming state legible even if a device misses an early frame.
        view.alpha = .35f
        if (direction == 0) view.translationY = px(view, 10f) else view.translationX = px(view, 18f * direction)
        view.animate().alpha(1f).translationX(0f).translationY(0f).setStartDelay(delay)
            .setDuration(220).setInterpolator(ease).withEndAction(null).start()
    }
    fun content(group: ViewGroup, direction: Int = 0) {
        bindTree(group)
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (i < 8 && child !is android.widget.Space) enter(child, (i * 18L).coerceAtMost(90), direction)
        }
    }
    fun reposition(view: View, delta: Float) {
        if (!enabled() || kotlin.math.abs(delta) < 1f) return
        bind(view); view.animate().cancel(); view.translationY = delta
        view.animate().translationY(0f).setStartDelay(0).setDuration(220).setInterpolator(ease).withEndAction(null).start()
    }
    fun pulse(view: View) {
        bind(view); view.animate().cancel()
        if (!enabled()) { view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f; return }
        view.scaleX = .96f; view.scaleY = .96f
        view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(180).setInterpolator(ease).withEndAction(null).start()
    }
    fun text(view: TextView, value: String) {
        if (view.text.toString() == value) return
        val wasVisible = view.text.isNotEmpty(); view.text = value
        if (wasVisible) pulse(view)
    }
    fun selection(button: MaterialButton, background: Int, foreground: Int) {
        (button.getTag(R.id.orbit_motion_tint) as? ValueAnimator)?.cancel()
        val fromBackground = button.backgroundTintList?.defaultColor ?: background
        val fromText = button.currentTextColor
        if (!enabled()) { button.backgroundTintList = android.content.res.ColorStateList.valueOf(background); button.setTextColor(foreground); return }
        val evaluator = android.animation.ArgbEvaluator()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 150; interpolator = ease
            addUpdateListener {
                val f = it.animatedValue as Float
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(evaluator.evaluate(f, fromBackground, background) as Int)
                button.setTextColor(evaluator.evaluate(f, fromText, foreground) as Int)
            }
        }
        bind(button); button.setTag(R.id.orbit_motion_tint, animator); animator.start()
    }
    fun tick(view: View) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun reject(view: View) {
        bind(view); view.animate().cancel(); (view.getTag(R.id.orbit_motion_tint) as? ValueAnimator)?.cancel()
        if (!enabled()) return
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, px(view, -4f), px(view, 4f), 0f).apply {
            duration = 180; interpolator = ease
            view.setTag(R.id.orbit_motion_tint, this); start()
        }
    }
}
