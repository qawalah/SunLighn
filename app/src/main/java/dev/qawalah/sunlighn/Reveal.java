package dev.qawalah.sunlighn;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Region;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A change of colours (Sun / Moon, the theme, the accent) explained with motion: a picture of the screen as it
 * was lies over the new one, and a circle grows from the finger until the new screen fills it. It arrives with
 * the CrossPaths ease-out. With animations off (reduced motion) there is no picture: the change is immediate.
 * The picture takes no touches, so the new screen can be used while it opens.
 */
final class Reveal {
    private Reveal() {}

    static final long DURATION = 560;
    private static Bitmap pending;
    private static float pendingX, pendingY;
    private static final List<ValueAnimator> RUNNING = new ArrayList<>();
    static int covers;   // how many reveals have started (read by the tests)

    /** Runs {@code change} now and reveals the new screen from (x, y), in {@code root}'s coordinates. */
    static void play(FrameLayout root, float x, float y, Runnable change) {
        Bitmap before = snapshot(root);
        change.run();
        if (before != null) cover(root, before, x, y, 0);
    }

    /** Before the screen restarts (a theme change): keep a picture to reveal from once it is built again. */
    static void keep(View root, float x, float y) {
        pending = snapshot(root);
        pendingX = x;
        pendingY = y;
    }

    /** After the screen is built again: lay the kept picture on top and open it. */
    static void resume(FrameLayout root) {
        if (pending == null) return;
        Bitmap b = pending;
        pending = null;
        cover(root, b, pendingX, pendingY, 120);   // a moment for the new screen's first frame
    }

    /** True when the phone allows animations (Settings, Accessibility, Remove animations is off). */
    static boolean motion(Context c) {
        if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    /** Ends every reveal at once (the tests take pictures of the finished screen). */
    static void finishAll() {
        for (ValueAnimator a : new ArrayList<>(RUNNING)) a.end();
    }

    private static Bitmap snapshot(View root) {
        if (!motion(root.getContext()) || root.getWidth() == 0 || root.getHeight() == 0) return null;
        try {
            Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            return b;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static void cover(FrameLayout root, Bitmap before, float x, float y, long delay) {
        covers++;
        Cover v = new Cover(root.getContext(), before, x, y);
        root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        float w = before.getWidth(), h = before.getHeight();
        float far = (float) Math.max(Math.max(Math.hypot(x, y), Math.hypot(w - x, y)),
                Math.max(Math.hypot(x, h - y), Math.hypot(w - x, h - y)));
        ValueAnimator a = ValueAnimator.ofFloat(0f, far);
        a.setDuration(DURATION);
        a.setStartDelay(delay);
        a.setInterpolator(new PathInterpolator(0.16f, 1f, 0.3f, 1f));
        a.addUpdateListener(an -> {
            v.radius = (float) an.getAnimatedValue();
            v.invalidate();
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) {
                RUNNING.remove(a);
                ViewGroup parent = (ViewGroup) v.getParent();
                if (parent != null) parent.removeView(v);
                v.before = null;
                before.recycle();
            }
        });
        RUNNING.add(a);
        a.start();
    }

    /** The old screen with a growing hole in it. */
    private static final class Cover extends View {
        Bitmap before;
        float radius;
        private final float x, y;
        private final Path hole = new Path();

        Cover(Context c, Bitmap before, float x, float y) {
            super(c);
            this.before = before;
            this.x = x;
            this.y = y;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @SuppressWarnings("deprecation")
        @Override protected void onDraw(Canvas cv) {
            if (before == null || before.isRecycled()) return;
            cv.save();
            if (radius > 0) {
                hole.reset();
                hole.addCircle(x, y, radius, Path.Direction.CW);
                if (Build.VERSION.SDK_INT >= 26) cv.clipOutPath(hole);
                else cv.clipPath(hole, Region.Op.DIFFERENCE);
            }
            cv.drawBitmap(before, 0, 0, null);
            cv.restore();
        }
    }
}
