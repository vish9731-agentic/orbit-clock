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
        long anchor = Instant.parse("2026-11-02T08:30:00Z").toEpochMilli();
        long occurrence = anchor;
        for (int i=1;i<=8;i++) {
            occurrence = SnoozeDuration.nextAlarm(occurrence, occurrence+30_000L, 5);
            eq("no drift snooze "+i, anchor+i*300_000L, occurrence);
        }
        eq("late handling skips passed slots", anchor+900_000L,
            SnoozeDuration.nextAlarm(anchor, anchor+610_000L, 5));
        eq("exact deadline uses future slot", anchor+600_000L,
            SnoozeDuration.nextAlarm(anchor, anchor+300_000L, 5));
        eq("old seconds are removed", anchor+300_000L,
            SnoozeDuration.nextAlarm(anchor+21_000L, anchor+40_000L, 5));

        String[] periods={"dawn","morning","noon","afternoon","sunset","blue-hour","night"};
        int[] sampleMinutes={360,480,720,900,1080,1260,60};
        for(int i=0;i<periods.length;i++) eq("local atmosphere "+periods[i],periods[i],RingAtmosphere.period(sampleMinutes[i]));
        RingAtmosphere.Scene dawn=RingAtmosphere.sample(330).from, morning=RingAtmosphere.sample(480).from;
        RingAtmosphere.Scene noon=RingAtmosphere.sample(720).from, afternoon=RingAtmosphere.sample(900).from;
        RingAtmosphere.Scene sunset=RingAtmosphere.sample(1080).from, blue=RingAtmosphere.sample(1260).from;
        RingAtmosphere.Scene night=RingAtmosphere.sample(1380).from;
        eq("dawn combines warm horizon with cool sky",true,(dawn.glowColor>>16&255)>(dawn.glowColor&255) && (dawn.skyColor&255)>(dawn.skyColor>>16&255));
        eq("morning sunlight is yellow",true,(morning.sunColor>>16&255)>(morning.sunColor&255) && (morning.sunColor>>8&255)>(morning.sunColor&255));
        eq("noon sky is blue",true,(noon.skyColor&255)>(noon.skyColor>>8&255) && (noon.skyColor>>8&255)>(noon.skyColor>>16&255));
        eq("afternoon warmth remains distinct from noon",true,afternoon.glowColor!=noon.glowColor && (afternoon.glowColor>>16&255)>(afternoon.glowColor&255));
        eq("sunset glow is orange",true,(sunset.glowColor>>16&255)>(sunset.glowColor>>8&255) && (sunset.glowColor>>8&255)>(sunset.glowColor&255));
        eq("blue hour has no hot sun",true,blue.sun==0 && (blue.skyColor&255)>(blue.skyColor>>16&255));
        eq("night has restrained stars and moon",true,night.sun==0 && night.stars>0 && night.moon>0);
        eq("daylight has no stars or moon",true,morning.stars==0 && morning.moon==0 && noon.stars==0 && noon.moon==0);
        eq("atmosphere midnight wraps",RingAtmosphere.period(0),RingAtmosphere.period(1440));
        eq("atmosphere negative minute wraps",RingAtmosphere.period(1439),RingAtmosphere.period(-1));
        eq("atmosphere blend wraps",RingAtmosphere.sample(1439).blend,RingAtmosphere.sample(-1).blend);
        boolean black=true,smooth=true,corner=true,weights=true,sourceColors=true;
        for(int minute=0;minute<1440;minute++) {
            int base=RingAtmosphere.tint(minute);
            RingAtmosphere.Frame frame=RingAtmosphere.sample(minute),nextFrame=RingAtmosphere.sample(minute+1);
            black &= (base>>>24)==255;
            weights &= frame.minute==minute && frame.blend>=0 && frame.blend<=1;
            for(int shift:new int[]{16,8,0}) black &= (base>>shift&255)<=8;
            for(RingAtmosphere.Scene scene:new RingAtmosphere.Scene[]{frame.from,frame.to}) {
                corner &= (scene.cornerX<=.15f || scene.cornerX>=.85f) && scene.cornerY>=0 && scene.cornerY<=.10f;
                for(int c:new int[]{scene.glowColor,scene.skyColor,scene.sunColor}) sourceColors &= (c>>>24)==255;
            }
            int[] hereFrom={frame.from.glowColor,frame.from.skyColor,frame.from.sunColor};
            int[] hereTo={frame.to.glowColor,frame.to.skyColor,frame.to.sunColor};
            int[] nextFrom={nextFrame.from.glowColor,nextFrame.from.skyColor,nextFrame.from.sunColor};
            int[] nextTo={nextFrame.to.glowColor,nextFrame.to.skyColor,nextFrame.to.sunColor};
            for(int role=0;role<3;role++) for(int shift:new int[]{16,8,0}) {
                float here=(hereFrom[role]>>shift&255)*(1-frame.blend)+(hereTo[role]>>shift&255)*frame.blend;
                float there=(nextFrom[role]>>shift&255)*(1-nextFrame.blend)+(nextTo[role]>>shift&255)*nextFrame.blend;
                smooth &= Math.abs(here-there)<4f;
            }
        }
        eq("main field stays black throughout the day",true,black);
        eq("corner scenes never traverse the clock or controls",true,corner);
        eq("all-day palettes blend without flashes",true,smooth);
        eq("all-day blend weights are valid",true,weights);
        eq("localized color sources are opaque",true,sourceColors);
        LocalDateTime original=LocalDateTime.of(2026,11,1,15,0), ringing=original;
        eq("original afternoon ringing atmosphere","afternoon",RingAtmosphere.period(ringing.getHour()*60+ringing.getMinute()));
        for(int i=0;i<8;i++) ringing=ringing.plusMinutes(60);
        eq("eight hourly snoozes reach 23:00",LocalTime.of(23,0),ringing.toLocalTime());
        eq("later ringing selects current night despite afternoon original","night",RingAtmosphere.period(ringing.getHour()*60+ringing.getMinute()));
        ZonedDateTime localNight=Instant.parse("2026-11-01T17:30:00Z").atZone(ZoneId.of("Asia/Kolkata"));
        eq("time of day follows local zone","night",RingAtmosphere.period(localNight.getHour()*60+localNight.getMinute()));
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
