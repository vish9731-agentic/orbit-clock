package dev.orbit.clock.core

import android.app.*
import android.content.*
import android.os.Build
import java.time.Instant

object Scheduler {
    private fun state(c: Context) = c.getSharedPreferences("scheduled_occurrences", Context.MODE_PRIVATE)
    private fun identity(a: Alarm) = listOf(a.hour, a.minute, a.days.sorted().joinToString(","),
        a.enabled, a.deleted, a.snoozeUntil, a.skipThrough).joinToString("|")
    private fun pending(c: Context, id: String, at: Long = 0): PendingIntent = PendingIntent.getBroadcast(c, 0,
        Intent(c, AlarmReceiver::class.java).setData(android.net.Uri.parse("orbit://alarm/$id"))
            .putExtra("id", id).putExtra("at", at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun allowed(c: Context) = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun schedule(c: Context, a: Alarm) {
        val prefs = state(c)
        val fingerprint = identity(a)
        if (prefs.getString("${a.id}.identity", null) != fingerprint) {
            prefs.edit().remove("${a.id}.active").putString("${a.id}.identity", fingerprint).commit()
        }
        val active = prefs.getLong("${a.id}.active", 0)
        val next = if (active > 0) {
            // Reopening the app while an alarm rings must not turn a one-shot into tomorrow's alarm.
            if (a.days.isEmpty()) null else a.next(Instant.ofEpochMilli(maxOf(System.currentTimeMillis(), active)))
        } else a.next()
        install(c, a, next)
    }
    private fun install(c: Context, a: Alarm, next: Instant?) {
        val manager = c.getSystemService(AlarmManager::class.java)
        manager.cancel(pending(c, a.id))
        state(c).edit().remove("${a.id}.scheduled").commit()
        if (next == null || !allowed(c)) return
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName) ?: return
        val show = PendingIntent.getActivity(c, a.id.hashCode(), launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val at = next.toEpochMilli()
        state(c).edit().putLong("${a.id}.scheduled", at).commit()
        try { manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(c, a.id, at)) }
        catch (_: SecurityException) { state(c).edit().remove("${a.id}.scheduled").commit() }
    }
    fun restore(c: Context) { Store(c).all().forEach { schedule(c, it) } }
    fun isActive(c: Context, a: Alarm, occurrence: Long): Boolean =
        a.enabled && !a.deleted && occurrence > 0 && a.skipThrough < occurrence &&
            state(c).getLong("${a.id}.active", 0) == occurrence &&
            state(c).getString("${a.id}.identity", null) == identity(a)
    fun accept(c: Context, a: Alarm, occurrence: Long): Boolean {
        val prefs = state(c)
        if (!a.enabled || a.deleted || occurrence <= 0 || a.skipThrough >= occurrence ||
            prefs.getLong("${a.id}.scheduled", 0) != occurrence ||
            prefs.getString("${a.id}.identity", null) != identity(a)) return false
        prefs.edit().putLong("${a.id}.active", occurrence).remove("${a.id}.scheduled").commit()
        // Schedule the next repeat before launching the service. Process death while ringing
        // must not remove all future occurrences. Calculate strictly after the delivered time.
        val next = if (a.days.isEmpty()) null else
            a.next(Instant.ofEpochMilli(maxOf(System.currentTimeMillis(), occurrence)))
        install(c, a, next)
        return true
    }
    fun act(c: Context, id: String, occurrence: Long, snooze: Boolean, snoozeMinutesOverride: Int? = null) {
        val store = Store(c)
        val a = store.get(id) ?: return
        if (!isActive(c, a, occurrence)) return
        val changed = if (snooze) {
            val minutes = snoozeMinutesOverride?.let { SnoozeDuration.clamp(it) } ?: a.snoozeMinutes
            val now = System.currentTimeMillis()
            val next = if (a.timer) now + minutes * 60_000L else SnoozeDuration.nextAlarm(occurrence, now, minutes)
            a.copy(snoozeUntil = next, skipThrough = occurrence)
        } else a.copy(snoozeUntil=0, skipThrough=occurrence, enabled=a.days.isNotEmpty())
        store.save(changed)
        RingingService.reconcile(c, changed)
    }
}
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val a = Store(context).get(id) ?: return
        val at = intent.getLongExtra("at", 0)
        if (!Scheduler.accept(context, a, at)) return
        context.startForegroundService(Intent(context, RingingService::class.java).putExtra("id", id).putExtra("at", at))
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Scheduler.restore(context) }
}
