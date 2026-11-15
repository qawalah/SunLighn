package dev.qawalah.sunlighn;

/**
 * What a camera sees: where the sun or moon lands in a frame pointed along your line.
 * Full-frame sensor (36 x 24 mm), rectilinear lens. Plain Java so it can be tested anywhere.
 */
final class FrameMath {
    private FrameMath() {}

    /** Focal lengths offered, in mm (full-frame equivalent). */
    static final int[] FOCALS = {14, 24, 35, 50, 85, 135, 200, 400};

    static final double FOCAL_MIN = 14, FOCAL_MAX = 600;

    /** What the subject is, so its outline can be drawn to scale, with a usual height in metres for each. */
    static final String[] KINDS = {"Person", "Tree", "House", "Building", "Tower", "Peak"};
    static final double[] KIND_HEIGHTS = {1.8, 10, 8, 30, 100, 300};
    /** Width of the outline as a share of its height. */
    static final double[] KIND_WIDTHS = {0.32, 0.62, 1.25, 0.55, 0.2, 2.6};

    /** The nearest common lens if the focal length is within 7% of it, else the focal length itself. */
    static double snapFocal(double f) {
        for (int std : FOCALS) if (Math.abs(f - std) / std < 0.07) return std;
        return Math.max(FOCAL_MIN, Math.min(FOCAL_MAX, f));
    }

    /** The next common lens above (dir > 0) or below the focal length. */
    static double stepFocal(double f, int dir) {
        if (dir > 0) {
            for (int std : FOCALS) if (std > f + 0.5) return std;
            return FOCALS[FOCALS.length - 1];
        }
        for (int i = FOCALS.length - 1; i >= 0; i--) if (FOCALS[i] < f - 0.5) return FOCALS[i];
        return FOCALS[0];
    }

    /** How high the top of something height metres tall stands above the horizon from metres away, in degrees. */
    static double topAngle(double height, double metres) {
        if (height <= 0 || metres <= 0) return 0;
        return Math.toDegrees(Math.atan2(height, metres));
    }

    // ---- the ground (3.2): you and the subject need not stand on the same level ----

    /** Your eye above your feet, metres. */
    static final double EYE = 1.6;

    /** How much higher the subject's foot is than your eye: the subject's ground less yours, less your eye height. */
    static double rise(double subjectGround, double youGround) {
        return subjectGround - youGround - EYE;
    }

    /** How high the subject's foot is from your eye, degrees: below the horizon (negative) when you stand higher. */
    static double baseAngle(double rise, double metres) {
        if (metres <= 0) return 0;
        return Math.toDegrees(Math.atan2(rise, metres));
    }

    /** How high the subject's top is from your eye, degrees, with its foot {@code rise} metres above your eye. */
    static double topAngle(double height, double metres, double rise) {
        if (metres <= 0) return 0;
        return Math.toDegrees(Math.atan2(rise + Math.max(0, height), metres));
    }

    /**
     * How high the sun or moon must be for where it should sit, with the subject's foot {@code rise} metres above
     * your eye: on the horizon (or on the subject's ground when that stands higher), behind the middle, or just on
     * top. Never below {@link #LOWEST}.
     */
    static double targetAlt(int place, double height, double metres, double rise) {
        double base = baseAngle(rise, metres), top = topAngle(height, metres, rise);
        double alt = place == 0 ? Math.max(LOWEST, base) : place == 1 ? (base + top) / 2 : top + BODY_RADIUS;
        return Math.max(LOWEST, alt);
    }

    /**
     * How far away a subject height metres tall must be for its top to stand topAngle degrees up, with its foot
     * {@code rise} metres above your eye. When you stand so high that the top would be below the horizon from any
     * distance, a quarter of its height is kept, so the answer stays a place to stand.
     */
    static double distanceFor(double height, double topAngle, double rise) {
        double up = Math.max(0.25 * height, height + rise);
        return up / Math.tan(Math.toRadians(Math.max(0.05, Math.min(89, topAngle))));
    }

    /** The sun's or moon's radius in the sky, degrees (both are about half a degree across). */
    static final double BODY_RADIUS = 0.27;
    /** Lower than this the sun is lost in the haze of the horizon: never aim below it. */
    static final double LOWEST = 0.5;

