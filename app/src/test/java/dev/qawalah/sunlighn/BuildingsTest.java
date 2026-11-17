package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Buildings that hide the sun (3.14): reading the tiles, heights, what the ray crosses, what is left out. */
public class BuildingsTest {
    private static final double LAT = 51.5, LON = -0.12;

    /** A square building w metres wide whose near side is d metres north of the origin, h tall. */
    private static Buildings.B square(double d, double w, double h) {
        return new Buildings.B(new double[]{-w / 2, w / 2, w / 2, -w / 2}, new double[]{d, d, d + w, d + w}, h, false);
    }

    private static Buildings.Area area(Buildings.B... bs) {
        List<Buildings.B> l = new ArrayList<>();
        for (Buildings.B b : bs) l.add(b);
        return new Buildings.Area(LAT, LON, 0, l);
    }

    private static double mLat() { return Math.toRadians(1) * FrameMath.EARTH_R; }

    @Test public void aBuildingInTheWayHidesBelowItsTop() {
        Buildings.Area a = area(square(100, 20, 31.6));
        Buildings.Hit h = Buildings.horizon(a, LAT, LON, 0, Double.NaN, Double.NaN);
        assertNotNull(h);
        assertEquals(100, h.dist, 0.01);
        assertEquals(Math.toDegrees(Math.atan2(30, 100)), h.alt, 0.01);   // 31.6 m less the eye's 1.6 m, 100 m off
        assertFalse(h.guessed);
        // Looking east, the building north is not in the way
        assertNull(Buildings.horizon(a, LAT, LON, 90, Double.NaN, Double.NaN));
    }

    @Test public void theHighestTopWinsNotTheNearest() {
        Buildings.Hit h = Buildings.horizon(area(square(50, 10, 5), square(300, 30, 120)), LAT, LON, 0, Double.NaN, Double.NaN);
        assertEquals(120, h.height, 1e-9);
        assertEquals(300, h.dist, 0.01);
    }

    @Test public void theSubjectsOwnBuildingAndYoursAreLeftOut() {
        Buildings.Area a = area(square(200, 20, 60));
        assertNull(Buildings.horizon(a, LAT, LON, 0, LAT + 210 / mLat(), LON));    // the subject point inside it
        assertNull(Buildings.horizon(a, LAT, LON, 0, LAT + 195 / mLat(), LON));    // 5 m in front: still its own
        assertNotNull(Buildings.horizon(a, LAT, LON, 0, LAT + 150 / mLat(), LON)); // 50 m in front: it hides the sun
        // You stand inside a building: it hides nothing
        assertNull(Buildings.horizon(area(square(-5, 10, 40)), LAT, LON, 0, Double.NaN, Double.NaN));
    }

    @Test public void theTilesFiveMetresMeansNotMapped() {
        double[] h = Buildings.height(5, 0);
        assertEquals(Buildings.GUESS, h[0], 0);
        assertEquals(1, h[1], 0);
        assertEquals(30, Buildings.height(30, 0)[0], 0);
        assertEquals(0, Buildings.height(30, 0)[1], 0);
        assertEquals(Buildings.GUESS, Buildings.height(Double.NaN, Double.NaN)[0], 0);
    }

    /** A real tile of Al Khobar (OpenFreeMap, zoom 14): 4530 buildings, the 30 m one where the tile says. */
    @Test public void readsARealTile() throws Exception {
        byte[] data;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("khobar-14-10476-6951.pbf")) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            data = o.toByteArray();
        }
        List<Buildings.Raw> all = Buildings.decode(data, 10476, 6951);
        assertEquals(4530, all.size());
        Buildings.Raw tall = null;
        int guessed = 0;
        for (Buildings.Raw r : all) {
            if (r.guessed) guessed++;
            if (r.height == 30) tall = r;
        }
        assertTrue("almost all heights are not mapped in Al Khobar", guessed > 4500);
        assertNotNull(tall);
        assertEquals(26.28431, tall.lat[0], 1e-5);
        assertEquals(50.19881, tall.lon[0], 1e-5);

        // Chosen round a spot in the tile and along a line: the near ones and the corridor, not the whole tile
        Buildings.Area a = Buildings.select(all, 26.285, 50.205, 240);
        assertTrue(a.list.size() > 50);
        assertTrue(a.list.size() < all.size() / 2);
    }

    @Test public void theTilesCoverRoundYouAndAlongTheLine() {
        List<int[]> t = Buildings.tiles(26.285, 50.205, 240);
        assertTrue(t.size() >= 1 && t.size() <= 8);
        boolean home = false;
        for (int[] q : t) if (q[0] == 10476 && q[1] == 6951) home = true;
        assertTrue(home);
        assertEquals(10476, Buildings.tileX(50.205));
        assertEquals(6951, Buildings.tileY(26.285));
    }
}
