package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** Find the spot (3.24): every day's moment at the height asked, and the spot that puts the body behind the subject. */
public class SpotsTest {
    private static final double LAT = 51.5007, LON = -0.1246;   // Big Ben
    private static final long FROM = 1795305600000L;             // 2026-11-22 00:00 UTC

    private static Spots.Ask ask(int sits, boolean behind, double lo, double hi, double dMin, double dMax) {
        Spots.Ask a = new Spots.Ask();
        a.sits = sits;
        a.behindYou = behind;
        a.height = 30;
        a.lo = lo;
        a.hi = hi;
        a.dMin = dMin;
        a.dMax = dMax;
        return a;
    }

    @Test public void everySettingSunAtTheHeightPutsItBehindTheSubject() {
        double dist = Spots.distanceFor(Spots.ON_TOP, 30, 4);
        List<Spots.Spot> s = Spots.find(false, LAT, LON, ask(Spots.ON_TOP, false, 4, 4, 20, 20000), false, FROM, 30, 0);
        assertEquals(30, s.size());                               // in London's winter the sun gets to 4° every day
        for (Spots.Spot x : s) {
            double[] sun = Astro.sun(x.timeMs, LAT, LON);
            assertEquals(4, sun[1], 0.05);
            assertTrue("setting", Astro.sun(x.timeMs - 600000, LAT, LON)[1] > 4);
            assertEquals(sun[0], x.bodyAz, 0.05);
            assertEquals(Astro.norm360(sun[0] + 180), x.bearing, 0.05);
            assertEquals(dist, x.dist, 1e-6);
            assertEquals(dist, Astro.distance(LAT, LON, x.lat, x.lon), 1);
            // from the spot, the subject (and the sun beyond it) is the way the sun stands
            assertEquals(0, FrameMath.diff(Astro.bearing(x.lat, x.lon, LAT, LON), sun[0]), 0.2);
            assertFalse(x.rises);
        }
    }

    /** 3.24.3 (owner: "like a pie"): a range of heights is a stretch of spots each day, nearer for higher. */
    @Test public void aRangeOfHeightsIsAStretchOfDistances() {
        List<Spots.Spot> s = Spots.find(false, LAT, LON, ask(Spots.ON_TOP, false, 3, 8, 20, 20000), false, FROM, 10, 0);
        assertEquals(10, s.size());
        double near = Spots.distanceFor(Spots.ON_TOP, 30, 8), far = Spots.distanceFor(Spots.ON_TOP, 30, 3);
        for (Spots.Spot x : s) {
            assertEquals(near, Astro.distance(LAT, LON, x.near[0], x.near[1]), 2);
            assertEquals(far, Astro.distance(LAT, LON, x.far[0], x.far[1]), 5);
            assertTrue(x.dist >= near - 1 && x.dist <= far + 1);
            assertEquals(x.dist, Astro.distance(LAT, LON, x.lat, x.lon), 2);
        }
    }

    /** The distance range (the lens's, or the user's) keeps only the heights whose spot is in it. */
    @Test public void theDistanceRangeLimitsThePie() {
        double dMin = 250, dMax = 400;
        List<Spots.Spot> s = Spots.find(false, LAT, LON, ask(Spots.ON_TOP, false, 2, 12, dMin, dMax), false, FROM, 10, 0);
        assertFalse(s.isEmpty());
        for (Spots.Spot x : s) {
            assertTrue(x.dist >= dMin - 1e-6 && x.dist <= dMax + 1e-6);
            assertTrue(Astro.distance(LAT, LON, x.near[0], x.near[1]) >= dMin - 2);
            assertTrue(Astro.distance(LAT, LON, x.far[0], x.far[1]) <= dMax + 2);
        }
        double[] w = ask(Spots.ON_TOP, false, 2, 12, dMin, dMax).works();
        assertEquals(dMin, w[0], 1e-6);
        assertEquals(dMax, w[1], 1e-6);
        assertTrue("none works", ask(Spots.ON_TOP, false, 2, 3, 20, 50).works()[0] > ask(Spots.ON_TOP, false, 2, 3, 20, 50).works()[1]);
    }

    @Test public void behindYouStandsOnTheBodysSideAnywhereInTheRange() {
        Spots.Spot x = Spots.find(false, LAT, LON, ask(Spots.ON_TOP, true, 10, 10, 100, 400), true, FROM, 1, 0).get(0);
        assertTrue(x.rises);
        assertEquals(x.bodyAz, x.bearing, 0.01);
        assertEquals(200, x.dist, 1e-6);                          // the middle of 100 to 400 m on a log scale
        assertEquals(100, Astro.distance(LAT, LON, x.near[0], x.near[1]), 1);
        assertEquals(400, Astro.distance(LAT, LON, x.far[0], x.far[1]), 2);
        assertTrue("rising", Astro.sun(x.timeMs - 600000, LAT, LON)[1] < 10);
    }

