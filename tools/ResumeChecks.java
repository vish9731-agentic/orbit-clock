import dev.orbit.clock.core.AlarmResumeTiming;
import dev.orbit.clock.core.AlarmTiming;
import java.time.*;
import java.util.*;

/** Executes production temporary-off policies without Android or test doubles. */
public final class ResumeChecks {
    private static int checks;
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Set<Integer> DAILY = Set.of(1,2,3,4,5,6,7);
    private static final Set<Integer> WEEKDAYS = Set.of(1,2,3,4,5);
    private static final Set<Integer> WEEKENDS = Set.of(6,7);
    private static void eq(String name, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(name + ": " + expected + " != " + actual);
        checks++;
    }
    private static Instant at(String value) { return Instant.parse(value); }
    private static AlarmResumeTiming.Suggestions suggest(Set<Integer> days, String now) {
        return AlarmResumeTiming.suggest(6,0,days,0,0,0,at(now),UTC);
    }
    private static void first(String name, Set<Integer> days, String now, String expected) {
        AlarmResumeTiming.Suggestions plan = suggest(days, now);
        eq(name, at(expected), plan.options.get(0).at);
        eq(name + " remains scheduled immediately", at(expected), AlarmTiming.next(6,0,days,
            true,false,0,plan.options.get(0).at.toEpochMilli()-1,at(now),UTC));
    }
    public static void main(String[] args) {
        first("Tuesday night skips Wednesday resumes Thursday", WEEKDAYS,"2026-11-03T22:00:00Z","2026-11-05T06:00:00Z");
        first("daily Monday -> Tuesday", DAILY,"2026-11-02T05:00:00Z","2026-11-03T06:00:00Z");
        first("weekday Friday -> Monday", WEEKDAYS,"2026-11-06T05:00:00Z","2026-11-09T06:00:00Z");
        first("weekend Saturday -> Sunday", WEEKENDS,"2026-11-07T05:00:00Z","2026-11-08T06:00:00Z");
        first("weekend Sunday -> next Saturday", WEEKENDS,"2026-11-08T05:00:00Z","2026-11-14T06:00:00Z");
        first("weekly future occurrence extends beyond old eight-day horizon", Set.of(1),
            "2026-11-03T05:00:00Z","2026-11-16T06:00:00Z");
        first("custom Tue Thu Sat keeps repeat days", Set.of(2,4,6),
            "2026-11-03T05:00:00Z","2026-11-05T06:00:00Z");
        first("year boundary", WEEKDAYS,"2027-01-01T05:00:00Z","2027-01-04T06:00:00Z");
        first("leap day", DAILY,"2028-02-28T05:00:00Z","2028-02-29T06:00:00Z");
        first("one-shot remains one occurrence tomorrow", Set.of(),
            "2026-11-02T05:00:00Z","2026-11-03T06:00:00Z");

        AlarmResumeTiming.Suggestions friday = suggest(DAILY,"2026-11-06T05:00:00Z");
        eq("Friday skip primary is Saturday",at("2026-11-07T06:00:00Z"),friday.options.get(0).at);
        eq("daily weekend break is relevant",2,friday.options.size());
        eq("weekend break label", "after_weekend",friday.options.get(1).kind);
        eq("daily weekend break resumes Monday",at("2026-11-09T06:00:00Z"),friday.options.get(1).at);
        eq("weekend-only alarm never invents weekday",1,suggest(WEEKENDS,"2026-11-07T05:00:00Z").options.size());
        eq("weekday schedule gets no duplicate weekend break",1,suggest(WEEKDAYS,"2026-11-06T05:00:00Z").options.size());
        AlarmResumeTiming.Suggestions custom = suggest(Set.of(2,6),"2026-11-03T05:00:00Z");
        eq("custom weekday break respects allowed Tuesday",at("2026-11-10T06:00:00Z"),custom.options.get(1).at);
        AlarmResumeTiming.Suggestions once = suggest(Set.of(),"2026-11-02T05:00:00Z");
        eq("one-shot deduplicates next weekday",2,once.options.size());
        eq("one-shot weekend choice", "next_weekend",once.options.get(1).kind);
        eq("one-shot next weekend date",at("2026-11-07T06:00:00Z"),once.options.get(1).at);
        AlarmResumeTiming.Suggestions onceFriday = suggest(Set.of(),"2026-11-06T05:00:00Z");
        eq("one-shot Friday offers Monday alternate", "next_weekday",onceFriday.options.get(1).kind);
        eq("one-shot Monday alternate date",at("2026-11-09T06:00:00Z"),onceFriday.options.get(1).at);

        Instant monday = at("2026-11-02T06:00:00Z");
        AlarmResumeTiming.Suggestions active = AlarmResumeTiming.suggest(6,0,DAILY,0,0,
            monday.toEpochMilli(),at("2026-11-02T06:01:00Z"),UTC);
        eq("turning off ringing repeat skips active, not tomorrow",at("2026-11-03T06:00:00Z"),active.options.get(0).at);
        eq("active occurrence retained as skipped",monday,active.skipped);
        AlarmResumeTiming.Suggestions snoozed = AlarmResumeTiming.suggest(6,0,DAILY,
            at("2026-11-02T06:20:00Z").toEpochMilli(),monday.toEpochMilli(),0,
            at("2026-11-02T06:10:00Z"),UTC);
        eq("snooze deadline is skipped",at("2026-11-02T06:20:00Z"),snoozed.skipped);
        eq("snooze resumes normal walltime",at("2026-11-03T06:00:00Z"),snoozed.options.get(0).at);
        AlarmResumeTiming.Suggestions alreadyPaused = AlarmResumeTiming.suggest(6,0,DAILY,0,
            at("2026-11-05T06:00:00Z").toEpochMilli()-1,0,at("2026-11-02T05:00:00Z"),UTC);
        eq("another temporary off skips actual next installed day",at("2026-11-06T06:00:00Z"),alreadyPaused.options.get(0).at);

        ZoneId ny = ZoneId.of("America/New_York");
        AlarmResumeTiming.Suggestions spring = AlarmResumeTiming.suggest(2,30,Set.of(),0,0,0,
            at("2026-03-07T05:00:00Z"),ny);
        eq("spring gap shifts resumed day forward",at("2026-03-08T07:30:00Z"),spring.options.get(0).at);
        eq("spring shifted target validates",true,AlarmResumeTiming.validTarget(2,30,Set.of(),
            spring.options.get(0).at,at("2026-03-07T05:00:00Z"),ny));
        AlarmResumeTiming.Suggestions fall = AlarmResumeTiming.suggest(1,30,Set.of(),0,0,0,
            at("2026-10-31T04:00:00Z"),ny);
        eq("fall overlap chooses first occurrence",at("2026-11-01T05:30:00Z"),fall.options.get(0).at);
        eq("second fall occurrence is not a valid resume target",false,AlarmResumeTiming.validTarget(1,30,
            Set.of(),at("2026-11-01T06:30:00Z"),at("2026-10-31T04:00:00Z"),ny));
        eq("invalid changed repeat day rejected",false,AlarmResumeTiming.validTarget(6,0,WEEKDAYS,
            at("2026-11-07T06:00:00Z"),at("2026-11-02T05:00:00Z"),UTC));
        eq("past choice rejected",false,AlarmResumeTiming.validTarget(6,0,DAILY,monday,monday,UTC));
        eq("different walltime rejected",false,AlarmResumeTiming.validTarget(6,0,DAILY,
            at("2026-11-03T06:01:00Z"),monday,UTC));
        AlarmResumeTiming.Suggestions india = AlarmResumeTiming.suggest(6,0,DAILY,0,0,0,
            at("2026-11-02T00:00:00Z"),ZoneId.of("Asia/Kolkata"));
        eq("half-hour zone uses local day/time",at("2026-11-03T00:30:00Z"),india.options.get(0).at);

        // Every weekly subset, all seven start days: no invented repeat days, duplicate
        // options, missed far-future weekly target or unscheduled selected suggestion.
        for (int bits=1;bits<128;bits++) for (int start=0;start<7;start++) {
            Set<Integer> days = new HashSet<>();
            for(int day=1;day<=7;day++)if((bits & (1 << (day-1))) != 0)days.add(day);
            Instant now = at("2026-11-02T05:00:00Z").plus(Duration.ofDays(start));
            AlarmResumeTiming.Suggestions p = AlarmResumeTiming.suggest(6,0,days,0,0,0,now,UTC);
            eq("bounded choices",true,p.options.size()>=1 && p.options.size()<=3);
            Set<Instant> unique = new HashSet<>();
            for(AlarmResumeTiming.Option option:p.options) {
                eq("future after skipped",true,option.at.isAfter(p.skipped));
                eq("preserved repeat set",true,days.contains(option.at.atZone(UTC).getDayOfWeek().getValue()));
                eq("unique choices",true,unique.add(option.at));
                eq("selected target installs exactly",option.at,AlarmTiming.next(6,0,days,true,false,0,
                    option.at.toEpochMilli()-1,now,UTC));
            }
        }
        System.out.println(checks + " temporary-off calendar checks passed.");
    }
}
