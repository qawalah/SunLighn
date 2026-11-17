package dev.qawalah.sunlighn;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The buildings fetched for real (the network): skipped unless BUILDINGS_LIVE is set.
 * {@code BUILDINGS_LIVE=1 ./gradlew testDebugUnitTest --tests '*BuildingsLiveTest*' --rerun}
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BuildingsLiveTest {
    @Test public void alKhobar() throws Exception {
        assumeTrue(System.getenv("BUILDINGS_LIVE") != null);
        Context c = RuntimeEnvironment.getApplication();
        Buildings.enabled = true;
        Buildings.keep(null);
        double lat = 26.2850, lon = 50.2050, bearing = 240;
        long t0 = System.currentTimeMillis();
        Buildings.fetch(c, lat, lon, bearing, null);
        Buildings.Area a = null;
        for (int i = 0; i < 600 && a == null; i++) {
            Thread.sleep(100);
            ShadowLooper.idleMainLooper();
            a = Buildings.get(c, lat, lon, bearing);
        }
        assertNotNull("fetched", a);
        Buildings.Hit h = Buildings.horizon(a, lat, lon, bearing, Double.NaN, Double.NaN);
        System.out.println("BUILDINGS " + a.list.size() + " near, " + a.guessed() + " guessed, in "
                + (System.currentTimeMillis() - t0) + " ms; highest this way: "
                + (h == null ? "none" : String.format("%.1f° at %.0f m, %.0f m tall", h.alt, h.dist, h.height)));
        assertTrue(a.list.size() > 50);

        // The viewfinder with them: a 10 m tree 120 m off along the line, the sun low behind it
        double[] sub = Astro.destination(lat, lon, bearing, 120);
        FrameView v = new FrameView(c);
        v.setLens(24, true);
        v.setAim(bearing, 4, false);
        v.setSubject(bearing, 120, 10, 1, bearing, false, -FrameMath.EYE);
        v.setBodies(bearing + 2, 6, 0, -30, 0.5, true);
        v.setBuildings(a, lat, lon, sub[0], sub[1]);
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1500, android.view.View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1080, 1500);
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(1080, 1500, android.graphics.Bitmap.Config.ARGB_8888);
        v.draw(new android.graphics.Canvas(bm));
        java.io.File dir = new java.io.File("build/screens");
        dir.mkdirs();
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(dir, "50-live-khobar-buildings.png"))) {
            bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
    }
}
