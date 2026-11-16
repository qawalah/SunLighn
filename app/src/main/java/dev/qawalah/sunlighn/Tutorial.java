package dev.qawalah.sunlighn;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * First-start tutorial: three swipeable cards with a small animated picture each. The first shows the change from
 * the sun to the moon (the switch, the icon and the colours, opening in a circle as the app does); the others are
 * the two tools, each with its Home icon (three until 3.6). Skip is always there. Shown once, can be replayed from Settings.
 */
final class Tutorial extends FrameLayout {
    interface Listener { void onClosed(); }

    private static final int PAGES = 3;
    private static final String[] TITLES = {"Sun or moon", "Line up a street", "Build the shot"};
    private static final Ui.Icon[] ICONS = {null, Ui.Icon.TOOL_LINE, Ui.Icon.TOOL_BUILD};
    private static final int[] ARTS = {Art.SWITCH, 0, 2};
    private static final String[] BODIES = {
            null,   // depends on the accent setting, see bodyOf
            "Stand at one end, tap the other. SunLighn says when the sun or the moon sits exactly on that line.",
            "Pick your subject, then place the sun or the moon in the picture, or say where it sits: on the horizon, "
                    + "behind the middle, just on top. SunLighn finds where to stand and when, and shades where it can never be."};

    private final Ui.Palette p;
    private final Listener listener;
    private final Pager pager;
    private final Art[] arts = new Art[PAGES];
    private final View[] dots = new View[PAGES];
    private final TextView next, skip;
    private int page = -1;

