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
        Heights.enabled = false;   // level ground unless a test says otherwise: no network in these tests
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
        assertTrue("the cards have a size", byDescription("Line up a street").getHeight() > 0);
        boolean night = act.palette().night;
        assertEquals("the sun's accent is amber", Ui.accentColor(Ui.ACCENT_SUN, night), act.palette().accent);
        int reveals = Reveal.covers;
        click(find("Moon"));
        waitForPrefix("the moon");
        assertEquals("the moon's accent is violet", Ui.accentColor(Ui.ACCENT_MOON, night), act.palette().accent);
        // the colours open in a circle from the finger, then the picture of the old screen goes
        assertEquals(reveals + 1, Reveal.covers);
        Reveal.finishAll();
        idle();
        assertFalse(hasReveal(root()));
        shot("01b-home-moon");
        click(find("Sun"));
        assertEquals(Ui.accentColor(Ui.ACCENT_SUN, night), act.palette().accent);
    }

    /**
     * Each tool is a row: its name and words on the left, its icon on the right; with Continue showing, all three
     * fit the screen without scrolling (owner's sketch, 2026-11-15).
     */
    @Test public void toolCardsAreRowsAndFit() throws Exception {
        goHome();
        assertNotNull(describedFrom("Continue"));
        for (String tool : new String[]{"Line up a street", "Where to stand", "Build the shot"}) {
            android.widget.LinearLayout card = (android.widget.LinearLayout) byDescription(tool);
            assertEquals(tool, android.widget.LinearLayout.HORIZONTAL, card.getOrientation());
            ViewGroup words = (ViewGroup) card.getChildAt(0);
            assertEquals(tool, ((TextView) words.getChildAt(0)).getText().toString());
            View disc = card.getChildAt(1);
            assertTrue(tool + ": icon on the right", disc.getLeft() >= words.getRight());
            assertTrue(tool + ": an icon", ((ViewGroup) disc).getChildAt(0) instanceof android.widget.ImageView);
        }
        int[] at = new int[2];
        View last = byDescription("Build the shot");
        last.getLocationInWindow(at);
        assertTrue("Build the shot ends on screen: " + (at[1] + last.getHeight()), at[1] + last.getHeight() <= 2340);
        shot("01-home");
    }

    /** A fixed accent in Settings stays when the body changes; "Sun and moon" is the default. */
    @Test public void accentSettingSunAndMoon() throws Exception {
        fresh(null, null, null);
        click(byDescription("Settings"));
        assertNotNull(byDescription("Accent: Sun and moon"));
        shot("17b-settings");
        prefs().edit().putInt("accent2", 0).apply();   // Teal, as a pick would save it (the pick restarts the screen)
        act = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle();
        boolean night = act.palette().night;
        assertEquals(Ui.accentColor(0, night), act.palette().accent);
        click(find("Moon"));
        assertEquals("a fixed accent does not follow", Ui.accentColor(0, night), act.palette().accent);
    }

    /** Pictures of the change from the sun to the moon: the reveal halfway, and the tutorial's first card. */
    @Test public void sunToMoonPictures() throws Exception {
        fresh(null, null, null);
        shot("21a-home-sun");
        java.lang.reflect.Field tx = MainActivity.class.getDeclaredField("touchX"), ty = MainActivity.class.getDeclaredField("touchY");
        tx.setAccessible(true);
        ty.setAccessible(true);
        View moon = find("Moon");
        int[] at = new int[2];
        moon.getLocationInWindow(at);
        tx.setFloat(act, at[0] + moon.getWidth() / 2f);
        ty.setFloat(act, at[1] + moon.getHeight() / 2f);
        moon.performClick();                       // no idle: the reveal has only just begun
        View cover = findCover(root());
        assertNotNull(cover);
        java.lang.reflect.Field radius = cover.getClass().getDeclaredField("radius");
        radius.setAccessible(true);
        radius.setFloat(cover, 1000f);
        rawShot("21b-home-reveal-halfway");
        Reveal.finishAll();
        idle();

        // The tutorial's pager stays empty in a picture taken by hand (LESSONS.md), so its first picture is drawn alone.
        Tutorial t = new Tutorial(act, act.palette(), () -> { });
        View art = null;
        for (View v : all(t)) if (v.getClass().getName().endsWith("Tutorial$Art")) { art = v; break; }
        assertNotNull(art);
        java.lang.reflect.Field tf = art.getClass().getDeclaredField("t");
        tf.setAccessible(true);
        float[] times = {0.1f, 0.38f, 0.62f, 1f};
        for (int i = 0; i < times.length; i++) {
            tf.setFloat(art, times[i]);
            art.measure(View.MeasureSpec.makeMeasureSpec(912, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
            art.layout(0, 0, 912, 1000);
            Bitmap b = Bitmap.createBitmap(912, 1000, Bitmap.Config.ARGB_8888);
            b.eraseColor(act.palette().bg);
            art.draw(new Canvas(b));
            save(b, "22" + (char) ('a' + i) + "-tutorial-sun-or-moon");
        }
    }

    private java.util.List<View> all(View v) {
        java.util.List<View> out = new java.util.ArrayList<>();
        out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) out.addAll(all(((ViewGroup) v).getChildAt(i)));
        return out;
    }

    private View findCover(View v) {
        for (View x : all(v)) if (x.getClass().getName().endsWith("Reveal$Cover")) return x;
        return null;
    }

    private void rawShot(String name) throws Exception {
        View r = root();
        r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        r.layout(0, 0, 1080, 2340);
        Bitmap b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888);
        r.draw(new Canvas(b));
        save(b, name);
    }

    private void save(Bitmap b, String name) throws Exception {
        File dir = new File("build/screens");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /** The ground (3.2): with the subject's ground 20 m above yours, Your shot says so and the viewfinder draws a hill. */
    @Test public void groundHeightOnYourShotAndInTheViewfinder() throws Exception {
        prefs().edit().putFloat("el_" + Math.round(YOU_LAT * 1e4) + "_" + Math.round(YOU_LON * 1e4), 5f)
                .putFloat("el_" + Math.round(SUB_LAT * 1e4) + "_" + Math.round(SUB_LON * 1e4), 25f).apply();
        waitForPrefix("Sun behind subject");
        click(find("Use the soonest"));
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        assertNotNull("Your shot names the difference of ground", walk(root(), "20 m lower than the subject's ground", true, false));
        click(find("Open the viewfinder"));
        shot("24-viewfinder-subject-on-higher-ground");
    }

    private boolean hasReveal(View v) {
        if (v.getClass().getName().endsWith("Reveal$Cover")) return true;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (hasReveal(((ViewGroup) v).getChildAt(i))) return true;
        }
        return false;
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

    /** The moon chosen on Home: the When step shows only moon answers, and the Day chart only the moon. */
    @Test public void lineUpAStreetMoonOnly() throws Exception {
        fresh(YOU, SUB, "1,1,2");
        click(describedFrom("Continue"));
        waitForPrefix("Moon behind Big Ben");
        assertNull(walk(root(), "Sun behind", true, false));
        assertNull(findOrNull("Sun"));          // no sun section heading
        shot("04-when-moon");
        click(tab("Day"));
        waitForPrefix("Purple is the moon");
        shot("04b-when-day-moon");
    }

    /** Switching the body inside a tool recomputes the answers and keeps the step. */
    @Test public void switchBodyInsideATool() throws Exception {
        fresh(YOU, SUB, "1,1,2");
        click(describedFrom("Continue"));
        waitForPrefix("Moon behind Big Ben");
        click(byDescription("Sun or moon: Moon"));
        click(byDescription("Sun"));
        waitForPrefix("Sun behind Big Ben");
        assertNull(walk(root(), "Moon behind", true, false));
        assertTrue(stepBar().startsWith("Step 3 of 4"));
    }

    /** "Use the soonest" goes to Your shot at that moment; a time row does the same for its moment. */
    @Test public void useTheSoonestGoesToYourShot() throws Exception {
        waitForPrefix("Sun behind subject");
        click(find("Use the soonest"));
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        assertTrue(prefs().getLong("shot_ms", 0) > System.currentTimeMillis());
        waitFor("Open the viewfinder");
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 3 of 4"));
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
        assertNull(walk(root(), "Moon behind", true, false));
        shot("2-day");
        click(tab("Times"));

        // The Frame: full screen, at the moment of the answer, with the lens keys and the jumps.
        click(find("Use the soonest"));
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
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 3 of 4"));
        assertFalse(act.isFinishing());
    }

    /** With a height, the subject is drawn to scale; the dark theme and a long lens. */
    @Test public void frameWithSubjectHeightAtNight() throws Exception {
        prefs().edit().putFloat("subject_h", 96).putInt("subject_kind", 4).putInt("theme", 2)
                .putFloat("focal_mm", 85).apply();
        open();
        waitForPrefix("Sun behind subject");
        shot("5-answer-dark");
        click(find("Use the soonest"));
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

    /** How you want it shows the shot as a small picture, and it follows the settings (3.3). */
    @Test public void criteriaShowsTheShot() throws Exception {
        fresh(null, SUB, "2,0,1");
        click(describedFrom("Continue"));
        FrameView pic = (FrameView) byDescription("How it will look");
        float k = act.getResources().getDisplayMetrics().density;
        assertEquals(120 * k, pic.getHeight(), 2);
        assertFalse("a picture to look at, not to touch", pic.isClickable());
        double onTop = pic.bodyAlt();
        pick("Where the sun sits", "On the horizon");
        pic = (FrameView) byDescription("How it will look");
        assertTrue("lower on the horizon: " + pic.bodyAlt() + " < " + onTop, pic.bodyAlt() < onTop);
        pick("Where the sun sits", "Just on top");
    }

    /** Your shot shows the shot as a picture; a tap opens the viewfinder. Also for the moon and Behind you. */
    @Test public void yourShotShowsTheShot() throws Exception {
        waitForPrefix(" behind subject");
        click(find("Use the soonest"));
        View pic = byDescription("Your shot as a picture");
        assertTrue(pic.isClickable());
        shot("07b-your-shot-picture");
        click(pic);
        assertNotNull(ofType(root(), FrameScreen.class));
        act.onBackPressed();
        idle();
        // The moon at night, the body behind you: it still draws.
        prefs().edit().putInt("mode", 1).apply();
        Bitmap b = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        byDescription("Your shot as a picture").draw(new Canvas(b));
    }

    /** From a line: "Where to stand" keeps the subject and goes to that tool, at its criteria; Back steps back. */
    @Test public void whereToStandFromALine() throws Exception {
        waitFor("Where to stand");
        click(find("Where to stand"));
        assertTrue(stepBar().startsWith("Step 2 of 4"));
        waitFor("Find spots");
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        assertTrue(prefs().contains("subject_lat"));
    }

    /** Where to stand: say how you want it, find the spots, and "Fly here" puts You there and shows the shot. */
    @Test public void whereToStandCriteriaThenShot() throws Exception {
        fresh(null, SUB, "2,0,1");
        click(describedFrom("Continue"));
        assertTrue(stepBar().startsWith("Step 2 of 4"));
        assertNotNull(byDescription("Where the sun sits: Just on top"));
        assertNotNull(describedFrom("Subject: Building"));
        waitForPrefix("to fill a third of the picture");
        shot("05-criteria");
        pick("Light", "Sunrise");
        assertNotNull(byDescription("Light: Sunrise"));
        pick("Light", "Sunset");
        pick("Where the sun sits", "On the horizon");
        assertEquals(0, prefs().getInt("stand_place", 2));
        // A drone can be anywhere, so the spot is exactly as far as asked (on foot it may move off the river).
        click(find("Drone"));
        click(find("Find spots"));
        assertTrue(stepBar().startsWith("Step 3 of 4"));
        waitFor("Fly here");
        waitForPrefix("Fly 300 m ");
        shot("06-spots");
        assertNotNull(find("Puts you on this spot and shows your shot."));
        click(find("Fly here"));
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        SharedPreferences p = prefs();
        double yLat = Double.parseDouble(p.getString("you_lat", "0")), yLon = Double.parseDouble(p.getString("you_lon", "0"));
        assertEquals(300, Astro.distance(yLat, yLon, SUB_LAT, SUB_LON), 3);
        waitFor("Open the viewfinder");
        shot("07-your-shot");
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 3 of 4"));
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 2 of 4"));
        assertNotNull(byDescription("Where the sun sits: On the horizon"));
    }

    /** The moon has one more criterion: how full it must be. */
    @Test public void whereToStandMoonHowFull() throws Exception {
        fresh(null, SUB, "2,1,1");
        click(describedFrom("Continue"));
        assertNotNull(byDescription("Light: Moonset"));
        assertNotNull(byDescription("How full: Half or more"));
        pick("How full", "Any");
        assertEquals(0, prefs().getInt("moon_full", 1));
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

    /** No network here: saving must still work at once, under a number name. */
    private void offline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(cm).setActiveNetworkInfo(null);
    }

    /** Your shot: Save in one press (named later), Remind me, Directions; Saved lists it and opens it again. */
    @Test public void yourShotSaveRemindDirections() throws Exception {
        offline();
        waitForPrefix("Sun behind subject");
        click(find("Use the soonest"));
        click(byDescription("Save this shot"));
        waitForPrefix("Saved as Shot 1");
        shot("08-your-shot-saved");
        assertEquals(1, Trip.loadLines(act).size());
        assertEquals(prefs().getLong("shot_ms", 0), Trip.loadLines(act).get(0).timeMs);
        assertNotNull(byDescription("Saved as Shot 1"));          // the key says it is saved; a second press saves nothing new
        click(byDescription("Saved as Shot 1"));
        assertEquals(1, Trip.loadLines(act).size());

        click(byDescription("Remind me 30 minutes before"));
        assertEquals(1, Reminders.all(act).size());
        assertNotNull(byDescription("Reminder set. Press to take it off"));

        click(byDescription("Directions to your spot"));
        android.content.Intent go = shadowOf(act).getNextStartedActivity();
        assertNotNull(go);
        assertTrue(go.getData().toString().startsWith("geo:"));

        goHome();
        click(byDescription("Saved"));
        waitFor("Shot 1");
        shot("09-saved-shots");
        assertNotNull(byDescription("Reminder set. Press to take it off"));
        click(describedFrom("Open Shot 1"));
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        act.onBackPressed();                               // back to Saved, where it was opened (audit 3.2.2)
        idle();
        assertNotNull(describedFrom("Open Shot 1"));
        act.onBackPressed();
        idle();
        assertNotNull(byDescription("Build the shot"));    // and from Saved, Home
    }

    /** The viewfinder: Move opens a small map to drag where you stand; Back closes it first; Save saves the shot. */
    @Test public void viewfinderMoveAndSave() throws Exception {
        offline();
        waitForPrefix("Sun behind subject");
        click(find("Use the soonest"));
        click(byDescription("Viewfinder: see the shot as the camera will"));
        click(byDescription("Move: drag where you stand"));
        assertNotNull(byDescription("Map: drag You, or tap where you will stand"));
        // Present is not enough: the sheet has a shadow (elevation), and Android draws and touches a higher view on
        // top whatever the order. The Move map was hidden under it from 3.1.1 (Ali's test, 2026-11-15).
        View panel = (View) byDescription("Map: drag You, or tap where you will stand").getParent();
        ViewGroup over = (ViewGroup) panel.getParent();
        for (int i = 0; i < over.getChildCount(); i++)
            if (over.getChildAt(i) != panel) assertTrue("the Move map is on top", panel.getZ() > over.getChildAt(i).getZ());
        shot("10-viewfinder-move");
        FrameScreen f = (FrameScreen) ofType(root(), FrameScreen.class);
        long before = f.time();
        f.moveTo(YOU_LAT + 0.001, YOU_LON);
        idle();
        assertEquals(YOU_LAT + 0.001, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        // The answer is worked out again for the new spot and the scene goes to it (Ali, 2026-11-15)
        waitForPrefix("You moved");
        assertTrue("the viewfinder shows the new spot's moment", f.time() != before);
        act.onBackPressed();
        idle();
        assertNull(walk(root(), "Map: drag You, or tap where you will stand", false, true));
        assertNotNull(ofType(root(), FrameScreen.class));       // the viewfinder is still open
        click(byDescription("Save this shot"));
        waitForPrefix("Saved as Shot 1");
        act.onBackPressed();
        idle();
        assertNull(ofType(root(), FrameScreen.class));
        assertTrue(stepBar().startsWith("Step 4 of 4"));
    }

    // ---- fixes from the whole-branch review ----

    /** Review 1: switching the body on Your shot goes back a step, so no sun moment is shown as a moon shot. */
    @Test public void switchBodyOnYourShotStepsBack() throws Exception {
        waitForPrefix("Sun behind subject");
        click(find("Use the soonest"));
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        click(byDescription("Sun or moon: Sun"));
        click(byDescription("Moon"));
        assertTrue(stepBar().startsWith("Step 3 of 4"));
    }

    /** Review 2: a moon moment opened from the trip opens as a moon shot. */
    @Test public void tripMomentKeepsItsBody() throws Exception {
        Trip.Line l = new Trip.Line("x", "Line", YOU_LAT, YOU_LON, SUB_LAT, SUB_LON, 0);
        act.showMoment(l, System.currentTimeMillis() + 86400000L, true);
        idle();
        assertTrue(byDescription("Sun or moon: Moon") != null);
    }

    /** Review 3: once You moves, the shot on screen is not the saved one any more. */
    @Test public void movingYouUnsavesTheShot() throws Exception {
        offline();
        waitForPrefix("Sun behind subject");
        click(find("Use the soonest"));
        click(byDescription("Save this shot"));
        assertNotNull(describedFrom("Saved as "));
        // You moved by a way that does not go through the map's tap (a drag, Line it up, a new spot): simulated
        // by changing where You is saved and opening the screen again.
        prefs().edit().putString("you_lat", "" + (YOU_LAT + 0.002)).apply();
        ctl.recreate();
        act = ctl.get();
        idle();
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        assertNotNull(byDescription("Save this shot"));
    }

    /** Review 5: Back from Your shot to Build it keeps the picture as it was left. */
    @Test public void buildItKeepsThePicture() throws Exception {
        fresh(null, SUB, "3,0,1");
        prefs().edit().putBoolean("drone", true).apply();
        open();
        waitForPrefix(" of Big Ben");
        pick("Light", "Sunrise");
        waitFor("Fly here");
        waitForPrefix(" of Big Ben");
        click(find("Fly here"));
        assertTrue(stepBar().startsWith("Step 3 of 3"));
        act.onBackPressed();
        idle();
        assertNotNull(byDescription("Light: Sunrise"));
    }

    /** Review 8: starting Where to stand sets your spot aside; back on Home it is back. */
    @Test public void whereToStandSetsYouAsideNotAway() throws Exception {
        goHome();
        click(byDescription("Where to stand"));
        assertFalse(prefs().contains("you_lat"));
        goHome();
        assertEquals(YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
    }

    /** Review 9: the phone switching to dark mode mid-tool restarts the screen on the same step, not on Home. */
    @Test public void restartMidToolKeepsTheStep() throws Exception {
        waitForPrefix("Sun behind subject");
        ctl.recreate();
        act = ctl.get();
        idle();
        assertTrue(stepBar().startsWith("Step 3 of 4"));
    }

    /** Saved: empty, then a shot saved on Your shot, its moments in the trip, plan one, remove it with Undo, Back. */
    @Test public void savedShotsTripRemoveAndBack() throws Exception {
        offline();
        goHome();
        click(byDescription("Saved"));
        waitForPrefix("Nothing saved yet");               // one way to save: Save on Your shot (audit 3.2.2)
        shot("12-saved-empty");
        act.onBackPressed();
        idle();
        click(byDescription("Line up a street"));         // the points are kept: Next, Next, then When
        click(find("Next"));
        click(find("Next"));
        waitForPrefix(" behind subject");
        click(find("Use the soonest"));
        click(byDescription("Save this shot"));
        goHome();
        click(byDescription("Saved"));
        waitFor("Shot 1");
        click(tab("Trip"));
        waitForPrefix(" behind subject");
        shot("13-saved-trip");

        // A bell sets a reminder and an alarm.
        click(byDescription("Plan this shoot: remind me 30 minutes before"));
        assertEquals(1, Reminders.all(act).size());
        AlarmManager am = (AlarmManager) act.getSystemService(Context.ALARM_SERVICE);
        assertNotNull(shadowOf(am).getNextScheduledAlarm());

        // Removing the shot takes its reminder with it; Undo brings both back.
        click(tab("Shots"));
        click(byDescription("Remove Shot 1"));
        assertEquals(0, Reminders.all(act).size());
        waitForPrefix("Removed Shot 1 and its reminder");
        click(find("Undo"));
        assertEquals(1, Reminders.all(act).size());
        waitFor("Shot 1");

        // Back closes Saved (back on Home), then leaves the app.
        act.onBackPressed();
        idle();
        assertNotNull(byDescription("Build the shot"));
        assertFalse(act.isFinishing());
        act.onBackPressed();
        assertTrue(act.isFinishing());
    }

    /** A finger on the glass of a view: down, moves, up (the clock moves on with it). */
    private void drag(View v, float x1, float y1, float x2, float y2) {
        long t0 = android.os.SystemClock.uptimeMillis();
        v.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, x1, y1, 0));
        for (int i = 1; i <= 10; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));
            v.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, android.os.SystemClock.uptimeMillis(),
                    android.view.MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * i / 10f, y1 + (y2 - y1) * i / 10f, 0));
        }
        v.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, android.os.SystemClock.uptimeMillis(),
                android.view.MotionEvent.ACTION_UP, x2, y2, 0));
        idle();
    }

    /** Build the shot: a drag off the sun frames the picture, a drag on it moves it, Centre puts it back (3.3). */
    @Test public void buildTheShotFramesByDrag() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().putBoolean("tutorial_done", true)
                .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                .putString("subject_name", "Big Ben").putFloat("subject_h", 96).putInt("subject_kind", 4)
                .putBoolean("drone", true)
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15")
                .putString("flow", "3,0,1").apply();
        open();
        waitForPrefix(" of Big Ben");
        ComposeScreen cs = (ComposeScreen) ofType(root(), ComposeScreen.class);
        FrameView fv = (FrameView) ofType(cs, FrameView.class);
        assertEquals(0, cs.panDeg(), 1e-9);
        assertTrue(Double.isNaN(cs.framePitch()));
        assertNull("no Centre while centred", walk(root(), "Centre: put the subject back in the middle", false, true));
        float[] body = fv.bodyOnScreen();
        assertNotNull(body);
        double[] bodyBefore = cs.bodyPlace();

        // Off the sun: the picture swings and tilts; the sun stays where it is on the subject.
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0] - 150 * k, body[1] + 120 * k, body[0] - 60 * k, body[1] + 60 * k);
        assertTrue("swung", Math.abs(cs.panDeg()) > 0.5);
        assertFalse("tilted", Double.isNaN(cs.framePitch()));
        assertEquals(bodyBefore[0], cs.bodyPlace()[0], 1e-6);
        assertEquals(bodyBefore[1], cs.bodyPlace()[1], 1e-6);
        assertNotNull(byDescription("Centre: put the subject back in the middle"));
        shot("17c-build-the-shot-framed");

        // On the sun: the sun moves, the framing does not.
        double pan = cs.panDeg();
        body = fv.bodyOnScreen();
        drag(fv, body[0], body[1], body[0] + 40 * k, body[1] - 30 * k);
        assertEquals(pan, cs.panDeg(), 1e-9);
        assertTrue("the sun moved", Math.abs(cs.bodyPlace()[0] - bodyBefore[0]) > 0.2);

        // Never so far that the subject leaves the picture.
        body = fv.bodyOnScreen();
        drag(fv, 20 * k, body[1] + 140 * k, fv.getWidth() - 20 * k, body[1] + 140 * k);
        drag(fv, 20 * k, body[1] + 140 * k, fv.getWidth() - 20 * k, body[1] + 140 * k);
        assertTrue("subject in frame", Math.abs(cs.panDeg()) <= cs.halfWidthDeg() - 2 + 1e-6);   // at least 2 degrees in
        body = fv.bodyOnScreen();
        float away = body[0] < fv.getWidth() / 2f ? fv.getWidth() - 30 * k : 30 * k;     // a big tilt, off the sun
        drag(fv, away, 300 * k, away, 20 * k);
        float[] q = fv.bodyOnScreen();
        assertNotNull(q);
        android.graphics.RectF f = fv.frameRect();
        assertTrue("the sun stays in the picture: " + q[0] + "," + q[1] + " in " + f, f.contains(q[0], q[1]));

        // Centre: back to the middle at the usual tilt.
        click(byDescription("Centre: put the subject back in the middle"));
        assertEquals(0, cs.panDeg(), 1e-9);
        assertTrue(Double.isNaN(cs.framePitch()));
    }

    /** The framing made in Build the shot is the viewfinder's, and is kept with the saved shot (3.3). */
    @Test public void framingCarriesToTheViewfinder() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().putBoolean("tutorial_done", true)
                .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                .putString("subject_name", "Big Ben").putFloat("subject_h", 96).putInt("subject_kind", 4)
                .putBoolean("drone", true)
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15")
                .putString("flow", "3,0,1").apply();
        open();
        waitForPrefix(" of Big Ben");
        ComposeScreen cs = (ComposeScreen) ofType(root(), ComposeScreen.class);
        FrameView fv = (FrameView) ofType(cs, FrameView.class);
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0] - 150 * k, body[1] + 120 * k, body[0] - 90 * k, body[1] + 100 * k);
        double pan = cs.panDeg(), pitch = cs.framePitch();
        assertTrue(Math.abs(pan) > 0.5);
        click(find("Fly here"));
        click(byDescription("Viewfinder: see the shot as the camera will"));
        FrameScreen f = (FrameScreen) ofType(root(), FrameScreen.class);
        FrameView view = (FrameView) ofType(f, FrameView.class);
        double you0 = Double.parseDouble(prefs().getString("you_lat", "0")), you1 = Double.parseDouble(prefs().getString("you_lon", "0"));
        double subjectAz = Astro.bearing(you0, you1, SUB_LAT, SUB_LON);
        assertEquals("aimed as framed", 0, FrameMath.diff(view.aimAz(), Astro.norm360(subjectAz + pan)), 0.05);
        assertEquals("tilted as framed", pitch, view.aimPitch(), 0.05);
        // Saved with the shot: opened again from Saved it is framed the same.
        click(byDescription("Save this shot"));
        Trip.Line saved = Trip.loadLines(act).get(0);
        assertEquals(pan, saved.panDeg, 1e-6);
        assertEquals(pitch, saved.pitch, 1e-6);
        f.aimAgain();                                     // what a double tap does: centred again
        assertEquals(0, FrameMath.diff(view.aimAz(), subjectAz), 0.05);
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
        // In London in November the sun never climbs past about 20 degrees: higher is shaded and says why.
        waitForDescribed("Impossible: The sun is never that high at sunset");
        shot("17-build-the-shot");
        // A longer lens keeps the picture and moves you back; the moon can be placed instead of the sun.
        TextView head = (TextView) walk(root(), " of Big Ben", true, false);
        String before = head.getText().toString();
        click(byDescription("Longer lens"));
        waitForPrefix(" of Big Ben");
        assertFalse(before.equals(((TextView) walk(root(), " of Big Ben", true, false)).getText().toString()));
        pick("Light", "Sunrise");
        waitForPrefix(" of Big Ben");
        pick("Look in", "Next year");
        waitForPrefix(" of Big Ben");
        shot("18-build-the-shot-sunrise-year");
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
        for (int i = 0; i < 5; i++) idle();
        // the owner saw empty pages on the phone: every card must have the width of the screen and its words a size
        View title = find("Sun or moon");
        assertTrue("the first card's title has a size: " + title.getWidth(), title.getWidth() > 0 && title.getHeight() > 0);
        assertEquals("a card is as wide as the screen", root().getWidth(), ((View) title.getParent()).getWidth());
        int[] at = new int[2];
        title.getLocationOnScreen(at);
        assertTrue("the first card is on screen: x=" + at[0], at[0] >= 0 && at[0] < root().getWidth());
        shot("23-tutorial-first-card");
        assertNotNull(find("Sun or moon"));
        assertNotNull(find("Line up a street"));
        assertNotNull(find("Build the shot"));
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Skip"));
        assertTrue(prefs().getBoolean("tutorial_done", false));
    }

    // ---- helpers ----

    private void waitForDescribed(String start) throws InterruptedException {
        for (int i = 0; i < 600 && describedFrom(root(), start) == null; i++) {
            Thread.sleep(50);
            idle();
        }
        assertNotNull("never described: " + start, describedFrom(root(), start));
    }

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
        Reveal.finishAll();   // the picture of the finished screen, not the change
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
