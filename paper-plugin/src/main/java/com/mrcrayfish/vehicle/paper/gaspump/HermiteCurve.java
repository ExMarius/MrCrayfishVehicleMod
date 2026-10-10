package com.mrcrayfish.vehicle.paper.gaspump;

import org.joml.Vector3f;

/**
 * Cubic Hermite interpolation between two points, each with its own tangent
 * vector. This is a direct port of the basis functions in the original mod's
 * {@code com.mrcrayfish.vehicle.client.util.HermiteInterpolator} (used there to
 * draw the gas pump's hose as a curved mesh). A vanilla Paper client cannot
 * render custom meshes, so {@link com.mrcrayfish.vehicle.paper.gaspump.GasPumpRig}
 * samples this same curve at a handful of points and places a short display
 * entity between each consecutive pair to approximate it.
 */
public final class HermiteCurve {

    private HermiteCurve() {
    }

    /**
     * Position on the curve between {@code startPoint} (progress 0) and
     * {@code endPoint} (progress 1), using {@code startTangent}/{@code endTangent}
     * as the Hermite control tangents.
     */
    public static Vector3f position(Vector3f startPoint, Vector3f startTangent,
                                    Vector3f endPoint, Vector3f endTangent, float progress) {
        return new Vector3f(
                basisPosition(startPoint.x, endPoint.x, startTangent.x, endTangent.x, progress),
                basisPosition(startPoint.y, endPoint.y, startTangent.y, endTangent.y, progress),
                basisPosition(startPoint.z, endPoint.z, startTangent.z, endTangent.z, progress));
    }

    /** Tangent direction of the curve at {@code progress}, mirroring {@code HermiteInterpolator#angle}. */
    public static Vector3f tangent(Vector3f startPoint, Vector3f startTangent,
                                   Vector3f endPoint, Vector3f endTangent, float progress) {
        return new Vector3f(
                basisTangent(startPoint.x, endPoint.x, startTangent.x, endTangent.x, progress),
                basisTangent(startPoint.y, endPoint.y, startTangent.y, endTangent.y, progress),
                basisTangent(startPoint.z, endPoint.z, startTangent.z, endTangent.z, progress));
    }

    /** Evenly spaced points along the curve from progress 0 to 1 inclusive, {@code segments + 1} points. */
    public static Vector3f[] sample(Vector3f startPoint, Vector3f startTangent,
                                    Vector3f endPoint, Vector3f endTangent, int segments) {
        if (segments < 1) {
            throw new IllegalArgumentException("segments must be at least 1");
        }
        Vector3f[] points = new Vector3f[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float progress = (float) i / (float) segments;
            points[i] = position(startPoint, startTangent, endPoint, endTangent, progress);
        }
        return points;
    }

    private static float basisPosition(float p1, float p2, float t1, float t2, float s) {
        float ss = s * s;
        float sss = ss * s;
        float a1 = 2 * sss - 3 * ss + 1;
        float a2 = -2 * sss + 3 * ss;
        float a3 = sss - 2 * ss + s;
        float a4 = sss - ss;
        return a1 * p1 + a2 * p2 + a3 * t1 + a4 * t2;
    }

    private static float basisTangent(float p1, float p2, float t1, float t2, float s) {
        float ss = s * s;
        float a1 = 6 * ss - 6 * s;
        float a2 = -6 * ss + 6 * s;
        float a3 = 3 * ss - 4 * s + 1;
        float a4 = 3 * ss - 2 * s;
        return a1 * p1 + a2 * p2 + a3 * t1 + a4 * t2;
    }
}
