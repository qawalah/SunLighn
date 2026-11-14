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
 * Presses through the real screens on a computer: the answer card, Where to stand, the trip plan,
 * reminders and Back. Pictures of each step land in app/build/screens/ for a person to look at.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
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

    @Test public void answerCardWhereToStandAndUndo() throws Exception {
        waitFor("Where to stand");
        waitFor("Morning low sun");
        shot("1-answer");
        double before = Astro.distance(YOU_LAT, YOU_LON, SUB_LAT, SUB_LON);

        click(find("Move here")); // the first one is the evening low sun
        waitFor("Undo move");
        SharedPreferences p = prefs();
        double yLat = Double.parseDouble(p.getString("you_lat", "0")), yLon = Double.parseDouble(p.getString("you_lon", "0"));
        // Same distance, and now the line points at the sun.
        assertEquals(before, Astro.distance(yLat, yLon, SUB_LAT, SUB_LON), 2);
        waitForPrefix("Next · ");
        shot("2-moved");

        click(find("Undo move"));
        assertEquals(YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
    }

    @Test public void tripAddLineRemindRemoveAndBack() throws Exception {
        click(byDescription("Trip plan"));
        waitFor("Add the line on the map");
        shot("3-trip-empty");

        click(find("Add the line on the map"));
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        waitFor("Line 1");
        assertNull("the line on the map is now in the trip", findOrNull("Add the line on the map"));
        waitForPrefix("Sun behind subject");
        shot("4-trip-moments");

        // A bell sets a reminder and an alarm.
        View bell = byDescription("Remind me 30 minutes before");
        click(bell);
        assertEquals(1, Reminders.all(act).size());
        AlarmManager am = (AlarmManager) act.getSystemService(Context.ALARM_SERVICE);
        assertNotNull(shadowOf(am).getNextScheduledAlarm());
        assertNotNull(byDescription("Remove the reminder"));
        shot("5-trip-reminder");

        // Removing the line takes its reminder with it; Undo brings both back.
        click(find("Remove"));
        assertEquals(0, Reminders.all(act).size());
        waitForPrefix("Removed Line 1 and its reminder");
        shot("6-trip-removed");
        click(find("Undo"));
        assertEquals(1, Reminders.all(act).size());
        waitFor("Line 1");

        // Back closes the trip, then leaves the app.
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Moments"));
        assertFalse(act.isFinishing());
        act.onBackPressed();
        assertTrue(act.isFinishing());
    }

    @Test public void tripLineShownOnMapSharesItsReminders() throws Exception {
        click(byDescription("Trip plan"));
        waitFor("Add the line on the map");
        click(find("Add the line on the map"));
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        waitForPrefix("Sun behind subject");
        click(byDescription("Show Line 1 on the map"));
        idle();
        assertNull(findOrNull("Moments"));
        waitForPrefix("Next · ");
        // A bell on the answer card is the same reminder as the trip's bell for that moment.
        click(byDescription("Remind me 30 minutes before"));
        Reminders.Item r = Reminders.all(act).get(0);
        assertTrue(r.text.startsWith("Line 1 · "));
        click(byDescription("Trip plan"));
        waitForPrefix("Sun behind subject");
        assertNotNull(byDescription("Remove the reminder"));
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
