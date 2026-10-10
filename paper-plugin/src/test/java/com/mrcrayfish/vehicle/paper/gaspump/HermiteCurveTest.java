package com.mrcrayfish.vehicle.paper.gaspump;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HermiteCurveTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void progressZeroReturnsStartPoint() {
        Vector3f start = new Vector3f(1.0F, 2.0F, 3.0F);
        Vector3f end = new Vector3f(4.0F, 5.0F, 6.0F);
        Vector3f result = HermiteCurve.position(start, new Vector3f(0.0F, -5.0F, 0.0F),
                end, new Vector3f(0.0F, 3.0F, 0.0F), 0.0F);
        assertVector(start, result);
    }

    @Test
    void progressOneReturnsEndPoint() {
        Vector3f start = new Vector3f(1.0F, 2.0F, 3.0F);
        Vector3f end = new Vector3f(4.0F, 5.0F, 6.0F);
        Vector3f result = HermiteCurve.position(start, new Vector3f(0.0F, -5.0F, 0.0F),
                end, new Vector3f(0.0F, 3.0F, 0.0F), 1.0F);
        assertVector(end, result);
    }

    @Test
    void straightLineWithZeroTangentsIsLinearInterpolation() {
        Vector3f start = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f end = new Vector3f(10.0F, 0.0F, 0.0F);
        Vector3f zero = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f midpoint = HermiteCurve.position(start, zero, end, zero, 0.5F);
        assertEquals(5.0F, midpoint.x, EPSILON);
    }

    @Test
    void downwardStartTangentPullsCurveBelowTheStraightLine() {
        // Mirrors the original pump renderer: hose leaves the pump heading down
        // (0, -5, 0) before curving up to the nozzle, so early samples should
        // dip below a straight line between the two endpoints.
        Vector3f start = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f end = new Vector3f(4.0F, 1.0F, 0.0F);
        Vector3f startTangent = new Vector3f(0.0F, -5.0F, 0.0F);
        Vector3f endTangent = new Vector3f(3.0F, 0.0F, 0.0F);
        Vector3f quarter = HermiteCurve.position(start, startTangent, end, endTangent, 0.25F);
        assertTrue(quarter.y < 1.0F, "expected the curve to dip below the straight line near the pump");
    }

    @Test
    void sampleProducesSegmentsPlusOnePoints() {
        Vector3f start = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f end = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f[] points = HermiteCurve.sample(start, new Vector3f(0.0F, -1.0F, 0.0F),
                end, new Vector3f(1.0F, 0.0F, 0.0F), 8);
        assertEquals(9, points.length);
        assertVector(start, points[0]);
        assertVector(end, points[8]);
    }

    @Test
    void sampleRejectsFewerThanOneSegment() {
        Vector3f zero = new Vector3f(0.0F, 0.0F, 0.0F);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> HermiteCurve.sample(zero, zero, zero, zero, 0));
    }

    @Test
    void tangentAtStartMatchesStartTangentDirection() {
        Vector3f start = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f end = new Vector3f(1.0F, 1.0F, 0.0F);
        Vector3f startTangent = new Vector3f(0.0F, -5.0F, 0.0F);
        Vector3f endTangent = new Vector3f(3.0F, 0.0F, 0.0F);
        Vector3f tangent = HermiteCurve.tangent(start, startTangent, end, endTangent, 0.0F);
        assertVector(startTangent, tangent);
    }

    @Test
    void tangentAtEndMatchesEndTangentDirection() {
        Vector3f start = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f end = new Vector3f(1.0F, 1.0F, 0.0F);
        Vector3f startTangent = new Vector3f(0.0F, -5.0F, 0.0F);
        Vector3f endTangent = new Vector3f(3.0F, 0.0F, 0.0F);
        Vector3f tangent = HermiteCurve.tangent(start, startTangent, end, endTangent, 1.0F);
        assertVector(endTangent, tangent);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
