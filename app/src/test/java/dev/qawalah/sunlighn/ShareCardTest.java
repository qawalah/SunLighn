package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** The share card (3.35): the right sizes, with and without a photo, long names that fit, a trip of one and of five. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShareCardTest {
    private static ShareCard.Data data(String title, boolean photo) {
        ShareCard.Data d = new ShareCard.Data();
        d.title = title;
        d.city = "Riyadh";
        d.moment = "Sun behind subject · Sat 28 Nov · 17:12";
        d.detail = "3° above the horizon · Golden hour";
        d.hasLine = true;
        d.youLat = 24.70; d.youLon = 46.65; d.subLat = 24.711; d.subLon = 46.674;
        if (photo) {
            Bitmap b = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            c.drawColor(0xFF2E4A7A);
            android.graphics.Paint p = new android.graphics.Paint();
            p.setColor(0xFFF2A65A);
            c.drawCircle(1100, 400, 160, p);
            d.photo = b;
        }
        return d;
    }

    private static void save(Bitmap b, String name) throws Exception {
        File f = new File("build/screens/card-" + name + ".png");
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test public void shotCardHasTheRightSize() throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        Bitmap b = ShareCard.shot(c, data("Kingdom Centre at golden hour", true), false);
        assertEquals(1200, b.getWidth());
        assertEquals(675, b.getHeight());
        Bitmap s = ShareCard.shot(c, data("Kingdom Centre at golden hour", true), true);
        assertEquals(1080, s.getWidth());
        assertEquals(1920, s.getHeight());
        save(b, "shot-photo");
        save(s, "story-photo");
    }

    @Test public void withAndWithoutPhoto() throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        Bitmap with = ShareCard.shot(c, data("Kingdom Centre", true), false);
        Bitmap without = ShareCard.shot(c, data("Kingdom Centre", false), false);
        assertNotEquals(0xFFECEBE7, with.getPixel(1000, 340));       // the photo fills its box
        save(without, "shot-sketch");
        assertNotNull(without);
    }

    @Test public void longTitleFits() throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        StringBuilder t = new StringBuilder("برج المملكة عند غروب الشمس مع Kingdom Centre and the whole Olaya skyline ");
        while (t.length() < 160) t.append("برج ");
        Bitmap b = ShareCard.shot(c, data(t.toString(), true), false);
        assertEquals(1200, b.getWidth());
        save(b, "long-title");
    }

    @Test public void tripOfOneAndFive() throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        List<ShareCard.Data> one = new ArrayList<>(), five = new ArrayList<>();
        one.add(data("Kingdom Centre", true));
        for (int i = 0; i < 5; i++) five.add(data("Shot " + i, i % 2 == 0));
        Bitmap a = ShareCard.trip(c, "Riyadh", "Nov 26 to 28", one, false);
        Bitmap b = ShareCard.trip(c, "Riyadh", "Nov 26 to 28", five, false);
        Bitmap s = ShareCard.trip(c, "Riyadh", "Nov 26 to 28", five, true);
        assertEquals(1200, a.getWidth());
        assertEquals(1200, b.getWidth());
        assertEquals(1920, s.getHeight());
        save(a, "trip-1");
        save(b, "trip-5");
        save(s, "trip-story");
    }
}
