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
}
