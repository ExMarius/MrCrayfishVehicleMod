package com.mrcrayfish.vehicle.paper.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuelFlowTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    void freeFuelAddsTheFullIncrementAndChargesNothing() {
        FuelFlow.Step step = FuelFlow.next(0.0F, 1000.0F, 0.0D, 0.0D, 10.0D);
        assertEquals(100.0F, step.newFuel(), EPSILON);
        assertEquals(0.0D, step.charge());
        assertFalse(step.stopped());
        assertEquals(FuelFlow.StopReason.NONE, step.reason());
    }

    @Test
    void pricedFuelChargesExactlyPricePerPercentTimesPercentApplied() {
        FuelFlow.Step step = FuelFlow.next(0.0F, 1000.0F, 1000.0D, 2.5D, 4.0D);
        assertEquals(40.0F, step.newFuel(), EPSILON);
        assertEquals(10.0D, step.charge(), EPSILON);
        assertFalse(step.stopped());
    }

    @Test
    void stopsExactlyAtFullTankWithoutOvershootingCapacity() {
        FuelFlow.Step step = FuelFlow.next(995.0F, 1000.0F, 1000.0D, 0.0D, 10.0D);
        assertEquals(1000.0F, step.newFuel(), EPSILON);
        assertTrue(step.stopped());
        assertEquals(FuelFlow.StopReason.TANK_FULL, step.reason());
    }

    @Test
    void stopsWhenTheBalanceRunsOutMidIncrementAndSpendsExactlyWhatWasLeft() {
        // 4% would normally cost 8, but only 3 is available -> 1.5% applied, all 3 spent.
        FuelFlow.Step step = FuelFlow.next(0.0F, 1000.0F, 3.0D, 2.0D, 4.0D);
        assertEquals(15.0F, step.newFuel(), EPSILON);
        assertEquals(3.0D, step.charge(), EPSILON);
        assertTrue(step.stopped());
        assertEquals(FuelFlow.StopReason.OUT_OF_MONEY, step.reason());
    }

    @Test
    void zeroCapacityTankIsImmediatelyReportedAsFull() {
        FuelFlow.Step step = FuelFlow.next(0.0F, 0.0F, 100.0D, 1.0D, 5.0D);
        assertTrue(step.stopped());
        assertEquals(FuelFlow.StopReason.TANK_FULL, step.reason());
        assertEquals(0.0D, step.charge());
    }

    @Test
    void negativeOrZeroBalanceWithAPriceStopsImmediatelyWithoutCharging() {
        FuelFlow.Step step = FuelFlow.next(0.0F, 1000.0F, 0.0D, 2.0D, 4.0D);
        assertEquals(0.0F, step.newFuel(), EPSILON);
        assertEquals(0.0D, step.charge(), EPSILON);
        assertTrue(step.stopped());
        assertEquals(FuelFlow.StopReason.OUT_OF_MONEY, step.reason());
    }
}
