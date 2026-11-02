import dev.orbit.clock.phone.WidgetTiming;
import java.util.Objects;
public class WidgetChecks {
    static int count;
    static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
        count++;
    }
    public static void main(String[] args) {
        long now = 10_000_000L;
        for (int minutes : new int[]{10,15,30,45,60,90}) {
            eq(minutes, WidgetTiming.window(minutes));
            long w = minutes * 60_000L;
            eq("clock", WidgetTiming.kind(now, now+w+1, false, 0, w, w));
            eq("alarm", WidgetTiming.kind(now, now+w, false, now+1, w, w));
            eq("bedtime", WidgetTiming.kind(now, 0, false, now+w, w, w));
            eq("clock", WidgetTiming.kind(now, 0, false, now+w+1, w, w));
        }
        eq(15, WidgetTiming.window(20));
        eq("clock", WidgetTiming.kind(now,0,false,0,900000,900000));
        eq("ringing", WidgetTiming.kind(now,now+1,true,now+1,900000,900000));
        eq("bednow", WidgetTiming.kind(now,0,false,now,900000,900000));
        eq("bednow", WidgetTiming.kind(now,0,false,now-899999,900000,900000));
        eq("clock", WidgetTiming.kind(now,0,false,now-900000,900000,900000));
        eq("clock", WidgetTiming.kind(now,now,false,0,900000,900000));
        eq("alarm", WidgetTiming.kind(now,now+5*60000,false,0,900000,900000));
        eq("clock", WidgetTiming.kind(now,now+30*60000,false,0,900000,900000));
        System.out.println("PASS " + count + " dynamic-widget boundary checks");
    }
}
