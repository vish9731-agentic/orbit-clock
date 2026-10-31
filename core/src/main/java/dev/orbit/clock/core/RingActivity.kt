package dev.orbit.clock.core

import android.content.*
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.*
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
    private val gesture = RotaryGesture()
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
        val colors = OrbitPalette(this)
        val watch = packageManager.hasSystemFeature("android.hardware.type.watch")
        fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
        val a = Store(this).get(id)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(colors.surface); isFocusableInTouchMode = true
        }
        fun label(value: String, size: Float, color: Int = colors.onSurface) = TextView(this).apply {
            text = value; textSize = size; gravity = Gravity.CENTER; setTextColor(color); includeFontPadding = false
        }
        if (!watch) body.addView(label("ALARM", 12f, colors.primary).apply { letterSpacing = .16f }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(28) })
        body.addView(label(LocalTime.now().format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a")), if (watch) 40f else 72f).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        body.addView(label(a?.label ?: "Alarm", if (watch) 18f else 24f, colors.muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(if (watch) 12 else 48) })
        fun control(value: String, tonal: Boolean, callback: () -> Unit) = MaterialButton(this).apply {
            text = value; isAllCaps = false; textSize = 16f; cornerRadius = dp(32); insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(if (tonal) colors.primaryContainer else colors.primary)
            setTextColor(if (tonal) colors.onPrimaryContainer else colors.onPrimary)
            setOnClickListener { callback() }
        }
        body.addView(control("Snooze ${a?.snoozeMinutes ?: 10} min", false) { act(true) }, LinearLayout.LayoutParams(-1, dp(if (watch) 48 else 64)))
        body.addView(control("Dismiss", true) { act(false) }, LinearLayout.LayoutParams(-1, dp(if (watch) 48 else 64)).apply { topMargin = dp(12) })
        progress = label(if (watch) "↻ Snooze  ·  ↺ Dismiss" else "Take your time. Your alarm is here.", 12f, colors.muted)
        body.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (watch) 12 else 28) })
        indicator = LinearProgressIndicator(this).apply { max = 100; visibility = View.INVISIBLE; trackCornerRadius = dp(3) }
        body.addView(indicator, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(12) })
        setContentView(body)
        ViewCompat.setOnApplyWindowInsetsListener(body) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val pad = dp(if (watch) 28 else 32)
            v.setPadding(pad + bars.left, dp(16) + bars.top, pad + bars.right, dp(16) + bars.bottom); insets
        }
        ViewCompat.requestApplyInsets(body); body.requestFocus()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    private fun act(snooze: Boolean) { Scheduler.act(this, id, at, snooze); finish() }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val action = gesture.turn(event.getAxisValue(MotionEvent.AXIS_SCROLL), SystemClock.elapsedRealtime())
            progress.text = "${if (gesture.isSnoozing) "Snooze" else "Dismiss"} ${gesture.progress()}%"
            indicator.visibility = View.VISIBLE; indicator.setProgressCompat(gesture.progress(), true)
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            when (action) { RotaryGesture.Action.SNOOZE -> act(true); RotaryGesture.Action.DISMISS -> act(false); else -> Unit }
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun onDestroy() { unregisterReceiver(changes); super.onDestroy() }
}
