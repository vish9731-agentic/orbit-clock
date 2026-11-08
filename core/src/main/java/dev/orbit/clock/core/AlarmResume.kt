package dev.orbit.clock.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** A temporary-off choice carries the exact disabled revision it is allowed to change. */
object AlarmResume {
    data class Option(val at: Instant, val kind: String)
    data class Plan(val disabled: Alarm, val skipped: Instant, val options: List<Option>)

    fun plan(source: Alarm, disabled: Alarm, activeOccurrence: Long, now: Instant, zone: ZoneId): Plan {
        val suggestions = AlarmResumeTiming.suggest(source.hour, source.minute, source.days,
            source.snoozeUntil, source.skipThrough, activeOccurrence, now, zone)
        return Plan(disabled, suggestions.skipped, suggestions.options.map { Option(it.at, it.kind) })
    }

    fun encode(plan: Plan): String = JSONObject().apply {
        put("disabled", Store.encode(plan.disabled))
        put("skipped", plan.skipped.toEpochMilli())
        put("options", JSONArray().apply { plan.options.forEach { option ->
            put(JSONObject().put("at", option.at.toEpochMilli()).put("kind", option.kind))
        } })
    }.toString()

    fun decode(raw: String): Plan? = runCatching {
        val value = JSONObject(raw)
        val disabled = Store.decode(value.getJSONObject("disabled"))
        require(!disabled.enabled && !disabled.deleted && !disabled.timer)
        val skipped = Instant.ofEpochMilli(value.getLong("skipped"))
        val choices = value.getJSONArray("options")
        require(choices.length() in 1..3)
        val options = (0 until choices.length()).map { index ->
            val item = choices.getJSONObject(index)
            val kind = item.getString("kind")
            require(kind in setOf("next", "after_weekend", "next_weekday", "next_weekend"))
            val at = Instant.ofEpochMilli(item.getLong("at"))
            require(at.isAfter(skipped))
            Option(at, kind)
        }
        require(options.map { it.at }.distinct().size == options.size)
        Plan(disabled, skipped, options)
    }.getOrNull()
}
