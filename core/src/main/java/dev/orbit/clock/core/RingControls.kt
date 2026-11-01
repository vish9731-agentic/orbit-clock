package dev.orbit.clock.core

import android.app.PendingIntent
import android.content.*
import android.net.Uri

/** Per-occurrence selection shared by the banner and full ringing UI, never the alarm default. */
object RingControls {
    const val CHANGED = "dev.orbit.clock.RING_SELECTION"
    private fun prefs(c: Context) = c.getSharedPreferences("ring_selection", Context.MODE_PRIVATE)
    fun minutes(c: Context, a: Alarm, at: Long, fallback: Int = a.snoozeMinutes): Int {
        val p = prefs(c)
        return SnoozeDuration.clamp(if (p.getLong(a.id + ".at", 0) == at) p.getInt(a.id + ".minutes", fallback) else fallback)
    }
    fun select(c: Context, id: String, at: Long, minutes: Int) {
        prefs(c).edit().putLong(id + ".at", at).putInt(id + ".minutes", SnoozeDuration.clamp(minutes)).apply()
        c.sendBroadcast(Intent(CHANGED).setPackage(c.packageName))
    }
    fun pending(c: Context, a: Alarm, at: Long, action: String): PendingIntent = PendingIntent.getBroadcast(c, 0,
        Intent(c, RingActionReceiver::class.java).setAction(action)
            .setData(Uri.Builder().scheme("orbit").authority("ring-action").appendPath(a.id).appendPath(at.toString()).appendPath(action).build())
            .putExtra("id", a.id).putExtra("at", at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class RingActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val at = intent.getLongExtra("at", 0)
        val a = Store(c).get(id) ?: return
        if (!Scheduler.isActive(c, a, at)) return // Old banners cannot operate on a later occurrence.
        val minutes = RingControls.minutes(c, a, at)
        when (intent.action) {
            "snooze" -> Scheduler.act(c, id, at, true, snoozeMinutesOverride = minutes)
            "stop" -> Scheduler.act(c, id, at, false)
            "less" -> RingControls.select(c, id, at, SnoozeDuration.decrease(minutes))
            "more" -> RingControls.select(c, id, at, SnoozeDuration.increase(minutes))
        }
    }
}