    Tutorial(Context c, Ui.Palette p, Listener listener) {
        super(c);
        this.p = p;
        this.listener = listener;
        setBackgroundColor(p.bg);
        setClickable(true); // swallow taps so the map underneath is not touched

        pager = new Pager(c);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setOverScrollMode(OVER_SCROLL_NEVER);
        PageRow row = new PageRow(c);
        pager.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        for (int i = 0; i < PAGES; i++) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = Ui.dp(c, 28);
            card.setPadding(pad, Ui.dp(c, 96), pad, Ui.dp(c, 160));
            arts[i] = new Art(c, ARTS[i]);
            final Art art = arts[i];
            art.setOnClickListener(v -> art.play());   // a tap plays the picture again
            card.addView(arts[i], new LinearLayout.LayoutParams(-1, 0, 1f));
            if (ICONS[i] != null) {   // the tool's icon, as on its Home card
                FrameLayout disc = new FrameLayout(c);
                disc.setBackground(Ui.shape(c, Ui.withAlpha(p.accent, 28), 0, 99, false));
                android.widget.ImageView glyph = new android.widget.ImageView(c);
                glyph.setImageDrawable(Ui.icon(c, ICONS[i], p.accent));
                disc.addView(glyph, new FrameLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28), Gravity.CENTER));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48));
                dl.topMargin = Ui.dp(c, 16);
                card.addView(disc, dl);
            }
            TextView title = Ui.text(c, TITLES[i], 28, true, p.text);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = Ui.dp(c, 12);
            card.addView(title, tl);
            TextView body = Ui.text(c, i == 0 ? bodyOf(c) : BODIES[i], 15, false, p.muted);
            body.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(c, 10);
            card.addView(body, bl);
            row.addView(card, new LinearLayout.LayoutParams(0, -1)); // as wide as the pager: see PageRow
        }
        addView(pager, new LayoutParams(-1, -1));

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), Ui.dp(c, 24));
        LinearLayout dotRow = new LinearLayout(c);
        for (int i = 0; i < PAGES; i++) {
            View d = new View(c);
            dots[i] = d;
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8));
            dl.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            dotRow.addView(d, dl);
        }
        bottom.addView(dotRow, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout buttons = new LinearLayout(c);
        skip = Ui.button(c, p, "Skip", false);
        next = Ui.button(c, p, "Next", true);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 1f);
        sl.rightMargin = Ui.dp(c, 8);
        buttons.addView(skip, sl);
        buttons.addView(next, new LinearLayout.LayoutParams(0, -2, 2f));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = Ui.dp(c, 16);
        bottom.addView(buttons, bl);
        LayoutParams bp = new LayoutParams(-1, -2);
        bp.gravity = Gravity.BOTTOM;
        addView(bottom, bp);

        skip.setOnClickListener(v -> listener.onClosed());
        next.setOnClickListener(v -> {
            if (page >= PAGES - 1) listener.onClosed();
            else pager.goTo(page + 1);
        });
        pager.onPage = this::showPage;
        showPage(0);
    }

    /** The first card's words: the colours follow the body unless an accent is fixed in Settings. */
    private static String bodyOf(Context c) {
        // What the app is for comes first: the first-time tester read the whole card and still did not know (audit 3.2.2)
        String what = "SunLighn tells you where to stand and when the sun or the moon sits exactly behind what you "
                + "photograph. ";
        return Ui.accentIndex(c) == Ui.ACCENT_FOLLOW
                ? what + "Choose the sun or the moon at the top of Home; the app takes its colour: amber for the sun, "
                + "violet for the moon."
                : what + "Choose the sun or the moon at the top of Home.";
    }

    /** Keep the buttons clear of the system bars. */
    void setInsets(int top, int bottomInset) {
        setPadding(0, top, 0, bottomInset);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        pager.post(() -> pager.scrollTo(Math.max(page, 0) * pager.getWidth(), 0));   // stay on the card after a turn
    }

    /**
     * The row of cards. Each card is as wide as the pager, set while measuring: sizing them from onSizeChanged
     * changed the layout inside a layout pass and the phone showed empty pages (3.1.0).
     */
    private static final class PageRow extends LinearLayout {
        PageRow(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
        }

        int pageWidth;   // given by the pager before it measures this row

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int page = pageWidth > 0 ? pageWidth : getResources().getDisplayMetrics().widthPixels;
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).getLayoutParams().width = page;
            super.onMeasure(MeasureSpec.makeMeasureSpec(page * getChildCount(), MeasureSpec.EXACTLY), hSpec);
        }
    }

    private void showPage(int i) {
        if (i == page) return;
        page = i;
        for (int k = 0; k < PAGES; k++) {
            dots[k].setBackground(Ui.shape(getContext(), k == i ? p.accent : p.line, 0, 99, false));
        }
        next.setText(i >= PAGES - 1 ? "Got it" : "Next");
        skip.setVisibility(i >= PAGES - 1 ? INVISIBLE : VISIBLE);
        arts[i].play(); // the picture restarts each time its card becomes current
    }

    /** Horizontal pager: snaps to a card, a fling moves one card. */
    private static final class Pager extends HorizontalScrollView {
        Runnable1 onPage;
        private int fling = 0;
        private final GestureDetector detector;

        interface Runnable1 { void run(int page); }

        Pager(Context c) {
            super(c);
            detector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener());
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            if (getChildCount() > 0 && getChildAt(0) instanceof PageRow) {
                ((PageRow) getChildAt(0)).pageWidth = MeasureSpec.getSize(wSpec);
            }
            super.onMeasure(wSpec, hSpec);
        }

        int pageNow() {
            int w = Math.max(getWidth(), 1);
            return Math.max(0, Math.min(PAGES - 1, Math.round(getScrollX() / (float) w)));
        }

        void goTo(int i) {
            i = Math.max(0, Math.min(PAGES - 1, i));
            smoothScrollTo(i * getWidth(), 0);
            if (onPage != null) onPage.run(i);
        }

        @Override public void fling(int velocityX) {
            fling = velocityX > 0 ? 1 : -1; // handled when the finger lifts
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            detector.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_DOWN) fling = 0;
            boolean r = super.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                int target = fling != 0 ? Math.round(getScrollX() / (float) Math.max(getWidth(), 1) + fling * 0.5f)
                        : pageNow();
                goTo(target);
            }
            return r;
        }
    }

    /** The small animated picture on each card. t runs 0..1 over about a second. */
    private final class Art extends View {
        static final int SWITCH = 3;   // the sun-or-moon card; 0..2 are the tools
        private final int kind;
        private float t = 1f;
        private ValueAnimator anim;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Art(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        void play() {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f);
            if (kind == SWITCH) {
                anim.setInterpolator(null);    // linear: the parts below ease themselves
                anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
                anim.setDuration(2600);
                anim.start();
                return;
            }
            anim.setDuration(kind == SWITCH ? 2600 : 1000);
            anim.setInterpolator(new PathInterpolator(0.16f, 1f, 0.3f, 1f));
            anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        private float lerp(float a, float b, float f) { return a + (b - a) * f; }
        private float part(float from, float to) { return Math.max(0f, Math.min(1f, (t - from) / (to - from))); }

        @Override protected void onDraw(Canvas cv) {
            if (kind == SWITCH) {
                drawSwitch(cv);
                return;
            }
            float w = getWidth(), h = getHeight();
            float k = Ui.dp(getContext(), 1);
            float cx = w / 2f, cy = h / 2f;
            float s = Math.min(w, h * 1.1f);
            // map-like grid card
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.panel);
            float half = Math.min(w / 2f, s / 2f);
            cv.drawRoundRect(cx - half, cy - half * 0.8f, cx + half, cy + half * 0.8f, 16 * k, 16 * k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * k);
            paint.setColor(p.line);
            cv.drawRoundRect(cx - half, cy - half * 0.8f, cx + half, cy + half * 0.8f, 16 * k, 16 * k, paint);
            for (int i = 1; i < 4; i++) {
                float gx = cx - half + 2 * half * i / 4f;
                cv.drawLine(gx, cy - half * 0.8f + 8 * k, gx, cy + half * 0.8f - 8 * k, paint);
                float gy = cy - half * 0.8f + 1.6f * half * i / 4f;
                cv.drawLine(cx - half + 8 * k, gy, cx + half - 8 * k, gy, paint);
            }
            float ax = cx - half * 0.55f, ay = cy + half * 0.45f;   // you
            float bx = cx + half * 0.35f, by = cy - half * 0.25f;   // subject

            paint.setStrokeCap(Paint.Cap.ROUND);
            // you: the pin drops onto the point and a halo spreads, as in the CrossPaths tutorial
            float u = 2.2f * k;
            if (kind == 0) {
                // the subject first: a blue point appears where you tap, with its halo
                float pop = part(0.2f, 0.7f);
                Ui.drawPinHalo(cv, bx, by, u, Ui.ROUTE_BLUE, part(0f, 0.6f) * 1.4f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                cv.drawCircle(bx, by, 12 * k * pop, paint);
                paint.setColor(Ui.ROUTE_BLUE);
                cv.drawCircle(bx, by, 9 * k * pop, paint);
                return;
            }
            // where to stand: the pin drops onto its spot and the line runs to the subject
            float drop = kind == 1 ? part(0.1f, 0.7f) : 1f;
            float back = (drop - 1f);
            float eased = kind == 1 ? 1f + 2.7f * back * back * back + 1.7f * back * back : 1f; // overshoots, settles
            Ui.drawPinHalo(cv, ax, ay, u, Ui.ROUTE_GREEN, kind == 1 ? part(0f, 0.5f) : 1f);
            // line drawing from you to subject
            float draw = kind == 1 ? part(0.45f, 1f) : 1f;
            float ex = lerp(ax, bx, draw), ey = lerp(ay, by, draw);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.5f * k);
            paint.setShader(new LinearGradient(ax, ay, bx, by, Ui.ROUTE_GREEN, Ui.ROUTE_BLUE, Shader.TileMode.CLAMP));
            cv.drawLine(ax, ay, ex, ey, paint);
            paint.setShader(null);
            float popB = 1f;
            paint.setColor(Ui.ROUTE_BLUE);
            cv.drawCircle(bx, by, 9 * k * popB, paint);
            if (kind == 2) {
                // the sun slides along the line's direction until it sits right behind the subject
                float dx = bx - ax, dy = by - ay, len = (float) Math.hypot(dx, dy);
                float ux = dx / len, uy = dy / len;
                // end on the line, just beyond the subject; start off to the side
                float sx = bx + ux * len * 0.55f;
                float sy = by + uy * len * 0.55f;
                float fromX = sx + half * 0.55f, fromY = sy + half * 0.1f;
                float f = part(0.1f, 0.9f);
                float px = lerp(fromX, sx, f), py = lerp(fromY, sy, f);
                paint.setColor(Ui.withAlpha(p.amber, 70));
                cv.drawCircle(px, py, 22 * k, paint);
                paint.setColor(p.amber);
                cv.drawCircle(px, py, 14 * k, paint);
            }
            Ui.drawPin(cv, ax, ay, u, (eased - 1f) * 26f * u, Math.min(1f, drop * 3f));
        }
    
        private float ease(float f) {   // CrossPaths arrival: fast start, long soft landing
            float u = 1f - f;
            return 1f - u * u * u;
        }

        /**
         * The sun-or-moon card: the Home switch, the body's icon and a main key. A finger presses Moon and the
         * moon's colours open in a circle from it, the way the app changes.
         */
        private void drawSwitch(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float k = Ui.dp(getContext(), 1);
            float cx = w / 2f, cy = h / 2f;
            float half = Math.min(w / 2f, Math.min(w, h * 1.1f) / 2f);
            android.graphics.RectF box = new android.graphics.RectF(cx - half, cy - half * 0.8f, cx + half, cy + half * 0.8f);
            float trackY = box.top + 18 * k, trackH = 40 * k;
            android.graphics.RectF track = new android.graphics.RectF(box.left + 18 * k, trackY, box.right - 18 * k, trackY + trackH);
            float tapX = track.centerX() + track.width() / 4f, tapY = track.centerY();
            float open = ease(part(0.42f, 0.86f));
            float far = (float) Math.hypot(Math.max(tapX - box.left, box.right - tapX), box.bottom - tapY);

            drawState(cv, box, track, false, k);
            if (open > 0f) {
                cv.save();
                android.graphics.Path hole = new android.graphics.Path();
                hole.addCircle(tapX, tapY, far * open, android.graphics.Path.Direction.CW);
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(box, 16 * k, 16 * k, android.graphics.Path.Direction.CW);
                hole.op(clip, android.graphics.Path.Op.INTERSECT);
                cv.clipPath(hole);
                drawState(cv, box, track, true, k);
                cv.restore();
            }
            // the finger: a soft dot that presses and lets go
            float press = part(0.26f, 0.42f), lift = part(0.48f, 0.62f);
            if (press > 0f && lift < 1f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Ui.withAlpha(p.text, (int) (60 * (1f - lift))));
                cv.drawCircle(tapX, tapY, (14 + 8 * press) * k, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * k);
                paint.setColor(Ui.withAlpha(p.text, (int) (120 * (1f - lift))));
                cv.drawCircle(tapX, tapY, (14 + 16 * lift) * k, paint);
            }
        }

        /** One state of the card, the sun's or the moon's, in that body's accent. */
        private void drawState(Canvas cv, android.graphics.RectF box, android.graphics.RectF track, boolean moon, float k) {
            int accent = Ui.accentColor(Ui.resolveAccent(Ui.accentIndex(getContext()), moon), p.night);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.panel);
            cv.drawRoundRect(box, 16 * k, 16 * k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * k);
            paint.setColor(p.line);
            cv.drawRoundRect(box, 16 * k, 16 * k, paint);
            // the switch: a raised track, the chosen half on a lighter thumb, an icon and a word in each
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.raised);
            cv.drawRoundRect(track, 12 * k, 12 * k, paint);
            float mid = track.centerX(), in = 3 * k;
            android.graphics.RectF thumb = moon
                    ? new android.graphics.RectF(mid, track.top + in, track.right - in, track.bottom - in)
                    : new android.graphics.RectF(track.left + in, track.top + in, mid, track.bottom - in);
            paint.setColor(p.panel);
            cv.drawRoundRect(thumb, 9 * k, 9 * k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(p.line);
            cv.drawRoundRect(thumb, 9 * k, 9 * k, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(13.5f * getResources().getDisplayMetrics().scaledDensity);
            for (int i = 0; i < 2; i++) {
                boolean on = (i == 1) == moon;
                String word = i == 0 ? "Sun" : "Moon";
                paint.setTypeface(Ui.font(getContext(), on ? 700 : 500));
                float tw = paint.measureText(word), icon = 18 * k, gap = 7 * k;
                float x0 = (i == 0 ? (track.left + mid) / 2f : (mid + track.right) / 2f) - (icon + gap + tw) / 2f;
                android.graphics.drawable.Drawable d = Ui.icon(getContext(), i == 0 ? Ui.Icon.SUN : Ui.Icon.MOON,
                        on ? accent : p.muted);
                d.setBounds((int) x0, (int) (track.centerY() - icon / 2f), (int) (x0 + icon), (int) (track.centerY() + icon / 2f));
                d.draw(cv);
                paint.setColor(on ? p.text : p.muted);
                cv.drawText(word, x0 + icon + gap, track.centerY() + paint.getTextSize() * 0.35f, paint);
            }
            // the body, large, on a soft disc of its colour
            float gy = (track.bottom + box.bottom - 58 * k) / 2f, r = Math.min(46 * k, (box.bottom - 58 * k - track.bottom) / 2.4f);
            paint.setColor(Ui.withAlpha(accent, 34));
            cv.drawCircle(box.centerX(), gy, r * 1.25f, paint);
            android.graphics.drawable.Drawable big = Ui.icon(getContext(), moon ? Ui.Icon.MOON : Ui.Icon.SUN, accent);
            big.setBounds((int) (box.centerX() - r * 0.8f), (int) (gy - r * 0.8f), (int) (box.centerX() + r * 0.8f), (int) (gy + r * 0.8f));
            big.draw(cv);
            // the main key, in the accent with its ink
            android.graphics.RectF key = new android.graphics.RectF(box.left + 18 * k, box.bottom - 58 * k, box.right - 18 * k, box.bottom - 18 * k);
            paint.setColor(accent);
            cv.drawRoundRect(key, 12 * k, 12 * k, paint);
            int ink = p.night ? (moon ? 0xFF1E1147 : 0xFF2A1B02) : 0xFFFFFFFF;
            if (Ui.accentIndex(getContext()) != Ui.ACCENT_FOLLOW) ink = p.ink;
            paint.setColor(ink);
            paint.setTypeface(Ui.font(getContext(), 700));
            String label = "Next";
            cv.drawText(label, key.centerX() - paint.measureText(label) / 2f, key.centerY() + paint.getTextSize() * 0.35f, paint);
        }
    }
}
