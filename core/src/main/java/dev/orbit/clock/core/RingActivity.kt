package dev.orbit.clock.core

import android.content.*
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.*
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RingActivity : AppCompatActivity() {
    private var id = ""
    private var at = 0L
    private var watch = false
    private var selectedMinutes = 10
    private var volumeKey = KeyEvent.KEYCODE_UNKNOWN
    private val gesture = RotaryGesture()
    private lateinit var duration: TextView
    private lateinit var decrease: MaterialButton
    private lateinit var increase: MaterialButton
    private lateinit var progress: TextView
    private lateinit var indicator: LinearProgressIndicator
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val a = Store(c).get(id)
            if (a == null || !Scheduler.isActive(c, a, at)) finish()
        }
    }
    override fun onCreate(state: Bundle?) {
        setTheme(R.style.Theme_OrbitClock_Ringing)
        super.onCreate(state)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        id = intent.getStringExtra("id") ?: ""; at = intent.getLongExtra("at", 0)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, IntentFilter(Store.CHANGED), RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, IntentFilter(Store.CHANGED))
        val alarm = Store(this).get(id)
        if (alarm == null || !Scheduler.isActive(this, alarm, at)) { finish(); return }
        val sameOccurrence = state != null && state.getString("ring.id") == id && state.getLong("ring.at") == at
        selectedMinutes = SnoozeDuration.clamp(if (sameOccurrence) state!!.getInt("ring.minutes", alarm.snoozeMinutes) else alarm.snoozeMinutes)
        val colors = OrbitPalette(this)
        watch = packageManager.hasSystemFeature("android.hardware.type.watch")
        fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(if (watch) 28 else 32), dp(if (watch) 8 else 24), dp(if (watch) 28 else 32), dp(if (watch) 8 else 24))
            isFocusableInTouchMode = true
        }
        fun label(value: String, size: Float, color: Int = colors.onSurface) = TextView(this).apply {
            text = value; textSize = size; gravity = Gravity.CENTER; setTextColor(color); includeFontPadding = false
        }
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"))
        if (watch) {
            // Keep all touch controls visible on a small round screen. Rotary progress reuses this line.
            progress = label("$time · ${alarm.label}", 16f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            body.addView(progress, LinearLayout.LayoutParams(dp(128), -2).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(10) })
        } else {
            body.addView(label("ALARM", 12f, colors.primary).apply { letterSpacing = .16f }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })
            body.addView(label(time, 64f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
            body.addView(label(alarm.label, 24f, colors.muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(32) })
        }
        fun control(value: String, tonal: Boolean, callback: () -> Unit) = MaterialButton(this).apply {
            text = value; isAllCaps = false; textSize = 16f; cornerRadius = dp(32); insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(if (tonal) colors.primaryContainer else colors.primary)
            setTextColor(if (tonal) colors.onPrimaryContainer else colors.onPrimary)
            setOnClickListener { callback() }
        }
        val stepper = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun step(value: String, description: String, callback: () -> Unit) = control(value, true, callback).apply {
            contentDescription = description; textSize = 24f; minWidth = 0; minimumWidth = 0
            setPadding(0, 0, 0, 0); cornerRadius = dp(24)
            backgroundTintList = ColorStateList.valueOf(colors.containerHigh); setTextColor(colors.onSurface)
        }
        decrease = step("−", "Decrease snooze by 5 minutes") { selectedMinutes = SnoozeDuration.decrease(selectedMinutes); updateDuration() }
        increase = step("+", "Increase snooze by 5 minutes") { selectedMinutes = SnoozeDuration.increase(selectedMinutes); updateDuration() }
        duration = label("", if (watch) 18f else 20f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val size = dp(if (watch) 40 else 48)
        stepper.addView(decrease, LinearLayout.LayoutParams(size, size))
        stepper.addView(duration, LinearLayout.LayoutParams(dp(if (watch) 68 else 96), size))
        stepper.addView(increase, LinearLayout.LayoutParams(size, size))
        body.addView(stepper, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(if (watch) 8 else 20) })
        updateDuration()
        body.addView(control("Snooze", false) { act(true) }, LinearLayout.LayoutParams(-1, dp(if (watch) 44 else 64)))
        body.addView(control("Dismiss", true) { act(false) }, LinearLayout.LayoutParams(-1, dp(if (watch) 44 else 64)).apply { topMargin = dp(if (watch) 8 else 12) })
        if (!watch) {
            progress = label("", 12f, colors.muted).apply { visibility = View.GONE }
            body.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        }
        indicator = LinearProgressIndicator(this).apply { max = 100; visibility = View.GONE; trackCornerRadius = dp(3) }
        body.addView(indicator, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(8) })
        val scroller = ScrollView(this).apply {
            isFillViewport = true; setBackgroundColor(colors.surface)
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
        setContentView(scroller)
        ViewCompat.setOnApplyWindowInsetsListener(scroller) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, scroller).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        ViewCompat.requestApplyInsets(scroller); body.requestFocus()
    }
    private fun updateDuration() {
        duration.text = "$selectedMinutes min"
        duration.contentDescription = "Snooze duration: $selectedMinutes minutes"
        decrease.isEnabled = selectedMinutes > SnoozeDuration.MIN
        increase.isEnabled = selectedMinutes < SnoozeDuration.MAX
        decrease.alpha = if (decrease.isEnabled) 1f else .38f
        increase.alpha = if (increase.isEnabled) 1f else .38f
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("ring.id", id); out.putLong("ring.at", at); out.putInt("ring.minutes", selectedMinutes)
        super.onSaveInstanceState(out)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    private fun act(snooze: Boolean) {
        Scheduler.act(this, id, at, snooze, snoozeMinutesOverride = if (snooze) selectedMinutes else null)
        finish()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Consume the whole press, including repeats. Act on release so holding a key
            // cannot spill volume-change events into the screen beneath the alarm.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && volumeKey == KeyEvent.KEYCODE_UNKNOWN) {
                volumeKey = event.keyCode
            } else if (event.action == KeyEvent.ACTION_UP && volumeKey == event.keyCode) {
                volumeKey = KeyEvent.KEYCODE_UNKNOWN
                if (!event.isCanceled) act(true)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) volumeKey = KeyEvent.KEYCODE_UNKNOWN
        super.onWindowFocusChanged(hasFocus)
    }
    override fun onPause() {
        volumeKey = KeyEvent.KEYCODE_UNKNOWN
        super.onPause()
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val action = gesture.turn(event.getAxisValue(MotionEvent.AXIS_SCROLL), SystemClock.elapsedRealtime())
            progress.text = "${if (gesture.isSnoozing) "Snooze" else "Dismiss"} ${gesture.progress()}%"
            progress.visibility = View.VISIBLE
            if (!watch) { indicator.visibility = View.VISIBLE; indicator.setProgressCompat(gesture.progress(), true) }
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            when (action) { RotaryGesture.Action.SNOOZE -> act(true); RotaryGesture.Action.DISMISS -> act(false); else -> Unit }
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun onDestroy() { unregisterReceiver(changes); super.onDestroy() }
}
