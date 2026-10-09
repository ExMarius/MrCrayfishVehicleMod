package com.mrcrayfish.vehicle.paper.render;

import org.joml.Vector3f;

/**
 * Two-point Hermite (cubic) spline helper, ported from the original mod's client-only
 * {@code com.mrcrayfish.vehicle.client.util.HermiteInterpolator} (used there to bend the
 * gas pump's hose toward the player). The math is identical; only the vector type changed
 * (plain {@link Vector3f} instead of Minecraft's client-only {@code Vector3d}) since this
 * runs on the server, which has no client classes available.
 *
 * @see <a href="https://www.cubic.org/docs/hermite.htm">Nils Pipenbrinck's original writeup</a>
 */
public final class HermiteSpline {
    private final Vector3f startPos;
    private final Vector3f startTangent;
    private final Vector3f endPos;
    private final Vector3f endTangent;

    public HermiteSpline(Vector3f startPos, Vector3f startTangent, Vector3f endPos, Vector3f endTangent) {
        this.startPos = startPos;
        this.startTangent = startTangent;
        this.endPos = endPos;
        this.endTangent = endTangent;
    }

    /** Samples the curve's position at {@code s} in {@code [0, 1]}. */
    public Vector3f point(float s) {
        return new Vector3f(
                hermite(startPos.x, endPos.x, startTangent.x, endTangent.x, s),
                hermite(startPos.y, endPos.y, startTangent.y, endTangent.y, s),
                hermite(startPos.z, endPos.z, startTangent.z, endTangent.z, s));
    }

    private static float hermite(float p1, float p2, float t1, float t2, float s) {
        float ss = s * s;
        float sss = ss * s;
        float a1 = 2 * sss - 3 * ss + 1;
        float a2 = -2 * sss + 3 * ss;
        float a3 = sss - 2 * ss + s;
        float a4 = sss - ss;
        return a1 * p1 + a2 * p2 + a3 * t1 + a4 * t2;
    }
}
