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
        assertEquals(0.5F, spec.energyPerTick(), EPSILON);
        assertEquals(0.2F, spec.seats().getFirst().sourceOffset().y()
                + LandVehicleSpec.RIDER_HEIGHT_CORRECTION, EPSILON);
        assertEquals(0.2F, spec.wheels().getFirst().centerY(), EPSILON);
        assertEquals(0.0F, spec.wheels().getFirst().contactY(), EPSILON);
        assertFalse(spec.canTowTrailers());
    }

    @Test
    void quadBikeMatchesGeneratedPropertiesAndHasTwoSeats() {
        LandVehicleSpec spec = LandVehicleSpec.QUAD_BIKE;
        assertEquals(1.1F, spec.bodyScale(), EPSILON);
        assertEquals(4.4F, spec.wheelOffset(), EPSILON);
        assertEquals(20_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(9.5F / 16.0F * 1.1F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-11.0F / 16.0F * 1.1F, spec.rearAxleOffset(), EPSILON);
        assertEquals(1.21F, spec.firstFrontWheel().scaleY(), EPSILON);
        assertEquals(0.0F, spec.firstFrontWheel().contactY(), EPSILON);
        assertEquals(2, spec.seats().size());
        assertTrue(spec.seats().getFirst().driver());
        assertFalse(spec.seats().getLast().driver());
        assertTrue(spec.canTowTrailers());
    }

    @Test
    void tractorMatchesGeneratedPropertiesAndRendererTransforms() {
        LandVehicleSpec spec = LandVehicleSpec.TRACTOR;
        assertEquals(1.0F, spec.bodyScale(), EPSILON);
        assertEquals(-3.0F, spec.axleOffset(), EPSILON);
        assertEquals(5.7F, spec.wheelOffset(), EPSILON);
        assertEquals(8.0F, spec.enginePower(), EPSILON);
        assertEquals(15_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(0.25F, spec.energyPerTick(), EPSILON);
        assertEquals(14.0F / 16.0F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-14.5F / 16.0F, spec.rearAxleOffset(), EPSILON);
        assertEquals(0.9375F, spec.firstFrontWheel().scaleX(), EPSILON);
        assertEquals(1.425F, spec.firstFrontWheel().scaleY(), EPSILON);
        assertEquals(1.875F, spec.firstRearWheel().scaleX(), EPSILON);
        assertEquals(2.8F, spec.firstRearWheel().scaleY(), EPSILON);
        assertEquals(0.0F, spec.firstFrontWheel().contactY(), EPSILON);
        assertEquals(0.0F, spec.firstRearWheel().contactY(), EPSILON);
        assertEquals(0.96875F, spec.engine().center().y(), EPSILON);
        assertEquals(0.85F, spec.engine().scale(), EPSILON);
        assertEquals(-67.5F, spec.steering().rotationX(), EPSILON);
        assertEquals(-24.5F, spec.towBarOffset().z(), EPSILON);
        assertEquals(1, spec.seats().size());
        assertTrue(spec.seats().getFirst().driver());
        assertTrue(spec.canTowTrailers());
    }

    @Test
    void dirtBikeMatchesGeneratedPropertiesAndMotorcycleBehavior() {
        LandVehicleSpec spec = LandVehicleSpec.DIRT_BIKE;
        assertEquals(1.0F, spec.bodyScale(), EPSILON);
        assertEquals(5.6F, spec.wheelOffset(), EPSILON);
        assertEquals(16.0F, spec.enginePower(), EPSILON);
        assertEquals(20_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(0.35F, spec.energyPerTick(), EPSILON);
        assertEquals(14.08F / 16.0F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-11.61F / 16.0F, spec.rearAxleOffset(), EPSILON);
        assertEquals(0.9375F, spec.firstFrontWheel().scaleX(), EPSILON);
        assertEquals(1.4F, spec.firstFrontWheel().scaleY(), EPSILON);
        assertEquals(0.0F, spec.firstFrontWheel().contactY(), EPSILON);
        assertEquals(0.7125F, spec.engine().center().y(), EPSILON);
        assertEquals(2, spec.seats().size());
        assertTrue(spec.seats().getFirst().driver());
        assertFalse(spec.seats().getLast().driver());
        assertFalse(spec.canTowTrailers());
        assertEquals(45.0F, spec.motorcycle().maxLeanAngle(), EPSILON);
        assertEquals(10.5F / 16.0F, spec.motorcycle().steeringPivotZ(), EPSILON);
        assertEquals(-22.5F, spec.motorcycle().steeringAxisTilt(), EPSILON);
        assertEquals(-22.5F, spec.bodyRoll(17.5F, 30.0D), EPSILON);
        assertEquals(22.5F, spec.bodyRoll(-35.0F, 15.0D), EPSILON);
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
