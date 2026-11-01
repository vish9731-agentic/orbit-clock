package dev.orbit.clock.core;

/** Sub-black local-time colors, smoothly interpolated through a 24-hour cycle. */
public final class RingAtmosphere {
    private RingAtmosphere() {}
    private static final int[] MINUTES = {0, 240, 420, 600, 840, 1110, 1320, 1440};
    private static final int[] COLORS = {0xff030509, 0xff05070c, 0xff120e10, 0xff0b100e,
        0xff141108, 0xff120b14, 0xff080c13, 0xff030509};
    public static int tint(int minute) {
        int m = Math.floorMod(minute, 1440);
        int i = 0;
        while (i < MINUTES.length - 2 && m >= MINUTES[i + 1]) i++;
        float f = (float)(m - MINUTES[i]) / (MINUTES[i + 1] - MINUTES[i]);
        int color = 0xff000000;
        for (int shift : new int[]{16, 8, 0}) {
            int a = COLORS[i] >> shift & 255, b = COLORS[i + 1] >> shift & 255;
            color |= Math.round(a + (b-a)*f) << shift;
        }
        return color;
    }
}
