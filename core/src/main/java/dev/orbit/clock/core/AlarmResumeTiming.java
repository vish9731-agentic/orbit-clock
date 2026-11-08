package dev.orbit.clock.core;

import java.time.*;
import java.util.*;

/** Calendar-only suggestions. No preferences, delayed jobs, inferred holidays or repeat edits. */
public final class AlarmResumeTiming {
    private AlarmResumeTiming() {}
    public static final class Option {
        public final Instant at;
        public final String kind;
        public Option(Instant at, String kind) { this.at = at; this.kind = kind; }
    }
    public static final class Suggestions {
        public final Instant skipped;
        public final List<Option> options;
        Suggestions(Instant skipped, List<Option> options) {
            this.skipped = skipped;
            this.options = Collections.unmodifiableList(options);
        }
    }
    public static Suggestions suggest(int hour, int minute, Set<Integer> days, long snoozeUntil,
            long skipThrough, long activeOccurrence, Instant now, ZoneId zone) {
        Instant skipped = activeOccurrence > 0 ? Instant.ofEpochMilli(activeOccurrence) :
            AlarmTiming.next(hour, minute, days, true, false, snoozeUntil, skipThrough, now, zone);
        if (skipped == null) return new Suggestions(now, new ArrayList<>());
        long cutoff = Math.max(skipThrough, skipped.toEpochMilli());
        Instant next = AlarmTiming.next(hour, minute, days, true, false, 0, cutoff, now, zone);
        List<Option> options = new ArrayList<>();
        if (next == null) return new Suggestions(skipped, options);
        options.add(new Option(next, "next"));
        LocalDate first = next.atZone(zone).toLocalDate();
        if (days.isEmpty()) {
            addCategory(options, hour, minute, days, first, false, "next_weekday", now, zone);
            addCategory(options, hour, minute, days, first, true, "next_weekend", now, zone);
        } else if (first.getDayOfWeek().getValue() >= 6) {
            // Offer a weekend break only when the saved repeat set contains a weekday.
            addCategory(options, hour, minute, days, first, false, "after_weekend", now, zone);
        }
        return new Suggestions(skipped, options);
    }
    private static void addCategory(List<Option> options, int hour, int minute, Set<Integer> days,
            LocalDate first, boolean weekend, String kind, Instant now, ZoneId zone) {
        for (int i = 0; i <= 7; i++) {
            LocalDate day = first.plusDays(i);
            int weekday = day.getDayOfWeek().getValue();
            if ((weekday >= 6) != weekend || (!days.isEmpty() && !days.contains(weekday))) continue;
            Instant at = day.atTime(hour, minute).atZone(zone).toInstant();
            if (!at.isAfter(now)) continue;
            for (Option old : options) if (old.at.equals(at)) return;
            options.add(new Option(at, kind));
            return;
        }
    }
    public static boolean validTarget(int hour, int minute, Set<Integer> days, Instant at,
            Instant now, ZoneId zone) {
        if (!at.isAfter(now)) return false;
        LocalDate date = at.atZone(zone).toLocalDate();
        if (!days.isEmpty() && !days.contains(date.getDayOfWeek().getValue())) return false;
        // Resolve the saved wall time through the same spring-gap/fall-overlap rule as alarms.
        return date.atTime(hour, minute).atZone(zone).toInstant().equals(at);
    }
}
