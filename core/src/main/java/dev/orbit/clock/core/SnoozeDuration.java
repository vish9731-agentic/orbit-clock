package dev.orbit.clock.core;

/** Limits for the ringing screen; the saved alarm's default is not edited. */
public final class SnoozeDuration {
    public static final int MIN = 5;
    public static final int MAX = 60;
    public static final int STEP = 5;
    private SnoozeDuration() {}
    /** Keep clock alarms on the delivered occurrence's minute grid, never action-time seconds. */
    public static long nextAlarm(long occurrence, long now, int minutes) {
        long step = clamp(minutes) * 60_000L;
        long anchor = Math.floorDiv(occurrence, 60_000L) * 60_000L;
        long slots = Math.max(1L, Math.floorDiv(now - anchor, step) + 1L);
        return anchor + slots * step;
    }
    public static int clamp(int minutes) { return Math.max(MIN, Math.min(MAX, minutes)); }
    public static int increase(int minutes) { return clamp(clamp(minutes) + STEP); }
    public static int decrease(int minutes) { return clamp(clamp(minutes) - STEP); }
}
