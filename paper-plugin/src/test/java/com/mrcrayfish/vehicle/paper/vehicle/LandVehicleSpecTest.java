package com.mrcrayfish.vehicle.paper.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(3, spec.bodyParts().size());
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
    void sportsCarMatchesGeneratedPropertiesRendererCosmeticsAndStorage() {
        LandVehicleSpec spec = LandVehicleSpec.SPORTS_CAR;
        assertEquals(1.0F, spec.bodyScale(), EPSILON);
        assertEquals(0.0F, spec.axleOffset(), EPSILON);
        assertEquals(2.6F, spec.wheelOffset(), EPSILON);
        assertEquals(20.0F, spec.enginePower(), EPSILON);
        assertEquals(35.0F, spec.maxSteeringAngle(), EPSILON);
        assertEquals(20_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(0.25F, spec.energyPerTick(), EPSILON);
        assertEquals(20.0F / 16.0F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-19.0F / 16.0F, spec.rearAxleOffset(), EPSILON);
        assertEquals(0.9F, spec.minEnginePitch(), EPSILON);
        assertEquals(1.5F, spec.maxEnginePitch(), EPSILON);
        assertEquals("vehicle:entity.sports_car.engine", spec.engineSound());
        assertEquals(4, spec.wheels().size());
        assertEquals(2, spec.seats().size());
        assertPoint(spec.seats().getFirst().sourceOffset(), -0.4375F, 0.0375F, -0.3125F);
        assertPoint(spec.seats().getLast().sourceOffset(), 0.4375F, 0.0375F, -0.3125F);
        assertPoint(spec.engine().center(), 0.0F, 0.763125F, 1.1875F);
        assertEquals(0.825F, spec.engine().scale(), EPSILON);
        assertPoint(spec.steering().center(), -0.25F, 0.59399375F, 0.1023625F);
        assertPoint(spec.steeringDisplayOffset(), -0.1875F, 0.3125F, 0.125F);
        assertPoint(new LandVehicleSpec.Point(
                        spec.steering().center().x() + spec.steeringDisplayOffset().x(),
                        spec.steering().center().y() + spec.steeringDisplayOffset().y(),
                        spec.steering().center().z() + spec.steeringDisplayOffset().z()),
                -0.4375F, 0.90649375F, 0.2273625F);
        assertEquals(-67.5F, spec.steering().rotationX(), EPSILON);
        assertPoint(spec.fuelFiller().center(), -0.625F, 0.56875F, -0.875F);
        assertPoint(spec.ignition().center(), -0.3125F, 0.44375F, 0.40625F);
        assertEquals(7, spec.bodyParts().size());
        assertEquals(4, spec.bodyParts().stream().filter(part -> part.openable() != null).count());
        assertTrue(spec.bodyParts().stream().filter(part -> part.openable() != null)
                .allMatch(part -> part.openable().animationLength() == 12));
        assertEquals(2, spec.storageCompartments().size());
        assertEquals("glove_box", spec.storageCompartments().getFirst().key());
        assertEquals(9, spec.storageCompartments().getFirst().size());
        assertEquals("trunk", spec.storageCompartments().getLast().key());
        assertEquals(27, spec.storageCompartments().getLast().size());
        assertFalse(spec.canTowTrailers());
    }

    @Test
    void sportsCarSteeringDisplayOverlapsDashboardColumn() {
        LandVehicleSpec spec = LandVehicleSpec.SPORTS_CAR;
        LandVehicleSpec.Point source = spec.steering().center();
        LandVehicleSpec.Point offset = spec.steeringDisplayOffset();
        float x = source.x() + offset.x();
        float y = source.y() + offset.y();
        float z = source.z() + offset.z();

        // Bounds are transformed from the source steering-wheel and dashboard JSON.
        assertTrue(x - 0.175F <= -0.4062F && x + 0.175F >= -0.4688F);
        assertTrue(y - 0.1533F <= 0.9650F && y + 0.1868F >= 0.8834F);
        assertTrue(z - 0.1276F <= 0.2472F && z + 0.0467F >= 0.1656F);
        assertEquals(spec.seats().getFirst().sourceOffset().x(), x, EPSILON);
    }

    @Test
    void sportsCarStorageBoxesUseSourceRayTraceCoordinates() {
        LandVehicleSpec.Box gloveBox = LandVehicleSpec.SPORTS_CAR
                .storageCompartments().getFirst().interactionBox();
        assertPoint(gloveBox.min(), 0.125F, 0.38125F, 0.1875F);
        assertPoint(gloveBox.max(), 0.5F, 0.63125F, 0.3125F);
        assertEquals(0.9375D, gloveBox.rayIntersection(
                new LandVehicleSpec.Point(0.25F, 0.5F, 1.25F),
                new LandVehicleSpec.Point(0.0F, 0.0F, -1.0F), 6.0D), 1.0E-6D);
        assertTrue(Double.isInfinite(gloveBox.rayIntersection(
                new LandVehicleSpec.Point(-0.5F, 0.5F, 1.25F),
                new LandVehicleSpec.Point(0.0F, 0.0F, -1.0F), 6.0D)));

        LandVehicleSpec.Box trunk = LandVehicleSpec.SPORTS_CAR
                .storageCompartments().getLast().interactionBox();
        assertPoint(trunk.min(), -0.4375F, 0.4125F, -1.1875F);
        assertPoint(trunk.max(), 0.4375F, 0.6F, -0.75F);
    }

    @Test
    void miniBusMatchesGeneratedPropertiesAndRenderer() {
        LandVehicleSpec spec = LandVehicleSpec.MINI_BUS;
        assertEquals(2.0F, spec.entityWidth(), EPSILON);
        assertEquals(2.0F, spec.entityHeight(), EPSILON);
        assertEquals(1.0F, spec.stepHeight(), EPSILON);
        assertEquals(1.3F, spec.bodyScale(), EPSILON);
        assertEquals(1.0F, spec.axleOffset(), EPSILON);
        assertEquals(4.76F, spec.wheelOffset(), EPSILON);
        assertEquals(14.0F, spec.enginePower(), EPSILON);
        assertEquals(35.0F, spec.maxSteeringAngle(), EPSILON);
        assertEquals(30_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(0.375F, spec.energyPerTick(), EPSILON);
        assertEquals(0.75F, spec.minEnginePitch(), EPSILON);
        assertEquals(1.25F, spec.maxEnginePitch(), EPSILON);
        assertEquals("vehicle:entity.mini_bus.engine", spec.engineSound());
        assertNull(spec.engine());
        assertEquals("go_kart_steering_wheel", spec.steering().model());
        assertPoint(spec.steering().center(), -0.40625F, 1.516441875F, 1.27057125F);
        assertEquals(0.91F, spec.steering().scale(), EPSILON);
        assertPoint(spec.fuelFiller().center(), -0.975F, 1.2805F, -0.7109375F);
        assertPoint(spec.ignition().center(), 0.0F, 1.0164375F, 1.584375F);
        assertEquals(4, spec.wheels().size());
        assertEquals(5, spec.seats().size());
        assertEquals(8, spec.bodyParts().size());
        assertEquals(3, spec.bodyParts().stream().filter(part -> part.openable() != null).count());
        LandVehicleSpec.Part leftDoor = spec.bodyParts().get(2);
        assertPoint(leftDoor.center(), 0.934375F, 0.87425F, 1.665625F);
        assertEquals(LandVehicleSpec.Axis.Y, leftDoor.openable().axis());
        assertEquals(-75.0F, leftDoor.openable().angle(), EPSILON);
        assertEquals(12, leftDoor.openable().animationLength());
        assertPoint(leftDoor.openable().interactionBox().min(),
                -0.040625F, 0.0F, -1.096875F);
        assertPoint(leftDoor.openable().interactionBox().max(),
                0.340886F, 1.305712F, 0.040625F);
        LandVehicleSpec.Part slidingDoor = spec.bodyParts().get(4);
        assertPoint(slidingDoor.center(), 0.934375F, 0.87425F, -0.609375F);
        assertEquals(105.0F, slidingDoor.openable().angle(), EPSILON);
        assertEquals(20, slidingDoor.openable().animationLength());
        assertTrue(spec.canTowTrailers());
        assertEquals("big_tow_bar", spec.towBarModel());
        assertPoint(spec.towBarVisualCenter(), 0.0F, 0.5F, -2.03125F);
        assertPoint(spec.towBarPhysicsOffset(), 0.0F, 0.0F, -2.03125F);
    }

    @Test
    void golfCartMatchesGeneratedPropertiesRendererAndRearSeatYaw() {
        LandVehicleSpec spec = LandVehicleSpec.GOLF_CART;
        assertEquals(2.0F, spec.entityWidth(), EPSILON);
        assertEquals(1.0F, spec.entityHeight(), EPSILON);
        assertEquals(1.15F, spec.bodyScale(), EPSILON);
        assertEquals(-0.5F, spec.axleOffset(), EPSILON);
        assertEquals(4.4F, spec.wheelOffset(), EPSILON);
        assertEquals(25.0F, spec.enginePower(), EPSILON);
        assertEquals(35.0F, spec.maxSteeringAngle(), EPSILON);
        assertEquals(15_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(0.25F, spec.energyPerTick(), EPSILON);
        assertEquals(0.5F, spec.minEnginePitch(), EPSILON);
        assertEquals(1.0F, spec.maxEnginePitch(), EPSILON);
        assertEquals("vehicle:entity.vehicle.helicopter_rotor", spec.engineSound());
        assertNull(spec.engine());
        assertEquals("go_kart_steering_wheel", spec.steering().model());
        assertPoint(spec.steering().center(), -0.39675F, 1.3277991F, 0.13126346F);
        assertEquals(1.0925F, spec.steering().scale(), EPSILON);
        assertPoint(spec.fuelFiller().center(), -0.934375F, 0.675625F, -0.43125F);
        assertPoint(spec.ignition().center(), -0.6109375F, 0.47796875F, 0.6109375F);
        assertEquals(4, spec.wheels().size());
        assertEquals(4, spec.seats().size());
        assertPoint(spec.seats().getFirst().sourceOffset(),
                -0.3953125F, 0.6396875F, -0.43125F);
        assertPoint(spec.seats().getLast().sourceOffset(),
                0.3953125F, 0.6396875F, -1.078125F);
        assertEquals(0.0F, spec.seats().getFirst().yawOffset(), EPSILON);
        assertEquals(180.0F, spec.seats().get(2).yawOffset(), EPSILON);
        assertEquals(180.0F, spec.seats().get(3).yawOffset(), EPSILON);
        assertFalse(spec.canTowTrailers());
        assertEquals(spec, LandVehicleSpec.byId("golf_cart"));
        assertEquals(12, LandVehicleSpec.ids().size());
    }

    @Test
    void sportsPlaneMatchesGeneratedPropertiesAndComplexModelRig() {
        LandVehicleSpec spec = LandVehicleSpec.SPORTS_PLANE;
        assertEquals(LandVehicleSpec.MotionType.AIR, spec.motionType());
        assertEquals(3.0F, spec.entityWidth(), EPSILON);
        assertEquals(1.6875F, spec.entityHeight(), EPSILON);
        assertEquals(0.85F, spec.bodyScale(), EPSILON);
        assertEquals(4.0F, spec.wheelOffset(), EPSILON);
        assertPoint(spec.bodyOrigin(), 0.0F, 0.6375F, -0.425F);
        assertEquals(24.0F, spec.enginePower(), EPSILON);
        assertEquals(25.0F, spec.maxSteeringAngle(), EPSILON);
        assertEquals(0.765625F, spec.frontAxleOffset(), EPSILON);
        assertEquals(-0.40625F, spec.rearAxleOffset(), EPSILON);
        assertEquals(75_000.0F, spec.energyCapacity(), EPSILON);
        assertEquals(1.0F, spec.energyPerTick(), EPSILON);
        assertEquals("vehicle:entity.sports_plane.engine", spec.engineSound());
        assertEquals(3, spec.wheels().size());
        assertEquals(1, spec.seats().size());
        assertPoint(spec.seats().getFirst().sourceOffset(), 0.0F, 0.53125F, -0.425F);
        assertEquals(7, spec.bodyParts().size());
        assertEquals(3.0F, spec.modelScaleCorrection("sports_plane_body"), EPSILON);
        assertEquals(3.0F, spec.modelScaleCorrection("sports_plane_wings"), EPSILON);
        assertEquals(1.0F, spec.modelScaleCorrection("sports_plane_propeller"), EPSILON);
        assertPoint(spec.wheeliePivot(), 0.0F, 0.0F, 0.0F);

        LandVehicleSpec.Plane plane = spec.plane();
        assertNotNull(plane);
        assertEquals(16.0F, plane.minimumSpeedToTakeOff(), EPSILON);
        assertEquals(35.0F, plane.maxFlapAngle(), EPSILON);
        assertEquals(0.25F, plane.flapStrength(), EPSILON);
        assertEquals(0.1F, plane.flapSensitivity(), EPSILON);
        assertEquals(45.0F, plane.maxElevatorAngle(), EPSILON);
        assertEquals(0.15F, plane.elevatorStrength(), EPSILON);
        assertEquals(0.075F, plane.elevatorSensitivity(), EPSILON);
        assertEquals(2.0F, plane.maxTurnAngle(), EPSILON);
        assertEquals(spec, LandVehicleSpec.byId("sports_plane"));
    }

    @Test
    void jetSkiMatchesGeneratedPropertiesAndBoatRenderer() {
        LandVehicleSpec spec = LandVehicleSpec.JET_SKI;
        assertEquals(1.5F, spec.entityWidth(), EPSILON);
        assertEquals(1.0F, spec.entityHeight(), EPSILON);
        assertEquals(1.25F, spec.bodyScale(), EPSILON);
        assertEquals(2.75F, spec.axleOffset(), EPSILON);
        assertEquals(0.0F, spec.wheelOffset(), EPSILON);
        assertEquals(LandVehicleSpec.MotionType.WATER, spec.motionType());
        assertPoint(spec.bodyOrigin(), 0.0F, 0.83984375F, 0.25F);
        assertEquals(18.0F, spec.enginePower(), EPSILON);
        assertEquals(35.0F, spec.maxSteeringAngle(), EPSILON);
        assertEquals(0.5F, spec.energyPerTick(), EPSILON);
        assertEquals(1.2F, spec.minEnginePitch(), EPSILON);
        assertEquals(2.2F, spec.maxEnginePitch(), EPSILON);
        assertEquals("vehicle:entity.jet_ski.engine", spec.engineSound());
        assertEquals(15.0F, spec.steeringVisualAngle(), EPSILON);
        assertPoint(spec.steering().center(), 0.0F, 1.2835938F, 0.53125F);
        assertEquals("quad_bike_handles", spec.steering().model());
        assertEquals(-45.0F, spec.steering().rotationX(), EPSILON);
        assertPoint(spec.fuelFiller().center(), 0.0F, 0.9375F, 0.9140625F);
        assertEquals("small_fuel_door_closed", spec.fuelFiller().model());
        assertEquals(0.4375F, spec.fuelFiller().scale(), EPSILON);
        assertTrue(spec.wheels().isEmpty());
        assertEquals(2, spec.seats().size());
        assertPoint(spec.seats().getFirst().sourceOffset(),
                0.0F, 0.60546875F, 0.015625F);
        assertPoint(spec.seats().getLast().sourceOffset(),
                0.0F, 0.60546875F, -0.53125F);
        assertPoint(spec.trailerOffset(), 0.0F, -0.094F, -0.65F);
        assertFalse(spec.canTowTrailers());
        assertEquals(spec, LandVehicleSpec.byId("jet_ski"));
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
        assertEquals(0.6625F, LandVehicleSpec.SPORTS_CAR.bodyOrigin().y(), EPSILON);
        assertEquals(1.118F, LandVehicleSpec.MINI_BUS.bodyOrigin().y(), EPSILON);
        assertEquals(0.8553125F, LandVehicleSpec.GOLF_CART.bodyOrigin().y(), EPSILON);
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
        assertWheel(LandVehicleSpec.SPORTS_CAR.firstFrontWheel(), 0.35F, 1.4F, 0.0F);
        assertWheel(LandVehicleSpec.SPORTS_CAR.firstRearWheel(), 0.35F, 1.4F, 0.0F);
        assertEquals(1.12F, Math.abs(LandVehicleSpec.OFF_ROADER.firstFrontWheel().contactX()), EPSILON);
        assertEquals(0.875F,
                Math.abs(LandVehicleSpec.SPORTS_CAR.firstFrontWheel().contactX()), EPSILON);
        assertWheel(LandVehicleSpec.MINI_BUS.firstFrontWheel(), 0.38675F, 1.547F, 0.0F);
        assertWheel(LandVehicleSpec.MINI_BUS.firstRearWheel(), 0.38675F, 1.547F, 0.0F);
        assertEquals(0.883675F,
                Math.abs(LandVehicleSpec.MINI_BUS.firstFrontWheel().contactX()), EPSILON);
        assertWheel(LandVehicleSpec.GOLF_CART.firstFrontWheel(), 0.31625F, 1.265F, 0.0F);
        assertWheel(LandVehicleSpec.GOLF_CART.firstRearWheel(), 0.31625F, 1.265F, 0.0F);
        assertEquals(0.805F,
                Math.abs(LandVehicleSpec.GOLF_CART.firstFrontWheel().contactX()), EPSILON);

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
        assertPoint(LandVehicleSpec.SPORTS_CAR.engine().center(),
                0.0F, 0.763125F, 1.1875F);
        assertPoint(LandVehicleSpec.SPORTS_CAR.steering().center(),
                -0.25F, 0.59399375F, 0.1023625F);
        assertPoint(LandVehicleSpec.MINI_BUS.steering().center(),
                -0.40625F, 1.516441875F, 1.27057125F);
        assertPoint(LandVehicleSpec.GOLF_CART.steering().center(),
                -0.39675F, 1.3277991F, 0.13126346F);
        for (String id : LandVehicleSpec.ids()) {
            LandVehicleSpec spec = LandVehicleSpec.byId(id);
            if (spec != LandVehicleSpec.SPORTS_CAR) {
                assertPoint(spec.steeringDisplayOffset(), 0.0F, 0.0F, 0.0F);
            }
        }
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
        assertPoint(LandVehicleSpec.SPORTS_CAR.fuelFiller().center(),
                -0.625F, 0.56875F, -0.875F);
        assertPoint(LandVehicleSpec.MINI_BUS.fuelFiller().center(),
                -0.975F, 1.2805F, -0.7109375F);
        assertPoint(LandVehicleSpec.GOLF_CART.fuelFiller().center(),
                -0.934375F, 0.675625F, -0.43125F);

        assertPoint(LandVehicleSpec.QUAD_BIKE.ignition().center(),
                -0.34375F, 0.5775F, 0.446875F);
        assertPoint(LandVehicleSpec.TRACTOR.ignition().center(),
                -0.171875F, 0.91875F, -0.109375F);
        assertPoint(LandVehicleSpec.OFF_ROADER.ignition().center(),
                0.0F, 1.015F, 0.5425F);
        assertPoint(LandVehicleSpec.SPORTS_CAR.ignition().center(),
                -0.3125F, 0.44375F, 0.40625F);
        assertPoint(LandVehicleSpec.MINI_BUS.ignition().center(),
                0.0F, 1.0164375F, 1.584375F);
        assertPoint(LandVehicleSpec.GOLF_CART.ignition().center(),
                -0.6109375F, 0.47796875F, 0.6109375F);
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
        assertEquals(0.0375F, LandVehicleSpec.SPORTS_CAR.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(0.0375F, LandVehicleSpec.SPORTS_CAR.seats().getLast().sourceOffset().y(), EPSILON);
        assertEquals(0.71175F, LandVehicleSpec.MINI_BUS.seats().getFirst().sourceOffset().y(), EPSILON);
        assertPoint(LandVehicleSpec.MINI_BUS.seats().getFirst().sourceOffset(),
                -0.40625F, 0.71175F, 0.73125F);
        assertPoint(LandVehicleSpec.MINI_BUS.seats().getLast().sourceOffset(),
                -0.40625F, 0.71175F, -1.21875F);
        assertEquals(0.6396875F,
                LandVehicleSpec.GOLF_CART.seats().getFirst().sourceOffset().y(), EPSILON);
        assertEquals(180.0F, LandVehicleSpec.GOLF_CART.seats().getLast().yawOffset(), EPSILON);
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
        assertPoint(LandVehicleSpec.SPORTS_CAR.trailerOffset(), 0.0F, 0.0F, 0.0F);
        assertPoint(LandVehicleSpec.MINI_BUS.trailerOffset(), 0.0F, 0.0F, 0.0F);
        assertPoint(LandVehicleSpec.GOLF_CART.trailerOffset(), 0.0F, 0.0F, 0.0F);
        assertPoint(LandVehicleSpec.JET_SKI.trailerOffset(), 0.0F, -0.094F, -0.65F);
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
