package com.mrcrayfish.vehicle.paper.station;

import org.joml.Vector3f;

/**
 * Bends the fuel hose between the station's outlet and whatever point the nozzle currently
 * sits at. This is deliberately NOT a tangent-based spline: it is a straight line between the
 * two endpoints plus a bulge that is mathematically zero at both {@code t = 0} and
 * {@code t = 1}. That makes it impossible for the curve to ever swing past either endpoint, no
 * matter how close together the station and the player holding the nozzle are -- there is no
 * derivative/tangent magnitude to get wrong, just two fixed points and a bounded sideways
 * offset in the middle.
 */
public final class HoseCurve {
    private HoseCurve() {
    }

    /**
     * Samples the curve at {@code t} in {@code [0, 1]}.
     *
     * @param start       the fixed end, at the station's outlet
     * @param end         the moving end, at the nozzle
     * @param sagDirection a unit(-ish) direction the hose bulges toward at its midpoint
     * @param sagAmount   how far the midpoint bulges along {@code sagDirection}
     */
    public static Vector3f point(Vector3f start, Vector3f end, Vector3f sagDirection, float sagAmount, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        float inverse = 1.0F - clamped;
        float bulge = 4.0F * clamped * inverse * sagAmount;
        return new Vector3f(
                start.x * inverse + end.x * clamped + sagDirection.x * bulge,
                start.y * inverse + end.y * clamped + sagDirection.y * bulge,
                start.z * inverse + end.z * clamped + sagDirection.z * bulge);
    }
}
