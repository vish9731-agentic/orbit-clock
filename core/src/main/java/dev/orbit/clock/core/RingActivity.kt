package dev.orbit.clock.core

import android.app.Activity
import android.content.*
import android.os.*
import android.view.*
import android.widget.*

class RingActivity : Activity() {
    private var id = ""
    private var at = 0L
    private val gesture = RotaryGesture()
    private lateinit var progress: TextView
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val a = Store(c).get(id)
            if (a == null || a.deleted || !a.enabled || a.skipThrough >= at) finish()
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readIntent()
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, IntentFilter(Store.CHANGED), RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, IntentFilter(Store.CHANGED))
        val a = Store(this).get(id)
        val body = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(28,28,28,28)
            setBackgroundColor(0xff111318.toInt()); isFocusableInTouchMode=true
        }
        fun label(s: String, size: Float) = TextView(this).apply { text=s; textSize=size; gravity=Gravity.CENTER; setTextColor(0xffe5e1ee.toInt()) }
        body.addView(label(a?.label ?: "Alarm", 22f))
        body.addView(label(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), 42f))
        progress = label("↻ Snooze   ·   ↺ Dismiss", 14f); body.addView(progress)
        body.addView(Button(this).apply { text="Snooze ${a?.snoozeMinutes ?: 10} min"; setOnClickListener { act(true) } })
        body.addView(Button(this).apply { text="Dismiss"; setOnClickListener { act(false) } })
        setContentView(body); body.requestFocus()
    }
    private fun readIntent() { id=intent.getStringExtra("id") ?: ""; at=intent.getLongExtra("at", 0) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    private fun act(snooze: Boolean) { Scheduler.act(this, id, at, snooze); finish() }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val action = gesture.turn(event.getAxisValue(MotionEvent.AXIS_SCROLL), SystemClock.elapsedRealtime())
            progress.text="${if(gesture.isSnoozing) "Snooze" else "Dismiss"} ${gesture.progress()}%"
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            when(action) {
                RotaryGesture.Action.SNOOZE -> act(true)
                RotaryGesture.Action.DISMISS -> act(false)
                else -> Unit
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun onDestroy() { unregisterReceiver(changes); super.onDestroy() }
}
