package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Saved grouped by city (3.29): the city read from the place answer, kept with the shot, and the groups' order. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CityGroupTest {
    private static Trip.Line shot(String id, String city) {
        Trip.Line l = new Trip.Line(id, id, 26.28, 50.21, 26.29, 50.22, 0);
        l.city = city;
        return l;
    }

    @Test public void theCityIsReadFromThePlaceAnswer() {
        assertEquals("Jeddah", Places.cityName("{\"address\":{\"suburb\":\"Al Shati\",\"city\":\"Jeddah\"}}"));
        assertEquals("Zermatt", Places.cityName("{\"address\":{\"town\":\"Zermatt\",\"state\":\"Valais\"}}"));
        assertNull(Places.cityName("{\"address\":{\"quarter\":\"Westminster\"}}"));
        assertNull(Places.cityName("not json"));
    }

    @Test public void shotsAreGroupedByCityInTheOrderSaved() {
        List<Trip.Line> in = Arrays.asList(shot("a", "Lisbon"), shot("b", null), shot("c", "Porto"),
                shot("d", "Lisbon"), shot("e", ""));
        Map<String, List<Trip.Line>> g = Trip.byCity(in);
        assertEquals(Arrays.asList("Lisbon", "Porto", "Other places", "Place not known yet"), new ArrayList<>(g.keySet()));
        assertEquals(2, g.get("Lisbon").size());
        assertEquals("d", g.get("Lisbon").get(1).id);
    }

    @Test public void theCityIsKept() {
        android.content.Context c = RuntimeEnvironment.getApplication();
        Trip.saveLines(c, new ArrayList<>(Arrays.asList(shot("a", "Lisbon"), shot("b", null))));
        List<Trip.Line> out = Trip.loadLines(c);
        assertEquals("Lisbon", out.get(0).city);
        assertNull(out.get(1).city);
        Trip.setCity(c, "b", "");
        assertEquals("", Trip.loadLines(c).get(1).city);
    }
}
