package dev.qawalah.sunlighn;

/**
 * Sun and moon positions and the "when do they line up with my line" search.
 * Plain Java, no Android classes, so it can be tested on any computer.
 *
 * Accuracy: sun about 0.01 degrees, moon about 0.1 degrees (Schlyter's
 * low-precision series plus parallax). Enough for a photograph.
 */
final class Astro {
    private Astro() {}

    /** One moment when the body sits exactly on the line. */
    static final class Hit {
        final long timeMs;
        final double alt;  // degrees above the horizon
        final double lit;  // moon: fraction lit 0..1, sun: 1
        Hit(long timeMs, double alt, double lit) {
            this.timeMs = timeMs;
            this.alt = alt;
            this.lit = lit;
        }
    }

    /** What the search found for one body. Either field can be null. */
    static final class Found {
        /** The soonest time the body is on the line and above the horizon. */
        Hit next;
        /** The soonest time it is on the line, low in the sky (and bright, for the moon). */
        Hit low;
    }

    static final double LOW_ALT = 8;         // "low" means up to this many degrees up
    static final double MOON_BRIGHT = 0.6;   // "bright" moon for the low row
    static final int SEARCH_DAYS = 400;
    private static final double MIN_ALT = -1; // just below the horizon still counts (sunrise/set)
    private static final long STEP_MS = 3 * 60 * 1000L;

    // ---- geometry ----