    /**
     * How high the sun or moon must be for where it should sit on a subject height metres tall, seen from metres
     * away: place 0 on the horizon, 1 behind the middle of the subject, 2 just on top (its lower edge on the top).
     */
    static double targetAlt(int place, double height, double metres) {
        return targetAlt(place, height, metres, 0);
    }

    /** The lens (snapped to a common one) that makes the subject a third of the picture's height. */
    static double lensForThird(double height, double metres, boolean portrait) {
        double fov = 3 * topAngle(height, metres);
        if (fov >= 170) return FOCAL_MIN;
        if (fov <= 0) return FOCAL_MAX;
        double f = sensorHeight(portrait) / (2 * Math.tan(Math.toRadians(fov / 2)));
        return snapFocal(Math.max(FOCAL_MIN, Math.min(FOCAL_MAX, f)));
    }

    static double sensorWidth(boolean portrait) { return portrait ? 24 : 36; }
    static double sensorHeight(boolean portrait) { return portrait ? 36 : 24; }

    /** Horizontal field of view in degrees. */
    static double fovH(double focalMm, boolean portrait) {
        return Math.toDegrees(2 * Math.atan(sensorWidth(portrait) / (2 * focalMm)));
    }

    /** Vertical field of view in degrees. */
    static double fovV(double focalMm, boolean portrait) {
        return Math.toDegrees(2 * Math.atan(sensorHeight(portrait) / (2 * focalMm)));
    }

    /** Smallest signed angle from b to a, in -180..180. */
    static double diff(double a, double b) {
        double d = (a - b) % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    /**
     * Where a point at (azimuth, altitude) lands for a camera looking at azCenter, tilted up by pitch degrees.
     * Returns {x, y} in units of the focal length (x to the right, y down), or null when it is behind the camera.
     */
    static double[] project(double az, double alt, double azCenter, double pitch) {
        double da = Math.toRadians(diff(az, azCenter)), a = Math.toRadians(alt), p = Math.toRadians(pitch);
        double vx = Math.cos(a) * Math.sin(da), vy = Math.sin(a), vz = Math.cos(a) * Math.cos(da);
        double y2 = vy * Math.cos(p) - vz * Math.sin(p);
        double z2 = vy * Math.sin(p) + vz * Math.cos(p);
        if (z2 <= 0.02) return null;
        return new double[]{vx / z2, -y2 / z2};
    }

    /**
     * How far to tilt up when the user has not chosen. Wide lenses: the horizon sits a third up from the bottom.
     * Long lenses see only a sliver of sky, so aim at the body that matters, if it is up.
     */
    static double autoPitch(double fovV, double bodyAlt) {
        if (fovV >= 25 || bodyAlt < 0) return fovV / 6;
        return bodyAlt;
    }

    /**
     * Where to aim when the user has not chosen: the horizon a third up from the bottom whenever the body still
     * fits in the picture that way; otherwise up at the body, kept in the upper part of the frame.
     */
    static double aimPitch(double fovV, double bodyAlt) {
        if (bodyAlt < 0 || bodyAlt <= fovV * 0.6) return fovV / 6;
        return bodyAlt - fovV * 0.25;
    }

    /**
     * The opposite of project: the {azimuth, altitude} a point of the picture looks at. x and y are in focal
     * lengths from the middle of the frame (x right, y down), for a camera aimed at azCenter and tilted up by pitch.
     */
    static double[] unproject(double x, double y, double azCenter, double pitch) {
        double p = Math.toRadians(pitch);
        double vx = x, y2 = -y, z2 = 1;
        double vy = y2 * Math.cos(p) + z2 * Math.sin(p);
        double vz = -y2 * Math.sin(p) + z2 * Math.cos(p);
        double n = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double alt = Math.toDegrees(Math.asin(vy / n));
        double az = azCenter + Math.toDegrees(Math.atan2(vx, vz));
        return new double[]{((az % 360) + 360) % 360, alt};
    }

    /** How far away something height metres tall must be for its top to stand topAngle degrees up. */
    static double distanceFor(double height, double topAngle) {
        return height / Math.tan(Math.toRadians(Math.max(0.05, Math.min(89, topAngle))));
    }

    /** AR (3.5): what to add to the compass: the true bearing to a landmark less what the compass read, -180..180. */
    static double headingOffset(double trueBearing, double measured) {
        return diff(trueBearing, measured);
    }
}
