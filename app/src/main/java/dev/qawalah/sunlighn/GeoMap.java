package dev.qawalah.sunlighn;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Point;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.osmdroid.util.GeoPoint;

/**
 * The main map behind one face (3.15, owner: "tilt to see the map in 3D"): MapLibre on the phone (it tilts, turns,
 * and stands the buildings up), osmdroid in the screen tests, where MapLibre's native engine cannot start, or when
 * the 3D map is off in Settings. Zoom is osmdroid's everywhere (a 256 px tile at zoom z); MapLibre's is one less.
 * The line, the pins and the badges are drawn by the app on top ({@link Layer}), the same on both.
 */
abstract class GeoMap {
    interface Listener {
        void onTap(GeoPoint g);
        void onHold(GeoPoint g);
        /** The 3D view came on or went off (a tilt by the key or two fingers). */
        void onTilted(boolean tilted);
        /** The map turned (3.20): degrees clockwise from north up. */
        default void onTurned(double bearing) {}
    }

    /** What the app draws over the map, and the touches it takes first (dragging a pin). */
    interface Layer {
        void draw(Canvas c, GeoMap m);
        boolean onTouch(MotionEvent e, GeoMap m);
    }

    /** One map look: the tiles for each engine, and how they are graded. */
    static final class Look {
        final org.osmdroid.tileprovider.tilesource.ITileSource osm;
        final String[] urls;          // {z}/{x}/{y} templates for MapLibre
        final int maxZoom;
        final ColorMatrixColorFilter filter;
        final int bg, line;
        Look(org.osmdroid.tileprovider.tilesource.ITileSource osm, String[] urls, int maxZoom,
             ColorMatrixColorFilter filter, int bg, int line) {
            this.osm = osm;
            this.urls = urls;
            this.maxZoom = maxZoom;
            this.filter = filter;
            this.bg = bg;
            this.line = line;
        }
    }

    /** Off in the screen tests: MapLibre is native code a computer cannot run. */
    static boolean libreAllowed = true;

    /** Is the 3D map chosen (Settings, 3D map; on by default)? */
    static boolean wanted(Context c) {
        return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getBoolean("map3d", true);
    }

    static GeoMap create(Activity a, Ui.Palette p, Layer layer, Listener l) {
        android.content.SharedPreferences sp = a.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        if (libreAllowed && wanted(a) && canRun(a)) {
            // A native crash while the 3D map starts would end the app on every launch, with Settings out of reach: the
            // start is marked, and unmarked once its first style has loaded or the screen stops. Still marked at the
            // next launch means it died: the flat map comes back, and the app says so (fellBack).
            if (sp.getBoolean("map3d_starting", false)) {
                sp.edit().putBoolean("map3d", false).putBoolean("map3d_starting", false).putBoolean("map3d_fell", true).commit();
            } else {
                try {
                    sp.edit().putBoolean("map3d_starting", true).commit();
                    return new LibreMap(a, p, layer, l);
                } catch (Throwable t) {
                    // the engine would not start (an old phone, a missing library): the flat map does the same work
                    sp.edit().putBoolean("map3d_starting", false).putBoolean("map3d", false).putBoolean("map3d_fell", true).commit();
                }
            }
        }
        return new OsmMap(a, p, layer, l);
    }

    /** Did the 3D map fail to start since last asked? (Said once, then forgotten.) */
    static boolean fellBack(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        boolean f = sp.getBoolean("map3d_fell", false);
        if (f) sp.edit().remove("map3d_fell").apply();
        return f;
    }

    /** The 3D map started well: its start is no longer marked. */
    static void started(Context c) {
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putBoolean("map3d_starting", false).apply();
    }

    /** MapLibre draws with OpenGL ES 3 and ships for Arm phones only. */
    static boolean canRun(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null || am.getDeviceConfigurationInfo().reqGlEsVersion < 0x30000) return false;
        for (String abi : Build.SUPPORTED_ABIS) if (abi.startsWith("arm")) return true;
        return false;
    }

    abstract View view();

    /** Is it ready to place things (MapLibre starts a moment after it is made)? */
    abstract boolean ready();

    /** Where a point is on the screen, in the map view's pixels; null before the map is ready. */
    abstract Point toPixels(GeoPoint g);

    abstract GeoPoint fromPixels(float x, float y);

    abstract GeoPoint center();

    /** osmdroid's zoom. */
    abstract double zoom();

    /** Moves there at once; a NaN zoom keeps the zoom. */
    abstract void setCenter(GeoPoint g, double zoom);

    /** Moves there smoothly; a NaN zoom keeps the zoom. */
    abstract void animateTo(GeoPoint g, double zoom);

    /** Shows this box with a margin of {@code pad} pixels, once the map has a size. */
    abstract void fit(double north, double east, double south, double west, int pad);

    abstract void setLook(Look look);

    /** Draw the app's layer again (something on it changed). */
    abstract void redraw();

    /** Where the scale's bottom left corner sits, from the map's bottom left. */
    abstract void setScaleOffset(int x, int y);

    /** The map's turn, degrees clockwise from north at the top (0 on the flat map). */
    double bearing() { return 0; }

    /** Can it tilt (the 3D view)? */
    boolean tilts() { return false; }

    boolean tilted() { return false; }

    /** The 3D key: tilt it (and stand the buildings up), or back to flat and north up. */
    void setTilted(boolean on) {}

    /** The North key (3.20): turn the map so north is up, the tilt kept. */
    void northUp() {}

    /**
     * Shadows on the ground (3.17): a GeoJSON FeatureCollection of polygons (longitude, latitude), drawn dark at
     * {@code alpha} (0 to 1) while the map is tilted, under the buildings. An empty collection clears them.
     */
    void setShadows(String geoJson, double alpha) {}

    void onStart() {}
    void onResume() {}
    void onPause() {}
    void onStop() {}
    void onDestroy() {}
    void onLowMemory() {}
}
