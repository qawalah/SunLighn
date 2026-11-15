package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** Shots: saved in one press, named by place later, and the 2.1 lines and planned shoots carried over. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShotsTest {
    private Context c;

    @Before public void setUp() {
        c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static Trip.Line line(String id, String name) {
        return new Trip.Line(id, name, 51.5, -0.12, 51.49, -0.13, 0);
    }

    @Test public void nextShotName() {
        List<Trip.Line> two = new ArrayList<>();
        two.add(line("a", "Shot 1"));
        two.add(line("b", "Tower Bridge, London"));
        assertEquals("Shot 2", Trip.nextShotName(two));
        two.add(line("c", "Shot 2"));
        assertEquals("Shot 3", Trip.nextShotName(two));
    }

    /** With no network the shot keeps its number name, marked as not named yet. */
    @Test public void unnamedShotKeepsNumberName() {
        Trip.Line l = line(null, null);
        l.timeMs = 1794700800000L;
        Trip.Line saved = Trip.addShot(c, l);
        assertEquals("Shot 1", saved.name);
        assertFalse(saved.named);
        Trip.Line back = Trip.loadLines(c).get(0);
        assertEquals("Shot 1", back.name);
        assertEquals(1794700800000L, back.timeMs);
        assertFalse(back.named);
    }

    /** A shot saved before 3.3 has no framing: it opens centred, at the usual tilt. */
    @Test public void oldShotHasNoFraming() {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putString("trip_lines",
                "[{\"id\":\"a\",\"name\":\"Old\",\"yLat\":51.5,\"yLon\":-0.12,\"sLat\":51.49,\"sLon\":-0.13,\"mode\":0}]").apply();
        Trip.Line l = Trip.loadLines(c).get(0);
        assertEquals(0, l.panDeg, 0);
        assertTrue(Double.isNaN(l.pitch));
    }

    /** Two shots named from the same place are told apart (Ali, 2026-11-15). */
    @Test public void namesFromOnePlaceAreToldApart() {
        java.util.List<Trip.Line> all = new java.util.ArrayList<>();
        all.add(new Trip.Line("a", "Millbank, City of Westminster", 51.5, -0.12, 51.49, -0.13, 0));
        all.add(new Trip.Line("b", "Shot 2", 51.5, -0.12, 51.49, -0.13, 1));
        assertEquals("Millbank, City of Westminster 2", Trip.uniqueName(all, "b", "Millbank, City of Westminster"));
        assertEquals("its own name stays", "Millbank, City of Westminster",
                Trip.uniqueName(all, "a", "Millbank, City of Westminster"));
        assertEquals("Tower Bridge", Trip.uniqueName(all, "b", "Tower Bridge"));
    }

    /** Review 7: a shot is named from its own name, never from whatever subject is on the map now. */
    @Test public void shotNameUsesItsOwnSubject() {
        assertEquals("Kingdom Tower, Al Olaya, Riyadh", Trip.shotName("Kingdom Tower", "Al Olaya, Riyadh"));
        assertEquals("Al Olaya, Riyadh", Trip.shotName("Shot 4", "Al Olaya, Riyadh"));
        assertEquals("Riyadh", Trip.shotName("Riyadh", "Riyadh"));
    }

    @Test public void placeNameFromJson() throws Exception {
        assertEquals("Al Shati, Jeddah", Places.placeName("{\"address\":{\"suburb\":\"Al Shati\",\"city\":\"Jeddah\"}}"));
        assertEquals("Zermatt, Valais", Places.placeName("{\"address\":{\"town\":\"Zermatt\",\"state\":\"Valais\"}}"));
        assertEquals("Westminster", Places.placeName("{\"address\":{\"quarter\":\"Westminster\"}}"));
        assertNull(Places.placeName("{}"));
        assertNull(Places.placeName("not json"));
    }

    /** 2.1 data: a saved line stays (now a shot with no time); a planned shoot not on a saved line becomes a shot. */
    @Test public void migratesLinesAndShoots() throws Exception {
        long t = System.currentTimeMillis() + 86400000L;
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit()
                .putString("trip_lines", "[{\"id\":\"L1\",\"name\":\"Line 1\",\"yLat\":51.5,\"yLon\":-0.12,"
                        + "\"sLat\":51.49,\"sLon\":-0.13,\"mode\":0}]").apply();
        String key = Reminders.key("map", true, t);
        Reminders.add(c, new Reminders.Item(key, t, "Moon behind subject at 6:00", "Planned in the viewfinder"));
        Shoots.put(c, key, t, line("map", ""), 85, false);

        // And a shoot planned on the saved line (review 6): it becomes a timed shot too, with its reminder.
        String onLine = Reminders.key("L1", false, t + 3600000L);
        Reminders.add(c, new Reminders.Item(onLine, t + 3600000L, "Sun behind subject at 7:00", "Line 1"));
        Shoots.put(c, onLine, t + 3600000L, line("L1", "Line 1"), 35, false);

        Trip.migrate(c);
        List<Trip.Line> all = Trip.loadLines(c);
        assertEquals(3, all.size());
        assertEquals("Line 1", all.get(0).name);
        assertEquals(0, all.get(0).timeMs);
        Trip.Line shot = null, sunShot = null;
        for (Trip.Line l : all) {
            if (l.timeMs == t) shot = l;
            if (l.timeMs == t + 3600000L) sunShot = l;
        }
        assertTrue(shot.moon);
        assertTrue("its reminder moved with it", Reminders.has(c, Reminders.key(shot.id, true, t)));
        assertFalse(Reminders.has(c, key));
        assertFalse(sunShot.moon);
        assertTrue(Reminders.has(c, Reminders.key(sunShot.id, false, t + 3600000L)));

        Trip.migrate(c);                       // once only
        assertEquals(3, Trip.loadLines(c).size());
    }
}
