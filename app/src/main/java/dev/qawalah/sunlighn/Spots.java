package dev.qawalah.sunlighn;

import java.util.ArrayList;
import java.util.List;

/**
 * Find the spot (3.24, owner 2026-11-22: "define the subject and where you want the sun height to be, the app tells you
 * all possible locations and dates"). For each day ahead, the moment the sun or moon passes the height the photographer
 * asked for, the way it stands then, and the spot that puts it behind the subject (or behind you) at the distance
 * chosen. Days in a row whose spots lie close together are one run. Plain Java, tested; meant for a background thread.
 */
final class Spots {
    private Spots() {}

    /** Where the body sits: on the horizon, behind the middle of the subject, just on top, or a height from the slider. */
    static final int HORIZON = 0, MIDDLE = 1, ON_TOP = 2, HEIGHT = 3;
    /** Which side of the day: both (grouped), setting, rising. */
    static final int ANY = 0, SETS = 1, RISES = 2;

    static final long DAY = 86400000L;
    private static final long STEP = 10 * 60000L;

    /** One day's answer: when, how high, where the body is, and where to stand (from the subject: bearing, lat, lon). */
    static final class Spot {
        final long timeMs;
        final double alt, bodyAz, bearing, lat, lon, lit;
        final boolean rises;
        Spot(long timeMs, double alt, double bodyAz, double bearing, double lat, double lon, double lit, boolean rises) {
            this.timeMs = timeMs;
            this.alt = alt;
            this.bodyAz = bodyAz;
            this.bearing = bearing;
            this.lat = lat;
            this.lon = lon;
            this.lit = lit;
            this.rises = rises;
        }
    }

    /** Days in a row with about the same spot: one row of the list, one cluster on the map. */
    static final class Run {
        final List<Spot> days = new ArrayList<>();
        final boolean rises;
        Run(boolean rises) { this.rises = rises; }
        Spot first() { return days.get(0); }
        Spot last() { return days.get(days.size() - 1); }
    }

    /**
     * How high the body must be: for the three keys, from the subject's height and the distance on level ground (a
     * subject seen from your eye, 1.6 m up); for a height picked on the slider, the middle of its range.
     */
    static double targetAlt(int sits, double height, double dist, double lo, double hi) {
        if (sits == HEIGHT) return (lo + hi) / 2;
        return FrameMath.targetAlt(sits, height, dist, -FrameMath.EYE);
    }

    /**
     * Every day from {@code from} for {@code days} days: the moment the body passes {@code alt} degrees going down (or up,
     * {@code rises}), and the spot {@code dist} metres from the subject that puts it behind the subject (or, {@code
     * behindYou}, behind the photographer, lighting the subject). Days it never gets that high are left out; a moon
     * less lit than {@code minLit} too.
     */
    static List<Spot> find(boolean moon, double lat, double lon, double alt, double dist, boolean behindYou, boolean rises,
                           long from, int days, double minLit) {
        List<Spot> out = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            long a = from + d * DAY, b = a + DAY;
            long t = crossing(moon, lat, lon, alt, rises, a, b);
            if (t < 0) continue;
            double lit = moon ? Astro.moonLit(t) : 1;
            if (moon && lit < minLit) continue;
            double az = pos(moon, t, lat, lon)[0];
            // Behind the subject: you stand on the far side from the body. Behind you: on its side.
            double bearing = behindYou ? az : Astro.norm360(az + 180);
            double[] at = Astro.destination(lat, lon, bearing, dist);
            out.add(new Spot(t, alt, az, bearing, at[0], at[1], lit, rises));
        }
        return out;
    }

    /** The first time in [a, b) the body passes alt in the asked direction, or -1. Sampled every 10 minutes, then halved. */
    static long crossing(boolean moon, double lat, double lon, double alt, boolean rises, long a, long b) {
        long t0 = a;
        double h0 = pos(moon, t0, lat, lon)[1] - alt;
        for (long t1 = a + STEP; t1 <= b; t1 += STEP) {
            double h1 = pos(moon, t1, lat, lon)[1] - alt;
            if (rises ? h0 < 0 && h1 >= 0 : h0 > 0 && h1 <= 0) {
                long lo = t0, hi = t1;
                for (int i = 0; i < 16; i++) {
                    long mid = (lo + hi) / 2;
                    double hm = pos(moon, mid, lat, lon)[1] - alt;
                    if ((hm > 0) == (h0 > 0)) lo = mid; else hi = mid;
                }
                return (lo + hi) / 2;
            }
            t0 = t1;
            h0 = h1;
        }
        return -1;
    }

    private static double[] pos(boolean moon, long t, double lat, double lon) {
        return moon ? Astro.moon(t, lat, lon) : Astro.sun(t, lat, lon);
    }

    /**
     * Days in a row (no more than a day and a half apart) whose spots turn less than {@code turn} degrees round the
     * subject from the run's first, up to {@code maxDays} days and never across a month (the list is by month): one run.
     * The order is kept.
     */
    static List<Run> runs(List<Spot> spots, double turn, int maxDays, java.util.TimeZone tz) {
        List<Run> out = new ArrayList<>();
        Run r = null;
        java.util.Calendar cal = java.util.Calendar.getInstance(tz);
        int month = -1;
        for (Spot s : spots) {
            cal.setTimeInMillis(s.timeMs);
            int m = cal.get(java.util.Calendar.YEAR) * 12 + cal.get(java.util.Calendar.MONTH);
            boolean join = r != null && r.rises == s.rises && r.days.size() < maxDays && m == month
                    && s.timeMs - r.last().timeMs < 36 * 3600000L
                    && Math.abs(FrameMath.diff(s.bearing, r.first().bearing)) <= turn;
            month = m;
            if (!join) {
                r = new Run(s.rises);
                out.add(r);
            }
            r.days.add(s);
        }
        return out;
    }

    /** The run whose days include the one nearest this spot on the map (metres), or -1 when none is within reach. */
    static int nearestRun(List<Run> runs, double lat, double lon, double reach) {
        int best = -1;
        double bestD = reach;
        for (int i = 0; i < runs.size(); i++) {
            for (Spot s : runs.get(i).days) {
                double d = Astro.distance(lat, lon, s.lat, s.lon);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
        }
        return best;
    }

    /**
     * The lens in the range [lo, hi] mm for this subject at this distance: the one that makes it a third of the picture's
     * height, or the nearest end of the range.
     */
    static double lens(double lo, double hi, double height, double dist, boolean portrait) {
        double f = FrameMath.lensForThird(height, dist, portrait);
        return Math.max(lo, Math.min(hi, f));
    }

    /** How much of the picture's height the subject fills with this lens, 0 to 1 (more than 1: it does not fit). */
    static double fill(double focal, double height, double dist, boolean portrait) {
        return FrameMath.topAngle(height, dist) / FrameMath.fovV(focal, portrait);
    }

    /** The lens stops of the range slider (3.4.3's, back with Find the spot). */
    static final double[] STOPS = {14, 16, 20, 24, 28, 35, 50, 70, 85, 105, 135, 200, 300, 400};
}
