package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The sweep of 3.24.2 (2026-11-23): what 3.24 added (Find the spot, Now, the far end of a street, the guides said once)
 * through Back, restarts, double taps, odd places and the ends of every slider. Fails on a crash (also one written to the
 * crash log), a dead end or a contradiction on screen. Findings: docs/sweep-3.24.2.md.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Sweep324Test {
    private static final double YOU_LAT = 51.5033, YOU_LON = -0.1196, SUB_LAT = 51.5007, SUB_LON = -0.1246;
    private static final Double[] YOU = {YOU_LAT, YOU_LON}, SUB = {SUB_LAT, SUB_LON};
    private static final String[] GUIDES = {"line", "build", "find", "choose", "map", "now", "shot", "frame"};

    private ActivityController<MainActivity> ctl;
    private MainActivity act;


    /** Buildings read by an earlier test stay in Buildings' static caches: start each test without them. */
    @SuppressWarnings("rawtypes")
    static void forgetBuildings() {
        for (String name : new String[]{"MEMORY", "TILES", "FAILED", "ASKING"}) {
            try {
                java.lang.reflect.Field f = Buildings.class.getDeclaredField(name);
                f.setAccessible(true);
                Object o = f.get(null);
                synchronized (o) {
                    if (o instanceof java.util.Map) ((java.util.Map) o).clear();
                    else if (o instanceof java.util.Collection) ((java.util.Collection) o).clear();
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Before public void setUp() {
        Heights.enabled = false;
        Buildings.enabled = false;
        forgetBuildings();
        GeoMap.libreAllowed = false;
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @After public void tearDown() throws Exception {
        Heights.enabled = false;
        Buildings.enabled = false;
        File f = new File(RuntimeEnvironment.getApplication().getFilesDir(), "last-crash.txt");
        if (f.exists()) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            f.delete();
            fail("the crash log has an entry:\n" + text);
        }
    }

    // ---------------------------------------------------------------- set-up

    private SharedPreferences.Editor clean() {
        SharedPreferences.Editor e = RuntimeEnvironment.getApplication().getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("tutorial_done", true).putBoolean("asked_location", true);
        for (String g : GUIDES) e.putBoolean("guide_" + g, true);
        return e;
    }

    private void launch(SharedPreferences.Editor e, Double[] you, Double[] sub, String flow) {
        double cLat = sub != null ? sub[0] : you != null ? you[0] : 51.502, cLon = sub != null ? sub[1] : you != null ? you[1] : -0.122;
        e.putString("cam_lat", "" + cLat).putString("cam_lon", "" + cLon).putString("cam_zoom", "15");
        if (you != null) e.putString("you_lat", "" + you[0]).putString("you_lon", "" + you[1]);
        if (sub != null) e.putString("subject_lat", "" + sub[0]).putString("subject_lon", "" + sub[1]).putString("subject_name", "Big Ben");
        if (flow != null) e.putString("flow", flow);
        e.apply();
        new File(RuntimeEnvironment.getApplication().getFilesDir(), "last-crash.txt").delete();
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
        idle();
    }

    private void launch(Double[] you, Double[] sub, String flow) { launch(clean(), you, sub, flow); }

    private void recreate() {
        ctl.recreate();
        act = ctl.get();
        idle();
    }

    private void processDeath() {
        Bundle b = new Bundle();
        ctl.saveInstanceState(b).pause().stop().destroy();
        ctl = Robolectric.buildActivity(MainActivity.class).setup(b);
        act = ctl.get();
        idle();
    }

    private void doubleTap(View v) {
        assertNotNull(v);
        v.performClick();
        if (v.isAttachedToWindow() && v.isShown()) v.performClick();
        idle();
    }

    // ---------------------------------------------------------------- Find the spot

    /** Find the spot: Back, a restart and process death at every step; double taps on Next, Stand here and Change. */
    @Test public void findBackRestartDoubleTapAtEveryStep() throws Exception {
        launch(null, null, null);
        click(byDescription("Find the spot"));
        assertStep("Step 1 of 4");
        recreate();
        assertStep("Step 1 of 4");
        act.tapMap(new GeoPoint(SUB_LAT, SUB_LON));
        idle();
        closeWhatIsIt();
        doubleTap(find("Next"));
        assertStep("Step 2 of 4");
        recreate();
        assertStep("Step 2 of 4");
        processDeath();
        assertStep("Step 2 of 4");
        doubleTap(find("Next"));
        assertStep("Step 3 of 4");
        waitFor("Stand here");
        recreate();
        assertStep("Step 3 of 4");
        processDeath();
        assertStep("Step 3 of 4");
        waitFor("Stand here");
        doubleTap(byDescription("Change how you want the sun"));
        assertStep("Step 2 of 4");
        click(find("Next"));
        waitFor("Stand here");
        doubleTap(find("Stand here"));
        assertStep("Step 4 of 4");
        assertNull("one tap opens Your shot, not also the viewfinder", ofType(root(), FrameScreen.class));
        recreate();
        assertStep("Step 4 of 4");
        processDeath();
        assertStep("Step 4 of 4");
        act.onBackPressed();
        idle();
        assertStep("Step 3 of 4");
        assertBackLeaves("Find the spot");
    }

    /** Find the spot in odd places, sun and moon: never a crash, always a way on or a reason. */
    private void findIn(String where, Double[] sub, boolean drone) throws Exception {
        for (int moon = 0; moon <= 1; moon++) {
            launch(clean().putBoolean("moon", moon == 1).putBoolean("drone", drone), null, sub, "4," + moon + ",2+");
            click(describedFrom("Continue"));
            assertStep("Step 3 of 4");
            waitUntil(() -> walk(root(), "Working it out", true, false) == null, 40000);
            idle();
            shot("find-" + where.replaceAll("[^A-Za-z0-9]", "_") + (moon == 1 ? "-moon" : "-sun"));
            String what = where + (moon == 1 ? " (moon)" : " (sun)");
            boolean stand = findOrNull("Stand here") != null || findOrNull("Fly here") != null;
            boolean never = findOrNull("Not in the coming year") != null;
            assertTrue(what + ": Where to stand offers no answer and no reason", stand || never);
            if (never) assertNotNull(what + ": never comes with what works", walk(root(), "on the horizon", true, false));
            View key = findOrNull(drone ? "Fly here" : "Stand here");
            if (key != null) {
                click(key);
                View warn = walk(root(), "That spot is ", true, false);
                if (warn == null) {
                    assertStep("Step 4 of 4");
                    waitFor("Open the viewfinder");
                    assertTrue(what + ": Your shot's moment is in the future", prefs().getLong("shot_ms", 0) > System.currentTimeMillis() - 60000);
                }
            }
            assertBackLeaves(what);
        }
    }

    @Test public void findTromsoPolarNight() throws Exception { findIn("Tromsø", new Double[]{69.6480, 18.9520}, true); }

    @Test public void findMcMurdoMidnightSun() throws Exception { findIn("McMurdo", new Double[]{-77.8490, 166.6640}, true); }

    @Test public void findNorthPole() throws Exception { findIn("North Pole", new Double[]{89.9990, 0.0}, true); }

    @Test public void findAcrossTheDateLine() throws Exception { findIn("Taveuni", new Double[]{-16.8010, -179.9990}, true); }

    /** At sea, on foot: every spot is water. Stand here is dimmed, says why when tapped, and opens nothing. */
    @Test public void findAtSea() throws Exception {
        Double[] sea = {54.5000, 3.0000};
        fillWater(sea[0], sea[1], 6);
        launch(clean().putBoolean("drone", false), null, sea, "4,0,2+");
        click(describedFrom("Continue"));
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 40000);
        waitUntil(() -> walk(root(), "on water", true, false) != null, 20000);
        shot("find-at-sea");
        View stand = find("Stand here");
        assertNotNull("the head says the spot is on water", walk(root(), "on water", true, false));
        click(stand);
        assertStep("Step 3 of 4");
        assertBackLeaves("find at sea");
    }

    /** Every Light, Where it sits and the ends of the height and distance sliders: the answer agrees with the choice. */
    @Test public void findEveryChoiceAndSliderEnd() throws Exception {
        for (int moon = 0; moon <= 1; moon++) {
            launch(clean().putBoolean("moon", moon == 1).putBoolean("drone", true), null, SUB, "4," + moon + ",1+");
            click(describedFrom("Continue"));
            assertStep("Step 2 of 4");
            sheetTo(Sheet.FULL);
            for (String sits : new String[]{"Horizon", "Middle", "On top"}) {
                click(find(sits));
                for (String light : new String[]{"Any", moon == 1 ? "Moonset" : "Sunset", moon == 1 ? "Moonrise" : "Sunrise"}) {
                    pick("Light", light);
                    for (int end : new int[]{0, -1}) {
                        SeekBar h = (SeekBar) walk(root(), "Sun height", false, true);
                        if (h == null) h = (SeekBar) walk(root(), "Moon height", false, true);
                        if (h == null) continue;      // Horizon: no height to set
                        slide(h, end < 0 ? h.getMax() : 0);
                        String what = (moon == 1 ? "moon " : "sun ") + sits + " / " + light + " / height " + (end < 0 ? "top" : "0");
                        assertStep("Step 2 of 4");
                        View d = walk(root(), "Distance ", true, false);
                        assertNotNull(what + ": the distance it gives is said", d);
                    }
                }
            }
            // Fixed: the distance at both ends
            click(find("Fixed"));
            SeekBar dist = (SeekBar) byDescription("Distance from the subject");
            for (int end : new int[]{0, dist.getMax()}) {
                slide(dist, end);
                assertNotNull("fixed at " + end + ": the height it gives is said", walk(root(), "From the fixed distance", true, false));
            }
            click(find("Auto"));
            click(find("Behind you"));
            assertNull(findOrNull("Where it sits on the subject"));
            sheetTo(Sheet.HALF);
            click(find("Next"));
            assertStep("Step 3 of 4");
            waitUntil(() -> walk(root(), "Working it out", true, false) == null, 40000);
            shot("find-choices-" + moon);
            assertBackLeaves("find every choice " + moon);
        }
    }

    /** The answer's height is the height asked for (Where to stand: "x.x° up" against How you want it). */
    @Test public void findAnswerMatchesTheHeight() throws Exception {
        launch(clean().putBoolean("drone", true).putInt("find_sits", Spots.ON_TOP).putFloat("find_alt", 6f), null, SUB, "4,0,2+");
        click(describedFrom("Continue"));
        waitFor("Fly here");
        TextView words = (TextView) walk(root(), "° up", true, false);
        assertNotNull(words);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9.]+)° up").matcher(words.getText());
        assertTrue(words.getText().toString(), m.find());
        assertEquals("the answer's height: '" + words.getText() + "'", 6.0, Double.parseDouble(m.group(1)), 0.3);
        // and its distance is the one Auto works out for 6° on top of a 30 m building
        String want = "Distance " + Fmt.distance(Spots.distanceFor(Spots.ON_TOP, 30, 6));
        assertNotNull("Where to stand says '" + want + "' (it says '" + words.getText() + "')", walk(root(), want, true, false));
    }

    // ---------------------------------------------------------------- Now

    /** Now with no location yet, then a tap; Look through the camera; restart and process death; Back. */
    @Test public void nowWithoutLocationThenTap() throws Exception {
        launch(null, null, null);
        click(byDescription("Now"));
        assertStep("Step Now");
        shot("now-no-location");
        assertTrue("Now with no place says what to do", walk(root(), "location", true, false) != null
                || walk(root(), "Tap the map", true, false) != null || walk(root(), "tap the map", true, false) != null);
        View cam = findOrNull("Look through the camera");
        if (cam != null) {
            click(cam);
            act.onBackPressed();
            idle();
            assertStep("Step Now");
        }
        act.tapMap(new GeoPoint(51.51, -0.13));
        idle();
        doubleTap(find("Look through the camera"));
        assertNotNull(ofType(root(), FrameScreen.class));
        act.onBackPressed();
        idle();
        assertNull("one Back closes the camera view", ofType(root(), FrameScreen.class));
        recreate();
        assertStep("Step Now");
        processDeath();
        assertStep("Step Now");
        assertBackLeaves("Now");
    }

    /** Now in odd places, sun and moon, with the day chart moved to both ends. */
    private void nowIn(String where, double lat, double lon) throws Exception {
        for (int moon = 0; moon <= 1; moon++) {
            launch(clean().putBoolean("moon", moon == 1).putString("here_lat", "" + lat).putString("here_lon", "" + lon), null, null, null);
            click(byDescription("Now"));
            assertStep("Step Now");
            DayChart dc = (DayChart) ofType(root(), DayChart.class);
            assertNotNull(where + ": the day chart", dc);
            for (String key : new String[]{"Next day", "Previous day", "Previous day", "Now"}) {
                View k = walk(root(), key, false, true);
                if (k == null) k = findOrNull(key);
                if (k != null) click(k);
            }
            shot("now-" + where.replaceAll("[^A-Za-z0-9]", "_") + "-" + moon);
            click(find("Look through the camera"));
            assertNotNull(ofType(root(), FrameScreen.class));
            act.onBackPressed();
            idle();
            assertBackLeaves("Now " + where);
        }
    }

    @Test public void nowNorthPole() throws Exception { nowIn("North Pole", 89.999, 0); }

    @Test public void nowMcMurdo() throws Exception { nowIn("McMurdo", -77.846, 166.676); }

    @Test public void nowDateLine() throws Exception { nowIn("Taveuni", -16.8, 179.999); }

    /** Now never touches a tool: after Now, Continue opens the tool as it was. */
    @Test public void nowKeepsTheToolsPoints() throws Exception {
        launch(YOU, SUB, "4,0,2+");
        click(byDescription("Now"));
        act.tapMap(new GeoPoint(51.52, -0.10));
        idle();
        act.onBackPressed();
        idle();
        click(describedFrom("Continue"));
        assertStep("Step 3 of 4");
        assertEquals("" + SUB_LAT, prefs().getString("subject_lat", ""));
    }

    // ---------------------------------------------------------------- Line up a street: the far end

    /** A tall building in front of You hides every time: the card names a point along the street and Move along, with Undo. */
    @Test public void streetMoveAlongThenUndo() throws Exception {
        Buildings.enabled = true;
        launch(YOU, SUB, "1,0,2");
        // 60 m towards the far end, 120 m tall, 40 m wide across the street
        double b = Astro.bearing(YOU_LAT, YOU_LON, SUB_LAT, SUB_LON);
        double[] at = Astro.destination(YOU_LAT, YOU_LON, b, 60);
        UserBuildings.add(act, new UserBuildings.Mine(at[0], at[1], 40, 20, b + 90, 120));
        click(describedFrom("Continue"));
        assertStep("Step 3 of 4");
        View move = null;
        for (int i = 0; i < 600 && move == null; i++) {
            Thread.sleep(50);
            idle();
            move = walk(root(), "Move along the street to where the sun clears the buildings", false, true);
        }
        shot("street-hidden");
        if (move == null) {
            sheetTo(Sheet.FULL);
            move = walk(root(), "Move along the street to where the sun clears the buildings", false, true);
        }
        assertNotNull("every time hidden by a 120 m block 60 m ahead: Move along the street is offered", move);
        double lat0 = Double.parseDouble(prefs().getString("you_lat", "0"));
        doubleTap(move);
        waitUntil(() -> walk(root(), "Undo", false, false) != null, 5000);
        double lat1 = Double.parseDouble(prefs().getString("you_lat", "0"));
        assertTrue("You moved", Math.abs(lat1 - lat0) > 1e-6);
        double moved = Astro.distance(lat0, YOU_LON, lat1, Double.parseDouble(prefs().getString("you_lon", "0")));
        double off = Math.abs(FrameMath.diff(Astro.bearing(lat0, YOU_LON, lat1, Double.parseDouble(prefs().getString("you_lon", "0"))), b));
        assertTrue("You moved along the street (off by " + off + "°)", off < 3 || Math.abs(off - 180) < 3);
        click(find("Undo"));
        assertEquals("Undo puts You back (a double tap moved twice?)", lat0, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        assertBackLeaves("street");
    }

    // ---------------------------------------------------------------- the guides (3.24.2: each said once)

    /** A first-time user: never two guides at once, Back closes each, and each comes only once, through a restart too. */
    @Test public void guidesNeverStackAndComeOnce() throws Exception {
        SharedPreferences.Editor e = RuntimeEnvironment.getApplication().getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("asked_location", true);
        launch(e, null, null, null);
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 6 && ofType(root(), Tutorial.class) != null; i++) {
            assertTrue("two guides at once on the first start", count(root(), Tutorial.class) <= 1);
            seen.add(firstTitle());
            act.onBackPressed();
            idle();
        }
        assertTrue("the first start shows a guide: " + seen, !seen.isEmpty());
        assertTrue("Home after the first guides: " + seen, onHome());
        recreate();
        assertNull("a restart does not show the first guides again", ofType(root(), Tutorial.class));
        for (String mode : new String[]{"Line up a street", "Find the spot", "Build the shot", "Now"}) {
            click(byDescription(mode));
            for (int i = 0; i < 4; i++) {
                idle();
                assertTrue(mode + ": two guides at once", count(root(), Tutorial.class) <= 1);
                if (ofType(root(), Tutorial.class) == null) break;
                act.onBackPressed();
                idle();
            }
            assertNull(mode + ": Back closed its guides", ofType(root(), Tutorial.class));
            assertNotNull(mode + ": the mode is open under the guides", describedFrom(root(), "Step "));
            goHome();
            click(byDescription(mode));
            idle();
            assertNull(mode + ": its guide comes once", ofType(root(), Tutorial.class));
            goHome();
        }
    }

    /** A guide showing when the screen restarts: it comes back, or it is gone and marked seen; never two. */
    @Test public void restartWithAGuideOpen() throws Exception {
        SharedPreferences.Editor e = RuntimeEnvironment.getApplication().getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("asked_location", true);
        launch(e, null, null, null);
        assertNotNull(ofType(root(), Tutorial.class));
        recreate();
        assertTrue("after a restart at most one guide", count(root(), Tutorial.class) <= 1);
        processDeath();
        assertTrue("after process death at most one guide", count(root(), Tutorial.class) <= 1);
        for (int i = 0; i < 6 && ofType(root(), Tutorial.class) != null; i++) {
            act.onBackPressed();
            idle();
        }
        assertTrue(onHome());
    }

    // ---------------------------------------------------------------- one tool to another

    /** Your shot of each tool offers another; Back from there walks back, and Saved reopens each tool's shot. */
    @Test public void toolToToolAndSaved() throws Exception {
        // Find the spot → Your shot → Build the shot
        launch(clean().putBoolean("drone", true), null, SUB, "4,0,2+");
        offline();
        click(describedFrom("Continue"));
        waitFor("Fly here");
        click(find("Fly here"));
        assertStep("Step 4 of 4");
        click(byDescription("Save this shot"));
        View other = byDescription("Build the shot: another tool, with this subject");
        click(other);
        assertNotNull("Build the shot opened with the subject", ofType(root(), ComposeScreen.class));
        act.onBackPressed();
        idle();
        assertNotNull("Back from the other tool: somewhere with a step bar", describedFrom(root(), "Step "));
        goHome();
        // Line up a street → Your shot → Build the shot (the far end as the subject)
        launch(YOU, SUB, "1,0,2");
        offline();
        click(describedFrom("Continue"));
        waitForAny(" behind ", "Never");
        View see = seeItOrNull();
        if (see != null) {
            click(see);
            act.onBackPressed();
            idle();
            click(byDescription("Save this shot"));
            View b = walk(root(), "Build the shot: another tool, with this subject", false, true);
            if (b == null) b = describedFrom(root(), "Build the shot");
            assertNotNull("Line up a street's Your shot offers Build the shot", b);
        }
        goHome();
        // Saved: each shot opens in a step bar of its own, and Back goes to Saved
        click(byDescription("Saved"));
        List<View> opens = new ArrayList<>();
        collectDescribed(root(), "Open ", opens);
        assertFalse("shots were saved", opens.isEmpty());
        int n = opens.size();
        for (int i = 0; i < n; i++) {
            List<View> now = new ArrayList<>();
            collectDescribed(root(), "Open ", now);
            View o = now.get(i);
            String name = o.getContentDescription().toString();
            click(o);
            assertNotNull(name + ": opens a step", describedFrom(root(), "Step "));
            act.onBackPressed();
            idle();
            assertNotNull(name + ": Back returns to Saved", ofType(root(), Trip.class));
        }
        assertBackLeaves("saved");
    }

    // ---------------------------------------------------------------- the crash log

    /**
     * C1: CrashLog.install runs in every onCreate and wraps the handler before it, so each restart of the screen (dark
     * mode, theme, accent, design) adds a layer: a crash is then written once per layer, through that many nested calls.
     */
    @Test public void crashHandlerInstalledOnce() throws Exception {
        final int[] depth = {0};
        Thread.setDefaultUncaughtExceptionHandler((t, x) -> {
            int n = 0;
            for (StackTraceElement s : new Throwable().getStackTrace()) if (s.getClassName().contains("CrashLog")) n++;
            depth[0] = n;
        });
        launch(YOU, SUB, "1,0,2");
        for (int i = 0; i < 5; i++) recreate();
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), new RuntimeException("probe"));
        new File(RuntimeEnvironment.getApplication().getFilesDir(), "last-crash.txt").delete();
        assertEquals("crash handler layers after 6 screens", 1, depth[0]);
    }

    /** A test that shows a bug in docs/sweep-3.24.2.md: it runs only with SWEEP_KNOWN_BUGS=1. Remove the call once fixed. */
    private static void knownBug(String id) {
        org.junit.Assume.assumeTrue("known bug " + id + ": set SWEEP_KNOWN_BUGS=1 to run", System.getenv("SWEEP_KNOWN_BUGS") != null);
    }

    // ---------------------------------------------------------------- helpers

    private void closeWhatIsIt() {
        android.app.Dialog d = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        if (d != null && d.isShowing()) {
            d.cancel();
            idle();
        }
    }

    private void offline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(cm).setActiveNetworkInfo(null);
    }

    private void pick(String label, String value) {
        View d = describedFrom(root(), label + ": ");
        assertNotNull("no dropdown " + label + " (on " + stepBar() + ")", d);
        if (d.getContentDescription().toString().equals(label + ": " + value)) return;
        click(d);
        View v = walk(root(), value, false, true);
        if (v == null) {      // 3.25: above 6° the two sides are "Afternoon, going down" / "Morning, going up" (the moon: going down / up)
            boolean up = value.equals("Sunrise") || value.equals("Moonrise");
            v = byDescription(value.startsWith("Moon") ? (up ? "Moon going up" : "Moon going down")
                    : (up ? "Morning, going up" : "Afternoon, going down"));
        }
        click(v);
    }

    private void slide(SeekBar sb, int to) {
        try {
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
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        idle();
    }

    private void sheetTo(int stop) {
        Sheet sh = (Sheet) ofType(root(), Sheet.class);
        if (sh == null) return;
        sh.setStop(stop, false);
        idle();
    }

    @SuppressWarnings("unchecked")
    private static void fillWater(double lat, double lon, int around) throws Exception {
        java.lang.reflect.Field f = Ground.class.getDeclaredField("TILES");
        f.setAccessible(true);
        java.util.Map<String, android.graphics.Bitmap> tiles = (java.util.Map<String, android.graphics.Bitmap>) f.get(null);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFFAAD3DF);
        double n = Math.pow(2, 15);
        int tx = (int) Math.floor((lon + 180) / 360 * n);
        double r = Math.toRadians(lat);
        int ty = (int) Math.floor((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * n);
        synchronized (tiles) {
            for (int dx = -around; dx <= around; dx++) for (int dy = -around; dy <= around; dy++) tiles.put((tx + dx) + "/" + (ty + dy), b);
        }
    }

    private String firstTitle() {
        Tutorial t = (Tutorial) ofType(root(), Tutorial.class);
        for (TextView tv : texts(t)) if (tv.getText().length() > 0 && tv.getTextSize() > 40) return tv.getText().toString();
        return "?";
    }

    private static int count(View v, Class<?> type) {
        int n = type.isInstance(v) && v.getVisibility() == View.VISIBLE ? 1 : 0;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) n += count(((ViewGroup) v).getChildAt(i), type);
        return n;
    }

    private static void collectDescribed(View v, String start, List<View> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        CharSequence d = v.getContentDescription();
        if (d != null && d.toString().startsWith(start)) out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectDescribed(((ViewGroup) v).getChildAt(i), start, out);
    }

    private String stepBar() {
        View v = describedFrom(root(), "Step ");
        return v == null ? "(no step bar)" : v.getContentDescription().toString();
    }

    private void assertStep(String prefix) {
        assertTrue("expected " + prefix + ", on " + stepBar(), stepBar().startsWith(prefix));
    }

    private boolean onHome() {
        return ofType(root(), HomeScreen.class) != null && ofType(root(), Trip.class) == null
                && ofType(root(), SettingsSheet.class) == null && ofType(root(), Tutorial.class) == null;
    }

    private void assertBackLeaves(String where) {
        for (int i = 0; i < 14 && !onHome(); i++) {
            act.onBackPressed();
            idle();
            assertFalse(where + ": the app left before reaching Home", act.isFinishing());
        }
        assertTrue(where + ": Back never reached Home (on " + stepBar() + ")", onHome());
        act.onBackPressed();
        idle();
        assertTrue(where + ": Back on Home did not leave", act.isFinishing());
    }

    private void goHome() {
        for (int i = 0; i < 12 && !onHome(); i++) {
            act.onBackPressed();
            idle();
        }
        assertTrue("never reached Home (on " + stepBar() + ")", onHome());
    }

    private View seeItOrNull() {
        View v = findOrNull("See it in the viewfinder");
        return v != null ? v : walk(root(), "See the soonest in the viewfinder", false, true);
    }

    private void waitUntil(BooleanSupplier ok, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!ok.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(50);
            idle();
        }
    }

    private void waitFor(String text) throws InterruptedException {
        waitUntil(() -> findOrNull(text) != null, 40000);
        assertNotNull("never showed: " + text + " (on " + stepBar() + ")", findOrNull(text));
    }

    private void waitForAny(String... any) throws InterruptedException {
        waitUntil(() -> { for (String s : any) if (walk(root(), s, true, false) != null) return true; return false; }, 30000);
    }

    private SharedPreferences prefs() { return act.getSharedPreferences("sunlighn", Context.MODE_PRIVATE); }

    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }

    private void click(View v) {
        assertNotNull(v);
        v.performClick();
        idle();
    }

    private View describedFrom(String start) {
        View v = describedFrom(root(), start);
        assertNotNull("no view described from: " + start + " (on " + stepBar() + ")", v);
        return v;
    }

    private static View describedFrom(View v, String start) {
        if (v.getVisibility() != View.VISIBLE) return null;
        CharSequence d = v.getContentDescription();
        if (d != null && d.toString().startsWith(start)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View r = describedFrom(g.getChildAt(i), start);
                if (r != null) return r;
            }
        }
        return null;
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

    private View find(String text) {
        View v = findOrNull(text);
        assertNotNull("not on screen: " + text + " (on " + stepBar() + ")", v);
        return v;
    }

    private View findOrNull(String text) { return walk(root(), text, false, false); }

    private View byDescription(String d) {
        View v = walk(root(), d, false, true);
        assertNotNull("no view described as: " + d + " (on " + stepBar() + ")", v);
        return v;
    }

    private List<TextView> texts(View v) {
        List<TextView> out = new ArrayList<>();
        if (v == null || v.getVisibility() != View.VISIBLE) return out;
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) out.addAll(texts(((ViewGroup) v).getChildAt(i)));
        return out;
    }

    private static View walk(View v, String s, boolean prefix, boolean description) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (description) {
            CharSequence d = v.getContentDescription();
            if (d != null && d.toString().equals(s)) return v;
        } else if (v instanceof TextView) {
            String t = ((TextView) v).getText().toString();
            if (prefix ? t.contains(s) : t.equals(s)) return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View r = walk(g.getChildAt(i), s, prefix, description);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void shot(String name) {
        try {
            Reveal.finishAll();
            idle();
            View r = root();
            r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
            r.layout(0, 0, 1080, 2340);
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888);
            r.draw(new android.graphics.Canvas(b));
            File dir = new File("build/sweep324");
            dir.mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, name + ".png"))) {
                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
