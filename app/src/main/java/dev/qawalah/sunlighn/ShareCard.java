package dev.qawalah.sunlighn;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * The share card (3.35, owner: "share as a whole to social app like X"): a picture of a shot or a trip, drawn on the
 * phone in the Pocket-map look, sent with the phone's share sheet to X, Instagram, WhatsApp and the rest. 1200 x 675
 * is the shape X shows whole in its feed; the story size is 1080 x 1920. No account, no server. A card shows the
 * city, never coordinates, and the line only as a direction.
 */
final class ShareCard {
    private ShareCard() {}

    static final int W = 1200, H = 675, SW = 1080, SH = 1920;
    private static final int PAPER = 0xFFECEBE7, DISC = 0xFFEE5A1E, INK = 0xFF22211F, MUTED = 0xFF5B5A56, GREY = 0xFFD3D2CE;

    /** What one card says about one shot. */
    static final class Data {
        String title, city, moment, detail;
        boolean moon, hasLine;
        Bitmap photo;
        double youLat, youLon, subLat, subLon;
    }

    // ---- one shot ----

    static Bitmap shot(Context c, Data d, boolean story) {
        int w = story ? SW : W, h = story ? SH : H;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        background(cv, w, h);
        float pad = story ? 72 : 56;
        RectF art = story ? new RectF(pad, pad, w - pad, w - pad) : new RectF(w * 0.52f, pad, w - pad, h - pad);
        if (d.photo != null) cropped(cv, d.photo, art);
        else sketch(cv, d, art, true);
        float tx = pad, ty = story ? art.bottom + 64 : pad + 8, tw = story ? w - 2 * pad : art.left - pad - 40;
        float th = story ? h - ty - 170 : h - ty - 130;   // the footer keeps its own place
        text(c, cv, d, tx, ty, tw, th, story);
        brand(c, cv, pad, h - pad);
        return b;
    }

    // ---- a trip: 1 to 4 tiles ----

    static Bitmap trip(Context c, String city, String dates, List<Data> shots, boolean story) {
        int w = story ? SW : W, h = story ? SH : H;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        background(cv, w, h);
        float pad = story ? 72 : 56;
        TextPaint p = paint(c, 700, story ? 84 : 64, INK);
        float headW = story ? w - 2 * pad : w * 0.40f;
        Layout name = layout(city == null || city.isEmpty() ? "Trip" : city, p, (int) headW, 2, story ? 52 : 40);
        canvas(cv, name, pad, pad);
        float y = pad + name.getHeight() + 14;
        TextPaint dp = paint(c, 500, story ? 38 : 30, MUTED);
        Layout dl = layout(dates == null ? "" : dates, dp, (int) headW, 2, 20);
        canvas(cv, dl, pad, y);
        y += dl.getHeight() + 10;
        TextPaint cp = paint(c, 500, story ? 34 : 28, MUTED);
        String count = shots.size() == 1 ? "1 shot" : shots.size() + " shots";
        Layout cl = layout(count, cp, (int) headW, 1, 20);
        canvas(cv, cl, pad, y);
        int n = Math.min(4, shots.size());
        RectF area = story ? new RectF(pad, y + cl.getHeight() + 48, w - pad, h - 190) : new RectF(w * 0.46f, pad, w - pad, h - pad);
        float gap = 18;
        int cols = n <= 1 ? 1 : 2, rows = n <= 2 ? (n == 2 && !story ? 1 : n) : 2;
        if (n == 2 && story) { cols = 1; rows = 2; }
        float tw = (area.width() - gap * (cols - 1)) / cols, th = (area.height() - gap * (rows - 1)) / rows;
        for (int i = 0; i < n; i++) {
            int col = i % cols, row = i / cols;
            RectF r = new RectF(area.left + col * (tw + gap), area.top + row * (th + gap),
                    area.left + col * (tw + gap) + tw, area.top + row * (th + gap) + th);
            Data s = shots.get(i);
            if (s.photo != null) cropped(cv, s.photo, r);
            else sketch(cv, s, r, false);
        }
        if (shots.size() > 4) {
            TextPaint mp = paint(c, 600, 28, MUTED);
            cv.drawText("+" + (shots.size() - 4) + " more", area.right - mp.measureText("+" + (shots.size() - 4) + " more"),
                    area.bottom + 34, mp);
        }
        brand(c, cv, pad, h - pad);
        return b;
    }

    // ---- sending ----

