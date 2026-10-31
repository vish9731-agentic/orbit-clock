import dev.orbit.clock.core.AlarmTiming;
import dev.orbit.clock.core.RotaryGesture;
import dev.orbit.clock.core.SnoozeDuration;
import java.time.*;
import java.util.*;

/** Executes actual production policies without Android SDK or third-party test dependencies. */
public class CoreChecks {
    static int count;
    static final ZoneId UTC = ZoneId.of("UTC");
    static void eq(String name, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(name + ": expected " + expected + " got " + actual);
        count++; System.out.println("PASS " + name);
    }
    static Instant next(int h, int m, Set<Integer> ds, String now, String zone, long snooze, long cutoff) {
        return AlarmTiming.next(h,m,ds,true,false,snooze,cutoff,Instant.parse(now),ZoneId.of(zone));
    }
    static void at(String name, String expected, Instant actual) { eq(name,Instant.parse(expected),actual); }
    public static void main(String[] args) {
        at("one-shot today","2026-10-30T07:00:00Z",next(7,0,Set.of(),"2026-10-30T06:00:00Z","UTC",0,0));
        at("one-shot tomorrow","2026-10-31T07:00:00Z",next(7,0,Set.of(),"2026-10-30T08:00:00Z","UTC",0,0));
        at("weekend skipped","2026-11-02T07:00:00Z",next(7,0,Set.of(1,2,3,4,5),"2026-10-30T08:00:00Z","UTC",0,0));
        at("weekly repeat","2026-11-06T07:00:00Z",next(7,0,Set.of(5),"2026-10-30T08:00:00Z","UTC",0,0));
        at("midnight rolls date","2026-10-31T00:00:00Z",next(0,0,Set.of(),"2026-10-30T23:59:59Z","UTC",0,0));
        at("exactly now is not rescheduled","2026-10-31T07:00:00Z",next(7,0,Set.of(),"2026-10-30T07:00:00Z","UTC",0,0));
        long snooze=Instant.parse("2026-10-30T08:10:00Z").toEpochMilli();
        at("snooze overrides wall time","2026-10-30T08:10:00Z",next(7,0,Set.of(),"2026-10-30T08:00:00Z","UTC",snooze,0));
        at("dismissed snooze ignored","2026-10-31T07:00:00Z",next(7,0,Set.of(),"2026-10-30T08:00:00Z","UTC",snooze,snooze));
        at("expired snooze ignored","2026-10-31T07:00:00Z",next(7,0,Set.of(),"2026-10-30T09:00:00Z","UTC",snooze,0));
        at("dismissed occurrence skipped","2026-10-31T07:00:00Z",next(7,0,Set.of(),"2026-10-30T06:00:00Z","UTC",0,Instant.parse("2026-10-30T07:00:00Z").toEpochMilli()));
        eq("disabled",null,AlarmTiming.next(7,0,Set.of(),false,false,snooze,0,Instant.parse("2026-10-30T06:00:00Z"),UTC));
        eq("deleted",null,AlarmTiming.next(7,0,Set.of(),true,true,snooze,0,Instant.parse("2026-10-30T06:00:00Z"),UTC));
        at("spring DST gap","2026-03-08T07:30:00Z",next(2,30,Set.of(),"2026-03-08T05:00:00Z","America/New_York",0,0));
        at("fall DST first occurrence","2026-11-01T05:30:00Z",next(1,30,Set.of(),"2026-11-01T04:00:00Z","America/New_York",0,0));
        at("fall DST no second ring","2026-11-02T06:30:00Z",next(1,30,Set.of(),"2026-11-01T05:45:00Z","America/New_York",0,0));
        at("local timezone followed","2026-10-30T01:30:00Z",next(7,0,Set.of(),"2026-10-30T00:00:00Z","Asia/Kolkata",0,0));
        boolean rejected=false;
        try { next(7,0,Set.of(8),"2026-10-30T00:00:00Z","UTC",0,0); } catch(IllegalArgumentException e) { rejected=true; }
        eq("invalid weekdays rejected",true,rejected);
        Instant now = Instant.parse("2026-10-30T08:00:00Z");
        eq("future timer",Instant.ofEpochMilli(snooze),AlarmTiming.nextTimer(true,false,snooze,0,now));
        eq("expired timer never becomes wall-clock alarm",null,AlarmTiming.nextTimer(true,false,snooze-3600000,0,now));
        eq("disabled timer",null,AlarmTiming.nextTimer(false,false,snooze,0,now));
        eq("dismissed timer",null,AlarmTiming.nextTimer(true,false,snooze,snooze,now));
        RotaryGesture g=new RotaryGesture();
        eq("accidental detent does not snooze",RotaryGesture.Action.NONE,g.turn(-1,100));
        eq("snooze progress",33,g.progress());
        eq("second detent insufficient",RotaryGesture.Action.NONE,g.turn(-1,200));
        eq("third detent snoozes",RotaryGesture.Action.SNOOZE,g.turn(-1,300));
        eq("gesture fires once",RotaryGesture.Action.NONE,g.turn(-10,400));
        g=new RotaryGesture();
        eq("dismiss needs more movement",RotaryGesture.Action.NONE,g.turn(5,100));
        eq("dismiss at six units",RotaryGesture.Action.DISMISS,g.turn(1,200));
        g=new RotaryGesture();g.turn(-2,100);
        eq("pause resets accumulation",RotaryGesture.Action.NONE,g.turn(-1,2000));
        eq("pause resets progress",33,g.progress());
        g=new RotaryGesture();g.turn(-2,100);
        eq("reverse direction resets",RotaryGesture.Action.NONE,g.turn(1,200));
        eq("reverse progress",16,g.progress());
        eq("invalid axis ignored",RotaryGesture.Action.NONE,g.turn(Float.NaN,300));
        eq("zero movement ignored",RotaryGesture.Action.NONE,g.turn(0,400));
        eq("snooze minimum",5,SnoozeDuration.clamp(0));
        eq("snooze maximum",60,SnoozeDuration.clamp(90));
        eq("snooze adds five minutes",15,SnoozeDuration.increase(10));
        eq("snooze subtracts five minutes",5,SnoozeDuration.decrease(10));
        eq("snooze stays at minimum",5,SnoozeDuration.decrease(5));
        eq("snooze stays at maximum",60,SnoozeDuration.increase(60));
        eq("existing custom snooze retained",12,SnoozeDuration.clamp(12));
        eq("custom snooze moves by five",17,SnoozeDuration.increase(12));
        eq("snooze overflow protected",60,SnoozeDuration.increase(Integer.MAX_VALUE));
        eq("snooze underflow protected",5,SnoozeDuration.decrease(Integer.MIN_VALUE));
        System.out.println("\n"+count+" production-policy checks passed.");
    }
}
