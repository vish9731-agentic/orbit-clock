package dev.orbit.clock.phone

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import dev.orbit.clock.core.Alarm
import dev.orbit.clock.core.RingingService
import dev.orbit.clock.core.Scheduler
import dev.orbit.clock.core.Store
import java.time.*
import org.json.JSONObject

/** Both sizes use the same saved schedules; no polling service or per-minute wakeups. */
open class AlarmWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TOGGLE) {
            val alarm = intent.getStringExtra("id")?.let { Store(context).get(it) }
            if (alarm != null && !alarm.deleted && !alarm.timer) {
                val enabled = intent.getBooleanExtra("enabled", alarm.enabled)
                // Re-read before changing. Repeated/stale taps cannot overwrite alarm edits.
                if (alarm.enabled != enabled) {
                    val updated = Store(context).save(alarm.copy(enabled = enabled, snoozeUntil = 0, skipThrough = 0))
                    if (!enabled) context.getSharedPreferences("widget_feedback", Context.MODE_PRIVATE).edit()
                        .putString("previous", Store.encode(alarm).toString()).putString("id", alarm.id)
                        .putLong("revision", updated.revision).putString("writer", updated.writer).putLong("until", System.currentTimeMillis() + 10_000).commit()
                    RingingService.reconcile(context, updated)
                }
            }
        }
        if (intent.action == UNDO) {
            val feedback = context.getSharedPreferences("widget_feedback", Context.MODE_PRIVATE)
            val id = intent.getStringExtra("id")
            val current = id?.let { Store(context).get(it) }
            val previous = feedback.getString("previous", null)?.let { runCatching { Store.decode(JSONObject(it)) }.getOrNull() }
            if (current != null && previous != null && current.id == feedback.getString("id", null) &&
                previous.id == current.id && !current.deleted && !current.enabled &&
                current.revision == feedback.getLong("revision", 0) && current.writer == feedback.getString("writer", null) && System.currentTimeMillis() < feedback.getLong("until", 0)) {
                Store(context).save(current.copy(enabled = previous.enabled, snoozeUntil = previous.snoozeUntil, skipThrough = previous.skipThrough))
            }
            if (id != null && id == feedback.getString("id", null)) feedback.edit().clear().commit()
        }
        if (intent.action in setOf(TOGGLE, UNDO, REFRESH, Store.CHANGED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) refresh(context)
    }
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = refresh(c)
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, options: Bundle) = refresh(c)
    override fun onDeleted(c: Context, ids: IntArray) = refresh(c)
    override fun onDisabled(c: Context) = refresh(c)

    companion object {
        const val EDIT = "dev.orbit.clock.widget.EDIT"
        const val ADD = "dev.orbit.clock.widget.ADD"
        const val BEDTIME = "dev.orbit.clock.widget.BEDTIME"
        private const val UNDO = "dev.orbit.clock.widget.UNDO"
        private const val TOGGLE = "dev.orbit.clock.widget.TOGGLE"
        private const val REFRESH = "dev.orbit.clock.widget.REFRESH"
        private fun boundary(c: Context) = PendingIntent.getBroadcast(c, 0,
            Intent(c, AlarmWidget::class.java).setAction(REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun launch(c: Context, action: String, id: String? = null) = PendingIntent.getActivity(c, 0,
            Intent(c, MainActivity::class.java).setAction(action)
                .setData(Uri.parse("orbit://widget/${Uri.encode(action)}/${Uri.encode(id ?: "all")}"))
                .putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun toggle(c: Context, a: Alarm) = PendingIntent.getBroadcast(c, 0,
            Intent(c, AlarmWidget::class.java).setAction(TOGGLE)
                .setData(Uri.parse("orbit://widget/toggle/${Uri.encode(a.id)}/${!a.enabled}"))
                .putExtra("id", a.id).putExtra("enabled", !a.enabled),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun undo(c: Context, id: String) = PendingIntent.getBroadcast(c, 0,
            Intent(c, AlarmWidget::class.java).setAction(UNDO).setData(Uri.parse("orbit://widget/undo/${Uri.encode(id)}")).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun time(c: Context, at: Long): String = DateFormat.getTimeFormat(c).format(java.util.Date(at))

        data class Snapshot(val kind: String, val alarm: Alarm?, val at: Long, val boundaryAt: Long)

        /** Injectable wall time for verification. Only installed alarms may claim to be upcoming. */
        fun snapshot(c: Context, now: Long): Snapshot {
            val all = Store(c).all().filterNot { it.deleted || it.timer }
            val state = c.getSharedPreferences("scheduled_occurrences", Context.MODE_PRIVATE)
            val ringing = all.firstOrNull { Scheduler.isActive(c, it, state.getLong("${it.id}.active", 0)) }
            val next = all.mapNotNull { a ->
                state.getLong("${a.id}.scheduled", 0).takeIf { a.enabled && Scheduler.allowed(c) && it > now }?.let { a to it }
            }.minWithOrNull(compareBy({ it.second }, { it.first.id }))
            val prefs = c.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val aw = WidgetTiming.window(prefs.getInt("alarmWindow", 15)) * 60_000L
            val bw = WidgetTiming.window(prefs.getInt("bedtimeWindow", 15)) * 60_000L
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val goal = c.getSharedPreferences("tools", Context.MODE_PRIVATE).getInt("sleepGoal", 8).coerceIn(4, 12)
            // Same Wake up + sleep-goal schedule as the app. Snoozes never shift bedtime.
            val wake = all.firstOrNull { it.label == "Wake up" && it.enabled }
            val beds = if (wake == null || !prefs.getBoolean("bedtimeEnabled", true)) emptyList() else
                (-1L..3L).mapNotNull { offset ->
                    val day = date.plusDays(offset)
                    if (wake.days.isNotEmpty() && day.dayOfWeek.value !in wake.days) null else {
                        val at = day.atTime(wake.hour, wake.minute).atZone(zone).toInstant().toEpochMilli()
                        if (wake.days.isEmpty() && at <= now) null else
                            day.atTime(wake.hour, wake.minute).minusHours(goal.toLong()).atZone(zone).toInstant().toEpochMilli()
                    }
                }.filter { it + WidgetTiming.BEDTIME_GRACE > now }.sorted()
            val bed = beds.firstOrNull() ?: 0L
            val ordinary = WidgetTiming.kind(now, next?.second ?: 0, ringing != null, bed, aw, bw)
            val feedback = c.getSharedPreferences("widget_feedback", Context.MODE_PRIVATE)
            val until = feedback.getLong("until", 0)
            val off = all.firstOrNull { it.id == feedback.getString("id", null) && !it.enabled && it.revision == feedback.getLong("revision", 0) && it.writer == feedback.getString("writer", null) }
            val kind = if (ordinary != "ringing" && off != null && now < until) "off" else ordinary
            val chosen = if (kind == "off") off else ringing ?: next?.first
            val boundaries = mutableListOf<Long>()
            next?.second?.let { boundaries += if (it - now > aw) it - aw else it }
            beds.forEach { b -> boundaries += when { b - now > bw -> b - bw; b > now -> b; else -> b + WidgetTiming.BEDTIME_GRACE } }
            // Pending receiver delivery must not leave a zero/negative countdown indefinitely.
            val waiting = all.any { it.enabled && state.getLong("${it.id}.scheduled", 0) in (now - 60_000)..now && state.getLong("${it.id}.scheduled", 0) > 0 }
            if (waiting) boundaries += now + 5_000
            val at = when (kind) { "ringing" -> state.getLong("${ringing?.id}.active", 0); "alarm" -> next?.second ?: 0; "bedtime", "bednow" -> bed; else -> 0 }
            if ((kind == "alarm" || kind == "bedtime") && at > now) {
                val minutes = ((at - now + 59_999) / 60_000).coerceAtLeast(1)
                boundaries += at - (minutes - 1) * 60_000
            }
            if (kind == "off") boundaries += until
            return Snapshot(kind, chosen, at, boundaries.filter { it > now }.minOrNull() ?: 0)
        }

        fun views(c: Context, wide: Boolean, s: Snapshot, now: Long): RemoteViews {
            return viewsAtSize(c, wide, s, now, if (wide) 340 else 160, if (wide) 80 else 160)
        }
        fun viewsAtSize(c: Context, wide: Boolean, s: Snapshot, now: Long, widthDp: Int, heightDp: Int): RemoteViews {
            val layout = if (!wide) R.layout.glance_widget_square else if (widthDp < 280) R.layout.glance_widget_strip_small else R.layout.glance_widget_strip
            val v = RemoteViews(c.packageName, layout)
            val event = s.kind != "clock"
            val alarm = s.kind == "alarm" || s.kind == "ringing" || s.kind == "off"
            val snoozed = alarm && s.alarm?.snoozeUntil?.let { it > now } == true
            val open = launch(c, Intent.ACTION_MAIN)
            v.setOnClickPendingIntent(R.id.widget_clock, open)
            val darkClock = c.getSharedPreferences("widget", Context.MODE_PRIVATE).getBoolean("darkClockText", false)
            v.setTextColor(R.id.widget_clock, if (darkClock) android.graphics.Color.rgb(20, 28, 24) else android.graphics.Color.WHITE)
            v.setViewVisibility(R.id.widget_clock, if (wide || !event) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.widget_event, if (event) View.VISIBLE else if (wide) View.INVISIBLE else View.GONE)
            v.setViewVisibility(R.id.widget_backdrop, if (event) View.VISIBLE else View.GONE)
            // One full-size background layer. Content containers never carry backgrounds.
            if (!event) v.setImageViewResource(R.id.widget_backdrop, 0)
            if (event) v.setImageViewBitmap(R.id.widget_backdrop, WidgetBackdrop.render(c, wide,
                (widthDp - 16).coerceAtLeast(100), (heightDp - 16).coerceAtLeast(if (wide) 48 else 100), !alarm))
            v.setImageViewResource(R.id.widget_icon, when { !alarm -> R.drawable.widget_moon; snoozed -> R.drawable.widget_snooze; else -> R.drawable.widget_alarm })
            val title = when (s.kind) {
                "off" -> "Alarm off"
                "ringing" -> "Alarm ringing"
                "alarm" -> if (snoozed) "Snoozed" else if (wide) s.alarm?.label?.ifBlank { "Alarm" } ?: "Alarm" else "Alarm"
                "bedtime" -> "Bedtime"
                "bednow" -> "It’s bedtime"
                else -> ""
            }
            val eventTime = if (s.at > 0) time(c, s.at) else s.alarm?.let {
                time(c, LocalDate.now().atTime(it.hour, it.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            } ?: ""
            val minutes = ((s.at - now + 59_999) / 60_000).coerceAtLeast(1)
            val primary = when (s.kind) {
                "alarm", "bedtime" -> if (wide) "In $minutes min" else "$minutes min"
                "ringing" -> "Ringing"
                "bednow" -> "Time for bed"
                "off" -> "Alarm off"
                else -> ""
            }
            val secondary = when (s.kind) {
                "bedtime" -> if (wide) "Bedtime · $eventTime" else "Until bedtime"
                "bednow" -> "Bedtime"
                "off" -> eventTime
                else -> if (wide) "$eventTime · $title" else if (snoozed) "Snoozed · $eventTime" else eventTime
            }
            v.setTextViewText(R.id.widget_title, secondary)
            v.setTextViewText(R.id.widget_countdown, primary)
            v.setViewVisibility(R.id.widget_event_time, View.GONE)
            v.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
            v.setContentDescription(R.id.widget_event, "$title${if (s.at > 0) " at $eventTime" else ""}. $primary. Tap to ${if (alarm) "edit alarm" else "open bedtime"}.")
            v.setOnClickPendingIntent(R.id.widget_event, if (alarm) launch(c, EDIT, s.alarm?.id) else launch(c, BEDTIME))
            v.setViewVisibility(R.id.widget_toggle, if (alarm && s.alarm != null) View.VISIBLE else View.INVISIBLE)
            s.alarm?.let { a ->
                v.setTextViewText(R.id.widget_toggle, if (s.kind == "off") "Undo" else "")
                val padding = if (s.kind == "off") 0 else (8 * c.resources.displayMetrics.density).toInt()
                v.setViewPadding(R.id.widget_toggle, padding, padding, padding, padding)
                v.setTextViewCompoundDrawablesRelative(R.id.widget_toggle, if (s.kind == "off") 0 else R.drawable.widget_switch_on, 0, 0, 0)
                v.setContentDescription(R.id.widget_toggle, if (s.kind == "off") "Undo turning off ${a.label}" else "${a.label}: on, turn off")
                v.setOnClickPendingIntent(R.id.widget_toggle, if (s.kind == "off") undo(c, a.id) else toggle(c, a))
            }
            return v
        }
        fun refresh(c: Context) {
            val manager = AppWidgetManager.getInstance(c)
            val ids = listOf(AlarmWidget::class.java, WideAlarmWidget::class.java).flatMap { manager.getAppWidgetIds(ComponentName(c, it)).toList() }
            val alarmManager = c.getSystemService(AlarmManager::class.java)
            alarmManager.cancel(boundary(c))
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val s = snapshot(c, now)
            ids.forEach { id ->
                val wide = manager.getAppWidgetInfo(id)?.provider?.className == WideAlarmWidget::class.java.name
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (wide) 340 else 160).coerceIn(if (wide) 250 else 130, 900)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, if (wide) 80 else 160).coerceIn(if (wide) 70 else 130, 500)
                manager.updateAppWidget(id, viewsAtSize(c, wide, s, now, width, height))
            }
            if (s.boundaryAt > now) {
                // Non-wakeup visual boundary only. Ring delivery remains the core scheduler's job.
                if (Scheduler.allowed(c)) {
                    try { alarmManager.setExact(AlarmManager.RTC, s.boundaryAt, boundary(c)) }
                    catch (_: SecurityException) { alarmManager.set(AlarmManager.RTC, s.boundaryAt, boundary(c)) }
                } else alarmManager.set(AlarmManager.RTC, s.boundaryAt, boundary(c))
            }
        }
    }
}
class WideAlarmWidget : AlarmWidget()
