package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** Line up a street (3.24): where along the street the sun clears what hides the far end. */
public class StreetTest {
    @Test public void offsetsRunFromBehindYouToTheFarEndWithYouAmongThem() {
        double[] o = Street.offsets(50, 100);
        assertEquals(-40, o[0], 1e-9);
        assertEquals(100, o[o.length - 1], 1e-9);
        boolean you = false;
        for (double x : o) you |= x == 0;
        assertTrue(you);
    }

    @Test public void stretchesWhereTheSunClears() {
        double[] off = {-40, -20, 0, 20, 40, 60, 80};
        double[] hide = {1, 1, 5, 5, 1, 1, 6};                    // a block near you, another at the end
        List<double[]> s = Street.clear(off, hide, 3);
        assertEquals(2, s.size());
        assertEquals(-40, s.get(0)[0], 1e-9);
        assertEquals(-20, s.get(0)[1], 1e-9);
        assertEquals(40, s.get(1)[0], 1e-9);
        assertEquals(60, s.get(1)[1], 1e-9);
        assertNull(Street.around(s));                             // hidden where you stand
        assertEquals(-20, Street.nearest(s), 1e-9);               // the nearest clear point, 20 m back
        assertTrue(Double.isNaN(Street.nearest(Street.clear(off, hide, 0.5))));
        assertEquals(0, Street.nearest(Street.clear(off, new double[]{0, 0, 0, 0, 0, 0, 0}, 3)), 1e-9);
    }

    @Test public void words() {
        assertEquals("where you stand", Street.words(2));
        assertTrue(Street.words(120).endsWith("towards the far end"));
        assertTrue(Street.words(-40).endsWith("away from the far end"));
    }
}
