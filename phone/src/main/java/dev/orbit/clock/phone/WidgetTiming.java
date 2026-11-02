package dev.orbit.clock.phone;

/** Pure boundary policy shared by both widget sizes. Epoch milliseconds, local dates computed by caller. */
public final class WidgetTiming {
    public static final long BEDTIME_GRACE = 15 * 60_000L;
    private WidgetTiming() {}
    public static int window(int minutes) {
        for (int allowed : new int[]{10, 15, 30, 45, 60, 90}) if (minutes == allowed) return minutes;
        return 15;
    }
    public static String kind(long now, long next, boolean ringing, long bed, long alarmWindow, long bedWindow) {
        if (ringing) return "ringing";
        if (next > now && next - now <= alarmWindow) return "alarm";
        if (bed > now && bed - now <= bedWindow) return "bedtime";
        if (bed > 0 && now >= bed && now - bed < BEDTIME_GRACE) return "bednow";
        return "clock";
    }
}