    /** Initial compass bearing (0..360, north = 0) from point 1 to point 2. */
    static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return norm360(Math.toDegrees(Math.atan2(y, x)));
    }

    /** Distance in metres. */
    static double distance(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static String compass(double bearing) {
        String[] names = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return names[(int) Math.round(norm360(bearing) / 22.5) % 16];
    }

    // ---- positions: each returns {azimuth, altitude} in degrees, azimuth from north clockwise ----

    static double[] sun(long ms, double lat, double lon) {
        double jd = jd(ms);
        double[] s = sunEcliptic(jd - 2451543.5);
        double lonS = Math.toRadians(s[0]);
        double[] v = {s[1] * Math.cos(lonS), s[1] * Math.sin(lonS), 0};
        double[] eq = toEquatorial(v, jd - 2451543.5);
        for (int i = 0; i < 3; i++) eq[i] *= 23454.78; // AU to Earth radii
        return horizontal(eq, jd, lat, lon);
    }

    static double[] moon(long ms, double lat, double lon) {
        double jd = jd(ms);
        double d = jd - 2451543.5;
        double[] m = moonEcliptic(d);
        double lm = Math.toRadians(m[0]), bm = Math.toRadians(m[1]);
        double[] v = {m[2] * Math.cos(bm) * Math.cos(lm), m[2] * Math.cos(bm) * Math.sin(lm), m[2] * Math.sin(bm)};
        return horizontal(toEquatorial(v, d), jd, lat, lon);
    }

    /** Fraction of the moon's disc that is lit, 0..1. */
    static double moonLit(long ms) {
        double d = jd(ms) - 2451543.5;
        double[] m = moonEcliptic(d);
        double[] s = sunEcliptic(d);
        double psi = Math.acos(Math.cos(Math.toRadians(m[1])) * Math.cos(Math.toRadians(m[0] - s[0])));
        return (1 - Math.cos(psi)) / 2;
    }

    // ---- the search ----

    /**
     * Looks ahead from startMs for the times the body's azimuth equals the line's bearing.
     * Gives up after SEARCH_DAYS. Meant to run on a background thread.
     */
    static Found search(boolean moon, double lat, double lon, double bearing, long startMs) {
        Found f = new Found();
        scan(moon, lat, lon, bearing, startMs, startMs + SEARCH_DAYS * 86400000L, h -> {
            if (f.next == null) f.next = h;
            if (f.low == null && isLow(h, moon)) f.low = h;
            return f.low == null;
        });
        return f;
    }

    /** Every time the body is on the line and up, from startMs to endMs, in order. For the trip plan. */
    static java.util.List<Hit> searchAll(boolean moon, double lat, double lon, double bearing, long startMs, long endMs) {
        java.util.List<Hit> out = new java.util.ArrayList<>();
        scan(moon, lat, lon, bearing, startMs, endMs, h -> {
            out.add(h);
            return true;
        });
        return out;
    }

    /** "Low" as the app means it: up to LOW_ALT degrees, and for the moon, bright. */
    static boolean isLow(Hit h, boolean moon) {
        return h.alt <= LOW_ALT && (!moon || h.lit >= MOON_BRIGHT);
    }

    private interface OnHit { boolean more(Hit h); }

    private static void scan(boolean moon, double lat, double lon, double bearing, long startMs, long endMs, OnHit cb) {
        long t0 = startMs;
        double d0 = diff(moon, t0, lat, lon, bearing);
        // Steps sit on a fixed grid, so the same moment comes out at the same millisecond whatever the start
        // (the trip and the answer card then agree, and a reminder set in one shows in the other).
        for (long t1 = (startMs / STEP_MS + 1) * STEP_MS; t1 <= endMs; t1 += STEP_MS) {
            double d1 = diff(moon, t1, lat, lon, bearing);
            // A real crossing changes sign while both sides are close; a jump across +-180 does not count.
            if (d0 != d1 && (d0 <= 0 && d1 > 0 || d0 >= 0 && d1 < 0) && Math.abs(d0) < 30 && Math.abs(d1) < 30) {
                long a = t0, b = t1;
                for (int i = 0; i < 18; i++) {
                    long mid = (a + b) / 2;
                    double dm = diff(moon, mid, lat, lon, bearing);
                    if ((dm <= 0) == (d0 <= 0)) a = mid; else b = mid;
                }
                long t = (a + b) / 2;
                double[] p = moon ? moon(t, lat, lon) : sun(t, lat, lon);
                if (p[1] >= MIN_ALT && t >= startMs && t <= endMs) {
                    if (!cb.more(new Hit(t, Math.max(p[1], 0), moon ? moonLit(t) : 1))) return;
                }
            }
            t0 = t1;
            d0 = d1;
        }
    }

    // ---- where to stand ----

    static final double SUGGEST_ALT = 4; // the low sun the "where to stand" suggestion aims for

    /**
     * The next time after startMs the sun passes alt degrees, going down (evening) or up (morning).
     * Looks two days ahead; returns -1 if it never does (polar day or night).
     */
    static long sunPasses(double lat, double lon, double alt, boolean evening, long startMs) {
        long end = startMs + 2 * 86400000L;
        long t0 = startMs;
        double a0 = sun(t0, lat, lon)[1] - alt;
        for (long t1 = startMs + STEP_MS; t1 <= end; t1 += STEP_MS) {
            double a1 = sun(t1, lat, lon)[1] - alt;
            if (evening ? a0 > 0 && a1 <= 0 : a0 < 0 && a1 >= 0) {
                long a = t0, b = t1;
                for (int i = 0; i < 18; i++) {
                    long mid = (a + b) / 2;
                    double am = sun(mid, lat, lon)[1] - alt;
                    if ((am > 0) == (a0 > 0)) a = mid; else b = mid;
                }
                return (a + b) / 2;
            }
            t0 = t1;
            a0 = a1;
        }
        return -1;
    }

    /**
     * The next time the sun or moon crosses a height (degrees) after startMs: going down (setting) or up (rising).
     * Looks three days ahead; -1 if it never does (polar day or night).
     */
    static long bodyPasses(boolean moon, double lat, double lon, double alt, boolean setting, long startMs) {
        long end = startMs + 3 * 86400000L;
        long t0 = startMs;
        double a0 = (moon ? moon(t0, lat, lon) : sun(t0, lat, lon))[1] - alt;
        for (long t1 = startMs + STEP_MS; t1 <= end; t1 += STEP_MS) {
            double a1 = (moon ? moon(t1, lat, lon) : sun(t1, lat, lon))[1] - alt;
            if (setting ? a0 > 0 && a1 <= 0 : a0 < 0 && a1 >= 0) {
                long a = t0, b = t1;
                for (int i = 0; i < 18; i++) {
                    long mid = (a + b) / 2;
                    double am = (moon ? moon(mid, lat, lon) : sun(mid, lat, lon))[1] - alt;
                    if ((am > 0) == (a0 > 0)) a = mid; else b = mid;
                }
                return (a + b) / 2;
            }
            t0 = t1;
            a0 = a1;
        }
        return -1;
    }

    /** The next {@code count} times the sun or moon crosses a height, one per day or so. Meant for a background thread. */
    static java.util.List<Hit> upcomingPasses(boolean moon, double lat, double lon, double alt, boolean setting,
                                              long startMs, int count) {
        java.util.List<Hit> out = new java.util.ArrayList<>();
        long from = startMs;
        for (int i = 0; i < count; i++) {
            long t = bodyPasses(moon, lat, lon, alt, setting, from);
            if (t < 0) break;
            out.add(new Hit(t, alt, moon ? moonLit(t) : 1));
            from = t + 12 * 3600000L;   // the next one is a day away
        }
        return out;
    }

    /** The point {lat, lon} that lies metres away from (lat, lon) in the given compass bearing. */
    static double[] destination(double lat, double lon, double bearing, double metres) {
        double d = metres / 6371000, th = Math.toRadians(bearing);
        double p1 = Math.toRadians(lat), l1 = Math.toRadians(lon);
        double p2 = Math.asin(Math.sin(p1) * Math.cos(d) + Math.cos(p1) * Math.sin(d) * Math.cos(th));
        double l2 = l1 + Math.atan2(Math.sin(th) * Math.sin(d) * Math.cos(p1), Math.cos(d) - Math.sin(p1) * Math.sin(p2));
        return new double[]{Math.toDegrees(p2), ((Math.toDegrees(l2) + 540) % 360) - 180};
    }

    /** Every time between two instants that the body is on the line and above the horizon (a day's worth). */
    static java.util.List<Hit> hitsBetween(boolean moon, double lat, double lon, double bearing, long fromMs, long toMs) {
        java.util.List<Hit> out = new java.util.ArrayList<>();
        long t0 = fromMs;
        double d0 = diff(moon, t0, lat, lon, bearing);
        for (long t1 = fromMs + STEP_MS; t1 <= toMs; t1 += STEP_MS) {
            double d1 = diff(moon, t1, lat, lon, bearing);
            if (d0 != d1 && (d0 <= 0 && d1 > 0 || d0 >= 0 && d1 < 0) && Math.abs(d0) < 30 && Math.abs(d1) < 30) {
                long a = t0, b = t1;
                for (int i = 0; i < 18; i++) {
                    long mid = (a + b) / 2;
                    double dm = diff(moon, mid, lat, lon, bearing);
                    if ((dm <= 0) == (d0 <= 0)) a = mid; else b = mid;
                }
                long t = (a + b) / 2;
                double[] pos = moon ? moon(t, lat, lon) : sun(t, lat, lon);
                if (pos[1] >= MIN_ALT) out.add(new Hit(t, Math.max(pos[1], 0), moon ? moonLit(t) : 1));
            }
            t0 = t1;
            d0 = d1;
        }
        return out;
    }

    /** True while the moon grows towards full (the right side is lit as seen from the northern hemisphere). */
    static boolean moonWaxing(long ms) {
        double d = jd(ms) - 2451543.5;
        return norm360(moonEcliptic(d)[0] - sunEcliptic(d)[0]) < 180;
    }

    private static double diff(boolean moon, long t, double lat, double lon, double bearing) {
        double az = (moon ? moon(t, lat, lon) : sun(t, lat, lon))[0];
        double x = norm360(az - bearing);
        return x > 180 ? x - 360 : x;
    }

    // ---- internals ----

    private static double jd(long ms) {
        return ms / 86400000.0 + 2440587.5;
    }

    static double norm360(double x) {
        x %= 360;
        return x < 0 ? x + 360 : x;
    }

    private static double sind(double x) { return Math.sin(Math.toRadians(x)); }
    private static double cosd(double x) { return Math.cos(Math.toRadians(x)); }

    /** {ecliptic longitude deg, distance AU} of the sun; d = days since 1999-12-31 0h UT. */
    private static double[] sunEcliptic(double d) {
        double w = 282.9404 + 4.70935e-5 * d;
        double e = 0.016709 - 1.151e-9 * d;
        double M = norm360(356.0470 + 0.9856002585 * d);
        double E = M + Math.toDegrees(e * sind(M) * (1 + e * cosd(M)));
        double xv = cosd(E) - e;
        double yv = Math.sqrt(1 - e * e) * sind(E);
        double v = Math.toDegrees(Math.atan2(yv, xv));
        return new double[]{norm360(v + w), Math.hypot(xv, yv)};
    }

    /** {ecliptic longitude deg, latitude deg, distance in Earth radii} of the moon. */
    private static double[] moonEcliptic(double d) {
        double N = norm360(125.1228 - 0.0529538083 * d);
        double i = 5.1454;
        double w = norm360(318.0634 + 0.1643573223 * d);
        double a = 60.2666;
        double e = 0.054900;
        double Mm = norm360(115.3654 + 13.0649929509 * d);
        double E = Mm + Math.toDegrees(e * sind(Mm) * (1 + e * cosd(Mm)));
        for (int k = 0; k < 5; k++) {
            E = E - (E - Math.toDegrees(e * sind(E)) - Mm) / (1 - e * cosd(E));
        }
        double xv = a * (cosd(E) - e);
        double yv = a * Math.sqrt(1 - e * e) * sind(E);
        double v = Math.toDegrees(Math.atan2(yv, xv));
        double r = Math.hypot(xv, yv);
        double vw = v + w;
        double xh = r * (cosd(N) * cosd(vw) - sind(N) * sind(vw) * cosd(i));
        double yh = r * (sind(N) * cosd(vw) + cosd(N) * sind(vw) * cosd(i));
        double zh = r * sind(vw) * sind(i);
        double lon = Math.toDegrees(Math.atan2(yh, xh));
        double lat = Math.toDegrees(Math.atan2(zh, Math.hypot(xh, yh)));

        // Sun's mean values, needed for the corrections.
        double Ms = norm360(356.0470 + 0.9856002585 * d);
        double ws = 282.9404 + 4.70935e-5 * d;
        double Ls = norm360(Ms + ws);
        double Lm = norm360(Mm + w + N);
        double D = Lm - Ls;
        double F = Lm - N;

        lon += -1.274 * sind(Mm - 2 * D)
                + 0.658 * sind(2 * D)
                - 0.186 * sind(Ms)
                - 0.059 * sind(2 * Mm - 2 * D)
                - 0.057 * sind(Mm - 2 * D + Ms)
                + 0.053 * sind(Mm + 2 * D)
                + 0.046 * sind(2 * D - Ms)
                + 0.041 * sind(Mm - Ms)
                - 0.035 * sind(D)
                - 0.031 * sind(Mm + Ms)
                - 0.015 * sind(2 * F - 2 * D)
                + 0.011 * sind(Mm - 4 * D);
        lat += -0.173 * sind(F - 2 * D)
                - 0.055 * sind(Mm - F - 2 * D)
                - 0.046 * sind(Mm + F - 2 * D)
                + 0.033 * sind(F + 2 * D)
                + 0.017 * sind(2 * Mm + F);
        r += -0.58 * cosd(Mm - 2 * D) - 0.46 * cosd(2 * D);
        return new double[]{norm360(lon), lat, r};
    }

    /** Ecliptic rectangular vector to equatorial. */
    private static double[] toEquatorial(double[] v, double d) {
        double ecl = 23.4393 - 3.563e-7 * d;
        return new double[]{v[0], v[1] * cosd(ecl) - v[2] * sind(ecl), v[1] * sind(ecl) + v[2] * cosd(ecl)};
    }

    /** Equatorial vector in Earth radii to {azimuth, altitude} as seen from the observer (includes parallax). */
    private static double[] horizontal(double[] eq, double jd, double lat, double lon) {
        double gmst = norm360(280.46061837 + 360.98564736629 * (jd - 2451545.0));
        double th = Math.toRadians(gmst + lon);
        double X = eq[0] * Math.cos(th) + eq[1] * Math.sin(th);
        double Y = eq[0] * Math.sin(th) - eq[1] * Math.cos(th); // towards the west
        double Z = eq[2];
        // Observer on the slightly flattened Earth.
        double latGeo = lat - 0.1924 * sind(2 * lat);
        double rho = 0.99833 + 0.00167 * cosd(2 * lat);
        X -= rho * cosd(latGeo);
        Z -= rho * sind(latGeo);
        double south = X * sind(lat) - Z * cosd(lat);
        double up = X * cosd(lat) + Z * sind(lat);
        double az = norm360(Math.toDegrees(Math.atan2(Y, south)) + 180);
        double alt = Math.toDegrees(Math.atan2(up, Math.hypot(south, Y)));
        return new double[]{az, alt};
    }
}