    @Test public void daysTheSunNeverGetsThatHighAreLeftOut() {
        // London in late November: the sun tops out near 17°, so 30° is never reached
        assertTrue(Spots.find(false, LAT, LON, ask(Spots.ON_TOP, true, 30, 30, 20, 20000), false, FROM, 20, 0).isEmpty());
    }

    @Test public void aDimMoonIsLeftOutWhenAskedFull() {
        List<Spots.Spot> all = Spots.find(true, LAT, LON, ask(Spots.ON_TOP, false, 5, 5, 20, 20000), false, FROM, 30, 0);
        List<Spots.Spot> full = Spots.find(true, LAT, LON, ask(Spots.ON_TOP, false, 5, 5, 20, 20000), false, FROM, 30, 0.9);
        assertTrue(all.size() > full.size());
        for (Spots.Spot x : full) assertTrue(x.lit >= 0.9);
    }

    /** 3.24.3: the lens range gives the distances (the subject a third of the picture); 1x is about 24 mm on a phone. */
    @Test public void theLensGivesTheDistancesAndPhoneZoom() {
        double near = Spots.distanceForLens(30, 24, false), far = Spots.distanceForLens(30, 120, false);
        assertTrue(far > 4 * near);
        assertEquals(1 / 3.0, Spots.fill(24, 30, near, false), 0.05);
        assertEquals("1x", Spots.phoneZoom(24));
        assertEquals("3x", Spots.phoneZoom(72));
        assertEquals("5x", Spots.phoneZoom(120));
        assertEquals("0.6x", Spots.phoneZoom(14));
    }

    @Test public void theKeysAndTheSliderGiveTheHeight() {
        assertEquals(5, Spots.targetAlt(Spots.HEIGHT, 30, 300, 4, 6), 1e-9);
        double top = FrameMath.topAngle(30, 300, -FrameMath.EYE);
        assertEquals(top + FrameMath.BODY_RADIUS, Spots.targetAlt(Spots.ON_TOP, 30, 300, 0, 0), 1e-9);
        assertEquals(FrameMath.LOWEST, Spots.targetAlt(Spots.HORIZON, 30, 300, 0, 0), 1e-9);
    }

    @Test public void daysInARowWithTheSameSpotAreOneRun() {
        List<Spots.Spot> s = Spots.find(false, LAT, LON, ask(Spots.ON_TOP, false, 4, 4, 20, 20000), false, FROM, 40, 0);
        List<Spots.Run> runs = Spots.runs(s, 2, 14, java.util.TimeZone.getTimeZone("UTC"));
        int n = 0;
        for (Spots.Run r : runs) {
            n += r.days.size();
            assertTrue(r.days.size() <= 14);
            for (Spots.Spot x : r.days) assertTrue(Math.abs(FrameMath.diff(x.bearing, r.first().bearing)) <= 2);
            java.util.Calendar a = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")), b = (java.util.Calendar) a.clone();
            a.setTimeInMillis(r.first().timeMs);
            b.setTimeInMillis(r.last().timeMs);
            assertEquals("never across a month", a.get(java.util.Calendar.MONTH), b.get(java.util.Calendar.MONTH));
        }
        assertEquals(s.size(), n);
        assertTrue("near the solstice the spot hardly moves", runs.size() < s.size());
        assertEquals(0, Spots.nearestRun(runs, runs.get(0).first().lat, runs.get(0).first().lon, 30));
        assertEquals(-1, Spots.nearestRun(runs, LAT + 1, LON, 30));
    }

    @Test public void theLensFillsAThirdWithinItsRange() {
        double f = Spots.lens(14, 400, 30, 300, false);
        assertEquals(1 / 3.0, Spots.fill(f, 30, 300, false), 0.12);
        assertEquals(24, Spots.lens(24, 35, 30, 3000, false), 1e-9 + 11);   // far away: the long end, at most 35
        assertTrue(Spots.lens(24, 35, 30, 3000, false) <= 35);
        assertEquals(24, Spots.lens(24, 105, 300, 100, false), 1e-9);       // a peak close by: the wide end
    }

    /** 3.24.1: the distance follows the sun's height: on top of a 30 m building at that height, and the higher, the closer. */
    @Test public void theDistanceFollowsTheHeight() {
        double d = Spots.distanceFor(Spots.ON_TOP, 30, 4);
        assertEquals(4, FrameMath.targetAlt(Spots.ON_TOP, 30, d, -FrameMath.EYE), 0.05);
        assertTrue(Spots.distanceFor(Spots.ON_TOP, 30, 10) < d);
        double m = Spots.distanceFor(Spots.MIDDLE, 30, 2);
        assertEquals(2, FrameMath.targetAlt(Spots.MIDDLE, 30, m, -FrameMath.EYE), 0.05);
        assertEquals("too high for a person: as close as it goes", Spots.NEAREST, Spots.distanceFor(Spots.ON_TOP, 1.8, 30), 1e-9);
        assertEquals("too low: as far as it goes", Spots.FARTHEST, Spots.distanceFor(Spots.ON_TOP, 30, 0.1), 1e-9);
    }
}
