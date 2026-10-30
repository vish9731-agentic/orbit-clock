package dev.orbit.clock.core;

import java.time.*;
import java.util.Set;

/** Android-independent timing policy used by Alarm.next, including DST rules. */
public final class AlarmTiming {
    private AlarmTiming() {}
    public static Instant nextTimer(boolean enabled, boolean deleted, long deadline, long skipThrough, Instant now) {
        if (!enabled || deleted || deadline <= now.toEpochMilli() || deadline <= skipThrough) return null;
        return Instant.ofEpochMilli(deadline);
    }
    public static Instant next(int hour, int minute, Set<Integer> days, boolean enabled,
            boolean deleted, long snoozeUntil, long skipThrough, Instant now, ZoneId zone) {
        LocalTime time = LocalTime.of(hour, minute);
        for (int day : days) if (day < 1 || day > 7) throw new IllegalArgumentException("ISO weekday required");
        if (!enabled || deleted) return null;
        if (snoozeUntil > now.toEpochMilli() && snoozeUntil > skipThrough)
            return Instant.ofEpochMilli(snoozeUntil);
        LocalDate date = now.atZone(zone).toLocalDate();
        for (int offset = 0; offset <= 8; offset++) {
            LocalDate day = date.plusDays(offset);
            if (!days.isEmpty() && !days.contains(day.getDayOfWeek().getValue())) continue;
            // Move nonexistent spring times forward; choose the first fall overlap.
            Instant candidate = day.atTime(time).atZone(zone).toInstant();
            if (candidate.isAfter(now) && candidate.toEpochMilli() > skipThrough) return candidate;
        }
        return null;
    }
}
