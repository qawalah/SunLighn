package dev.qawalah.sunlighn;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * "Line it up" (When, Never lines up here) turns the line to where the body comes closest. Found by Ali on
 * 2026-11-20 (Al Khobar, a line 24° NNE, the land hiding the sun up to 2.7°): the bar said "the sun lines up" and
 * the card still said "Never lines up here". The closest approach was a sunrise at 0°, behind the land, and the
 * new line only grazed the sun's reach.
 */
public class LineItUpTest {
    private static final double LAT = 26.29, LON = 50.20;     // Al Khobar, as in Ali's run
    private static final long NOV_20_2026 = 1795132800000L;   // 2026-11-20 00:00 UTC
    private static final double HIDDEN = 2.7;                 // "Red: hidden up to 2.7°"

    @Test public void aLineTooFarNorthNeverLinesUp() {
        assertNull(Astro.search(false, LAT, LON, 24, NOV_20_2026, HIDDEN).next);
    }

    @Test public void theClosestIsAboveWhatHidesIt() {
        Astro.Near near = Astro.nearest(false, LAT, LON, 24, NOV_20_2026, 366, HIDDEN);
        assertNotNull(near);
        assertTrue("closest at " + near.alt + "° up", near.alt >= HIDDEN);
    }

    @Test public void turningToTheClosestGivesAVisibleTime() {
        Astro.Near near = Astro.nearest(false, LAT, LON, 24, NOV_20_2026, 366, HIDDEN);
        Astro.Found after = Astro.search(false, LAT, LON, Astro.lineUpBearing(near), NOV_20_2026, HIDDEN);
        assertNotNull("Line it up must leave the sun on the line above what hides it", after.next);
    }

    @Test public void withNothingHiddenItStillCrossesRatherThanGrazes() {
        Astro.Near near = Astro.nearest(false, LAT, LON, 24, NOV_20_2026, 366, -90);
        Astro.Found after = Astro.search(false, LAT, LON, Astro.lineUpBearing(near), NOV_20_2026, -90);
        assertNotNull(after.next);
    }
}
