package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The 3D map's style (3.15): the look's tiles, and the buildings standing up at the heights the sums use. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MapStyleTest {
    @Test public void styleHasTheTilesAndTheBuildings() throws Exception {
        GeoMap.Look look = new GeoMap.Look(null, new String[]{"https://tile.openstreetmap.org/{z}/{x}/{y}.png"}, 19, null,
                0xFFECEBE7, 0xFFCCCCCC);
        String json = LibreMap.styleJson(look);
        JSONObject s = new JSONObject(json);
        assertEquals(8, s.getInt("version"));
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                s.getJSONObject("sources").getJSONObject("base").getJSONArray("tiles").getString(0));
        JSONArray layers = s.getJSONArray("layers");
        JSONObject b = layers.getJSONObject(2);
        assertEquals("fill-extrusion", b.getString("type"));
        assertEquals("building", b.getString("source-layer"));
        assertEquals(Buildings.GUESS, b.getJSONObject("paint").getJSONArray("fill-extrusion-height").getDouble(2), 0);
        java.io.File dir = new java.io.File("build/style");
        dir.mkdirs();
        try (java.io.FileWriter w = new java.io.FileWriter(new java.io.File(dir, "map-style.json"))) {
            w.write(json);
        }
    }
}
