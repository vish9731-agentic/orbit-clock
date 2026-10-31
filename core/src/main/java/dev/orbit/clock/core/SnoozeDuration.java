package dev.orbit.clock.core;

/** Limits for the ringing screen; the saved alarm's default is not edited. */
public final class SnoozeDuration {
    public static final int MIN = 5;
    public static final int MAX = 60;
    public static final int STEP = 5;
    private SnoozeDuration() {}
    public static int clamp(int minutes) { return Math.max(MIN, Math.min(MAX, minutes)); }
    public static int increase(int minutes) { return clamp(clamp(minutes) + STEP); }
    public static int decrease(int minutes) { return clamp(clamp(minutes) - STEP); }
}
