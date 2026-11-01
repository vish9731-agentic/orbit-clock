import dev.orbit.clock.core.AlarmTiming;
import dev.orbit.clock.core.RotaryGesture;
import dev.orbit.clock.core.SnoozeDuration;
import dev.orbit.clock.core.RingAtmosphere;
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
        int midnight = RingAtmosphere.tint(0), dawn = RingAtmosphere.tint(420), morning = RingAtmosphere.tint(600);
        int afternoon = RingAtmosphere.tint(840), evening = RingAtmosphere.tint(1110), night = RingAtmosphere.tint(1320);
        eq("deep-night blue hint", true, (midnight & 255) > (midnight >> 16 & 255));
        eq("dawn rose hint", true, (dawn >> 16 & 255) > (dawn & 255));
        eq("morning sage hint", true, (morning >> 8 & 255) > (morning >> 16 & 255));
        eq("afternoon warm hint", true, (afternoon >> 16 & 255) > (afternoon >> 8 & 255) && (afternoon >> 8 & 255) > (afternoon & 255));
        eq("evening violet hint", true, (evening & 255) > (evening >> 16 & 255) && (evening >> 16 & 255) > (evening >> 8 & 255));
        eq("night indigo hint", true, (night & 255) > (night >> 16 & 255));
        eq("atmosphere midnight wraps", RingAtmosphere.tint(0), RingAtmosphere.tint(1440));
        eq("atmosphere negative minute wraps", RingAtmosphere.tint(1439), RingAtmosphere.tint(-1));
        boolean dark = true, smooth = true;
        for (int minute=0; minute<1440; minute++) {
            int color=RingAtmosphere.tint(minute), nextColor=RingAtmosphere.tint(minute+1);
            dark &= (color >>> 24)==255;
            for (int shift : new int[]{16,8,0}) {
                int channel=color >> shift & 255;
                dark &= channel <= 20;
                smooth &= Math.abs(channel-(nextColor >> shift & 255)) <= 1;
            }
        }
        eq("all-day tint stays near black", true, dark);
        eq("all-day tint blends without boundary jumps", true, smooth);
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
