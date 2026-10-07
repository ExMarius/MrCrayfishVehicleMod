package com.mrcrayfish.vehicle.paper.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterVehiclePhysicsTest {
    private static final float EPSILON = 0.000001F;

    @Test
    void releasedBoatSpeedEquationAcceleratesAndClamps() {
        assertEquals(0.5F, WaterVehiclePhysics.updateSpeed(
                0.0F, 1.0F, true, true, 100.0D), EPSILON);
        assertEquals(10.0F, WaterVehiclePhysics.updateSpeed(
                9.8F, 1.0F, true, true, 100.0D), EPSILON);
        assertEquals(-0.5F, WaterVehiclePhysics.updateSpeed(
                0.0F, -1.0F, true, true, 100.0D), EPSILON);
        assertEquals(-4.0F, WaterVehiclePhysics.updateSpeed(
                -3.8F, -1.0F, true, true, 100.0D), EPSILON);
        assertEquals(8.0F, WaterVehiclePhysics.updateSpeed(
                9.8F, 1.0F, true, true, 8.0D), EPSILON);
    }

    @Test
    void releasedBoatSpeedEquationPreservesSourceDamping() {
        assertEquals(4.5F, WaterVehiclePhysics.updateSpeed(
                5.0F, 0.0F, true, true, 100.0D), EPSILON);
        assertEquals(4.25F, WaterVehiclePhysics.updateSpeed(
                5.0F, 1.0F, false, true, 100.0D), EPSILON);
        assertEquals(4.9F, WaterVehiclePhysics.updateSpeed(
                5.0F, 1.0F, true, false, 100.0D), EPSILON);
    }

    @Test
    void releasedBoatSteeringAndAirMultiplierAreExact() {
        assertEquals(17.5F, WaterVehiclePhysics.wheelAngle(35.0F, 10.0F), EPSILON);
        assertEquals(2.9166667F,
                WaterVehiclePhysics.deltaYaw(35.0F, 10.0F, false), EPSILON);
        assertEquals(5.8333335F,
                WaterVehiclePhysics.deltaYaw(35.0F, 10.0F, true), EPSILON);
    }

    @Test
    void releasedBoatSurfaceTargetRisesWithForwardSpeed() {
        assertEquals(64.65D, WaterVehiclePhysics.targetSurfaceY(65.0D, 0.0F), 0.000001D);
        assertEquals(64.9D, WaterVehiclePhysics.targetSurfaceY(65.0D, 10.0F), 0.000001D);
        assertEquals(64.55D, WaterVehiclePhysics.targetSurfaceY(65.0D, -4.0F), 0.000001D);
    }
}
