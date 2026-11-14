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

    @Test public void aDaysAlignmentsAreOnTheLine() {
        long day = 1750000000000L;                         // June 2025, London
        java.util.List<Astro.Hit> hits = Astro.hitsBetween(false, 51.5, -0.12, 90, day, day + 86400000L);
        assertTrue(hits.size() >= 1);                      // the sun is due east once a day in summer
        for (Astro.Hit h : hits) assertEquals(90, Astro.sun(h.timeMs, 51.5, -0.12)[0], 0.05);
    }

    @Test public void moonWaxingBeforeFullAndWaningAfter() {
        long fullMoon = 1731706080000L;                    // 2024-11-15 21:28 UTC
        assertTrue(Astro.moonWaxing(fullMoon - 4 * 86400000L));
        assertTrue(!Astro.moonWaxing(fullMoon + 4 * 86400000L));
    }

    @Test public void searchAllListsEveryAlignmentInRange() {
        long start = 1731600000000L, end = start + 7 * 86400000L;
        double lat = 51.5, lon = -0.12, bearing = 200;
        java.util.List<Astro.Hit> hits = Astro.searchAll(false, lat, lon, bearing, start, end);
        // The sun passes a south-west bearing once a day, in daylight.
        assertTrue(hits.size() >= 6 && hits.size() <= 8);
        long last = 0;
        for (Astro.Hit h : hits) {
            assertTrue(h.timeMs >= start && h.timeMs <= end && h.timeMs > last);
            assertEquals(bearing, Astro.sun(h.timeMs, lat, lon)[0], 0.05);
            last = h.timeMs;
        }
        // The same moment comes out at the same millisecond from a different start, so reminder keys match.
        long other = hits.get(0).timeMs - 3600000L - 12345;
        assertEquals(hits.get(0).timeMs, Astro.search(false, lat, lon, bearing, other).next.timeMs);
    }

    @Test public void sunPassesLowAltitudeEveningAndMorning() {
        long start = 1731600000000L; // 14 Nov 2024 16:00 UTC
        double lat = 51.5, lon = -0.12;
        long eve = Astro.sunPasses(lat, lon, 4, true, start);
        long morn = Astro.sunPasses(lat, lon, 4, false, start);
        assertTrue(eve > start && eve - start < 86400000L * 2);
        assertTrue(morn > start && morn - start < 86400000L * 2);
        assertEquals(4, Astro.sun(eve, lat, lon)[1], 0.05);
        assertEquals(4, Astro.sun(morn, lat, lon)[1], 0.05);
        assertTrue(Astro.sun(eve, lat, lon)[0] > 180);   // evening sun is in the west
        assertTrue(Astro.sun(morn, lat, lon)[0] < 180);  // morning sun is in the east
        // Polar night: the sun never gets that high.
        assertEquals(-1, Astro.sunPasses(78, 15, 4, true, 1734700000000L));
    }

    @Test public void destinationIsTheInverseOfBearingAndDistance() {
        double[] d = Astro.destination(51.5, -0.12, 75, 1500);
        assertEquals(75, Astro.bearing(51.5, -0.12, d[0], d[1]), 0.01);
        assertEquals(1500, Astro.distance(51.5, -0.12, d[0], d[1]), 0.5);
        double[] w = Astro.destination(0, 179.99, 90, 5000);
        assertTrue(w[1] < -179); // wraps across the date line
    }

    @Test public void upcomingPassesGiveOneEveningSunPerDayAtTheAskedHeight() {
        long start = 1731600000000L;
        double lat = 51.5, lon = -0.12;
        java.util.List<Astro.Hit> hits = Astro.upcomingPasses(false, lat, lon, 4, true, start, 7);
        assertEquals(7, hits.size());
        long last = start;
        for (Astro.Hit h : hits) {
            assertTrue(h.timeMs > last + 20 * 3600000L || last == start);
            assertEquals(4, Astro.sun(h.timeMs, lat, lon)[1], 0.05);
            assertTrue(Astro.sun(h.timeMs, lat, lon)[0] > 180);       // evening: the sun is in the west
            last = h.timeMs;
        }
        java.util.List<Astro.Hit> mornings = Astro.upcomingPasses(false, lat, lon, 4, false, start, 3);
        assertTrue(Astro.sun(mornings.get(0).timeMs, lat, lon)[0] < 180);
    }

    @Test public void moonrisesAndSetsComeWithTheirPhase() {
        long start = 1731600000000L;                               // a few hours before the full moon
        java.util.List<Astro.Hit> rises = Astro.upcomingPasses(true, 51.5, -0.12, 4, false, start, 5);
        assertEquals(5, rises.size());
        for (Astro.Hit h : rises) {
            assertEquals(4, Astro.moon(h.timeMs, 51.5, -0.12)[1], 0.3);
            assertTrue(h.lit >= 0 && h.lit <= 1);
        }
        assertTrue(rises.get(0).lit > 0.9);                        // full moon: it rises as the sun sets
    }
}

