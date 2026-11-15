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
 * First-start tutorial: three swipeable cards with a small animated picture each.
 * Skip is always there. Shown once, can be replayed from the help button.
 */
final class Tutorial extends FrameLayout {
    interface Listener { void onClosed(); }

    private static final String[] TITLES = {"Pick what you photograph", "See where to stand", "Look through the viewfinder"};
    private static final String[] BODIES = {
            "SunLighn gets the sun or the moon right behind your subject. Search for it or tap it on the map: a building, a peak, a tree.",
            "SunLighn shows where to stand and when. Or tap your own spot and it tells you the time.",
            "See the shot as your camera will: pinch for the lens, slide for the time. The bell plans the shoot and reminds you."};

    private final Ui.Palette p;
    private final Listener listener;
    private final Pager pager;
    private final Art[] arts = new Art[3];
    private final View[] dots = new View[3];
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
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        pager.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        for (int i = 0; i < 3; i++) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = Ui.dp(c, 28);
            card.setPadding(pad, Ui.dp(c, 96), pad, Ui.dp(c, 160));
            arts[i] = new Art(c, i);
            card.addView(arts[i], new LinearLayout.LayoutParams(-1, 0, 1f));
            TextView title = Ui.text(c, TITLES[i], 28, true, p.text);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = Ui.dp(c, 12);
            card.addView(title, tl);
            TextView body = Ui.text(c, BODIES[i], 15, false, p.muted);
            body.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(c, 10);
            card.addView(body, bl);
            row.addView(card, new LinearLayout.LayoutParams(0, -1)); // width set in onSizeChanged
        }
        addView(pager, new LayoutParams(-1, -1));

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), Ui.dp(c, 24));
        LinearLayout dotRow = new LinearLayout(c);
        for (int i = 0; i < 3; i++) {
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
            if (page >= 2) listener.onClosed();
            else pager.goTo(page + 1);
        });
        pager.onPage = this::showPage;
        showPage(0);
    }

    /** Keep the buttons clear of the system bars. */
    void setInsets(int top, int bottomInset) {
        setPadding(0, top, 0, bottomInset);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        LinearLayout row = (LinearLayout) pager.getChildAt(0);
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).getLayoutParams().width = w;
        }
        row.requestLayout();
        pager.post(() -> pager.scrollTo(Math.max(page, 0) * w, 0));
    }

    private void showPage(int i) {
        if (i == page) return;
        page = i;
        for (int k = 0; k < 3; k++) {
            dots[k].setBackground(Ui.shape(getContext(), k == i ? p.accent : p.line, 0, 99, false));
        }
        next.setText(i >= 2 ? "Got it" : "Next");
        skip.setVisibility(i >= 2 ? INVISIBLE : VISIBLE);
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

        int pageNow() {
            int w = Math.max(getWidth(), 1);
            return Math.max(0, Math.min(2, Math.round(getScrollX() / (float) w)));
        }

        void goTo(int i) {
            i = Math.max(0, Math.min(2, i));
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
            anim.setDuration(1000);
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
    }
}
