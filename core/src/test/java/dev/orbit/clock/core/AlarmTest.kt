package dev.orbit.clock.core

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class AlarmTest {
    private val utc = ZoneId.of("UTC")
    @Test fun oneShotRollsToTomorrow() {
        val a = Alarm(hour=7)
        assertEquals(Instant.parse("2026-10-31T07:00:00Z"), a.next(Instant.parse("2026-10-30T08:00:00Z"),utc))
    }
    @Test fun weekdayAlarmSkipsWeekend() {
        val a = Alarm(hour=7,days=setOf(1,2,3,4,5))
        assertEquals(Instant.parse("2026-11-02T07:00:00Z"), a.next(Instant.parse("2026-10-30T08:00:00Z"),utc))
    }
    @Test fun snoozeTakesPriority() {
        val at = Instant.parse("2026-10-30T08:10:00Z")
        assertEquals(at,Alarm(snoozeUntil=at.toEpochMilli()).next(Instant.parse("2026-10-30T08:00:00Z"),utc))
    }
    @Test fun disabledAndDeletedNeverSchedule() {
        assertNull(Alarm(enabled=false).next())
        assertNull(Alarm(deleted=true).next())
    }
    @Test fun nonexistentSpringTimeMovesForward() {
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"),Alarm(hour=2,minute=30)
            .next(Instant.parse("2026-03-08T05:00:00Z"),ZoneId.of("America/New_York")))
    }
    @Test fun fallOverlapRingsOnce() {
        // Between the two 01:30 occurrences, schedule tomorrow rather than ring twice.
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"),Alarm(hour=1,minute=30)
            .next(Instant.parse("2026-11-01T05:45:00Z"),ZoneId.of("America/New_York")))
    }
    @Test fun dismissingAnOccurrenceSkipsIt() {
        val at=Instant.parse("2026-10-30T07:00:00Z")
        assertEquals(Instant.parse("2026-10-31T07:00:00Z"),Alarm(hour=7,skipThrough=at.toEpochMilli())
            .next(Instant.parse("2026-10-30T06:00:00Z"),utc))
    }
}
