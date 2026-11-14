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
 * search, Saved, reminders and Back. Pictures of each step land in app/build/screens/ for a person to look at.
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
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15").apply();
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        act = ctl.get();
    }

    /** The line is drawn: the sheet leads with the soonest moment; Times, Day and the Frame all open; Back steps out. */
    @Test public void answerSheetDayAndFrame() throws Exception {
        waitFor("Times");
        waitForPrefix("Sun behind subject");
        waitForPrefix("% lit");
        shot("1-answer");
        assertNotNull(find("Behind subject"));
        assertNotNull(find("Pick a date"));

        click(tab("Day"));
        waitForPrefix("Drag along the day");
        shot("2-day");
        click(tab("Times"));

        // The Frame: full screen, at the moment of the answer, with the lens keys and the jumps.
        click(byDescription("Frame: see it as the camera will"));
        waitFor("Sun on the line");
        waitForPrefix("35 mm");
        shot("3-frame");
        click(byDescription("Longer lens"));
        waitForPrefix("50 mm");
        click(byDescription("Turn the camera: landscape or portrait"));
        click(find("Sun on the line"));
        waitForPrefix("on your line");
        shot("4-frame-portrait-50mm");
        assertEquals(50f, prefs().getFloat("focal_mm", 0), 0.01);

        // Back closes the Frame, then leaves the app. Nothing is lost: all is saved as you go.
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Sun on the line"));
        assertFalse(act.isFinishing());
        act.onBackPressed();
        assertTrue(act.isFinishing());
    }

    /** With a height, the subject is drawn to scale; the dark theme and a long lens. */
    @Test public void frameWithSubjectHeightAtNight() throws Exception {
        prefs().edit().putFloat("subject_h", 96).putInt("subject_kind", 4).putInt("theme", 2)
                .putFloat("focal_mm", 85).apply();
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        waitForPrefix("Sun behind subject");
        waitForPrefix("% lit");
        shot("5-answer-dark");
        click(byDescription("Frame: see it as the camera will"));
        waitFor("Moon on the line");
        shot("6-frame-height");
        click(find("Moon on the line"));
        shot("7-frame-moon");
        // The height dialog: Back closes it and nothing changes.
        click(byDescription("Subject height"));
        assertNotNull(ShadowDialog.getLatestDialog());
        assertTrue(ShadowDialog.getLatestDialog().isShowing());
        act.onBackPressed();
        idle();
        assertFalse(ShadowDialog.getLatestDialog().isShowing());
        assertNotNull(findOrNull("Moon on the line"));
        assertEquals(96f, prefs().getFloat("subject_h", 0), 0.01);
    }

    /** From a line: keep the subject and see where to stand instead; Undo puts You back. */
    @Test public void whereToStandFromALineAndUndo() throws Exception {
        waitFor("Where to stand");
        click(find("Where to stand"));
        waitFor("Stand here");
        assertFalse(prefs().contains("you_lat"));
        click(find("Undo"));
        assertEquals(YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        waitFor("Times");
    }

    /** Start from the subject only: the sheet says where to stand and when; "Stand here" makes the line and opens the Frame. */
    @Test public void pickTheSubjectFirstThenStandHere() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        // Nothing chosen yet: a tap places the subject, unless you say you start from where you stand.
        app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().putBoolean("tutorial_done", true)
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15").apply();
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        waitFor("Tap your subject");
        shot("8-start");
        assertNull(findOrNull("Use my location"));
        click(find("Where I stand first"));
        waitFor("Tap where you will stand");
        assertNotNull(find("Use my location"));
        click(find("Subject first"));
        waitFor("Tap your subject");

        // With only the subject saved, the sheet shows lights to shoot in and where to stand for each.
        app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().putBoolean("tutorial_done", true)
                .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                .putString("subject_name", "Big Ben")
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15").apply();
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        waitFor("Stand here");
        waitForPrefix("Stand 300 m ");
        shot("9-where-to-stand");

        click(find("Moonrise"));
        waitForPrefix("% lit");
        click(find("Evening sun"));
        waitForPrefix("Stand 300 m ");

        click(find("Stand here"));
        waitFor("Now");
        SharedPreferences p = prefs();
        double yLat = Double.parseDouble(p.getString("you_lat", "0")), yLon = Double.parseDouble(p.getString("you_lon", "0"));
        assertEquals(300, Astro.distance(yLat, yLon, SUB_LAT, SUB_LON), 3);
        // The Frame opened at the moment the plan was for: the sun is on the line there.
        waitForPrefix("on your line");
        shot("10-stood-frame");
        act.onBackPressed();
        idle();
        waitFor("Times");
    }

    /** Search: results set the subject; an empty answer and no connection each say what to do next. */
    @Test public void searchSetsTheSubject() throws Exception {
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
        waitFor("Tap your subject");
        assertFalse(prefs().contains("you_lat"));
        click(find("Undo"));
        assertEquals(YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        waitFor("Times");
    }

    @Test public void savedLineRemindRemoveAndBack() throws Exception {
        click(byDescription("Saved lines and trip"));
        waitFor("Save the line on the map");
        shot("12-saved-empty");

        click(find("Save the line on the map"));
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        waitFor("Line 1");
        assertNull("the line on the map is now saved", findOrNull("Save the line on the map"));
        waitForPrefix("Sun behind subject");
        shot("13-saved-moments");

        // A bell sets a reminder and an alarm.
        View bell = byDescription("Remind me 30 minutes before");
        click(bell);
        assertEquals(1, Reminders.all(act).size());
        AlarmManager am = (AlarmManager) act.getSystemService(Context.ALARM_SERVICE);
        assertNotNull(shadowOf(am).getNextScheduledAlarm());
        assertNotNull(byDescription("Remove the reminder"));

        // Removing the line takes its reminder with it; Undo brings both back.
        click(byDescription("Remove Line 1"));
        assertEquals(0, Reminders.all(act).size());
        waitForPrefix("Removed Line 1 and its reminder");
        shot("14-saved-removed");
        click(find("Undo"));
        assertEquals(1, Reminders.all(act).size());
        waitFor("Line 1");

        // Back closes Saved, then leaves the app.
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Moments"));
        assertFalse(act.isFinishing());
        act.onBackPressed();
        assertTrue(act.isFinishing());
    }

    @Test public void savedLineShownOnMapSharesItsReminders() throws Exception {
        click(byDescription("Saved lines and trip"));
        waitFor("Save the line on the map");
        click(find("Save the line on the map"));
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        waitForPrefix("Sun behind subject");
        click(byDescription("Show Line 1 on the map"));
        idle();
        assertNull(findOrNull("Moments"));
        waitForPrefix("% lit");
        // A bell on the sheet is the same reminder as the Saved screen's bell for that moment.
        click(byDescription("Remind me 30 minutes before"));
        Reminders.Item r = Reminders.all(act).get(0);
        assertTrue(r.text.startsWith("Line 1 · "));
        click(byDescription("Saved lines and trip"));
        waitForPrefix("Sun behind subject");
        assertNotNull(byDescription("Remove the reminder"));
    }

    /** The tutorial: Back closes it, like Skip. */
    @Test public void tutorialPicturesAndBack() throws Exception {
        prefs().edit().remove("tutorial_done").apply();
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        waitFor("Pick your subject");
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Skip"));
        assertTrue(prefs().getBoolean("tutorial_done", false));
    }

    // ---- helpers ----

    private SharedPreferences prefs() {
        return act.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
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
