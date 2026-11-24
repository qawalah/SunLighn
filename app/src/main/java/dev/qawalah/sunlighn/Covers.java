package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * City covers (3.30, owner: "connect to an api to generate city covers"; 3.31: "api only for now"). A banner over each
 * city in Saved, made once per city and kept on the phone: Claude designs a pocket map's cover for the city (its
 * skyline and landmarks as shapes, through the Anthropic SDK with structured output), and the app draws it
 * ({@link #draw}). It uses the owner's own Anthropic API key, kept in this phone's settings, and costs a little of that
 * account's credit per new city. Nothing is asked without a network, and a city is asked once per run of the app.
 * (3.30's free Photo and GPT sources went in 3.31; a phone set to either reads as Off.)
 */
final class Covers {
    private Covers() {}

    static final int OFF = 0, CLAUDE = 2;        // the stored values of 3.30 (1 and 3 were Photo and GPT)
    static final String[] NAMES = {"Off", "Claude"};
    static final int W = 1080, H = 360;       // a 3:1 banner
    static final String CLAUDE_MODEL = "claude-opus-5-5";

    interface Done { void cover(Bitmap b); }

    private static final Map<String, Bitmap> memory = new HashMap<>();
    private static final Set<String> asked = new HashSet<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    /** What went wrong with the last cover asked for, said under the setting; null when it worked. */
    static volatile String lastError;

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE); }

    static int source(Context c) { return prefs(c).getInt("cover_source", OFF) == CLAUDE ? CLAUDE : OFF; }

    /** On when Claude is chosen and its key is there. */
    static boolean on(Context c) { return source(c) == CLAUDE && !key(c, CLAUDE).isEmpty(); }

    static void setSource(Context c, int s) {
        prefs(c).edit().putInt("cover_source", s).apply();
        asked.clear();
        lastError = null;
    }

    static String key(Context c, int s) {
        return s == CLAUDE ? prefs(c).getString("claude_key", "") : "";
    }

    static void setKey(Context c, int s, String k) {
        prefs(c).edit().putString("claude_key", k.trim()).apply();
        asked.clear();
        lastError = null;
    }

    static boolean needsKey(int s) { return s == CLAUDE; }

    static boolean online(Context c) {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        @SuppressWarnings("deprecation") android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private static String slug(String city) {
        String s = city.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? "city" : s;
    }

    private static File file(Context c, String city, int s) {
        return new File(new File(c.getFilesDir(), "covers"), slug(city) + "-" + s + ".png");
    }

    /** The cover already made for this city, if any, without asking anyone. */
    static Bitmap cached(Context c, String city) {
        int s = source(c);
        if (s == OFF) return null;
        String k = s + ":" + city;
        Bitmap b = memory.get(k);
        if (b != null) return b;
        File f = file(c, city, s);
        if (!f.isFile()) return null;
        b = BitmapFactory.decodeFile(f.getPath());
        if (b != null) memory.put(k, b);
        return b;
    }

    /**
     * Makes the city's cover if it has none yet: once per run, only with the setting on (and its key) and a network.
     * The answer comes on the main thread; nothing comes when it fails (the reason is in {@link #lastError}).
     */
    static void make(Context c, String city, double lat, double lon, boolean online, Done done) {
        int s = source(c);
        if (s == OFF || city == null || city.isEmpty() || !online || cached(c, city) != null) return;
        if (needsKey(s) && key(c, s).isEmpty()) return;
        String k = s + ":" + city;
        if (!asked.add(k)) return;
        Context app = c.getApplicationContext();
        String apiKey = key(c, s);
        new Thread(() -> {
            Bitmap b = null;
            try {
                b = drawn(claude(apiKey, city, lat, lon));
                if (b == null) lastError = "No cover came back for " + city + ".";
            } catch (Throwable e) {                  // never let a cover close the app (3.30: NoSuchMethodError)
                lastError = reason(e);
                CrashLog.caught(app, "cover", e);
            }
            if (b == null) return;
            b = banner(b);
            File f = file(app, city, s);
            //noinspection ResultOfMethodCallIgnored
            f.getParentFile().mkdirs();
            try (OutputStream o = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, o);
            } catch (IOException ignored) {
            }
            lastError = null;
            final Bitmap done1 = b;
            main.post(() -> {
                memory.put(k, done1);
                done.cover(done1);
            });
        }).start();
    }

    /**
     * Asks for the Anthropic API key (Settings, and Saved's Add city covers). Saved, it turns covers on and runs
     * {@code then}.
     */
    static void askKey(Context c, Ui.Palette p, Runnable then) {
        android.widget.EditText field = new android.widget.EditText(c);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setHint("sk-ant-…");
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(c);
        int s = Ui.dp(c, 20);
        wrap.setPadding(s, Ui.dp(c, 4), s, 0);
        wrap.addView(field);
        new android.app.AlertDialog.Builder(c, p.night ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("Your Anthropic API key")
                .setMessage("Claude draws a cover for each city in Saved. The key is kept only on this phone and sent only "
                        + "to Anthropic. Each new city spends about 2 US cents of the key's credit (not a chat plan).")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    String k = field.getText().toString().trim();
                    if (k.isEmpty()) return;
                    setKey(c, CLAUDE, k);
                    setSource(c, CLAUDE);
                    if (then != null) then.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Words for a failure the owner can act on (a wrong key, no credit, no network). */
    static String reason(Throwable e) {
        String m = String.valueOf(e.getMessage());
        if (e instanceof com.anthropic.errors.UnauthorizedException || m.contains("401"))
            return "The key was not accepted. Check it in Settings, City covers.";
        if (e instanceof com.anthropic.errors.RateLimitException || m.contains("429"))
            return "The service is busy or the account is out of credit. It is tried again next time Saved opens.";
        if (e instanceof IOException || e instanceof com.anthropic.errors.AnthropicIoException)
            return "The cover could not be fetched (no network?).";
        return "The cover could not be made: " + (m.length() > 120 ? m.substring(0, 120) + "…" : m);
    }

    // ---- Claude: a poster designed as shapes ----

    /** One shape of the skyline, in fractions of the skyline's own box (its words for Claude: {@link #schema}). */
    public static final class Shape {
        public String kind;
        public double x;
        public double width;
        public double height;
        public int layer;
    }

    /** The skyline Claude designs for a city; the app draws it as a pocket map's cover (owner's picture, 3.30). */
    public static final class Design {
        public List<Shape> shapes = new ArrayList<>();
    }

    static Design claude(String apiKey, String city, double lat, double lon) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        String prompt = String.format(Locale.US,
                "Design the skyline of %s (near %.3f, %.3f) for the cover of a printed pocket city map: a calm, "
                        + "minimal row of grey silhouettes standing on one line, in the spirit of a classic folded map's "
                        + "cover. Use its real, recognisable skyline: its signature landmark tallest and near the middle "
                        + "(for example Kingdom Centre in Riyadh, Burj Khalifa in Dubai), ordinary buildings lower around "
                        + "it, a few far ones behind. Use only the shapes listed.",
                city, lat, lon);
        try {
            // The schema is written out here, not derived from Design.class: the SDK's derivation reads
            // Field.getAnnotatedType, which Android does not have (3.30 crashed on the phone; 3.31).
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(CLAUDE_MODEL)
                    .maxTokens(16000L)
                    // A refused request is answered by another model instead of stopping (server-side fallback, beta)
                    .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder()
                            .schema(schema()).build()).build())
                    .addUserMessage(prompt)
                    .build();
            Message m = client.messages().create(params);
            if (m.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
                lastError = "Claude declined to draw this city.";
                return null;
            }
            StringBuilder text = new StringBuilder();
            m.content().forEach(cb -> cb.text().ifPresent(t -> text.append(t.text())));
            return parse(text.toString());
        } finally {
            client.close();
        }
    }

    /** The JSON schema of {@link Design}, by hand (see {@link #claude}). */
    static JsonOutputFormat.Schema schema() {
        Map<String, Object> shape = new java.util.LinkedHashMap<>();
        shape.put("type", "object");
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("kind", prop("string", "One of: block, tower, spire, dome, minaret, arch (a gateway open at its foot), "
                + "opening (a slim tower with an opening near its top, like Kingdom Centre), mast, wheel, hill, tree, bridge"));
        props.put("x", prop("number", "Horizontal centre within the skyline, 0 (left end) to 1 (right end)"));
        props.put("width", prop("number", "Width as a fraction of the skyline's width, 0.01 to 0.4"));
        props.put("height", prop("number", "Height as a fraction of the skyline's height, 0.05 to 1 (only the signature landmark near 1)"));
        props.put("layer", prop("integer", "Distance: 0 near (darkest, drawn last) to 2 far (palest, drawn first)"));
        shape.put("properties", props);
        shape.put("required", java.util.Arrays.asList("kind", "x", "width", "height", "layer"));
        shape.put("additionalProperties", false);
        Map<String, Object> shapes = new java.util.LinkedHashMap<>();
        shapes.put("type", "array");
        shapes.put("description", "The skyline and landmarks, 10 to 30 shapes, recognisable for this city, its signature "
                + "landmark the tallest and near the middle");
        shapes.put("items", shape);
        Map<String, Object> top = new java.util.LinkedHashMap<>();
        top.put("shapes", shapes);
        return JsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(top))
                .putAdditionalProperty("required", JsonValue.from(java.util.Collections.singletonList("shapes")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("type", type);
        m.put("description", description);
        return m;
    }

    /** Claude's answer as a design; null when it is not one. Unknown fields are ignored. */
    static Design parse(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(json, Design.class);
        } catch (Exception e) {
            lastError = "Claude's design could not be read.";
            return null;
        }
    }

    static Bitmap drawn(Design d) {
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        draw(new Canvas(b), d, W, H);
        return b;
    }

    private static int colour(String hex, int fallback) {
        try {
            return hex == null ? fallback : android.graphics.Color.parseColor(hex.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (android.graphics.Color.red(a) + (android.graphics.Color.red(b) - android.graphics.Color.red(a)) * t);
        int g = (int) (android.graphics.Color.green(a) + (android.graphics.Color.green(b) - android.graphics.Color.green(a)) * t);
        int bl = (int) (android.graphics.Color.blue(a) + (android.graphics.Color.blue(b) - android.graphics.Color.blue(a)) * t);
        return android.graphics.Color.rgb(r, g, bl);
    }

    static final int PAPER = 0xFFECEBE7, DISC = 0xFFEE5A1E, LINE = 0xFF4A4946;

    /**
     * Draws a design as a pocket map's cover (owner's picture of Braun-style map booklets, 3.30): paper, a large orange
     * quarter disc in the top corner (the sun), the skyline in greys on the right on one ruled line, paler far away and
     * fading towards its foot, over a pale grey band. The city's name is set beside it by Saved, in dark type.
     */
    static void draw(Canvas cv, Design d, int w, int h) {
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        cv.drawColor(PAPER);
        pt.setColor(DISC);
        cv.drawCircle(w, 0, h * 0.78f, pt);

        float left = w * 0.40f, right = w * 0.97f, base = h * 0.80f, tall = h * 0.66f;
        Path band = new Path();                          // the slanted grey ground under the skyline
        band.moveTo(left - w * 0.04f, base);
        band.lineTo(w, base);
        band.lineTo(w, base + h * 0.12f);
        band.lineTo(left + w * 0.10f, base + h * 0.05f);
        band.close();
        pt.setColor(0xFFD3D2CE);
        cv.drawPath(band, pt);

        List<Shape> shapes = d.shapes == null ? new ArrayList<>() : new ArrayList<>(d.shapes);
        shapes.sort((a, b) -> Integer.compare(b.layer, a.layer));          // far first
        int[] tops = {0xFF5E5D5A, 0xFF8E8D89, 0xFFB4B3AF};
        for (Shape s : shapes) {
            if (s == null || s.kind == null) continue;
            int layer = (int) clamp(s.layer, 0, 2);
            pt.setShader(new LinearGradient(0, base - tall, 0, base, tops[layer], mix(tops[layer], PAPER, 0.45f),
                    Shader.TileMode.CLAMP));
            float x = (float) (left + (right - left) * clamp(s.x, 0, 1));
            shape(cv, pt, s.kind.toLowerCase(Locale.ROOT), x, (float) ((right - left) * clamp(s.width, 0.01, 0.4)),
                    (float) (tall * clamp(s.height, 0.05, 1)), base);
        }
        pt.setShader(null);
        pt.setColor(LINE);
        cv.drawRect(w * 0.06f, base, w, base + Math.max(2, h / 180f), pt);
    }

    private static void shape(Canvas cv, Paint pt, String kind, float x, float wd, float ht, float base) {
        float l = x - wd / 2, r = x + wd / 2, top = base - ht;
        Path p = new Path();
        switch (kind) {
            case "tower":
                cv.drawRect(x - wd * 0.18f, top + ht * 0.12f, x + wd * 0.18f, base, pt);
                cv.drawRect(l, top + ht * 0.1f, r, top + ht * 0.2f, pt);
                cv.drawRect(x - 2, top, x + 2, top + ht * 0.12f, pt);
                break;
            case "spire":
            case "minaret":
                cv.drawRect(x - wd * 0.25f, top + ht * 0.25f, x + wd * 0.25f, base, pt);
                p.moveTo(x - wd * 0.25f, top + ht * 0.25f);
                p.lineTo(x, top);
                p.lineTo(x + wd * 0.25f, top + ht * 0.25f);
                p.close();
                cv.drawPath(p, pt);
                if (kind.equals("minaret")) cv.drawRect(l + wd * 0.15f, top + ht * 0.4f, r - wd * 0.15f, top + ht * 0.45f, pt);
                break;
            case "dome":
                cv.drawRect(l, top + ht * 0.45f, r, base, pt);
                cv.drawArc(l + wd * 0.1f, top + ht * 0.05f, r - wd * 0.1f, top + ht * 0.85f, 180, 180, true, pt);
                cv.drawRect(x - 1.5f, top, x + 1.5f, top + ht * 0.1f, pt);
                break;
            case "arch":
                p.addRect(l, top, r, base, Path.Direction.CW);
                Path hole = new Path();
                hole.addRect(l + wd * 0.25f, top + ht * 0.45f, r - wd * 0.25f, base, Path.Direction.CW);
                hole.addCircle(x, top + ht * 0.45f, wd * 0.25f, Path.Direction.CW);
                p.op(hole, Path.Op.DIFFERENCE);
                cv.drawPath(p, pt);
                break;
            case "opening": {                            // a slim tower open near its top (Kingdom Centre)
                p.addRect(l, top, r, base, Path.Direction.CW);
                Path gap = new Path();
                float hw = wd * 0.3f, hb = top + ht * 0.35f;
                gap.addRect(x - hw, top - 1, x + hw, hb - hw, Path.Direction.CW);
                gap.addCircle(x, hb - hw, hw, Path.Direction.CW);
                p.op(gap, Path.Op.DIFFERENCE);
                cv.drawPath(p, pt);
                break;
            }
            case "mast":
                p.moveTo(x - wd * 0.3f, base);
                p.lineTo(x, top);
                p.lineTo(x + wd * 0.3f, base);
                p.close();
                cv.drawPath(p, pt);
                break;
            case "wheel": {
                Paint ring = new Paint(pt);
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(Math.max(2, wd * 0.05f));
                float rad = Math.min(wd, ht) / 2;
                cv.drawCircle(x, top + rad, rad, ring);
                p.moveTo(x - rad * 0.6f, base);
                p.lineTo(x, top + rad);
                p.lineTo(x + rad * 0.6f, base);
                cv.drawPath(p, ring);
                break;
            }
            case "hill":
                cv.drawArc(l, top, r, base + ht, 180, 180, true, pt);
                break;
            case "tree":
                cv.drawRect(x - wd * 0.06f, base - ht * 0.35f, x + wd * 0.06f, base, pt);
                cv.drawOval(l, top, r, base - ht * 0.25f, pt);
                break;
            case "bridge": {
                Paint line = new Paint(pt);
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(3);
                cv.drawRect(l, base - ht * 0.18f, r, base - ht * 0.12f, pt);
                cv.drawRect(l + wd * 0.2f - 3, top, l + wd * 0.2f + 3, base, pt);
                cv.drawRect(r - wd * 0.2f - 3, top, r - wd * 0.2f + 3, base, pt);
                p.moveTo(l + wd * 0.2f, top);                // the cable hangs between the two towers
                p.quadTo(x, base - ht * 0.1f, r - wd * 0.2f, top);
                cv.drawPath(p, line);
                break;
            }
            case "block":
                cv.drawRect(l, top, r, base, pt);
                break;
            default:                                     // a kind not in the list is left out
                break;
        }
    }

    // ---- shared ----

    /** Any picture cut to the 3:1 banner from its middle (a little above, where skylines sit) and scaled to it. */
    static Bitmap banner(Bitmap src) {
        if (src.getWidth() == W && src.getHeight() == H) return src;
        float want = (float) W / H;
        int sw = src.getWidth(), sh = src.getHeight();
        Rect from;
        if ((float) sw / sh > want) {
            int cw = Math.round(sh * want);
            from = new Rect((sw - cw) / 2, 0, (sw - cw) / 2 + cw, sh);
        } else {
            int ch = Math.round(sw / want);
            int top = Math.max(0, Math.min(sh - ch, Math.round((sh - ch) * 0.4f)));
            from = new Rect(0, top, sw, top + ch);
        }
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, from, new Rect(0, 0, W, H), new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }
}
