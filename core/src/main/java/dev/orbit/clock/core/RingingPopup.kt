package dev.orbit.clock.core

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Explicitly permitted phone controls. No swipe, outside-touch or Back dismissal. */
class RingingPopup(private val service: Context) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var panel: View? = null
    private var identity = ""
    private var clock: TextView? = null
    private var durationValue: TextView? = null
    private var durationGroup: View? = null
    private var decrease: MaterialButton? = null
    private var increase: MaterialButton? = null
    private var snooze: MaterialButton? = null
    private var shownMinutes = -1
    private var lastOpenAt = 0L
    val isShowing: Boolean get() = panel != null

    fun update(alarm: Alarm?, at: Long) {
        val eligible = alarm != null && canPresent(service) &&
            Scheduler.isActive(service, alarm, at)
        if (!eligible || alarm == null) { hide(); return }
        val minutes = RingControls.minutes(service, alarm, at)
        fun dp(value: Int) = (service.resources.displayMetrics.density * value).roundToInt()
        val metrics = windows.currentWindowMetrics
        val bounds = metrics.bounds
        val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val availableHeight = (bounds.height() - bars.top - bars.bottom - dp(16)).coerceAtLeast(1)
        val width = (bounds.width() - bars.left - bars.right - dp(24)).coerceAtMost(dp(400)).coerceAtLeast(1)
        // Selection changes keep the existing touch/accessibility targets mounted.
        val configuration = service.resources.configuration
        val key = listOf(alarm.id, at, alarm.label, alarm.snoozeMinutes, bounds.width(), bounds.height(),
            bars, configuration.fontScale, configuration.densityDpi, configuration.locales.toLanguageTags(),
            android.text.format.DateFormat.is24HourFormat(service)).joinToString("|")
        if (panel != null && identity == key) { updateClock(); updateDuration(minutes); return }
        hide()
        val context = DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(service, R.style.Theme_OrbitClock_Ringing))
        // The approved reference uses black, charcoal and a quiet amber action in both system themes.
        val foreground = 0xfff5f3f0.toInt()
        val muted = 0xffc6c4c1.toInt()
        val charcoal = 0xff252525.toInt()
        val amber = 0xffffc15a.toInt()
        fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun label(value: String, size: Float, color: Int, weight: Int = 400) = TextView(context).apply {
            text = value; textSize = size; setTextColor(color); includeFontPadding = false
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
            gravity = Gravity.CENTER
        }
        fun currentAlarm() = Store(service).get(alarm.id)?.takeIf { Scheduler.isActive(service, it, at) }
        fun openFullScreen() {
            // Re-read the exact occurrence at activation: an old card must never reopen a later alarm.
            if (currentAlarm() == null) return
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastOpenAt < 800L) return
            lastOpenAt = now
            runCatching {
                service.startActivity(Intent(service, RingActivity::class.java)
                    .setData(Uri.parse("orbit://ring/${alarm.id}"))
                    .putExtra("id", alarm.id).putExtra("at", at)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }.onFailure {
                lastOpenAt = 0L
                android.util.Log.w("OrbitRingingPopup", "Full-screen alarm unavailable", it)
            }
            // RingActivity's visibility broadcast hides the panel only after it actually opens.
        }
        fun openOnTap(view: View) {
            view.isFocusable = true
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            view.setOnClickListener { openFullScreen() }
            view.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLICK, "Open full-screen alarm"))
                }
            }
            val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
            var downX = 0f; var downY = 0f; var dragged = false; var trackingTap = false
            view.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; dragged = false; trackingTap = true }
                    MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - downX) > slop ||
                        kotlin.math.abs(event.y - downY) > slop) dragged = true
                    MotionEvent.ACTION_POINTER_DOWN -> dragged = true
                    MotionEvent.ACTION_UP -> {
                        // Even a drag on a card too short to scroll must not become a navigation tap.
                        target.isPressed = false
                        val activate = trackingTap && !dragged
                        trackingTap = false
                        if (activate) target.performClick()
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_CANCEL -> { target.isPressed = false; dragged = false; trackingTap = false }
                }
                false
            }
        }
        val root = column().apply {
            tag = "popup_card"
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(0xff050505.toInt()); cornerRadius = dp(32).toFloat() }
            elevation = dp(8).toFloat()
            contentDescription = "Persistent alarm controls"
            openOnTap(this)
        }
        fun button(value: String, description: String, adjustment: Boolean = false,
            primary: Boolean = false, action: () -> Unit) = object : MaterialButton(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                // A disabled endpoint still owns its entire target; it cannot activate the card behind it.
                if (!isEnabled) return true
                return super.onTouchEvent(event)
            }
        }.apply {
            text = value; contentDescription = description; isAllCaps = false
            textSize = if (adjustment) 30f else 16f
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), if (adjustment) 400 else 500, false)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(if (adjustment) 0 else dp(12), 0, if (adjustment) 0 else dp(12), 0)
            cornerRadius = dp(32); insetTop = 0; insetBottom = 0; strokeWidth = 0
            elevation = 0f
            backgroundTintList = ColorStateList.valueOf(when { adjustment -> android.graphics.Color.TRANSPARENT; primary -> 0xff382800.toInt(); else -> charcoal })
            rippleColor = ColorStateList.valueOf(0x24ffffff)
            setTextColor(if (primary) amber else foreground)
            setOnClickListener { OrbitMotion.tick(this); action() }
            OrbitMotion.bind(this)
        }

        clock = label("", 52f, foreground, 350).apply {
            tag = "popup_clock"; maxLines = 1; fontFeatureSettings = "tnum"; letterSpacing = -.025f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setAutoSizeTextTypeUniformWithConfiguration(36, 52, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            openOnTap(this)
        }
        val clockHeight = max(dp(64), ceil(clock!!.paint.fontMetrics.descent - clock!!.paint.fontMetrics.ascent).toInt())
        val stepper = row().apply {
            tag = "popup_stepper"
            background = GradientDrawable().apply { setColor(charcoal); cornerRadius = dp(32).toFloat() }
        }
        decrease = button("−", "Decrease snooze by 5 minutes", adjustment = true) {
            currentAlarm()?.let { RingControls.select(service, it.id, at,
                SnoozeDuration.decrease(RingControls.minutes(service, it, at))) }
        }.apply { tag = "popup_decrease" }
        increase = button("+", "Increase snooze by 5 minutes", adjustment = true) {
            currentAlarm()?.let { RingControls.select(service, it.id, at,
                SnoozeDuration.increase(RingControls.minutes(service, it, at))) }
        }.apply { tag = "popup_increase" }
        durationGroup = column().apply {
            gravity = Gravity.CENTER; tag = "popup_duration"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            openOnTap(this)
            durationValue = label("", 16f, muted).apply {
                fontFeatureSettings = "tnum"; maxLines = 1
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(durationValue, LinearLayout.LayoutParams(-1, -2))
        }
        stepper.addView(decrease, LinearLayout.LayoutParams(dp(64), dp(64)))
        stepper.addView(durationGroup, LinearLayout.LayoutParams(0, dp(64), 1f))
        stepper.addView(increase, LinearLayout.LayoutParams(dp(64), dp(64)))
        snooze = button("Snooze", "", primary = true) {
            Scheduler.act(service, alarm.id, at, true, RingControls.minutes(service, alarm, at))
        }
        val stop = button("Stop", "Stop alarm") {
            Scheduler.act(service, alarm.id, at, false)
        }.apply { tag = "popup_stop" }
        val innerWidth = (width - dp(32)).coerceAtLeast(1)
        // Use actual scaled text widths, not orientation, to decide whether the paired columns fit.
        val centerMinimum = max(dp(64), ceil(durationValue!!.paint.measureText("60 min")).toInt() + dp(8))
        val capsuleMinimum = dp(128) + centerMinimum
        val actionMinimum = max(dp(96), ceil(max(snooze!!.paint.measureText("Snooze"), stop.paint.measureText("Stop"))).toInt() + dp(24))
        val columnsFit = innerWidth >= capsuleMinimum + actionMinimum + dp(12)
        val main = LinearLayout(context).apply {
            tag = "popup_main"; orientation = if (columnsFit) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }
        val left = column().apply {
            addView(clock, LinearLayout.LayoutParams(-1, clockHeight).apply { bottomMargin = dp(8) })
            addView(stepper, LinearLayout.LayoutParams(-1, dp(64)))
        }
        val actions = LinearLayout(context).apply {
            tag = "popup_actions"; orientation = if (columnsFit) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        if (columnsFit) {
            val actionWidth = min((innerWidth * .39f).roundToInt().coerceAtLeast(actionMinimum), innerWidth - capsuleMinimum - dp(12))
            main.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
            actions.addView(snooze, LinearLayout.LayoutParams(-1, clockHeight).apply { bottomMargin = dp(8) })
            actions.addView(stop, LinearLayout.LayoutParams(-1, dp(64)))
            main.addView(actions, LinearLayout.LayoutParams(actionWidth, -2))
        } else {
            main.addView(left, LinearLayout.LayoutParams(-1, -2))
            actions.addView(snooze, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
            actions.addView(stop, LinearLayout.LayoutParams(0, dp(56), 1f))
            main.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        root.addView(main, LinearLayout.LayoutParams(-1, -2))
        val name = alarm.label.trim().ifBlank { "Alarm" }
        root.addView(label(name, 14f, muted).apply {
            tag = "popup_label"; gravity = Gravity.START
            breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
            contentDescription = "Alarm label: " + name
            openOnTap(this)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        updateDuration(minutes)
        updateClock()

        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val viewport = ScrollView(context).apply {
            isFillViewport = false; isVerticalScrollBarEnabled = false
            addView(root, android.widget.FrameLayout.LayoutParams(-1, -2))
        }
        // Short windows/large text scroll instead of losing any action or truncating the label.
        val params = WindowManager.LayoutParams(width, root.measuredHeight.coerceAtMost(availableHeight),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(8)
                setTitle("Orbit alarm controls")
            }
        runCatching { windows.addView(viewport, params) }.onSuccess {
            panel = viewport; identity = key
        }.onFailure {
            clearContent()
            android.util.Log.w("OrbitRingingPopup", "Persistent controls unavailable", it)
        }
    }

    private fun updateDuration(minutes: Int) {
        if (minutes == shownMinutes) return
        shownMinutes = minutes
        val value = minutes.toString() + " min"
        durationValue?.let { if (panel == null) it.text = value else OrbitMotion.text(it, value) }
        durationGroup?.contentDescription = "Snooze duration: " + minutes + " minutes"
        snooze?.contentDescription = "Snooze alarm for " + minutes + " minutes"
        decrease?.let { it.isEnabled = minutes > SnoozeDuration.MIN; it.alpha = if (it.isEnabled) 1f else .38f }
        increase?.let { it.isEnabled = minutes < SnoozeDuration.MAX; it.alpha = if (it.isEnabled) 1f else .38f }
    }

    private fun updateClock() {
        val value = android.text.format.DateFormat.getTimeFormat(service).format(java.util.Date())
        clock?.let {
            if (it.text.toString() != value) it.text = value
            it.contentDescription = "Alarm time: " + value
        }
    }

    private fun clearContent() {
        clock = null; durationValue = null; durationGroup = null
        decrease = null; increase = null; snooze = null; shownMinutes = -1
    }

    fun hide() {
        panel?.let { runCatching { windows.removeViewImmediate(it) } }
        panel = null; identity = ""; clearContent()
    }

    companion object {
        fun canPresent(context: Context): Boolean = permitted(context) && !RingActivity.isRingingVisible &&
            context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        fun permitted(context: Context): Boolean =
            !context.packageManager.hasSystemFeature("android.hardware.type.watch") && Settings.canDrawOverlays(context)
    }
}
