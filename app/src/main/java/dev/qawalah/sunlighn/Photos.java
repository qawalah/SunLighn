package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Result photos (3.35, owner: "possibility to add result picture"): up to {@link #MAX} per shot, kept on the phone in
 * {@code files/photos/<shot id>-N.jpg}. Every photo is cleaned on the way in: turned upright, scaled to
 * {@link #SIDE} px on the long side and re-encoded as JPEG, so nothing of the original's EXIF (place, time, camera) is
 * ever kept or shared.
 */
final class Photos {
    private Photos() {}

    static final int MAX = 3, SIDE = 1600;
    private static final int MAX_BYTES = 40 * 1024 * 1024;

    private static File dir(Context c) { return new File(c.getFilesDir(), "photos"); }

    /** The cleaned JPEG for this stream, or null when it is not an image (or too big to read). */
    static byte[] clean(InputStream in) {
        try {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) {
                all.write(buf, 0, n);
                if (all.size() > MAX_BYTES) return null;
            }
            byte[] bytes = all.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= SIDE) sample *= 2;   // never the whole giant in memory
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (b == null) return null;
            int rotate = 0;
            try {
                int ori = new ExifInterface(new ByteArrayInputStream(bytes))
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                rotate = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                        : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            } catch (IOException | RuntimeException ignored) {
            }
            float scale = Math.min(1f, (float) SIDE / Math.max(b.getWidth(), b.getHeight()));
            if (rotate != 0 || scale < 1f) {
                Matrix m = new Matrix();
                m.postRotate(rotate);
                m.postScale(scale, scale);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 85, out);
            return out.toByteArray();
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** Adds the picture at {@code src} to this shot; null when the shot has three already or it cannot be read. */
    static File add(Context c, String shotId, Uri src) {
        List<File> have = list(c, shotId);
        if (have.size() >= MAX) return null;
        byte[] jpeg;
        try (InputStream in = c.getContentResolver().openInputStream(src)) {
            if (in == null) return null;
            jpeg = clean(in);
        } catch (IOException | SecurityException e) {
            return null;
        }
        if (jpeg == null) return null;
        File d = dir(c);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        for (int n = 1; n <= MAX; n++) {
            File f = new File(d, shotId + "-" + n + ".jpg");
            if (f.exists()) continue;
            try (FileOutputStream o = new FileOutputStream(f)) {
                o.write(jpeg);
                return f;
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    static List<File> list(Context c, String shotId) {
        List<File> out = new ArrayList<>();
        for (int n = 1; n <= MAX; n++) {
            File f = new File(dir(c), shotId + "-" + n + ".jpg");
            if (f.isFile()) out.add(f);
        }
        return out;
    }

    static void remove(File f) {
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    static void removeAll(Context c, String shotId) {
        for (File f : list(c, shotId)) remove(f);
    }

    /** The photo as a bitmap, no larger than {@code maxSide} on its long side. */
    static Bitmap decode(File f, int maxSide) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
