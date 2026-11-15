package dev.qawalah.sunlighn;

/**
 * Where the sun or moon can be in a picture you build, and where it cannot. Too high for the days chosen, or
 * (on foot) every spot it would need is on water or inside a building. Plain Java, tested; meant for a
 * background thread.
 */
final class Sky {
    private Sky() {}

    /** The highest whole degree looked at: a picture with the sun higher than this is not this app's business. */
    static final int TOP = 60;
    private static final long STEP = 20 * 60000L;
    private static final int SAMPLES = 6;       // days looked at per height for the ground

    /** The highest the body gets in these days, degrees. */
    static double highest(boolean moon, double lat, double lon, long fromMs, int days) {
        double best = -90;
        long end = fromMs + days * 86400000L;
        for (long t = fromMs; t < end; t += STEP) {
            double alt = (moon ? Astro.moon(t, lat, lon) : Astro.sun(t, lat, lon))[1];
            if (alt > best) best = alt;
        }
        return best;
    }

    /**
     * Per whole degree of height, 0 to TOP: can the body be there, rising or setting, on some day in these days?
     * The subject is metres away; offDeg is how far right of the subject the body sits in the picture (you stand
     * that much off the line). Ground: unknown counts as possible.
     */
    static boolean[] possible(boolean moon, boolean setting, double lat, double lon, long fromMs, int days,
                              double metres, double offDeg, boolean drone, Ground.Fn ground) {
        boolean[] out = new boolean[TOP + 1];
        double top = highest(moon, lat, lon, fromMs, days);
        long span = Math.max(1, days) * 86400000L;
        for (int a = 0; a <= TOP; a++) {
            double alt = Math.max(FrameMath.LOWEST, a);
            if (alt > top) continue;                 // never that high in these days
            if (drone) {
                out[a] = true;
                continue;
            }
            boolean found = false, any = false;
            for (int i = 0; i < SAMPLES && !any; i++) {
                long t = Astro.bodyPasses(moon, lat, lon, alt, setting, fromMs + span * i / SAMPLES);
                if (t < 0 || t > fromMs + span) continue;
                found = true;
                double az = (moon ? Astro.moon(t, lat, lon) : Astro.sun(t, lat, lon))[0];
                double[] at = Astro.destination(lat, lon, Astro.norm360(az - offDeg + 180), metres);
                int g = ground.at(at[0], at[1]);
                if (g != Ground.WATER && g != Ground.BUILDING) any = true;
            }
            out[a] = any || !found;              // no day sampled: the highest says it gets there, so allow it
        }
        return out;
    }
}
