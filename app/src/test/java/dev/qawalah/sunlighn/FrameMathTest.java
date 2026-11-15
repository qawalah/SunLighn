package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FrameMathTest {
    @Test public void fieldOfView() {
        assertEquals(39.6, FrameMath.fovH(50, false), 0.1);   // a 50 mm lens
        assertEquals(73.7, FrameMath.fovH(24, false), 0.1);
        assertEquals(27.0, FrameMath.fovV(50, false), 0.1);
        assertEquals(27.0, FrameMath.fovH(50, true), 0.1);    // portrait swaps the two
    }

    @Test public void whatIsAimedAtIsInTheMiddle() {
        double[] c = FrameMath.project(74, 12, 74, 12);
        assertEquals(0, c[0], 1e-9);
        assertEquals(0, c[1], 1e-9);
    }

    @Test public void rightIsRightAndUpIsUp() {
        double[] r = FrameMath.project(84, 0, 74, 0);
        assertEquals(Math.tan(Math.toRadians(10)), r[0], 1e-6);   // 10 degrees to the right
        double[] up = FrameMath.project(74, 10, 74, 0);
        assertEquals(-Math.tan(Math.toRadians(10)), up[1], 1e-6); // y grows downwards, so up is negative
        double[] left = FrameMath.project(64, 0, 74, 0);
        assertEquals(-Math.tan(Math.toRadians(10)), left[0], 1e-6);
    }

    @Test public void tiltingUpPutsTheHorizonBelowCentre() {
        double[] h = FrameMath.project(74, 0, 74, 8);
        assertEquals(Math.tan(Math.toRadians(8)), h[1], 1e-6);
    }

    @Test public void wrapsAroundNorthAndIgnoresWhatIsBehind() {
        assertEquals(20, FrameMath.diff(10, 350), 1e-9);
        assertEquals(-20, FrameMath.diff(350, 10), 1e-9);
        assertNotNull(FrameMath.project(2, 0, 358, 0));
        assertNull(FrameMath.project(254, 0, 74, 0));              // directly behind the camera
    }

    @Test public void autoPitchKeepsAShortLensOnTheHorizonThirdAndALongLensOnTheSun() {
        assertEquals(40 / 6.0, FrameMath.autoPitch(40, 30), 1e-9);
        assertEquals(30, FrameMath.autoPitch(3, 30), 1e-9);
        assertEquals(3 / 6.0, FrameMath.autoPitch(3, -5), 1e-9);
    }

    /** Where the sun sits on the subject: on the horizon, behind its middle, or just on its top. */
    @Test public void targetAltPlaces() {
        assertEquals(0.5, FrameMath.targetAlt(0, 30, 300), 1e-9);
        assertEquals(Math.toDegrees(Math.atan2(30, 300)) / 2, FrameMath.targetAlt(1, 30, 300), 0.01);
        assertEquals(Math.toDegrees(Math.atan2(30, 300)) + 0.27, FrameMath.targetAlt(2, 30, 300), 0.01);
        assertEquals(0.5, FrameMath.targetAlt(2, 0, 300), 1e-9);          // no height: the floor
        assertEquals(0.5, FrameMath.targetAlt(1, 1.8, 3000), 1e-9);       // tiny: the floor
    }

    /** The lens that makes the subject a third of the picture's height. */
    @Test public void lensForAThird() {
        // 30 m at 300 m is 5.71 degrees tall; a third of the frame is 5.71, so the frame is 17.1 degrees tall:
        // 24 mm / (2 tan 8.57) = 79.6 mm, which snaps to 85.
        assertEquals(85, FrameMath.lensForThird(30, 300, false), 1e-9);
        assertEquals(FrameMath.FOCAL_MAX, FrameMath.lensForThird(1.8, 5000, false), 1e-9);
        assertEquals(FrameMath.FOCAL_MIN, FrameMath.lensForThird(300, 50, false), 1e-9);
    }
}
