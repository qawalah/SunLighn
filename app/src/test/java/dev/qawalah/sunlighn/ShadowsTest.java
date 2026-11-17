package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import org.junit.Test;

public class ShadowsTest {
    @Test public void shadowRunsAwayFromTheSun() {
        // sun due south (180) at 45 degrees: a 10 m wall's shadow falls 10 m north
        double[] v = Shadows.vector(180, 45, 10);
        assertEquals(0, v[0], 1e-6);
        assertEquals(10, v[1], 1e-6);
        // sun in the east: the shadow falls west
        v = Shadows.vector(90, 45, 10);
        assertEquals(-10, v[0], 1e-6);
        assertEquals(0, v[1], 1e-6);
    }

    @Test public void lowOrNoBodyGivesNoneAndLongOnesAreCut() {
        assertNull(Shadows.vector(90, 0.5, 10));
        assertNull(Shadows.vector(90, -5, 10));
        assertNull(Shadows.vector(90, 30, 0));
        double[] v = Shadows.vector(0, 1, 100);          // 5.7 km: cut at the limit
        assertEquals(Shadows.MAX_LENGTH, Math.hypot(v[0], v[1]), 1e-6);
    }

    @Test public void strengthFadesToTheHorizon() {
        assertEquals(0, Shadows.strength(0.4, false), 0);
        assertEquals(1, Shadows.strength(30, false), 0);
        assertTrue(Shadows.strength(4, false) > 0 && Shadows.strength(4, false) < 1);
        assertEquals(0.3, Shadows.strength(30, true), 1e-9);
    }

    @Test public void hullOfASquareSweptNorth() {
        double[] e = {0, 10, 10, 0}, n = {0, 0, 10, 10};
        double[][] h = Shadows.hull(e, n, 0, 20);        // a 10 x 10 footprint, shadow 20 m north
        assertEquals(4, h[0].length);
        double minN = 1e9, maxN = -1e9;
        for (double v : h[1]) { minN = Math.min(minN, v); maxN = Math.max(maxN, v); }
        assertEquals(0, minN, 1e-9);
        assertEquals(30, maxN, 1e-9);
    }

    @Test public void flatSubjectShadowIsAParallelogramAcrossTheView() {
        // you look north (az 0): the subject is 8 m wide across east-west; sun due west: the shadow falls east
        double[] v = Shadows.vector(270, 45, 10);
        double[][] h = Shadows.flat(0, 100, 0, 8, v[0], v[1]);
        double minE = 1e9, maxE = -1e9;
        for (double x : h[0]) { minE = Math.min(minE, x); maxE = Math.max(maxE, x); }
        assertEquals(-4, minE, 1e-6);
        assertEquals(14, maxE, 1e-6);
    }
}
