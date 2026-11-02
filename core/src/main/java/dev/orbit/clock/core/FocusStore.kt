package dev.orbit.clock.core

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.google.android.gms.wearable.*
import org.json.JSONObject
import java.util.UUID

/** One durable register; deterministic revision/writer ordering resolves concurrent offline edits. */
object FocusStore {
    const val CHANGED = "dev.orbit.clock.FOCUS_CHANGED"
    private const val PATH = "/orbit/focus/session"
    private fun prefs(c: Context) = c.getSharedPreferences("focus", 0)
    @Synchronized fun device(c: Context): String {
        val p = prefs(c)
        return p.getString("device", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device", it).commit() }
    }
    fun encode(s: FocusSession): String = JSONObject().apply {
        put("id", s.id); put("phase", s.phase); put("status", s.status); put("remaining", s.remaining)
        put("deadline", s.deadline); put("completed", s.completed); put("focus", s.focus)
        put("shortBreak", s.shortBreak); put("longBreak", s.longBreak); put("revision", s.revision)
        put("writer", s.writer); put("owner", s.owner)
    }.toString()
    fun decode(raw: String): FocusSession {
        val j = JSONObject(raw)
        return FocusSession(j.getString("id"), j.getString("phase"), j.getString("status"), j.getLong("remaining"),
            j.getLong("deadline"), j.getInt("completed"), j.getInt("focus"), j.getInt("shortBreak"),
            j.getInt("longBreak"), j.getLong("revision"), j.getString("writer"), j.getString("owner")).also {
            require(it.phase in listOf("Focus", "Break", "Long break") && it.status in listOf("Ready", "Running", "Paused", "Complete"))
            require(it.focus in 1..180 && it.shortBreak in 1..180 && it.longBreak in 1..180 && it.completed >= 0 && it.remaining >= 0)
        }
    }
    fun read(c: Context): FocusSession = runCatching { decode(prefs(c).getString("session", "")!!) }.getOrDefault(FocusSession())
    @Synchronized fun act(c: Context, action: String) {
        val old = read(c); val now = now(c, old); var next = old.act(action, now)
        if (next == old) return
        if (action == "start") next = next.copy(id = old.id.ifEmpty { UUID.randomUUID().toString() }, owner = device(c))
        write(c, next.copy(revision = maxOf(now, old.revision + 1), writer = device(c)), true)
    }
    @Synchronized fun configure(c: Context, focus: Int, short: Int, long: Int) {
        val old = read(c)
        if (old.status != "Ready" || listOf(focus, short, long).any { it !in 1..180 }) return
        write(c, old.copy(focus = focus, shortBreak = short, longBreak = long, remaining = focus * 60_000L,
            revision = maxOf(System.currentTimeMillis(), old.revision + 1), writer = device(c)), true)
    }
    private fun write(c: Context, s: FocusSession, sync: Boolean) {
        prefs(c).edit().putString("session", encode(s)).commit()
        schedule(c, s)
        c.sendBroadcast(Intent(CHANGED).setPackage(c.packageName))
        if (sync) push(c, s)
    }
    fun push(c: Context, s: FocusSession = read(c)) {
        if (s.revision == 0L) return
        Wearable.getDataClient(c).putDataItem(PutDataMapRequest.create(PATH).apply { dataMap.putString("record", encode(s)) }.asPutDataRequest().setUrgent())
    }
    @Synchronized fun accept(c: Context, item: DataItem): Boolean {
        if (item.uri.path != PATH) return false
        runCatching {
            merge(c, DataMapItem.fromDataItem(item).dataMap.getString("record") ?: return true)
        }
        return true
    }
    @Synchronized fun merge(c: Context, raw: String) {
        val incoming = decode(raw)
        if (incoming.newerThan(read(c))) write(c, incoming, false)
    }
    private fun pending(c: Context, s: FocusSession) = PendingIntent.getBroadcast(c, 9201,
        Intent(c, FocusReceiver::class.java).setAction("complete").setData(Uri.parse("orbit://focus/deadline"))
            .putExtra("revision", s.revision), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun boot(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun elapsedDeadline(c: Context, s: FocusSession): Long {
        val p = prefs(c)
        if (p.getLong("anchorDeadline", -1) != s.deadline || p.getInt("anchorBoot", -2) != boot(c)) {
            p.edit().putLong("anchorDeadline", s.deadline).putInt("anchorBoot", boot(c))
                .putLong("elapsedDeadline", SystemClock.elapsedRealtime() + (s.deadline - System.currentTimeMillis()).coerceAtLeast(0)).commit()
        }
        return p.getLong("elapsedDeadline", SystemClock.elapsedRealtime())
    }
    fun now(c: Context, s: FocusSession): Long = if (s.status == "Running" && s.owner == device(c))
        s.deadline - (elapsedDeadline(c, s) - SystemClock.elapsedRealtime()) else System.currentTimeMillis()
    fun schedule(c: Context, s: FocusSession = read(c)) {
        val manager = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c, s); manager.cancel(pi)
        if (s.status != "Complete") c.getSystemService(NotificationManager::class.java).cancel(9201)
        if (s.status == "Running" && s.owner == device(c)) {
            val at = maxOf(SystemClock.elapsedRealtime() + 50, elapsedDeadline(c, s))
            try {
                if (Scheduler.allowed(c)) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
                else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi) }
        }
        if (s.status in listOf("Running", "Paused")) notify(c, s, false)
    }
    @Synchronized fun finish(c: Context, revision: Long) {
        val s = read(c); val now = now(c, s)
        if (s.revision != revision || s.owner != device(c) || s.status != "Running") return
        val next = s.complete(now)
        if (next == s) { schedule(c, s); return }
        val done = next.copy(revision = maxOf(now, s.revision + 1), writer = device(c))
        write(c, done, true); notify(c, done, true)
    }
    private fun notify(c: Context, s: FocusSession, alert: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(NotificationManager::class.java)
        val channel = if (alert) "focus-completion" else "focus-progress"
        nm.createNotificationChannel(NotificationChannel(channel, if (alert) "Focus transitions" else "Focus session", if (alert) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW).apply { enableVibration(alert) })
        val open = PendingIntent.getActivity(c, 9201, Intent(c, FocusActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = if (s.status == "Running") "pause" else "start"
        val label = if (s.status == "Running") "Pause" else if (s.status == "Paused") "Resume" else if (s.phase == "Focus") "Start break" else "Start focus"
        val control = PendingIntent.getBroadcast(c, 9202, Intent(c, FocusReceiver::class.java).setAction(action).putExtra("revision", s.revision), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, channel).setSmallIcon(R.drawable.ic_timer).setContentTitle(if (alert) "${s.phase} complete" else "${s.phase} · ${s.status}")
            .setContentText("${s.completed} focus sessions completed").setContentIntent(open).setOnlyAlertOnce(!alert)
            .setOngoing(s.status == "Running").setAutoCancel(alert)
            .addAction(Notification.Action.Builder(null, label, control).build())
        if (s.status == "Running") n.setWhen(s.deadline).setUsesChronometer(true).setChronometerCountDown(true)
        nm.notify(9201, n.build())
    }
}
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val s = FocusStore.read(c)
        if (i.getLongExtra("revision", -1) != s.revision) return
        if (i.action == "complete") FocusStore.finish(c, s.revision) else FocusStore.act(c, i.action ?: "")
    }
}
