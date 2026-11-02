package dev.orbit.clock.core

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.sqrt

/** Direct manipulation; only physical release/detents can commit an alarm action. */
class AlarmSlideControl(context: Context, private val snooze: () -> Unit, private val stop: () -> Unit,
                        private val compact: Boolean = false) : FrameLayout(context) {
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private var startX = 0f
    private var dragging = false
    private var fired = false
    private var thresholdTicked = false
    private var pointer = MotionEvent.INVALID_POINTER_ID
    private var tracker: VelocityTracker? = null
    private var returnSpring: SpringAnimation? = null
    private var bezelSpring: SpringAnimation? = null
    private val stretchValue = FloatValueHolder(0f)
    private var stretchSpring: SpringAnimation? = null
    private var visualVelocity = 0f
    private var pullBeyondEdge = 0f
    val touchActive: Boolean get() = dragging

    fun setBezelProgress(snoozing: Boolean, fraction: Float) {
        if (fired || dragging) return
        VisualHints.cancel(this)
        val target = travel() * fraction.coerceIn(0f, 1f) * if (snoozing) -1f else 1f
        returnSpring?.cancel()
        if (!OrbitMotion.enabled()) {
            bezelSpring?.cancel(); visualVelocity = 0f; thumb.translationX = target; feedback(target); return
        }
        val spring = bezelSpring ?: SpringAnimation(thumb, DynamicAnimation.TRANSLATION_X).apply {
            this.spring = SpringForce(0f).apply { dampingRatio = .82f; stiffness = 230f }
            setMinValue(-travel()); setMaxValue(travel()); setMinimumVisibleChange(.05f)
            addUpdateListener { _, value, velocity -> visualVelocity = velocity; feedback(value) }
        }.also { bezelSpring = it }
        if (!spring.isRunning) spring.setStartVelocity(visualVelocity)
        spring.animateToFinalPosition(target)
    }
    fun releaseBezel() {
        if (dragging) return
        if (compact && hasWindowFocus() && OrbitMotion.enabled()) {
            bezelSpring?.animateToFinalPosition(0f)
        } else { bezelSpring?.cancel(); settle(immediate = !hasWindowFocus(), velocity = visualVelocity) }
    }
    private lateinit var snoozeLabel: MaterialButton
    private lateinit var stopLabel: MaterialButton
    private val thumb: MaterialButton

    init {
        if (compact) tag = "watch_ring_slider"
        background = GradientDrawable().apply { setColor(0xff103a35.toInt()); cornerRadius = dp(48).toFloat() }
        clipToOutline = true
        fun action(text: String, gravity: Int, callback: () -> Unit) {
            val button = MaterialButton(context).apply {
                this.text = text; isAllCaps = false; textSize = if (compact) 11f else 22f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.create("sans-serif",
                    android.graphics.Typeface.NORMAL), 450, false)
                setTextColor(0xffd3e3df.toInt())
                backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0); insetTop = 0; insetBottom = 0
                if (compact) {
                    contentDescription = if (text == "Stop") "Dismiss" else "Snooze"
                    maxLines = 1
                    setPadding(if (gravity == Gravity.END) dp(6) else 0, 0,
                        if (gravity == Gravity.START) dp(6) else 0, 0)
                    setAutoSizeTextTypeUniformWithConfiguration(9, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                }
                setOnClickListener {
                    if (!fired) {
                        fired = true; VisualHints.cancel(this@AlarmSlideControl)
                        performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM); callback()
                    }
                }
            }
            if (text == "Snooze") snoozeLabel = button else stopLabel = button
            addView(button, LayoutParams(dp(if (compact) 50 else 132), -1, gravity))
        }
        action("Snooze", Gravity.START, snooze); action("Stop", Gravity.END, stop)
        thumb = object : MaterialButton(context) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                // Let the pill stretch while its alarm glyph remains crisp and undistorted.
                val save = canvas.save()
                canvas.scale(1f / scaleX, 1f / scaleY, width / 2f, height / 2f)
                super.onDraw(canvas)
                canvas.restoreToCount(save)
            }
        }.apply {
            setTag(R.id.orbit_motion_bound, true); stateListAnimator = null
            contentDescription = "Alarm slider: swipe left to snooze, right to stop"
            setIconResource(R.drawable.ic_alarm); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconTint = ColorStateList.valueOf(0xff74e8d7.toInt())
            iconSize = dp(if (compact) 18 else 24); iconPadding = 0
            minWidth = 0; minimumWidth = 0
            setPadding(dp(if (compact) 8 else 34), 0, dp(if (compact) 8 else 34), 0)
            cornerRadius = dp(40); insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(0xff00564d.toInt())
            setOnClickListener { announceForAccessibility("Swipe left to snooze, right to stop") }
            setOnTouchListener { _, event -> handleTouch(event) }
        }
        addView(thumb, LayoutParams(dp(if (compact) 48 else 96), dp(if (compact) 32 else 64), Gravity.CENTER))
        if (!compact) VisualHints.bind(this, "ring_controls", directional = true)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun fingerX(event: MotionEvent): Float {
        val index = event.findPointerIndex(pointer)
        return if (index >= 0) event.getX(index) + event.rawX - event.x else Float.NaN
    }
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (fired) return true
                VisualHints.cancel(this)
                bezelSpring?.cancel(); returnSpring?.cancel(); thumb.animate().cancel()
                tracker?.recycle(); tracker = VelocityTracker.obtain()
                pointer = event.getPointerId(0); track(event)
                // Keep the exact visible position when catching a returning thumb.
                startX = fingerX(event) - thumb.translationX
                dragging = true; thresholdTicked = false; pullBeyondEdge = 0f
                feedback(thumb.translationX)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val raw = fingerX(event) - startX
                    if (!raw.isFinite()) { cancelTouch(); return true }
                    track(event); tracker?.computeCurrentVelocity(1000, dp(1800).toFloat())
                    visualVelocity = tracker?.getXVelocity(pointer) ?: 0f
                    thumb.translationX = raw.coerceIn(-travel(), travel())
                    // Edge resistance deforms the pill; its center remains inside the rail.
                    pullBeyondEdge = ((abs(raw) - travel()).coerceAtLeast(0f) / dp(80)).coerceIn(0f, 1f)
                    feedback(thumb.translationX)
                    if (travel() > 0 && abs(thumb.translationX) >= travel() * .7f && !thresholdTicked) {
                        thresholdTicked = true; OrbitMotion.tick(thumb)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return true
                track(event); tracker?.computeCurrentVelocity(1000, dp(1800).toFloat())
                val velocity = tracker?.getXVelocity(pointer) ?: 0f
                val raw = fingerX(event) - startX
                if (!raw.isFinite()) { cancelTouch(); return true }
                thumb.translationX = raw.coerceIn(-travel(), travel())
                val offset = thumb.translationX
                val complete = travel() > 0 && abs(offset) >= travel() * .7f
                recycleTouch()
                if (complete && !fired) {
                    fired = true; thumb.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    if (offset < 0) snooze() else stop()
                } else {
                    settle(velocity = velocity)
                    if (abs(offset) < dp(4)) thumb.performClick()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointer) cancelTouch()
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
            else -> return dragging
        }
        return true
    }
    private fun recycleTouch() {
        tracker?.recycle(); tracker = null; pointer = MotionEvent.INVALID_POINTER_ID
        dragging = false; pullBeyondEdge = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    private fun cancelTouch() { recycleTouch(); settle() }
    private fun travel() = ((width - dp(if (compact) 48 else 96)) / 2f - dp(10)).coerceAtLeast(0f)
    private fun track(event: MotionEvent) {
        val raw = MotionEvent.obtain(event)
        raw.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker?.addMovement(raw); raw.recycle()
    }
    private fun deform(stretch: Float) {
        thumb.rotation = 0f
        thumb.scaleX = 1f + stretch
        thumb.scaleY = 1f / sqrt(1f + stretch)
    }
    private fun feedback(offset: Float) {
        val fraction = if (travel() > 0) (offset / travel()).coerceIn(-1f, 1f) else 0f
        if (!OrbitMotion.enabled()) {
            stretchSpring?.cancel(); stretchValue.value = 0f; deform(0f)
        } else {
            val tension = (abs(fraction) * (if (compact) .08f else .13f) +
                (abs(visualVelocity) / dp(1800)).coerceIn(0f, 1f) * .06f +
                pullBeyondEdge * .05f + (if (dragging) .025f else 0f)).coerceAtMost(if (compact) .16f else .24f)
            val spring = stretchSpring ?: SpringAnimation(stretchValue).apply {
                this.spring = SpringForce(0f).apply { dampingRatio = .66f; stiffness = 360f }
                setMinValue(-.06f); setMaxValue(.26f); setMinimumVisibleChange(.001f)
                addUpdateListener { _, value, _ -> deform(value) }
            }.also { stretchSpring = it }
            spring.animateToFinalPosition(tension)
        }
        // Fade the destination before the stretched pill overlaps its letters.
        val covered = (abs(fraction) / (if (compact) .30f else .38f)).coerceIn(0f, 1f)
        val reveal = 1f - covered * covered * (3f - 2f * covered)
        snoozeLabel.alpha = if (fraction < 0) reveal else 1f - fraction * .35f
        stopLabel.alpha = if (fraction > 0) reveal else 1f + fraction * .35f
    }
    private fun settle(immediate: Boolean = false, velocity: Float = 0f) {
        returnSpring?.cancel(); thumb.animate().cancel()
        if (immediate || !OrbitMotion.enabled()) {
            visualVelocity = 0f; stretchSpring?.cancel(); stretchValue.value = 0f
            thumb.translationX = 0f; deform(0f)
            snoozeLabel.alpha = 1f; stopLabel.alpha = 1f
        } else {
            returnSpring = SpringAnimation(thumb, DynamicAnimation.TRANSLATION_X).apply {
                spring = SpringForce(0f).apply { dampingRatio = .68f; stiffness = if (compact) 300f else 280f }
                // Carry real release momentum. Overshoot is visual and never commits an action.
                setStartVelocity(velocity.coerceIn(-dp(1800).toFloat(), dp(1800).toFloat()))
                setMinValue(-travel()); setMaxValue(travel()); setMinimumVisibleChange(.1f)
                addUpdateListener { _, value, speed -> visualVelocity = speed; feedback(value) }
                addEndListener { _, canceled, _, _ ->
                    if (!canceled) { visualVelocity = 0f; thumb.translationX = 0f; feedback(0f) }
                }
                start()
            }
        }
    }
    override fun onDetachedFromWindow() {
        VisualHints.cancel(this); bezelSpring?.cancel(); recycleTouch(); settle(true)
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) { VisualHints.cancel(this); bezelSpring?.cancel(); recycleTouch(); settle(true) }
    }
}
