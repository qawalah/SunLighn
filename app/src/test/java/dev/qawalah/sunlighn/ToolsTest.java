package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 3.24 (owner, 2026-11-22): three tools and Now. Presses through Home, Line up a street (a line, not a subject), Find
 * the spot (every spot and day) and Now (the light where you are), with Back at every step, and keeps pictures in
 * app/build/screens/ (40-...).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ToolsTest {
    private static final double SUB_LAT = 51.5007, SUB_LON = -0.1246, YOU_LAT = 51.5033, YOU_LON = -0.1196;

    private ActivityController<MainActivity> ctl;
    private MainActivity act;

    @Before public void setUp() {
        Heights.enabled = false;
        Buildings.enabled = false;
        GeoMap.libreAllowed = false;
    }

    private SharedPreferences.Editor prefs() {
        Application app = RuntimeEnvironment.getApplication();
        return app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true)
                .putBoolean("guide_find", true).putBoolean("guide_choose", true).putBoolean("guide_map", true).putBoolean("guide_now", true).putBoolean("guide_shot", true).putBoolean("guide_frame", true).putBoolean("asked_location", true)
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15");
    }

    private SharedPreferences read() {
        return RuntimeEnvironment.getApplication().getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
    }

    private void open(SharedPreferences.Editor e) {
        e.apply();
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
        idle();
    }

    /** Home: the four modes, simplest first (Now a card like the rest, 3.24.2), and Continue, on one screen in every design. */
    @Test public void homeHasThreeToolsAndNow() throws Exception {
        String[] names = {"classic", "pocket", "readout", "appliance"};
        for (int d = 0; d < names.length; d++) {
            // with Continue showing: the fullest Home
            open(prefs().putInt("design", d).putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                    .putString("subject_name", "Big Ben").putString("flow", "1,0,1"));
            View line = byDescription("Line up a street"), find = byDescription("Find the spot"), build = byDescription("Build the shot");
            View now = describedFrom("Continue");
            int[] at = new int[2];
            int last = 0;
            for (View v : new View[]{line, find, build, byDescription("Now"), now}) {
                assertTrue("a size", v.getHeight() > 0);
                v.getLocationOnScreen(at);
                assertTrue(names[d] + ": in order, simplest first", at[1] > last);
                last = at[1];
            }
            shot("40-home-" + names[d]);
            assertTrue(names[d] + ": Continue, under Now, fits on the screen", last + now.getHeight() <= root().getHeight());
            ctl.pause().stop().destroy();
        }
    }

    /** Line up a street asks for a line: the far end, then where you stand; When has only the Sun height slider. */
    @Test public void lineUpAStreetIsALine() throws Exception {
        open(prefs());
        click(byDescription("Line up a street"));
        assertEquals("Step 1 of 4 · The far end", describedFrom("Step ").getContentDescription().toString());
        assertNotNull(find("Where does the street point?"));
        act.tapMap(new org.osmdroid.util.GeoPoint(SUB_LAT, SUB_LON));
        idle();
        assertNotNull(find("The far end is on the map"));
        assertNull("no subject kind for a street's end", findOrNull("What is it?"));
        shot("41-line-far-end");
        click(find("Next"));
        act.tapMap(new org.osmdroid.util.GeoPoint(YOU_LAT, YOU_LON));
        idle();
        click(find("Next"));
        assertEquals("Step 3 of 4 · When", describedFrom("Step ").getContentDescription().toString());
        waitForPrefix("at the far end");
        ((Sheet) ofType(root(), Sheet.class)).setStop(Sheet.FULL, false);
        idle();
        assertNotNull(find("At the far end"));
        assertNotNull(find("Sun height"));
        assertNotNull(find("Any height"));
        assertNull("no pictures of where it sits on a subject", findOrNull("Middle"));
        shot("42-line-when");
        // a height picked on the slider; Any height brings every time back
        RangeBar bar = (RangeBar) byDescription("Sun height range");
        bar.commit(2, 6);
        idle();
        assertEquals(4, read().getInt("line_sits", 0));
        click(byDescription("Any height: every time it lines up"));
        assertEquals(0, read().getInt("line_sits", 0));
        // Back: When, Where you stand, The far end, Home
        ((Sheet) ofType(root(), Sheet.class)).setStop(Sheet.HALF, false);
        idle();
        back();
        assertEquals("Step 2 of 4 · Where you stand", describedFrom("Step ").getContentDescription().toString());
        back();
        back();
        assertNotNull(byDescription("Find the spot"));
    }

    /**
     * Find the spot: the subject, then how you want the sun (every choice open; the distance follows the height unless
     * fixed), then every spot and day (Sunset and Sunrise apart), Stand here. 3.24.1 (owner).
     */
    @Test public void findTheSpot() throws Exception {
        open(prefs());
        click(byDescription("Find the spot"));
        assertEquals("Step 1 of 4 · Your subject", describedFrom("Step ").getContentDescription().toString());
        act.tapMap(new org.osmdroid.util.GeoPoint(SUB_LAT, SUB_LON));
        idle();
        assertNotNull(findOrNull("What is it?"));
        click(find("Next"));
        assertEquals("Step 2 of 4 · How you want it", describedFrom("Step ").getContentDescription().toString());
        assertNotNull(find("How do you want the sun?"));
        ((Sheet) ofType(root(), Sheet.class)).setStop(Sheet.FULL, false);
        idle();
        // nothing folded: every choice is on the page
        assertNull("no folded settings", describedFrom(root(), "Settings: "));
        for (String t : new String[]{"Where the sun is", "Where it sits on the subject", "Sun height", "Distance from the subject",
                "Lens", "How you get there"}) assertNotNull(t, find(t));
        assertNotNull(byDescription("Light: Any"));
        assertNotNull(byDescription("Lens range"));
        shot("43-find-how-you-want-the-sun");
        // the distance follows the sun's height: higher, closer
        android.widget.SeekBar height = (android.widget.SeekBar) byDescription("Sun height");
        height.setProgress(100);
        shadowOf(height).getOnSeekBarChangeListener().onStopTrackingTouch(height);
        idle();
        assertEquals(10f, read().getFloat("find_alt", 0), 0.01);
        assertNotNull("the distance worked out from 10° on top of a 30 m building",
                walk(root(), "Distance " + Fmt.distance(Spots.distanceFor(Spots.ON_TOP, 30, 10)), true, false));
        // fixed: the distance is set, and gives the height
        click(find("Fixed"));
        assertTrue(read().getBoolean("find_fixed", false));
        assertNotNull(byDescription("Distance from the subject"));
        assertNotNull(walk(root(), "From the fixed distance", true, false));
        click(find("Auto"));
        assertNotNull(byDescription("Sun height"));
        click(find("Horizon"));
        assertNotNull(walk(root(), "On the horizon: just clear of it", true, false));
        click(find("Behind you"));
        assertNull("behind you: no subject's top to sit on", findOrNull("Where it sits on the subject"));
        click(find("Behind subject"));
        click(find("On top"));
        ((Sheet) ofType(root(), Sheet.class)).setStop(Sheet.HALF, false);
        idle();
        click(find("Next"));
        assertEquals("Step 3 of 4 · Where to stand", describedFrom("Step ").getContentDescription().toString());
        waitFor("Stand here");
        shot("44-find-spots");
        ((Sheet) ofType(root(), Sheet.class)).setStop(Sheet.FULL, false);
        idle();
        assertNotNull("grouped by type: sunsets", find("Sunset"));
        assertNotNull("and sunrises, apart", find("Sunrise"));
        assertNull("no settings here: they are the step before", findOrNull("Where it sits on the subject"));
        shot("45-find-list");
        View row = descriptionEndsWith("Pick these days");
        assertNotNull(row);
        click(row);
        assertNotNull(descriptionEndsWith(", picked"));
        // Change goes back to how you want the sun
        click(byDescription("Change how you want the sun"));
        assertEquals("Step 2 of 4 · How you want it", describedFrom("Step ").getContentDescription().toString());
        click(find("Next"));
        waitFor("Stand here");
        click(find("Stand here"));
        assertEquals("Step 4 of 4 · Your shot", describedFrom("Step ").getContentDescription().toString());
        assertNotNull(find("Open the viewfinder"));
        assertNotNull("the other tool", byDescription("Build the shot: another tool, with this subject"));
        shot("46-find-your-shot");
        back();
        assertEquals("Step 3 of 4 · Where to stand", describedFrom("Step ").getContentDescription().toString());
        back();
        assertEquals("Step 2 of 4 · How you want it", describedFrom("Step ").getContentDescription().toString());
        back();
        back();
        assertNotNull(byDescription("Find the spot"));
    }

    /** A height the sun never reaches says so, and offers what works. */
    @Test public void findTheSpotNeverSaysWhatWorks() throws Exception {
        open(prefs().putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                .putInt("find_behind", 1).putFloat("find_alt", 80).putString("flow", "4,0,2+"));
        click(describedFrom("Continue"));
        waitFor("Not in the coming year");
        shot("47-find-never");
        click(find("Put the sun on the horizon"));
        waitFor("Stand here");
    }

    /**
     * 3.24.2 (owner: "break the tutorial into multiple ones, avoid repetition"): the first start shows the short overview,
     * then Which mode?; the first mode started carries the map's card, the next one does not; Your shot's guide comes
     * once; Settings lists each guide.
     */
    @Test public void guidesAreSaidOnce() throws Exception {
        open(prefs().putBoolean("tutorial_done", false).putBoolean("guide_choose", false).putBoolean("guide_map", false)
                .putBoolean("guide_line", false).putBoolean("guide_find", false).putBoolean("guide_shot", false));
        assertNotNull(find("Sun or moon"));
        assertNotNull(find("Pick a mode"));
        assertNotNull(find("See it first"));
        shot("51-guide-overview");
        back();
        assertNotNull("then which mode", find("Which mode?"));
        assertNotNull(walk(root(), "You give a street", true, false));
        shot("52-guide-which-mode");
        back();
        assertTrue(read().getBoolean("guide_choose", false));
        assertNull(ofType(root(), Tutorial.class));
        // the first mode: its own cards, then the map's
        click(byDescription("Line up a street"));
        idle();
        assertNotNull(find("Pick a street"));
        assertNotNull("the map's card comes with the first mode", find("Using the map"));
        back();
        assertTrue(read().getBoolean("guide_map", false));
        back();                                                    // Home
        // the second mode: its own cards only
        click(byDescription("Find the spot"));
        idle();
        assertNotNull(find("How you want it"));
        assertNull("the map is not explained again", findOrNull("Using the map"));
        back();
        back();
        // Your shot's guide, once
        open(prefs().putBoolean("guide_shot", false).putString("you_lat", "" + YOU_LAT).putString("you_lon", "" + YOU_LON)
                .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON).putString("flow", "1,0,3"));
        click(describedFrom("Continue"));
        idle();
        idle();
        assertNotNull("Your shot explained the first time", findOrNull("Every mode ends here: the moment, and the photo it makes."));
        back();
        assertTrue(read().getBoolean("guide_shot", false));
        // Settings lists each guide; Which mode? on Home
        click(byDescription("Close: back to Home"));
        click(byDescription("Settings"));
        for (String g : new String[]{"Which mode?", "Line up a street", "Find the spot", "Build the shot", "Now", "Using the map",
                "Your shot", "The viewfinder"}) assertNotNull(g, byDescription("Guide: " + g));
        back();
        click(byDescription("Which mode? What each gives you"));
        assertNotNull(find("Which mode?"));
        back();
    }

    /** The first time Find the spot starts, its guide; Back closes it; the three dots bring it back. */
    @Test public void findTheSpotGuide() throws Exception {
        open(prefs().putBoolean("guide_find", false));
        click(byDescription("Find the spot"));
        idle();
        assertNotNull(ofType(root(), Tutorial.class));
        shot("48-find-guide");
        back();
        assertNull(ofType(root(), Tutorial.class));
        assertTrue(read().getBoolean("guide_find", false));
    }

    /** Now: where you are, the day chart, the camera with no subject; Back closes each, then Home. A restart stays. */
    @Test public void nowShowsTheLightWhereYouAre() throws Exception {
        open(prefs().putString("you_lat", "" + YOU_LAT).putString("you_lon", "" + YOU_LON)
                .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON).putString("flow", "1,0,2"));
        click(byDescription("Now"));          // 3.24.2: a mode card like the others
        assertEquals("Step Now", describedFrom("Step ").getContentDescription().toString());
        assertNotNull(find("Look through the camera"));
        assertNotNull(ofType(root(), DayChart.class));
        shot("49-now");
        // a tap on the map looks from there
        act.tapMap(new org.osmdroid.util.GeoPoint(51.51, -0.13));
        idle();
        assertEquals(51.51, Double.parseDouble(read().getString("here_lat", "0")), 1e-6);
        // the tools' points are untouched, and Continue still offers the tool left unfinished
        assertEquals("" + YOU_LAT, read().getString("you_lat", ""));
        assertEquals("1,0,2", read().getString("flow", ""));
        click(find("Look through the camera"));
        FrameScreen f = (FrameScreen) ofType(root(), FrameScreen.class);
        assertNotNull(f);
        assertNull("nothing to remind", findShown("Remind me"));
        assertNull("nothing to walk round", findShown("Move"));
        assertNotNull(walk(root(), "Now here", true, false));
        shot("50-now-camera");
        back();
        assertNull(ofType(root(), FrameScreen.class));
        ctl.recreate();
        act = ctl.get();
        idle();
        assertEquals("Step Now", describedFrom("Step ").getContentDescription().toString());
        back();
        assertNotNull(describedFrom("Continue"));
    }

    // ---- helpers ----

    private void back() {
        act.onBackPressed();
        idle();
    }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void click(View v) {
        assertNotNull(v);
        v.performClick();
        idle();
    }

    private View root() { return act.getWindow().getDecorView(); }

    private void waitFor(String text) throws InterruptedException {
        for (int i = 0; i < 600 && findOrNull(text) == null; i++) {
            Thread.sleep(50);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
        }
        assertNotNull("never showed: " + text, findOrNull(text));
    }

    private void waitForPrefix(String text) throws InterruptedException {
        for (int i = 0; i < 600 && walk(root(), text, true, false) == null; i++) {
            Thread.sleep(50);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
        }
        assertNotNull("never showed: " + text, walk(root(), text, true, false));
    }

    private View find(String text) {
        View v = findOrNull(text);
        assertNotNull("not on screen: " + text, v);
        return v;
    }

    private View findOrNull(String text) { return walk(root(), text, false, false); }

    /** A key's name that is on screen (not hidden). */
    private View findShown(String text) {
        View v = findOrNull(text);
        return v != null && v.isShown() ? v : null;
    }

    private View byDescription(String d) {
        View v = walk(root(), d, false, true);
        assertNotNull("no view described as: " + d, v);
        return v;
    }

    private View describedFrom(String start) {
        View v = describedFrom(root(), start);
        assertNotNull("no view described from: " + start, v);
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

    private View descriptionEndsWith(String end) { return endsWith(root(), end); }

    private static View endsWith(View v, String end) {
        if (v.getVisibility() != View.VISIBLE) return null;
        CharSequence d = v.getContentDescription();
        if (d != null && d.toString().endsWith(end)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = endsWith(g.getChildAt(i), end);
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

    private void shot(String name) throws Exception {
        Reveal.finishAll();
        idle();
        View r = root();
        r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        r.layout(0, 0, 1080, 2340);
        Bitmap b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888);
        r.draw(new Canvas(b));
        File dir = new File("build/screens");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
