package com.mrcrayfish.vehicle.paper.station;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoseCurveTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void startAndEndAreExact() {
        Vector3f start = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f end = new Vector3f(10.0F, 2.0F, -3.0F);
        Vector3f sag = new Vector3f(0.0F, -1.0F, 0.0F);

        assertVectorEquals(start, HoseCurve.point(start, end, sag, 5.0F, 0.0F));
        assertVectorEquals(end, HoseCurve.point(start, end, sag, 5.0F, 1.0F));
    }

    @Test
    void midpointBulgesExactlyByTheRequestedAmountAlongSagDirection() {
        Vector3f start = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f end = new Vector3f(4.0F, 0.0F, 0.0F);
        Vector3f sag = new Vector3f(0.0F, -1.0F, 0.0F);

        Vector3f midpoint = HoseCurve.point(start, end, sag, 2.0F, 0.5F);
        // The straight-line component at t=0.5 is (2, 0, 0); the bulge term is
        // 4*0.5*0.5*sagAmount = sagAmount, applied along sagDirection.
        assertVectorEquals(new Vector3f(2.0F, -2.0F, 0.0F), midpoint);
    }

    @Test
    void neverOvershootsPastEitherEndpointEvenWhenEndpointsAreVeryClose() {
        // This is the specific scenario that broke the old tangent-based spline: the two
        // endpoints almost coincide. The new curve has no tangent to mis-scale, so it should
        // stay bounded close to the two (nearly identical) endpoints at every t.
        Vector3f start = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f end = new Vector3f(0.05F, 0.0F, 0.0F);
        Vector3f sag = new Vector3f(0.0F, -1.0F, 0.0F);

        for (float t = 0.0F; t <= 1.0F; t += 0.1F) {
            Vector3f point = HoseCurve.point(start, end, sag, 0.3F, t);
            assertTrue(point.x >= -0.1F && point.x <= 0.15F, "x should stay close to the endpoints: " + point);
            assertTrue(point.y >= -0.35F && point.y <= 0.01F, "y should stay bounded by the sag amount: " + point);
        }
    }

    @Test
    void tOutsideZeroOneIsClamped() {
        Vector3f start = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f end = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f sag = new Vector3f(0.0F, -1.0F, 0.0F);

        assertVectorEquals(start, HoseCurve.point(start, end, sag, 1.0F, -5.0F));
        assertVectorEquals(end, HoseCurve.point(start, end, sag, 1.0F, 5.0F));
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
