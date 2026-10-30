package dev.orbit.clock.core

import android.app.*
import android.content.*
import android.media.*
import android.os.*

class RingingService : Service() {
    private var tone: Ringtone? = null
    private var vibrating = false
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
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, IntentFilter(Store.CHANGED), RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, IntentFilter(Store.CHANGED))
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
            .setContentText(if (active.size > 1) "${active.size} alarms ringing • tap for controls" else "Alarm • tap for snooze or dismiss")
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true).setContentIntent(screen)
        if (fullScreen) notification.setFullScreenIntent(screen, true)
        startForeground(1, notification.build())
    }
    private fun updateOutputs() {
        val alarms = active.keys.mapNotNull { Store(this).get(it) }
        if (alarms.any { it.sound }) {
            if (tone?.isPlaying != true) {
                tone?.stop()
                tone = null
                // Some devices have no default alarm ringtone. Try other defaults safely.
                for (type in listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_RINGTONE)) {
                    val candidate = runCatching {
                        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, type)
                            ?: RingtoneManager.getDefaultUri(type)
                        RingtoneManager.getRingtone(this, uri)?.also {
                            it.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
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
            getSystemService(Vibrator::class.java).cancel(); vibrating = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); unregisterReceiver(changes)
        tone?.stop(); getSystemService(Vibrator::class.java).cancel()
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
