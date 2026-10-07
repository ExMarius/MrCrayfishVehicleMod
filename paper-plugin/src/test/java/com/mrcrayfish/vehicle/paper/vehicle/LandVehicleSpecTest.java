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
        assertEquals(0.938F, spec.firstFrontWheel().scaleX(), EPSILON);
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
        assertEquals(0.938F, spec.firstFrontWheel().scaleX(), EPSILON);
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
    void mopedMatchesSerializedPropertiesAndRendererParts() {
        LandVehicleSpec spec = LandVehicleSpec.MOPED;
        assertEquals(1.2F, spec.bodyScale(), EPSILON);
        assertEquals(-1.0F, spec.axleOffset(), EPSILON);
        assertEquals(3.2F, spec.wheelOffset(), EPSILON);
        assertEquals(12.0F, spec.enginePower(), EPSILON);
        assertEquals(12_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(0.225F, spec.energyPerTick(), EPSILON);
        assertEquals(14.0F / 16.0F * 1.2F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-6.7F / 16.0F * 1.2F, spec.rearAxleOffset(), EPSILON);
        assertEquals(0.72F, spec.firstFrontWheel().scaleX(), EPSILON);
        assertEquals(0.96F, spec.firstFrontWheel().scaleY(), EPSILON);
        assertEquals(1.2F, spec.firstRearWheel().scaleX(), EPSILON);
        assertEquals(0.96F, spec.firstRearWheel().scaleY(), EPSILON);
        assertEquals(0.0F, spec.firstFrontWheel().contactY(), EPSILON);
        assertEquals(0.0F, spec.firstRearWheel().contactY(), EPSILON);
        assertEquals(0.465F, spec.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(11.5F / 16.0F * 1.2F, spec.motorcycle().steeringPivotZ(), EPSILON);
        assertFalse(spec.motorcycle().frontWheelYaw180());
        assertPoint(spec.steering().center(), 0.0F, 1.0855425F, 0.6305325F);
        assertPoint(spec.fuelFiller().center(), 0.0F, 0.165F, 0.0F);
        assertPoint(spec.trailerOffset(), 0.0F, -0.031F, -0.65F);
        assertEquals(3, spec.mopedParts().chassisParts().size());
        assertPoint(spec.mopedParts().forkParts().getFirst().center(),
                0.0F, 0.47283F, 0.8863575F);
        assertPoint(spec.mopedParts().chest().center(), 0.0F, 1.065F, -0.7875F);
        assertPoint(spec.mopedParts().chestInteractionOffset(), 0.0F, 1.0F, -0.75F);
    }

    @Test
    void offRoaderMatchesGeneratedPropertiesAndDocumentedVanillaSeatAdaptation() {
        LandVehicleSpec spec = LandVehicleSpec.OFF_ROADER;
        assertEquals(1.4F, spec.bodyScale(), EPSILON);
        assertEquals(-1.0F, spec.axleOffset(), EPSILON);
        assertEquals(5.6F, spec.wheelOffset(), EPSILON);
        assertEquals(16.0F, spec.enginePower(), EPSILON);
        assertEquals(25_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(0.25F, spec.energyPerTick(), EPSILON);
        assertEquals(14.5F / 16.0F * 1.4F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-14.5F / 16.0F * 1.4F, spec.rearAxleOffset(), EPSILON);
        assertEquals(1.96F, spec.firstFrontWheel().scaleX(), EPSILON);
        assertEquals(1.96F, spec.firstFrontWheel().scaleY(), EPSILON);
        assertEquals(0.0F, spec.firstFrontWheel().contactY(), EPSILON);
        assertEquals(4, spec.wheels().size());
        assertEquals(4, spec.seats().size());
        assertTrue(spec.seats().getFirst().driver());
        assertPoint(spec.seats().getFirst().sourceOffset(), -0.4375F, 0.7525F, -0.2625F);
        assertPoint(spec.seats().get(1).sourceOffset(), 0.4375F, 0.7525F, -0.2625F);
        /* Vanilla cannot apply the source's standing/hanging player limb pose,
         * so both rear riders intentionally share the accepted lower Y. */
        assertPoint(spec.seats().get(2).sourceOffset(), -0.4375F, 0.70875F, -1.26875F);
        assertPoint(spec.seats().get(3).sourceOffset(), 0.4375F, 0.70875F, -1.65375F);
        assertPoint(spec.steering().center(), -0.4375F, 1.572701F, 0.299799F);
        assertEquals(1.05F, spec.steering().scale(), EPSILON);
        assertEquals(-45.0F, spec.steering().rotationX(), EPSILON);
        assertPoint(spec.fuelFiller().center(), -1.05F, 1.32125F, -0.56875F);
        assertPoint(spec.ignition().center(), 0.0F, 1.015F, 0.5425F);
        assertEquals(0.7F, spec.fuelFiller().scale(), EPSILON);
        assertEquals(0.7F, spec.ignition().scale(), EPSILON);
        assertFalse(spec.canTowTrailers());
        assertEquals("vehicle:entity.jet_ski.engine", spec.engineSound());
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

    @Test
    void commonBodyMatrixMatchesIndependentSourceEquations() {
        assertEquals(0.6375F, LandVehicleSpec.GO_KART.bodyOrigin().y(), EPSILON);
        assertEquals(0.709375F, LandVehicleSpec.LAWN_MOWER.bodyOrigin().y(), EPSILON);
        assertEquals(0.818125F, LandVehicleSpec.QUAD_BIKE.bodyOrigin().y(), EPSILON);
        assertEquals(0.66875F, LandVehicleSpec.TRACTOR.bodyOrigin().y(), EPSILON);
        assertEquals(0.85F, LandVehicleSpec.DIRT_BIKE.bodyOrigin().y(), EPSILON);
        assertEquals(0.765F, LandVehicleSpec.MOPED.bodyOrigin().y(), EPSILON);
        assertEquals(1.1025F, LandVehicleSpec.OFF_ROADER.bodyOrigin().y(), EPSILON);
    }

    @Test
    void everyWheelCenterScaleAndGroundContactMatchesGeneratedProperties() {
        assertWheel(LandVehicleSpec.GO_KART.firstFrontWheel(), 0.2F, 0.8F, 0.0F);
        assertWheel(LandVehicleSpec.GO_KART.firstRearWheel(), 0.215625F, 0.8625F, 0.0F);
        assertWheel(LandVehicleSpec.LAWN_MOWER.firstFrontWheel(), 0.240625F, 0.9625F, 0.0F);
        assertWheel(LandVehicleSpec.LAWN_MOWER.firstRearWheel(), 0.303125F, 1.2125F, 0.0F);
        assertWheel(LandVehicleSpec.QUAD_BIKE.firstFrontWheel(), 0.3025F, 1.21F, 0.0F);
        assertWheel(LandVehicleSpec.QUAD_BIKE.firstRearWheel(), 0.3025F, 1.21F, 0.0F);
        assertWheel(LandVehicleSpec.TRACTOR.firstFrontWheel(), 0.35625F, 1.425F, 0.0F);
        assertWheel(LandVehicleSpec.TRACTOR.firstRearWheel(), 0.7F, 2.8F, 0.0F);
        assertWheel(LandVehicleSpec.DIRT_BIKE.firstFrontWheel(), 0.35F, 1.4F, 0.0F);
        assertWheel(LandVehicleSpec.DIRT_BIKE.firstRearWheel(), 0.35F, 1.4F, 0.0F);
        assertWheel(LandVehicleSpec.MOPED.firstFrontWheel(), 0.24F, 0.96F, 0.0F);
        assertWheel(LandVehicleSpec.MOPED.firstRearWheel(), 0.24F, 0.96F, 0.0F);
        assertWheel(LandVehicleSpec.OFF_ROADER.firstFrontWheel(), 0.49F, 1.96F, 0.0F);
        assertWheel(LandVehicleSpec.OFF_ROADER.firstRearWheel(), 0.49F, 1.96F, 0.0F);
        assertEquals(1.12F, Math.abs(LandVehicleSpec.OFF_ROADER.firstFrontWheel().contactX()), EPSILON);

        /* Runtime reads generated JSON, where 0.9375 is serialized as 0.938. */
        assertEquals(0.11725F,
                Math.abs(LandVehicleSpec.TRACTOR.firstFrontWheel().halfWidthOffset()), EPSILON);
        assertEquals(0.938F, LandVehicleSpec.DIRT_BIKE.firstRearWheel().scaleX(), EPSILON);
    }

    @Test
    void rendererPartCentersPreserveTranslationScaleAndEngineHalfHeightOrder() {
        assertPoint(LandVehicleSpec.GO_KART.engine().center(), 0.0F, 0.6F, -0.6875F);
        assertPoint(LandVehicleSpec.QUAD_BIKE.engine().center(), 0.0F, 0.611875F, -0.06875F);
        assertPoint(LandVehicleSpec.TRACTOR.engine().center(), 0.0F, 0.96875F, 0.46875F);
        assertPoint(LandVehicleSpec.DIRT_BIKE.engine().center(), 0.0F, 0.7125F, 0.0F);

        assertPoint(LandVehicleSpec.GO_KART.steering().center(),
                0.0F, 0.6800875F, 0.5026625F);
        assertPoint(LandVehicleSpec.LAWN_MOWER.steering().center(),
                0.0F, 1.209375F, -0.1875F);
        assertPoint(LandVehicleSpec.QUAD_BIKE.steering().center(),
                0.0F, 1.230625F, 0.20625F);
        assertPoint(LandVehicleSpec.TRACTOR.steering().center(),
                0.0F, 1.3210963F, -0.4565224F);
        assertPoint(LandVehicleSpec.DIRT_BIKE.steering().center(), 0.0F, 0.85F, 0.0F);
        assertPoint(LandVehicleSpec.MOPED.steering().center(),
                0.0F, 1.0855425F, 0.6305325F);
        assertPoint(LandVehicleSpec.OFF_ROADER.steering().center(),
                -0.4375F, 1.572701F, 0.299799F);
    }

    @Test
    void fuelFillersAndIgnitionsUseRenderPartMatrixOrder() {
        assertPoint(LandVehicleSpec.GO_KART.fuelFiller().center(), 0.0F, 0.1375F, 0.0F);
        assertPoint(LandVehicleSpec.LAWN_MOWER.fuelFiller().center(),
                -0.3515625F, 0.865625F, 0.3515625F);
        assertPoint(LandVehicleSpec.QUAD_BIKE.fuelFiller().center(),
                0.0F, 1.044175F, 0.515625F);
        assertPoint(LandVehicleSpec.TRACTOR.fuelFiller().center(),
                -0.375F, 0.91875F, -0.03125F);
        assertPoint(LandVehicleSpec.DIRT_BIKE.fuelFiller().center(),
                0.0F, 1.2734375F, 0.2251875F);
        assertPoint(LandVehicleSpec.MOPED.fuelFiller().center(), 0.0F, 0.165F, 0.0F);
        assertPoint(LandVehicleSpec.OFF_ROADER.fuelFiller().center(),
                -1.05F, 1.32125F, -0.56875F);

        assertPoint(LandVehicleSpec.QUAD_BIKE.ignition().center(),
                -0.34375F, 0.5775F, 0.446875F);
        assertPoint(LandVehicleSpec.TRACTOR.ignition().center(),
                -0.171875F, 0.91875F, -0.109375F);
        assertPoint(LandVehicleSpec.OFF_ROADER.ignition().center(),
                0.0F, 1.015F, 0.5425F);
        assertEquals(0.66F, LandVehicleSpec.QUAD_BIKE.fuelFiller().scale(), EPSILON);
        assertEquals(0.55F, LandVehicleSpec.QUAD_BIKE.ignition().scale(), EPSILON);
    }

    @Test
    void sourceSeatOffsetsStaySeparateFromAcceptedVanillaCarrierCorrection() {
        assertEquals(-0.05F, LandVehicleSpec.GO_KART.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(0.63125F, LandVehicleSpec.LAWN_MOWER.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(0.611875F, LandVehicleSpec.QUAD_BIKE.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(0.64625F, LandVehicleSpec.QUAD_BIKE.seats().getLast().sourceOffset().y(), EPSILON);
        assertEquals(0.73125F, LandVehicleSpec.TRACTOR.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(0.85F, LandVehicleSpec.DIRT_BIKE.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(0.9125F, LandVehicleSpec.DIRT_BIKE.seats().getLast().sourceOffset().y(), EPSILON);
        assertEquals(0.465F, LandVehicleSpec.MOPED.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(0.7525F, LandVehicleSpec.OFF_ROADER.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(0.70875F, LandVehicleSpec.OFF_ROADER.seats().get(2).sourceOffset().y(), EPSILON);
        assertEquals(0.70875F, LandVehicleSpec.OFF_ROADER.seats().get(3).sourceOffset().y(), EPSILON);
        assertEquals(0.25F, LandVehicleSpec.RIDER_HEIGHT_CORRECTION, EPSILON);
    }

    @Test
    void towBarsKeepSeparateSourceVisualAndPhysicsOrigins() {
        assertTowBar(LandVehicleSpec.LAWN_MOWER, -1.25F);
        assertTowBar(LandVehicleSpec.QUAD_BIKE, -1.1F);
        assertTowBar(LandVehicleSpec.TRACTOR, -1.53125F);
    }

    @Test
    void vehicleTrailerOffsetsUseSerializedGeneratedValues() {
        assertPoint(LandVehicleSpec.GO_KART.trailerOffset(), 0.0F, -0.031F, -0.375F);
        assertPoint(LandVehicleSpec.LAWN_MOWER.trailerOffset(), 0.0F, -0.01F, -1.0F);
        assertPoint(LandVehicleSpec.QUAD_BIKE.trailerOffset(), 0.0F, 0.0F, -0.55F);
        assertPoint(LandVehicleSpec.TRACTOR.trailerOffset(), 0.0F, 0.0F, 0.0F);
        assertPoint(LandVehicleSpec.DIRT_BIKE.trailerOffset(), 0.0F, -0.062F, -0.312F);
        assertPoint(LandVehicleSpec.MOPED.trailerOffset(), 0.0F, -0.031F, -0.65F);
        assertPoint(LandVehicleSpec.OFF_ROADER.trailerOffset(), 0.0F, 0.0F, 0.0F);
    }

    private static void assertWheel(LandVehicleSpec.Wheel wheel, float centerY,
                                    float scaleY, float contactY) {
        assertEquals(centerY, wheel.centerY(), EPSILON);
        assertEquals(scaleY, wheel.scaleY(), EPSILON);
        assertEquals(contactY, wheel.contactY(), EPSILON);
    }

    private static void assertTowBar(LandVehicleSpec spec, float z) {
        assertPoint(spec.towBarVisualCenter(), 0.0F, 0.5F, z);
        assertPoint(spec.towBarPhysicsOffset(), 0.0F, 0.0F, z);
    }

    private static void assertPoint(LandVehicleSpec.Point point, float x, float y, float z) {
        assertEquals(x, point.x(), EPSILON);
        assertEquals(y, point.y(), EPSILON);
        assertEquals(z, point.z(), EPSILON);
    }
}
