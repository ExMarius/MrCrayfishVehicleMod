package com.mrcrayfish.vehicle.paper.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterVehiclePhysicsTest {
    private static final float EPSILON = 0.000001F;

    @Test
    void releasedBoatSpeedEquationAcceleratesAndClamps() {
        assertEquals(0.5F, WaterVehiclePhysics.updateSpeed(
                0.0F, 1.0F, true, true, 100.0D, 10.0F, 4.0F), EPSILON);
        assertEquals(10.0F, WaterVehiclePhysics.updateSpeed(
                9.8F, 1.0F, true, true, 100.0D, 10.0F, 4.0F), EPSILON);
        assertEquals(-0.5F, WaterVehiclePhysics.updateSpeed(
                0.0F, -1.0F, true, true, 100.0D, 10.0F, 4.0F), EPSILON);
        assertEquals(-4.0F, WaterVehiclePhysics.updateSpeed(
                -3.8F, -1.0F, true, true, 100.0D, 10.0F, 4.0F), EPSILON);
        assertEquals(8.0F, WaterVehiclePhysics.updateSpeed(
                9.8F, 1.0F, true, true, 8.0D, 10.0F, 4.0F), EPSILON);
    }

    @Test
    void releasedBoatSpeedEquationPreservesSourceDamping() {
        assertEquals(4.5F, WaterVehiclePhysics.updateSpeed(
                5.0F, 0.0F, true, true, 100.0D, 10.0F, 4.0F), EPSILON);
        assertEquals(4.25F, WaterVehiclePhysics.updateSpeed(
                5.0F, 1.0F, false, true, 100.0D, 10.0F, 4.0F), EPSILON);
        assertEquals(4.9F, WaterVehiclePhysics.updateSpeed(
                5.0F, 1.0F, true, false, 100.0D, 10.0F, 4.0F), EPSILON);
    }

    @Test
    void eachWaterVehicleAcceleratesToItsOwnEnginePowerInsteadOfASharedCap() {
        /* Speed Boat (enginePower 20) and Jet Ski (18) must not be flattened to
         * Aluminum Boat's (10) top speed just because they share one physics
         * routine - this was a real inconsistency found while diagnosing the
         * reported "boat rises when moving forward" behavior (r37 removed that
         * speed-dependent lift/sink entirely per explicit user direction; this
         * speed cap was a separate, still-applicable fix). */
        assertEquals(20.0F, WaterVehiclePhysics.updateSpeed(
                19.8F, 1.0F, true, true, 100.0D, 20.0F, 4.0F), EPSILON);
        assertEquals(18.0F, WaterVehiclePhysics.updateSpeed(
                17.8F, 1.0F, true, true, 100.0D, 18.0F, 4.0F), EPSILON);
        assertEquals(10.0F, WaterVehiclePhysics.updateSpeed(
                9.8F, 1.0F, true, true, 100.0D, 10.0F, 4.0F), EPSILON);
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
    void carriedJetSkiStartsAtItsRestingWaterline() {
        /* r37: per explicit user direction, this no longer matches the genuine
         * released source height (waterLevel - 0.35); see
         * WaterVehiclePhysics.targetSurfaceY and SOURCE_POSITION_AUDIT.md r37. */
        assertEquals(64.9D, WaterVehiclePhysics.restingSurfaceY(65.0D), 0.000001D);
    }

    @Test
    void releasedBoatSurfaceTargetNoLongerSinksWhileIdle() {
        /* r37: per explicit user direction, boats no longer sit lower in the water
         * while idle than while moving - every speed (including reverse) now
         * resolves to the same height. This is a disclosed deviation from the
         * genuine released source formula ("waterLevel - 0.35 + 0.25 *
         * min(1, speed/maxForwardSpeed)"), not a reproduction of it; see
         * SOURCE_POSITION_AUDIT.md r37 entry. */
        assertEquals(64.9D, WaterVehiclePhysics.targetSurfaceY(65.0D, 0.0F, 10.0F), 0.000001D);
        assertEquals(64.9D, WaterVehiclePhysics.targetSurfaceY(65.0D, 10.0F, 10.0F), 0.000001D);
        assertEquals(64.9D, WaterVehiclePhysics.targetSurfaceY(65.0D, -4.0F, 10.0F), 0.000001D);
        assertEquals(64.9D, WaterVehiclePhysics.targetSurfaceY(65.0D, 10.0F, 20.0F), 0.000001D);
    }
}
