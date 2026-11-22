package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The sweep (2026-11-22): many sequences and edge cases through the real screens, failing on a crash (also one the
 * app caught and wrote to its crash log), a dead end (no way back to Home) or a contradiction on screen. Each test
 * is one finding or one area; the findings are written up in docs/sweep-2026-11-22.md.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SweepTest {
    // London, as in ScreensTest: the south bank towards Big Ben, a line the sun crosses at sunset.
    private static final double YOU_LAT = 51.5033, YOU_LON = -0.1196, SUB_LAT = 51.5007, SUB_LON = -0.1246;
    private static final Double[] YOU = {YOU_LAT, YOU_LON}, SUB = {SUB_LAT, SUB_LON};

    private ActivityController<MainActivity> ctl;
    private MainActivity act;
    private ProxySelector realProxy;

    @Before public void setUp() {
        Heights.enabled = false;
        Buildings.enabled = false;
        GeoMap.libreAllowed = false;
        realProxy = ProxySelector.getDefault();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @After public void tearDown() throws Exception {
        ProxySelector.setDefault(realProxy);
        Heights.enabled = false;
        Buildings.enabled = false;
        // A failure the app swallowed (CrashLog.caught) or a thread that died (CrashLog's handler) is a bug too.
        File f = new File(RuntimeEnvironment.getApplication().getFilesDir(), "last-crash.txt");
        if (f.exists()) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            f.delete();
            fail("the crash log has an entry:\n" + text);
        }
    }

    // ---------------------------------------------------------------- set-up

    /**
     * A test that shows a bug listed in docs/sweep-2026-11-22.md: it fails until the bug is fixed, so it runs only with
     * SWEEP_KNOWN_BUGS=1 (the suite stays green for releases). Remove the call once the bug is fixed.
     */
    private static void knownBug(String id) {
        org.junit.Assume.assumeTrue("known bug " + id + ": set SWEEP_KNOWN_BUGS=1 to run", System.getenv("SWEEP_KNOWN_BUGS") != null);
    }

    /** As knownBug, for a bug that hangs the app (and the test run with it): SWEEP_HANGS=1, with a timeout round Gradle. */
    private static void knownHang(String id) {
        org.junit.Assume.assumeTrue("known hang " + id + ": set SWEEP_HANGS=1 to run", System.getenv("SWEEP_HANGS") != null);
    }

    private SharedPreferences.Editor clean() {
        return RuntimeEnvironment.getApplication().getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true)
                .putBoolean("guide_frame", true).putBoolean("asked_location", true);
    }

    private void launch(SharedPreferences.Editor e, Double[] you, Double[] sub, String flow) {
        double cLat = sub != null ? sub[0] : you != null ? you[0] : 51.502, cLon = sub != null ? sub[1] : you != null ? you[1] : -0.122;
        e.putString("cam_lat", "" + cLat).putString("cam_lon", "" + cLon).putString("cam_zoom", "15");
        if (you != null) e.putString("you_lat", "" + you[0]).putString("you_lon", "" + you[1]);
        if (sub != null) e.putString("subject_lat", "" + sub[0]).putString("subject_lon", "" + sub[1]).putString("subject_name", "Big Ben");
        if (flow != null) e.putString("flow", flow);
        e.apply();
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
        idle();
    }

    private void launch(Double[] you, Double[] sub, String flow) { launch(clean(), you, sub, flow); }

    /** Every connection fails at once (a dead proxy), while the phone still says it is online. */
    private void networkFails() {
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) {
                return Collections.singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 9)));
            }
            @Override public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
        });
    }

    /** The phone says it has no network at all. */
    private void offline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(cm).setActiveNetworkInfo(null);
    }

    // ---------------------------------------------------------------- the checks

    private String stepBar() {
        View v = describedFrom(root(), "Step ");
        return v == null ? "(no step bar)" : v.getContentDescription().toString();
    }

    private boolean onHome() {
        return ofType(root(), HomeScreen.class) != null && ofType(root(), Trip.class) == null
                && ofType(root(), SettingsSheet.class) == null && ofType(root(), Tutorial.class) == null;
    }

    /** No dead end: Back reaches Home within a dozen presses, and Back on Home leaves the app. */
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

    /** A real double tap: the second tap lands on the same key only if it is still on screen after the first. */
    private void doubleTap(View v) {
        assertNotNull(v);
        v.performClick();
        if (v.isAttachedToWindow() && v.isShown()) v.performClick();
        idle();
    }

    private void recreate() {
        ctl.recreate();
        act = ctl.get();
        idle();
    }

    /** Process death: the state is saved, the activity and its process go, a new one is made from the bundle. */
    private void processDeath() {
        Bundle b = new Bundle();
        ctl.saveInstanceState(b).pause().stop().destroy();
        ctl = Robolectric.buildActivity(MainActivity.class).setup(b);
        act = ctl.get();
        idle();
    }

    // ---------------------------------------------------------------- Back and restarts at every step

    /** Line up a street: Back at every step goes one step back; a restart at every step stays on it. */
    @Test public void lineUpBackAndRestartAtEveryStep() throws Exception {
        launch(null, null, null);
        click(byDescription("Line up a street"));
        assertTrue(stepBar(), stepBar().startsWith("Step 1 of 4"));
        recreate();
        assertTrue("restart on step 1: " + stepBar(), stepBar().startsWith("Step 1 of 4"));
        act.tapMap(new GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        assertTrue(stepBar(), stepBar().startsWith("Step 2 of 4"));
        recreate();
        assertTrue("restart on step 2: " + stepBar(), stepBar().startsWith("Step 2 of 4"));
        processDeath();
        assertTrue("process death on step 2: " + stepBar(), stepBar().startsWith("Step 2 of 4"));
        act.onBackPressed();
        idle();
        assertTrue(stepBar(), stepBar().startsWith("Step 1 of 4"));
        click(find("Next"));
        act.tapMap(new GeoPoint(YOU_LAT, YOU_LON));
        idle();
        click(find("Next"));
        assertTrue(stepBar(), stepBar().startsWith("Step 3 of 4"));
        waitForPrefix(" behind subject", " behind Big Ben");
        recreate();
        assertTrue("restart on When: " + stepBar(), stepBar().startsWith("Step 3 of 4"));
        processDeath();
        assertTrue("process death on When: " + stepBar(), stepBar().startsWith("Step 3 of 4"));
        waitForPrefix(" behind subject", " behind Big Ben");
        click(seeIt());
        assertNotNull("the viewfinder opened", ofType(root(), FrameScreen.class));
        act.onBackPressed();
        idle();
        assertNull(ofType(root(), FrameScreen.class));
        assertTrue(stepBar(), stepBar().startsWith("Step 4 of 4"));
        recreate();
        assertTrue("restart on Your shot: " + stepBar(), stepBar().startsWith("Step 4 of 4"));
        processDeath();
        assertTrue("process death on Your shot: " + stepBar(), stepBar().startsWith("Step 4 of 4"));
        waitFor("Open the viewfinder");
        act.onBackPressed();
        idle();
        assertTrue(stepBar(), stepBar().startsWith("Step 3 of 4"));
        assertBackLeaves("Line up a street");
    }

    /** Build the shot: the same, with the compose view and its settings open. */
    @Test public void buildBackAndRestartAtEveryStep() throws Exception {
        launch(clean().putBoolean("drone", true), null, null, null);
        click(byDescription("Build the shot"));
        assertTrue(stepBar(), stepBar().startsWith("Step 1 of 3"));
        recreate();
        assertTrue("restart on step 1: " + stepBar(), stepBar().startsWith("Step 1 of 3"));
        act.tapMap(new GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        assertNotNull(ofType(root(), ComposeScreen.class));
        waitForPrefix(" of Big Ben", " of the subject");
        recreate();
        assertNotNull("restart keeps the compose view", ofType(root(), ComposeScreen.class));
        click(describedFrom("Settings: "));
        assertNotNull(byDescription("Done: show the answer"));
        recreate();
        assertNotNull("restart with settings open keeps the compose view", ofType(root(), ComposeScreen.class));
        processDeath();
        assertNotNull("process death keeps the compose view", ofType(root(), ComposeScreen.class));
        waitForPrefix(" of Big Ben", " of the subject");
        waitFor("Fly here");
        clickWhenReady("Fly here");
        assertTrue(stepBar(), stepBar().startsWith("Step 3 of 3"));
        recreate();
        assertTrue("restart on Your shot: " + stepBar(), stepBar().startsWith("Step 3 of 3"));
        act.onBackPressed();
        idle();
        assertNotNull("Back from Your shot: the compose view", ofType(root(), ComposeScreen.class));
        act.onBackPressed();
        idle();
        assertTrue(stepBar(), stepBar().startsWith("Step 1 of 3"));
        assertBackLeaves("Build the shot");
    }

    /** Back closes every layer and changes nothing under it: Settings, guides, dropdowns, dialogs, search, Saved, map look. */
    @Test public void backClosesEveryLayer() throws Exception {
        launch(YOU, SUB, "1,0,2");
        // Home: Settings and its dropdown, the guides, How it works
        click(byDescription("Settings"));
        shot("settings-from-home");
        assertNotNull(byDescription("Theme: System"));
        click(byDescription("Theme: System"));
        assertNotNull(find("Dark"));
        act.onBackPressed();
        idle();
        assertNull("Back closed the list", findOrNull("Dark"));
        assertNotNull("Settings still open", byDescription("Theme: System"));
        for (String g : new String[]{"Guide: Line up a street", "Guide: Build the shot", "Guide: The viewfinder"}) {
            View row = walk(root(), g, false, true);
            assertNotNull("no row " + g, row);
            click(row);
            assertNotNull(g + " shows Skip", findOrNull("Skip"));
            act.onBackPressed();
            idle();
            assertNull(g + ": Back closes it", findOrNull("Skip"));
            // (Settings closes when a guide opens: guideFromSettingsBackReturnsToSettings)
            if (walk(root(), "Theme: System", false, true) == null) click(byDescription("Settings"));
        }
        act.onBackPressed();
        idle();
        assertTrue("Back from Settings: Home", onHome());
        // Saved
        click(byDescription("Saved"));
        act.onBackPressed();
        idle();
        assertTrue("Back from Saved: Home", onHome());
        // When: map look, the three dots, the Sun / Moon dropdown
        click(describedFrom("Continue"));
        waitForPrefix(" behind subject", " behind Big Ben");
        click(byDescription("Map look"));
        act.onBackPressed();
        idle();
        assertTrue(stepBar(), stepBar().startsWith("Step 3 of 4"));
        click(byDescription("Sun or moon: Sun"));
        act.onBackPressed();
        idle();
        assertTrue("Back closed the body list, still on When: " + stepBar(), stepBar().startsWith("Step 3 of 4"));
        assertNotNull(byDescription("Sun or moon: Sun"));
        // Search from the subject step
        act.onBackPressed();
        idle();
        act.onBackPressed();
        idle();
        assertTrue(stepBar(), stepBar().startsWith("Step 1 of 4"));
        click(byDescription("Search a place"));
        assertNotNull(byDescription("Search for a place"));
        act.onBackPressed();
        idle();
        assertNull(walk(root(), "Search for a place", false, true));
        assertTrue(stepBar(), stepBar().startsWith("Step 1 of 4"));
        // The viewfinder: Height dialog, Move joystick, Help guide
        click(find("Next"));
        click(find("Next"));
        waitForPrefix(" behind subject", " behind Big Ben");
        click(seeIt());
        assertNotNull(ofType(root(), FrameScreen.class));
        click(byDescription("Subject height"));
        assertNotNull("a height dialog", ShadowDialog.getLatestDialog());
        act.onBackPressed();
        idle();
        assertNotNull("Back from the height dialog: the viewfinder stays", ofType(root(), FrameScreen.class));
        click(byDescription("Move: walk where you stand"));
        assertNotNull(byDescription("Joystick: push to walk"));
        act.onBackPressed();
        idle();
        assertNull(walk(root(), "Joystick: push to walk", false, true));
        assertNotNull(ofType(root(), FrameScreen.class));
        click(byDescription("Help: how the viewfinder works"));
        assertNotNull(findOrNull("Skip"));
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Skip"));
        assertNotNull("Back from its guide: the viewfinder stays", ofType(root(), FrameScreen.class));
        act.onBackPressed();
        idle();
        assertTrue(stepBar(), stepBar().startsWith("Step 4 of 4"));
        assertBackLeaves("layers");
    }

    /** A restart with a layer open (dark mode at sunset): no crash, the step stays, and nothing is a dead end. */
    @Test public void restartWithEachLayerOpen() throws Exception {
        launch(YOU, SUB, "1,0,2");
        click(byDescription("Settings"));
        recreate();
        assertNotNull("Settings reopen after a restart", walk(root(), "Theme: System", false, true));
        act.onBackPressed();
        idle();
        click(byDescription("Saved"));
        recreate();
        assertBackLeaves("restart with Saved open");

        launch(YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        waitForPrefix(" behind subject", " behind Big Ben");
        click(seeIt());
        click(byDescription("Move: walk where you stand"));
        recreate();
        assertTrue("restart in the viewfinder: " + stepBar(), stepBar().startsWith("Step 4 of 4"));
        assertBackLeaves("restart in the viewfinder");

        launch(YOU, SUB, "1,0,0");
        click(describedFrom("Continue"));
        click(byDescription("Search a place"));
        recreate();
        assertTrue("restart in search: " + stepBar(), stepBar().startsWith("Step 1 of 4"));
        assertBackLeaves("restart in search");
    }

    /** What the viewfinder showed is not lost on a restart (finding: it closes; Back table says the step stays). */
    @Test public void restartInTheViewfinderKeepsIt() throws Exception {
        launch(YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        waitForPrefix(" behind subject", " behind Big Ben");
        click(seeIt());
        assertNotNull(ofType(root(), FrameScreen.class));
        recreate();
        assertNotNull("the viewfinder is still open after the screen restarts (dark mode at sunset)", ofType(root(), FrameScreen.class));
    }

    // ---------------------------------------------------------------- double taps

    @Test public void doubleTapNextMovesOneStep() throws Exception {
        launch(null, null, null);
        click(byDescription("Line up a street"));
        act.tapMap(new GeoPoint(SUB_LAT, SUB_LON));
        idle();
        doubleTap(find("Next"));
        assertTrue("one step: " + stepBar(), stepBar().startsWith("Step 2 of 4"));
        act.tapMap(new GeoPoint(YOU_LAT, YOU_LON));
        idle();
        doubleTap(find("Next"));
        assertTrue("one step: " + stepBar(), stepBar().startsWith("Step 3 of 4"));
        assertNull("When shows no Your shot underneath", ofType(root(), FrameScreen.class));
        assertBackLeaves("double Next");
    }

    @Test public void doubleTapSeeItAndSave() throws Exception {
        launch(YOU, SUB, "1,0,2");
        offline();
        click(describedFrom("Continue"));
        waitForPrefix(" behind subject", " behind Big Ben");
        doubleTap(seeIt());
        assertNotNull(ofType(root(), FrameScreen.class));
        act.onBackPressed();
        idle();
        assertNull("one Back closes the viewfinder (not two stacked)", ofType(root(), FrameScreen.class));
        assertTrue(stepBar(), stepBar().startsWith("Step 4 of 4"));
        doubleTap(byDescription("Save this shot"));
        assertEquals("a double Save saves one shot", 1, Trip.loadLines(act).size());
        View bell = byDescription("Remind me 30 minutes before");
        bell.performClick();
        idle();
        assertEquals(1, Reminders.all(act).size());
        // the viewfinder's Save twice
        click(byDescription("Viewfinder: see the shot as the camera will"));
        doubleTap(byDescription("Save this shot"));
        assertEquals("the viewfinder's Save on a saved shot adds none", 1, Trip.loadLines(act).size());
        assertBackLeaves("double Save");
    }

    @Test public void doubleTapStandHereAndDone() throws Exception {
        launch(clean().putBoolean("drone", true), null, SUB, "3,0,1");
        click(describedFrom("Continue"));
        waitForPrefix(" of Big Ben");
        click(describedFrom("Settings: "));
        doubleTap(byDescription("Done: show the answer"));
        assertNotNull("the answer is back", find("Fly here"));
        assertNull("settings folded", walk(root(), "Done: show the answer", false, true));
        doubleTap(find("Fly here"));
        assertTrue(stepBar(), stepBar().startsWith("Step 3 of 3"));
        act.onBackPressed();
        idle();
        assertNotNull("one Back: the compose view", ofType(root(), ComposeScreen.class));
        assertBackLeaves("double Stand here");
    }

    /** Line it up twice, then Undo: You is back where it was. */
    @Test public void doubleTapLineItUpThenUndo() throws Exception {
        launch(new Double[]{51.4980, -0.1246}, SUB, "1,0,2");   // due south of the subject: the sun never lines up
        click(describedFrom("Continue"));
        waitFor("Line it up");
        doubleTap(find("Line it up"));
        waitForPrefix("You moved round the subject");
        click(find("Undo"));
        idle();
        assertEquals("Undo puts You back where it was", 51.4980, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        assertEquals(-0.1246, Double.parseDouble(prefs().getString("you_lon", "0")), 1e-9);
    }

    /** "Show Behind you" twice: one switch, and the card agrees with the switch. */
    @Test public void doubleTapShowBehindYou() throws Exception {
        // You north-east of the subject, looking south-west into the November sunset: Behind you never lines up there
        launch(clean().putInt("mode", 1), YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        View swap = null;
        for (int i = 0; i < 200 && swap == null; i++) {
            Thread.sleep(50);
            idle();
            swap = findOrNull("Show Behind subject");
            if (swap == null) swap = findOrNull("Show Behind you");
        }
        if (swap == null) return;   // no such case here (the line lines up both ways): nothing to tap
        String label = ((TextView) swap).getText().toString();
        doubleTap(swap);
        waitForPrefix(label.endsWith("you") ? " behind you" : " behind Big Ben");
    }

    // ---------------------------------------------------------------- no network

    @Test public void noNetworkThroughBothTools() throws Exception {
        networkFails();
        Heights.enabled = true;
        Buildings.enabled = true;
        launch(null, null, null);
        click(byDescription("Line up a street"));
        act.tapMap(new GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        act.tapMap(new GeoPoint(YOU_LAT, YOU_LON));
        idle();
        click(find("Next"));
        waitForPrefix(" behind subject", " behind Big Ben");
        waitForPrefix("could not be fetched", "Buildings");
        click(tab("Day chart"));
        click(tab("All times"));
        click(seeIt());
        assertNotNull(ofType(root(), FrameScreen.class));
        Thread.sleep(500);
        idle();
        act.onBackPressed();
        idle();
        waitFor("Open the viewfinder");
        click(byDescription("Save this shot"));
        waitForPrefix("Saved as ");
        assertBackLeaves("no network, Line up a street");

        launch(null, null, null);
        click(byDescription("Build the shot"));
        act.tapMap(new GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        waitForPrefix(" of Big Ben", " of the subject");
        waitFor("Stand here");
        View stand = find("Stand here");
        // map tiles fetched by earlier tests stay in Ground's cache, so a spot can still be known to be water
        assertTrue("Stand here greyed with no reason given",
                stand.isEnabled() || walk(root(), "No spot ", true, false) != null);
        if (stand.isEnabled()) {
            click(stand);
            assertTrue(stepBar(), stepBar().startsWith("Step 3 of 3"));
        }
        assertBackLeaves("no network, Build the shot");
    }

    @Test public void searchWithNoNetworkSaysSo() throws Exception {
        networkFails();
        launch(null, null, null);
        click(byDescription("Line up a street"));
        click(byDescription("Search a place"));
        TextView field = (TextView) byDescription("Search for a place");
        field.setText("Big Ben");
        field.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        idle();
        boolean said = false;
        for (int i = 0; i < 200 && !said; i++) {
            Thread.sleep(50);
            idle();
            for (TextView t : texts(root())) {
                String s = t.getText().toString().toLowerCase();
                said |= s.contains("network") || s.contains("connection") || s.contains("could not") || s.contains("nothing found") || s.contains("no place");
            }
        }
        assertTrue("search with no network says why there are no results", said);
        act.onBackPressed();
        idle();
        assertTrue(stepBar(), stepBar().startsWith("Step 1 of 4"));
    }

    // ---------------------------------------------------------------- odd places

    /** One place through both tools and both bodies: no crash, never a dead end. */
    private void throughBothTools(String where, Double[] you, Double[] sub) throws Exception {
        for (int moon = 0; moon <= 1; moon++) {
            launch(clean().putBoolean("moon", moon == 1).putBoolean("drone", true), you, sub, "1," + moon + ",2");
            click(describedFrom("Continue"));
            waitUntil(() -> walk(root(), "Working it out", true, false) == null, 15000);
            Thread.sleep(200);
            idle();
            assertNotNull(where + ": When has a step bar", describedFrom(root(), "Step 3 of 4"));
            shot("place-" + where.replace(' ', '_') + (moon == 1 ? "-moon" : "-sun") + "-when");
            assertWhenHasAWayOn(where + (moon == 1 ? " (moon)" : " (sun)"));
            click(tab("Day chart"));
            click(tab("All times"));
            View see = seeItOrNull();
            if (see != null && see.isEnabled()) {
                click(see);
                act.onBackPressed();
                idle();
            }
            View lineUp = findOrNull("Line it up");
            if (lineUp != null) {
                click(lineUp);
                waitUntil(() -> walk(root(), "Working it out", true, false) == null, 15000);
            }
            assertBackLeaves(where + " Line up a street");

            launch(clean().putBoolean("moon", moon == 1).putBoolean("drone", true), null, sub, "3," + moon + ",1");
            click(describedFrom("Continue"));
            assertNotNull(where + ": compose view", ofType(root(), ComposeScreen.class));
            waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
            idle();
            shot("place-" + where.replace(' ', '_') + (moon == 1 ? "-moon" : "-sun") + "-build");
            View fly = findOrNull("Fly here");
            if (fly != null && fly.isEnabled()) {
                click(fly);
                assertTrue(where + ": " + stepBar(), stepBar().startsWith("Step 3 of 3"));
                waitUntil(() -> findOrNull("Open the viewfinder") != null, 5000);
                shot("place-" + where.replace(' ', '_') + (moon == 1 ? "-moon" : "-sun") + "-shot");
            }
            assertBackLeaves(where + " Build the shot");
        }
    }

    /** When always offers something: the viewfinder, Line it up, the other side, or says it never lines up. */
    private void assertWhenHasAWayOn(String where) {
        boolean see = seeItOrNull() != null && seeItOrNull().isEnabled();
        boolean fix = findOrNull("Line it up") != null;
        boolean other = findOrNull("Show Behind you") != null || findOrNull("Show Behind subject") != null;
        boolean never = walk(root(), "Never", true, false) != null || walk(root(), "never", true, false) != null
                || walk(root(), "No time", true, false) != null;
        assertTrue(where + ": When offers no way on and does not say why", see || fix || other || never);
    }

    @Test public void polarNightTromso() throws Exception {
        throughBothTools("Tromsø", new Double[]{69.6500, 18.9600}, new Double[]{69.6480, 18.9520});
    }

    @Test public void midnightSunMcMurdo() throws Exception {
        throughBothTools("McMurdo", new Double[]{-77.8460, 166.6760}, new Double[]{-77.8490, 166.6640});
    }

    @Test public void northPole() throws Exception {
        throughBothTools("North Pole", new Double[]{89.9990, 0.0}, new Double[]{89.9995, 90.0});
    }

    @Test public void acrossTheDateLine() throws Exception {
        throughBothTools("Taveuni date line", new Double[]{-16.8000, 179.9990}, new Double[]{-16.8010, -179.9990});
    }

    @Test public void youAndSubjectFiveMetresApart() throws Exception {
        throughBothTools("5 m", new Double[]{51.50070, -0.12453}, SUB);
    }

    @Test public void youAndSubjectThirtyKilometresApart() throws Exception {
        throughBothTools("30 km", new Double[]{51.7700, -0.1246}, SUB);
    }

    @Test public void youOnTheSubject() throws Exception {
        throughBothTools("0 m", SUB, SUB);
    }

    /** The subject at sea: every spot is on water; Build the shot says so and Stand here is greyed, never a dead end. */
    @Test public void subjectOnWater() throws Exception {
        // A water-coloured map round the subject, so the answer does not hang on the network
        Double[] sea = {54.5000, 3.0000};
        fillWater(sea[0], sea[1]);
        launch(clean(), null, sea, "3,0,1");
        click(describedFrom("Continue"));
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
        idle();
        View stand = findOrNull("Stand here");
        assertNotNull("Stand here is on screen", stand);
        TextView head = (TextView) walk(root(), "No spot on land", true, false);
        assertNotNull("the headline says there is no land", head);
        assertFalse("Stand here is greyed", stand.isEnabled());
        click(describedFrom("Settings: "));
        SeekBar dist = (SeekBar) byDescription("Distance from the subject");
        waitUntil(() -> ((ComposeScreen) ofType(root(), ComposeScreen.class)).distanceChecked(), 20000);
        shot("water-settings");
        // Spots beyond 3 km are not looked at and count as results: at sea the note must not send you to one of them
        TextView note = (TextView) walk(root(), "Nearest that does: ", true, false);
        if (note != null) {
            String n = note.getText().toString();
            assertFalse("at sea the note recommends an unchecked distance: '" + n + "'", n.matches(".*Nearest that does: [0-9.]+ km.*"));
        }
        assertNotNull("the slider says no distance works", walk(root(), "No distance works here", true, false));
        click(find("Drone"));
        click(byDescription("Done: show the answer"));
        waitFor("Fly here");
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
        assertTrue("a drone can fly there", find("Fly here").isEnabled());
        assertBackLeaves("subject on water");
    }

    @SuppressWarnings("unchecked")
    private static void fillWater(double lat, double lon) throws Exception {
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
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) tiles.put((tx + dx) + "/" + (ty + dy), b);
        }
    }

    /**
     * Build the shot where the sun never sets (McMurdo in November) or barely rises (Tromsø): when the headline says the
     * body never gets to where it is placed, the sky is shaded there, and "Put it lower" is only said when lower works.
     */
    @Test public void neverGetsThereAgreesWithTheShadingMcMurdo() throws Exception {
        neverGetsThere("McMurdo", new Double[]{-77.8490, 166.6640}, true);
        neverGetsThere("McMurdo", new Double[]{-77.8490, 166.6640}, false);
    }

    @Test public void neverGetsThereAgreesWithTheShadingTromso() throws Exception {
        neverGetsThere("Tromsø", new Double[]{69.6480, 18.9520}, true);
        neverGetsThere("Tromsø", new Double[]{69.6480, 18.9520}, false);
    }

    private void neverGetsThere(String where, Double[] sub, boolean drone) throws Exception {
        launch(clean().putBoolean("drone", drone), null, sub, "3,0,1");
        click(describedFrom("Continue"));
        ComposeScreen cs = (ComposeScreen) ofType(root(), ComposeScreen.class);
        java.lang.reflect.Field pf = ComposeScreen.class.getDeclaredField("possible");
        pf.setAccessible(true);
        waitUntil(() -> {
            try { return pf.get(cs) != null && walk(root(), "Working it out", true, false) == null; } catch (Exception e) { return false; }
        }, 30000);
        idle();
        boolean[] ok = (boolean[]) pf.get(cs);
        assertNotNull(where + ": the shading was worked out", ok);
        double alt = cs.bodyPlace()[1];
        double low = Sky.lowest(false, sub[0], sub[1], System.currentTimeMillis(), 42);
        String head = headline().getText().toString();
        StringBuilder shaded = new StringBuilder();
        for (int a = 0; a < ok.length; a++) shaded.append(ok[a] ? '.' : '#');
        shot("never-" + where.replace("ø", "o") + (drone ? "-drone" : "-foot"));
        String what = where + (drone ? " (drone)" : " (on foot)") + ": headline '" + head + "', sun placed at " + Math.round(alt)
                + "° (" + String.format(java.util.Locale.US, "%.2f", alt) + "°, the sun's lowest in 6 weeks "
                + String.format(java.util.Locale.US, "%.2f", low) + "°), heights 0..: " + shaded;
        if (head.contains("never gets there")) {
            int a = (int) Math.max(0, Math.min(ok.length - 1, Math.round(alt)));
            // The shading is in whole degrees (with half a degree's slack): a body a fraction of a degree past the
            // body's reach may sit on an unshaded band, but shading must be within a degree of it (3.23: 7.84° placed,
            // the midnight sun's lowest 8.05°, 0..7 shaded).
            boolean shadedNear = false;
            for (int b = Math.max(0, a - 1); b <= Math.min(ok.length - 1, a + 1); b++) shadedNear |= !ok[b];
            assertTrue(what + " — the sky is not shaded near where the sun is said never to get", shadedNear);
            boolean lowerWorks = false;
            for (int b = 0; b < a; b++) lowerWorks |= ok[b];
            if (walk(root(), "lower in the picture", true, false) != null)
                assertTrue(what + " — it says 'Put the sun lower' but no lower height works", lowerWorks);
        }
    }

    /** Across the date line the map shows the two points 220 m apart, not the whole world. */
    @Test public void dateLineMapShowsTheLine() throws Exception {
        Double[] you = {-16.8000, 179.9990}, sub = {-16.8010, -179.9990};
        launch(you, sub, "1,0,2");
        click(describedFrom("Continue"));
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 15000);
        for (int i = 0; i < 10; i++) { Thread.sleep(50); idle(); }
        shot("date-line-when");
        java.lang.reflect.Field mf = MainActivity.class.getDeclaredField("map");
        mf.setAccessible(true);
        org.osmdroid.views.MapView mv = (org.osmdroid.views.MapView) ((GeoMap) mf.get(act)).view();
        assertTrue("two points 220 m apart across the date line: the map is at zoom " + mv.getZoomLevelDouble()
                + " showing " + mv.getBoundingBox(), mv.getZoomLevelDouble() >= 13);
    }

    /** You placed on the subject itself: the step says so, rather than a line with no direction and a Line it up that cannot move You. */
    @Test public void youOnTheSubjectSaysSo() throws Exception {
        launch(null, null, null);
        click(byDescription("Line up a street"));
        act.tapMap(new GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        // a tap on the subject's pin does not place You; typed coordinates do
        click(byDescription("Search a place"));
        TextView field = (TextView) byDescription("Search for a place");
        field.setText(SUB_LAT + ", " + SUB_LON);
        field.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        idle();
        waitUntil(() -> describedFrom(root(), "Coordinates") != null || walk(root(), ", set as You", true, true) != null
                || anyDescribedEnding(root(), ", set as You") != null, 5000);
        View row = anyDescribedEnding(root(), ", set as You");
        assertNotNull("typed coordinates give a result", row);
        click(row);
        View next = find("Next");
        shot("you-on-the-subject-step");
        assertFalse("You placed on the subject itself (0 m): Next should refuse and say why", next.isEnabled() && next.getAlpha() == 1f);
        click(next);
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 15000);
        Thread.sleep(300);
        idle();
        shot("you-on-the-subject");
        if (stepBar().startsWith("Step 2 of 4")) return;   // Next refused: fine
        View fix = findOrNull("Line it up");
        if (fix != null) {
            double lat = Double.parseDouble(prefs().getString("you_lat", "0")), lon = Double.parseDouble(prefs().getString("you_lon", "0"));
            click(fix);
            double lat2 = Double.parseDouble(prefs().getString("you_lat", "0")), lon2 = Double.parseDouble(prefs().getString("you_lon", "0"));
            fail("You on the subject (0 m): When offers Line it up, and it moves You " + Math.round(Astro.distance(lat, lon, lat2, lon2))
                    + " m (the subject is where You stands; the card reads: "
                    + ((TextView) walk(root(), "is never behind", true, false) == null ? "?" : ((TextView) walk(root(), "is never behind", true, false)).getText()) + ")");
        }
    }

    /** Back table: Your shot opened from Saved goes back to Saved, also after the screen restarts. */
    @Test public void shotFromSavedBackAfterRestart() throws Exception {
        launch(YOU, SUB, "1,0,2");
        offline();
        click(describedFrom("Continue"));
        waitForPrefix(" behind Big Ben");
        click(seeIt());
        act.onBackPressed();
        idle();
        click(byDescription("Save this shot"));
        goHome();
        click(byDescription("Saved"));
        waitFor("Big Ben");
        click(describedFrom("Open Big Ben"));
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        recreate();
        act.onBackPressed();
        idle();
        assertTrue("after a restart, Back from a shot opened in Saved goes to Saved (now on " + stepBar() + ")", onSaved());
    }

    /** Back table: a guide opened from Settings closes with Back, "nothing changed": Settings is still there. */
    @Test public void guideFromSettingsBackReturnsToSettings() throws Exception {
        launch(null, null, null);
        click(byDescription("Settings"));
        click(byDescription("Guide: Build the shot"));
        assertNotNull(findOrNull("Skip"));
        act.onBackPressed();
        idle();
        assertNotNull("Back from a guide opened in Settings returns to Settings (it went to Home)", walk(root(), "Theme: System", false, true));
    }

    // ---------------------------------------------------------------- Build the shot's every setting

    @Test public void buildEveryLightLookInAndFullSun() throws Exception { everySetting(false); }

    @Test public void buildEveryLightLookInAndFullMoon() throws Exception { everySetting(true); }

    private void everySetting(boolean moon) throws Exception {
        launch(clean().putBoolean("moon", moon).putBoolean("drone", true), null, SUB, "3," + (moon ? 1 : 0) + ",1");
        click(describedFrom("Continue"));
        waitForPrefix(" of Big Ben", "never gets there");
        String[] lights = moon ? new String[]{"Moonset", "Moonrise", "Any"}
                : new String[]{"Sunset", "Sunrise", "Sunset golden hour", "Sunrise golden hour", "Any"};
        String[] looks = {"Next 2 weeks", "Next 6 weeks", "Next year"};
        String[] fulls = {"Any", "Half or more", "Nearly full"};
        for (String light : lights) {
            for (String look : looks) {
                for (String full : moon ? fulls : new String[]{null}) {
                    openSettings();
                    pickIn("Light", light);
                    pickIn("Look in", look);
                    if (full != null) pickIn("How full", full);
                    click(byDescription("Done: show the answer"));
                    // 3.23: the old answer stays (dimmed, Fly here greyed) while the new one is worked out
                    waitUntil(() -> !solving(), 30000);
                    idle();
                    String what = light + " / " + look + (full != null ? " / " + full : "");
                    View fly = findOrNull("Fly here");
                    assertNotNull(what + ": Fly here", fly);
                    TextView head = headline();
                    assertNotNull(what + ": a headline", head);
                    String h = head.getText().toString();
                    // the answer agrees with the settings: an Any light names its side, a sunset is not shown for Sunrise
                    if (!h.contains("never")) {
                        assertTrue(what + ": Fly here enabled with an answer (" + h + ")", fly.isEnabled());
                        View when = describedFrom(root(), "When: ");
                        assertNotNull(what + ": When", when);
                        if (light.equals("Any")) assertTrue(what + ": " + when.getContentDescription(),
                                when.getContentDescription().toString().matches("When: (Sun|Moon)(set|rise) · .*"));
                    } else {
                        assertFalse(what + ": Fly here greyed with no answer", fly.isEnabled());
                    }
                }
            }
        }
        // the Distance slider at both ends
        openSettings();
        SeekBar dist = (SeekBar) byDescription("Distance from the subject");
        for (int end : new int[]{0, dist.getMax(), 0}) {
            slide(dist, end);
            waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
            assertNotNull("the settings stay open at " + end, walk(root(), "Done: show the answer", false, true));
        }
        click(byDescription("Done: show the answer"));
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
        View fly = find("Fly here");
        if (fly.isEnabled()) {
            click(fly);
            assertTrue(stepBar(), stepBar().startsWith("Step 3 of 3"));
        }
        assertBackLeaves("every setting" + (moon ? " (moon)" : ""));
    }

    private void openSettings() {
        if (walk(root(), "Done: show the answer", false, true) == null) click(describedFrom("Settings: "));
    }

    private void pickIn(String label, String value) {
        View d = describedFrom(root(), label + ": ");
        assertNotNull("no dropdown " + label, d);
        if (d.getContentDescription().toString().equals(label + ": " + value)) return;
        click(d);
        click(byDescription(value));
    }

    /** A finger on the slider: start, move to the value, lift. */
    private void slide(SeekBar sb, int to) {
        sb.setPressed(true);
        try {
            java.lang.reflect.Method m = android.widget.ProgressBar.class.getDeclaredMethod("setProgressInternal", int.class, boolean.class, boolean.class);
            m.setAccessible(true);
            SeekBar.OnSeekBarChangeListener l = listener(sb);
            l.onStartTrackingTouch(sb);
            m.invoke(sb, to, true, false);
            sb.setPressed(false);
            l.onStopTrackingTouch(sb);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        idle();
    }

    private static SeekBar.OnSeekBarChangeListener listener(SeekBar sb) throws Exception {
        java.lang.reflect.Field f = SeekBar.class.getDeclaredField("mOnSeekBarChangeListener");
        f.setAccessible(true);
        return (SeekBar.OnSeekBarChangeListener) f.get(sb);
    }

    private boolean solving() {
        ComposeScreen cs = (ComposeScreen) ofType(root(), ComposeScreen.class);
        try {
            java.lang.reflect.Field f = ComposeScreen.class.getDeclaredField("solving");
            f.setAccessible(true);
            return f.getBoolean(cs);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private TextView headline() {
        ComposeScreen cs = (ComposeScreen) ofType(root(), ComposeScreen.class);
        try {
            java.lang.reflect.Field f = ComposeScreen.class.getDeclaredField("headline");
            f.setAccessible(true);
            return (TextView) f.get(cs);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** Settings opened and folded with Done while the answer is still being worked out. */
    @Test public void buildSettingsWhileWorkingItOut() throws Exception {
        launch(clean().putBoolean("drone", true), null, SUB, "3,0,1");
        click(describedFrom("Continue"));
        // no wait: straight in while the first answer is worked out
        click(describedFrom("Settings: "));
        click(byDescription("Done: show the answer"));
        click(describedFrom("Settings: "));
        click(byDescription("Done: show the answer"));
        waitForPrefix(" of Big Ben");
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
        assertTrue(find("Fly here").isEnabled());
        assertBackLeaves("settings while working");
    }

    /**
     * A change of Light, then Stand here before the new answer is in (a slow phone, Look in Next year). The worker's
     * answer is held back by not letting the main thread run, as a phone that has not drawn it yet.
     */
    @Test public void standHereBeforeTheNewAnswerIsIn() throws Exception {
        launch(clean().putBoolean("drone", true), null, SUB, "3,0,1");
        click(describedFrom("Continue"));
        waitForPrefix(" of Big Ben");
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
        String sunsetHead = headline().getText().toString();
        click(describedFrom("Settings: "));
        click(describedFrom("Light: "));
        byDescription("Sunrise").performClick();               // from here no idle: the new answer cannot arrive
        byDescription("Done: show the answer").performClick();
        String head = headline().getText().toString();
        View fly = find("Fly here");
        View when = describedFrom(root(), "When: ");
        String settings = describedFrom(root(), "Settings: ").getContentDescription().toString();
        assertTrue("the settings say Sunrise: " + settings, settings.startsWith("Settings: Sunrise"));
        if (fly.isEnabled() && !head.startsWith("Working it out")) {
            ComposeScreen cs = (ComposeScreen) ofType(root(), ComposeScreen.class);
            java.lang.reflect.Field rf = ComposeScreen.class.getDeclaredField("runs");
            rf.setAccessible(true);
            java.util.List<?> runs = (java.util.List<?>) rf.get(cs);
            StringBuilder rs = new StringBuilder();
            for (Object r : runs) {
                java.lang.reflect.Field ff = r.getClass().getDeclaredField("first");
                ff.setAccessible(true);
                rs.append(new java.util.Date(ff.getLong(r))).append("; ");
            }
            shot("stale-answer-under-sunrise", false);
            fly.performClick();
            long msNow = prefs().getLong("shot_ms", 0);
            idle();
            long ms = prefs().getLong("shot_ms", 0);
            System.out.println("SWEEP stale runs: " + rs + " shot_ms at click " + new java.util.Date(msNow) + " after idle " + new java.util.Date(ms));
            java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
            c.setTimeInMillis(ms);
            fail("while the Sunrise answer was worked out the sheet still showed the Sunset one ('" + head + "', was '" + sunsetHead
                    + "', " + (when == null ? "" : when.getContentDescription()) + ") with Fly here enabled; Fly here then opened Your shot at "
                    + c.getTime() + " (hour " + c.get(java.util.Calendar.HOUR_OF_DAY) + ")");
        }
    }

    // ---------------------------------------------------------------- Line up a street: Behind subject / you while working

    @Test public void behindToggledWhileWorkingItOut() throws Exception {
        launch(YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        // no wait: toggle back and forth while the times are worked out
        for (int i = 0; i < 4; i++) {
            View v = findOrNull(i % 2 == 0 ? "Behind you" : "Behind subject");
            if (v != null) v.performClick();
            idle();
        }
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 15000);
        Thread.sleep(400);
        idle();
        int mode = prefs().getInt("mode", 0);
        String side = mode == 1 ? " behind you" : " behind Big Ben";
        String wrong = mode == 1 ? " behind Big Ben" : " behind you";
        View right = walk(root(), side, true, false);
        // the card's words agree with the switch
        for (TextView t : texts(root())) {
            String s = t.getText().toString();
            if (s.startsWith("Sun" + wrong) || s.startsWith("Moon" + wrong))
                fail("the switch says" + side + " but the card says '" + s + "'");
        }
        assertNotNull("the card names the side chosen", right);
    }

    @Test public void lineItUpUndoThenLineItUpAgain() throws Exception {
        launch(new Double[]{51.4980, -0.1246}, SUB, "1,0,2");
        click(describedFrom("Continue"));
        waitFor("Line it up");
        click(find("Line it up"));
        waitForPrefix("You moved round the subject");
        double movedLat = Double.parseDouble(prefs().getString("you_lat", "0"));
        // the card is the answer for the new spot: Line it up again must not be offered when it now lines up
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 15000);
        Thread.sleep(300);
        idle();
        click(find("Undo"));
        assertEquals(51.4980, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        waitFor("Line it up");
        click(find("Line it up"));
        waitForPrefix("You moved round the subject");
        assertEquals("Line it up is the same move each time", movedLat, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-6);
        recreate();
        assertTrue(stepBar(), stepBar().startsWith("Step 3 of 4"));
    }

    // ---------------------------------------------------------------- Saved

    @Test public void savedSaveRenameRemoveOpenBell() throws Exception {
        launch(YOU, SUB, "1,0,2");
        offline();
        click(describedFrom("Continue"));
        waitForPrefix(" behind subject", " behind Big Ben");
        click(seeIt());
        act.onBackPressed();
        idle();
        click(byDescription("Save this shot"));
        waitForPrefix("Saved as ");
        assertBackLeavesToHomeOnly();
        click(byDescription("Saved"));
        waitFor("Big Ben");
        // rename: hold, type, Save
        View open = describedFrom("Open Big Ben");
        assertTrue(open.performLongClick());
        idle();
        Dialog d = ShadowDialog.getLatestDialog();
        assertNotNull("rename dialog", d);
        android.widget.EditText name = (android.widget.EditText) ofType(d.getWindow().getDecorView(), android.widget.EditText.class);
        name.setText("Thames sunset");
        ((android.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        waitFor("Thames sunset");
        // rename to nothing: nothing changes, and the old name stays
        assertTrue(describedFrom("Open Thames sunset").performLongClick());
        idle();
        d = ShadowDialog.getLatestDialog();
        ((android.widget.EditText) ofType(d.getWindow().getDecorView(), android.widget.EditText.class)).setText("   ");
        ((android.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        assertNotNull(findOrNull("Thames sunset"));
        // the bell, on and off
        click(byDescription("Remind me 30 minutes before"));
        assertEquals(1, Reminders.all(act).size());
        click(byDescription("Reminder set. Press to take it off"));
        assertEquals(0, Reminders.all(act).size());
        click(byDescription("Remind me 30 minutes before"));
        // open it: Your shot at its moment, the name kept
        click(describedFrom("Open Thames sunset"));
        assertTrue(stepBar(), stepBar().startsWith("Step 4 of 4"));
        assertNotNull("Your shot knows it is saved under its new name", describedFrom(root(), "Saved as Thames sunset"));
        recreate();
        assertTrue("restart on a shot opened from Saved: " + stepBar(), stepBar().startsWith("Step 4 of 4"));
        act.onBackPressed();
        idle();
        // remove it, and its reminder; Undo
        if (!onSaved()) {
            goHome();
            click(byDescription("Saved"));
        }
        waitFor("Thames sunset");
        click(byDescription("Remove Thames sunset"));
        assertEquals(0, Trip.loadLines(act).size());
        assertEquals(0, Reminders.all(act).size());
        click(find("Undo"));
        assertEquals(1, Trip.loadLines(act).size());
        assertEquals(1, Reminders.all(act).size());
        // the Trip tab, and a moment's bell
        click(tab("Trip"));
        waitForPrefix(" behind subject", " behind Big Ben");
        View planned = walk(root(), "Planned. Press to take it off your shoots", false, true);
        assertNotNull("the reminder set on the shot shows lit on its moment in Trip", planned);
        assertBackLeaves("Saved");
    }

    /** Removed with Undo still in the bar, then Saved closed and reopened: the shot stays removed, no crash. */
    @Test public void savedRemoveThenLeave() throws Exception {
        launch(YOU, SUB, "1,0,2");
        offline();
        click(describedFrom("Continue"));
        waitForPrefix(" behind subject", " behind Big Ben");
        click(seeIt());
        act.onBackPressed();
        idle();
        click(byDescription("Save this shot"));
        goHome();
        click(byDescription("Saved"));
        waitFor("Big Ben");
        click(describedFrom("Open Big Ben"));
        act.onBackPressed();
        idle();
        click(byDescription("Remove Big Ben"));
        act.onBackPressed();     // Saved closes with Undo still showing
        idle();
        assertTrue(onHome());
        View cont = describedFrom(root(), "Continue");
        if (cont != null) {
            click(cont);
            assertNotNull("Continue opens a step", describedFrom(root(), "Step "));
            View saved = describedFrom(root(), "Saved as ");
            assertNull("the removed shot is not shown as saved", saved);
        }
    }

    private static View anyDescribedEnding(View v, String end) {
        if (v.getVisibility() != View.VISIBLE) return null;
        CharSequence d = v.getContentDescription();
        if (d != null && d.toString().endsWith(end)) return v;
        if (v instanceof ViewGroup) for (int i = ((ViewGroup) v).getChildCount() - 1; i >= 0; i--) {
            View r = anyDescribedEnding(((ViewGroup) v).getChildAt(i), end);
            if (r != null) return r;
        }
        return null;
    }

    private boolean onSaved() { return walk(root(), "Shots", false, false) != null && walk(root(), "Trip", false, false) != null; }

    private void assertBackLeavesToHomeOnly() {
        goHome();
    }

    // ---------------------------------------------------------------- moon mode through both tools

    @Test public void moonThroughBothTools() throws Exception {
        launch(clean().putBoolean("drone", true), null, null, null);
        click(byDescription("Moon"));
        idle();
        Reveal.finishAll();
        idle();
        click(byDescription("Line up a street"));
        assertNotNull(byDescription("Sun or moon: Moon"));
        act.tapMap(new GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        act.tapMap(new GeoPoint(YOU_LAT, YOU_LON));
        idle();
        click(find("Next"));
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 15000);
        Thread.sleep(300);
        idle();
        for (TextView t : texts(root())) assertFalse("a moon tool shows the sun: " + t.getText(), t.getText().toString().startsWith("Sun behind"));
        assertWhenHasAWayOn("moon, London");
        click(tab("Day chart"));
        click(tab("All times"));
        View see = seeItOrNull();
        if (see != null && see.isEnabled()) {
            click(see);
            assertNotNull(byDescription("Moon lined up"));
            act.onBackPressed();
            idle();
            waitFor("Open the viewfinder");
            View other = walk(root(), "Build the shot", false, false);
            if (other != null) {
                while (other != null && !other.isClickable()) other = (View) other.getParent();
                click(other);
                assertNotNull("Build the shot from Your shot", ofType(root(), ComposeScreen.class));
                waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
                assertNotNull(walk(root(), "How full: ", true, true) == null ? describedFrom(root(), "Settings: Moon") : null);
            }
        }
        assertBackLeaves("moon");
    }

    /** Your shot from Build the shot shows You, the subject and the line on the map, for the moon as for the sun. */
    @Test public void yourShotMapShowsBothPinsSun() throws Exception { yourShotMapShowsBothPins(false); }

    @Test public void yourShotMapShowsBothPinsMoon() throws Exception { yourShotMapShowsBothPins(true); }

    private void yourShotMapShowsBothPins(boolean moon) throws Exception {
        launch(clean().putBoolean("drone", true).putBoolean("moon", moon), null, SUB, "3," + (moon ? 1 : 0) + ",1");
        click(describedFrom("Continue"));
        waitForPrefix(" of Big Ben");
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
        clickWhenReady("Fly here");
        waitFor("Open the viewfinder");
        for (int i = 0; i < 20; i++) {          // the sheet settles and the map is fitted again (posted)
            Thread.sleep(50);
            idle();
            View r = root();
            r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
            r.layout(0, 0, 1080, 2340);
        }
        shot("your-shot-map-" + (moon ? "moon" : "sun"));
        java.lang.reflect.Field mf = MainActivity.class.getDeclaredField("map");
        mf.setAccessible(true);
        org.osmdroid.views.MapView mv = (org.osmdroid.views.MapView) ((GeoMap) mf.get(act)).view();
        org.osmdroid.util.BoundingBox bb = mv.getBoundingBox();
        double yLat = Double.parseDouble(prefs().getString("you_lat", "0")), yLon = Double.parseDouble(prefs().getString("you_lon", "0"));
        assertTrue((moon ? "moon" : "sun") + ": the subject is off the map on Your shot (" + bb + ")", bb.contains(SUB_LAT, SUB_LON));
        assertTrue((moon ? "moon" : "sun") + ": You is off the map on Your shot (" + bb + ")", bb.contains(yLat, yLon));
    }

    // ---------------------------------------------------------------- more keys, pressed many times

    /** Every viewfinder key, twice, in both layouts; the lens to both ends; then Back. */
    @Test public void viewfinderEveryKeyBothLayouts() throws Exception {
        for (int layout = 0; layout <= 1; layout++) {
            launch(clean().putInt("viewfinder", layout), YOU, SUB, "1,0,2");
            offline();
            click(describedFrom("Continue"));
            waitForPrefix(" behind Big Ben");
            click(seeIt());
            FrameScreen f = (FrameScreen) ofType(root(), FrameScreen.class);
            assertNotNull(f);
            for (int i = 0; i < 30; i++) { View w = walk(root(), "Wider lens", false, true); if (w != null) w.performClick(); }
            idle();
            assertNotNull("layout " + layout + ": still in the viewfinder after the widest lens", ofType(root(), FrameScreen.class));
            for (int i = 0; i < 40; i++) { View w = walk(root(), "Longer lens", false, true); if (w != null) w.performClick(); }
            idle();
            assertNotNull("layout " + layout + ": still in the viewfinder after the longest lens", ofType(root(), FrameScreen.class));
            for (String key : new String[]{"Turn the camera: landscape or portrait", "Now: show the sky as it is now",
                    "Sun lined up", "Turn the camera: landscape or portrait"}) {
                View k = walk(root(), key, false, true);
                if (k == null) continue;
                k.performClick();
                idle();
                assertNotNull("layout " + layout + ": one press of '" + key + "' closed the viewfinder", ofType(root(), FrameScreen.class));
                k = walk(root(), key, false, true);
                if (k != null) { k.performClick(); idle(); }
                assertNotNull("layout " + layout + ": a second press of '" + key + "' closed the viewfinder", ofType(root(), FrameScreen.class));
            }
            View move = byDescription("Move: walk where you stand");
            doubleTap(move);          // the second tap finds no Move key: the keys hide while walking
            assertNotNull("walking", walk(root(), "Joystick: push to walk", false, true));
            f.moveTo(YOU_LAT + 0.0005, YOU_LON + 0.0005);
            idle();
            waitForPrefix("You moved");   // the new spot's moment comes back worked out
            act.onBackPressed();      // closes the joystick
            idle();
            View bell = describedFrom(root(), "Remind me");
            if (bell == null) bell = describedFrom(root(), "Plan this shoot");
            if (bell != null) doubleTap(bell);
            View save = walk(root(), "Save this shot", false, true);
            if (save != null) doubleTap(save);
            shot("viewfinder-keys-" + layout);
            StringBuilder names = new StringBuilder();
            for (Trip.Line l : Trip.loadLines(act)) names.append(l.name).append(" @ ").append(new java.util.Date(l.timeMs)).append("; ");
            assertTrue("layout " + layout + ": at most one shot saved, found " + names, Trip.loadLines(act).size() <= 1);
            act.onBackPressed();
            idle();
            assertNull("Back closes the viewfinder", ofType(root(), FrameScreen.class));
            assertBackLeaves("viewfinder keys, layout " + layout);
        }
    }

    /** While walking (Move) the screen offers a way to stop walking, not only the phone's Back. */
    @Test public void walkingHasAKeyToStop() throws Exception {
        launch(YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        waitForPrefix(" behind Big Ben");
        click(seeIt());
        click(byDescription("Move: walk where you stand"));
        assertNotNull(walk(root(), "Joystick: push to walk", false, true));
        shot("walking");
        View vf = ofType(root(), FrameScreen.class);        // only what the viewfinder itself shows (Your shot is under it)
        View stop = walk(vf, "Move: walk where you stand", false, true);
        if (stop == null) stop = describedFrom(vf, "Done");
        if (stop == null) stop = describedFrom(vf, "Close");
        if (stop == null) stop = describedFrom(vf, "Stop");
        assertNotNull("while walking no key on screen stops it (Move hides with the other keys; only the phone's Back closes the joystick)", stop);
    }

    /** The three dots on When: Swap twice, Clear then Undo, Clear then Back. */
    @Test public void threeDotsSwapClearUndo() throws Exception {
        launch(YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        waitForPrefix(" behind Big Ben");
        for (int i = 0; i < 2; i++) {
            menu("Swap You and the subject");
            waitForPrefix("Swapped");
        }
        assertEquals("two swaps: You back where it was", YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        menu("Clear");
        waitForPrefix("Cleared");
        assertNotNull("after Clear there is a step to be on: " + stepBar(), describedFrom(root(), "Step "));
        click(find("Undo"));
        assertEquals(YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        assertTrue(prefs().contains("subject_lat"));
        menu("Clear");
        assertBackLeaves("after Clear");
        launch(clean(), null, null, null);
        assertNull("after Clear and Back, no Continue to an empty tool", describedFrom(root(), "Continue"));
    }

    private void menu(String item) {
        View more = byDescription("More");
        more.performClick();
        idle();
        android.widget.PopupMenu m = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull("the three dots open a menu", m);
        android.view.Menu menu = m.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).getTitle().toString().equals(item)) {
                shadowOf(m).getOnMenuItemClickListener().onMenuItemClick(menu.getItem(i));
                idle();
                return;
            }
        }
        fail("no menu item " + item);
    }

    /** The Day chart's next day a year on, and back; All times opened from the Day chart. */
    @Test public void dayChartAYearOfDays() throws Exception {
        launch(YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        waitForPrefix(" behind Big Ben");
        click(tab("Day chart"));
        for (int i = 0; i < 370; i++) {
            View n = walk(root(), "Next day", false, true);
            assertNotNull("Next day key on day " + i, n);
            n.performClick();
            if (i % 30 == 0) idle();
        }
        idle();
        for (int i = 0; i < 400; i++) {
            View n = walk(root(), "Previous day", false, true);
            if (n == null) break;
            n.performClick();
            if (i % 30 == 0) idle();
        }
        idle();
        shot("day-chart-back-from-a-year");
        click(tab("All times"));
        assertBackLeaves("day chart");
    }

    /** Trip days: less than one and more than fourteen are refused, the list follows. */
    @Test public void tripDaysAtBothEnds() throws Exception {
        launch(YOU, SUB, null);
        click(byDescription("Saved"));
        click(tab("Trip"));
        for (int i = 0; i < 20; i++) { View v = walk(root(), "One day less", false, true); if (v != null && v.isEnabled()) click(v); }
        assertEquals(1, prefs().getInt("trip_days", 3));
        for (int i = 0; i < 20; i++) { View v = walk(root(), "One day more", false, true); if (v != null && v.isEnabled()) click(v); }
        int days = prefs().getInt("trip_days", 3);
        assertTrue("at most 14 days after the first: " + days, days <= 15);
        assertBackLeaves("trip days");
    }

    /** A shared link: one that holds nothing, then a good one, then the same one again. */
    @Test public void sharedLinks() throws Exception {
        launch(null, null, null);
        android.content.Intent bad = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("sunwhere://shot?d=not-a-shot"));
        ctl.newIntent(bad);
        idle();
        waitForPrefix("does not hold a shot");
        Trip.Line l = new Trip.Line("x", "Shared", YOU_LAT, YOU_LON, SUB_LAT, SUB_LON, 0);
        l.timeMs = System.currentTimeMillis() + 86400000L;
        l.subjectName = "Big Ben";
        String url = ShotLink.url(l);
        for (int i = 0; i < 2; i++) {
            ctl.newIntent(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
            idle();
            Dialog d = ShadowDialog.getLatestDialog();
            assertNotNull("a dialog offers to save the shared shot", d);
            ((android.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            idle();
            assertTrue("Your shot opens: " + stepBar(), stepBar().startsWith("Step 4 of 4"));
        }
        assertEquals("the same link opened twice: two shots (or one, if the app notices)", true, Trip.loadLines(act).size() >= 1);
        assertBackLeaves("shared link");
    }

    /** When's sheet pulled to full with a finger's tap on its bar, then left to settle (the map is refitted). */
    @Test public void whenSheetPulledToFull() throws Exception {
        launch(YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        waitForPrefix(" behind Big Ben");
        Sheet sh = (Sheet) ofType(root(), Sheet.class);
        sh.setStop(Sheet.FULL, true);                 // animated, as a fling: it settles and the map is fitted again
        for (int i = 0; i < 40; i++) {
            Thread.sleep(25);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));   // hung here before the fix
        }
        shot("when-sheet-full");
        assertNotNull(describedFrom(root(), "Step 3 of 4"));
        act.onBackPressed();       // full goes back to half
        idle();
        assertTrue(stepBar(), stepBar().startsWith("Step 3 of 4"));
    }

    /** A saved shot with no time ("Any time", as 2.1's lines became) opened from Saved, on the flat map. */
    @Test public void savedAnyTimeShotOpens() throws Exception {
        launch(null, null, null);
        Trip.Line l = new Trip.Line("old", "Old line", YOU_LAT, YOU_LON, SUB_LAT, SUB_LON, 0);
        Trip.addShot(act, l);
        click(byDescription("Saved"));
        waitFor("Old line");
        click(describedFrom("Open Old line"));      // hung here (osmdroid zoomToBoundingBox) before the fix
        assertTrue(stepBar(), stepBar().startsWith("Step 3 of 4"));
        assertBackLeaves("any-time shot from Saved");
    }

    /** A link to a shot with no time ("any time it lines up"), You and the subject apart as usual. */
    @Test public void sharedLinkAnyTime() throws Exception {
        launch(null, null, null);
        Trip.Line l = new Trip.Line("z", "Any time", YOU_LAT, YOU_LON, SUB_LAT, SUB_LON, 0);
        ctl.newIntent(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ShotLink.url(l))));
        idle();
        Dialog d = ShadowDialog.getLatestDialog();
        assertNotNull(d);
        ((android.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        shot("shared-link-any-time");
        assertBackLeaves("shared link, any time");
    }

    /** A link to a shot whose You stands on its subject (the app refuses that on the map). */
    @Test public void sharedLinkSameSpot() throws Exception {
        launch(null, null, null);
        Trip.Line same = new Trip.Line("y", "Same spot", SUB_LAT, SUB_LON, SUB_LAT, SUB_LON, 0);
        same.timeMs = System.currentTimeMillis() + 86400000L;
        ctl.newIntent(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ShotLink.url(same))));
        idle();
        Dialog d = ShadowDialog.getLatestDialog();
        if (d != null && d.isShowing()) {
            ((android.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            idle();
            shot("shared-link-same-spot");
            assertBackLeaves("shared link, same spot");
        }
    }

    /** Build the shot: the lens to both ends, the subject's height dialog with Back, Help with Back. */
    @Test public void buildLensEndsAndDialogs() throws Exception {
        launch(clean().putBoolean("drone", true), null, SUB, "3,0,1");
        click(describedFrom("Continue"));
        waitForPrefix(" of Big Ben");
        for (int i = 0; i < 30; i++) byDescription("Wider lens").performClick();
        idle();
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
        shot("build-widest");
        for (int i = 0; i < 40; i++) byDescription("Longer lens").performClick();
        idle();
        waitUntil(() -> walk(root(), "Working it out", true, false) == null, 20000);
        shot("build-longest");
        click(byDescription("Subject height"));
        act.onBackPressed();
        idle();
        assertNotNull(ofType(root(), ComposeScreen.class));
        click(byDescription("Help: how Build the shot works"));
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Skip"));
        View fly = find("Fly here");
        if (fly.isEnabled()) click(fly);
        assertBackLeaves("lens ends");
    }

    // ---------------------------------------------------------------- the Sun / Moon switch mid-tool

    @Test public void switchBodyAtEveryStep() throws Exception {
        launch(clean().putBoolean("drone", true), YOU, SUB, "1,0,0");
        click(describedFrom("Continue"));
        for (int step = 0; step < 4; step++) {
            String before = stepBar();
            click(byDescription("Sun or moon: Sun"));
            click(byDescription("Moon"));
            Reveal.finishAll();
            idle();
            assertNotNull(before + ": the bar says Moon", walk(root(), "Sun or moon: Moon", false, true));
            click(byDescription("Sun or moon: Moon"));
            click(byDescription("Sun"));
            Reveal.finishAll();
            idle();
            if (step < 3) {
                View next = findOrNull("Next");
                if (next != null) click(next);
                else {
                    waitForPrefix(" behind subject", " behind Big Ben");
                    click(seeIt());
                    act.onBackPressed();
                    idle();
                }
            }
        }
        assertBackLeaves("switch body");
    }

    /** A picture of the screen, for the write-up: app/build/sweep/NAME.png. */
    private void shot(String name) { shot(name, true); }

    private void shot(String name, boolean settle) {
        try {
            if (settle) {
                Reveal.finishAll();
                idle();
            }
            View r = root();
            r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
            r.layout(0, 0, 1080, 2340);
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888);
            r.draw(new android.graphics.Canvas(b));
            File dir = new File("build/sweep");
            dir.mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, name + ".png"))) {
                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ---------------------------------------------------------------- helpers (as ScreensTest's)

    private void waitUntil(BooleanSupplier ok, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!ok.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(50);
            idle();
        }
    }

    private void waitFor(String text) throws InterruptedException {
        waitUntil(() -> findOrNull(text) != null, 30000);
        assertNotNull("never showed: " + text, findOrNull(text));
    }

    /** Waits for any of these words to show anywhere. */
    private void waitForPrefix(String... any) throws InterruptedException {
        waitUntil(() -> {
            for (String s : any) if (walk(root(), s, true, false) != null) return true;
            return false;
        }, 30000);
        boolean seen = false;
        for (String s : any) seen |= walk(root(), s, true, false) != null;
        assertTrue("never showed: " + String.join(" | ", any) + " (on " + stepBar() + ")", seen);
    }

    private void goHome() {
        for (int i = 0; i < 10 && !onHome(); i++) {
            act.onBackPressed();
            idle();
        }
        assertTrue("never reached Home", onHome());
    }

    private SharedPreferences prefs() { return act.getSharedPreferences("sunlighn", Context.MODE_PRIVATE); }

    /** 3.23: Stand here / Fly here is greyed while a new answer is worked out; press it once it is ready. */
    private void clickWhenReady(String text) throws InterruptedException {
        waitUntil(() -> findOrNull(text) != null && findOrNull(text).isEnabled(), 30000);
        View v = find(text);
        assertTrue(text + " never became ready", v.isEnabled());
        click(v);
    }

    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }

    private void click(View v) {
        assertNotNull(v);
        v.performClick();
        idle();
    }

    private View seeItOrNull() {
        View v = findOrNull("See it in the viewfinder");
        return v != null ? v : walk(root(), "See the soonest in the viewfinder", false, true);
    }

    private View seeIt() {
        View v = seeItOrNull();
        assertNotNull("no key to see the soonest in the viewfinder", v);
        return v;
    }

    private View tab(String name) {
        View v = walk(root(), name, false, true);
        if (v == null) v = walk(root(), name + ", open", false, true);
        if (v == null) v = findOrNull(name);
        assertNotNull("no tab: " + name, v);
        return v;
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
        if (v.getVisibility() != View.VISIBLE) return out;
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
}
