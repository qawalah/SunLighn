package dev.qawalah.sunlighn;

import static org.junit.Assume.assumeTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.views.MapView;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A remote control for the real app, for a tester agent (no phone needed). Runs only when ALI_DIR is set:
 * it waits for command files (cmd.1, cmd.2 ...) in that folder, does each like a finger would, and answers with
 * a picture of the screen and the list of what can be pressed (out.N.png, out.N.txt, then done.N).
 * Start: ALI_DIR=/some/dir ./gradlew testDebugUnitTest --tests '*AliDriver*' --rerun
 * Commands: see HELP below.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AliDriver {
    private static final int W = 1080, H = 2340, S = 2;       // the picture is half size: 540 x 1170
    private static final double PHONE_LAT = 51.5008, PHONE_LON = -0.1180;   // the phone stands in London

    static final String HELP = "Commands (x y are in the picture's numbers, 540 wide, 1170 tall):\n"
            + "  look                      show the screen again\n"
            + "  tap N | tap x y           tap thing number N, or a point\n"
            + "  hold N | hold x y         press and hold\n"
            + "  doubletap x y             two quick taps\n"
            + "  swipe x1 y1 x2 y2         drag a finger from one point to another\n"
            + "  scroll down|up [x y]      swipe to see more (at x y, default the middle of the screen)\n"
            + "  pinch out|in x y          two fingers apart (out) or together (in) around x y\n"
            + "  type some words           type into the text field that is open\n"
            + "  enter                     the keyboard's search / enter key\n"
            + "  back                      the phone's Back\n"
            + "  allow | deny              answer the phone's permission question\n"
            + "  wait [seconds]            wait (things that load from the internet), default 3\n"
            + "  phone                     what the phone itself shows: notifications, alarms set\n"
            + "  reopen                    close the app and open it again\n"
            + "  dark on|off               the phone's dark mode\n"
            + "  quit                      end the test\n";

    private File dir;
    private ActivityController<MainActivity> ctl;
    private MainActivity act;
    private final List<Target> targets = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private PopupMenu menu;
    private ShadowActivity.PermissionsRequest seenPerm, openPerm;
    private Rect dialogRect;
    private long lastCommand = System.currentTimeMillis();

    private static final class Target {
        final View view; final Rect r; final String label;
        final MenuItem item;
        Target(View v, Rect r, String label) { view = v; this.r = r; this.label = label; item = null; }
        Target(MenuItem m, Rect r) { view = null; this.r = r; label = m.getTitle().toString(); item = m; }
    }

    @Test public void drive() throws Exception {
        String d = System.getenv("ALI_DIR");
        assumeTrue("set ALI_DIR to drive the app", d != null && !d.isEmpty());
        dir = new File(d);
        dir.mkdirs();
        Heights.enabled = true;
        Application app = RuntimeEnvironment.getApplication();
        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        Location here = new Location(LocationManager.NETWORK_PROVIDER);
        here.setLatitude(PHONE_LAT);
        here.setLongitude(PHONE_LON);
        here.setAccuracy(20);
        here.setTime(System.currentTimeMillis());
        shadowOf(lm).simulateLocation(here);

        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
        settle(600);
        notes.add("You tapped the app's icon and it opened. Phone time: "
                + new SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.UK).format(new Date(System.currentTimeMillis()))
                + ". The phone is in London.");
        report(0, "(start)");

        for (int n = 1; ; n++) {
            File c = new File(dir, "cmd." + n);
            long idleSince = System.currentTimeMillis();
            while (!c.exists()) {
                Thread.sleep(100);
                idle();
                if (System.currentTimeMillis() - idleSince > 45 * 60_000L) return;   // nobody is driving any more
            }
            Thread.sleep(50);
            String cmd = new String(Files.readAllBytes(c.toPath()), StandardCharsets.UTF_8).trim();
            // the time a person spends looking passes on the phone too (up to 3 s), so short messages go away
            long thought = Math.min(3000, Math.max(0, System.currentTimeMillis() - lastCommand));
            advance(thought);
            try {
                if (cmd.equals("quit")) { notes.add("Test ended."); writeText(n, cmd, ""); touchDone(n); return; }
                run(cmd);
            } catch (Throwable t) {
                notes.add("The test tool could not do that: " + t);
            }
            act = ctl.get();
            report(n, cmd);
            lastCommand = System.currentTimeMillis();
        }
    }

    // ---- commands ----

    private void run(String cmd) throws Exception {
        String[] a = cmd.split("\\s+");
        String verb = a[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "look": break;
            case "help": notes.add(HELP); break;
            case "tap": tapLike(a, false); break;
            case "hold": tapLike(a, true); break;
            case "doubletap": { float[] p = point(a, 1); tap(p[0], p[1]); advance(80); tap(p[0], p[1]); settle(400); break; }
            case "swipe": swipe(f(a[1]) * S, f(a[2]) * S, f(a[3]) * S, f(a[4]) * S, 400); settle(500); break;
            case "scroll": {
                boolean down = a.length < 2 || a[1].equalsIgnoreCase("down");
                float x = a.length >= 4 ? f(a[2]) * S : W / 2f, y = a.length >= 4 ? f(a[3]) * S : H * 0.6f;
                float dy = 700;
                swipe(x, y, x, down ? y - dy : y + dy, 350);
                settle(500);
                break;
            }
            case "pinch": pinch(a[1].equalsIgnoreCase("out"), f(a[2]) * S, f(a[3]) * S); settle(400); break;
            case "type": {
                EditText e = field();
                if (e == null) { notes.add("There is no text field open to type into."); break; }
                String words = cmd.substring(cmd.indexOf(' ') + 1);
                e.requestFocus();
                e.append(words);
                settle(300);
                break;
            }
            case "enter": {
                EditText e = field();
                if (e == null) { notes.add("No keyboard is open."); break; }
                int action = e.getImeOptions() & android.view.inputmethod.EditorInfo.IME_MASK_ACTION;
                e.onEditorAction(action == 0 ? android.view.inputmethod.EditorInfo.IME_ACTION_DONE : action);
                settle(500);
                break;
            }
            case "back": back(); settle(500); break;
            case "allow": case "deny": answerPermission(verb.equals("allow")); settle(500); break;
            case "wait": {
                int s = a.length > 1 ? Math.max(1, Math.min(20, Integer.parseInt(a[1]))) : 3;
                for (int i = 0; i < s * 10; i++) { Thread.sleep(100); advance(100); }
                break;
            }
            case "phone": phone(); break;
            case "reopen": {
                ctl.pause().stop().destroy();
                ctl = Robolectric.buildActivity(MainActivity.class).setup();
                act = ctl.get();
                menu = null;
                settle(600);
                notes.add("You closed the app and opened it again from its icon.");
                break;
            }
            case "dark": {
                boolean on = a.length < 2 || a[1].equalsIgnoreCase("on");
                Configuration conf = new Configuration(act.getResources().getConfiguration());
                conf.uiMode = (conf.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | (on ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                RuntimeEnvironment.setQualifiers(on ? "+night" : "+notnight");
                ctl.configurationChange(conf);
                act = ctl.get();
                settle(600);
                notes.add("The phone's dark mode is now " + (on ? "on." : "off."));
                break;
            }
            default: notes.add("Unknown command. " + HELP);
        }
    }

    private void tapLike(String[] a, boolean hold) throws Exception {
        float x, y;
        if (a.length == 2) {
            int i = Integer.parseInt(a[1]);
            if (i < 1 || i > targets.size()) { notes.add("There is no thing number " + i + " on this screen."); return; }
            Target t = targets.get(i - 1);
            if (t.item != null) {          // a row of a small menu
                PopupMenu m = menu;
                menu = null;
                m.dismiss();
                if (t.item.isEnabled()) shadowOf(m).getOnMenuItemClickListener().onMenuItemClick(t.item);
                settle(500);
                return;
            }
            x = t.r.exactCenterX();
            y = t.r.exactCenterY();
        } else {
            x = f(a[1]) * S;
            y = f(a[2]) * S;
        }
        if (menu != null) { menu.dismiss(); menu = null; settle(200); return; }   // a tap beside a menu closes it
        if (openPerm != null) { notes.add("The phone's question is still open: answer with allow or deny."); return; }
        if (hold) { press(x, y, 900); settle(500); } else { tap(x, y); settle(450); }
    }

    private float[] point(String[] a, int from) { return new float[]{f(a[from]) * S, f(a[from + 1]) * S}; }

    private static float f(String s) { return Float.parseFloat(s); }

    private void back() {
        if (openPerm != null) { answerPermission(false); return; }
        if (menu != null) { menu.dismiss(); menu = null; return; }
        Dialog dl = dialog();
        if (dl != null) { dl.onBackPressed(); return; }
        act.onBackPressed();
        if (act.isFinishing()) notes.add("The app closed: you are back on the phone's home screen. Use reopen to open it again.");
    }

    private void answerPermission(boolean yes) {
        if (openPerm == null) { notes.add("The phone is not asking anything."); return; }
        ShadowActivity.PermissionsRequest r = openPerm;
        openPerm = null;
        int[] res = new int[r.requestedPermissions.length];
        for (int i = 0; i < res.length; i++) {
            if (yes) shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(r.requestedPermissions[i]);
            else shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(r.requestedPermissions[i]);
            res[i] = yes ? 0 : -1;
        }
        act.onRequestPermissionsResult(r.requestCode, r.requestedPermissions, res);
    }

    private void phone() {
        StringBuilder b = new StringBuilder("The phone's notifications: ");
        NotificationManager nm = (NotificationManager) act.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> all = shadowOf(nm).getAllNotifications();
        if (all.isEmpty()) b.append("none.");
        for (Notification n : all) b.append("\n  • ").append(n.extras.getCharSequence(Notification.EXTRA_TITLE))
                .append(": ").append(n.extras.getCharSequence(Notification.EXTRA_TEXT));
        List<Reminders.Item> rem = Reminders.all(act);
        b.append("\nReminders the app has asked the phone to ring: ").append(rem.isEmpty() ? "none." : "");
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK);
        for (Reminders.Item i : rem) b.append("\n  • ").append(f.format(new Date(i.timeMs))).append("  ").append(i.title)
                .append(": ").append(i.text);
        notes.add(b.toString());
    }

    // ---- fingers ----

    private void tap(float x, float y) {
        long t0 = SystemClock.uptimeMillis();
        send(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0));
        advance(60);
        send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0));
    }

    private void press(float x, float y, long ms) {
        long t0 = SystemClock.uptimeMillis();
        send(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0));
        for (long t = 0; t < ms; t += 50) advance(50);
        send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0));
    }

    private void swipe(float x1, float y1, float x2, float y2, long ms) {
        long t0 = SystemClock.uptimeMillis();
        send(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x1, y1, 0));
        int steps = (int) Math.max(6, ms / 16);
        for (int i = 1; i <= steps; i++) {
            advance(ms / steps);
            float k = i / (float) steps;
            send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                    x1 + (x2 - x1) * k, y1 + (y2 - y1) * k, 0));
        }
        advance(30);    // the finger stops before it lifts, so it is a drag, not a fling
        send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x2, y2, 0));
        advance(60);
        send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x2, y2, 0));
    }

    private void pinch(boolean out, float cx, float cy) {
        float from = out ? 80 : 360, to = out ? 360 : 80;
        long t0 = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()};
        pp[0].id = 0; pp[1].id = 1;
        pp[0].toolType = pp[1].toolType = MotionEvent.TOOL_TYPE_FINGER;
        send(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 1, pp, coords(cx, cy, from, 1), 0, 0, 1, 1, 0, 0, 0, 0));
        send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2, pp, coords(cx, cy, from, 2), 0, 0, 1, 1, 0, 0, 0, 0));
        for (int i = 1; i <= 20; i++) {
            advance(16);
            float g = from + (to - from) * i / 20f;
            send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 2, pp, coords(cx, cy, g, 2), 0, 0, 1, 1, 0, 0, 0, 0));
        }
        send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2, pp, coords(cx, cy, to, 2), 0, 0, 1, 1, 0, 0, 0, 0));
        send(MotionEvent.obtain(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1, pp, coords(cx, cy, to, 1), 0, 0, 1, 1, 0, 0, 0, 0));
    }

    private static MotionEvent.PointerCoords[] coords(float cx, float cy, float gap, int n) {
        MotionEvent.PointerCoords[] c = new MotionEvent.PointerCoords[n];
        for (int i = 0; i < n; i++) {
            c[i] = new MotionEvent.PointerCoords();
            c[i].x = cx + (i == 0 ? -gap / 2 : gap / 2);
            c[i].y = cy;
            c[i].pressure = 1;
            c[i].size = 1;
        }
        return c;
    }

    /** A finger on the glass: to the dialog on top if one is open (outside it closes it), else to the app. */
    private void send(MotionEvent e) {
        layout();
        Dialog dl = dialog();
        if (dl != null && dialogRect != null) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !dialogRect.contains((int) e.getX(), (int) e.getY())) {
                dl.cancel();
                e.recycle();
                return;
            }
            e.offsetLocation(-dialogRect.left, -dialogRect.top);
            dl.getWindow().getDecorView().dispatchTouchEvent(e);
        } else {
            act.dispatchTouchEvent(e);
        }
        e.recycle();
    }

    // ---- the phone's clock ----

    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }

    private void advance(long ms) {
        if (ms <= 0) { idle(); return; }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    /** Lets animations end and quick answers from the internet arrive. */
    private void settle(long ms) throws InterruptedException {
        for (long t = 0; t < ms; t += 100) { Thread.sleep(40); advance(100); }
        act = ctl.get();
    }

    // ---- what is on screen ----

    private Dialog dialog() {
        Dialog top = null;
        for (Dialog d : ShadowDialog.getShownDialogs()) if (d.isShowing()) top = d;
        return top;
    }

    private EditText field() {
        View f = act.getCurrentFocus();
        if (f instanceof EditText && f.isShown()) return (EditText) f;
        Dialog dl = dialog();
        View root = dl != null ? dl.getWindow().getDecorView() : act.getWindow().getDecorView();
        return firstEdit(root);
    }

    private static EditText firstEdit(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof EditText) return (EditText) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                EditText e = firstEdit(g.getChildAt(i));
                if (e != null) return e;
            }
        }
        return null;
    }

    private void layout() {
        View r = act.getWindow().getDecorView();
        r.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        r.layout(0, 0, W, H);
        Dialog dl = dialog();
        dialogRect = null;
        if (dl != null) {
            View dv = dl.getWindow().getDecorView();
            int w = W - 2 * 72;
            dv.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H - 300, View.MeasureSpec.AT_MOST));
            int h = dv.getMeasuredHeight();
            dialogRect = new Rect(72, (H - h) / 2, 72 + w, (H - h) / 2 + h);
            dv.layout(0, 0, w, h);
        }
    }

    private void report(int n, String cmd) throws Exception {
        // things the app did outside itself
        ShadowActivity sa = shadowOf(act);
        Intent go;
        while ((go = sa.getNextStartedActivity()) != null) notes.add(describe(go));
        CharSequence toast = ShadowToast.getTextOfLatestToast();
        if (toast != null) { notes.add("A short message popped up: " + toast); ShadowToast.reset(); }
        ShadowActivity.PermissionsRequest pr = sa.getLastRequestedPermission();
        if (pr != null && pr != seenPerm) { seenPerm = pr; openPerm = pr; }
        PopupMenu latest = ShadowPopupMenu.getLatestPopupMenu();
        if (latest != null && latest != menu && shadowOf(latest).isShowing()) menu = latest;
        if (menu != null && !shadowOf(menu).isShowing()) menu = null;

        Reveal.finishAll();
        for (int i = 0; i < 12; i++) { Thread.sleep(100); idle(); }   // map tiles and quick answers arrive
        layout();
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        act.getWindow().getDecorView().draw(c);
        targets.clear();
        List<String> words = new ArrayList<>();
        Rect mapRect = null;
        Dialog dl = dialog();
        if (dl != null) {
            c.drawColor(0x99000000);
            c.save();
            c.translate(dialogRect.left, dialogRect.top);
            dl.getWindow().getDecorView().draw(c);
            c.restore();
            collect(dl.getWindow().getDecorView(), dialogRect.left, dialogRect.top, new Rect(dialogRect), dl.getWindow().getDecorView(), words);
        } else {
            View root = act.getWindow().getDecorView();
            collect(root, 0, 0, new Rect(0, 0, W, H), root, words);
            mapRect = mapArea(root);
        }
        prune();
        if (menu != null) drawMenu(c);
        if (openPerm != null) drawPermission(c);

        Bitmap half = Bitmap.createScaledBitmap(b, W / S, H / S, true);
        try (FileOutputStream out = new FileOutputStream(new File(dir, "out." + n + ".png"))) {
            half.compress(Bitmap.CompressFormat.PNG, 100, out);
        }

        StringBuilder t = new StringBuilder();
        for (String s : notes) t.append("What happened: ").append(s).append('\n');
        notes.clear();
        if (openPerm != null) t.append("The PHONE (not the app) is asking: Allow SunLighn to ").append(permWords(openPerm))
                .append("? Answer with: allow, or deny.\n");
        t.append("Picture: ").append(new File(dir, "out." + n + ".png").getAbsolutePath()).append("  (540 wide, 1170 tall)\n");
        if (dl != null) t.append("A box is open over the app (Back or a tap outside it closes it).\n");
        if (menu != null) t.append("A small menu is open (the test tool drew it like the phone would).\n");
        t.append("\nThings you can press:\n");
        if (targets.isEmpty()) t.append("  (nothing)\n");
        for (int i = 0; i < targets.size(); i++) {
            Target g = targets.get(i);
            t.append(String.format(Locale.US, "  [%d] %s  at (%d, %d), size %d x %d%n", i + 1, g.label,
                    g.r.centerX() / S, g.r.centerY() / S, g.r.width() / S, g.r.height() / S));
        }
        if (mapRect != null) t.append(String.format(Locale.US, "\nThe map shows from (%d, %d) to (%d, %d), where it is not covered.%n",
                mapRect.left / S, mapRect.top / S, mapRect.right / S, mapRect.bottom / S));
        t.append("\nWords on the screen (top to bottom):\n");
        for (String w : words) t.append("  ").append(w).append('\n');
        writeText(n, cmd, t.toString());
        touchDone(n);
    }

    /** One entry per thing: drop a container holding two or more others, and an inner copy with its parent's name. */
    private void prune() {
        List<Target> keep = new ArrayList<>();
        for (Target t : targets) {
            if (t.view == null) { keep.add(t); continue; }
            int inside = 0;
            boolean copy = false;
            for (Target o : targets) {
                if (o == t || o.view == null) continue;
                if (within(o.view, t.view)) inside++;
                if (within(t.view, o.view) && o.label.equals(t.label)) copy = true;
            }
            if (inside < 2 && !copy) keep.add(t);
        }
        targets.clear();
        targets.addAll(keep);
    }

    private static boolean within(View v, View outer) {
        for (android.view.ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) if (p == outer) return true;
        return false;
    }

    private void writeText(int n, String cmd, String body) throws Exception {
        String head = "You did: " + cmd + "\n";
        Files.write(new File(dir, "out." + n + ".txt").toPath(), (head + body).getBytes(StandardCharsets.UTF_8));
    }

    private void touchDone(int n) throws Exception {
        Files.write(new File(dir, "done." + n).toPath(), new byte[0]);
    }

    private static String permWords(ShadowActivity.PermissionsRequest r) {
        String p = String.join(",", r.requestedPermissions);
        if (p.contains("LOCATION")) return "access this device's location";
        if (p.contains("NOTIFICATIONS")) return "send you notifications";
        return "use " + p;
    }

    private String describe(Intent i) {
        String a = i.getAction() == null ? "" : i.getAction();
        if (Intent.ACTION_CHOOSER.equals(a)) {
            Intent in = i.getParcelableExtra(Intent.EXTRA_INTENT);
            String text = in == null ? null : in.getStringExtra(Intent.EXTRA_TEXT);
            return "The phone's share menu opened (to send to another app), with this text:\n---\n" + text + "\n---";
        }
        if (Intent.ACTION_INSERT.equals(a)) return "Your calendar app opened a new event: \"" + i.getStringExtra("title")
                + "\", " + i.getStringExtra("eventLocation") + ". You came back to SunLighn.";
        if (Intent.ACTION_VIEW.equals(a) && i.getData() != null && "geo".equals(i.getData().getScheme()))
            return "Your maps app opened at " + i.getData() + ". You came back to SunLighn.";
        return "Another app opened: " + a + " " + i.getData() + ". You came back to SunLighn.";
    }

    private void drawMenu(Canvas c) {
        Menu m = menu.getMenu();
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFFF4F4F6);
        tx.setTextSize(48);
        int rowH = 144, w = 560, h = rowH * m.size();
        int x = W - w - 24, y = 220;
        bg.setShadowLayer(24, 0, 8, 0x66000000);
        c.drawRoundRect(x, y, x + w, y + h, 24, 24, bg);
        for (int i = 0; i < m.size(); i++) {
            MenuItem it = m.getItem(i);
            tx.setColor(it.isEnabled() ? 0xFF1A1A1A : 0xFF9A9A9A);
            c.drawText(it.getTitle().toString(), x + 48, y + rowH * i + rowH / 2f + 16, tx);
            Rect r = new Rect(x, y + rowH * i, x + w, y + rowH * (i + 1));
            targets.add(new Target(it, r));
        }
    }

    private void drawPermission(Canvas c) {
        c.drawColor(0x99000000);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFFECEFF4);
        c.drawRoundRect(90, 800, W - 90, 1500, 60, 60, bg);
        tx.setColor(0xFF1A1A1A);
        tx.setTextSize(54);
        c.drawText("Allow SunLighn to", 160, 940, tx);
        c.drawText(permWords(openPerm) + "?", 160, 1010, tx);
        tx.setColor(0xFF2A5BD7);
        tx.setTextSize(50);
        c.drawText("Allow", 160, 1250, tx);
        c.drawText("Don't allow", 160, 1400, tx);
    }

    /** Pressable things, and words, that a person can see: on screen, not hidden behind something on top. */
    private void collect(View v, int ox, int oy, Rect clip, View root, List<String> words) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.05f) return;
        int left = ox + v.getLeft() + Math.round(v.getTranslationX()), top = oy + v.getTop() + Math.round(v.getTranslationY());
        Rect r = new Rect(left, top, left + v.getWidth(), top + v.getHeight());
        Rect vis = new Rect(r);
        if (!vis.intersect(clip)) return;
        boolean pressable = (v.isClickable() || v.isLongClickable()) && !(v instanceof MapView) && v != root
                && !(v.getWidth() * (long) v.getHeight() > W * (long) H * 6 / 10 && v.getContentDescription() == null);
        if (pressable && seen(root, v, vis, ox - v.getLeft() - Math.round(v.getTranslationX()) + left - ox, 0)) {
            String label = labelOf(v);
            String kind = v instanceof EditText ? "text field" : v instanceof SeekBar ? "slider" : "";
            StringBuilder s = new StringBuilder();
            s.append(label.isEmpty() ? "(no words)" : "\"" + label + "\"");
            if (!kind.isEmpty()) s.append(" ").append(kind);
            if (!v.isEnabled()) s.append(" (greyed out)");
            if (v.isLongClickable() && !v.isClickable()) s.append(" (press and hold)");
            if (vis.height() < r.height() - 6 || vis.width() < r.width() - 6) s.append(" (partly out of sight)");
            targets.add(new Target(v, vis, s.toString()));
        }
        if (v instanceof TextView && !(v instanceof EditText)) {
            CharSequence tx = ((TextView) v).getText();
            if (tx != null && tx.toString().trim().length() > 0 && seen(root, v, vis, 0, 0)) words.add("\"" + tx.toString().trim().replace('\n', ' ') + "\"");
        } else if (v instanceof EditText) {
            EditText e = (EditText) v;
            words.add("[text field: " + (e.getText().length() > 0 ? "\"" + e.getText() + "\"" : "empty, hint \"" + e.getHint() + "\"") + "]");
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int cx = left - v.getScrollX(), cy = top - v.getScrollY();
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), cx, cy, vis, root, words);
        }
    }

    /** Is the middle of what shows of v the topmost thing there (v or something inside it)? */
    private boolean seen(View root, View v, Rect vis, int unused1, int unused2) {
        Rect base = dialogRect != null && root != act.getWindow().getDecorView() ? dialogRect : new Rect(0, 0, W, H);
        View hit = hit(root, base.left, base.top, vis.exactCenterX(), vis.exactCenterY());
        for (View h = hit; h != null; h = h.getParent() instanceof View ? (View) h.getParent() : null) if (h == v) return true;
        // the point lies on a child that is inside v, or hit is v's own ancestor background drawn behind it
        return hit == null;
    }

    private static View hit(View v, int ox, int oy, float x, float y) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.05f) return null;
        int left = ox + v.getLeft() + Math.round(v.getTranslationX()), top = oy + v.getTop() + Math.round(v.getTranslationY());
        if (x < left || y < top || x >= left + v.getWidth() || y >= top + v.getHeight()) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int cx = left - v.getScrollX(), cy = top - v.getScrollY();
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View r = hit(g.getChildAt(i), cx, cy, x, y);
                if (r != null) return r;
            }
            boolean solid = v.getBackground() != null || v.isClickable() || v instanceof MapView;
            return solid ? v : null;
        }
        return v;
    }

    private static String labelOf(View v) {
        CharSequence d = v.getContentDescription();
        if (d != null && d.length() > 0) return d.toString();
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) return ((TextView) v).getText().toString();
        StringBuilder b = new StringBuilder();
        texts(v, b);
        String s = b.toString().trim().replace('\n', ' ');
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }

    private static void texts(View v, StringBuilder b) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) b.append(b.length() > 0 ? " · " : "").append(t);
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), b);
    }

    private Rect mapArea(View root) {
        MapView m = find(root, MapView.class);
        if (m == null || !m.isShown()) return null;
        int[] at = new int[2];
        m.getLocationInWindow(at);
        Rect r = new Rect(at[0], at[1], at[0] + m.getWidth(), at[1] + m.getHeight());
        // only say "map" when a good part of it can be seen
        int seen = 0;
        for (int i = 1; i < 10; i++) for (int j = 1; j < 10; j++) {
            View h = hit(root, 0, 0, r.left + r.width() * i / 10f, r.top + r.height() * j / 10f);
            for (View x = h; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null) if (x == m) { seen++; break; }
        }
        return seen >= 8 ? r : null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> T find(View v, Class<T> type) {
        if (type.isInstance(v)) return (T) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            T r = find(((ViewGroup) v).getChildAt(i), type);
            if (r != null) return r;
        }
        return null;
    }
}
