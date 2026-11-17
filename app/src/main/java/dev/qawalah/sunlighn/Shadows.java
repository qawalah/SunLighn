package dev.qawalah.sunlighn;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shadows (3.17): the ground shadow of a standing thing, from where the sun (or the moon) is. Plain flat-ground
 * geometry in metres east and north of any origin: the shadow runs away from the body, as long as the height over the
 * tangent of the body's height above the horizon. The viewfinder and the 3D map draw what this gives.
 */
final class Shadows {
    private Shadows() {}

    /** Under this the shadow is too long to mean anything (the body is at the horizon). */
    static final double MIN_ALT = 1.0;
    /** No shadow is drawn longer than this, metres. */
    static final double MAX_LENGTH = 400;

    static boolean enabled = true;

    /** The setting (Settings, Shadows): on by default. */
    static boolean on(Context c) {
        return enabled && c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getBoolean("shadows", true);
    }

    static void set(Context c, boolean on) {
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putBoolean("shadows", on).apply();
    }

    /**
     * Where the tip of a shadow falls from its foot: {east, north} metres, away from the body; null when there is
     * none (the body at or below {@link #MIN_ALT}, or nothing standing).
     */
    static double[] vector(double bodyAz, double bodyAlt, double height) {
        if (!(height > 0) || !(bodyAlt >= MIN_ALT)) return null;
        double len = Math.min(MAX_LENGTH, height / Math.tan(Math.toRadians(bodyAlt)));
        double az = Math.toRadians(bodyAz);
        return new double[]{-Math.sin(az) * len, -Math.cos(az) * len};
    }

    /** How dark a shadow is, 0 to 1: fades out as the body comes down to the horizon; the moon's is faint. */
    static double strength(double bodyAlt, boolean moon) {
        double s = Math.max(0, Math.min(1, (bodyAlt - 0.5) / 7.5));
        return moon ? s * 0.3 : s;
    }

    /**
     * The shadow of a prism: the convex hull of its footprint and the footprint moved by (dE, dN). Exact for a convex
     * footprint, a little generous for others. Returns {east[], north[]} counter-clockwise.
     */
    static double[][] hull(double[] e, double[] n, double dE, double dN) {
        int m = e.length;
        double[][] pts = new double[2 * m][];
        for (int i = 0; i < m; i++) {
            pts[i] = new double[]{e[i], n[i]};
            pts[m + i] = new double[]{e[i] + dE, n[i] + dN};
        }
        Arrays.sort(pts, (a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        List<double[]> h = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = h.size();
            for (int i = 0; i < pts.length; i++) {
                double[] p = pts[pass == 0 ? i : pts.length - 1 - i];
                while (h.size() >= start + 2 && cross(h.get(h.size() - 2), h.get(h.size() - 1), p) <= 1e-9) h.remove(h.size() - 1);
                h.add(p);
            }
            h.remove(h.size() - 1);
        }
        double[] he = new double[h.size()], hn = new double[h.size()];
        for (int i = 0; i < he.length; i++) {
            he[i] = h.get(i)[0];
            hn[i] = h.get(i)[1];
        }
        return new double[][]{he, hn};
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** A flat thing facing {@code viewAz} (the subject, drawn as a pictogram): its shadow's four corners. */
    static double[][] flat(double e, double n, double viewAz, double width, double dE, double dN) {
        double az = Math.toRadians(viewAz), pe = Math.cos(az) * width / 2, pn = -Math.sin(az) * width / 2;
        return hull(new double[]{e - pe, e + pe}, new double[]{n - pn, n + pn}, dE, dN);
    }
}
