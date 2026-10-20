package com.mrcrayfish.vehicle.paper.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Locks the dynamic fuel-load curve demanded for the Paper port: idle engine burns
 * only the idle ratio, load grows with speed and throttle demand, and it saturates at
 * exactly 1.0 at full-burn speed with full throttle. Engine-off/resting/showcase never
 * reach fuelLoad() in LandVehicle.tick, so their consumption is strictly 0 by design. */
final class LandVehicleFuelTest {
    private static final double EPSILON = 1.0E-6D;
    /** Mirrors the shipped config.yml physics block (idle 10 %, 0.6 speed + 0.4 accel,
     * full burn at 18 blocks/s). */
    private static final DriveTuning TUNING =
            new DriveTuning(100.0D, 1.0D, 0.1D, 0.6D, 0.4D, 18.0D, 1.0D);

    @Test
    void engineOnAtStandstillBurnsOnlyTheIdleRatio() {
        assertEquals(0.1D, LandVehicle.fuelLoad(TUNING, 0.0D, 0.0D), EPSILON);
    }

    @Test
    void cruisingAddsTheSpeedWeightedTerm() {
        // 50 % of full-burn speed: idle + (1-idle) * 0.6 * 0.5 = 0.37
        assertEquals(0.37D, LandVehicle.fuelLoad(TUNING, 9.0D, 0.0D), EPSILON);
    }

    @Test
    void throttleDemandAddsTheAccelWeightedTerm() {
        // standing still, full throttle: idle + (1-idle) * 0.4 = 0.46
        assertEquals(0.46D, LandVehicle.fuelLoad(TUNING, 0.0D, 1.0D), EPSILON);
    }

    @Test
    void fullBurnSpeedWithFullThrottleSaturatesAtExactlyOne() {
        assertEquals(1.0D, LandVehicle.fuelLoad(TUNING, 18.0D, 1.0D), EPSILON);
    }

    @Test
    void nonFiniteInputsFallBackToPureIdleBurn() {
        // NaN/Infinity leaking from corrupted motion or third-party plugins must never
        // reach the tank: Math.min/max propagate NaN, which would brick refueling.
        assertEquals(0.1D, LandVehicle.fuelLoad(TUNING, Double.NaN, 1.0D), EPSILON);
        assertEquals(0.1D, LandVehicle.fuelLoad(TUNING, 9.0D, Double.NaN), EPSILON);
        assertEquals(1.0D, LandVehicle.fuelLoad(TUNING, Double.POSITIVE_INFINITY, 1.0D), EPSILON);
        assertEquals(0.1D, LandVehicle.fuelLoad(TUNING, Double.NEGATIVE_INFINITY, 0.0D), EPSILON);
    }

    @Test
    void loadIsClampedToTheValidRange() {
        assertEquals(1.0D, LandVehicle.fuelLoad(TUNING, 1000.0D, 1.0D), EPSILON);
        assertEquals(0.1D, LandVehicle.fuelLoad(TUNING, -5.0D, -1.0D), EPSILON);
    }
}