    /** Writes the card as card.png and opens the phone's share sheet with it and these words. */
    static boolean send(Activity a, Bitmap card, String words) {
        File dir = new File(a.getCacheDir(), ShotFileProvider.DIR);
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
            f.delete();
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        File f = new File(dir, "sunwhere-card.png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            card.compress(Bitmap.CompressFormat.PNG, 100, o);
        } catch (IOException e) {
            return false;
        }
        Uri u = ShotFileProvider.uriFor(a, f.getName());
        Intent send = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u)
                .putExtra(Intent.EXTRA_TEXT, words).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(f.getName(), u));
        try {
            a.startActivity(Intent.createChooser(send, "Share this card"));
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            return false;
        }
    }

    // ---- drawing ----

    private static void background(Canvas cv, int w, int h) {
        cv.drawColor(PAPER);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(DISC);
        cv.drawCircle(w, 0, Math.min(w, h) * 0.30f, p);
    }

    private static TextPaint paint(Context c, int weight, float size, int colour) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Ui.font(c, weight));
        p.setTextSize(size);
        p.setColor(colour);
        return p;
    }

    /** The text, shrunk in 4 px steps until it fits {@code maxLines} lines (down to {@code minSize}). */
    private static Layout layout(String s, TextPaint p, int width, int maxLines, float minSize) {
        return layout(s, p, width, maxLines, minSize, Float.MAX_VALUE);
    }

    /** As above, and no taller than {@code maxHeight}. */
    private static Layout layout(String s, TextPaint p, int width, int maxLines, float minSize, float maxHeight) {
        float size = p.getTextSize();
        while (true) {
            Layout l = StaticLayout.Builder.obtain(s, 0, s.length(), p, Math.max(10, width)).setMaxLines(maxLines)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END).setIncludePad(false).setLineSpacing(0, 0.9f).build();
            boolean fits = l.getLineCount() <= maxLines && l.getLineEnd(l.getLineCount() - 1) >= s.length()
                    && l.getHeight() <= maxHeight;
            if (fits || size <= minSize) return l;
            size -= 4;
            p.setTextSize(size);
        }
    }

    private static void canvas(Canvas cv, Layout l, float x, float y) {
        cv.save();
        cv.translate(x, y);
        l.draw(cv);
        cv.restore();
    }

    /** The words of a card: the city, the title (it takes what space is left), the moment, the detail. */
    private static void text(Context c, Canvas cv, Data d, float x, float y, float w, float h, boolean story) {
        Layout city = d.city == null || d.city.isEmpty() ? null
                : layout(d.city, paint(c, 600, story ? 40 : 32, MUTED), (int) w, 1, 18);
        TextPaint mp = paint(c, 600, story ? 48 : 38, d.moon ? 0xFF5A4FCF : 0xFFC4520F);
        Layout moment = d.moment == null || d.moment.isEmpty() ? null : layout(d.moment, mp, (int) w, 2, 24);
        Layout detail = d.detail == null || d.detail.isEmpty() ? null
                : layout(d.detail, paint(c, 500, story ? 38 : 30, MUTED), (int) w, 2, 20);
        float fixed = (city == null ? 0 : city.getHeight() + 14) + (moment == null ? 0 : moment.getHeight() + 14)
                + (detail == null ? 0 : detail.getHeight() + 8) + 22;
        Layout title = layout(d.title == null ? "" : d.title, paint(c, 700, story ? 96 : 76, INK), (int) w, story ? 5 : 4,
                story ? 36 : 30, Math.max(60, h - fixed));
        float cur = y;
        if (city != null) { canvas(cv, city, x, cur); cur += city.getHeight() + 14; }
        canvas(cv, title, x, cur);
        cur += title.getHeight() + 22;
        if (moment != null) { canvas(cv, moment, x, cur); cur += moment.getHeight() + 10; }
        if (detail != null) canvas(cv, detail, x, cur);
    }

    private static void brand(Context c, Canvas cv, float x, float baseline) {
        TextPaint p = paint(c, 700, 34, INK);
        cv.drawText("SunWhere", x, baseline, p);
        Paint line = new Paint();
        line.setColor(INK);
        cv.drawRect(x, baseline - 44, x + 150, baseline - 41, line);
    }

    /** The picture fills the rounded box, cut from its middle. */
    private static void cropped(Canvas cv, Bitmap src, RectF box) {
        float want = box.width() / box.height();
        int sw = src.getWidth(), sh = src.getHeight();
        Rect from;
        if ((float) sw / sh > want) {
            int cw = Math.round(sh * want);
            from = new Rect((sw - cw) / 2, 0, (sw - cw) / 2 + cw, sh);
        } else {
            int ch = Math.round(sw / want);
            from = new Rect(0, (sh - ch) / 2, sw, (sh - ch) / 2 + ch);
        }
        Path clip = new Path();
        clip.addRoundRect(box, 28, 28, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        cv.drawBitmap(src, from, box, new Paint(Paint.FILTER_BITMAP_FLAG));
        cv.restore();
    }

    /**
     * No photo: the shot drawn as a sketch: the subject, the line running to the sun (or moon) where it stands, and
     * only the direction of the line (never where "You" are).
     */
    private static void sketch(Canvas cv, Data d, RectF box, boolean large) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(GREY);
        cv.drawRoundRect(box, 28, 28, p);
        float cx = box.centerX(), base = box.bottom - box.height() * 0.18f;
        double bearing = d.hasLine ? Astro.bearing(d.youLat, d.youLon, d.subLat, d.subLon) : 0;
        if (!d.hasLine) bearing = 0;
        double dir = Math.toRadians(bearing);
        float len = Math.min(box.width(), box.height()) * 0.36f;
        float sx = cx, sy = base - len * 0.2f;                        // the subject
        float ex = (float) (sx + Math.sin(dir) * len), ey = (float) (sy - Math.cos(dir) * len);
        ex = Math.max(box.left + 30, Math.min(box.right - 30, ex));
        ey = Math.max(box.top + 30, Math.min(box.bottom - 30, ey));
        p.setColor(MUTED);
        p.setStrokeWidth(large ? 6 : 4);
        cv.drawLine(sx, sy, ex, ey, p);
        p.setColor(d.moon ? 0xFF5A4FCF : DISC);
        cv.drawCircle(ex, ey, Math.max(14, box.width() * 0.06f), p);
        p.setColor(INK);
        float tw = Math.max(12, box.width() * 0.035f), th = Math.max(30, box.height() * 0.22f);
        cv.drawRect(sx - tw, sy - th, sx + tw, sy, p);                 // the subject, a plain tower
        Paint ground = new Paint();
        ground.setColor(INK);
        cv.drawRect(box.left + 24, sy, box.right - 24, sy + 3, ground);
    }
}
