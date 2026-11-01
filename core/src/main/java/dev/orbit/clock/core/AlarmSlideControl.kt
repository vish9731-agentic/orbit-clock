package dev.orbit.clock.core

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
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
    private val thumb: MaterialButton
    init {
        background = GradientDrawable().apply { setColor(0xff103a35.toInt()); cornerRadius = dp(48).toFloat() }
        fun action(text: String, gravity: Int, callback: () -> Unit) {
            addView(MaterialButton(context).apply {
                this.text = text; isAllCaps = false; textSize = 28f; setTextColor(0xffd3e3df.toInt())
                backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0); insetTop = 0; insetBottom = 0
                setOnClickListener { if (!fired) { fired = true; callback() } }
            }, LayoutParams(dp(132), -1, gravity))
        }
        action("Snooze", Gravity.START, snooze)
        action("Stop", Gravity.END, stop)
        thumb = MaterialButton(context).apply {
            contentDescription = "Alarm slider: swipe left to snooze, right to stop"
            setIconResource(R.drawable.ic_alarm); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconTint = ColorStateList.valueOf(0xff74e8d7.toInt()); iconSize = dp(28); iconPadding = 0
            minWidth = 0; minimumWidth = 0; setPadding(dp(34), 0, dp(34), 0)
            cornerRadius = dp(40); insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(0xff00564d.toInt())
            setOnClickListener { announceForAccessibility("Swipe left to snooze, right to stop") }
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startX = e.rawX; dragging = true; parent.requestDisallowInterceptTouchEvent(true); true }
                    MotionEvent.ACTION_MOVE -> {
                        if (dragging) translationX = (e.rawX - startX).coerceIn(-travel(), travel())
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val offset = translationX
                        val complete = dragging && travel() > 0 && abs(offset) >= travel() * .7f
                        dragging = false; parent.requestDisallowInterceptTouchEvent(false)
                        if (complete && !fired) { fired = true; if (offset < 0) snooze() else stop() }
                        else { translationX = 0f; performClick() }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { dragging = false; translationX = 0f; parent.requestDisallowInterceptTouchEvent(false); true }
                    else -> false
                }
            }
        }
        addView(thumb, LayoutParams(dp(100), dp(68), Gravity.CENTER))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun travel() = ((width - dp(100)) / 2f - dp(10)).coerceAtLeast(0f)
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) { dragging = false; thumb.translationX = 0f }
    }
}
