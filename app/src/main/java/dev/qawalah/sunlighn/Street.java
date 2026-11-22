package dev.qawalah.sunlighn;

import java.util.ArrayList;
import java.util.List;

/**
 * Line up a street (3.24, owner 2026-11-22: "tells the user where to stand along that line and when"). Once the line
 * and the sun's height are fixed, the time hardly depends on where along the street you stand; what changes is what
 * hides the far end: the buildings there stand higher the closer you are. This works out, for points along the line,
 * where the sun or moon at a height clears them. Plain Java, tested.
 */
final class Street {
    private Street() {}

    /** How far behind you (away from the far end) the street is looked at, and how far apart the points are. */
    static final double BEHIND = 280, STEP = 20;

    /**
     * The points looked at, as metres from you towards the far end (negative: away from it): from {@code back} metres
     * behind you to {@code ahead} metres on, every {@link #STEP}, always with you (0) among them.
     */
    static double[] offsets(double back, double ahead) {
        List<Double> out = new ArrayList<>();
        for (double s = -Math.floor(back / STEP) * STEP; s <= ahead + 1e-6; s += STEP) out.add(s);
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** The stretches [from, to] of the offsets where what hides the far end stands lower than {@code alt}. */
    static List<double[]> clear(double[] off, double[] hides, double alt) {
        List<double[]> out = new ArrayList<>();
        double[] cur = null;
        for (int i = 0; i < off.length; i++) {
            boolean ok = hides[i] < alt;
            if (ok && cur == null) {
                cur = new double[]{off[i], off[i]};
                out.add(cur);
            } else if (ok) {
                cur[1] = off[i];
            } else {
                cur = null;
            }
        }
        return out;
    }

    /** The stretch you (offset 0) stand in, or null. */
    static double[] around(List<double[]> stretches) {
        for (double[] s : stretches) if (s[0] <= 0 && s[1] >= 0) return s;
        return null;
    }

    /** The clear offset nearest you, or NaN when nowhere along the street is clear. */
    static double nearest(List<double[]> stretches) {
        double best = Double.NaN;
        for (double[] s : stretches) {
            double near = s[0] > 0 ? s[0] : s[1] < 0 ? s[1] : 0;
            if (Double.isNaN(best) || Math.abs(near) < Math.abs(best)) best = near;
        }
        return best;
    }

    /** "120 m towards the far end", "40 m away from it", "where you stand". */
    static String words(double offset) {
        long m = Math.round(Math.abs(offset));
        if (m < 5) return "where you stand";
        return Fmt.distance(m) + (offset > 0 ? " towards the far end" : " away from the far end");
    }
}
