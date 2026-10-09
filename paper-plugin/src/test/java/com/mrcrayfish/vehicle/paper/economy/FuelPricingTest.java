package com.mrcrayfish.vehicle.paper.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuelPricingTest {

    @Test
    void stoppedImmediatelyWhenTankAlreadyFull() {
        FuelPricing.Step step = FuelPricing.step(100.0F, 100.0F, 1_000.0D, 1.0D, 5.0D);
        assertEquals(100.0F, step.newFuel());
        assertEquals(0.0D, step.charge());
        assertTrue(step.stopped());
        assertEquals(FuelPricing.Reason.FULL, step.reason());
    }

    @Test
    void appliesFullIncrementAndChargesExactly() {
        FuelPricing.Step step = FuelPricing.step(0.0F, 100.0F, 1_000.0D, 2.0D, 5.0D);
        assertEquals(5.0F, step.newFuel(), 1.0E-4F);
        assertEquals(10.0D, step.charge(), 1.0E-6D);
        assertFalse(step.stopped());
        assertEquals(FuelPricing.Reason.CONTINUE, step.reason());
    }

    @Test
    void stopsOnTheFinalIncrementThatReachesCapacity() {
        FuelPricing.Step step = FuelPricing.step(97.0F, 100.0F, 1_000.0D, 1.0D, 5.0D);
        assertEquals(100.0F, step.newFuel(), 1.0E-4F);
        assertEquals(3.0D, step.charge(), 1.0E-6D);
        assertTrue(step.stopped());
        assertEquals(FuelPricing.Reason.FULL, step.reason());
    }

    @Test
    void spendsOnlyWhatThePlayerCanAffordThenStops() {
        // Player can afford 2% (at 1.0/percent) of a 5% increment attempt.
        FuelPricing.Step step = FuelPricing.step(0.0F, 100.0F, 2.0D, 1.0D, 5.0D);
        assertEquals(2.0F, step.newFuel(), 1.0E-4F);
        assertEquals(2.0D, step.charge(), 1.0E-6D);
        assertTrue(step.stopped());
        assertEquals(FuelPricing.Reason.INSUFFICIENT_FUNDS, step.reason());
    }

    @Test
    void refusesToStartWithZeroBalance() {
        FuelPricing.Step step = FuelPricing.step(0.0F, 100.0F, 0.0D, 1.0D, 5.0D);
        assertEquals(0.0F, step.newFuel());
        assertEquals(0.0D, step.charge());
        assertTrue(step.stopped());
        assertEquals(FuelPricing.Reason.INSUFFICIENT_FUNDS, step.reason());
    }

    @Test
    void freeWhenPriceIsZeroRegardlessOfBalance() {
        FuelPricing.Step step = FuelPricing.step(0.0F, 100.0F, 0.0D, 0.0D, 5.0D);
        assertEquals(5.0F, step.newFuel(), 1.0E-4F);
        assertEquals(0.0D, step.charge());
        assertFalse(step.stopped());
        assertEquals(FuelPricing.Reason.CONTINUE, step.reason());
    }

    @Test
    void neverOverfillsPastCapacityNearTheEnd() {
        FuelPricing.Step step = FuelPricing.step(99.0F, 100.0F, 1_000.0D, 0.0D, 5.0D);
        assertEquals(100.0F, step.newFuel(), 1.0E-4F);
        assertTrue(step.stopped());
    }
}
