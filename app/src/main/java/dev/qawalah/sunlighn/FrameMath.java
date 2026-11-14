package dev.qawalah.sunlighn;

/**
 * What a camera sees: where the sun or moon lands in a frame pointed along your line.
 * Full-frame sensor (36 x 24 mm), rectilinear lens. Plain Java so it can be tested anywhere.
 */
final class FrameMath {
    private FrameMath() {}

    /** Focal lengths offered, in mm (full-frame equivalent). */
    static final int[] FOCALS = {14, 24, 35, 50, 85, 135, 200, 400};

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
}
