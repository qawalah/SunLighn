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
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

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
 * City covers (3.30, owner: "connect to an api to generate city covers"; "Claude for now, … do all, including gpt"). A
 * banner over each city in Saved, made once per city and kept on the phone:
 * <ul>
 * <li><b>Photo</b>: the city's own picture from its Wikipedia article (free, no key);</li>
 * <li><b>Claude</b>: Claude designs a flat poster of the city (its sky, skyline and landmarks as shapes, through the
 * Anthropic SDK with structured output), which the app draws ({@link #draw});</li>
 * <li><b>GPT</b>: OpenAI's image model paints one.</li>
 * </ul>
 * Claude and GPT use the owner's own key, kept in this phone's settings, and cost a little of that account's credit per
 * new city. Nothing is asked without a network, and a city is asked once per run of the app.
 */
final class Covers {
    private Covers() {}

    static final int OFF = 0, PHOTO = 1, CLAUDE = 2, GPT = 3;
    static final String[] NAMES = {"Off", "Photo (free)", "Claude", "GPT"};
    static final int W = 1080, H = 360;       // a 3:1 banner
    static final String CLAUDE_MODEL = "claude-opus-5-5";
    static final String GPT_MODEL = "gpt-image-1";

    interface Done { void cover(Bitmap b); }

    private static final Map<String, Bitmap> memory = new HashMap<>();
    private static final Set<String> asked = new HashSet<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    /** What went wrong with the last cover asked for, said under the setting; null when it worked. */
    static volatile String lastError;

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE); }

    static int source(Context c) { return Math.max(0, Math.min(GPT, prefs(c).getInt("cover_source", OFF))); }

    static void setSource(Context c, int s) {
        prefs(c).edit().putInt("cover_source", s).apply();
        asked.clear();
        lastError = null;
    }

    static String key(Context c, int s) {
        return s == CLAUDE ? prefs(c).getString("claude_key", "") : s == GPT ? prefs(c).getString("openai_key", "") : "";
    }

    static void setKey(Context c, int s, String k) {
        prefs(c).edit().putString(s == CLAUDE ? "claude_key" : "openai_key", k.trim()).apply();
        asked.clear();
        lastError = null;
    }

    static boolean needsKey(int s) { return s == CLAUDE || s == GPT; }

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
                if (s == PHOTO) b = photo(city);
                else if (s == CLAUDE) b = drawn(claude(apiKey, city, lat, lon));
                else b = gpt(apiKey, city);
                if (b == null) lastError = "No cover came back for " + city + ".";
            } catch (Exception e) {
                lastError = reason(e);
                CrashLog.caught(app, "cover " + NAMES[s], e);
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

    /** Words for a failure the owner can act on (a wrong key, no credit, no network). */
    static String reason(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (e instanceof com.anthropic.errors.UnauthorizedException || m.contains("401"))
            return "The key was not accepted. Check it in Settings, City covers.";
        if (e instanceof com.anthropic.errors.RateLimitException || m.contains("429"))
            return "The service is busy or the account is out of credit. It is tried again next time Saved opens.";
        if (e instanceof IOException || e instanceof com.anthropic.errors.AnthropicIoException)
            return "The cover could not be fetched (no network?).";
        return "The cover could not be made: " + (m.length() > 120 ? m.substring(0, 120) + "…" : m);
    }

    // ---- Photo: the city's Wikipedia picture ----

    private static Bitmap photo(String city) throws IOException {
        String title = URLEncoder.encode(city.replace(' ', '_'), "UTF-8").replace("+", "%20");
        String json = get("https://en.wikipedia.org/api/rest_v1/page/summary/" + title, null);
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            org.json.JSONObject img = o.optJSONObject("originalimage");
            if (img == null) img = o.optJSONObject("thumbnail");
            if (img == null) return null;
            String src = img.optString("source", "");
            if (src.isEmpty() || src.endsWith(".svg")) return null;    // a flag or a seal, not a picture of the place
            return image(src);
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    // ---- Claude: a poster designed as shapes ----

    /** One shape of the skyline, in fractions of the skyline's own box. */
    public static final class Shape {
        @JsonPropertyDescription("One of: block, tower, spire, dome, minaret, arch (a gateway open at its foot), opening (a slim tower with an "
                + "opening near its top, like Kingdom Centre), mast, wheel, hill, tree, bridge")
        public String kind;
        @JsonPropertyDescription("Horizontal centre within the skyline, 0 (left end) to 1 (right end)")
        public double x;
        @JsonPropertyDescription("Width as a fraction of the skyline's width, 0.01 to 0.4")
        public double width;
        @JsonPropertyDescription("Height as a fraction of the skyline's height, 0.05 to 1 (only the signature landmark near 1)")
        public double height;
        @JsonPropertyDescription("Distance: 0 near (darkest, drawn last) to 2 far (palest, drawn first)")
        public int layer;
    }

    /** The skyline Claude designs for a city; the app draws it as a pocket map's cover (owner's picture, 3.30). */
    public static final class Design {
        @JsonPropertyDescription("The skyline and landmarks, 10 to 30 shapes, recognisable for this city, its signature "
                + "landmark the tallest and near the middle")
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
            StructuredMessageCreateParams<Design> params = MessageCreateParams.builder()
                    .model(CLAUDE_MODEL)
                    .maxTokens(16000L)
                    // A refused request is answered by another model instead of stopping (server-side fallback, beta)
                    .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .addUserMessage(prompt)
                    .outputConfig(Design.class)
                    .build();
            StructuredMessage<Design> m = client.messages().create(params);
            if (m.stopReason().map(com.anthropic.models.messages.StopReason.REFUSAL::equals).orElse(false)) {
                lastError = "Claude declined to draw this city.";
                return null;
            }
            Design[] out = {null};
            m.content().forEach(cb -> cb.text().ifPresent(t -> out[0] = t.text()));
            return out[0];
        } finally {
            client.close();
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

    // ---- GPT: OpenAI's image model ----

    private static Bitmap gpt(String apiKey, String city) throws IOException {
        String prompt = "A calm, minimal flat poster of " + city + " at golden hour, wide panorama, its real recognisable "
                + "skyline and landmarks as silhouettes against a clear evening sky, no text, no people, no logos.";
        String body;
        try {
            body = new org.json.JSONObject().put("model", GPT_MODEL).put("prompt", prompt).put("size", "1536x1024")
                    .put("quality", "low").put("n", 1).toString();
        } catch (org.json.JSONException e) {
            return null;
        }
        HttpURLConnection h = (HttpURLConnection) new URL("https://api.openai.com/v1/images/generations").openConnection();
        h.setRequestMethod("POST");
        h.setDoOutput(true);
        h.setConnectTimeout(15000);
        h.setReadTimeout(180000);
        h.setRequestProperty("Authorization", "Bearer " + apiKey);
        h.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = h.getOutputStream()) {
            o.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = h.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " from OpenAI");
        String json = Places.read(h.getInputStream());
        h.disconnect();
        try {
            org.json.JSONObject first = new org.json.JSONObject(json).getJSONArray("data").getJSONObject(0);
            String b64 = first.optString("b64_json", "");
            if (!b64.isEmpty()) {
                byte[] png = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(png, 0, png.length);
            }
            String url = first.optString("url", "");
            return url.isEmpty() ? null : image(url);
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    // ---- shared ----

    private static String get(String url, String auth) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestProperty("User-Agent", Places.USER_AGENT);
        if (auth != null) h.setRequestProperty("Authorization", auth);
        h.setConnectTimeout(10000);
        h.setReadTimeout(20000);
        if (h.getResponseCode() != 200) throw new IOException("HTTP " + h.getResponseCode());
        try (InputStream in = h.getInputStream()) {
            return Places.read(in);
        } finally {
            h.disconnect();
        }
    }

    private static Bitmap image(String url) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestProperty("User-Agent", Places.USER_AGENT);
        h.setConnectTimeout(10000);
        h.setReadTimeout(30000);
        if (h.getResponseCode() != 200) throw new IOException("HTTP " + h.getResponseCode());
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) {
                b.write(buf, 0, n);
                if (b.size() > 20 * 1024 * 1024) return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            byte[] bytes = b.toByteArray();
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= W) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } finally {
            h.disconnect();
        }
    }

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
