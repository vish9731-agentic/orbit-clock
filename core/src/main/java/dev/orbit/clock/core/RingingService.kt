package dev.orbit.clock.core

import android.app.*
import android.content.*
import android.media.*
import android.os.*

class RingingService : Service() {
    private var tone: Ringtone? = null
    private var vibrating = false
    private var alarmFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var retryFocusOnNewAlarm = false
    private var focusGeneration = 0
    private val alarmAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var lock: PowerManager.WakeLock? = null
    private val active = linkedMapOf<String, Long>()
    private val timeouts = mutableMapOf<String, Runnable>()
    private val handler = Handler(Looper.getMainLooper())
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            active.toMap().forEach { (id, at) ->
                val a = Store(c).get(id)
                if (a == null || !Scheduler.isActive(c, a, at)) finishOccurrence(id)
            }
            if (active.isNotEmpty()) { updateOutputs(); updateNotification(false) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, IntentFilter(Store.CHANGED).apply { addAction(RingControls.CHANGED) }, RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, IntentFilter(Store.CHANGED).apply { addAction(RingControls.CHANGED) })
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("ringing", "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Time-sensitive alarm controls"; setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id")
        val at = intent?.getLongExtra("at", 0) ?: 0
        val a = id?.let { Store(this).get(it) }
        if (id == null || a == null || !Scheduler.isActive(this, a, at)) {
            if (active.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        active[id] = at
        updateNotification(true)
        acquireAudioFocus()
        val wake = lock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "orbit:alarm").also { lock = it }
        if (wake.isHeld) wake.release()
        wake.acquire(11*60_000L)
        updateOutputs()
        timeouts.remove(id)?.let { handler.removeCallbacks(it) }
        val timeout = Runnable { if (active[id] == at) Scheduler.act(this, id, at, false) }
        timeouts[id] = timeout
        handler.postDelayed(timeout, 10*60_000L)
        return START_NOT_STICKY
    }
    private fun updateNotification(fullScreen: Boolean) {
        val entry = active.entries.lastOrNull() ?: return
        val a = Store(this).get(entry.key) ?: return
        val screen = PendingIntent.getActivity(this, a.id.hashCode(), Intent(this, RingActivity::class.java)
            .setData(android.net.Uri.parse("orbit://ring/${a.id}")).putExtra("id", a.id).putExtra("at", entry.value)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "ringing")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(a.label)
            .setContentText(if (active.size > 1) "${active.size} alarms ringing" else "Alarm")
            .setOnlyAlertOnce(!fullScreen)
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true).setContentIntent(screen)
        val minutes = RingControls.minutes(this, a, entry.value)
        val snooze = RingControls.pending(this, a, entry.value, "snooze")
        val stop = RingControls.pending(this, a, entry.value, "stop")
        if (packageManager.hasSystemFeature("android.hardware.type.watch")) {
            notification.addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_alarm), "Snooze", snooze).build())
                .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_close), "Stop", stop).build())
        } else {
            fun controls(compact: Boolean) = android.widget.RemoteViews(packageName,
                if (compact) R.layout.ring_notification_compact else R.layout.ring_notification).apply {
                val name = a.label.trim().ifBlank { "Alarm" }
                setTextViewText(R.id.ring_notification_label, name)
                setOnClickPendingIntent(R.id.ring_notification_snooze, snooze)
                setOnClickPendingIntent(R.id.ring_notification_stop, stop)
                setContentDescription(R.id.ring_notification_snooze, "Snooze alarm for $minutes minutes")
                setContentDescription(R.id.ring_notification_stop, "Stop alarm")
                if (compact) setTextViewText(R.id.ring_notification_snooze, "Snooze · $minutes min")
                else {
                    setTextViewText(R.id.ring_notification_minutes, "$minutes min")
                    setContentDescription(R.id.ring_notification_minutes, "Snooze duration: $minutes minutes")
                    setOnClickPendingIntent(R.id.ring_notification_less, RingControls.pending(this@RingingService, a, entry.value, "less"))
                    setOnClickPendingIntent(R.id.ring_notification_more, RingControls.pending(this@RingingService, a, entry.value, "more"))
                    setBoolean(R.id.ring_notification_less, "setEnabled", minutes > SnoozeDuration.MIN)
                    setBoolean(R.id.ring_notification_more, "setEnabled", minutes < SnoozeDuration.MAX)
                    setFloat(R.id.ring_notification_less, "setAlpha", if (minutes > SnoozeDuration.MIN) 1f else .38f)
                    setFloat(R.id.ring_notification_more, "setAlpha", if (minutes < SnoozeDuration.MAX) 1f else .38f)
                }
            }
            notification.setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(controls(true)).setCustomBigContentView(controls(false)).setCustomHeadsUpContentView(controls(false))
        }
        if (fullScreen) notification.setFullScreenIntent(screen, true)
        startForeground(1, notification.build())
    }
    private fun acquireAudioFocus() {
        if (alarmFocusRequest != null && !retryFocusOnNewAlarm) return // Shared across overlapping alarms.
        if (alarmFocusRequest != null) releaseAudioFocus() // A new alarm may request after permanent loss.
        val manager = getSystemService(AudioManager::class.java)
        // PAUSE is idempotent; never send PLAY_PAUSE, which could start stopped media.
        runCatching {
            val down = SystemClock.uptimeMillis()
            for (action in intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
                manager.dispatchMediaKeyEvent(android.view.KeyEvent(down, SystemClock.uptimeMillis(), action,
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            }
        }.onFailure { android.util.Log.w("OrbitAudioFocus", "Media pause dispatch unavailable", it) }
        val generation = ++focusGeneration
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(alarmAudioAttributes).setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (generation == focusGeneration && active.isNotEmpty()) {
                    hasAudioFocus = change == AudioManager.AUDIOFOCUS_GAIN
                    retryFocusOnNewAlarm = change == AudioManager.AUDIOFOCUS_LOSS
                    updateOutputs() // Respect losses; a delayed gain can start the still-active alarm.
                }
            }, handler).build()
        alarmFocusRequest = request
        val result = runCatching { manager.requestAudioFocus(request) }
            .onFailure { android.util.Log.w("OrbitAudioFocus", "Alarm audio focus unavailable", it) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        retryFocusOnNewAlarm = result == AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }
    private fun releaseAudioFocus() {
        ++focusGeneration // Ignore callbacks queued for an occurrence that has already ended.
        hasAudioFocus = false
        retryFocusOnNewAlarm = false
        val request = alarmFocusRequest ?: return
        alarmFocusRequest = null
        runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request) }
            .onFailure { android.util.Log.w("OrbitAudioFocus", "Audio focus cleanup unavailable", it) }
    }
    private fun updateOutputs() {
        val alarms = active.keys.mapNotNull { Store(this).get(it) }
        if (hasAudioFocus && alarms.any { it.sound }) {
            if (tone?.isPlaying != true) {
                tone?.stop()
                tone = null
                // Some devices have no default alarm ringtone. Try other defaults safely.
                for (type in listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_RINGTONE)) {
                    val candidate = runCatching {
                        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, type)
                            ?: RingtoneManager.getDefaultUri(type)
                        RingtoneManager.getRingtone(this, uri)?.also {
                            it.audioAttributes = alarmAudioAttributes
                            it.isLooping = true
                            it.play()
                        }
                    }.getOrNull()
                    if (candidate != null && candidate.isPlaying) { tone = candidate; break }
                    candidate?.stop()
                }
            }
        } else { tone?.stop(); tone = null }
        val shouldVibrate = alarms.any { it.vibrate }
        if (shouldVibrate != vibrating) {
            val vibrator = getSystemService(Vibrator::class.java)
            if (shouldVibrate) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 400), 0))
            else vibrator.cancel()
            vibrating = shouldVibrate
        }
    }
    private fun finishOccurrence(id: String) {
        active.remove(id)
        timeouts.remove(id)?.let { handler.removeCallbacks(it) }
        if (active.isEmpty()) {
            tone?.stop(); tone = null
            releaseAudioFocus()
            getSystemService(Vibrator::class.java).cancel(); vibrating = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); unregisterReceiver(changes)
        tone?.stop(); tone = null; releaseAudioFocus(); getSystemService(Vibrator::class.java).cancel()
        if (lock?.isHeld == true) lock?.release()
        active.toMap().forEach { (id, at) -> Scheduler.act(this, id, at, false) }
        active.clear(); timeouts.clear(); super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        fun reconcile(c: Context, a: Alarm) {
            c.sendBroadcast(Intent(Store.CHANGED).setPackage(c.packageName))
        }
    }
}
