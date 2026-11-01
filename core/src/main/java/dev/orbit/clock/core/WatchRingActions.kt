package dev.orbit.clock.core

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Watch-only actions. Bezel progress paints an aura; it never moves hit targets or acts. */
class WatchRingActions(
    context: Context,
    private val colors: OrbitPalette,
    private val onSnooze: () -> Unit,
    private val onDismiss: () -> Unit
) : ViewGroup(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Path()
    private val arc = RectF()
    private val snoozeColor = colors.primary
    private val dismissColor = 0xfff1b7b2.toInt()
    private var snoozeButton: ImageButton? = null
    private var dismissButton: ImageButton? = null
    private var currentProgress = 0
    private var currentSnoozing = true

    init {
        tag = "watch_ring_actions"
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        minimumHeight = px(80f).roundToInt()
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        snoozeButton = actionButton("Snooze", "watch_snooze", snoozeColor, true) {
            if (isEnabled) onSnooze()
        }.also { addView(it) }
        dismissButton = actionButton("Dismiss", "watch_dismiss", dismissColor, false) {
            if (isEnabled) onDismiss()
        }.also { addView(it) }
    }

    private fun px(dp: Float) = resources.displayMetrics.density * dp

    private fun actionButton(label: String, key: String, color: Int, snooze: Boolean, action: () -> Unit) = ImageButton(context).apply {
        tag = key
        contentDescription = label
        isFocusable = true
        scaleType = ImageView.ScaleType.CENTER
        setPadding(0, 0, 0, 0)
        setImageDrawable(ActionIcon(color, px(20f).roundToInt(), snooze))
        // The visual circle starts at 32dp; the native circular touch/ripple target is 48dp.
        val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 65)), null, mask)
        setOnClickListener {
            OrbitMotion.tick(this)
            action()
        }
    }

    /** Display the existing RotaryGesture result. Completion and thresholds belong to the activity. */
    fun setProgress(snoozing: Boolean, percent: Int) {
        val value = percent.coerceIn(0, 100)
        if (currentProgress == value && currentSnoozing == snoozing) return
        currentProgress = value
        currentSnoozing = snoozing
        snoozeButton?.apply {
            isSelected = value > 0 && snoozing
            stateDescription = if (isSelected) "$value percent" else null
        }
        dismissButton?.apply {
            isSelected = value > 0 && !snoozing
            stateDescription = if (isSelected) "$value percent" else null
        }
        // Immediate physical progress also works with reduced motion; no decorative loop runs.
        invalidate()
    }

    fun resetProgress() = setProgress(true, 0)

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        snoozeButton?.apply { isEnabled = enabled; imageAlpha = if (enabled) 255 else 96 }
        dismissButton?.apply { isEnabled = enabled; imageAlpha = if (enabled) 255 else 96 }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(px(200f).roundToInt(), widthMeasureSpec), resolveSize(px(80f).roundToInt(), heightMeasureSpec))
        val touch = MeasureSpec.makeMeasureSpec(px(48f).roundToInt(), MeasureSpec.EXACTLY)
        for (index in 0 until childCount) getChildAt(index).measure(touch, touch)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val cy = height / 2f
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val cx = width * if (index == 0) .27f else .73f
            val x = (cx - child.measuredWidth / 2f).roundToInt()
            val y = (cy - child.measuredHeight / 2f).roundToInt()
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val progress = currentProgress / 100f
        drawAction(canvas, width * .27f, height / 2f, snoozeColor, true, if (currentSnoozing) progress else 0f)
        drawAction(canvas, width * .73f, height / 2f, dismissColor, false, if (!currentSnoozing) progress else 0f)
    }

    private fun drawAction(canvas: Canvas, cx: Float, cy: Float, accent: Int, snoozing: Boolean, progress: Float) {
        val opacity = if (isEnabled) 1f else .38f
        val core = px(16f + 6f * progress)
        val outer = min(height * .48f, width * .24f)
        val aura = core + (outer - core).coerceAtLeast(0f) * progress
        paint.style = Paint.Style.FILL
        if (progress > 0f) {
            // The halo extends on every side of the circular core, independently of button bounds.
            val strength = ((55 + 85 * progress) * opacity * progress).roundToInt()
            paint.shader = RadialGradient(cx, cy, aura,
                intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(accent, strength), Color.TRANSPARENT),
                floatArrayOf(0f, .68f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, aura, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = px(1f)
            for (layer in 1..3) {
                val fraction = layer / 3f
                paint.color = ColorUtils.setAlphaComponent(accent, ((24 + 45 * progress) * opacity * (1f - fraction * .4f)).roundToInt())
                canvas.drawCircle(cx, cy, core + (aura - core) * fraction, paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(colors.containerHigh, accent, .10f + .35f * progress), (255 * opacity).roundToInt())
        canvas.drawCircle(cx, cy, core, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = px(1f)
        paint.color = ColorUtils.setAlphaComponent(accent, ((105 + 130 * progress) * opacity).roundToInt())
        canvas.drawCircle(cx, cy, core, paint)

        // A curved arrow is the only direction cue: anticlockwise left, clockwise right.
        val radius = px(26f + 3f * progress)
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val start = if (snoozing) 45f else 135f
        val sweep = if (snoozing) -250f else 250f
        paint.strokeWidth = px(1.3f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = ColorUtils.setAlphaComponent(accent, (100 * opacity).roundToInt())
        canvas.drawArc(arc, start, sweep, false, paint)
        if (progress > 0f) {
            paint.strokeWidth = px(2.2f)
            paint.color = ColorUtils.setAlphaComponent(accent, (235 * opacity).roundToInt())
            canvas.drawArc(arc, start, sweep * progress, false, paint)
        }
        val end = Math.toRadians((start + sweep).toDouble())
        val x = cx + cos(end).toFloat() * radius
        val y = cy + sin(end).toFloat() * radius
        val direction = if (snoozing) -1f else 1f
        val tx = -sin(end).toFloat() * direction
        val ty = cos(end).toFloat() * direction
        val length = px(5f)
        val half = px(2.6f)
        arrow.reset()
        arrow.moveTo(x + tx * px(1f), y + ty * px(1f))
        arrow.lineTo(x - tx * length - ty * half, y - ty * length + tx * half)
        arrow.lineTo(x - tx * length + ty * half, y - ty * length - tx * half)
        arrow.close()
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.setAlphaComponent(accent, ((130 + 105 * progress) * opacity).roundToInt())
        canvas.drawPath(arrow, paint)
    }

    private class ActionIcon(private val color: Int, private val size: Int, private val snoozing: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = this@ActionIcon.color }
        override fun draw(canvas: Canvas) {
            val checkpoint = canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            if (snoozing) {
                canvas.drawCircle(12f, 12f, 8.5f, paint)
                canvas.drawLine(12f, 8f, 12f, 12f, paint)
                canvas.drawLine(12f, 12f, 15f, 12f, paint)
            } else {
                canvas.drawLine(6f, 6f, 18f, 18f, paint)
                canvas.drawLine(18f, 6f, 6f, 18f, paint)
            }
            canvas.restoreToCount(checkpoint)
        }
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
