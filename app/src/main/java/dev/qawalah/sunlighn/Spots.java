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
        /** 3.24.3: how far from the subject this day's spot is, and the stretch of the day's spots (near to far). */
        double dist;
        double[] near, far;      // {lat, lon} of the nearest and farthest spot that works this day
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

    /**
     * 3.24.3 (owner: "display possible distances, not only an arc: like a pie"; "the lens doesn't change anything"): what
     * the photographer asks. Heights from {@code lo} to {@code hi} degrees; distances from {@code dMin} to {@code dMax}
     * metres (from the lens range, or set by the user). For Middle and On top the height gives the distance, so each
     * height in the range is a spot nearer or farther; on the horizon or behind you any distance in the range will do.
     */
    static final class Ask {
        int sits = ON_TOP;
        boolean behindYou;
        double height = 30, lo = 3, hi = 6, dMin = NEAREST, dMax = FARTHEST;

        /** Does the height decide the distance (Middle, On top, behind the subject)? */
        boolean heightSetsDistance() { return !behindYou && (sits == MIDDLE || sits == ON_TOP); }

        /** The distances that can work, {near, far}; near > far when none can. */
        double[] works() {
            if (!heightSetsDistance()) return new double[]{dMin, dMax};
            double near = distanceFor(sits, height, hi), far = distanceFor(sits, height, lo);
            return new double[]{Math.max(dMin, near), Math.min(dMax, far)};
        }
    }

    /** The heights looked at in the range, five across it (one when the range is a point). */
    private static double[] heights(double lo, double hi) {
        if (hi - lo < 0.05) return new double[]{(lo + hi) / 2};
        double[] h = new double[5];
        for (int i = 0; i < 5; i++) h[i] = lo + (hi - lo) * i / 4;
        return h;
    }

    /**
     * Every day from {@code from}: the body at each height in the range (going down, or up for {@code rises}), the spot
     * that height asks for, kept when its distance is in range. A day's answer is its middle height that works; the
     * day's nearest and farthest spots make the pie on the map.
     */
    static List<Spot> find(boolean moon, double lat, double lon, Ask ask, boolean rises, long from, int days, double minLit) {
        List<Spot> out = new ArrayList<>();
        double[] hs = heights(ask.lo, ask.hi);
        int n = (int) (DAY / STEP) + 1;
        double[] alt = new double[n];
        boolean setsD = ask.heightSetsDistance();
        double pick = Math.sqrt(ask.dMin * ask.dMax);        // the middle of the range, on a log scale
        for (int d = 0; d < days; d++) {
            long a = from + d * DAY;
            for (int i = 0; i < n; i++) alt[i] = pos(moon, a + i * STEP, lat, lon)[1];
            List<double[]> ok = new ArrayList<>();           // {time, height, az, dist}
            for (double h : hs) {
                long t = crossing(moon, lat, lon, h, rises, a, alt);
                if (t < 0) continue;
                double dist = setsD ? distanceFor(ask.sits, ask.height, h) : pick;
                if (setsD && (dist < ask.dMin - 1e-6 || dist > ask.dMax + 1e-6)) continue;
                ok.add(new double[]{t, h, pos(moon, t, lat, lon)[0], dist});
            }
            if (ok.isEmpty()) continue;
            double[] mid = ok.get(ok.size() / 2);
            long t = (long) mid[0];
            double lit = moon ? Astro.moonLit(t) : 1;
            if (moon && lit < minLit) continue;
            double bearing = ask.behindYou ? mid[2] : Astro.norm360(mid[2] + 180);
            double[] at = Astro.destination(lat, lon, bearing, mid[3]);
            Spot sp = new Spot(t, mid[1], mid[2], bearing, at[0], at[1], lit, rises);
            sp.dist = mid[3];
            if (setsD) {
                double[] nearest = ok.get(0), farthest = ok.get(0);
                for (double[] o : ok) {
                    if (o[3] < nearest[3]) nearest = o;
                    if (o[3] > farthest[3]) farthest = o;
                }
                sp.near = Astro.destination(lat, lon, ask.behindYou ? nearest[2] : Astro.norm360(nearest[2] + 180), nearest[3]);
                sp.far = Astro.destination(lat, lon, ask.behindYou ? farthest[2] : Astro.norm360(farthest[2] + 180), farthest[3]);
            } else {
                sp.near = Astro.destination(lat, lon, bearing, ask.dMin);
                sp.far = Astro.destination(lat, lon, bearing, ask.dMax);
            }
            out.add(sp);
        }
        return out;
    }

    /** The crossing of {@code alt} in a day sampled every 10 minutes from {@code a} (samples given), halved to the minute. */
    private static long crossing(boolean moon, double lat, double lon, double h, boolean rises, long a, double[] alt) {
        for (int i = 1; i < alt.length; i++) {
            double h0 = alt[i - 1] - h, h1 = alt[i] - h;
            if (rises ? h0 < 0 && h1 >= 0 : h0 > 0 && h1 <= 0) {
                long lo = a + (i - 1) * STEP, hi = a + i * STEP;
                for (int k = 0; k < 12; k++) {
                    long m = (lo + hi) / 2;
                    double hm = pos(moon, m, lat, lon)[1] - h;
                    if ((hm > 0) == (h0 > 0)) lo = m; else hi = m;
                }
                return (lo + hi) / 2;
            }
        }
        return -1;
    }

    /**
     * How far away the subject is a third of the picture's height with this lens (the framing the app aims for), metres:
     * the lens range gives the distances that can frame it so (3.24.3).
     */
    static double distanceForLens(double height, double focal, boolean portrait) {
        double top = FrameMath.fovV(focal, portrait) / 3;
        return Math.max(NEAREST, Math.min(FARTHEST, distanceFor(ON_TOP, height, top + FrameMath.BODY_RADIUS)));
    }

    /** A focal length as a phone's zoom: 1x is about 24 mm on most phones (main camera), 3x about 72 mm, 5x about 120 mm. */
    static String phoneZoom(double mm) {
        double x = mm / 24.0;
        return (x < 1 ? String.format(java.util.Locale.US, "%.1fx", x) : x < 10 && Math.abs(x - Math.round(x)) > 0.05
                ? String.format(java.util.Locale.US, "%.1fx", x) : Math.round(x) + "x");
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

    /** The nearest and farthest distances looked at, metres. */
    static final double NEAREST = 20, FARTHEST = 20000;

    /**
     * 3.24.1 (owner: "distance should be a driven variable unless fixed by user"): how far from the subject to stand
     * so the body {@code alt} degrees up sits where asked on it (Middle, On top; level ground, your eye 1.6 m up). The
     * higher the body, the closer. Kept between {@link #NEAREST} and {@link #FARTHEST}: at the ends the body cannot be
     * that high (too close) or that low (too far) on this subject; {@link #targetAlt} says where it is then.
     */
    static double distanceFor(int sits, double height, double alt) {
        double lo = Math.log(NEAREST), hi = Math.log(FARTHEST);
        if (FrameMath.targetAlt(sits, height, NEAREST, -FrameMath.EYE) <= alt) return NEAREST;
        if (FrameMath.targetAlt(sits, height, FARTHEST, -FrameMath.EYE) >= alt) return FARTHEST;
        for (int i = 0; i < 40; i++) {                    // the height falls as the distance grows
            double mid = (lo + hi) / 2;
            if (FrameMath.targetAlt(sits, height, Math.exp(mid), -FrameMath.EYE) > alt) lo = mid; else hi = mid;
        }
        double d = Math.exp((lo + hi) / 2);
        return d < 1000 ? Math.round(d) : Math.round(d / 10) * 10.0;
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
    static final double[] STOPS = {14, 16, 20, 24, 28, 35, 48, 50, 72, 85, 105, 120, 135, 200, 240, 300, 400};   // 3.24.3: a phone's 0.5x, 1x, 2x, 3x, 5x, 10x among them
}
