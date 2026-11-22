package dev.qawalah.sunlighn;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.view.MotionEvent;
import android.view.View;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.TilesOverlay;

/** The flat map (osmdroid): the screen tests, the 3D map turned off, and phones MapLibre cannot run on. */
final class OsmMap extends GeoMap {
    private final MapView map;
    private final ScaleBarOverlay scaleBar;

    OsmMap(Activity a, Ui.Palette p, Layer layer, Listener l) {
        map = new MapView(a);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true); // street names stay readable on a phone screen
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(2.0);
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint pt) {
                l.onTap(pt);
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint pt) {
                l.onHold(pt);
                return true;
            }
        }));
        float sd = a.getResources().getDisplayMetrics().scaledDensity, d = a.getResources().getDisplayMetrics().density;
        scaleBar = new ScaleBarOverlay(map);
        scaleBar.setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric);
        scaleBar.setAlignBottom(true);
        scaleBar.setLineWidth(2f);
        scaleBar.setTextSize(11f * sd);
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(p.text);
        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeWidth(2f * d);
        scaleBar.setBarPaint(bar);
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(p.text);
        txt.setTextSize(11f * sd);
        txt.setTypeface(Ui.font(a, 600));
        scaleBar.setTextPaint(txt);
        map.getOverlays().add(scaleBar);
        final GeoMap self = this;
        map.getOverlays().add(new Overlay() {
            @Override public boolean onTouchEvent(MotionEvent e, MapView mv) { return layer.onTouch(e, self); }
            @Override public void draw(Canvas c, MapView mv, boolean shadow) { if (!shadow) layer.draw(c, self); }
        });
    }

    @Override View view() { return map; }

    @Override boolean ready() { return true; }

    @Override Point toPixels(GeoPoint g) { return map.getProjection().toPixels(g, null); }

    @Override GeoPoint fromPixels(float x, float y) {
        org.osmdroid.api.IGeoPoint g = map.getProjection().fromPixels((int) x, (int) y);
        return new GeoPoint(g.getLatitude(), g.getLongitude());
    }

    @Override GeoPoint center() {
        org.osmdroid.api.IGeoPoint c = map.getMapCenter();
        return new GeoPoint(c.getLatitude(), c.getLongitude());
    }

    @Override double zoom() { return map.getZoomLevelDouble(); }

    @Override void setCenter(GeoPoint g, double zoom) {
        if (!Double.isNaN(zoom)) map.getController().setZoom(zoom);
        map.getController().setCenter(g);
    }

    @Override void animateTo(GeoPoint g, double zoom) {
        if (!Double.isNaN(zoom)) map.getController().setZoom(zoom);
        map.getController().animateTo(g);
    }

    @Override void fit(double north, double east, double south, double west, int pad) {
        // zoomToBoundingBox never returns on a map that has no size yet, so wait for the first layout.
        if (!map.isLayoutOccurred() || map.getWidth() == 0 || map.getHeight() == 0) {
            map.addOnFirstLayoutListener((v, l, t, r, b) -> fit(north, east, south, west, pad));
            return;
        }
        // 3.23 (sweep S2): padding wider than the map (a map squeezed to 66 px under the sheet) never returns
        int room = Math.min(map.getWidth(), map.getHeight()) / 2 - Math.max(2, Math.min(map.getWidth(), map.getHeight()) / 8);
        map.zoomToBoundingBox(new BoundingBox(north, east, south, west), true, Math.max(0, Math.min(pad, room)));
    }

    @Override void setLook(Look look) {
        map.setTileSource(look.osm);
        map.setBackgroundColor(look.bg);
        TilesOverlay tiles = map.getOverlayManager().getTilesOverlay();
        tiles.setLoadingBackgroundColor(look.bg);   // no grey grid while tiles load
        tiles.setLoadingLineColor(look.line);
        tiles.setColorFilter(look.filter);
        map.invalidate();
    }

    @Override void redraw() { map.invalidate(); }

    @Override void setScaleOffset(int x, int y) { scaleBar.setScaleBarOffset(x, y); }

    @Override void onResume() { map.onResume(); }

    @Override void onPause() { map.onPause(); }

    @Override void onDestroy() { map.onDetach(); }
}
