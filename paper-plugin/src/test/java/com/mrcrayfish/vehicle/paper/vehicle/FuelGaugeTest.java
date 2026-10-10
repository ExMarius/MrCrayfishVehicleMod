package com.mrcrayfish.vehicle.paper.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FuelGaugeTest {
    @Test
    void heartGaugeUsesTwentySafeHalfHeartSteps() {
        assertEquals(20.0D, FuelGauge.mountHealth(1.0F));
        assertEquals(19.0D, FuelGauge.mountHealth(0.999F));
        assertEquals(19.0D, FuelGauge.mountHealth(0.95F));
        assertEquals(10.0D, FuelGauge.mountHealth(0.50F));
        assertEquals(1.0D, FuelGauge.mountHealth(0.01F));
        assertEquals(1.0D, FuelGauge.mountHealth(0.0F));
    }

    @Test
    void overlayMatchesOriginalNumericFuelReadout() {
        assertEquals("Fuel: 14999.50 / 15000.00 (100.0%)", FuelGauge.overlay(14_999.5F, 15_000.0F));
        assertEquals("Fuel: 7500.00 / 15000.00 (50.0%)", FuelGauge.overlay(7_500.0F, 15_000.0F));
        assertEquals("Fuel: 0.00 / 15000.00 (0.0%)", FuelGauge.overlay(-1.0F, 15_000.0F));
    }
}
