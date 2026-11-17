package dev.qawalah.sunlighn;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.module.http.HttpRequestUtil;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.osmdroid.util.GeoPoint;

import java.util.Locale;

/**
 * The 3D map (3.15): MapLibre with the same raster looks as the flat map, graded with the same colour matrices (the map
 * is drawn into a TextureView, whose layer paint takes the filter), and OpenFreeMap's buildings standing up at their
 * heights while the map is tilted. Two fingers up or down tilt it, two fingers turn it, the 3D key does both and undoes
 * them. The app's line and pins are drawn on a view above it, from the same projection.
 */
final class LibreMap extends GeoMap {
    /** MapLibre's zoom for a 512 px tile is osmdroid's for a 256 px tile less one. */
    private static final double ZOOM_SHIFT = 1;
    private static final double TILT = 55, TILTED_FROM = 8;
    private static boolean agentSet;

    private final Activity a;
    private final Ui.Palette p;
    private final Layer layer;
    private final Listener listener;
    private final MapView mv;
    private final FrameLayout frame;
    private final View over;
    private MapLibreMap ml;
    private Look look;
    private boolean tilted;
    private String shadowJson = EMPTY;
    private double shadowAlpha;
    private static final String EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}";
    private final Paint scaleLine = new Paint(Paint.ANTI_ALIAS_FLAG), scaleText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int scaleX, scaleY;

    // asked for before the map was ready
    private GeoPoint wantCenter = new GeoPoint(20.0, 10.0);
    private double wantZoom = 3;
    private double[] wantFit;
    private int wantPad;

    LibreMap(Activity a, Ui.Palette p, Layer layer, Listener l) {
        this.a = a;
        this.p = p;
        this.layer = layer;
        this.listener = l;
        MapLibre.getInstance(a.getApplicationContext());
        if (!agentSet) {
            // The tile servers ask every app to say who it is (OpenStreetMap's tile policy).
            HttpRequestUtil.setOkHttpClient(new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> chain.proceed(
                    chain.request().newBuilder().header("User-Agent", Places.USER_AGENT).build())).build());
            agentSet = true;
        }
        MapLibreMapOptions o = MapLibreMapOptions.createFromAttributes(a)
                .textureMode(true)                       // a TextureView, so its layer paint can grade the colours
                .foregroundLoadColor(p.bg)
                .logoEnabled(false)
                .attributionEnabled(false)               // the credit is the app's own line, as on the flat map
                .compassEnabled(false)
                .minZoomPreference(2 - ZOOM_SHIFT)
                .maxPitchPreference(60)
                .rotateGesturesEnabled(true)
                .tiltGesturesEnabled(true);
        mv = new MapView(a, o);
        mv.onCreate(null);

        frame = new FrameLayout(a) {
            private boolean grabbing;

            // A finger that lands on a pin drags it (the layer takes the touch); anything else moves the map.
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                int act = e.getActionMasked();
                if (act == MotionEvent.ACTION_DOWN) grabbing = ml != null && layer.onTouch(e, LibreMap.this);
                else if (grabbing) {
                    layer.onTouch(e, LibreMap.this);
                    if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) grabbing = false;
                    return true;
                }
                if (grabbing) return true;
                return super.dispatchTouchEvent(e);
            }

            @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                super.onSizeChanged(w, h, ow, oh);
                if (wantFit != null && ml != null && w > 0 && h > 0) post(LibreMap.this::fitNow);
            }
        };
        frame.setBackgroundColor(p.bg);
        frame.addView(mv, new FrameLayout.LayoutParams(-1, -1));
        over = new View(a) {
            @Override protected void onDraw(Canvas c) {
                if (ml == null) return;
                layer.draw(c, LibreMap.this);
                drawScale(c);
            }
        };
        frame.addView(over, new FrameLayout.LayoutParams(-1, -1));

        float sd = a.getResources().getDisplayMetrics().scaledDensity, d = a.getResources().getDisplayMetrics().density;
        scaleLine.setColor(p.text);
        scaleLine.setStyle(Paint.Style.STROKE);
        scaleLine.setStrokeWidth(2f * d);
        scaleText.setColor(p.text);
        scaleText.setTextSize(11f * sd);
        scaleText.setTypeface(Ui.font(a, 600));

        mv.getMapAsync(m -> {
            ml = m;
            m.addOnMapClickListener(pt -> {
                l.onTap(new GeoPoint(pt.getLatitude(), pt.getLongitude()));
                return true;
            });
            m.addOnMapLongClickListener(pt -> {
                l.onHold(new GeoPoint(pt.getLatitude(), pt.getLongitude()));
                return true;
            });
            m.addOnCameraMoveListener(this::moved);
            m.addOnCameraIdleListener(this::moved);
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(wantCenter.getLatitude(), wantCenter.getLongitude()),
                    wantZoom - ZOOM_SHIFT));
            if (look != null) applyLook();
            if (wantFit != null) fitNow();
            over.invalidate();
        });
    }

    /** The camera moved: the layer follows, and the buildings stand up or lie down with the tilt. */
    private void moved() {
        over.invalidate();
        boolean t = ml.getCameraPosition().tilt > TILTED_FROM;
        if (t != tilted) {
            tilted = t;
            showBuildings();
            listener.onTilted(t);
        }
    }

    private void showBuildings() {
        Style s = ml == null ? null : ml.getStyle();
        if (s == null || !s.isFullyLoaded()) return;
        org.maplibre.android.style.layers.Layer b = s.getLayer("buildings-3d");
        if (b != null) b.setProperties(PropertyFactory.visibility(tilted ? Property.VISIBLE : Property.NONE));
        org.maplibre.android.style.layers.Layer sh = s.getLayer("shadows");
        if (sh != null) {
            sh.setProperties(PropertyFactory.visibility(tilted ? Property.VISIBLE : Property.NONE),
                    PropertyFactory.fillOpacity((float) shadowAlpha));
        }
        org.maplibre.android.style.sources.GeoJsonSource src = s.getSourceAs("shadows");
        if (src != null) src.setGeoJson(shadowJson);
    }

    @Override void setShadows(String geoJson, double alpha) {
        shadowJson = geoJson == null ? EMPTY : geoJson;
        shadowAlpha = alpha;
        showBuildings();
    }

    @Override View view() { return frame; }

    @Override boolean ready() { return ml != null; }

    @Override Point toPixels(GeoPoint g) {
        if (ml == null) return null;
        PointF q = ml.getProjection().toScreenLocation(new LatLng(g.getLatitude(), g.getLongitude()));
        return new Point(Math.round(q.x), Math.round(q.y));
    }

    @Override GeoPoint fromPixels(float x, float y) {
        if (ml == null) return center();
        LatLng l = ml.getProjection().fromScreenLocation(new PointF(x, y));
        return new GeoPoint(l.getLatitude(), l.getLongitude());
    }

    @Override GeoPoint center() {
        if (ml == null) return wantCenter;
        LatLng t = ml.getCameraPosition().target;
        return t == null ? wantCenter : new GeoPoint(t.getLatitude(), t.getLongitude());
    }

    @Override double zoom() { return ml == null ? wantZoom : ml.getCameraPosition().zoom + ZOOM_SHIFT; }

    @Override void setCenter(GeoPoint g, double zoom) {
        wantFit = null;
        if (ml == null) {
            wantCenter = g;
            if (!Double.isNaN(zoom)) wantZoom = zoom;
            return;
        }
        LatLng t = new LatLng(g.getLatitude(), g.getLongitude());
        ml.moveCamera(Double.isNaN(zoom) ? CameraUpdateFactory.newLatLng(t) : CameraUpdateFactory.newLatLngZoom(t, zoom - ZOOM_SHIFT));
    }

    @Override void animateTo(GeoPoint g, double zoom) {
        if (ml == null) {
            setCenter(g, zoom);
            return;
        }
        LatLng t = new LatLng(g.getLatitude(), g.getLongitude());
        ml.animateCamera(Double.isNaN(zoom) ? CameraUpdateFactory.newLatLng(t) : CameraUpdateFactory.newLatLngZoom(t, zoom - ZOOM_SHIFT), 500);
    }

    @Override void fit(double north, double east, double south, double west, int pad) {
        wantFit = new double[]{north, east, south, west};
        wantPad = pad;
        if (ml != null && frame.getWidth() > 0 && frame.getHeight() > 0) fitNow();
    }

    private void fitNow() {
        if (wantFit == null || ml == null) return;
        double[] f = wantFit;
        wantFit = null;
        LatLngBounds b = new LatLngBounds.Builder().include(new LatLng(f[0], f[1])).include(new LatLng(f[2], f[3])).build();
        CameraPosition now = ml.getCameraPosition();
        CameraPosition c = ml.getCameraForLatLngBounds(b, new int[]{wantPad, wantPad, wantPad, wantPad}, now.bearing, now.tilt);
        if (c != null) ml.animateCamera(CameraUpdateFactory.newCameraPosition(c), 500);
    }

    @Override void setLook(Look look) {
        this.look = look;
        frame.setBackgroundColor(look.bg);
        if (ml != null) applyLook();
    }

    private void applyLook() {
        ml.setStyle(new Style.Builder().fromJson(styleJson(look)), s -> {
            GeoMap.started(a);
            showBuildings();
        });
        // The same grading as the flat map: the colour matrix on the whole picture (the map and its buildings).
        TextureView tv = find(mv);
        if (tv != null) {
            Paint paint = new Paint();
            paint.setColorFilter(look.filter);
            tv.setLayerPaint(look.filter == null ? null : paint);
        } else {
            mv.post(() -> {
                TextureView later = find(mv);
                if (later != null && look != null) {
                    Paint paint = new Paint();
                    paint.setColorFilter(look.filter);
                    later.setLayerPaint(look.filter == null ? null : paint);
                }
            });
        }
    }

    private static TextureView find(View v) {
        if (v instanceof TextureView) return (TextureView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextureView t = find(g.getChildAt(i));
                if (t != null) return t;
            }
        }
        return null;
    }

    /**
     * The look as a MapLibre style: the raster tiles, and OpenFreeMap's buildings as walls at their heights (shown
     * while tilted). A height the tile writes as 5 m is not mapped: about three floors, as in the sums (Buildings).
     */
    static String styleJson(Look look) {
        try {
            JSONObject base = new JSONObject().put("type", "raster").put("tileSize", 256).put("maxzoom", look.maxZoom)
                    .put("tiles", new JSONArray(java.util.Arrays.asList(look.urls)));
            JSONObject ofm = new JSONObject().put("type", "vector").put("url", "https://tiles.openfreemap.org/planet");
            JSONArray layers = new JSONArray()
                    .put(new JSONObject().put("id", "bg").put("type", "background")
                            .put("paint", new JSONObject().put("background-color", hex(look.bg))))
                    .put(new JSONObject().put("id", "base").put("type", "raster").put("source", "base")
                            .put("paint", new JSONObject().put("raster-fade-duration", 120)))
                    .put(new JSONObject().put("id", "shadows").put("type", "fill").put("source", "shadows")
                            .put("layout", new JSONObject().put("visibility", "none"))
                            .put("paint", new JSONObject().put("fill-color", "#000000").put("fill-opacity", 0.3)
                                    .put("fill-antialias", true)))
                    .put(new JSONObject().put("id", "buildings-3d").put("type", "fill-extrusion").put("source", "ofm")
                            .put("source-layer", "building").put("minzoom", 13)
                            .put("layout", new JSONObject().put("visibility", "none"))
                            .put("paint", new JSONObject()
                                    .put("fill-extrusion-color", "#D9D3CA")
                                    .put("fill-extrusion-opacity", 0.92)
                                    .put("fill-extrusion-height", new JSONArray("[\"case\",[\"==\",[\"get\",\"render_height\"],5],"
                                            + Buildings.GUESS + ",[\"get\",\"render_height\"]]"))
                                    .put("fill-extrusion-base", new JSONArray("[\"get\",\"render_min_height\"]"))));
            JSONObject shadows = new JSONObject().put("type", "geojson")
                    .put("data", new JSONObject().put("type", "FeatureCollection").put("features", new JSONArray()));
            return new JSONObject().put("version", 8)
                    .put("sources", new JSONObject().put("base", base).put("ofm", ofm).put("shadows", shadows))
                    .put("layers", layers).toString();
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(int c) { return String.format(Locale.US, "#%06X", c & 0xFFFFFF); }

    @Override void redraw() { over.invalidate(); }

    @Override void setScaleOffset(int x, int y) {
        scaleX = x;
        scaleY = y;
        over.invalidate();
    }

    /** The scale, like the flat map's: flat only (a tilted map has no one scale). */
    private void drawScale(Canvas c) {
        if (tilted || ml == null) return;
        double lat = center().getLatitude();
        double mpp = ml.getProjection().getMetersPerPixelAtLatitude(lat);
        if (!(mpp > 0)) return;
        float most = 100 * a.getResources().getDisplayMetrics().density;
        double want = mpp * most, step = Math.pow(10, Math.floor(Math.log10(want)));
        double m = want >= 5 * step ? 5 * step : want >= 2 * step ? 2 * step : step;
        float len = (float) (m / mpp), x = scaleX, y = over.getHeight() - scaleY;
        float tick = 6 * a.getResources().getDisplayMetrics().density;
        c.drawLine(x, y, x + len, y, scaleLine);
        c.drawLine(x, y, x, y - tick, scaleLine);
        c.drawLine(x + len, y, x + len, y - tick, scaleLine);
        String s = m >= 1000 ? Fmt.distance(m) : Math.round(m) + " m";
        c.drawText(s, x + 4, y - tick - 2, scaleText);
    }

    @Override double bearing() { return ml == null ? 0 : ml.getCameraPosition().bearing; }

    @Override boolean tilts() { return true; }

    @Override boolean tilted() { return tilted; }

    @Override void setTilted(boolean on) {
        if (ml == null) return;
        CameraPosition now = ml.getCameraPosition();
        ml.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                .target(now.target).zoom(now.zoom).tilt(on ? TILT : 0).bearing(on ? now.bearing : 0).build()), 600);
    }

    @Override void onStart() { mv.onStart(); }
    @Override void onResume() { mv.onResume(); }
    @Override void onPause() { mv.onPause(); }
    @Override void onStop() {
        mv.onStop();
        GeoMap.started(a);              // it ran until the screen stopped: no crash at start
    }
    @Override void onDestroy() { mv.onDestroy(); }
    @Override void onLowMemory() { mv.onLowMemory(); }
}
