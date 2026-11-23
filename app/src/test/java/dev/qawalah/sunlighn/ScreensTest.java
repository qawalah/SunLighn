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
import android.widget.SeekBar;
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
 * Presses through the real screens on a computer: the answer sheet, the Day tab, the Frame, Build the shot,
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
        Buildings.enabled = false; // no buildings either (3.14)
        GeoMap.libreAllowed = false; // the flat map: MapLibre's engine is native code (3.15)
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences p = app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        p.edit().clear().putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true).putBoolean("guide_find", true).putBoolean("guide_choose", true).putBoolean("guide_map", true).putBoolean("guide_now", true).putBoolean("guide_shot", true).putBoolean("guide_frame", true)
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
                .edit().clear().putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true).putBoolean("guide_find", true).putBoolean("guide_choose", true).putBoolean("guide_map", true).putBoolean("guide_now", true).putBoolean("guide_shot", true).putBoolean("guide_frame", true)
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

    /** 3.7: the designs (Classic, Pocket map, Readout; Appliance since 3.19), day and night: Home, When and Your shot. */
    @Test public void designs() throws Exception {
        String[] names = {"classic", "pocket", "readout", "appliance"};
        for (int d = 0; d < names.length; d++) {
            for (int theme = 1; theme <= 2; theme++) {
                RuntimeEnvironment.getApplication().getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear()
                        .putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true).putBoolean("guide_find", true).putBoolean("guide_choose", true).putBoolean("guide_map", true).putBoolean("guide_now", true).putBoolean("guide_shot", true).putBoolean("guide_frame", true).putInt("design", d).putInt("theme", theme)
                        .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15")
                        .putString("you_lat", "" + YOU_LAT).putString("you_lon", "" + YOU_LON)
                        .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                        .putString("subject_name", "Big Ben").putString("flow", "1,0,2").apply();
                ctl = Robolectric.buildActivity(MainActivity.class).setup();
                act = ctl.get();
                idle();
                String tag = "30-" + names[d] + (theme == 2 ? "-night" : "-day");
                shot(tag + "-1-home");
                click(byDescription("Moon"));
                idle();
                shot(tag + "-1b-home-moon");
                click(byDescription("Sun"));
                idle();
                click(describedFrom("Continue"));
                waitForPrefix("Sun at ");               // the answer, not "Working it out"
                shot(tag + "-3-when");
                ((Sheet) ofType(root(), Sheet.class)).setStop(Sheet.FULL, false);
                idle();
                shot(tag + "-3b-when-full");
                for (View v : all(root())) {                 // 3.9: a run of days opens to its days
                    CharSequence desc = v.getContentDescription();
                    if (desc != null && desc.toString().endsWith("Show the days") && v.isShown()) {
                        v.performClick();
                        idle();
                        shot(tag + "-3e-run-open");
                        break;
                    }
                }
                ((Sheet) ofType(root(), Sheet.class)).setStop(Sheet.HALF, false);
                idle();
                click(tab("Day chart"));
                shot(tag + "-3c-day-chart");
                click(tab("All times"));
                click(byDescription("Map look"));
                shot(tag + "-3d-map-look");
                act.onBackPressed();
                idle();
                click(seeIt());
                idle();
                shot(tag + "-5-viewfinder");
                closeFrameIfOpen();
                waitFor("Open the viewfinder");
                shot(tag + "-4-your-shot");
                act.onBackPressed();
                idle();
                act.onBackPressed();
                idle();
                shot(tag + "-2-subject");
                ctl.pause().stop().destroy();
            }
        }
    }

    /**
     * 3.12 (owner): Line up a street says how high the sun should be; the times follow the choice, Any height shows all.
     * 3.24 (owner: "a line, not a subject; the sun height as a slider"): the pictures of where it sits went.
     */
    @Test public void lineUpWhereTheSunSits() throws Exception {
        waitForPrefix("Sun at ");
        sheetTo(Sheet.FULL);
        assertNull("no pictures of a subject's top", findOrNull("On top"));
        assertNotNull(find("Any height"));
        shot("31-line-up-any-height");
        // 3.19.4: the slider says what its ticks and red mean (the heights the sun really lines up at), never silent
        assertTrue("the slider explains its shading",
                walk(root(), "Shaded:", true, false) != null || walk(root(), "No time in the coming year", true, false) != null);
        // 3.18: the sun's height picked as a range on the slider (two thumbs); no picture lit
        RangeBar rb = (RangeBar) byDescription("Sun height range");
        rb.commit(2.0, 5.0);
        idle();
        assertEquals(4, prefs().getInt("line_sits", 0));
        assertEquals(2f, prefs().getFloat("line_lo", 0), 0.01);
        assertEquals(5f, prefs().getFloat("line_hi", 0), 0.01);
        assertNotNull(walk(root(), "2.0° to 5.0° up", true, false));
        shot("31c-line-up-angle");
        click(byDescription("Any height: every time it lines up"));
        assertEquals(0, prefs().getInt("line_sits", 0));
        assertNotNull(walk(root(), "Sun at ", true, false));

        // 3.19.3: a real finger, not commit(). The sheet is rebuilt after the lift, never inside the slider's own
        // touch (it removes the slider; the next layout pass then met a null child and the app crashed).
        RangeBar live = (RangeBar) byDescription("Sun height range");
        long t = android.os.SystemClock.uptimeMillis();
        float y = live.getHeight() / 2f;
        // 3.25: a press off a thumb moves nothing, so the finger lands on the high thumb (Any height: at the right end)
        float onThumb = live.getWidth() - 14 * act.getResources().getDisplayMetrics().density;
        live.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN,
                onThumb, y, 0));
        live.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_UP,
                onThumb, y, 0));
        assertNotNull("the slider is still in place right after the lift", live.getParent());
        idle();
        assertTrue("then the sheet is rebuilt", live != byDescription("Sun height range"));
    }

    /** The app opens on Home: two tools, simplest first (3.6: Where to stand is part of Build the shot). */
    @Test public void opensOnHome() throws Exception {
        fresh(null, null, null);
        assertNotNull(byDescription("Line up a street"));
        assertNull("Where to stand is part of Build the shot", walk(root(), "Where to stand", false, true));
        assertNotNull(byDescription("Build the shot"));
        assertNull(findOrNull("Continue"));
        shot("01-home");
        assertTrue("the cards have a size", byDescription("Line up a street").getHeight() > 0);
        boolean night = act.palette().night;
        int design = act.palette().design;   // 3.7: the design's own sun and moon
        assertEquals("the sun's accent is amber", Ui.accentColor(Ui.ACCENT_FOLLOW, false, night, design), act.palette().accent);
        int reveals = Reveal.covers;
        click(find("Moon"));
        waitForPrefix("the moon");
        assertEquals("the moon's accent is violet", Ui.accentColor(Ui.ACCENT_FOLLOW, true, night, design), act.palette().accent);
        // the colours open in a circle from the finger, then the picture of the old screen goes
        assertEquals(reveals + 1, Reveal.covers);
        Reveal.finishAll();
        idle();
        assertFalse(hasReveal(root()));
        shot("01b-home-moon");
        click(find("Sun"));
        assertEquals(Ui.accentColor(Ui.ACCENT_FOLLOW, false, night, design), act.palette().accent);
    }

    /**
     * Each tool is a row: its name and words on the left, its icon on the right; with Continue showing, all three
     * fit the screen without scrolling (owner's sketch, 2026-11-15).
     */
    @Test public void toolCardsAreRowsAndFit() throws Exception {
        goHome();
        assertNotNull(describedFrom("Continue"));
        for (String tool : new String[]{"Line up a street", "Find the spot", "Build the shot"}) {
            android.widget.LinearLayout card = (android.widget.LinearLayout) byDescription(tool);
            assertEquals(tool, android.widget.LinearLayout.HORIZONTAL, card.getOrientation());
            ViewGroup words = (ViewGroup) card.getChildAt(0);
            assertEquals(tool, ((TextView) words.getChildAt(0)).getText().toString());
            View disc = card.getChildAt(1);
            assertTrue(tool + ": icon on the right", disc.getLeft() >= words.getRight());
            View glyph = disc instanceof ViewGroup ? ((ViewGroup) disc).getChildAt(0) : disc;   // Pocket map: the icon alone
            assertTrue(tool + ": an icon", glyph instanceof android.widget.ImageView);
        }
        int[] at = new int[2];
        View last = byDescription("Build the shot");
        last.getLocationInWindow(at);
        assertTrue("Build the shot ends on screen: " + (at[1] + last.getHeight()), at[1] + last.getHeight() <= 2340);
        View cont = describedFrom("Continue");
        cont.getLocationInWindow(at);
        assertTrue("Continue ends on screen too (3.24: under the three tools and Now): " + (at[1] + cont.getHeight()),
                at[1] + cont.getHeight() <= 2340);
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
        moon.performClick();                       // 3.13.2: the change between sun and moon plays first
        HomeScreen hs = (HomeScreen) ofType(root(), HomeScreen.class);
        java.lang.reflect.Field mf = HomeScreen.class.getDeclaredField("morph");
        mf.setAccessible(true);
        float[] steps = {0.3f, 0.5f, 0.75f};
        for (int i = 0; i < steps.length; i++) {
            mf.setFloat(hs, steps[i]);
            rawShot("21m" + (char) ('a' + i) + "-home-change");
        }
        int covers = Reveal.covers;                // the change ends, then the colours open in a circle (Reveal)
        for (int i = 0; i < 100 && !prefs().getBoolean("moon", false); i++) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));
        }
        assertTrue("the moon is chosen after the change", prefs().getBoolean("moon", false));
        assertTrue("the colours then change in a circle", Reveal.covers > covers);
        rawShot("21b-home-moon-after-change");
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
        waitForPrefix("Sun at the far end");
        useSoonest();
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        assertNotNull("Your shot names the difference of ground", walk(root(), "20 m lower than the ground at the far end", true, false));
        click(find("Open the viewfinder"));
        shot("24-viewfinder-subject-on-higher-ground");
    }

    /** A building of 40 x 40 m, its middle {@code along} metres the way {@code bearing} looks and {@code side} to the right. */
    private static Buildings.B block(double bearing, double along, double side, double length, double width, double height) {
        double b = Math.toRadians(bearing), ue = Math.sin(b), un = Math.cos(b), ve = Math.cos(b), vn = -Math.sin(b);
        double ce = ue * along + ve * side, cn = un * along + vn * side;
        double[] e = new double[4], n = new double[4];
        int[][] s = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            e[i] = ce + ue * s[i][0] * length / 2 + ve * s[i][1] * width / 2;
            n[i] = cn + un * s[i][0] * length / 2 + vn * s[i][1] * width / 2;
        }
        return new Buildings.B(e, n, height, false);
    }

    /**
     * 3.14 (owner: a sunset went behind a block): a 120 m tower beyond the subject, on the line, leaves out the low
     * times and When says so; the viewfinder draws it and the street's buildings. Off in Settings, nothing is hidden.
     */
    @Test public void buildingsHideTheLowSun() throws Exception {
        double b = Astro.bearing(YOU_LAT, YOU_LON, SUB_LAT, SUB_LON);
        java.util.List<Buildings.B> list = new java.util.ArrayList<>();
        list.add(block(b, 700, 0, 40, 40, 120));                       // the tower, behind the subject
        for (int i = 0; i < 8; i++) {                                    // a street: blocks either side of the line
            list.add(block(b, 40 + i * 45, -28, 38, 16, 14 + (i * 7) % 12));
            list.add(block(b, 60 + i * 45, 30, 38, 16, 18 + (i * 5) % 10));
        }
        Buildings.keep(new Buildings.Area(YOU_LAT, YOU_LON, b, list));
        Buildings.enabled = true;
        try {
            fresh(YOU, SUB, "1,0,2");
            click(describedFrom("Continue"));
            waitForPrefix("Sun at Big Ben");
            assertNotNull("When names the building", walk(root(), "120 m tall, hides the sun below", true, false));
            shot("40-when-a-building-hides-the-sun");
            useSoonest();
            click(find("Open the viewfinder"));
            shot("41-viewfinder-buildings");
            // Off in Settings: nothing is hidden, nothing said
            fresh(YOU, SUB, "1,0,2");
            Buildings.set(act, false);
            click(describedFrom("Continue"));
            waitForPrefix("Sun at Big Ben");
            assertNull("Left out, buildings are not named", walk(root(), "A building", true, false));
        } finally {
            Buildings.enabled = false;
            Buildings.keep(null);
        }
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
        click(byDescription("Build the shot"));
        assertTrue(stepBar().startsWith("Step 1 of 3"));
        shot("02-build-subject-step");
        act.tapMap(new org.osmdroid.util.GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        assertNotNull(ofType(root(), ComposeScreen.class));
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 1 of 3"));
        assertTrue(prefs().contains("subject_lat"));
        act.onBackPressed();
        idle();
        assertNotNull(byDescription("Build the shot"));
        act.onBackPressed();
        idle();
        assertTrue(act.isFinishing());
        // Line up a street starts from the subject too (3.8), then where you stand
        fresh(null, null, null);
        click(byDescription("Line up a street"));
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        waitFor("Where does the street point?");             // 3.24: the far end of a street, not a subject
        act.tapMap(new org.osmdroid.util.GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        waitFor("Where will you stand?");
        act.tapMap(new org.osmdroid.util.GeoPoint(YOU_LAT, YOU_LON));
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
        waitForPrefix("Moon at Big Ben");
        assertNull(walk(root(), "Sun behind", true, false));
        assertNull(findOrNull("Sun"));          // no sun section heading
        shot("04-when-moon");
        click(tab("Day chart"));
        waitForPrefix("Purple is the moon");
        shot("04b-when-day-moon");
    }

    /** Switching the body inside a tool recomputes the answers and keeps the step. */
    @Test public void switchBodyInsideATool() throws Exception {
        fresh(YOU, SUB, "1,1,2");
        click(describedFrom("Continue"));
        waitForPrefix("Moon at Big Ben");
        click(byDescription("Sun or moon: Moon"));
        click(byDescription("Sun"));
        waitForPrefix("Sun at Big Ben");
        assertNull(walk(root(), "Moon at ", true, false));
        assertTrue(stepBar().startsWith("Step 3 of 4"));
    }

    /** 3.8: "See it in the viewfinder" opens the soonest moment at once; Back shows Your shot, then When. */
    @Test public void useTheSoonestGoesToYourShot() throws Exception {
        waitForPrefix("Sun at the far end");
        click(seeIt());
        idle();
        assertNotNull("the viewfinder opens at once", ofType(root(), FrameScreen.class));
        closeFrameIfOpen();
        assertTrue(stepBar().startsWith("Step 4 of 4"));
        assertTrue(prefs().getLong("shot_ms", 0) > System.currentTimeMillis());
        waitFor("Open the viewfinder");
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 3 of 4"));
    }

    /**
     * Coming back after the app was closed: Home offers to continue where the user was. A Where to stand left
     * unfinished before 3.6 continues in Build the shot.
     */
    @Test public void restartOffersContinue() throws Exception {
        fresh(null, SUB, "2,0,2");
        assertNotNull(describedFrom("Continue"));
        waitForPrefix("Build the shot");
        click(describedFrom("Continue"));
        assertNotNull(ofType(root(), ComposeScreen.class));
        assertEquals("3,0,1", prefs().getString("flow", null));
    }

    /** The line is drawn: the sheet leads with the soonest moment; Times, Day and the Frame all open; Back steps out. */
    @Test public void answerSheetDayAndFrame() throws Exception {
        waitFor("All times");
        waitForPrefix("Sun at the far end");
        shot("1-answer");
        assertNotNull(find("At the far end"));
        assertNotNull(find("Pick a date"));
        click(byDescription("Pull the sheet up or down"));   // half to full: the whole list of answers
        shot("1b-answer-full");
        click(byDescription("Pull the sheet up or down"));   // full to peek: the map is free
        shot("1c-answer-peek");
        click(byDescription("Pull the sheet up or down"));

        click(tab("Day chart"));
        waitForPrefix("Orange is the sun");
        assertNull(walk(root(), "Moon at ", true, false));
        shot("2-day");
        click(tab("All times"));

        // The Frame: full screen, at the moment of the answer, with the lens keys and the jumps.
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        waitForPrefix("Lined up · ");
        waitForPrefix("35 mm");
        shot("3-frame");
        click(byDescription("Longer lens"));
        waitForPrefix("50 mm");
        click(byDescription("Turn the camera: landscape or portrait"));
        click(byDescription("Sun lined up"));
        waitForPrefix("· Lined up ·");
        shot("4-frame-portrait-50mm");
        assertEquals(50f, prefs().getFloat("focal_mm", 0), 0.01);

        // Back closes the Frame, then goes one step back. Nothing is lost: all is saved as you go.
        act.onBackPressed();
        idle();
        assertNull(walk(root(), "Sun lined up", false, true));
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
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        waitForPrefix("Lined up · ");                          // a sun shot: only the sun's jump (owner, 3.4)
        shot("6-frame-height");
        click(byDescription("Sun lined up"));
        shot("7-frame-moon");
        // The height dialog: Back closes it and nothing changes.
        click(byDescription("Subject height"));
        assertNotNull(ShadowDialog.getLatestDialog());
        assertTrue(ShadowDialog.getLatestDialog().isShowing());
        act.onBackPressed();
        idle();
        assertFalse(ShadowDialog.getLatestDialog().isShowing());
        assertNotNull(walk(root(), "Sun lined up", false, true));
        assertEquals(96f, prefs().getFloat("subject_h", 0), 0.01);
    }

    /**
     * 3.16 (owner: both layouts, switchable in Settings): the Camera layout. The time over the sky, the lens as a scale,
     * the one orange Lined up key; the key goes to the moment, a tap on the scale changes the lens.
     */
    @Test public void viewfinderCameraLayout() throws Exception {
        prefs().edit().putInt("viewfinder", Ui.VF_CAMERA).putFloat("subject_h", 96).putInt("subject_kind", 4)
                .putFloat("focal_mm", 85).apply();
        open();
        waitForPrefix("Sun behind subject");
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        waitForPrefix("● Lined up");
        shot("6c-frame-camera");
        assertNotNull("the shutter is Take the shot (3.19.6)", walk(root(), "Take the shot", false, false));
        assertNull("Save is the shutter now, not a key on top", walk(ofType(root(), FrameScreen.class), "Save", false, false));
        assertNull("the band is the other layout's", walk(root(), "Lined up · ", true, false));
        View scale = walk(root(), "Lens: 85 mm. Tap a length.", false, true);
        assertNotNull(scale);
        int[] at = new int[2];
        scale.getLocationInWindow(at);
        long t0 = android.os.SystemClock.uptimeMillis();
        float y = at[1] + scale.getHeight() / 2f, x = at[0] + 14 * act.getResources().getDisplayMetrics().density;
        act.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        act.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 50, android.view.MotionEvent.ACTION_UP, x, y, 0));
        idle();
        assertNotNull("the widest stop", walk(root(), "Lens: 14 mm. Tap a length.", false, true));
        ((TimeRuler) ofType(root(), TimeRuler.class)).dragBy(-200);                         // the time moves on: the key turns to a ring and says how far
        idle();
        waitForPrefix("Lined up at ");
        shot("6d-frame-camera-later");
        click(byDescription("Sun lined up"));
        waitForPrefix("● Lined up");
        act.onBackPressed();
        idle();
        assertNull(ofType(root(), FrameScreen.class));
    }

    /** 3.16: the Answer band (the default) names the moment in the band; Settings switches the layout. */
    @Test public void viewfinderLayoutIsASetting() throws Exception {
        waitForPrefix("Sun at the far end");
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        waitForPrefix("Lined up · ");
        assertNotNull(walk(root(), "Sun lined up", false, true));
        assertNull("no camera key in the band layout", walk(root(), "● Lined up", true, false));
        assertEquals(Ui.VF_BAND, Ui.viewfinder(act));
        prefs().edit().putInt("viewfinder", Ui.VF_CAMERA).apply();
        assertEquals(Ui.VF_CAMERA, Ui.viewfinder(act));
    }

    /** A step that asks for a point says when it has it, and what Next does (3.3). */
    @Test public void pointStepsSayTheyAreSet() throws Exception {
        act.onBackPressed();                              // from When to Your subject, both points set
        idle();
        assertTrue(stepBar().startsWith("Step 2 of 4"));
        assertNull(findOrNull("Where will you stand?"));
        assertNotNull(walk(root(), "Next shows when the sun lines up", true, false));
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        assertNull(findOrNull("Where does the street point?"));
        assertNotNull(walk(root(), "The far end", true, false));      // 3.24: a street's far end, not a subject
        assertNotNull(walk(root(), "Next: where you stand", true, false));
    }

    /** Your shot shows the shot as a picture; a tap opens the viewfinder. Also for the moon and Behind you. */
    @Test public void yourShotShowsTheShot() throws Exception {
        waitForPrefix(" at the far end");
        useSoonest();
        View pic = byDescription("Your shot as a picture");
        assertTrue(pic.isClickable());
        shot("07b-your-shot-picture");
        click(pic);
        assertNotNull(ofType(root(), FrameScreen.class));
        act.onBackPressed();
        idle();
        // The moon, behind you (the body at your back): the picture is built for it and draws.
        act.onBackPressed();                                // to When
        idle();
        click(byDescription("Sun or moon: Sun"));
        click(byDescription("Moon"));
        click(find("Behind you"));
        waitForPrefix(" behind you");
        View use = seeItOrNull();
        if (use != null && use.isEnabled()) {
            click(use);
            closeFrameIfOpen();
            FrameView moonPic = (FrameView) byDescription("Your shot as a picture");
            Bitmap b = Bitmap.createBitmap(1080, 360, Bitmap.Config.ARGB_8888);
            moonPic.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY));
            moonPic.layout(0, 0, 1080, 360);
            moonPic.draw(new Canvas(b));
            float[] mq = moonPic.moonOnScreen();
            assertTrue("the moon is at your back, not in the picture", mq == null || !moonPic.frameRect().contains(mq[0], mq[1]));
        }
    }

    /** From a line: "Build the shot" keeps the subject and builds the shot from it; Back steps back. */
    @Test public void buildTheShotFromALine() throws Exception {
        waitForPrefix(" at the far end");
        useSoonest();                                      // 3.8: the other tool is on the last page, Your shot
        click(describedFrom("Build the shot: another tool"));
        assertNotNull(ofType(root(), ComposeScreen.class));
        act.onBackPressed();
        idle();
        assertTrue(stepBar().startsWith("Step 1 of 3"));
        assertTrue(prefs().contains("subject_lat"));
    }

    /** The moon has one more setting in Build the shot: how full it must be (3.6, from Where to stand). */
    @Test public void buildTheShotMoonHowFull() throws Exception {
        fresh(null, SUB, "3,1,1");
        click(describedFrom("Continue"));
        assertNotNull(ofType(root(), ComposeScreen.class));
        assertNotNull(describedFrom("Settings: Moonset · Half or more"));
        click(describedFrom("Settings: "));
        assertNotNull(byDescription("How full: Half or more"));
        pick("How full", "Any");
        assertEquals(0, prefs().getInt("moon_full", 1));
        assertNotNull(describedFrom("Settings: Moonset · Any phase"));
    }

    /**
     * Where the sun sits (3.6, from Where to stand): three keys that snap the body there; dragging it yourself
     * lights none. The distance slider in Settings gives the subject its size, and On top stays on top.
     */
    @Test public void buildTheShotWhereTheSunSits() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        click(describedFrom("Settings: "));                // 3.10: the card pulled up shows Where the sun sits
        assertNotNull(byDescription("Where the sun sits: Just on top"));
        double top = cs.bodyPlace()[1];
        click(find("Horizon"));
        assertEquals(0, prefs().getInt("stand_place", 2));
        assertTrue("on the horizon is lower: " + cs.bodyPlace()[1] + " < " + top, cs.bodyPlace()[1] < top);
        assertEquals(0, cs.bodyPlace()[0], 1e-9);
        click(find("Middle"));
        double middle = cs.bodyPlace()[1];
        assertTrue(middle < top);
        shot("17f-build-where-it-sits");
        // Drag the body: no key is lit any more
        FrameView fv = drawn((FrameView) ofType(cs, FrameView.class));
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0], body[1], body[0] + 30 * k, body[1] - 40 * k);
        assertNotNull(byDescription("Where the sun sits: where you put it"));
        // On top, then farther: the subject is smaller, the sun still on its top (lower in the sky)
        click(find("On top"));
        double near = cs.bodyPlace()[1];
        SeekBar sb = (SeekBar) byDescription("Distance from the subject");
        java.lang.reflect.Field lf = SeekBar.class.getDeclaredField("mOnSeekBarChangeListener");
        lf.setAccessible(true);
        SeekBar.OnSeekBarChangeListener l = (SeekBar.OnSeekBarChangeListener) lf.get(sb);
        sb.setProgress(90);
        l.onProgressChanged(sb, 90, true);
        l.onStopTrackingTouch(sb);
        idle();
        assertTrue("farther: the top is lower: " + cs.bodyPlace()[1] + " < " + near, cs.bodyPlace()[1] < near);
        assertNotNull(byDescription("Where the sun sits: Just on top"));
        for (int i = 0; i < 100 && !describedFrom("Settings: ").getContentDescription().toString().contains(" km away"); i++) {
            Thread.sleep(50);
            idle();
        }
        assertTrue(describedFrom("Settings: ").getContentDescription().toString(),
                describedFrom("Settings: ").getContentDescription().toString().contains(" km away"));
    }

    /** Search: results set the subject; an empty answer and no connection each say what to do next. */
    @Test public void searchSetsTheSubject() throws Exception {
        fresh(null, null, null);
        click(byDescription("Build the shot"));
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
        waitFor("All times");
        click(byDescription("More"));
        android.widget.PopupMenu menu = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(menu);
        shadowOf(menu).getOnMenuItemClickListener().onMenuItemClick(menu.getMenu().findItem(2));
        idle();
        waitFor("Where does the street point?");         // 3.8: cleared, the line starts again from its far end (3.24)
        assertFalse(prefs().contains("you_lat"));
        click(find("Undo"));
        assertEquals(YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
        waitFor("All times");
    }

    /** No network here: saving must still work at once, under a number name. */
    private void offline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(cm).setActiveNetworkInfo(null);
    }

    /** Your shot: Save in one press (named later), Remind me, Directions; Saved lists it and opens it again. */
    @Test public void yourShotSaveRemindDirections() throws Exception {
        offline();
        waitForPrefix("Sun at the far end");
        useSoonest();
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

    /** The viewfinder: Move shows the joystick to walk where you stand; Back closes it first; Save saves the shot. */
    @Test public void viewfinderMoveAndSave() throws Exception {
        offline();
        waitForPrefix("Sun at the far end");
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        click(byDescription("Move: walk where you stand"));
        // Present is not enough: the sheet has a shadow (elevation), and Android draws and touches a higher view on
        // top whatever the order. The Move map was hidden under it from 3.1.1 (Ali's test, 2026-11-15).
        View panel = byDescription("Joystick: push to walk");
        while (!(panel.getParent() instanceof FrameScreen)) panel = (View) panel.getParent();
        ViewGroup over = (ViewGroup) panel.getParent();
        for (int i = 0; i < over.getChildCount(); i++)
            if (over.getChildAt(i) != panel) assertTrue("the joystick is on top", panel.getZ() > over.getChildAt(i).getZ());
        shot("10-viewfinder-joystick");
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
        assertNull(walk(root(), "Joystick: push to walk", false, true));
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
        waitForPrefix("Sun at the far end");
        useSoonest();
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
        waitForPrefix("Sun at the far end");
        useSoonest();
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
        clickWhenReady("Fly here");
        assertTrue(stepBar().startsWith("Step 3 of 3"));
        act.onBackPressed();
        idle();
        // 3.22: the settings are folded once Done is pressed; the row that names them says the light was kept
        assertNotNull(describedFrom("Settings: Sunrise ·"));
    }

    /** Review 8: starting Build the shot sets your spot aside; back on Home it is back. */
    @Test public void buildTheShotSetsYouAsideNotAway() throws Exception {
        goHome();
        click(byDescription("Build the shot"));
        assertFalse(prefs().contains("you_lat"));
        goHome();
        assertEquals(YOU_LAT, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
    }

    /** Review 9: the phone switching to dark mode mid-tool restarts the screen on the same step, not on Home. */
    @Test public void restartMidToolKeepsTheStep() throws Exception {
        waitForPrefix("Sun at the far end");
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
        click(byDescription("Line up a street"));         // a clean start: the subject, then where you stand (3.8)
        act.tapMap(new org.osmdroid.util.GeoPoint(SUB_LAT, SUB_LON));
        idle();
        click(find("Next"));
        act.tapMap(new org.osmdroid.util.GeoPoint(YOU_LAT, YOU_LON));
        idle();
        click(find("Next"));
        waitForPrefix(" at the far end");
        useSoonest();
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

    /** The sun on its day path is dragged round you: the time follows it (3.20, owner: "move the day by dragging the sun"). */
    @Test public void dragTheSunMovesTheDay() throws Exception {
        waitFor("All times");
        waitForPrefix("Sun at the far end");
        click(tab("Day chart"));
        waitForPrefix("Orange is the sun");
        java.lang.reflect.Field lf = MainActivity.class.getDeclaredField("lineOverlay");
        lf.setAccessible(true);
        Object layer = lf.get(act);
        java.lang.reflect.Field dx = layer.getClass().getDeclaredField("dayX"), dy = layer.getClass().getDeclaredField("dayY");
        dx.setAccessible(true);
        dy.setAccessible(true);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("cursorMs");
        cf.setAccessible(true);
        // a time with the sun up, so the body stands on its drawn path
        java.lang.reflect.Field df = MainActivity.class.getDeclaredField("dayStart");
        df.setAccessible(true);
        cf.setLong(act, df.getLong(act) + 12 * 3600000L);
        java.lang.reflect.Field mf = MainActivity.class.getDeclaredField("map");
        mf.setAccessible(true);
        GeoMap gm = (GeoMap) mf.get(act);
        gm.view().invalidate();
        root().layout(0, 0, 1080, 2340);
        gm.view().draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888)));
        float x = dx.getFloat(layer), y = dy.getFloat(layer);
        assertFalse("the body is drawn on its path", Float.isNaN(x));
        long before = cf.getLong(act);
        java.lang.reflect.Field yf = MainActivity.class.getDeclaredField("you");
        yf.setAccessible(true);
        android.graphics.Point a = gm.toPixels((org.osmdroid.util.GeoPoint) yf.get(act));
        // turn the finger 25 degrees round you, the way the sun goes (clockwise in the northern sky's south)
        double ang = Math.atan2(x - a.x, -(y - a.y)) + Math.toRadians(25), r = Math.hypot(x - a.x, y - a.y);
        int[] at = new int[2];
        gm.view().getLocationInWindow(at);
        drag(act.getWindow().getDecorView(), at[0] + x, at[1] + y,
                at[0] + a.x + (float) (Math.sin(ang) * r), at[1] + a.y - (float) (Math.cos(ang) * r));
        long after = cf.getLong(act);
        assertTrue("the time moved with the sun: " + before + " -> " + after, Math.abs(after - before) > 20 * 60000L);
        assertTrue("the same day", after >= df.getLong(act) && after < df.getLong(act) + 24 * 3600000L);
        shot("2b-day-sun-dragged");
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
        app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true).putBoolean("guide_find", true).putBoolean("guide_choose", true).putBoolean("guide_map", true).putBoolean("guide_now", true).putBoolean("guide_shot", true).putBoolean("guide_frame", true)
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
        drag(fv, body[0], body[1], body[0] - 40 * k, body[1] + 30 * k);   // inward: at the edge it stops (Ali, 3.3)
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

    /** Build the shot as it opens on Big Ben for these tests, with this framing remembered from last time. */
    private ComposeScreen openBuild(String pan, String pitch) {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences.Editor e = app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true).putBoolean("guide_find", true).putBoolean("guide_choose", true).putBoolean("guide_map", true).putBoolean("guide_now", true).putBoolean("guide_shot", true).putBoolean("guide_frame", true)
                .putString("subject_lat", "" + SUB_LAT).putString("subject_lon", "" + SUB_LON)
                .putString("subject_name", "Big Ben").putFloat("subject_h", 96).putInt("subject_kind", 4)
                .putBoolean("drone", true)
                .putString("cam_lat", "51.502").putString("cam_lon", "-0.122").putString("cam_zoom", "15")
                .putString("flow", "3,0,1");
        if (pan != null) e.putString("frame_pan", pan).putString("frame_pitch", pitch);
        e.apply();
        open();
        try {
            waitForPrefix(" of Big Ben");
        } catch (InterruptedException x) {
            throw new RuntimeException(x);
        }
        return (ComposeScreen) ofType(root(), ComposeScreen.class);
    }

    /** Lays out and draws a view, so what it says about its picture is what is on screen now. */
    private static FrameView drawn(FrameView fv) {
        View r = fv.getRootView();
        r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        r.layout(0, 0, 1080, 2340);
        Bitmap b = Bitmap.createBitmap(Math.max(1, fv.getWidth()), Math.max(1, fv.getHeight()), Bitmap.Config.ARGB_8888);
        fv.draw(new Canvas(b));
        return fv;
    }

    /** Some of the subject is in the picture: between its foot and its top, inside the frame (the spec's rule). */
    private static void assertSubjectSeen(FrameView fv) {
        float[] foot = fv.subjectOnScreen(), top = fv.subjectTopOnScreen();
        android.graphics.RectF f = fv.frameRect();
        assertNotNull(foot);
        assertNotNull(top);
        assertTrue("the subject's column crosses the picture: x " + foot[0] + " in " + f, foot[0] >= f.left && foot[0] <= f.right);
        assertTrue("some of the subject in the picture: " + top[1] + ".." + foot[1] + " in " + f, top[1] <= f.bottom && foot[1] >= f.top);
    }

    private static void assertInPicture(String what, FrameView fv, float[] q) {
        assertNotNull(what, q);
        assertTrue(what + " in the picture: " + q[0] + "," + q[1] + " in " + fv.frameRect(), fv.frameRect().contains(q[0], q[1]));
    }

    private java.util.List<TextView> texts(View v) {
        java.util.List<TextView> out = new java.util.ArrayList<>();
        for (View x : all(v)) if (x instanceof TextView && x.getVisibility() == View.VISIBLE) out.add((TextView) x);
        return out;
    }

    /** Ali 3.3: the time is the answer: the headline is "Tomorrow · 3:20 PM" or "Nov 23 · 5:52 PM", never cut. */
    @Test public void headlineKeepsTheTime() throws Exception {
        waitForPrefix(" at the far end");
        java.lang.reflect.Method from = MainActivity.class.getDeclaredMethod("setSearchFrom", long.class);
        from.setAccessible(true);
        from.invoke(act, System.currentTimeMillis() + 10 * 86400000L);   // a day with a weekday name, not "Tomorrow"
        idle();
        waitForPrefix(" at the far end");
        boolean found = false;
        for (TextView t : texts(root())) {
            String x = t.getText().toString();
            assertFalse("a full weekday in a headline: " + x, x.matches("^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday), .* · \\d.*"));
            found |= x.matches("^[A-Z][a-z]{2} \\d{1,2} · \\d{1,2}:\\d{2}.*")
                    || x.matches("^\\d{1,2}:\\d{2}( [AP]M)?$");   // 3.9: the card's time alone, the day in its label
        }
        assertTrue("a short headline", found);
    }

    /** Your shot's keys are all on screen, whole, under the picture (3.3). */
    @Test public void yourShotKeysOnScreen() throws Exception {
        waitForPrefix(" at the far end");
        useSoonest();
        sheetTo(Sheet.FULL);                                // 3.9: Your shot pulls; its keys are under the head
        View r = root();
        r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        r.layout(0, 0, 1080, 2340);
        float k = act.getResources().getDisplayMetrics().density;
        for (String d : new String[]{"Save this shot", "Remind me 30 minutes before", "Directions to your spot"}) {
            View key = byDescription(d);
            int[] at = new int[2];
            key.getLocationInWindow(at);
            assertTrue(d + " whole: " + key.getHeight(), key.getHeight() >= 40 * k - 1);
            assertTrue(d + " on screen: " + (at[1] + key.getHeight()), at[1] + key.getHeight() <= 2340);
        }
    }

    /** Ali 3.3: "Reminder set" fits its key on one line. */
    @Test public void reminderKeyOnOneLine() throws Exception {
        waitForPrefix(" at the far end");
        useSoonest();
        click(byDescription("Remind me 30 minutes before"));
        TextView key = (TextView) byDescription("Reminder set. Press to take it off");
        View r = root();
        r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        r.layout(0, 0, 1080, 2340);
        assertEquals(1, key.getLineCount());
    }

    /** Ali 3.3: a saved shot opens with its subject's name; Back from a Trip moment goes back to Saved. */
    @Test public void savedShotKeepsItsSubjectAndTripMomentsGoBack() throws Exception {
        fresh(YOU, SUB, "1,0,2");
        click(describedFrom("Continue"));
        waitForPrefix("at Big Ben");
        useSoonest();
        click(byDescription("Save this shot"));
        java.lang.reflect.Field f = MainActivity.class.getDeclaredField("subjectName");
        f.setAccessible(true);
        f.set(act, null);                                 // the map moved on to another subject
        goHome();
        click(byDescription("Saved"));
        click(tab("Shots"));
        click(describedFrom("Open "));
        waitForPrefix("at Big Ben");
        act.onBackPressed();
        idle();
        click(tab("Trip"));
        waitForPrefix(" behind subject");
        View moment = null;
        for (TextView t : texts(root())) if (t.getText().toString().contains(" behind subject") && t.isClickable()) moment = t;
        if (moment == null) for (TextView t : texts(root())) if (t.getText().toString().contains(" behind subject")) moment = (View) t.getParent();
        click(moment);
        act.onBackPressed();                              // the viewfinder
        idle();
        act.onBackPressed();                              // Your shot: back to Saved, where the moment was opened
        idle();
        assertNotNull(tab("Trip"));
    }

    /** Ali 3.3: Centre appearing does not shift the other keys. */
    @Test public void centreKeyDoesNotMoveTheOthers() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        View turn = byDescription("Turn the camera: landscape or portrait");
        FrameView fv = drawn((FrameView) ofType(cs, FrameView.class));
        int[] before = new int[2];
        turn.getLocationInWindow(before);
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0] - 150 * k, body[1] + 120 * k, body[0] - 90 * k, body[1] + 100 * k);
        drawn(fv);
        int[] after = new int[2];
        turn.getLocationInWindow(after);
        assertEquals(before[0], after[0]);
    }

    /** Review minor: Save after re-centring in the viewfinder keeps the framing on screen, not the old one. */
    @Test public void saveKeepsTheFramingOnScreen() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        FrameView fv = drawn((FrameView) ofType(cs, FrameView.class));
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0] - 150 * k, body[1] + 120 * k, body[0] - 90 * k, body[1] + 100 * k);
        clickWhenReady("Fly here");
        click(byDescription("Save this shot"));
        assertTrue(Math.abs(Trip.loadLines(act).get(0).panDeg) > 0.5);
        click(byDescription("Viewfinder: see the shot as the camera will"));
        ((FrameScreen) ofType(root(), FrameScreen.class)).aimAgain();
        click(describedFrom("Save"));                     // the viewfinder's Save
        assertEquals(0, Trip.loadLines(act).get(0).panDeg, 1e-9);
        assertEquals(1, Trip.loadLines(act).size());
    }

    /** Review minor: a sideways drag swings the picture but leaves the tilt to follow the sun. */
    @Test public void sidewaysDragKeepsTheUsualTilt() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        FrameView fv = drawn((FrameView) ofType(cs, FrameView.class));
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0] - 150 * k, body[1] + 120 * k, body[0] - 100 * k, body[1] + 120 * k);
        assertTrue(Math.abs(cs.panDeg()) > 0.5);
        assertTrue("the tilt still follows: " + cs.framePitch(), Double.isNaN(cs.framePitch()));
    }

    private void sheetTo(int stop) {
        ((Sheet) ofType(root(), Sheet.class)).setStop(stop, false);
        idle();
    }

    /** Owner, 3.4: a tool started from Home starts clean; the last points come back with one tap. */
    @Test public void aToolStartsClean() throws Exception {
        goHome();
        click(byDescription("Line up a street"));
        assertTrue(stepBar().startsWith("Step 1 of 4"));
        assertNull("no old You", prefs().getString("you_lat", null));
        assertNull("no old subject", prefs().getString("subject_lat", null));
        assertNull("no Use the last bar (owner, 3.8)", findOrNull("Use the last"));
    }

    /** Owner, 3.4: Build the shot's Light has Any; When lists the days by Sunset and Sunrise. */
    @Test public void anyLightInBuildTheShot() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        waitForPrefix("° up");
        pick("Light", "Any");
        for (int i = 0; i < 100 && walk(root(), "When: Sun", true, true) == null; i++) { Thread.sleep(50); idle(); }
        View when = describedFrom("When: ");
        assertTrue(when.getContentDescription().toString(), when.getContentDescription().toString().matches("When: (Sunset|Sunrise) · .*"));
    }

    @Test public void buildTheShotPanelIsCompact() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        waitForPrefix("° up");
        assertNull("no Days field", walk(root(), "Days", false, false));
        assertNotNull("the answer leads with Soonest (3.19.7)", walk(root(), "Soonest", true, false));
        assertNotNull("the distance is named Distance (3.19.7)", walk(root(), "Distance ", true, false));        View when = describedFrom("When: ");
        View stand = find("Fly here");
        boolean sameRow = false;
        for (android.view.ViewParent v = when.getParent(); v instanceof View; v = v.getParent()) sameRow |= v == stand.getParent();
        assertTrue("When sits beside Stand here", sameRow);
        // When stays on one line: "Today to / Tomorrow" read as two choices (roadmap, 3.6)
        View r = root();
        // The words shrink a step per layout until they fit; a longer date ("Tomorrow to Nov 18") takes more passes.
        for (int i = 0; i < 40; i++) {
            r.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
            r.layout(0, 0, 1080, 2340);
            idle();
        }
        ViewGroup valueRow = (ViewGroup) ((ViewGroup) ((ViewGroup) when).getChildAt(0)).getChildAt(1);
        TextView wv = (TextView) valueRow.getChildAt(valueRow.getChildCount() - 1);
        if (wv.getLayout() == null) {   // a shrink step asked for a layout the test's passes did not give (a phone's next frame does)
            wv.measure(View.MeasureSpec.makeMeasureSpec(wv.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            wv.layout(wv.getLeft(), wv.getTop(), wv.getRight(), wv.getTop() + wv.getMeasuredHeight());
        }
        assertEquals(wv.getText().toString(), 1, wv.getLineCount());
        assertEquals(wv.getText() + " is cut", 0, wv.getLayout().getEllipsisCount(0));
        assertNull("no long hint under the picture", walk(root(), "Drag the picture to frame it", true, false));
        for (TextView t : texts(root())) assertFalse("short words: " + t.getText(), t.getText().toString().contains(" tall."));
        // Settings folded: one row naming them; a tap unfolds Light, Look in, On foot / Drone in place.
        assertNull(walk(root(), "Light: Sunset", false, true));
        click(describedFrom("Settings: "));
        assertNotNull(byDescription("Light: Sunset"));
        // 3.22 (owner): open settings take the answer's place; nothing of the answer stays under them
        assertNull("no Fly here under open settings", walk(root(), "Fly here", false, false));
        assertNull("no When under open settings", describedFrom(root(), "When: "));
        assertNull("no Soonest under open settings", walk(root(), "Soonest", true, false));
        assertNotNull(find("Where the sun sits on the subject"));
        // 3.19.7: the Distance slider is marked where it gives a result (3.22: words only for a problem)
        for (int i = 0; i < 100 && !cs.distanceChecked(); i++) {
            Thread.sleep(50);
            idle();
        }
        assertTrue("the slider knows where it gives results", cs.distanceChecked());
        assertNull("no list of ranges", walk(root(), "Results:", true, false));
        shot("17d-build-the-shot-settings-open");
        assertNotNull(find("Drone"));
        click(byDescription("Done: show the answer"));
        assertNull(walk(root(), "Light: Sunset", false, true));
        assertNotNull("the answer is back", find("Fly here"));
        assertNull("no red line under a good answer", walk(root(), "Shaded red", true, false));
        // Help: the tool's guide (3.20), with the how-to; Back closes it and Build the shot is still there.
        click(byDescription("Help: how Build the shot works"));
        idle();
        assertNotNull(find("Place the sun"));
        assertNotNull(walk(root(), "Drag anywhere else to frame the picture", true, false));
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Skip"));
        assertNotNull(ofType(root(), ComposeScreen.class));
    }

    /** Owner, 3.4: golden hour is a light of its own; the sun cannot be placed above it (6°). */
    @Test public void goldenHourKeepsTheSunLow() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        waitForPrefix("° up");
        click(describedFrom("Settings: "));
        pick("Light", "Sunset golden hour");
        assertNotNull(describedFrom("Settings: Sunset golden hour"));
        FrameView fv = drawn((FrameView) ofType(cs, FrameView.class));
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0], body[1], body[0], body[1] - 120 * k);     // try to lift it high
        for (int i = 0; i < 40 && cs.bodyPlace()[1] > Light.GOLDEN_TOP + 0.5; i++) { Thread.sleep(50); idle(); }
        assertTrue("in golden hour: " + cs.bodyPlace()[1], cs.bodyPlace()[1] <= Light.GOLDEN_TOP + 0.5);
    }

    /** Owner, 3.4: a sun shot's viewfinder says nothing about the moon (and the other way round). */
    @Test public void viewfinderShowsOnlyTheShotsBody() throws Exception {
        waitForPrefix(" at the far end");
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        FrameView view = (FrameView) ofType(ofType(root(), FrameScreen.class), FrameView.class);
        assertFalse(view.showsMoon());
        assertTrue(view.showsSun());
        assertNull(walk(root(), "Moon lined up", false, true));
        assertNull(walk(root(), "Moon · ", true, false));
        assertNotNull(walk(root(), "Sun lined up", false, true));
    }

    /** A rotation sensor for the viewfinder's Compass (Robolectric has none). */
    private void addCompass() {
        android.hardware.SensorManager sm = (android.hardware.SensorManager) act.getSystemService(Context.SENSOR_SERVICE);
        shadowOf(sm).addSensor(org.robolectric.shadows.ShadowSensor.newInstance(android.hardware.Sensor.TYPE_ROTATION_VECTOR));
    }

    /** AR (3.5): with Compass on and the camera allowed, the camera shows behind; a landmark sets the compass. */
    @Test public void arCalibratesTheCompassFromALandmark() throws Exception {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA);
        addCompass();
        waitForPrefix(" at the far end");
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        FrameScreen f = (FrameScreen) ofType(root(), FrameScreen.class);
        FrameView view = (FrameView) ofType(f, FrameView.class);
        click(byDescription("AR: follow the way the phone points"));
        assertTrue("AR drawing", view.isAr());
        assertNotNull("the camera behind", byDescription("Camera"));
        f.feedRaw(100, 5);                                 // the phone's compass reads 100°
        assertEquals(0, FrameMath.diff(view.aimAz(), 100), 0.5);
        // Calibrate: a landmark you can see (not the subject), point the cross at it, Set.
        click(byDescription("Calibrate the compass"));
        click(find("Landmark"));                           // the sun or moon comes first when up (3.26)
        assertNotNull(byDescription("Map: tap a landmark you can see"));
        assertTrue("the cross to aim with", view.showsCross());
        shot("25-ar-calibrate");
        double lmLat = YOU_LAT + 0.004, lmLon = YOU_LON + 0.003;
        f.pickLandmark(lmLat, lmLon);
        click(find("Set"));
        double you0 = Double.parseDouble(prefs().getString("you_lat", "0")), you1 = Double.parseDouble(prefs().getString("you_lon", "0"));
        double truth = Astro.bearing(you0, you1, lmLat, lmLon);
        f.feedRaw(100, 5);                                 // the same reading now aims at the landmark's true bearing
        assertEquals(0, FrameMath.diff(view.aimAz(), truth), 0.5);
        waitForPrefix("Compass set");
        assertEquals(FrameMath.headingOffset(truth, 100), Double.parseDouble(prefs().getString("compass_offset", "0")), 0.01);
        // Compass off: the drawn scene and the lens come back.
        click(byDescription("AR is on. Press to aim at the subject again"));
        assertFalse(view.isAr());
        assertNull(walk(root(), "Camera", false, true));
    }

    /** 3.26 (owner): the compass is set from the real sun (or moon), and a landmark from where you really are. */
    @Test public void arCalibratesFromTheSunAndFromWhereYouAre() throws Exception {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA);
        addCompass();
        waitForPrefix(" at the far end");
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        FrameScreen f = (FrameScreen) ofType(root(), FrameScreen.class);
        FrameView view = (FrameView) ofType(f, FrameView.class);
        click(byDescription("AR: follow the way the phone points"));
        f.feedRaw(200, 20);
        long noon = 1781956800000L;                        // 2026-06-20 12:00 UTC: the sun high over London
        f.calClockMs = noon;
        click(byDescription("Calibrate the compass"));
        assertNotNull("the sun up, so it is offered first", walk(root(), "Point the cross at the real sun.", false, false));
        assertNull("no map for the sun", walk(root(), "Map: tap a landmark you can see", false, true));
        shot("25b-ar-calibrate-sun");
        double you0 = Double.parseDouble(prefs().getString("you_lat", "0")), you1 = Double.parseDouble(prefs().getString("you_lon", "0"));
        double sunAz = Astro.sun(noon, you0, you1)[0];
        click(find("Set"));
        waitForPrefix("Compass set from the sun");
        f.feedRaw(200, 20);
        assertEquals(0, FrameMath.diff(view.aimAz(), sunAz), 0.5);

        // The moon down at that hour: said, and Set refuses
        f.calClockMs = noon;
        click(byDescription("Calibrate the compass"));
        click(find("Moon"));
        if (Astro.moon(noon, you0, you1)[1] <= 0.5) {
            assertNotNull(walk(root(), "The moon is down now. Use the sun or a landmark.", false, false));
            assertFalse(find("Set").isEnabled());
        }

        // A landmark, seen from where the phone is (500 m east of the shot's spot), not from the shot's spot
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        double hereLat = you0, hereLon = you1 + 0.0072;
        f.hereIs(hereLat, hereLon);
        click(find("Landmark"));
        click(find("Where I am"));
        double lmLat = you0 + 0.004, lmLon = you1 + 0.003;
        f.pickLandmark(lmLat, lmLon);
        shot("25c-ar-calibrate-from-here");
        f.feedRaw(200, 20);
        click(find("Set"));
        waitForPrefix("Compass set");
        f.feedRaw(200, 20);
        assertEquals(0, FrameMath.diff(view.aimAz(), Astro.bearing(hereLat, hereLon, lmLat, lmLon)), 0.5);
        assertTrue("not the bearing from the shot's spot",
                Math.abs(FrameMath.diff(view.aimAz(), Astro.bearing(you0, you1, lmLat, lmLon))) > 5);
    }

    /** AR (3.5): camera refused, Compass still follows the phone over the drawn sky. */
    @Test public void arWithoutTheCameraKeepsTheDrawnSky() throws Exception {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA);
        addCompass();
        waitForPrefix(" at the far end");
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        FrameView view = (FrameView) ofType(ofType(root(), FrameScreen.class), FrameView.class);
        click(byDescription("AR: follow the way the phone points"));
        act.onRequestPermissionsResult(MainActivity.REQ_CAMERA, new String[]{Manifest.permission.CAMERA},
                new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
        idle();
        assertFalse(view.isAr());
        assertNull(walk(root(), "Camera", false, true));
        assertNotNull(byDescription("AR is on. Press to aim at the subject again"));
    }

    /** Owner, 3.4: in the viewfinder, Move shows a joystick: push it to walk; the scene and the moment follow. */
    @Test public void joystickWalksYou() throws Exception {
        waitForPrefix(" at the far end");
        useSoonest();
        click(byDescription("Viewfinder: see the shot as the camera will"));
        click(byDescription("Move: walk where you stand"));
        View stick = byDescription("Joystick: push to walk");
        assertNull("no map panel any more", walk(root(), "Map: drag You, or tap where you will stand", false, true));
        // The map and the joystick take the lower part of the screen: the sheet steps aside (owner, 3.4.3)
        assertNull("the sheet is away while walking", walk(ofType(root(), FrameScreen.class), "AR", false, false));
        View mini0 = byDescription("Map: where you stand now");
        View r0 = root();
        r0.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        r0.layout(0, 0, 1080, 2340);
        float kk = act.getResources().getDisplayMetrics().density;
        assertTrue("a big map: " + mini0.getHeight(), mini0.getHeight() >= 220 * kk - 1);
        double y0 = Double.parseDouble(prefs().getString("you_lat", "0")), x0 = Double.parseDouble(prefs().getString("you_lon", "0"));
        double before = Astro.distance(y0, x0, SUB_LAT, SUB_LON);
        int[] at = new int[2];
        stick.getLocationInWindow(at);
        float cx = at[0] + stick.getWidth() / 2f, cy = at[1] + stick.getHeight() / 2f;
        long t0 = android.os.SystemClock.uptimeMillis();
        act.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, cx, cy, 0));
        for (int i = 0; i < 40; i++) {                     // push forward (up) for two seconds
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
            act.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, android.os.SystemClock.uptimeMillis(),
                    android.view.MotionEvent.ACTION_MOVE, cx, cy - stick.getHeight() / 2f, 0));
        }
        act.dispatchTouchEvent(android.view.MotionEvent.obtain(t0, android.os.SystemClock.uptimeMillis(),
                android.view.MotionEvent.ACTION_UP, cx, cy - stick.getHeight() / 2f, 0));
        idle();
        double y1 = Double.parseDouble(prefs().getString("you_lat", "0")), x1 = Double.parseDouble(prefs().getString("you_lon", "0"));
        double after = Astro.distance(y1, x1, SUB_LAT, SUB_LON);
        assertTrue("walked towards the subject: " + before + " -> " + after, after < before - 5);
        // The small map beside the joystick shows where you are now (owner, 3.4.2)
        org.osmdroid.views.MapView mini = (org.osmdroid.views.MapView) byDescription("Map: where you stand now");
        FrameScreen fs = (FrameScreen) ofType(root(), FrameScreen.class);
        assertEquals(y1, fs.walkMapYou()[0], 1e-6);
        assertEquals(x1, fs.walkMapYou()[1], 1e-6);
        assertTrue("the map follows you", Astro.distance(mini.getMapCenter().getLatitude(), mini.getMapCenter().getLongitude(), y1, x1)
                < Astro.distance(y0, x0, y1, x1) + 1);
        waitForPrefix("You moved");
        act.onBackPressed();                              // Back closes the joystick first
        idle();
        assertNull(walk(root(), "Joystick: push to walk", false, true));
        assertNotNull("the sheet is back", walk(ofType(root(), FrameScreen.class), "AR", false, false));
        assertNotNull(ofType(root(), FrameScreen.class));
    }

    /** Review 1: at a long lens a drag still frames, following the finger, never jumping. */
    @Test public void longLensFramingFollowsTheFinger() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        for (int i = 0; i < 12; i++) click(byDescription("Longer lens"));
        FrameView fv = drawn((FrameView) ofType(cs, FrameView.class));
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        float away = body[0] < fv.getWidth() / 2f ? fv.getWidth() - 40 * k : 40 * k;
        float step = (away > body[0] ? -30 : 30) * k;
        drag(fv, away, body[1] + 100 * k, away + step, body[1] + 100 * k);
        double first = cs.panDeg();
        drag(drawn(fv), away + step, body[1] + 100 * k, away, body[1] + 100 * k);     // and back again
        double back = cs.panDeg();
        assertTrue("it framed: " + first, Math.abs(first) > 0.01);
        assertTrue("it follows the finger both ways: " + first + " then " + back, Math.abs(first - back) > 0.01);
        assertEquals("back where it began", 0, back, 0.05);
        assertInPicture("the sun", drawn(fv), fv.bodyOnScreen());
    }

    /** Review 2: framing remembered from another time never opens with the shot out of the picture; nor after Turn. */
    @Test public void restoredFramingAndTurnKeepTheShotInThePicture() throws Exception {
        ComposeScreen cs = openBuild("30", "25");
        FrameView fv = drawn((FrameView) ofType(cs, FrameView.class));
        assertInPicture("the sun", fv, fv.bodyOnScreen());
        assertSubjectSeen(fv);
        float k = act.getResources().getDisplayMetrics().density;
        float[] body = fv.bodyOnScreen();
        drag(fv, 30 * k, body[1] + 140 * k, fv.getWidth() - 30 * k, body[1] + 140 * k);
        click(byDescription("Turn the camera: landscape or portrait"));
        assertInPicture("the sun after Turn", drawn(fv), fv.bodyOnScreen());
        assertSubjectSeen(fv);
    }

    /** Review 3: the framing that travels is the one on screen, after a lens change too. */
    @Test public void framingAfterALensChangeTravels() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        FrameView fv = (FrameView) ofType(cs, FrameView.class);
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0] - 150 * k, body[1] + 120 * k, body[0] - 90 * k, body[1] + 100 * k);
        click(byDescription("Longer lens"));
        double pan = cs.panDeg();
        clickWhenReady("Fly here");
        click(byDescription("Viewfinder: see the shot as the camera will"));
        FrameView view = (FrameView) ofType(ofType(root(), FrameScreen.class), FrameView.class);
        double you0 = Double.parseDouble(prefs().getString("you_lat", "0")), you1 = Double.parseDouble(prefs().getString("you_lon", "0"));
        assertEquals(0, FrameMath.diff(view.aimAz(), Astro.bearing(you0, you1, SUB_LAT, SUB_LON) + pan), 0.05);
    }

    /** Review 6: Your shot's picture shows what the viewfinder last had, and a saved framed shot opens framed. */
    @Test public void yourShotPictureStaysTrue() throws Exception {
        ComposeScreen cs = openBuild(null, null);
        FrameView fv = (FrameView) ofType(cs, FrameView.class);
        float[] body = fv.bodyOnScreen();
        float k = act.getResources().getDisplayMetrics().density;
        drag(fv, body[0] - 150 * k, body[1] + 120 * k, body[0] - 90 * k, body[1] + 100 * k);
        double pan = cs.panDeg();
        clickWhenReady("Fly here");
        click(byDescription("Save this shot"));
        double you0 = Double.parseDouble(prefs().getString("you_lat", "0")), you1 = Double.parseDouble(prefs().getString("you_lon", "0"));
        double subjectAz = Astro.bearing(you0, you1, SUB_LAT, SUB_LON);
        FrameView pic = (FrameView) byDescription("Your shot as a picture");
        assertEquals(0, FrameMath.diff(pic.aimAz(), subjectAz + pan), 0.05);
        // The viewfinder re-centred: back on Your shot the picture is centred too.
        click(pic);
        ((FrameScreen) ofType(root(), FrameScreen.class)).aimAgain();
        act.onBackPressed();
        idle();
        pic = (FrameView) byDescription("Your shot as a picture");
        assertEquals(0, FrameMath.diff(pic.aimAz(), subjectAz), 0.05);
        // A saved framed shot opens framed at once (it was saved before the re-centring).
        goHome();
        click(byDescription("Saved"));
        click(describedFrom("Open "));
        pic = (FrameView) byDescription("Your shot as a picture");
        assertEquals(0, FrameMath.diff(pic.aimAz(), subjectAz + pan), 0.05);
    }

    /** The framing made in Build the shot is the viewfinder's, and is kept with the saved shot (3.3). */
    @Test public void framingCarriesToTheViewfinder() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true).putBoolean("guide_find", true).putBoolean("guide_choose", true).putBoolean("guide_map", true).putBoolean("guide_now", true).putBoolean("guide_shot", true).putBoolean("guide_frame", true)
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
        clickWhenReady("Fly here");
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
        app.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().putBoolean("tutorial_done", true).putBoolean("guide_line", true).putBoolean("guide_build", true).putBoolean("guide_find", true).putBoolean("guide_choose", true).putBoolean("guide_map", true).putBoolean("guide_now", true).putBoolean("guide_shot", true).putBoolean("guide_frame", true)
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
        assertNotNull(describedFrom("Settings: Sunset ·"));
        clickWhenReady("Fly here");
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
        waitForPrefix("You moved round the far end");
        assertFalse(Math.abs(Double.parseDouble(prefs().getString("you_lat", "0")) - 51.498) < 1e-6);
        click(find("Undo"));
        assertEquals(51.498, Double.parseDouble(prefs().getString("you_lat", "0")), 1e-9);
    }

    /**
     * The guides (3.20): a tool's guide comes the first time the tool starts, the viewfinder's the first time it opens;
     * Back closes each and it is not shown again; Help and the three dots replay them. Every card's picture is saved.
     */
    @Test public void guidesComeOnceAndReplay() throws Exception {
        fresh(YOU, SUB, null);
        prefs().edit().remove("guide_line").remove("guide_build").remove("guide_frame").apply();
        click(byDescription("Line up a street"));
        idle();
        waitFor("Pick a street");
        assertNotNull(find("Skip"));
        shot("26a-guide-line-first-card");
        act.onBackPressed();
        idle();
        assertNull("Back closes the guide", findOrNull("Skip"));
        assertTrue(prefs().getBoolean("guide_line", false));
        assertTrue("the tool is still open", stepBar().startsWith("Step 1 of 4"));
        act.onBackPressed();   // Home
        idle();
        click(byDescription("Line up a street"));
        idle();
        assertNull("seen once: not again", findOrNull("Skip"));
        act.onBackPressed();
        idle();
        click(byDescription("Build the shot"));
        idle();
        waitFor("Place the sun");
        act.onBackPressed();
        idle();
        assertTrue(prefs().getBoolean("guide_build", false));

        // the viewfinder's guide: the first time it opens, then from its Help key
        fresh(YOU, SUB, "1,0,2");
        prefs().edit().remove("guide_frame").apply();
        click(describedFrom("Continue"));
        waitForPrefix("Sun at ");
        click(seeIt());           // See it opens the viewfinder at the soonest moment
        idle();
        waitFor("Swipe");
        assertNotNull(find("Pinch"));
        assertNotNull(walk(root(), "Sideways changes the time", true, false));
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Skip"));
        assertNotNull("Back closed the guide, not the viewfinder", byDescription("Help: how the viewfinder works"));
        click(byDescription("Help: how the viewfinder works"));
        idle();
        assertNotNull(find("Skip"));
        act.onBackPressed();
        idle();

        // every card's picture, at its end and halfway
        for (Tutorial.Kind kind : Tutorial.Kind.values()) {
            Tutorial g = new Tutorial(act, act.palette(), kind, () -> { });
            int n = 0;
            for (View v : all(g)) {
                if (!v.getClass().getName().endsWith("Tutorial$Art")) continue;
                java.lang.reflect.Field tf = v.getClass().getDeclaredField("t");
                tf.setAccessible(true);
                for (float at : new float[]{0.5f, 1f}) {
                    tf.setFloat(v, at);
                    Bitmap b = Bitmap.createBitmap(900, 620, Bitmap.Config.ARGB_8888);
                    new Canvas(b).drawColor(act.palette().bg);
                    v.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(620, View.MeasureSpec.EXACTLY));
                    v.layout(0, 0, 900, 620);
                    v.draw(new Canvas(b));
                    save(b, "27-guide-" + kind.name().toLowerCase(java.util.Locale.ROOT) + "-" + n + (at < 1 ? "a" : "b"));
                }
                n++;
            }
            assertEquals(Tutorial.pages(act, kind).length, n);
        }
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
        int n = 0;                                          // 3.11: each card's picture, at its end
        for (View v : all(root())) {
            if (!v.getClass().getName().endsWith("Tutorial$Art")) continue;
            Bitmap b = Bitmap.createBitmap(900, 700, Bitmap.Config.ARGB_8888);
            new Canvas(b).drawColor(act.palette().bg);
            v.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, 900, 700);
            v.draw(new Canvas(b));
            save(b, "23" + (char) ('b' + n++) + "-tutorial-art");
        }
        assertNotNull(find("Sun or moon"));
        assertNotNull(find("Pick a mode"));                // 3.24.2: three short cards
        assertNotNull(find("See it first"));
        act.onBackPressed();
        idle();
        assertNull(findOrNull("Skip"));
        assertTrue(prefs().getBoolean("tutorial_done", false));
    }

    // ---- helpers ----

    private void waitForDescribed(String start) throws InterruptedException {
        for (int i = 0; i < 600 && describedFrom(root(), start) == null; i++) {
            Thread.sleep(50);
            // the clock moves too: what waits a moment (the shading, 250 ms) comes, as on a phone
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
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
        // In Build the shot the settings are folded (3.4): unfold them first
        if (describedFrom(root(), label + ": ") == null && describedFrom(root(), "Settings: ") != null) click(describedFrom("Settings: "));
        click(describedFrom(label + ": "));
        click(byDescription(value));
        // 3.22: the answer is hidden while Build the shot's settings are open; Done folds them again
        View done = walk(root(), "Done: show the answer", false, true);
        if (done != null) click(done);
    }

    /** 3.23: Stand here / Fly here is greyed while a new answer is worked out; press it once it is ready. */
    private void clickWhenReady(String text) throws InterruptedException {
        for (int i = 0; i < 600 && (findOrNull(text) == null || !findOrNull(text).isEnabled()); i++) {
            Thread.sleep(50);
            idle();
        }
        View v = find(text);
        assertTrue(text + " never became ready", v.isEnabled());
        click(v);
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
    /** 3.8: the soonest opens in the viewfinder; Back from it shows Your shot, where most tests carry on. */
    /** The key that opens the soonest moment: the big "See it in the viewfinder", or the card's small "See it" (3.19.4). */
    private View seeItOrNull() {
        View v = findOrNull("See it in the viewfinder");
        return v != null ? v : walk(root(), "See the soonest in the viewfinder", false, true);
    }

    private View seeIt() {
        View v = seeItOrNull();
        assertNotNull("no key to see the soonest in the viewfinder", v);
        return v;
    }

    private void useSoonest() throws Exception {
        click(seeIt());
        idle();
        closeFrameIfOpen();
    }

    private void closeFrameIfOpen() {
        if (ofType(root(), FrameScreen.class) != null) {
            act.onBackPressed();
            idle();
        }
    }

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
