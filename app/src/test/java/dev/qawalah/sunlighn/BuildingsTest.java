package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Buildings that hide the sun (3.14): heights from tags, what the ray crosses, what is left out. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BuildingsTest {
    private static final double LAT = 51.5, LON = -0.12;

    /** A square building w metres wide whose near side is d metres north of the origin, h tall. */
    private static String way(long id, double d, double w, String tags) {
        double mLat = Math.toRadians(1) * FrameMath.EARTH_R, mLon = mLat * Math.cos(Math.toRadians(LAT));
        double s = LAT + d / mLat, n = LAT + (d + w) / mLat, wst = LON - w / 2 / mLon, e = LON + w / 2 / mLon;
        return "{\"type\":\"way\",\"id\":" + id + ",\"tags\":" + tags + ",\"geometry\":["
                + pt(s, wst) + "," + pt(s, e) + "," + pt(n, e) + "," + pt(n, wst) + "," + pt(s, wst) + "]}";
    }

    private static String pt(double lat, double lon) { return "{\"lat\":" + lat + ",\"lon\":" + lon + "}"; }

    private static Buildings.Area area(String... ways) throws Exception {
        return Buildings.parse("{\"elements\":[" + String.join(",", ways) + "]}", LAT, LON, 0);
    }

    @Test public void heightsFromTags() throws Exception {
        assertEquals(25, Buildings.height(new JSONObject("{\"height\":\"25 m\"}"))[0], 1e-9);
        assertEquals(25, Buildings.metres("82'"), 0.1);
        assertEquals(12.5, Buildings.metres("12,5"), 1e-9);
        double[] floors = Buildings.height(new JSONObject("{\"building\":\"yes\",\"building:levels\":\"6\"}"));
        assertEquals(18, floors[0], 1e-9);
        assertEquals(0, floors[1], 0);                         // known, not guessed
        double[] guess = Buildings.height(new JSONObject("{\"building\":\"yes\"}"));
        assertEquals(9, guess[0], 1e-9);
        assertEquals(1, guess[1], 0);
        assertEquals(3, Buildings.height(new JSONObject("{\"building\":\"garage\"}"))[0], 1e-9);
    }

    @Test public void aBuildingInTheWayHidesBelowItsTop() throws Exception {
        Buildings.Area a = area(way(1, 100, 20, "{\"building\":\"yes\",\"height\":\"31.6\"}"));
        Buildings.Hit h = Buildings.horizon(a, LAT, LON, 0, Double.NaN, Double.NaN);
        assertNotNull(h);
        assertEquals(100, h.dist, 0.5);
        assertEquals(Math.toDegrees(Math.atan2(30, 100)), h.alt, 0.05);   // 31.6 m less the eye's 1.6 m, 100 m off
        assertFalse(h.guessed);
        // Looking east, the building north is not in the way
        assertNull(Buildings.horizon(a, LAT, LON, 90, Double.NaN, Double.NaN));
    }

    @Test public void theHighestTopWinsNotTheNearest() throws Exception {
        Buildings.Area a = area(way(1, 50, 10, "{\"height\":\"5\"}"), way(2, 300, 30, "{\"height\":\"120\"}"));
        Buildings.Hit h = Buildings.horizon(a, LAT, LON, 0, Double.NaN, Double.NaN);
        assertEquals(120, h.height, 1e-9);
        assertEquals(300, h.dist, 0.5);
    }

    @Test public void theSubjectsOwnBuildingAndYoursAreLeftOut() throws Exception {
        Buildings.Area a = area(way(1, 200, 20, "{\"height\":\"60\"}"));
        double mLat = Math.toRadians(1) * FrameMath.EARTH_R;
        double subLat = LAT + 210 / mLat;                       // the subject point inside the building
        assertNull(Buildings.horizon(a, LAT, LON, 0, subLat, LON));
        double nearLat = LAT + 195 / mLat;                      // 5 m in front of it: still the subject's own
        assertNull(Buildings.horizon(a, LAT, LON, 0, nearLat, LON));
        // You stand inside a building: it hides nothing
        Buildings.Area mine = area(way(2, -5, 10, "{\"height\":\"40\"}"));
        assertNull(Buildings.horizon(mine, LAT, LON, 0, Double.NaN, Double.NaN));
    }

    @Test public void aWayInTwoPartsOfTheQueryIsCountedOnce() throws Exception {
        String w = way(1, 100, 20, "{\"height\":\"20\"}");
        assertEquals(1, area(w, w).list.size());
    }

    @Test public void theQueryLooksRoundAndAlongTheWay() {
        String q = Buildings.query(LAT, LON, 270);
        assertTrue(q.contains("around:300"));
        assertTrue(q.contains("poly:"));
        assertTrue(q.contains("out tags geom"));
    }
}
