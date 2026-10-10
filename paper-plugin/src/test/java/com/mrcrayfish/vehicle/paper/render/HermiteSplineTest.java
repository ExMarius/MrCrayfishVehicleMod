package com.mrcrayfish.vehicle.paper.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HermiteSplineTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void endpointsMatchTheControlPoints() {
        HermiteSpline spline = new HermiteSpline(
                new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, -5.0F, 0.0F),
                new Vector3f(3.0F, 0.0F, 4.0F), new Vector3f(0.0F, 3.0F, 0.0F));

        assertVector(spline.point(0.0F), 0.0F, 1.0F, 0.0F);
        assertVector(spline.point(1.0F), 3.0F, 0.0F, 4.0F);
    }

    @Test
    void tangentsBendTheCurveAwayFromAStraightLine() {
        // A straight line from (0,0,0) to (0,0,10) with tangents that both point straight
        // down its own length is just that line -- replicating the original mod's "idle"
        // control points (hose start tangent (0,-5,0), idle end tangent (0,3,0)) bends
        // noticeably off that line partway through, exactly like the original's draped hose.
        HermiteSpline straight = new HermiteSpline(
                new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 10.0F),
                new Vector3f(0.0F, 0.0F, 10.0F), new Vector3f(0.0F, 0.0F, 10.0F));
        assertVector(straight.point(0.5F), 0.0F, 0.0F, 5.0F);

        HermiteSpline bent = new HermiteSpline(
                new Vector3f(0.0F, 10.0F, 0.0F), new Vector3f(0.0F, -5.0F, 0.0F),
                new Vector3f(0.0F, 0.1F, 2.0F), new Vector3f(0.0F, 3.0F, 0.0F));
        Vector3f midpoint = bent.point(0.5F);
        // The straight-line midpoint between the two control points would sit at y=5.05;
        // the Hermite curve (hand-computed from the textbook basis functions) sags to
        // y=4.05 instead because both tangents point downward, same shape of sag the
        // original mod's idle hose draws between a pump and its resting nozzle holder.
        assertTrue(midpoint.y < 5.05F, "expected the tangents to pull the curve below the straight-line "
                + "midpoint (5.05), got " + midpoint);
        assertVector(midpoint, 0.0F, 4.05F, 1.0F);
    }

    private static void assertVector(Vector3f actual, float x, float y, float z) {
        assertEquals(x, actual.x, EPSILON);
        assertEquals(y, actual.y, EPSILON);
        assertEquals(z, actual.z, EPSILON);
    }
}
