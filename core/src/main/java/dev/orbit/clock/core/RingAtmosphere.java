package dev.orbit.clock.core;

/** Local-time corner light. Policy is Android-free so day/wrap behaviour can be checked directly. */
public final class RingAtmosphere {
    private RingAtmosphere() {}

    public static final class Scene {
        public final String period;
        public final int glowColor, skyColor, sunColor;
        public final float cornerX, cornerY, sun, stars, moon;
        private Scene(String period, int glow, int sky, int sunlight, float x, float y,
                float sun, float stars, float moon) {
            this.period = period;
            glowColor = glow; skyColor = sky; sunColor = sunlight;
            cornerX = x; cornerY = y; this.sun = sun; this.stars = stars; this.moon = moon;
        }
    }

    public static final class Frame {
        public final int minute;
        public final Scene from, to;
        public final float blend;
        private Frame(int minute, Scene from, Scene to, float blend) {
            this.minute = minute; this.from = from; this.to = to; this.blend = blend;
        }
    }

    private static final Scene NIGHT = new Scene("night", 0xff283160, 0xff26366d,
        0xffacbce0, .96f, .018f, 0f, 1f, 1f);
    private static final Scene DAWN = new Scene("dawn", 0xffff9857, 0xff508bce,
        0xffffd490, .035f, .02f, .68f, 0f, 0f);
    private static final Scene MORNING = new Scene("morning", 0xffffd35c, 0xffc6ac57,
        0xffffefb1, .965f, .018f, 1f, 0f, 0f);
    private static final Scene NOON = new Scene("noon", 0xffc9dbde, 0xff3c9ce8,
        0xffffefb0, .965f, .025f, .92f, 0f, 0f);
    private static final Scene AFTERNOON = new Scene("afternoon", 0xffefb159, 0xff5d95a7,
        0xffffd391, .035f, .028f, .82f, 0f, 0f);
    private static final Scene SUNSET = new Scene("sunset", 0xffff702f, 0xffae466e,
        0xffffba74, .965f, .035f, .85f, 0f, 0f);
    private static final Scene BLUE_HOUR = new Scene("blue-hour", 0xff3a66ae, 0xff304b9b,
        0xff90b5ee, .035f, .018f, 0f, 0f, 0f);

    // Scene anchors are local wall time, never the original alarm's scheduled hour.
    private static final int[] MINUTES = {0, 180, 330, 480, 720, 900, 1080, 1260, 1380, 1440};
    private static final Scene[] SCENES = {NIGHT, NIGHT, DAWN, MORNING, NOON,
        AFTERNOON, SUNSET, BLUE_HOUR, NIGHT, NIGHT};

    public static Frame sample(int minute) {
        int m = Math.floorMod(minute, 1440);
        int i = 0;
        while (i < MINUTES.length - 2 && m >= MINUTES[i + 1]) i++;
        float t = (float)(m - MINUTES[i]) / (MINUTES[i + 1] - MINUTES[i]);
        float blend = t * t * (3f - 2f * t); // Zero slope at each anchor; no boundary flashes.
        return new Frame(m, SCENES[i], SCENES[i + 1], blend);
    }

    public static String period(int minute) {
        Frame frame = sample(minute);
        return (frame.blend < .5f ? frame.from : frame.to).period;
    }

    /** The untouched majority of the screen remains black; color lives only in the corner light. */
    public static int tint(int minute) { return 0xff030405; }
}
