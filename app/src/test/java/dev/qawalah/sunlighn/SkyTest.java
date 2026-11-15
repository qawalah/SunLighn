package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Where the sun can and cannot be, for Build the shot's shaded sky. */
public class SkyTest {
    private static final long NOV_15 = 1794700800000L;   // 2026-11-15 00:00 UTC

    /** Noon sun in Jeddah in mid-November: 90 - 21.5 - 18.4 (declination) = about 50 degrees, lower each day after. */
    @Test public void highestSunJeddahNovember() {
        double h = Sky.highest(false, 21.5, 39.2, NOV_15, 14);
        assertTrue("got " + h, h > 49 && h < 51.5);
    }

    /** London in November: the sun never climbs past about 20 degrees, so higher is impossible. */
    @Test public void possibleAboveHighestIsFalse() {
        boolean[] ok = Sky.possible(false, true, 51.5, -0.12, NOV_15, 14, 300, 0, false, (lat, lon) -> Ground.LAND);
        assertEquals(Sky.TOP + 1, ok.length);
        assertTrue(ok[10]);
        assertFalse(ok[25]);
    }

    /** On foot, a height whose every spot is on water is impossible; a drone can be there. */
    @Test public void waterEverywhereOnFootIsFalse() {
        boolean[] foot = Sky.possible(false, true, 51.5, -0.12, NOV_15, 14, 300, 0, false, (lat, lon) -> Ground.WATER);
        for (boolean b : foot) assertFalse(b);
        boolean[] drone = Sky.possible(false, true, 51.5, -0.12, NOV_15, 14, 300, 0, true, (lat, lon) -> Ground.WATER);
        assertTrue(drone[10]);
    }

    /** Without a network the ground is unknown, and unknown rules nothing out. */
    @Test public void unknownCountsAsPossible() {
        boolean[] ok = Sky.possible(false, true, 51.5, -0.12, NOV_15, 14, 300, 0, false, (lat, lon) -> Ground.UNKNOWN);
        assertTrue(ok[10]);
    }
}
