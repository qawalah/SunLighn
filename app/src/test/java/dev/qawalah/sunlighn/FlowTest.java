package dev.qawalah.sunlighn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FlowTest {
    @Test public void backStepsThenHomeThenLeaves() {
        Flow f = new Flow();
        f.start(Flow.BUILD);
        f.next();
        f.next();
        assertEquals(Flow.SHOT, f.current());
        assertTrue(f.back());
        assertEquals(Flow.COMPOSE, f.current());
        assertTrue(f.back());
        assertEquals(Flow.SUBJECT, f.current());
        assertTrue(f.back());
        assertEquals(Flow.HOME, f.tool);
        assertFalse(f.back());
    }

    @Test public void stepsPerTool() {
        assertArrayEquals(new int[]{1, 0, 2, 6}, Flow.steps(Flow.LINE));
        assertArrayEquals(new int[]{1, 5, 6}, Flow.steps(Flow.BUILD));
        assertEquals(0, Flow.steps(Flow.HOME).length);
        assertArrayEquals(new int[]{1, 9, 7, 6}, Flow.steps(Flow.FIND));  // 3.24.1: subject, the sun, where to stand, your shot
        assertArrayEquals(new int[]{8}, Flow.steps(Flow.NOW));
    }

    /** 3.24: Line up a street asks for a line: its first step is the far end. Now is one screen, named alone. */
    @Test public void titlesPerTool() {
        Flow f = new Flow();
        f.start(Flow.LINE);
        assertEquals("1 of 4 · The far end", f.bar());
        f.start(Flow.FIND);
        assertEquals("1 of 4 · Your subject", f.bar());
        f.next();
        assertEquals("2 of 4 · The sun", f.bar());
        f.next();
        assertEquals("3 of 4 · Where to stand", f.bar());
        assertEquals(f.step, Flow.decode(f.encode()).step);
        f.start(Flow.NOW);
        assertEquals("Now", f.bar());
        assertTrue(f.back());
        assertEquals(Flow.HOME, f.tool);
        assertEquals(Flow.FIND, Flow.decode("4,0,1").tool);
        assertEquals("a 3.24.0 route (no sun step) keeps its place", Flow.SPOTS, Flow.decode("4,0,1").current());
        assertEquals(Flow.SUN, Flow.decode("4,0,1+").current());
        assertEquals(Flow.SUBJECT, Flow.decode("4,0,0").current());
        assertEquals(Flow.HOME, Flow.decode("6,0,0").tool);
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
        assertEquals(2, Flow.decode("3,0,9").step);
    }

    /** A Where to stand route saved before 3.6 opens Build the shot at the same place. */
    @Test public void oldWhereToStandOpensBuildTheShot() {
        Flow f = Flow.decode("2,1,1");             // How you want it
        assertEquals(Flow.BUILD, f.tool);
        assertTrue(f.moon);
        assertEquals(Flow.COMPOSE, f.current());
        assertEquals(Flow.COMPOSE, Flow.decode("2,0,2").current());   // the spots
        assertEquals(Flow.SHOT, Flow.decode("2,0,3").current());
        assertEquals(Flow.SUBJECT, Flow.decode("2,0,0").current());
    }

    @Test public void goJumpsToStep() {
        Flow f = new Flow();
        f.start(Flow.LINE);
        f.go(Flow.SHOT);
        assertEquals(3, f.step);
    }

    @Test public void barText() {
        Flow f = new Flow();
        f.start(Flow.BUILD);
        f.next();
        assertEquals("2 of 3 · Build it", f.bar());
    }
}
