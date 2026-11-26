package dev.qawalah.sunlighn;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Hands the {@code .sunwhere} file being sent to the app it is sent with (3.28). Serves only the files in the cache's
 * {@link #DIR} folder, read only, to apps given the link by the share menu (not exported). No library: AndroidX is not
 * in the app.
 */
public final class ShotFileProvider extends ContentProvider {
    static final String DIR = "shared";

    static String authority(Context c) { return c.getPackageName() + ".files"; }

    static Uri uriFor(Context c, String name) {
        return new Uri.Builder().scheme("content").authority(authority(c)).appendPath(name).build();
    }

    private File file(Uri u) throws FileNotFoundException {
        String name = u.getLastPathSegment();
        if (name == null || name.contains("/") || name.startsWith(".")) throw new FileNotFoundException();
        File f = new File(new File(getContext().getCacheDir(), DIR), name);
        if (!f.isFile()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) throw new FileNotFoundException("read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try {
            f = file(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override public String getType(Uri uri) {
        String n = String.valueOf(uri.getLastPathSegment()).toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".png") ? "image/png" : ShotFile.MIME;      // 3.35: the share card
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }

    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
