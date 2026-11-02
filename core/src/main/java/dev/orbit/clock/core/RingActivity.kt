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
    private var watchActions: WatchArcControl? = null
    private var completingAction = false
    private var phoneTime: TextView? = null
    private var atmosphere: RingAtmosphereDrawable? = null
    private var clockRunning = false
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            if (!clockRunning) return
            refreshPhoneClock()
            clockHandler.postDelayed(this, 60_000L - Math.floorMod(System.currentTimeMillis(), 60_000L))
        }
    }
    private fun refreshPhoneClock() {
        val now = LocalTime.now()
        atmosphere?.updateTime(now.toSecondOfDay() / 60)
        phoneTime?.let { clock ->
            val value = now.format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm"))
            clock.text = value
            clock.contentDescription = "Alarm time: $value"
        }
    }
    private val bezelHandler = Handler(Looper.getMainLooper())
    // Match RotaryGesture's >1500ms pause boundary without changing its thresholds.
    private val resetBezelFeedback = Runnable { watchActions?.releaseBezel() }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val a = Store(c).get(id)
            if (completingAction) return
            if (a == null || !Scheduler.isActive(c, a, at)) finish()
            else if (::duration.isInitialized) {
                selectedMinutes = RingControls.minutes(c, a, at, selectedMinutes); updateDuration()
                if (clockRunning && (i.action == Intent.ACTION_TIME_CHANGED || i.action == Intent.ACTION_TIMEZONE_CHANGED)) {
                    clockHandler.removeCallbacks(clockTick); clockTick.run()
                } else refreshPhoneClock()
            }
        }
    }
    override fun onCreate(state: Bundle?) {
        watch = packageManager.hasSystemFeature("android.hardware.type.watch")
        setTheme(R.style.Theme_OrbitClock_Ringing)
        super.onCreate(state)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        id = intent.getStringExtra("id") ?: ""; at = intent.getLongExtra("at", 0)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, IntentFilter(Store.CHANGED).apply { addAction(RingControls.CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }, RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, IntentFilter(Store.CHANGED).apply { addAction(RingControls.CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) })
        val alarm = Store(this).get(id)
        if (alarm == null || !Scheduler.isActive(this, alarm, at)) { finish(); return }
        val sameOccurrence = state != null && state.getString("ring.id") == id && state.getLong("ring.at") == at
        selectedMinutes = SnoozeDuration.clamp(if (sameOccurrence) state!!.getInt("ring.minutes", alarm.snoozeMinutes) else alarm.snoozeMinutes)
        selectedMinutes = RingControls.minutes(this, alarm, at, selectedMinutes)
        val colors = OrbitPalette(this)
        val background = colors.surface
        val foreground = if (watch) colors.onSurface else 0xffe5ebe8.toInt()
        val accent = if (watch) colors.primary else 0xff74e8d7.toInt()
        val name = alarm.label.trim().ifBlank { "Alarm" }
        fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
        fun label(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 400, false); gravity = Gravity.CENTER; setTextColor(foreground); includeFontPadding = false
        }
        val watchWidth = resources.configuration.screenWidthDp
        val watchHeight = resources.configuration.screenHeightDp
        val watchRoot = if (watch) FrameLayout(this).apply { setBackgroundColor(0xff090f0d.toInt()); isFocusableInTouchMode = true } else null
        val sidePadding = if (watch) ((watchWidth - 180) / 2).coerceIn(0, 20) else 16
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(sidePadding), dp(8), dp(sidePadding), dp(8))
            isFocusableInTouchMode = true
        }
        val now = LocalTime.now()
        val time = now.format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm"))
        if (watch) {
            val timeWidth = (watchWidth * .76f).toInt()
            val timeHeight = (watchHeight * .28f).toInt()
            val timeTop = (watchHeight * .045f).toInt()
            watchRoot!!.addView(label(time, 48f).apply {
                typeface = Typeface.create(typeface, 350, false)
                maxLines = 1; fontFeatureSettings = "tnum"
                contentDescription = "Alarm time: $time"; phoneTime = this
                setAutoSizeTextTypeUniformWithConfiguration(30, 48, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }, FrameLayout.LayoutParams(dp(timeWidth), dp(timeHeight), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(timeTop) })
            watchRoot.addView(label(name, 18f).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                contentDescription = "Alarm label: $name"
                setTextColor(0xffa5bab3.toInt())
                setAutoSizeTextTypeUniformWithConfiguration(12, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }, FrameLayout.LayoutParams(dp((watchWidth * .66f).toInt()), dp((watchHeight * .125f).toInt()), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp((watchHeight * .325f).toInt()) })
        } else {
            body.addView(Space(this), LinearLayout.LayoutParams(1, 0, 3.3f))
            body.addView(label(time, 144f).apply {
                typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 350, false); letterSpacing = -.025f
                fontFeatureSettings = "tnum"
                phoneTime = this
                contentDescription = "Alarm time: $time"; maxLines = 1
                setAutoSizeTextTypeUniformWithConfiguration(56, 144, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }, LinearLayout.LayoutParams(-1, dp(156)))
            body.addView(label(name, 24f).apply {
                setTextColor(0xffb9c2bd.toInt())
                contentDescription = "Alarm label: $name"; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(40) })
        }
        fun control(value: String, callback: () -> Unit) = MaterialButton(this).apply {
            text = value; isAllCaps = false; textSize = 22f; typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 400, false); minWidth = 0; minimumWidth = 0
            setPadding(0, 0, 0, 0); cornerRadius = dp(28); insetTop = 0; insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(if (watch) 0xff18332e.toInt() else android.graphics.Color.TRANSPARENT)
            setTextColor(foreground); setOnClickListener { OrbitMotion.tick(this); callback() }
            OrbitMotion.bind(this)
        }
        val stepper = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        decrease = control("−") { selectedMinutes = SnoozeDuration.decrease(selectedMinutes); RingControls.select(this, id, at, selectedMinutes); updateDuration() }.apply { contentDescription = "Decrease snooze by 5 minutes" }
        increase = control("+") { selectedMinutes = SnoozeDuration.increase(selectedMinutes); RingControls.select(this, id, at, selectedMinutes); updateDuration() }.apply { contentDescription = "Increase snooze by 5 minutes" }
        if (watch) {
            decrease.textSize = 28f; increase.textSize = 28f
            decrease.cornerRadius = dp(28); increase.cornerRadius = dp(28)
        }
        duration = label("", if (watch) 12f else 16f).apply {
            typeface = Typeface.create(typeface, 500, false); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            if (watch) { maxLines = 1; setAutoSizeTextTypeUniformWithConfiguration(10, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP) }
        }
        val size = dp(if (watch) 56 else 48)
        stepper.addView(decrease, LinearLayout.LayoutParams(size, size))
        stepper.addView(duration, LinearLayout.LayoutParams(dp(if (watch) 38 else 96), size))
        stepper.addView(increase, LinearLayout.LayoutParams(size, size))
        if (watch) watchRoot!!.addView(stepper, FrameLayout.LayoutParams(dp(150), dp(56), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp((watchHeight * .45f).toInt()) })
        else body.addView(stepper, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        updateDuration()
        if (watch) {
            val actions = WatchArcControl(this, { act(true) }, { act(false) })
            watchActions = actions
            watchRoot!!.addView(actions, 0, FrameLayout.LayoutParams(-1, -1))
        } else {
            body.addView(AlarmSlideControl(this,
                { VisualHints.learn(this, "ring_controls"); act(true) },
                { VisualHints.learn(this, "ring_controls"); act(false) }), LinearLayout.LayoutParams(-1, dp(80)))
            body.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1.15f))
            progress = label("", 12f).apply { visibility = View.GONE }
            body.addView(progress, LinearLayout.LayoutParams(-1, -2))
        }
        if (!watch) {
            indicator = LinearProgressIndicator(this).apply { max = 100; visibility = View.GONE; trackCornerRadius = dp(3); setIndicatorColor(accent) }
            body.addView(indicator, LinearLayout.LayoutParams(-1, dp(4)))
        }
        val scroller = ScrollView(this).apply {
            isFillViewport = true
            if (watch) setBackgroundColor(0xff090f0d.toInt()) else {
                val scene = RingAtmosphereDrawable(now.toSecondOfDay() / 60)
                atmosphere = scene
                this.background = scene
            }
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
        if (watch) setContentView(watchRoot) else setContentView(scroller)
        ViewCompat.setOnApplyWindowInsetsListener(scroller) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, scroller).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
            if (!watch) {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                // Hide the status bar without triggering Android's first-use immersive tutorial.
                hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        OrbitMotion.bindTree(if (watch) watchRoot!! else body)
        if (state == null && !watch) { OrbitMotion.enter(body.getChildAt(1)); OrbitMotion.enter(body.getChildAt(2), 30) }
        ViewCompat.requestApplyInsets(if (watch) watchRoot!! else scroller)
        if (watch) watchRoot!!.requestFocus() else body.requestFocus()
    }
    private fun updateDuration() {
        OrbitMotion.text(duration, "$selectedMinutes min")
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
        if (completingAction) return
        completingAction = true
        bezelHandler.removeCallbacks(resetBezelFeedback)
        decrease.isEnabled = false; increase.isEnabled = false
        // Commit now. Alarm silence and persistence never depend on animation.
        Scheduler.act(this, id, at, snooze, snoozeMinutesOverride = if (snooze) selectedMinutes else null)
        if (watch && hasWindowFocus() && watchActions != null) {
            val actions = watchActions!!
            actions.complete(snooze) {
                if (!isFinishing && !isDestroyed) {
                    val point = actions.thumbCenter()
                    WatchRingTransition.openHome(this, point.first, point.second)
                }
            }
        } else finish()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Consume the whole press, including repeats. Act on release so holding a key
            // cannot spill volume-change events into the screen beneath the alarm.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && volumeKey == KeyEvent.KEYCODE_UNKNOWN) {
                volumeKey = event.keyCode
            } else if (event.action == KeyEvent.ACTION_UP && volumeKey == event.keyCode) {
                volumeKey = KeyEvent.KEYCODE_UNKNOWN
                if (!event.isCanceled) { VisualHints.learn(this, "volume_snooze"); act(true) }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) volumeKey = KeyEvent.KEYCODE_UNKNOWN
        super.onWindowFocusChanged(hasFocus)
    }
    override fun onResume() {
        super.onResume()
        // A restored task must not bring a previously snoozed occurrence back on screen.
        val alarm = Store(this).get(id)
        if (!completingAction && (alarm == null || !Scheduler.isActive(this, alarm, at))) { finish(); return }
        // Use the actual local clock after every Snooze/new occurrence or resume.
        // Neither the saved alarm hour nor its original occurrence selects the scene.
        clockRunning = true; clockHandler.removeCallbacks(clockTick); clockTick.run()
    }
    override fun onPause() {
        clockRunning = false
        clockHandler.removeCallbacks(clockTick)
        volumeKey = KeyEvent.KEYCODE_UNKNOWN
        if (completingAction && !isFinishing) finish()
        super.onPause()
    }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (watch && event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            // Alarm actions own the watch bezel, even when large text makes the
            // ScrollView scrollable. Never spend an action detent on scrolling.
            val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (!delta.isFinite() || delta == 0f) return true
            return onGenericMotionEvent(event)
        }
        return super.dispatchGenericMotionEvent(event)
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            if (watch && watchActions?.touchActive == true) return true
            val rawTurn = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            // Wear's positive raw axis is anticlockwise. Keep the existing
            // three/six-unit amounts while mapping anticlockwise to Snooze.
            val action = gesture.turn(if (watch) -rawTurn else rawTurn, SystemClock.elapsedRealtime())
            if (watch) {
                watchActions?.setBezelProgress(gesture.isSnoozing, gesture.fraction())
                bezelHandler.removeCallbacks(resetBezelFeedback)
                bezelHandler.postDelayed(resetBezelFeedback, 1501)
            } else {
                progress.text = "${if (gesture.isSnoozing) "Snooze" else "Stop"} ${gesture.progress()}%"
                progress.visibility = View.VISIBLE
                indicator.visibility = View.VISIBLE; indicator.setProgressCompat(gesture.progress(), true)
            }
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            when (action) { RotaryGesture.Action.SNOOZE -> { VisualHints.learn(this, "watch_bezel"); act(true) }; RotaryGesture.Action.DISMISS -> { VisualHints.learn(this, "watch_bezel"); act(false) }; else -> Unit }
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun onDestroy() { clockRunning = false; clockHandler.removeCallbacksAndMessages(null); bezelHandler.removeCallbacks(resetBezelFeedback); unregisterReceiver(changes); super.onDestroy() }
}
