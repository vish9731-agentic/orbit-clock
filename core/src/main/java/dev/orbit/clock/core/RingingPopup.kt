package dev.orbit.clock.core

import android.app.KeyguardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Explicitly permitted phone controls. No swipe, outside-touch or Back dismissal. */
class RingingPopup(private val service: Context) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var panel: View? = null
    private var identity = ""
    private var clock: TextView? = null
    val isShowing: Boolean get() = panel != null

    fun update(alarm: Alarm?, at: Long) {
        val eligible = alarm != null && canPresent(service) &&
            Scheduler.isActive(service, alarm, at)
        if (!eligible || alarm == null) { hide(); return }
        val minutes = RingControls.minutes(service, alarm, at)
        val key = "${alarm.id}|$at|$minutes|${alarm.label}|${service.resources.displayMetrics.widthPixels}|${service.resources.displayMetrics.heightPixels}|${service.resources.configuration.fontScale}"
        if (panel != null && identity == key) { updateClock(); return }
        hide()
        val context = ContextThemeWrapper(service, R.style.Theme_OrbitClock_Ringing)
        fun dp(value: Int) = (context.resources.displayMetrics.density * value).toInt()
        fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(0xff101c19.toInt()); cornerRadius = dp(28).toFloat(); setStroke(dp(1), 0xff29443e.toInt()) }
            elevation = dp(8).toFloat()
            contentDescription = "Persistent alarm controls"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val header = row()
        header.addView(TextView(context).apply {
            text = alarm.label.trim().ifBlank { "Alarm" }; textSize = 16f; setTextColor(0xffe5ebe8.toInt())
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        clock = TextView(context).apply { textSize = 20f; setTextColor(0xffe5ebe8.toInt()); setPadding(dp(12), 0, 0, 0) }
        header.addView(clock)
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        fun button(label: String, description: String, action: () -> Unit) = MaterialButton(context).apply {
            text = label; contentDescription = description; isAllCaps = false; textSize = 16f
            minWidth = 0; minimumWidth = 0; minimumHeight = dp(48); cornerRadius = dp(24)
            setPadding(dp(8), 0, dp(8), 0)
            insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(0xff17443a.toInt()); setTextColor(0xffc8f7e9.toInt())
            setOnClickListener { OrbitMotion.tick(this); action() }
            OrbitMotion.bind(this)
        }
        val duration = row().apply { gravity = Gravity.CENTER }
        duration.addView(button("−", "Decrease snooze by 5 minutes") {
            RingControls.select(service, alarm.id, at, SnoozeDuration.decrease(RingControls.minutes(service, alarm, at)))
        }.apply { isEnabled = minutes > SnoozeDuration.MIN }, LinearLayout.LayoutParams(dp(48), dp(48)))
        duration.addView(TextView(context).apply {
            text = "$minutes min"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xffb9c2bd.toInt())
            contentDescription = "Snooze duration: $minutes minutes"
        }, LinearLayout.LayoutParams(dp(88), dp(48)))
        duration.addView(button("+", "Increase snooze by 5 minutes") {
            RingControls.select(service, alarm.id, at, SnoozeDuration.increase(RingControls.minutes(service, alarm, at)))
        }.apply { isEnabled = minutes < SnoozeDuration.MAX }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(duration)
        val actions = row()
        actions.addView(button("Snooze", "Snooze alarm for $minutes minutes") {
            Scheduler.act(service, alarm.id, at, true, RingControls.minutes(service, alarm, at))
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        actions.addView(button("Stop", "Stop alarm") {
            Scheduler.act(service, alarm.id, at, false)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val bounds = service.resources.displayMetrics
        val width = (bounds.widthPixels - dp(24)).coerceAtMost(dp(420))
        val params = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(8)
                setTitle("Orbit alarm controls")
            }
        runCatching { windows.addView(root, params) }.onSuccess {
            panel = root; identity = key; updateClock()
        }.onFailure {
            clock = null
            android.util.Log.w("OrbitRingingPopup", "Persistent controls unavailable", it)
        }
    }

    private fun updateClock() {
        clock?.text = android.text.format.DateFormat.getTimeFormat(service).format(java.util.Date())
    }

    fun hide() {
        panel?.let { runCatching { windows.removeViewImmediate(it) } }
        panel = null; clock = null; identity = ""
    }

    companion object {
        fun canPresent(context: Context): Boolean = permitted(context) && !RingActivity.isRingingVisible &&
            context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        fun permitted(context: Context): Boolean =
            !context.packageManager.hasSystemFeature("android.hardware.type.watch") && Settings.canDrawOverlays(context)
    }
}
