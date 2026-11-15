package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.robolectric.RuntimeEnvironment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Presses through the real screens on a computer: the answer sheet, the Day tab, the Frame, Where to stand,
 * Plan a frame, search, Saved with its Shoots tab, reminders and Back. Pictures of each step land in app/build/screens/ for a person to look at.
 */
@RunWith(RobolectricTestRunner.class)
// A phone of 360 x 780 dp at three pixels per dp, so the pictures look as they do in the hand.
@Config(sdk = 34, qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreensTest {
    // A line in London, from the south bank towards the north-west, a common sunset direction in summer.
    private static final double YOU_LAT = 51.5033, YOU_LON = -0.1196, SUB_LAT = 51.5007, SUB_LON = -0.1246;

    private ActivityController<MainActivity> ctl;
    private MainActivity act;

    @Before public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences p = app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        p.edit().clear().putBoolean("tutorial_done", true)
                .putString("you_lat", "" + YOU_LAT).putString("you_lon", "" + YOU_LON)
                .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15")
                .putString("flow", "1,0,2").apply();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
        // The app opens on Home; the tests start where the user left off: the When step of Line up a street.
        click(describedFrom("Continue"));
    }

    /** Opens the app afresh with these points (null: none) and this flow (null: none). */
    private void fresh(Double[] youPt, Double[] subPt, String flow) {
        SharedPreferences.Editor e = RuntimeEnvironment.getApplication().getSharedPreferences("sunlighn", Context.MODE_PRIVATE)
                .edit().clear().putBoolean("tutorial_done", true)
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15");
        if (youPt != null) e.putString("you_lat", "" + youPt[0]).putString("you_lon", "" + youPt[1]);
        if (subPt != null) e.putString("subject_lat", "" + subPt[0]).putString("subject_lon", "" + subPt[1])
                .putString("subject_name", "Big Ben");
        if (flow != null) e.putString("flow", flow);
        e.apply();
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
        idle();
    }

    private static final Double[] YOU = {YOU_LAT, YOU_LON}, SUB = {SUB_LAT, SUB_LON};

    private String stepBar() {
        View v = describedFrom("Step ");
        return v.getContentDescription().toString();
    }

    /** The app opens on Home: three tools, simplest first; Back there leaves the app. */
    @Test public void opensOnHome() throws Exception {
        fresh(null, null, null);
        assertNotNull(byDescription("Line up a street"));
        assertNotNull(byDescription("Where to stand"));
        assertNotNull(byDescription("Build the shot"));
        assertNull(findOrNull("Continue"));
        shot("01-home");
        click(find("Moon"));
        waitForPrefix("the moon");
        shot("01b-home-moon");
    }

    /** Back walks back through the steps, keeps what was placed, then Home, then leaves the app. */
    @Test public void backStepsThroughEveryTool() throws Exception {
        fresh(null, null, null);
        click(byDescription("Where to stand"));
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        shot("02-stand-subject-step");
        act.tapMap(new org.osmdroid.util.GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        assertTrue(stepBar().startsWith("Step 2 of 4"));
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        assertTrue(prefs().contains("subject_lat"));
        act.onBackPressed();
        idle();
        assertNotNull(byDescription("Build the shot"));
        act.onBackPressed();
        idle();
        assertTrue(act.isFinishing());
        // Line up a street starts from where you stand
        fresh(null, null, null);
        click(byDescription("Line up a street"));
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        waitFor("Where will you stand?");
        act.tapMap(new org.osmdroid.util.GeoPoint(YOU_LAT, YOU_LON));
        idle();
        click(find("Next"));
        waitFor("What is at the other end?");
        act.tapMap(new org.osmdroid.util.GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        assertTrue(stepBar().startsWith("Step 3 of 4"));
        act.onBackPressed();
        idle();
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        assertTrue(prefs().contains("you_lat") && prefs().contains("subject_lat"));
    }

    /** Coming back after the app was closed: Home offers to continue where the user was. */
    @Test public void restartOffersContinue() throws Exception {
        fresh(null, SUB, "2,0,2");
        assertNotNull(describedFrom("Continue"));
        waitForPrefix("Where to stand, Big Ben");
        click(describedFrom("Continue"));
        assertTrue(stepBar().startsWith("Step 3 of 4"));
    }

    /** The line is drawn: the sheet leads with the soonest moment; Times, Day and the Frame all open; Back steps out. */
    @Test public void answerSheetDayAndFrame() throws Exception {
        waitFor("Times");
        waitForPrefix("Sun behind subject");
        waitForPrefix("% lit");
        shot("1-answer");
        assertNotNull(find("Behind subject"));
        assertNotNull(find("Pick a date"));
        click(byDescription("Pull the sheet up or down"));   // half to full: the whole list of answers
        shot("1b-answer-full");
        click(byDescription("Pull the sheet up or down"));   // full to peek: the map is free
        shot("1c-answer-peek");
        click(byDescription("Pull the sheet up or down"));

        click(tab("Day"));
        waitForPrefix("Orange is the sun");
        shot("2-day");
        click(tab("Times"));

        // The Frame: full screen, at the moment of the answer, with the lens keys and the jumps.
        click(byDescription("Viewfinder: see the shot as the camera will"));
        waitFor("Sun lined up");
        waitForPrefix("35 mm");
        shot("3-frame");
        click(byDescription("Longer lens"));
        waitForPrefix("50 mm");
        click(byDescription("Turn the camera: landscape or portrait"));
        click(find("Sun lined up"));
        waitForPrefix("· lined up");
        shot("4-frame-portrait-50mm");
        assertEquals(50f, prefs().getFloat("focal_mm", 0), 0.01);

        // Back closes the Frame, then goes one step back. Nothing is lost: all is saved as you go.
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Sun lined up"));
        assertTrue(stepBar().startsWith("Step 3 of 4"));
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 2 of 4"));
        assertFalse(act.isFinishing());
    }

    /** With a height, the subject is drawn to scale; the dark theme and a long lens. */
    @Test public void frameWithSubjectHeightAtNight() throws Exception {
        prefs().edit().putFloat("subject_h", 96).putInt("subject_kind", 4).putInt("theme", 2)
                .putFloat("focal_mm", 85).apply();
        open();
        waitForPrefix("Sun behind subject");
        waitForPrefix("% lit");
        shot("5-answer-dark");
        click(byDescription("Viewfinder: see the shot as the camera will"));
        waitFor("Moon lined up");
        shot("6-frame-height");
        click(find("Moon lined up"));
        shot("7-frame-moon");
        // The height dialog: Back closes it and nothing changes.
        click(byDescription("Subject height"));
        assertNotNull(ShadowDialog.getLatestDialog());
        assertTrue(ShadowDialog.getLatestDialog().isShowing());
        act.onBackPressed();
        idle();
        assertFalse(ShadowDialog.getLatestDialog().isShowing());
        assertNotNull(findOrNull("Moon lined up"));
        assertEquals(96f, prefs().getFloat("subject_h", 0), 0.01);
    }

    /** From a line: "Where to stand" keeps the subject and goes to that tool, at its criteria; Back steps back. */
    @Test public void whereToStandFromALine() throws Exception {
        waitFor("Where to stand");
        click(find("Where to stand"));
        assertTrue(stepBar().startsWith("Step 2 of 4"));
        waitFor("Stand here");
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        assertTrue(prefs().contains("subject_lat"));
    }

    /** Where to stand: the spots for a light, and "Fly here" puts You there and shows the shot. */
    @Test public void whereToStandThenStandHere() throws Exception {
        fresh(null, SUB, "2,0,2");
        click(describedFrom("Continue"));
        waitFor("Stand here");
        waitForPrefix("Stand 300 m ");
        shot("9-where-to-stand");
        click(byDescription("Pull the sheet up or down"));
        shot("9b-where-to-stand-full");
        click(byDescription("Pull the sheet up or down"));
        click(byDescription("Pull the sheet up or down"));

        pick("Light", "Moonrise");
        waitForPrefix("% lit");
        pick("Light", "Sunset");
        waitForPrefix("Stand 300 m ");

        // A drone can be anywhere, so the spot is exactly as far as asked (on foot it may move off the river).
        click(find("Drone"));
        click(find("Fly here"));
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        SharedPreferences p = prefs();
        double yLat = Double.parseDouble(p.getString("you_lat", "0")), yLon = Double.parseDouble(p.getString("you_lon", "0"));
        assertEquals(300, Astro.distance(yLat, yLon, SUB_LAT, SUB_LON), 3);
        waitFor("Open the viewfinder");
        shot("10-stood");
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 3 of 4"));
    }

    /** Search: results set the subject; an empty answer and no connection each say what to do next. */
    @Test public void searchSetsTheSubject() throws Exception {
        fresh(null, null, null);
        click(byDescription("Where to stand"));
        click(byDescription("Search a place"));
        SearchScreen s = (SearchScreen) ofType(root(), SearchScreen.class);
        assertNotNull(s);
        s.show(null);
        waitForPrefix("Check the connection");
        s.show(new java.util.ArrayList<>());
        waitForPrefix("Nothing found");
        s.show(Places.parse("[{\"name\":\"Tower Bridge\",\"display_name\":\"Tower Bridge, London, England\","
                + "\"lat\":\"51.5055\",\"lon\":\"-0.0754\"}]"));
        shot("11-search");
        click(byDescription("Tower Bridge, set as Subject"));
        assertNull(ofType(root(), SearchScreen.class));
        assertEquals(51.5055, Double.parseDouble(prefs().getString("subject_lat", "0")), 1e-9);
        assertEquals("Tower Bridge", prefs().getString("subject_name", null));
        // Back from the search closes it and changes nothing.
        click(byDescription("Search a place"));
        act.onBackPressed();
        idle();
        assertNull(ofType(root(), SearchScreen.class));
        assertFalse(act.isFinishing());
    }

    /** Clear lives behind the three dots and can be undone. */
    @Test public void clearAndUndo() throws Exception {
        waitFor("Times");
        click(byDescription("More"));
        android.widget.PopupMenu menu = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(menu);
        shadowOf(menu).getOnMenuItemClickListener().onMenuItemClick(menu.getMenu().findItem(2));
        idle();
        waitFor("Where will you stand?");
        assertFalse(prefs().contains("you_lat"));
        click(find("Undo"));
        assertEquals(YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        waitFor("Times");
    }

    @Test public void savedLineRemindRemoveAndBack() throws Exception {
        goHome();
        click(byDescription("Saved"));
        waitFor("Save the line on the map");
        shot("12-saved-empty");

        click(find("Save the line on the map"));
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        waitFor("Line 1");
        assertNull("the line on the map is now saved", findOrNull("Save the line on the map"));
        shot("13-saved-lines");
        click(tab("Trip"));
        waitForPrefix("Sun behind subject");
        shot("13b-saved-trip");

        // A bell sets a reminder and an alarm.
        View bell = byDescription("Plan this shoot: remind me 30 minutes before");
        click(bell);
        assertEquals(1, Reminders.all(act).size());
        AlarmManager am = (AlarmManager) act.getSystemService(Context.ALARM_SERVICE);
        assertNotNull(shadowOf(am).getNextScheduledAlarm());
        assertNotNull(byDescription("Planned. Press to take it off your shoots"));
        // The planned shoot is on its own tab, and opens by itself.
        click(tab("Shoots"));
        waitForPrefix("Sun behind subject");
        shot("13c-saved-shoots");

        // Removing the line takes its reminder with it; Undo brings both back.
        click(tab("Lines"));
        click(byDescription("Remove Line 1"));
        assertEquals(0, Reminders.all(act).size());
        waitForPrefix("Removed Line 1 and its reminder");
        shot("14-saved-removed");
        click(find("Undo"));
        assertEquals(1, Reminders.all(act).size());
        waitFor("Line 1");

        // Back closes Saved (back on Home), then leaves the app.
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Moments"));
        assertNotNull(byDescription("Build the shot"));
        assertFalse(act.isFinishing());
        act.onBackPressed();
        assertTrue(act.isFinishing());
    }

    @Test public void savedLineShownOnMapSharesItsReminders() throws Exception {
        goHome();
        click(byDescription("Saved"));
        waitFor("Save the line on the map");
        click(find("Save the line on the map"));
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        waitFor("Line 1");
        click(byDescription("Show Line 1 on the map"));
        idle();
        assertNull(findOrNull("Moments"));
        waitForPrefix("% lit");
        // A bell on the sheet is the same reminder as the Saved screen's bell for that moment.
        click(byDescription("Plan this shoot: remind me 30 minutes before"));
        Reminders.Item r = Reminders.all(act).get(0);
        assertTrue(r.text.startsWith("Line 1 · "));
        goHome();
        click(byDescription("Saved"));   // opens on Shoots, because there is one
        waitForPrefix("Sun behind subject");
        assertNotNull(byDescription("Planned. Press to take it off your shoots"));
        // Tapping the shoot opens the viewfinder at its moment.
        click(describedFrom("Shoot "));
        waitFor("Now");
        waitForPrefix("· lined up");
        // The bell there shows it is planned; pressing it takes it off, with Undo.
        click(byDescription("Planned. Press to take it off your shoots"));
        assertEquals(0, Reminders.all(act).size());
        click(find("Undo"));
        assertEquals(1, Reminders.all(act).size());
    }

    /** Plan a frame: make the picture, and the app says where to stand and when. */
    @Test public void planAFrameThenStandThere() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().putBoolean("tutorial_done", true)
                .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                .putString("subject_name", "Big Ben").putFloat("subject_h", 96).putInt("subject_kind", 4)
                .putBoolean("drone", true)   // anywhere will do: the ground under the spot is not this test's business
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15")
                .putString("flow", "3,0,1").apply();
        open();
        waitForPrefix(" of Big Ben");
        shot("17-plan-a-frame");
        // A longer lens keeps the picture and moves you back; the moon can be placed instead of the sun.
        TextView head = (TextView) walk(root(), " of Big Ben", true, false);
        String before = head.getText().toString();
        click(byDescription("Longer lens"));
        waitForPrefix(" of Big Ben");
        assertFalse(before.equals(((TextView) walk(root(), " of Big Ben", true, false)).getText().toString()));
        pick("Light", "Moonrise");
        waitForPrefix(" of Big Ben");
        shot("18-plan-a-frame-moon");
        pick("Light", "Sunset");
        waitForPrefix(" of Big Ben");
        // Back from the height dialog changes nothing; "Fly here" makes the line and opens the viewfinder.
        click(byDescription("Subject height"));
        act.onBackPressed();
        idle();
        assertNotNull(byDescription("Light: Sunset"));
        click(find("Fly here"));
        assertTrue(prefs().contains("you_lat"));
        assertTrue(stepBar().startsWith("Step 3 of 3"));
        waitFor("Open the viewfinder");
        shot("19-plan-a-frame-result");
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 2 of 3"));
        waitForPrefix(" of Big Ben");
    }

    /** A line the sun never crosses says so, shows the closest it comes, and lines it up on request. */
    @Test public void neverLinesUpOffersTheClosest() throws Exception {
        prefs().edit().putString("you_lat", "51.4980").putString("you_lon", "-0.1246").apply();   // due south, looking north
        open();
        waitFor("Line it up");
        waitForPrefix("Misses by ");
        click(byDescription("Pull the sheet up or down"));
        shot("20-never-lines-up");
        click(find("Line it up"));
        waitForPrefix("You moved, so the ");
        assertFalse(Math.abs(Double.parseDouble(prefs().getString("you_lat", "0")) - 51.498) < 1e-6);
        click(find("Undo"));
        assertEquals(51.498, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
    }

    /** The tutorial: Back closes it, like Skip. */
    @Test public void tutorialPicturesAndBack() throws Exception {
        prefs().edit().remove("tutorial_done").apply();
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        waitFor("Skip");
        assertNotNull(find("Line up a street"));
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Skip"));
        assertTrue(prefs().getBoolean("tutorial_done", false));
    }

    // ---- helpers ----

    /** Opens the app again with what is saved, and continues where the user was. */
    private void open() {
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle();
        click(describedFrom("Continue"));
    }

    /** Back until Home shows. */
    private void goHome() {
        for (int i = 0; i < 8 && walk(root(), "Build the shot", false, true) == null; i++) {
            act.onBackPressed();
            idle();
        }
        assertNotNull(byDescription("Build the shot"));
    }

    private SharedPreferences prefs() {
        return act.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
    }

    /** Settings picks are dropdowns: the field names its value, the list opens from the bottom, Back closes it. */
    @Test public void dropdownsReplacePills() throws Exception {
        fresh(null, null, null);
        click(byDescription("Settings"));
        click(byDescription("Theme: System"));
        assertNotNull(find("Dark"));
        shot("17-dropdown");
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Dark"));
        assertNotNull(byDescription("Theme: System"));
        pick("Theme", "Dark");
        assertEquals(2, act.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getInt("theme", 0));
    }

    /** Opens a dropdown by its label and picks a value from its list. */
    private void pick(String label, String value) {
        click(describedFrom(label + ": "));
        click(byDescription(value));
    }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void click(View v) {
        assertNotNull(v);
        v.performClick();
        idle();
    }

    /** A tab is a word with a line under it; press the tab, not the word. */
    private View tab(String name) {
        View v = walk(root(), name, false, true);
        if (v == null) v = walk(root(), name + ", open", false, true);
        if (v == null) v = findOrNull(name);
        assertNotNull("no tab: " + name, v);
        return v;
    }

    /** The topmost view whose description starts with these words. */
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

    private View root() {
        return act.getWindow().getDecorView();
    }

    private void waitFor(String text) throws InterruptedException {
        for (int i = 0; i < 600 && findOrNull(text) == null; i++) {
            Thread.sleep(50);
            idle();
        }
        assertNotNull("never showed: " + text, findOrNull(text));
    }

    private void waitForPrefix(String text) throws InterruptedException {
        for (int i = 0; i < 600 && walk(root(), text, true, false) == null; i++) {
            Thread.sleep(50);
            idle();
        }
        assertNotNull("never showed: " + text, walk(root(), text, true, false));
    }

    private View find(String text) {
        View v = findOrNull(text);
        assertNotNull("not on screen: " + text, v);
        return v;
    }

    private View findOrNull(String text) {
        return walk(root(), text, false, false);
    }

    private View byDescription(String d) {
        View v = walk(root(), d, false, true);
        assertNotNull("no view described as: " + d, v);
        return v;
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
            // Topmost first, so a screen on top wins over the map underneath.
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View r = walk(g.getChildAt(i), s, prefix, description);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void shot(String name) throws Exception {
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
