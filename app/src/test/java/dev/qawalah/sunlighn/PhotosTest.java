package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/** Result photos (3.35): cleaned of EXIF, upright, scaled, at most three, gone with their shot. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotosTest {
    private static byte[] jpeg(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF3366CC);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 90, o);
        return o.toByteArray();
    }

    private static Uri file(Context c, String name, byte[] bytes) throws Exception {
        File f = new File(c.getCacheDir(), name);
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(bytes); }
        return Uri.fromFile(f);
    }

    @Test public void rotatedPhotoComesOutUpright() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        File f = new File(c.getCacheDir(), "rot.jpg");
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(jpeg(400, 200)); }
        ExifInterface e = new ExifInterface(f.getPath());
        e.setAttribute(ExifInterface.TAG_ORIENTATION, "6");
        e.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "26/1,17/1,0/1");
        e.saveAttributes();
        byte[] out = Photos.clean(new java.io.FileInputStream(f));
        assertNotNull(out);
        Bitmap b = BitmapFactory.decodeByteArray(out, 0, out.length);
        assertEquals(200, b.getWidth());
        assertEquals(400, b.getHeight());
        ExifInterface after = new ExifInterface(new ByteArrayInputStream(out));
        assertNull(after.getAttribute(ExifInterface.TAG_GPS_LATITUDE));
        assertTrue(after.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) <= 1);
    }

    @Test public void hugeImageIsScaledNotLoadedWhole() {
        byte[] out = Photos.clean(new ByteArrayInputStream(jpeg(6000, 4000)));
        assertNotNull(out);
        Bitmap b = BitmapFactory.decodeByteArray(out, 0, out.length);
        assertEquals(Photos.SIDE, Math.max(b.getWidth(), b.getHeight()));
    }

    @Test public void notAnImageIsRefused() {
        assertNull(Photos.clean(new ByteArrayInputStream("%PDF-1.4 not a picture".getBytes())));
        assertNull(Photos.clean(new ByteArrayInputStream(new byte[0])));
    }

    @Test public void fourthPhotoIsRefused() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Uri u = file(c, "p.jpg", jpeg(100, 80));
        for (int i = 0; i < 3; i++) assertNotNull(Photos.add(c, "shot-a", u));
        assertNull(Photos.add(c, "shot-a", u));
        assertEquals(3, Photos.list(c, "shot-a").size());
        Photos.remove(Photos.list(c, "shot-a").get(0));
        assertNotNull(Photos.add(c, "shot-a", u));             // a freed place is used again
        Photos.removeAll(c, "shot-a");
        assertTrue(Photos.list(c, "shot-a").isEmpty());
    }

    @Test public void removingTheShotRemovesItsPhotos() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Trip.Line l = Trip.addShot(c, new Trip.Line("x", "A", 26.28, 50.21, 26.29, 50.22, 0));
        assertNotNull(Photos.add(c, l.id, file(c, "q.jpg", jpeg(50, 50))));
        Trip.removeShot(c, l.id);
        assertTrue(Photos.list(c, l.id).isEmpty());
    }
}
