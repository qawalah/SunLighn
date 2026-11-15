package dev.qawalah.sunlighn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FlowTest {
    @Test public void backStepsThenHomeThenLeaves() {
        Flow f = new Flow();
        f.start(Flow.STAND);
        f.next();
        f.next();
        assertEquals(Flow.SPOTS, f.current());
        assertTrue(f.back());
        assertEquals(Flow.CRITERIA, f.current());
        assertTrue(f.back());
        assertEquals(Flow.SUBJECT, f.current());
        assertTrue(f.back());
        assertEquals(Flow.HOME, f.tool);
        assertFalse(f.back());
    }

    @Test public void stepsPerTool() {
        assertArrayEquals(new int[]{0, 1, 2, 6}, Flow.steps(Flow.LINE));
        assertArrayEquals(new int[]{1, 3, 4, 6}, Flow.steps(Flow.STAND));
        assertArrayEquals(new int[]{1, 5, 6}, Flow.steps(Flow.BUILD));
        assertEquals(0, Flow.steps(Flow.HOME).length);
    }

    @Test public void nextStopsAtLast() {
        Flow f = new Flow();
        f.start(Flow.BUILD);
        f.go(Flow.SHOT);
        assertFalse(f.next());
        assertEquals(Flow.SHOT, f.current());
    }

    @Test public void encodeRoundTrip() {
        Flow f = new Flow();
        f.start(Flow.LINE);
        f.moon = true;
        f.step = 2;
        Flow g = Flow.decode(f.encode());
        assertEquals(Flow.LINE, g.tool);
        assertTrue(g.moon);
        assertEquals(2, g.step);
        assertEquals(Flow.HOME, Flow.decode("x").tool);
        assertFalse(Flow.decode(null).moon);
        assertEquals(Flow.HOME, Flow.decode(null).tool);
        assertEquals(3, Flow.decode("2,0,9").step);
    }

    @Test public void goJumpsToStep() {
        Flow f = new Flow();
        f.start(Flow.LINE);
        f.go(Flow.SHOT);
        assertEquals(3, f.step);
    }

    @Test public void barText() {
        Flow f = new Flow();
        f.start(Flow.STAND);
        f.next();
        assertEquals("2 of 4 · How you want it", f.bar());
    }
}
