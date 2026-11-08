package dev.orbit.clock.core

import android.Manifest
import android.app.AlarmManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class FocusActivity : AppCompatActivity() {
    private val watch get() = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var colors: OrbitPalette
    private lateinit var face: FocusFace
    private lateinit var phase: TextView
    private lateinit var sessions: TextView
    private lateinit var primary: MaterialButton
    private lateinit var end: MaterialButton
    private lateinit var settings: MaterialButton
    private var watchStatus: TextView? = null
    private lateinit var syncStatus: TextView
    private lateinit var retrySync: MaterialButton
    private var foreground = false
    private var openWatch: MaterialButton? = null
    private var rotaryTotal = 0f
    private var lastTurn = 0L
    private var latched = false
    private var rotarySign = 0
    private val changes = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { update() } }
    private val tick = object : Runnable { override fun run() { update(); handler.postDelayed(this, 1000) } }
    override fun onCreate(state: Bundle?) {
        if (watch) setTheme(R.style.Theme_OrbitClock_Ringing) else DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(state); colors = OrbitPalette(this)
        val filter = IntentFilter(FocusStore.CHANGED).apply { addAction(FocusHandoff.CHANGED); addAction(FocusSync.CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, filter)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(if (watch) 25 else 24), dp(if (watch) 14 else 32), dp(if (watch) 25 else 24), dp(28))
            setBackgroundColor(colors.surface)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }; setContentView(scroll)
        fun text(size: Float) = TextView(this).apply { textSize = size; setTextColor(colors.onSurface); gravity = Gravity.CENTER }
        if (!watch) body.addView(text(30f).apply { text = "Focus mode" })
        phase = text(16f); body.addView(phase)
        face = FocusFace(this, colors).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
        body.addView(face, LinearLayout.LayoutParams(-1, dp(if (watch) 80 else 240)))
        sessions = text(13f); if (!watch) body.addView(sessions)
        fun button(label: String, action: () -> Unit) = MaterialButton(this).apply {
            text = label; isAllCaps = false; minHeight = dp(48); setOnClickListener { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); action() }
            body.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        if (!watch) {
            watchStatus = text(13f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
            body.addView(watchStatus)
        }
        primary = button("Start focus") { control() }
        if (!watch) openWatch = button("Open on watch") { FocusStore.push(this); FocusHandoff.open(this); update() }
        if (watch) body.addView(sessions)
        syncStatus = text(if (watch) 12f else 13f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        body.addView(syncStatus)
        retrySync = button("Sync again") { Sync.refresh(this); update() }
        end = button("End session") {
            MaterialAlertDialogBuilder(this).setTitle("End this focus session?").setMessage("Reset the current cycle and session count?")
                .setNegativeButton("Keep session", null).setPositiveButton("End") { _, _ -> FocusStore.act(this, "end") }.show()
        }
        settings = button("Durations") { durations() }
        button("Back to timers") {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("orbit_tab", "Timer"))
            }
            finish()
        }
        if (watch) body.addView(text(12f).apply { text = "Clockwise: start / resume\nAnticlockwise: pause" })
        Sync.refresh(this)
    }
    override fun onResume() {
        super.onResume(); foreground = true; setTurnScreenOn(watch && FocusHandoff.isOpening(this, intent.getStringExtra(FocusHandoff.EXTRA_NONCE)))
        FocusStore.schedule(this); handler.removeCallbacks(tick); handler.post(tick)
        window.decorView.post { FocusHandoff.visible(this) }
    }
    override fun onNewIntent(value: Intent) {
        super.onNewIntent(value); setIntent(value)
        setTurnScreenOn(watch && FocusHandoff.isOpening(this, value.getStringExtra(FocusHandoff.EXTRA_NONCE)))
        update(); window.decorView.post { FocusHandoff.visible(this) }
    }
    override fun onPause() {
        foreground = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTurnScreenOn(false); handler.removeCallbacks(tick); resetRotary(); super.onPause()
    }
    override fun onDestroy() { unregisterReceiver(changes); handler.removeCallbacks(clearCue); super.onDestroy() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) resetRotary() else if (::face.isInitialized) window.decorView.post { FocusHandoff.visible(this) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun update() {
        val saved = FocusStore.read(this)
        val now = FocusStore.now(this, saved)
        val left = saved.left(now)
        if (watch && foreground && saved.status == "Running" && left > 0)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (saved.status == "Running" && left == 0L && saved.owner == FocusStore.device(this)) { FocusStore.finish(this, saved.revision); return }
        val s = saved.complete(now)
        phase.text = "${s.phase} · ${s.status}"
        sessions.text = "${s.completed} focus sessions · ${s.completed % 4}/4 this cycle"
        val seconds = (left + 999) / 1000
        face.value = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
        face.fraction = (left.toFloat() / s.duration()).coerceIn(0f, 1f); face.contentDescription = "${s.phase}, ${face.value} remaining, ${s.status}"; face.invalidate()
        primary.text = when(s.status) { "Running" -> "Pause"; "Paused" -> "Resume"; "Complete" -> if (s.phase == "Focus") "Start break" else "Start focus"; else -> "Start focus" }
        settings.visibility = if (s.status == "Ready") View.VISIBLE else View.GONE
        end.visibility = if (s.status == "Ready") View.GONE else View.VISIBLE
        syncStatus.text = FocusSync.statusText(this)
        syncStatus.visibility = if (syncStatus.text.isNotEmpty()) View.VISIBLE else View.GONE
        retrySync.visibility = if (FocusSync.statusCode(this) in listOf("disconnected", "unconfirmed", "unavailable", "conflict")) View.VISIBLE else View.GONE
        watchStatus?.let {
            it.text = FocusHandoff.statusText(this)
            it.visibility = if (s.id.isNotEmpty() && it.text.isNotEmpty()) View.VISIBLE else View.GONE
        }
        openWatch?.apply {
            visibility = if (s.id.isNotEmpty() && s.status != "Ready") View.VISIBLE else View.GONE
            isEnabled = !FocusHandoff.isPending()
        }
    }
    private fun control(action: String? = null) {
        val saved = FocusStore.read(this)
        val s = saved.complete(FocusStore.now(this, saved))
        val command = action ?: if (s.status == "Running") "pause" else "start"
        if (command == "start" && s.status != "Running") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                MaterialAlertDialogBuilder(this).setTitle("Focus notifications")
                    .setMessage("Allow notifications to hear or feel completion alerts while Orbit is in the background.")
                    .setNegativeButton("Start without alerts") { _, _ -> FocusStore.act(this, command) }
                    .setPositiveButton("Allow") { _, _ -> requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 90) }.show(); return
            }
            if (!Scheduler.allowed(this)) {
                MaterialAlertDialogBuilder(this).setTitle("Allow precise focus alerts?")
                    .setMessage("Without precise alarm access, Android can delay the completion alert while idle.")
                    .setNegativeButton("Start anyway") { _, _ -> FocusStore.act(this, command) }
                    .setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName"))) }.show(); return
            }
        }
        FocusStore.act(this, command)
        update()
    }
    private fun durations() {
        val s = FocusStore.read(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        fun field(label: String, value: Int): EditText {
            body.addView(TextView(this).apply { text = "$label · minutes" })
            return EditText(this).apply { setText(value.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER; contentDescription = "$label minutes"; body.addView(this) }
        }
        val focus = field("Focus", s.focus); val short = field("Short break", s.shortBreak); val long = field("Long break", s.longBreak)
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Focus durations").setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val values = listOf(focus, short, long).map { it.text.toString().toIntOrNull() }
            if (values.any { it == null || it !in 1..180 }) { focus.error = "Use 1–180 minutes for each duration" }
            else { FocusStore.configure(this, values[0]!!, values[1]!!, values[2]!!); dialog.dismiss() }
        } }; dialog.show()
    }
    private fun resetRotary() { rotaryTotal = 0f; lastTurn = 0; latched = false; rotarySign = 0 }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!watch || event.action != MotionEvent.ACTION_SCROLL || !event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) return super.dispatchGenericMotionEvent(event)
        if (!hasWindowFocus()) return true
        val now = SystemClock.uptimeMillis(); val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)
        if (!delta.isFinite() || delta == 0f) return true
        if (now - lastTurn > 600) resetRotary()
        lastTurn = now
        if (latched) return true
        // Same raw encoder polarity as Orbit's current clockwise Snooze mapping.
        val sign = if (delta > 0) 1 else -1
        if (sign != rotarySign) { rotaryTotal = 0f; rotarySign = sign }
        rotaryTotal += delta
        face.rotary = (rotaryTotal / 3f).coerceIn(-1f, 1f); face.invalidate()
        handler.removeCallbacks(clearCue); handler.postDelayed(clearCue, 600)
        if (kotlin.math.abs(rotaryTotal) >= 3f) {
            latched = true; face.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            control(if (rotaryTotal < 0) "start" else "pause")
        }
        return true
    }
    private val clearCue = Runnable { if (::face.isInitialized) { face.rotary = 0f; face.invalidate() } }
}
private class FocusFace(c: Context, private val colors: OrbitPalette) : View(c) {
    var value = "25:00"; var fraction = 1f; var rotary = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val r = minOf(width, height) / 2f - 8 * density
        val cx = width / 2f; val cy = height / 2f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5 * density; paint.color = colors.secondaryContainer
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = colors.primary; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(cx-r, cy-r, cx+r, cy+r, -90f, 360 * fraction, false, paint)
        if (rotary != 0f) { paint.color = colors.onSurface; canvas.drawArc(cx-r, cy-r, cx+r, cy+r, -90f, rotary * 150, false, paint) }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        paint.textSize = minOf(48 * resources.displayMetrics.scaledDensity, r * 0.58f); paint.color = colors.onSurface
        canvas.drawText(value, cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
    }
}
