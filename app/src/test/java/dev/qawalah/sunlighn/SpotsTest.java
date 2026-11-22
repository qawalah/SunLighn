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

    @Test public void everySettingSunAtTheHeightPutsItBehindTheSubject() {
        List<Spots.Spot> s = Spots.find(false, LAT, LON, 4, 300, false, false, FROM, 30, 0);
        assertEquals(30, s.size());                               // in London's winter the sun gets to 4° every day
        for (Spots.Spot x : s) {
            double[] sun = Astro.sun(x.timeMs, LAT, LON);
            assertEquals(4, sun[1], 0.05);
            assertTrue("setting", Astro.sun(x.timeMs - 600000, LAT, LON)[1] > 4);
            assertEquals(sun[0], x.bodyAz, 0.01);
            assertEquals(Astro.norm360(sun[0] + 180), x.bearing, 0.01);
            assertEquals(300, Astro.distance(LAT, LON, x.lat, x.lon), 1);
            // from the spot, the subject (and the sun beyond it) is the way the sun stands
            assertEquals(0, FrameMath.diff(Astro.bearing(x.lat, x.lon, LAT, LON), sun[0]), 0.2);
            assertFalse(x.rises);
        }
    }

    @Test public void behindYouStandsOnTheBodysSide() {
        Spots.Spot x = Spots.find(false, LAT, LON, 10, 200, true, true, FROM, 1, 0).get(0);
        assertTrue(x.rises);
        assertEquals(x.bodyAz, x.bearing, 0.01);
        assertTrue("rising", Astro.sun(x.timeMs - 600000, LAT, LON)[1] < 10);
    }

    @Test public void daysTheSunNeverGetsThatHighAreLeftOut() {
        // London in late November: the sun tops out near 17°, so 30° is never reached
        assertTrue(Spots.find(false, LAT, LON, 30, 300, false, false, FROM, 20, 0).isEmpty());
    }

    @Test public void aDimMoonIsLeftOutWhenAskedFull() {
        List<Spots.Spot> all = Spots.find(true, LAT, LON, 5, 300, false, false, FROM, 30, 0);
        List<Spots.Spot> full = Spots.find(true, LAT, LON, 5, 300, false, false, FROM, 30, 0.9);
        assertTrue(all.size() > full.size());
        for (Spots.Spot x : full) assertTrue(x.lit >= 0.9);
    }

    @Test public void theKeysAndTheSliderGiveTheHeight() {
        assertEquals(5, Spots.targetAlt(Spots.HEIGHT, 30, 300, 4, 6), 1e-9);
        double top = FrameMath.topAngle(30, 300, -FrameMath.EYE);
        assertEquals(top + FrameMath.BODY_RADIUS, Spots.targetAlt(Spots.ON_TOP, 30, 300, 0, 0), 1e-9);
        assertEquals(FrameMath.LOWEST, Spots.targetAlt(Spots.HORIZON, 30, 300, 0, 0), 1e-9);
    }

    @Test public void daysInARowWithTheSameSpotAreOneRun() {
        List<Spots.Spot> s = Spots.find(false, LAT, LON, 4, 300, false, false, FROM, 40, 0);
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
}
