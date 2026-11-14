package dev.qawalah.sunlighn;

/** What the light is like, from how high the sun is. The names photographers use. */
final class Light {
    private Light() {}

    enum Kind { NIGHT, TWILIGHT, BLUE, GOLDEN, DAY }

    static final double GOLDEN_TOP = 6, GOLDEN_BOTTOM = -4, BLUE_BOTTOM = -6, NIGHT_TOP = -18;

    static Kind of(double sunAlt) {
        if (sunAlt > GOLDEN_TOP) return Kind.DAY;
        if (sunAlt >= GOLDEN_BOTTOM) return Kind.GOLDEN;
        if (sunAlt >= BLUE_BOTTOM) return Kind.BLUE;
        if (sunAlt >= NIGHT_TOP) return Kind.TWILIGHT;
        return Kind.NIGHT;
    }

    static String name(Kind k) {
        switch (k) {
            case DAY: return "Daylight";
            case GOLDEN: return "Golden hour";
            case BLUE: return "Blue hour";
            case TWILIGHT: return "Twilight";
            default: return "Night";
        }
    }

    /** One colour per light, the same on the time ruler, the Day chart and the answers. */
    static int color(Kind k) {
        switch (k) {
            case DAY: return 0xFF7CC4F8;
            case GOLDEN: return 0xFFF59E0B;
            case BLUE: return 0xFF3B62D9;
            case TWILIGHT: return 0xFF27336B;
            default: return 0xFF0E1530;
        }
    }
}
