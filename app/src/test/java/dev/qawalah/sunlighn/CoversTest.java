package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;

/** City covers (3.30): a design Claude could send is read and drawn, any picture becomes a 3:1 banner, the setting keeps. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CoversTest {
    static final String RIYADH = "{\"shapes\":["
            + "{\"kind\":\"block\",\"x\":0.05,\"width\":0.05,\"height\":0.25,\"layer\":1},"
            + "{\"kind\":\"block\",\"x\":0.12,\"width\":0.04,\"height\":0.35,\"layer\":0},"
            + "{\"kind\":\"spire\",\"x\":0.2,\"width\":0.04,\"height\":0.45,\"layer\":2},"
            + "{\"kind\":\"block\",\"x\":0.28,\"width\":0.05,\"height\":0.3,\"layer\":0},"
            + "{\"kind\":\"tower\",\"x\":0.36,\"width\":0.05,\"height\":0.55,\"layer\":1},"
            + "{\"kind\":\"opening\",\"x\":0.5,\"width\":0.1,\"height\":1.0,\"layer\":0},"
            + "{\"kind\":\"block\",\"x\":0.62,\"width\":0.05,\"height\":0.4,\"layer\":0},"
            + "{\"kind\":\"spire\",\"x\":0.7,\"width\":0.04,\"height\":0.6,\"layer\":1},"
            + "{\"kind\":\"block\",\"x\":0.78,\"width\":0.06,\"height\":0.3,\"layer\":0},"
            + "{\"kind\":\"dome\",\"x\":0.86,\"width\":0.06,\"height\":0.25,\"layer\":0},"
            + "{\"kind\":\"block\",\"x\":0.94,\"width\":0.05,\"height\":0.35,\"layer\":2},"
            + "{\"kind\":\"made-up\",\"x\":1.5,\"width\":-1,\"height\":9,\"layer\":7}]}";

    @Test public void aDesignIsReadAndDrawn() throws Exception {
        Covers.Design d = new ObjectMapper().readValue(RIYADH, Covers.Design.class);
        assertEquals(12, d.shapes.size());
        Bitmap b = Covers.drawn(d);
        assertEquals(Covers.W, b.getWidth());
        assertEquals(Covers.H, b.getHeight());
        File out = new File("build/screens/cover-riyadh.png");
        out.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(out)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    /** 3.31: the schema is written by hand (the SDK's derivation crashed on Android) and Claude's answer is read back. */
    @Test public void theSchemaAndTheAnswer() {
        String schema = Covers.schema()._additionalProperties().toString();
        assertTrue(schema, schema.contains("shapes"));
        assertTrue(schema, schema.contains("layer"));
        Covers.Design d = Covers.parse(RIYADH.replace("{\"shapes\"", "{\"note\":\"extra\",\"shapes\""));
        assertNotNull(d);
        assertEquals(12, d.shapes.size());
        assertNull(Covers.parse("not json"));
        Covers.Design pct = Covers.parse("{\"shapes\":[{\"kind\":\"tower\",\"x\":50,\"width\":5,\"height\":90,\"layer\":0}]}");
        assertEquals(0.5, pct.shapes.get(0).x, 1e-9);
        assertEquals(0.9, pct.shapes.get(0).height, 1e-9);
    }

    /** 3.31.1: a subscription's sign-in is not an API key; a refused key is said, not logged as a crash. */
    @Test public void keysAndRefusals() {
        assertTrue(Covers.looksLikeApiKey("sk-ant-api03-abc"));
        assertTrue(Covers.looksLikeApiKey("sk-ant-usr-abc"));
        assertFalse(Covers.looksLikeApiKey("sk-ant-oat01-abc"));
        assertFalse(Covers.looksLikeApiKey("hello"));
        assertTrue(Covers.expected(new java.io.IOException("no network")));
        assertFalse(Covers.expected(new IllegalStateException("bug")));
        assertTrue(Covers.reason(new java.io.IOException("401 Invalid bearer token")).contains("console.anthropic.com"));
    }

    @Test public void anEmptyDesignStillDraws() {
        assertNotNull(Covers.drawn(new Covers.Design()));
        assertNull(Covers.drawn(null));
    }

    @Test public void anyPictureBecomesABanner() {
        Bitmap tall = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888);
        Bitmap b = Covers.banner(tall);
        assertEquals(Covers.W, b.getWidth());
        assertEquals(Covers.H, b.getHeight());
    }

    @Test public void theSettingAndKeysKeep() {
        Context c = RuntimeEnvironment.getApplication();
        assertEquals(Covers.OFF, Covers.source(c));
        Covers.setSource(c, Covers.CLAUDE);
        Covers.setKey(c, Covers.CLAUDE, "  sk-ant-test ");
        assertEquals(Covers.CLAUDE, Covers.source(c));
        assertEquals("sk-ant-test", Covers.key(c, Covers.CLAUDE));
        assertTrue(Covers.on(c));
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putInt("cover_source", 1).apply();   // 3.30's Photo
        assertEquals(Covers.OFF, Covers.source(c));
        assertFalse(Covers.on(c));
        assertNull(Covers.cached(c, "Lisbon"));
    }
}
