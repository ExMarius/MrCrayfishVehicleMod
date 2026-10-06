package com.mrcrayfish.vehicle.paper.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandVehicleSpecTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void goKartKeepsAcceptedGeometryAndRuntimeProperties() {
        LandVehicleSpec spec = LandVehicleSpec.GO_KART;
        assertEquals(0.6375F, spec.bodyOrigin().y(), EPSILON);
        assertEquals(0.2F, spec.wheeliePivot().y(), EPSILON);
        assertEquals(0.5625F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-0.59375F, spec.rearAxleOffset(), EPSILON);
        assertEquals(18.0F, spec.enginePower(), EPSILON);
        assertEquals(15_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(0.325F, spec.seats().getFirst().sourceOffset().y()
                + LandVehicleSpec.RIDER_HEIGHT_CORRECTION, EPSILON);
        assertEquals(0.2F, spec.wheels().getFirst().centerY(), EPSILON);
        assertEquals(0.0F, spec.wheels().getFirst().contactY(), EPSILON);
        assertFalse(spec.canTowTrailers());
    }

    @Test
    void lawnMowerMatchesGeneratedProperties() {
        LandVehicleSpec spec = LandVehicleSpec.LAWN_MOWER;
        assertEquals(1.25F, spec.bodyScale(), EPSILON);
        assertEquals(3.08F, spec.wheelOffset(), EPSILON);
        assertEquals(8.0F, spec.enginePower(), EPSILON);
        assertEquals(5_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(13.5F / 16.0F * 1.25F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-10.7F / 16.0F * 1.25F, spec.rearAxleOffset(), EPSILON);
        assertEquals(0.77F * 1.25F, spec.firstFrontWheel().scaleY(), EPSILON);
        assertEquals(0.97F * 1.25F, spec.firstRearWheel().scaleY(), EPSILON);
        assertEquals(0.0F, spec.firstFrontWheel().contactY(), EPSILON);
        assertEquals(0.0F, spec.firstRearWheel().contactY(), EPSILON);
        assertEquals(6.0F / 16.0F * 1.25F,
                Math.abs(spec.firstFrontWheel().axleX()), EPSILON);
        assertEquals(5.0F / 16.0F * 1.25F,
                Math.abs(spec.firstRearWheel().axleX()), EPSILON);
        assertTrue(spec.canTowTrailers());
        assertTrue(spec.lawnMower());
    }
}
