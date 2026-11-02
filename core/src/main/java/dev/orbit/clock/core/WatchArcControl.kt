package dev.orbit.clock.core

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.FrameLayout
import kotlin.math.*

/** Bezel-first, vector-drawn short bottom arc. Motion never commits an alarm action. */
class WatchArcControl(context: Context, private val snooze: () -> Unit, private val stop: () -> Unit) : FrameLayout(context) {
    private fun dp(value: Float) = resources.displayMetrics.density * value
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff103a35.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = dp(18f) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffd3e3df.toInt(); textAlign = Paint.Align.CENTER; textSize = dp(9f) * resources.configuration.fontScale.coerceAtMost(1.3f) }
    private val oval = RectF()
    private var fraction = 0f
    private var velocity = 0f
    private var radius = 0f
    private val halfSweep = 34f
    private var completing = false
    private var completion: (() -> Unit)? = null
    private var dragging = false
    private var downX = 0f
    private var downFraction = 0f
    private var tracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val touchActive: Boolean get() = dragging || completing
    private var target = 0f
    private var motionRunning = false
    private var lastFrame = 0L
    private val frames = Choreographer.getInstance()
    private val motionFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!motionRunning) return
            // A delayed UI frame must not jump across most of the rail. Advance
            // at most 32ms of spring time per rendered frame, preserving velocity.
            val dt = if (lastFrame == 0L) 1.0 / 60.0 else ((frameTimeNanos - lastFrame) / 1e9).coerceIn(0.0, .032)
            lastFrame = frameTimeNanos
            if (!OrbitMotion.enabled()) { fraction = target; velocity = 0f }
            else {
                // Analytic damped spring, stiffness 190, damping ratio .86.
                val omega = 13.7840487521; val damping = .86 * omega
                val frequency = omega * sqrt(1.0 - .86 * .86)
                val a = (fraction - target).toDouble()
                val b = (velocity + damping * a) / frequency
                val sine = sin(frequency * dt); val cosine = cos(frequency * dt); val decay = exp(-damping * dt)
                fraction = (target + decay * (a * cosine + b * sine)).toFloat().coerceIn(-1f, 1f)
                velocity = (decay * ((b * frequency - damping * a) * cosine + (-a * frequency - damping * b) * sine)).toFloat()
            }
            placeThumb(); invalidate()
            if (abs(fraction - target) < .0004f && abs(velocity) < .006f) {
                fraction = target; velocity = 0f; motionRunning = false; placeThumb(); invalidate()
                if (completing) finishCompletion()
            } else frames.postFrameCallback(this)
        }
    }
    private fun cancelMotion() { motionRunning = false; lastFrame = 0L; frames.removeFrameCallback(motionFrame) }
    private val thumb = ArcThumb(context)
    private val leftAction = View(context).apply { contentDescription = "Snooze"; isClickable = true; isFocusable = true; setOnClickListener { if (!completing) snooze() } }
    private val rightAction = View(context).apply { contentDescription = "Dismiss"; isClickable = true; isFocusable = true; setOnClickListener { if (!completing) stop() } }

    init {
        tag = "watch_ring_slider"; setWillNotDraw(false); clipChildren = false
        addView(leftAction, LayoutParams(dp(40f).toInt(), dp(32f).toInt()))
        addView(rightAction, LayoutParams(dp(40f).toInt(), dp(32f).toInt()))
        addView(thumb, LayoutParams(dp(40f).toInt(), dp(40f).toInt()))
        thumb.contentDescription = "Alarm slider: rotate clockwise to snooze, anticlockwise to dismiss"
        thumb.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (completing || !thumb.containsPill(event.x, event.y)) false else {
                        cancelMotion(); downX = event.rawX; downFraction = fraction; dragging = true
                        tracker?.recycle(); tracker = VelocityTracker.obtain(); track(event)
                        parent.requestDisallowInterceptTouchEvent(true); true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging && radius > 0f) {
                        fraction = (downFraction + (event.rawX - downX) / radius).coerceIn(-1f, 1f)
                        velocity = 0f; track(event); placeThumb(); invalidate()
                    }; true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    track(event); tracker?.computeCurrentVelocity(1000)
                    val speed = (tracker?.xVelocity ?: 0f) / radius.coerceAtLeast(1f)
                    tracker?.recycle(); tracker = null
                    val displacement = event.rawX - downX
                    val commit = dragging && event.actionMasked == MotionEvent.ACTION_UP && abs(displacement) > touchSlop && abs(fraction) >= .8f && displacement * fraction > 0
                    dragging = false; parent.requestDisallowInterceptTouchEvent(false)
                    if (commit) { if (fraction < 0) snooze() else stop() }
                    else animateTo(0f, speed.coerceIn(-2f, 2f) * .45f)
                    true
                }
                else -> false
            }
        }
    }
    private fun track(event: MotionEvent) {
        val copy = MotionEvent.obtain(event); copy.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker?.addMovement(copy); copy.recycle()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        radius = (min(w, h) / 2f - dp(16f)).coerceAtLeast(1f)
        oval.set(w / 2f - radius, h / 2f - radius, w / 2f + radius, h / 2f + radius)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val size = dp(40f).toInt(); val centerX = width / 2f; val centerY = height / 2f
        thumb.layout((centerX - size / 2).toInt(), (centerY + radius - size / 2).toInt(), (centerX + size / 2).toInt(), (centerY + radius + size / 2).toInt())
        fun action(view: View, angle: Float) {
            val theta = Math.toRadians(angle.toDouble()); val x = centerX + radius * cos(theta).toFloat(); val y = centerY + radius * sin(theta).toFloat()
            val aw = dp(40f).toInt(); val ah = dp(32f).toInt()
            view.layout((x - aw / 2).toInt(), (y - ah / 2).toInt(), (x + aw / 2).toInt(), (y + ah / 2).toInt())
        }
        action(leftAction, 116f); action(rightAction, 64f); placeThumb()
    }
    private fun placeThumb() {
        val angle = Math.toRadians((90f - fraction * halfSweep).toDouble())
        thumb.translationX = radius * cos(angle).toFloat()
        thumb.translationY = radius * (sin(angle).toFloat() - 1f)
        thumb.tangent = -fraction * halfSweep
        thumb.stretch = if (OrbitMotion.enabled()) (abs(velocity) * .025f).coerceAtMost(.065f) else 0f
        thumb.invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); canvas.drawArc(oval, 90f - halfSweep, halfSweep * 2f, false, track)
        fun label(text: String, angle: Float, alpha: Float) {
            val theta = Math.toRadians(angle.toDouble()); val save = canvas.save()
            canvas.translate(width / 2f + radius * cos(theta).toFloat(), height / 2f + radius * sin(theta).toFloat())
            canvas.rotate(angle - 90f); textPaint.alpha = (255 * alpha).toInt()
            canvas.drawText(text, 0f, -(textPaint.ascent() + textPaint.descent()) / 2f, textPaint); canvas.restoreToCount(save)
        }
        label("Snooze", 116f, if (fraction < 0) (1f - abs(fraction) * 2.5f).coerceAtLeast(0f) else 1f)
        label("Stop", 64f, if (fraction > 0) (1f - fraction * 2.5f).coerceAtLeast(0f) else 1f)
    }
    private fun animateTo(target: Float, startVelocity: Float = velocity) {
        if (!OrbitMotion.enabled()) {
            cancelMotion(); fraction = target; this.target = target; velocity = 0f; placeThumb(); invalidate()
            if (completing) finishCompletion()
        } else {
            this.target = target
            if (!motionRunning) {
                velocity = startVelocity; motionRunning = true; lastFrame = 0L
                frames.postFrameCallback(motionFrame)
            }
        }
    }
    fun setBezelProgress(snoozing: Boolean, progress: Float) {
        if (!dragging && !completing) animateTo(progress.coerceIn(0f, 1f) * if (snoozing) -1f else 1f)
    }
    fun releaseBezel() { if (!dragging && !completing) animateTo(0f) }
    fun complete(snoozing: Boolean, finished: () -> Unit) {
        if (completing) return
        completing = true; dragging = false; completion = finished
        animateTo(if (snoozing) -1f else 1f)
        // A lost frame/window must never leave a completed alarm stuck on screen.
        postDelayed({ if (completing) finishCompletion() }, 1600)
    }
    private fun finishCompletion() {
        val callback = completion ?: return; completion = null
        cancelMotion(); callback()
    }
    fun thumbCenter(): Pair<Float, Float> = Pair(thumb.left + thumb.width / 2f + thumb.translationX, thumb.top + thumb.height / 2f + thumb.translationY)
    override fun onDetachedFromWindow() {
        cancelMotion(); tracker?.recycle(); tracker = null; completion = null; super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus && !completing) {
            cancelMotion(); fraction = 0f; target = 0f; velocity = 0f; dragging = false; placeThumb(); invalidate()
        }
    }
    private class ArcThumb(context: Context) : View(context) {
        var tangent = 0f
        var stretch = 0f
        private fun dp(value: Float) = resources.displayMetrics.density * value
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff00564d.toInt() }
        private val bounds = RectF()
        private val glyph = requireNotNull(context.getDrawable(R.drawable.ic_alarm)).mutate().apply { setTint(0xff74e8d7.toInt()) }
        fun containsPill(x: Float, y: Float): Boolean {
            val theta = Math.toRadians(tangent.toDouble())
            val dx = x - width / 2f; val dy = y - height / 2f
            val localX = (dx * cos(theta).toFloat() + dy * sin(theta).toFloat()) / (1f + stretch)
            val localY = (-dx * sin(theta).toFloat() + dy * cos(theta).toFloat()) / (1f - stretch * .5f)
            val endDistance = (abs(localX) - dp(6f)).coerceAtLeast(0f)
            return endDistance * endDistance + localY * localY <= dp(12f) * dp(12f)
        }
        override fun onDraw(canvas: Canvas) {
            val saved = canvas.save(); canvas.translate(width / 2f, height / 2f)
            val shape = canvas.save(); canvas.rotate(tangent); canvas.scale(1f + stretch, 1f - stretch * .5f)
            bounds.set(-dp(18f), -dp(12f), dp(18f), dp(12f)); canvas.drawRoundRect(bounds, dp(12f), dp(12f), paint)
            canvas.restoreToCount(shape)
            val half = dp(8f).toInt(); glyph.setBounds(-half, -half, half, half); glyph.draw(canvas)
            canvas.restoreToCount(saved)
        }
    }
}
