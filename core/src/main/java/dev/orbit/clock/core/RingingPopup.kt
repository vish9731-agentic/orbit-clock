package dev.orbit.clock.core

import android.app.KeyguardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

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
    val isShowing: Boolean get() = panel != null

    fun update(alarm: Alarm?, at: Long) {
        val eligible = alarm != null && canPresent(service) &&
            Scheduler.isActive(service, alarm, at)
        if (!eligible || alarm == null) { hide(); return }
        val minutes = RingControls.minutes(service, alarm, at)
        fun dp(value: Int) = (service.resources.displayMetrics.density * value).toInt()
        val metrics = windows.currentWindowMetrics
        val bounds = metrics.bounds
        val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val availableHeight = (bounds.height() - bars.top - bars.bottom - dp(16)).coerceAtLeast(1)
        val compact = availableHeight < dp(320)
        val target = 48
        // Duration changes update this window in place, preserving touch/accessibility focus.
        val key = listOf(alarm.id, at, alarm.label, alarm.snoozeMinutes, bounds.width(), bounds.height(),
            bars, service.resources.configuration.fontScale).joinToString("|")
        if (panel != null && identity == key) { updateClock(); updateDuration(minutes); return }
        hide()
        val context = DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(service, R.style.Theme_OrbitClock_Ringing))
        val colors = OrbitPalette(context)
        fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun label(value: String, size: Float, color: Int, weight: Int = 400) = TextView(context).apply {
            text = value; textSize = size; setTextColor(color); includeFontPadding = false
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
            gravity = Gravity.CENTER
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(colors.containerHigh); cornerRadius = dp(28).toFloat()
                setStroke(dp(1), MaterialColors.layer(colors.surface, colors.onSurface, .08f))
            }
            elevation = dp(8).toFloat()
            contentDescription = "Persistent alarm controls"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        fun button(value: String, description: String, prominent: Boolean, action: () -> Unit) = MaterialButton(context).apply {
            text = value; contentDescription = description; isAllCaps = false
            textSize = if (prominent) 24f else 14f
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), if (prominent) 400 else 500, false)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            cornerRadius = dp(if (prominent) target / 2 else 24); insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(if (prominent) colors.primary else colors.secondaryContainer)
            setTextColor(if (prominent) colors.onPrimary else colors.onSecondaryContainer)
            if (prominent) setAutoSizeTextTypeUniformWithConfiguration(18, 24, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setOnClickListener { OrbitMotion.tick(this); action() }
            OrbitMotion.bind(this)
        }

        // Time leads; the smaller duration readout stays between generous controls.
        val header = row()
        clock = label("", if (compact) 32f else 36f, colors.onSurface).apply {
            tag = "popup_clock"; maxLines = 1; fontFeatureSettings = "tnum"; letterSpacing = -.015f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        header.addView(clock, LinearLayout.LayoutParams(0, -2, 1f))
        val name = alarm.label.trim().ifBlank { "Alarm" }
        header.addView(label(name, 11f, colors.muted).apply {
            tag = "popup_label"; maxLines = 1; maxWidth = dp(64); ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            contentDescription = "Alarm label: " + name
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val durationRow = row()
        decrease = button("−", "Decrease snooze by 5 minutes", true) {
            RingControls.select(service, alarm.id, at,
                SnoozeDuration.decrease(RingControls.minutes(service, alarm, at)))
        }.apply { tag = "popup_decrease" }
        increase = button("+", "Increase snooze by 5 minutes", true) {
            RingControls.select(service, alarm.id, at,
                SnoozeDuration.increase(RingControls.minutes(service, alarm, at)))
        }.apply { tag = "popup_increase" }
        durationGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            tag = "popup_duration"; isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            durationValue = label("", 16f, colors.primary, 500).apply {
                fontFeatureSettings = "tnum"; maxLines = 1
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(durationValue, LinearLayout.LayoutParams(-1, -2))
            addView(label("min", 12f, colors.muted, 500).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        durationRow.addView(decrease, LinearLayout.LayoutParams(dp(target), dp(target)))
        durationRow.addView(durationGroup, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(8); marginEnd = dp(8)
        })
        durationRow.addView(increase, LinearLayout.LayoutParams(dp(target), dp(target)))
        root.addView(durationRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        val actions = row().apply { tag = "popup_actions" }
        snooze = button("Snooze", "", false) {
            Scheduler.act(service, alarm.id, at, true, RingControls.minutes(service, alarm, at))
        }
        actions.addView(snooze, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        actions.addView(button("Stop", "Stop alarm", false) {
            Scheduler.act(service, alarm.id, at, false)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))
        updateDuration(minutes)
        updateClock()

        val width = (bounds.width() - bars.left - bars.right - dp(24)).coerceAtMost(dp(400)).coerceAtLeast(1)
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val viewport = ScrollView(context).apply {
            isFillViewport = false; isVerticalScrollBarEnabled = false
            addView(root, android.widget.FrameLayout.LayoutParams(-1, -2))
        }
        // Short landscape windows/large text can scroll instead of losing the actions.
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
        durationValue?.let { if (panel == null) it.text = minutes.toString() else OrbitMotion.text(it, minutes.toString()) }
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
