package dev.orbit.clock.core

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.button.MaterialButton
import kotlin.math.abs

/** Google Clock-style rail with a draggable center thumb and accessible action labels. */
class AlarmSlideControl(context: Context, private val snooze: () -> Unit, private val stop: () -> Unit) : FrameLayout(context) {
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private var startX = 0f
    private var dragging = false
    private var fired = false
    private var thresholdTicked = false
    private var tracker: VelocityTracker? = null
    private var returnSpring: SpringAnimation? = null
    private lateinit var snoozeLabel: MaterialButton
    private lateinit var stopLabel: MaterialButton
    private val thumb: MaterialButton
    init {
        background = GradientDrawable().apply { setColor(0xff103a35.toInt()); cornerRadius = dp(48).toFloat() }
        fun action(text: String, gravity: Int, callback: () -> Unit) {
            val button = MaterialButton(context).apply {
                this.text = text; isAllCaps = false; textSize = 22f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL), 450, false); setTextColor(0xffd3e3df.toInt())
                backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0); insetTop = 0; insetBottom = 0
                setOnClickListener { if (!fired) { fired = true; performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM); callback() } }
            }
            if (text == "Snooze") snoozeLabel = button else stopLabel = button
            addView(button, LayoutParams(dp(132), -1, gravity))
        }
        action("Snooze", Gravity.START, snooze)
        action("Stop", Gravity.END, stop)
        thumb = MaterialButton(context).apply {
            setTag(R.id.orbit_motion_bound, true); stateListAnimator = null
            contentDescription = "Alarm slider: swipe left to snooze, right to stop"
            setIconResource(R.drawable.ic_alarm); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconTint = ColorStateList.valueOf(0xff74e8d7.toInt()); iconSize = dp(24); iconPadding = 0
            minWidth = 0; minimumWidth = 0; setPadding(dp(34), 0, dp(34), 0)
            cornerRadius = dp(40); insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(0xff00564d.toInt())
            setOnClickListener { announceForAccessibility("Swipe left to snooze, right to stop") }
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        VisualHints.cancel(this@AlarmSlideControl)
                        returnSpring?.cancel(); animate().cancel(); tracker?.recycle(); tracker = VelocityTracker.obtain(); track(e)
                        startX = e.rawX - translationX; dragging = true; thresholdTicked = false
                        if (OrbitMotion.enabled()) animate().scaleX(1.04f).scaleY(1.04f).setStartDelay(0).setDuration(100).start()
                        parent.requestDisallowInterceptTouchEvent(true); true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (dragging) {
                            translationX = (e.rawX - startX).coerceIn(-travel(), travel())
                            track(e); feedback(translationX)
                            val fraction = if (travel() > 0) translationX / travel() else 0f
                            if (abs(fraction) >= .7f && !thresholdTicked) { thresholdTicked = true; OrbitMotion.tick(this) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        track(e); tracker?.computeCurrentVelocity(1000, dp(900).toFloat())
                        val velocity = tracker?.xVelocity ?: 0f
                        tracker?.recycle(); tracker = null
                        if (dragging) translationX = (e.rawX - startX).coerceIn(-travel(), travel())
                        val offset = translationX
                        val complete = dragging && travel() > 0 && abs(offset) >= travel() * .7f
                        dragging = false; parent.requestDisallowInterceptTouchEvent(false)
                        if (complete && !fired) { fired = true; performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM); if (offset < 0) snooze() else stop() }
                        else { settle(velocity = velocity); if (abs(offset) < dp(4)) performClick() }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { tracker?.recycle(); tracker = null; dragging = false; settle(); parent.requestDisallowInterceptTouchEvent(false); true }
                    else -> false
                }
            }
        }
        addView(thumb, LayoutParams(dp(96), dp(64), Gravity.CENTER))
        VisualHints.bind(this, "ring_controls", directional = true)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun travel() = ((width - dp(96)) / 2f - dp(10)).coerceAtLeast(0f)
    private fun track(event: MotionEvent) {
        val raw = MotionEvent.obtain(event)
        raw.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker?.addMovement(raw); raw.recycle()
    }
    private fun feedback(offset: Float) {
        val fraction = if (travel() > 0) (offset / travel()).coerceIn(-1f, 1f) else 0f
        thumb.rotation = if (OrbitMotion.enabled()) fraction * 8f else 0f
        snoozeLabel.alpha = if (fraction > 0) 1f - fraction * .45f else 1f
        stopLabel.alpha = if (fraction < 0) 1f + fraction * .45f else 1f
    }
    private fun settle(immediate: Boolean = false, velocity: Float = 0f) {
        returnSpring?.cancel(); thumb.animate().cancel()
        if (immediate || !OrbitMotion.enabled()) {
            thumb.translationX = 0f; thumb.rotation = 0f; thumb.scaleX = 1f; thumb.scaleY = 1f
            feedback(0f)
        } else {
            thumb.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(180).start()
            returnSpring = SpringAnimation(thumb, DynamicAnimation.TRANSLATION_X).apply {
                spring = SpringForce(0f).apply { dampingRatio = .82f; stiffness = 700f }
                setStartVelocity(velocity); setMinValue(-travel()); setMaxValue(travel()); setMinimumVisibleChange(.5f)
                addUpdateListener { _, value, _ -> feedback(value) }
                addEndListener { _, canceled, _, _ -> if (!canceled) { thumb.translationX = 0f; feedback(0f) } }
                start()
            }
        }
    }
    override fun onDetachedFromWindow() { tracker?.recycle(); tracker = null; dragging = false; settle(true); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) { tracker?.recycle(); tracker = null; dragging = false; settle(true) }
    }
}
