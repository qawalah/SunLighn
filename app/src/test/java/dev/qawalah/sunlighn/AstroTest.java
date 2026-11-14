package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AstroTest {
    // Reference values from an independent astronomy library (PyEphem).
    @Test public void sunMatchesReference() {
        double[] s = Astro.sun(1731600000000L, 21.42, 39.83);
        assertEquals(257.00, s[0], 0.1);
        assertEquals(-18.96, s[1], 0.1);
        double[] s2 = Astro.sun(1750000000000L, 51.5, -0.12);
        assertEquals(249.47, s2[0], 0.1);
        assertEquals(44.76, s2[1], 0.1);
    }

    @Test public void moonMatchesReference() {
        double[] m = Astro.moon(1731600000000L, 21.42, 39.83);
        assertEquals(85.15, m[0], 0.3);
        assertEquals(33.86, m[1], 0.3);
        assertEquals(0.977, Astro.moonLit(1731600000000L), 0.01);
        double[] m2 = Astro.moon(1750000000000L, 51.5, -0.12);
        assertEquals(346.23, m2[0], 0.3);
        assertEquals(-57.61, m2[1], 0.3);
    }

    @Test public void bearingAndDistance() {
        assertEquals(90, Astro.bearing(0, 0, 0, 1), 0.01);
        assertEquals(0, Astro.bearing(0, 0, 1, 0), 0.01);
        assertEquals(111195, Astro.distance(0, 0, 0, 1), 100);
        assertEquals("ENE", Astro.compass(74));
    }

    @Test public void searchFindsAnAlignmentOnTheLine() {
        long start = 1731600000000L;
        double lat = 51.5, lon = -0.12, bearing = 100; // a line pointing a bit south of east
        Astro.Found f = Astro.search(false, lat, lon, bearing, start);
        assertNotNull(f.next);
        assertTrue(f.next.timeMs >= start);
        assertEquals(bearing, Astro.sun(f.next.timeMs, lat, lon)[0], 0.05);
        assertTrue(f.next.alt >= 0);
        if (f.low != null) assertTrue(f.low.alt <= Astro.LOW_ALT);
    }

}
