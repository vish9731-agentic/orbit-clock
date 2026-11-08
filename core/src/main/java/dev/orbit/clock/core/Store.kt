package dev.orbit.clock.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.time.Instant
import java.time.ZoneId

class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    val device: String get() = synchronized(lock) {
        prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).commit() }
    }
    fun all(): List<Alarm> = synchronized(lock) {
        val list = JSONArray(prefs.getString("records", "[]"))
        (0 until list.length()).map { decode(list.getJSONObject(it)) }
    }
    fun get(id: String) = all().firstOrNull { it.id == id }
    fun save(alarm: Alarm): Alarm = synchronized(lock) {
        val old = get(alarm.id)
        val updated = alarm.copy(revision = maxOf(System.currentTimeMillis(), (old?.revision ?: 0) + 1), writer = device)
        persist(all().filterNot { it.id == updated.id } + updated)
        Scheduler.schedule(context, updated)
        Sync.push(context, updated)
        context.sendBroadcast(android.content.Intent(CHANGED).setPackage(context.packageName))
        updated
    }
    /** Disable first: dismissing the suggestion must never silently re-enable an alarm. */
    fun disableWithSuggestions(id: String, now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(), expectedRevision: Long? = null,
            expectedWriter: String? = null): AlarmResume.Plan? = synchronized(lock) {
        val source = get(id) ?: return@synchronized null
        if (!source.enabled || source.deleted || source.timer) return@synchronized null
        if ((expectedRevision != null && source.revision != expectedRevision) ||
                (expectedWriter != null && source.writer != expectedWriter)) return@synchronized null
        val occurrence = context.getSharedPreferences("scheduled_occurrences", Context.MODE_PRIVATE)
            .getLong("$id.active", 0)
        val active = occurrence.takeIf { Scheduler.isActive(context, source, it) } ?: 0L
        val disabled = save(source.copy(enabled = false, snoozeUntil = 0))
        RingingService.reconcile(context, disabled)
        AlarmResume.plan(source, disabled, active, now, zone)
    }
    /** Read the latest record, so a stale visible row cannot overwrite a paired-device edit. */
    fun enable(id: String): Alarm? = synchronized(lock) {
        val current = get(id) ?: return@synchronized null
        if (current.deleted || current.timer) return@synchronized null
        if (current.enabled) return@synchronized current
        save(current.copy(enabled = true, snoozeUntil = 0, skipThrough = 0))
    }
    fun resumeSuggestion(plan: AlarmResume.Plan, option: AlarmResume.Option,
            now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Alarm? = synchronized(lock) {
        val expected = plan.disabled
        val current = get(expected.id) ?: return@synchronized null
        if (current != expected || current.enabled || current.deleted || current.timer ||
                option !in plan.options || !option.at.isAfter(plan.skipped) ||
                !AlarmResumeTiming.validTarget(current.hour, current.minute, current.days, option.at, now, zone))
            return@synchronized null
        save(current.copy(enabled = true, snoozeUntil = 0, skipThrough = option.at.toEpochMilli() - 1))
    }
    fun merge(incoming: Alarm) = synchronized(lock) {
        val old = get(incoming.id)
        if (old == null || incoming.revision > old.revision ||
            (incoming.revision == old.revision && incoming.writer > old.writer)) {
            persist(all().filterNot { it.id == incoming.id } + incoming)
            Scheduler.schedule(context, incoming)
            RingingService.reconcile(context, incoming)
            context.sendBroadcast(android.content.Intent(CHANGED).setPackage(context.packageName))
        }
    }
    private fun persist(records: List<Alarm>) {
        check(prefs.edit().putString("records", JSONArray().apply { records.forEach { put(encode(it)) } }.toString()).commit())
    }
    companion object {
        private val lock = Any()
        const val CHANGED = "dev.orbit.clock.CHANGED"
        fun encode(a: Alarm) = JSONObject().apply {
            put("id", a.id); put("hour", a.hour); put("minute", a.minute); put("label", a.label); put("timer", a.timer)
            put("days", JSONArray(a.days.sorted())); put("enabled", a.enabled); put("vibrate", a.vibrate)
            put("sound", a.sound); put("snoozeMinutes", a.snoozeMinutes); put("snoozeUntil", a.snoozeUntil)
            put("skipThrough", a.skipThrough); put("deleted", a.deleted); put("revision", a.revision); put("writer", a.writer)
        }
        fun decode(o: JSONObject): Alarm {
            val ds = o.getJSONArray("days")
            return Alarm(id=o.getString("id"), hour=o.getInt("hour").also { require(it in 0..23) },
                minute=o.getInt("minute").also { require(it in 0..59) }, label=o.getString("label"),
                timer=o.optBoolean("timer", o.getString("label").startsWith("Timer ·")),
                days=(0 until ds.length()).map { ds.getInt(it).also { d -> require(d in 1..7) } }.toSet(),
                enabled=o.getBoolean("enabled"), vibrate=o.getBoolean("vibrate"), sound=o.getBoolean("sound"),
                snoozeMinutes=o.getInt("snoozeMinutes").also { require(it in 1..60) },
                snoozeUntil=o.getLong("snoozeUntil"), skipThrough=o.getLong("skipThrough"),
                deleted=o.getBoolean("deleted"), revision=o.getLong("revision"), writer=o.getString("writer"))
        }
    }
}
