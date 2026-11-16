package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    // ---- the ground (3.2) ----

    @Test public void riseIsTheGroundsLessYourEye() {
        assertEquals(20 - 1.6, FrameMath.rise(120, 100), 1e-9);    // the subject's ground 20 m higher
        assertEquals(-20 - 1.6, FrameMath.rise(100, 120), 1e-9);   // you stand 20 m higher
    }

    @Test public void aSubjectOnAHillStandsHigherInThePicture() {
        // 30 m tall, 300 m away: level ground puts its top at 5.7 degrees; 20 m higher ground at 9.5.
        double level = FrameMath.topAngle(30, 300, 0), hill = FrameMath.topAngle(30, 300, 20);
        assertEquals(5.71, level, 0.01);
        assertEquals(9.46, hill, 0.01);
        assertEquals(3.81, FrameMath.baseAngle(20, 300), 0.01);
        // just on top: the body's lower edge on the top, so a hill asks for a higher sun
        assertTrue(FrameMath.targetAlt(2, 30, 300, 20) > FrameMath.targetAlt(2, 30, 300, 0) + 3.5);
        // on the horizon: the sun must clear the subject's ground when that stands above the horizon
        assertEquals(3.81, FrameMath.targetAlt(0, 30, 300, 20), 0.01);
        assertEquals(FrameMath.LOWEST, FrameMath.targetAlt(0, 30, 300, -20), 1e-9);
    }

    @Test public void distanceForTakesTheGroundIntoAccount() {
        // the top 5 degrees up: level ground 343 m away; with the foot 20 m up the subject looks 50 m tall, so 572 m
        assertEquals(342.9, FrameMath.distanceFor(30, 5, 0), 0.5);
        assertEquals(571.5, FrameMath.distanceFor(30, 5, 20), 0.5);
        // standing far above: never a negative or endless distance
        assertTrue(FrameMath.distanceFor(30, 5, -100) > 0);
        assertEquals(FrameMath.distanceFor(30, 5), FrameMath.distanceFor(30, 5, 0), 1e-9);
    }

    /** AR (3.5): the compass correction is the true bearing to a landmark less what the compass read, -180..180. */
    @org.junit.Test public void headingOffsetFromALandmark() {
        org.junit.Assert.assertEquals(7, FrameMath.headingOffset(107, 100), 1e-9);
        org.junit.Assert.assertEquals(-10, FrameMath.headingOffset(355, 5), 1e-9);
        org.junit.Assert.assertEquals(15, FrameMath.headingOffset(5, 350), 1e-9);
    }

    /** 3.12: the land's horizon: a hill 100 m up 2 km away stands about 2.8° up; far flat sea dips below 0. */
    @Test public void landHorizon() {
        double[] d = {1900, 2000, 2100, 20000};
        double hill = FrameMath.landHorizon(0, d, new double[]{100, 100, 100, 0});
        org.junit.Assert.assertTrue("a lone spike (a tower) is not a hill",
                FrameMath.landHorizon(0, d, new double[]{0, 100, 0, 0}) < 0.1);
        org.junit.Assert.assertEquals(Math.toDegrees(Math.atan2(100 - 1900.0 * 1900 * 0.87 / (2 * 6371000) - 1.6, 1900)), hill, 1e-9);
        org.junit.Assert.assertTrue(hill > 2.8 && hill < 3.1);
        org.junit.Assert.assertTrue("flat far away sits a little below level", FrameMath.landHorizon(0, d, new double[]{0, 0, 0, 0}) < 0);
        org.junit.Assert.assertEquals(-90, FrameMath.landHorizon(0, d, new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN}), 0);
        double[] steps = FrameMath.horizonDistances();
        org.junit.Assert.assertEquals(1000, steps[0], 1e-6);
        org.junit.Assert.assertEquals(25000, steps[steps.length - 1], 1e-6);
    }
}
