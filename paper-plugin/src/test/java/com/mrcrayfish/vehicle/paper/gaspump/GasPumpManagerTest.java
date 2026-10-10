package com.mrcrayfish.vehicle.paper.gaspump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GasPumpManagerTest {
    @Test
    void snapsToTheNearestCardinalDirection() {
        assertEquals(0.0F, GasPumpManager.snapYaw(10.0F));
        assertEquals(90.0F, GasPumpManager.snapYaw(80.0F));
        assertEquals(90.0F, GasPumpManager.snapYaw(100.0F));
        assertEquals(180.0F, GasPumpManager.snapYaw(181.0F));
        assertEquals(270.0F, GasPumpManager.snapYaw(260.0F));
        assertEquals(0.0F, GasPumpManager.snapYaw(350.0F));
    }

    @Test
    void wrapsAroundForNegativeAndOutOfRangeYaw() {
        assertEquals(0.0F, GasPumpManager.snapYaw(-5.0F));
        assertEquals(270.0F, GasPumpManager.snapYaw(-95.0F));
        assertEquals(0.0F, GasPumpManager.snapYaw(360.0F));
        assertEquals(90.0F, GasPumpManager.snapYaw(450.0F));
    }
}
