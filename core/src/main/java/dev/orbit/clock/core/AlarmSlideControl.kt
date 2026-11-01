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
class AlarmSlideControl(context: Context, private val snooze: () -> Unit, private val stop: () -> Unit, private val compact: Boolean = false) : FrameLayout(context) {
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private var startX = 0f
    private var dragging = false
    private var fired = false
    private var thresholdTicked = false
    private var tracker: VelocityTracker? = null
    private var returnSpring: SpringAnimation? = null
    private var bezelSpring: SpringAnimation? = null
    private var lastBezelTime = 0L
    private var lastBezelTarget = 0f
    private var bezelVelocity = 0f
    private var visualVelocity = 0f
    val touchActive: Boolean get() = dragging
    /** Physical progress is authoritative; spring inertia cannot fire an action. */
    fun setBezelProgress(snoozing: Boolean, fraction: Float) {
        if (fired || dragging) return
        val target = travel() * fraction.coerceIn(0f, 1f) * if (snoozing) -1f else 1f
        val now = android.os.SystemClock.uptimeMillis()
        val dt = (now - lastBezelTime).coerceIn(16, 250) / 1000f
        bezelVelocity = ((target - lastBezelTarget) / dt).coerceIn(-dp(300).toFloat(), dp(300).toFloat())
        lastBezelTime = now; lastBezelTarget = target
        returnSpring?.cancel()
        if (!OrbitMotion.enabled()) {
            bezelSpring?.cancel(); visualVelocity = 0f; thumb.translationX = target; feedback(target); return
        }
        val spring = bezelSpring ?: SpringAnimation(thumb, DynamicAnimation.TRANSLATION_X).apply {
            this.spring = SpringForce(0f).apply { dampingRatio = .78f; stiffness = 420f }
            setMinValue(-travel()); setMaxValue(travel()); setMinimumVisibleChange(.15f)
            addUpdateListener { _, value, velocity -> visualVelocity = velocity; feedback(value) }
        }.also { bezelSpring = it }
        if (!spring.isRunning) spring.setStartVelocity(visualVelocity + bezelVelocity * .18f)
        spring.animateToFinalPosition(target)
    }
    fun releaseBezel() {
        if (dragging) return
        bezelSpring?.cancel(); lastBezelTarget = 0f
        settle(immediate = !hasWindowFocus(), velocity = visualVelocity); bezelVelocity = 0f
    }
    private lateinit var snoozeLabel: MaterialButton
    private lateinit var stopLabel: MaterialButton
    private val thumb: MaterialButton
    init {
        if (compact) tag = "watch_ring_slider"
        background = GradientDrawable().apply { setColor(0xff103a35.toInt()); cornerRadius = dp(48).toFloat() }
        fun action(text: String, gravity: Int, callback: () -> Unit) {
            val button = MaterialButton(context).apply {
                this.text = text; isAllCaps = false; textSize = if (compact) 13f else 22f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL), 450, false); setTextColor(0xffd3e3df.toInt())
                backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0); insetTop = 0; insetBottom = 0
                if (compact) {
                    contentDescription = if (text == "Stop") "Dismiss" else "Snooze"
                    maxLines = 1
                    setPadding(if (gravity == Gravity.END) dp(14) else 0, 0, if (gravity == Gravity.START) dp(14) else 0, 0)
                    setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                }
                setOnClickListener { if (!fired) { fired = true; performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM); callback() } }
            }
            if (text == "Snooze") snoozeLabel = button else stopLabel = button
            addView(button, LayoutParams(dp(if (compact) 56 else 132), -1, gravity))
        }
        action("Snooze", Gravity.START, snooze)
        action("Stop", Gravity.END, stop)
        thumb = MaterialButton(context).apply {
            setTag(R.id.orbit_motion_bound, true); stateListAnimator = null
            contentDescription = "Alarm slider: swipe left to snooze, right to stop"
            setIconResource(R.drawable.ic_alarm); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconTint = ColorStateList.valueOf(0xff74e8d7.toInt()); iconSize = dp(if (compact) 20 else 24); iconPadding = 0
            minWidth = 0; minimumWidth = 0; setPadding(dp(if (compact) 12 else 34), 0, dp(if (compact) 12 else 34), 0)
            cornerRadius = dp(40); insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(0xff00564d.toInt())
            setOnClickListener { announceForAccessibility("Swipe left to snooze, right to stop") }
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        VisualHints.cancel(this@AlarmSlideControl)
                        bezelSpring?.cancel(); returnSpring?.cancel(); animate().cancel(); tracker?.recycle(); tracker = VelocityTracker.obtain(); track(e)
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
        addView(thumb, LayoutParams(dp(if (compact) 72 else 96), dp(if (compact) 48 else 64), Gravity.CENTER))
        VisualHints.bind(this, "ring_controls", directional = true)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun travel() = ((width - dp(if (compact) 72 else 96)) / 2f - dp(10)).coerceAtLeast(0f)
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
            visualVelocity = 0f; thumb.translationX = 0f; thumb.rotation = 0f; thumb.scaleX = 1f; thumb.scaleY = 1f
            feedback(0f)
        } else {
            thumb.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(180).start()
            returnSpring = SpringAnimation(thumb, DynamicAnimation.TRANSLATION_X).apply {
                spring = SpringForce(0f).apply { dampingRatio = if (compact) .84f else .82f; stiffness = if (compact) 400f else 700f }
                setStartVelocity(if (compact) velocity.coerceIn(-dp(240).toFloat(), dp(240).toFloat()) * .45f else velocity); setMinValue(-travel()); setMaxValue(travel()); setMinimumVisibleChange(.5f)
                addUpdateListener { _, value, velocity -> visualVelocity = velocity; feedback(value) }
                addEndListener { _, canceled, _, _ -> if (!canceled) { thumb.translationX = 0f; feedback(0f) } }
                start()
            }
        }
    }
    override fun onDetachedFromWindow() { bezelSpring?.cancel(); tracker?.recycle(); tracker = null; dragging = false; settle(true); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) { bezelSpring?.cancel(); tracker?.recycle(); tracker = null; dragging = false; settle(true) }
    }
}
