package dev.orbit.clock.core

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.appcompat.widget.AppCompatImageView
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


/** Direct manipulation; only physical release/detents can commit an alarm action. */
class AlarmSlideControl(context: Context, private val snooze: () -> Unit, private val stop: () -> Unit,
                        private val compact: Boolean = false) : FrameLayout(context) {
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private var startX = 0f
    private var downX = 0f
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
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
    private val relaxStretch = Runnable { relaxHeldShape() }
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
            this.spring = SpringForce(0f).apply { dampingRatio = .86f; stiffness = 260f }
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
    private val thumb: FrameLayout
    private lateinit var thumbSurface: PillSurface
    private lateinit var thumbIcon: AppCompatImageView

    init {
        if (compact) tag = "watch_ring_slider"
        background = GradientDrawable().apply { setColor(0xff103a35.toInt()); cornerRadius = dp(48).toFloat() }
        clipToOutline = true
        clipChildren = false; clipToPadding = false
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
        val thumbWidth = dp(if (compact) 48 else 96)
        val thumbHeight = dp(if (compact) 32 else 64)
        thumb = FrameLayout(context).apply {
            setTag(R.id.orbit_motion_bound, true)
            stateListAnimator = null
            clipChildren = false; clipToPadding = false
            isClickable = true; isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Alarm slider: swipe left to snooze, right to stop"
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                }
            }
            thumbSurface = PillSurface(context, thumbWidth.toFloat(), thumbHeight.toFloat()).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            // Reserve drawing room for the surface; neither container nor glyph is scaled.
            addView(thumbSurface, LayoutParams((thumbWidth * 1.5f).toInt(),
                (thumbHeight * 1.25f).toInt(), Gravity.CENTER))
            thumbIcon = AppCompatImageView(context).apply {
                setImageResource(R.drawable.ic_alarm)
                imageTintList = ColorStateList.valueOf(0xff74e8d7.toInt())
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = false; isFocusable = false
            }
            addView(thumbIcon, LayoutParams(dp(if (compact) 18 else 24),
                dp(if (compact) 18 else 24), Gravity.CENTER))
            setOnClickListener { announceForAccessibility("Swipe left to snooze, right to stop") }
            setOnTouchListener { _, event -> handleTouch(event) }
        }
        addView(thumb, LayoutParams(dp(if (compact) 48 else 96), dp(if (compact) 32 else 64), Gravity.CENTER))
        if (!compact) VisualHints.bind(this, "ring_controls", directional = true)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun hitsThumb(event: MotionEvent): Boolean {
        val centerX = thumb.left + thumb.width / 2f + thumb.translationX
        val centerY = thumb.top + thumb.height / 2f
        val stretch = thumbSurface.stretch
        val halfWidth = thumb.width * (1f + stretch.coerceAtLeast(0f)) / 2f
        val lift = (stretch.coerceAtLeast(0f) / .035f).coerceIn(0f, 1f)
        val halfHeight = thumb.height * (1f + .045f * lift) / 2f
        return abs(event.x - centerX) <= halfWidth && abs(event.y - centerY) <= halfHeight
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        dragging || (event.actionMasked == MotionEvent.ACTION_DOWN && !fired && hitsThumb(event)) ||
            super.onInterceptTouchEvent(event)
    override fun onTouchEvent(event: MotionEvent): Boolean =
        if (dragging || (event.actionMasked == MotionEvent.ACTION_DOWN && !fired && hitsThumb(event)))
            handleTouch(event) else super.onTouchEvent(event)

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
                downX = fingerX(event)
                startX = downX - thumb.translationX
                dragging = true; thresholdTicked = false; pullBeyondEdge = 0f
                feedback(thumb.translationX)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val raw = fingerX(event) - startX
                    if (!raw.isFinite()) { cancelTouch(); return true }
                    track(event); tracker?.computeCurrentVelocity(1000, dp(1600).toFloat())
                    visualVelocity = tracker?.getXVelocity(pointer) ?: 0f
                    thumb.translationX = raw.coerceIn(-travel(), travel())
                    // Edge resistance deforms the pill; its center remains inside the rail.
                    pullBeyondEdge = ((abs(raw) - travel()).coerceAtLeast(0f) / dp(80)).coerceIn(0f, 1f)
                    feedback(thumb.translationX)
                    removeCallbacks(relaxStretch); postDelayed(relaxStretch, 110)
                    if (travel() > 0 && abs(thumb.translationX) >= travel() * .7f && !thresholdTicked) {
                        thresholdTicked = true; OrbitMotion.tick(thumb)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return true
                track(event); tracker?.computeCurrentVelocity(1000, dp(1600).toFloat())
                val velocity = tracker?.getXVelocity(pointer) ?: 0f
                val raw = fingerX(event) - startX
                if (!raw.isFinite()) { cancelTouch(); return true }
                thumb.translationX = raw.coerceIn(-travel(), travel())
                val offset = thumb.translationX
                val movement = fingerX(event) - downX
                // A catch/tap on an inertially displaced thumb is never an action.
                val complete = travel() > 0 && abs(offset) >= travel() * .7f &&
                    abs(movement) >= touchSlop && movement * offset > 0f
                recycleTouch()
                if (complete && !fired) {
                    fired = true; thumb.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    if (offset < 0) snooze() else stop()
                } else {
                    settle(velocity = velocity)
                    if (abs(movement) < touchSlop) thumb.performClick()
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
        removeCallbacks(relaxStretch)
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
    /** Geometry-only deformation keeps the native icon RenderNode untouched. */
    private fun deform(stretch: Float) {
        thumb.rotation = 0f
        thumb.scaleX = 1f; thumb.scaleY = 1f
        thumbSurface.stretch = stretch
    }
    private class PillSurface(context: Context, private val baseWidth: Float,
                              private val baseHeight: Float) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff00564d.toInt() }
        private val shape = RectF()
        var stretch = 0f
            set(value) {
                val next = value.coerceIn(-.04f, .22f)
                if (field != next) { field = next; invalidate() }
            }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = baseWidth * (1f + stretch)
            val lift = (stretch.coerceAtLeast(0f) / .035f).coerceIn(0f, 1f)
            val h = baseHeight * (1f + .045f * lift)
            shape.set((width - w) / 2f, (height - h) / 2f,
                (width + w) / 2f, (height + h) / 2f)
            // Preserve circular endcaps, rather than scaling them into ellipses.
            canvas.drawRoundRect(shape, h / 2f, h / 2f, paint)
        }
    }
    private fun relaxHeldShape() {
        if (dragging) { visualVelocity = 0f; feedback(thumb.translationX) }
    }
    private fun feedback(offset: Float) {
        val fraction = if (travel() > 0) (offset / travel()).coerceIn(-1f, 1f) else 0f
        if (!OrbitMotion.enabled()) {
            stretchSpring?.cancel(); stretchValue.value = 0f; deform(0f)
        } else {
            val speed = (abs(visualVelocity) / dp(1600)).coerceIn(0f, 1f)
            val tension = (abs(fraction) * .055f + speed * .07f +
                pullBeyondEdge * .065f + (if (dragging) .035f else 0f))
                .coerceAtMost(if (compact) .14f else .18f)
            val spring = stretchSpring ?: SpringAnimation(stretchValue).apply {
                this.spring = SpringForce(0f).apply { dampingRatio = .80f; stiffness = 460f }
                setMinValue(-.04f); setMaxValue(.22f); setMinimumVisibleChange(.001f)
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
                spring = SpringForce(0f).apply { dampingRatio = .82f; stiffness = if (compact) 350f else 330f }
                // Carry real release momentum. Overshoot is visual and never commits an action.
                setStartVelocity(velocity.coerceIn(-dp(1600).toFloat(), dp(1600).toFloat()))
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
