package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.TimeZone;

/** The plain parts of 2.0: the light's names, the words for times and distances, lenses, and typed coordinates. */
public class LightFmtTest {
    @Test public void lightByTheSunsHeight() {
        assertEquals(Light.Kind.DAY, Light.of(20));
        assertEquals(Light.Kind.GOLDEN, Light.of(6));
        assertEquals(Light.Kind.GOLDEN, Light.of(-4));
        assertEquals(Light.Kind.BLUE, Light.of(-5));
        assertEquals(Light.Kind.TWILIGHT, Light.of(-7));
        assertEquals(Light.Kind.NIGHT, Light.of(-19));
        assertEquals("Golden hour", Light.name(Light.Kind.GOLDEN));
    }

    /** A run of days says its times earliest first by the clock ("3:58 PM to 4:01 PM"), never backwards. */
    @Test public void timesEarliestByTheClock() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        long day1At1601 = 1794700800000L + (16 * 60 + 1) * 60000L;
        long day9At1558 = 1794700800000L + 8 * 86400000L + (15 * 60 + 58) * 60000L;
        long[] o = Fmt.byClock(day1At1601, day9At1558, utc);
        assertEquals(day9At1558, o[0]);
        assertEquals(day1At1601, o[1]);
        o = Fmt.byClock(day9At1558 - 8 * 86400000L + 3600000L, day9At1558, utc);   // 16:58 then 15:58
        assertEquals(day9At1558, o[0]);
    }

    @Test public void untilSaysHowLong() {
        long now = 1_000_000_000_000L;
        assertEquals("", Fmt.until(now - 1, now));
        assertEquals("in 40 min", Fmt.until(now + 40 * 60000L, now));
        assertEquals("in 5 h", Fmt.until(now + 5 * 3600000L, now));
        assertEquals("in 3 days", Fmt.until(now + 3 * 86400000L, now));
    }

    @Test public void distancesAndZones() {
        assertEquals("451 m", Fmt.distance(451));
        assertEquals("1.0 km", Fmt.distance(1000));
        assertEquals("12 km", Fmt.distance(12300));
        assertEquals("London time", Fmt.zoneName(TimeZone.getTimeZone("Europe/London")));
        assertEquals("New York time", Fmt.zoneName(TimeZone.getTimeZone("America/New_York")));
    }

    @Test public void midnightIsThePlacesMidnight() {
        TimeZone tokyo = TimeZone.getTimeZone("Asia/Tokyo");
        long t = 1_750_000_000_000L;                       // 2025-06-15 15:06:40 UTC = 16 June 00:06 in Tokyo
        assertEquals(1_749_999_600_000L, Fmt.midnight(t, tokyo));
    }

    @Test public void lensesSnapAndStep() {
        assertEquals(35, FrameMath.snapFocal(36.5), 1e-9);
        assertEquals(42, FrameMath.snapFocal(42), 1e-9);
        assertEquals(50, FrameMath.stepFocal(35, 1), 1e-9);
        assertEquals(24, FrameMath.stepFocal(35, -1), 1e-9);
        assertEquals(35, FrameMath.stepFocal(42, -1), 1e-9);
        assertEquals(14, FrameMath.stepFocal(14, -1), 1e-9);
    }

    @Test public void subjectTopAndAim() {
        assertEquals(45, FrameMath.topAngle(100, 100), 1e-9);
        assertEquals(0, FrameMath.topAngle(0, 100), 1e-9);
        // A body that fits keeps the horizon a third up; a high one is aimed at.
        assertEquals(10, FrameMath.aimPitch(60, 20), 1e-9);
        assertEquals(40 - 2.5, FrameMath.aimPitch(10, 40), 1e-9);
    }

    @Test public void typedCoordinates() {
        Places.Place p = Places.coordinates(" 51.5007, -0.1246 ");
        assertNotNull(p);
        assertEquals(51.5007, p.lat, 1e-9);
        assertEquals(-0.1246, p.lon, 1e-9);
        assertNull(Places.coordinates("Tower Bridge"));
        assertNull(Places.coordinates("95, 10"));
    }

    @Test public void unprojectUndoesProject() {
        double[] q = FrameMath.project(236, 7, 230, 5);
        double[] back = FrameMath.unproject(q[0], q[1], 230, 5);
        assertEquals(236, back[0], 1e-6);
        assertEquals(7, back[1], 1e-6);
        assertEquals(100, FrameMath.distanceFor(100, 45), 1e-6);
    }

    @Test public void groundFromTheMapsColours() {
        assertEquals(Ground.WATER, Ground.classify(0xFFAAD3DF));
        assertEquals(Ground.BUILDING, Ground.classify(0xFFD9D0C9));
        assertEquals(Ground.LAND, Ground.classify(0xFFF2EFE9));
        assertEquals(Ground.WATER, Ground.verdict(12, 0, 25));
        assertEquals(Ground.LAND, Ground.verdict(3, 4, 25));       // a label or a bank does not make it water
        assertEquals(Ground.BUILDING, Ground.verdict(0, 14, 25));
        assertEquals(Ground.UNKNOWN, Ground.verdict(0, 0, 0));
    }

    @Test public void theSunNeverCrossesALineDueNorthInLondon() {
        long start = 1_794_000_000_000L;
        assertNull(Astro.search(false, 51.5, -0.12, 0, start).next);
        Astro.Near n = Astro.nearest(false, 51.5, -0.12, 0, start, 366);
        assertNotNull(n);
        // The closest is a midsummer sunrise or sunset, some 50 degrees off north.
        assertEquals(51, Math.abs(n.off), 4);
    }
}
