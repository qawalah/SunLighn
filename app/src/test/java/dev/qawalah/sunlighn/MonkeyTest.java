package dev.qawalah.sunlighn;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.util.GeoPoint;
import org.robolectric.Robolectric;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * A seeded monkey (sweep of 3.24.2, 2026-11-23): random taps on whatever can be pressed, Back, the map tapped, the sheet
 * pulled, sliders moved, dialogs and menus answered, the screen restarted and the process killed, for a few hundred steps
 * per seed. It fails on an exception, an entry in the app's crash log, or a Back that never reaches Home; the message
 * gives the seed and the last steps, so a failure replays exactly with the same seed.
 *
 * Seeds: MONKEY_SEEDS=1,2,3 (default 1 to 6), steps per seed MONKEY_STEPS (default 300).
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MonkeyTest {
    private static final double SUB_LAT = 51.5007, SUB_LON = -0.1246, YOU_LAT = 51.5033, YOU_LON = -0.1196;
    private ActivityController<MainActivity> ctl;
    private MainActivity act;
    private final Deque<String> log = new ArrayDeque<>();
    private volatile long lastProgress;
    private volatile boolean done;

    @Before public void setUp() {
        Heights.enabled = false;
        Buildings.enabled = false;
        GeoMap.libreAllowed = false;
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @After public void tearDown() {
        done = true;
    }

    private final long seed;

    public MonkeyTest(long seed) { this.seed = seed; }

    /** One test per seed, so each starts with a clean sandbox (one long run held every screen and ran out of memory). */
    @ParameterizedRobolectricTestRunner.Parameters(name = "seed {0}")
    public static List<Object[]> seeds() {
        String seeds = System.getenv("MONKEY_SEEDS");
        List<Object[]> list = new ArrayList<>();
        if (seeds == null || seeds.isEmpty()) for (long s = 1; s <= 6; s++) list.add(new Object[]{s});
        else for (String s : seeds.split(",")) list.add(new Object[]{Long.parseLong(s.trim())});
        return list;
    }

    @Test public void monkey() throws Exception {
        int steps = System.getenv("MONKEY_STEPS") != null ? Integer.parseInt(System.getenv("MONKEY_STEPS")) : 300;
        startWatchdog();
        try {
            run(seed, steps);
        } finally {
            System.gc();
            Runtime rt = Runtime.getRuntime();
            all.append("seed ").append(seed).append(" done, heap used after GC ").append((rt.totalMemory() - rt.freeMemory()) >> 20).append(" MB\n");
            Files.write(new File("build/monkey-log-" + seed + ".txt").toPath(), all.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Writes the main thread's stack and stops the run when it has made no progress for a minute (an endless loop). */
    private void startWatchdog() {
        final Thread main = Thread.currentThread();
        lastProgress = System.currentTimeMillis();
        Thread w = new Thread(() -> {
            while (!done) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                if (!done && System.currentTimeMillis() - lastProgress > 60000) {
                    StringBuilder sb = new StringBuilder("MONKEY HANG after:\n");
                    synchronized (log) { for (String l : log) sb.append("  ").append(l).append('\n'); }
                    sb.append("main thread:\n");
                    for (StackTraceElement e : main.getStackTrace()) sb.append("  at ").append(e).append('\n');
                    try { Files.write(new File("build/monkey-hang.txt").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
                    System.err.println(sb);
                    Runtime.getRuntime().halt(3);
                }
            }
        }, "monkey-watchdog");
        w.setDaemon(true);
        w.start();
    }

    private final StringBuilder all = new StringBuilder();

    private void note(String s) {
        lastProgress = System.currentTimeMillis();
        String where = act == null ? "" : (ofType(root(), FrameScreen.class) != null ? "viewfinder"
                : ofType(root(), ComposeScreen.class) != null ? "compose" : ofType(root(), Trip.class) != null ? "saved"
                : ofType(root(), SettingsSheet.class) != null ? "settings" : ofType(root(), Tutorial.class) != null ? "guide"
                : ofType(root(), SearchScreen.class) != null ? "search" : onHome() ? "home" : stepName());
        all.append(s).append("  [now: ").append(where).append("]\n");
        synchronized (log) {
            log.addLast(s);
            while (log.size() > 40) log.removeFirst();
        }
    }

    private void run(long seed, int steps) throws Exception {
        Random r = new Random(seed);
        synchronized (log) { log.clear(); }
        // Half the seeds start with points in place and every guide seen; the others start as a first-time user.
        boolean fresh = seed % 2 == 0;
        launch(seed, fresh);
        for (int i = 0; i < steps; i++) {
            String did;
            try {
                did = step(r);
            } catch (Throwable t) {
                fail(report(seed, i, t.toString()), t);
                return;
            }
            note(seed + "#" + i + " " + did);
            String crash = crashLog();
            if (crash != null) fail(report(seed, i, "the crash log has an entry:\n" + crash), null);
            if (act.isFinishing()) {
                note(seed + "#" + i + " (left the app: open it again)");
                ctl = Robolectric.buildActivity(MainActivity.class).setup();
                act = ctl.get();
                idle();
            }
        }
        // No dead end: Back reaches Home, then leaves.
        for (int i = 0; i < 20 && !onHome(); i++) {
            closeDialogs();
            act.onBackPressed();
            idle();
            note(seed + " end: Back");
        }
        if (!onHome()) fail(report(seed, steps, "Back never reached Home"), null);
    }

    private String stepName() {
        View v = describedFrom(root(), "Step ");
        return v == null ? "?" : v.getContentDescription().toString();
    }

    private static View describedFrom(View v, String start) {
        if (v.getVisibility() != View.VISIBLE) return null;
        CharSequence d = v.getContentDescription();
        if (d != null && d.toString().startsWith(start)) return v;
        if (v instanceof ViewGroup) for (int i = ((ViewGroup) v).getChildCount() - 1; i >= 0; i--) {
            View r = describedFrom(((ViewGroup) v).getChildAt(i), start);
            if (r != null) return r;
        }
        return null;
    }

    private static void fail(String msg, Throwable cause) {
        AssertionError e = new AssertionError(msg);
        if (cause != null) e.initCause(cause);
        throw e;
    }

    private String report(long seed, int step, String what) {
        StringBuilder sb = new StringBuilder("seed " + seed + ", step " + step + ": " + what + "\nlast steps:\n");
        synchronized (log) { for (String l : log) sb.append("  ").append(l).append('\n'); }
        return sb.toString();
    }

    private void launch(long seed, boolean fresh) {
        SharedPreferences.Editor e = RuntimeEnvironment.getApplication().getSharedPreferences("sunlighn", Context.MODE_PRIVATE)
                .edit().clear().putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15");
        if (!fresh) {
            e.putBoolean("tutorial_done", true).putBoolean("asked_location", true);
            for (String g : new String[]{"line", "build", "find", "choose", "map", "now", "shot", "frame"}) e.putBoolean("guide_" + g, true);
            e.putString("you_lat", "" + YOU_LAT).putString("you_lon", "" + YOU_LON)
                    .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                    .putString("flow", seed % 3 == 0 ? "1,0,2" : seed % 3 == 1 ? "3,0,1" : "4,0,2+");
        }
        e.apply();
        new File(RuntimeEnvironment.getApplication().getFilesDir(), "last-crash.txt").delete();
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
        idle();
        note(seed + " launch " + (fresh ? "fresh" : "with points"));
    }

    /** One random action; returns what it did. */
    private String step(Random r) throws Exception {
        Dialog d = ShadowDialog.getLatestDialog();
        if (d != null && d.isShowing()) return answerDialog(d, r);
        android.widget.PopupMenu pm = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        if (pm != null && shadowOf(pm).isShowing()) {
            android.view.Menu m = pm.getMenu();
            int k = r.nextInt(m.size() + 1);
            pm.dismiss();
            if (k < m.size() && m.getItem(k).isEnabled()) {
                shadowOf(pm).getOnMenuItemClickListener().onMenuItemClick(m.getItem(k));
                idle();
                return "menu: " + m.getItem(k).getTitle();
            }
            return "menu dismissed";
        }
        int p = r.nextInt(100);
        if (p < 14) {                          // the step's main key, so the monkey gets through to Your shot and the viewfinder
            for (String k : new String[]{"Next", "Stand here", "Fly here", "See it", "See it in the viewfinder", "Open the viewfinder",
                    "Look through the camera", "Line it up", "Use my location"}) {
                View v = walkText(root(), k);
                if (v != null && v.isEnabled()) {
                    v.performClick();
                    idle();
                    return "main key: " + k;
                }
            }
        }
        if (p < 66) return tapSomething(r);
        if (p < 74) {
            act.onBackPressed();
            idle();
            return "Back";
        }
        if (p < 79) {
            GeoPoint g = new GeoPoint(SUB_LAT + (r.nextDouble() - 0.5) * 0.01, SUB_LON + (r.nextDouble() - 0.5) * 0.016);
            act.tapMap(g);
            idle();
            return "tap map " + g;
        }
        if (p < 83) return slideSomething(r);
        if (p < 86) {
            Sheet sh = (Sheet) ofType(root(), Sheet.class);
            if (sh == null || !sh.isShown()) return "no sheet";
            int stop = new int[]{Sheet.PEEK, Sheet.HALF, Sheet.FULL}[r.nextInt(3)];
            sh.setStop(stop, r.nextBoolean());
            for (int i = 0; i < 6; i++) shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(60));
            return "sheet to " + stop;
        }
        if (p < 89) return holdSomething(r);
        if (p < 93) {
            Thread.sleep(150 + r.nextInt(500));     // let worker threads finish
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500 + r.nextInt(8000)));   // and delayed work run
            lastProgress = System.currentTimeMillis();
            return "wait";
        }
        if (p < 97) {
            ctl.recreate();
            act = ctl.get();
            idle();
            return "restart (recreate)";
        }
        Bundle b = new Bundle();
        ctl.saveInstanceState(b).pause().stop().destroy();
        ctl = Robolectric.buildActivity(MainActivity.class).setup(b);
        act = ctl.get();
        idle();
        return "process death";
    }

    private String answerDialog(Dialog d, Random r) {
        if (d instanceof android.app.AlertDialog) {
            int[] which = {android.content.DialogInterface.BUTTON_POSITIVE, android.content.DialogInterface.BUTTON_NEGATIVE,
                    android.content.DialogInterface.BUTTON_NEUTRAL};
            int k = r.nextInt(4);
            if (k < 3) {
                android.widget.Button bt = ((android.app.AlertDialog) d).getButton(which[k]);
                if (bt != null && bt.isShown()) {
                    String t = bt.getText().toString();
                    bt.performClick();
                    idle();
                    return "dialog button: " + t;
                }
            }
        } else {
            List<View> c = clickables(d.getWindow().getDecorView());
            if (!c.isEmpty() && r.nextInt(3) > 0) {
                View v = c.get(r.nextInt(c.size()));
                String l = label(v);
                v.performClick();
                idle();
                return "dialog tap: " + l;
            }
        }
        d.cancel();
        idle();
        return "dialog cancelled";
    }

    private String tapSomething(Random r) {
        List<View> c = clickables(root());
        if (c.isEmpty()) return "nothing to tap";
        View v = c.get(r.nextInt(c.size()));
        String l = label(v);
        boolean twice = r.nextInt(12) == 0;
        v.performClick();
        if (twice && v.isAttachedToWindow() && v.isShown()) v.performClick();
        idle();
        return (twice ? "double tap: " : "tap: ") + l;
    }

    private String holdSomething(Random r) {
        List<View> c = new ArrayList<>();
        collect(root(), c, true);
        if (c.isEmpty() || r.nextBoolean()) {
            GeoPoint g = new GeoPoint(SUB_LAT + (r.nextDouble() - 0.5) * 0.004, SUB_LON + (r.nextDouble() - 0.5) * 0.006);
            try {
                java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("onMapHold", GeoPoint.class);
                m.setAccessible(true);
                m.invoke(act, g);
                idle();
                return "hold map " + g;
            } catch (NoSuchMethodException e) {
                return "nothing to hold";
            } catch (Exception e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            }
        }
        View v = c.get(r.nextInt(c.size()));
        String l = label(v);
        v.performLongClick();
        idle();
        return "hold: " + l;
    }

    private String slideSomething(Random r) throws Exception {
        List<View> bars = new ArrayList<>();
        collectType(root(), bars);
        if (bars.isEmpty()) return "no slider";
        View v = bars.get(r.nextInt(bars.size()));
        if (v instanceof SeekBar) {
            SeekBar sb = (SeekBar) v;
            int to = r.nextInt(3) == 0 ? (r.nextBoolean() ? 0 : sb.getMax()) : r.nextInt(sb.getMax() + 1);
            java.lang.reflect.Field f = SeekBar.class.getDeclaredField("mOnSeekBarChangeListener");
            f.setAccessible(true);
            SeekBar.OnSeekBarChangeListener l = (SeekBar.OnSeekBarChangeListener) f.get(sb);
            if (l != null) l.onStartTrackingTouch(sb);
            sb.setPressed(true);
            java.lang.reflect.Method m = android.widget.ProgressBar.class.getDeclaredMethod("setProgressInternal", int.class, boolean.class, boolean.class);
            m.setAccessible(true);
            m.invoke(sb, to, true, false);
            sb.setPressed(false);
            if (l != null) l.onStopTrackingTouch(sb);
            idle();
            return "slide " + label(v) + " to " + to + "/" + sb.getMax();
        }
        RangeBar rb = (RangeBar) v;
        double a = r.nextDouble() * 40, b = r.nextDouble() * 40;
        try {
            rb.commit(Math.min(a, b), Math.max(a, b));
        } catch (NoSuchMethodError e) {
            return "range bar without commit";
        }
        idle();
        return "range " + label(v) + " " + Math.round(Math.min(a, b)) + " to " + Math.round(Math.max(a, b));
    }

    // ---- views ----

    private List<View> clickables(View top) {
        List<View> out = new ArrayList<>();
        collect(top, out, false);
        return out;
    }

    private static void collect(View v, List<View> out, boolean longClick) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v.isEnabled() && v.getWidth() > 0 && v.getHeight() > 0 && (longClick ? v.isLongClickable() : v.isClickable())
                && !(v instanceof org.osmdroid.views.MapView)) out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out, longClick);
    }

    private static void collectType(View v, List<View> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if ((v instanceof SeekBar || v instanceof RangeBar) && v.isEnabled() && v.getWidth() > 0) out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectType(((ViewGroup) v).getChildAt(i), out);
    }

    private static View walkText(View v, String s) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof TextView && ((TextView) v).getText().toString().equals(s) && v.isClickable()) return v;
        if (v instanceof ViewGroup) for (int i = ((ViewGroup) v).getChildCount() - 1; i >= 0; i--) {
            View r = walkText(((ViewGroup) v).getChildAt(i), s);
            if (r != null) return r;
        }
        return null;
    }

    private static String label(View v) {
        CharSequence d = v.getContentDescription();
        if (d != null && d.length() > 0) return "'" + d + "'";
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) return "\"" + ((TextView) v).getText() + "\"";
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                String s = label(((ViewGroup) v).getChildAt(i));
                if (!s.startsWith("<")) return s;
            }
        }
        return "<" + v.getClass().getSimpleName() + ">";
    }

    private void closeDialogs() {
        Dialog d = ShadowDialog.getLatestDialog();
        if (d != null && d.isShowing()) {
            d.cancel();
            idle();
        }
    }

    private boolean onHome() {
        return ofType(root(), HomeScreen.class) != null && ofType(root(), Trip.class) == null
                && ofType(root(), SettingsSheet.class) == null && ofType(root(), Tutorial.class) == null;
    }

    private String crashLog() throws Exception {
        File f = new File(RuntimeEnvironment.getApplication().getFilesDir(), "last-crash.txt");
        if (!f.exists()) return null;
        String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        f.delete();
        return t;
    }

    private static View ofType(View v, Class<?> type) {
        if (type.isInstance(v)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View r = ofType(g.getChildAt(i), type);
                if (r != null) return r;
            }
        }
        return null;
    }

    private View root() { return act.getWindow().getDecorView(); }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idle();
        lastProgress = System.currentTimeMillis();
    }
}
