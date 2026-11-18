package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** A shot as a link (3.18): what goes in comes out, and what is not ours is refused. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShotLinkTest {
    private static Trip.Line shot() {
        Trip.Line l = new Trip.Line("id-1", "Big Ben | Westminster", 51.5007, -0.1246, 51.50073, -0.12461 + 0.01, 0);
        l.height = 96;
        l.kind = 4;
        l.timeMs = 1763400000000L;
        l.moon = true;
        l.subjectName = "Big Ben";
        l.panDeg = -3.5;
        l.pitch = 12.25;
        return l;
    }

    @Test public void aShotComesOutAsItWentIn() {
        Trip.Line a = shot(), b = ShotLink.decode(ShotLink.encode(a));
        assertNotNull(b);
        assertNotEquals(a.id, b.id);                       // the other phone gets its own
        assertEquals(a.name, b.name);
        assertEquals(a.youLat, b.youLat, 1e-7);
        assertEquals(a.subLon, b.subLon, 1e-7);
        assertEquals(a.mode, b.mode);
        assertEquals(96, b.height, 0);
        assertEquals(4, b.kind);
        assertEquals(a.timeMs, b.timeMs);
        assertTrue(b.moon);
        assertEquals("Big Ben", b.subjectName);
        assertEquals(-3.5, b.panDeg, 1e-9);
        assertEquals(12.25, b.pitch, 1e-9);
        assertTrue(b.named);
    }

    @Test public void noTiltAndNoNameSurvive() {
        Trip.Line a = new Trip.Line("x", "", 26.3, 50.2, 26.31, 50.21, 1);
        a.pitch = Double.NaN;
        Trip.Line b = ShotLink.decode(ShotLink.encode(a));
        assertTrue(Double.isNaN(b.pitch));
        assertNull(b.subjectName);
        assertFalse(b.named);
        assertEquals(1, b.mode);
    }

    @Test public void theLinkIsShortAndTheCodeIsFoundInBothForms() {
        String code = ShotLink.encode(shot());
        assertTrue("code is " + code.length(), code.length() < 220);
        assertEquals(code, ShotLink.codeOf(Uri.parse("sunwhere://shot?d=" + code)));
        assertEquals(code, ShotLink.codeOf(Uri.parse(ShotLink.PAGE + "#" + code)));
        assertTrue(ShotLink.url(shot()).startsWith(ShotLink.PAGE + "#"));
    }

    @Test public void whatIsNotAShotIsRefused() {
        assertNull(ShotLink.decode(null));
        assertNull(ShotLink.decode(""));
        assertNull(ShotLink.decode("not a code at all!"));
        assertNull(ShotLink.decode("AAAA"));
    }
}
