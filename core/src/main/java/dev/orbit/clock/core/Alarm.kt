package dev.orbit.clock.core

import java.time.*
import java.util.UUID

/** Weekdays use ISO values: Monday=1 through Sunday=7. Wall time follows the local zone. */
data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "Alarm",
    val timer: Boolean = false,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
    val snoozeMinutes: Int = 10,
    val snoozeUntil: Long = 0,
    val skipThrough: Long = 0,
    val deleted: Boolean = false,
    val revision: Long = System.currentTimeMillis(),
    val writer: String = ""
) {
    fun next(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Instant? =
        if (timer) AlarmTiming.nextTimer(enabled, deleted, snoozeUntil, skipThrough, now)
        else AlarmTiming.next(hour, minute, days, enabled, deleted, snoozeUntil, skipThrough, now, zone)
}
