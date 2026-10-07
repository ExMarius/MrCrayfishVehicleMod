package com.mrcrayfish.vehicle.paper.vehicle;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable Paper-side equivalent of the generated VehicleProperties,
 * PoweredProperties and LandProperties used by the original mod. Values in
 * these definitions are copied from the generated JSON and source renderers.
 */
public record LandVehicleSpec(
        String id,
        String displayName,
        String bodyModel,
        float entityWidth,
        float entityHeight,
        float stepHeight,
        float bodyScale,
        float axleOffset,
        float wheelOffset,
        float enginePower,
        float maxSteeringAngle,
        float frontAxleOffset,
        float rearAxleOffset,
        float maxReverseSpeed,
        float energyCapacity,
        float energyPerTick,
        float minEnginePitch,
        float maxEnginePitch,
        String engineSound,
        boolean exhaustFumes,
        Point exhaustPosition,
        Part engine,
        Part steering,
        Part fuelFiller,
        Part ignition,
        List<Wheel> wheels,
        List<Seat> seats,
        boolean canTowTrailers,
        Point towBarOffset,
        Point trailerOffset,
        Motorcycle motorcycle,
        MopedParts mopedParts,
        boolean lawnMower
) {
    public static final float MODEL_UNIT = 1.0F / 16.0F;
    public static final float STANDARD_TRACTION = 0.8F;
    public static final float SLIDE_TRACTION = 0.05F;
    /* 1.21.4's passenger attachment sits two model pixels above the source seat pose. */
    public static final float RIDER_HEIGHT_CORRECTION = 4.0F * MODEL_UNIT;

    public static final LandVehicleSpec GO_KART = new LandVehicleSpec(
            "go_kart", "Go Kart", "go_kart_body",
            1.5F, 0.5F, 1.05F,
            1.0F, -1.0F, 3.2F,
            18.0F, 20.0F, 9.0F * MODEL_UNIT, -9.5F * MODEL_UNIT, 5.0F,
            15_000.0F, 0.5F, 0.9F, 2.0F, "vehicle:entity.go_kart.engine",
            true, new Point(0.0F, 8.0F * MODEL_UNIT, -1.0F),
            new Part("iron_small_engine", new Point(0.0F, 0.6F, -11.0F * MODEL_UNIT),
                    0.8F, 0.0F, 180.0F, 0.0F),
            new Part("go_kart_steering_wheel",
                    new Point(0.0F, 0.5F + (-1.0F + 3.2F) * MODEL_UNIT + 0.6814F * MODEL_UNIT,
                            8.0426F * MODEL_UNIT),
                    1.0F, -45.0F, 0.0F, 0.0F),
            itemPart("fuel_door_closed", 0.0F, 0.0F, 0.0F,
                    1.0F, 0.0F, 0.0F, 0.0F, 1.0F, -1.0F, 3.2F),
            null,
            List.of(
                    wheel(-1, true, 7.0F, 0.0F, 8.75F, 1.0F, 0.8F, 0.8F, 1.0F, -1.0F, 3.2F),
                    wheel(1, true, 7.0F, 0.0F, 8.75F, 1.0F, 0.8F, 0.8F, 1.0F, -1.0F, 3.2F),
                    wheel(-1, false, 7.0F, 0.25F, -9.5F, 1.0F, 0.8625F, 0.8625F, 1.0F, -1.0F, 3.2F),
                    wheel(1, false, 7.0F, 0.25F, -9.5F, 1.0F, 0.8625F, 0.8625F, 1.0F, -1.0F, 3.2F)
            ),
            List.of(seat(true, 0.0F, -3.0F, -1.0F, 1.0F, -1.0F, 3.2F)),
            false, new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, -0.031F, -0.375F),
            null, null, false
    );

    /* wheelOffset = (8 * 0.97 / 2) - 0.8 = 3.08; auto-scaled front wheels become 0.77. */
    public static final LandVehicleSpec LAWN_MOWER = new LandVehicleSpec(
            "lawn_mower", "Lawn Mower", "lawn_mower_body",
            1.2F, 1.0F, 1.0F,
            1.25F, -2.0F, 3.08F,
            8.0F, 35.0F, 13.5F * MODEL_UNIT * 1.25F, -10.7F * MODEL_UNIT * 1.25F, 5.0F,
            5_000.0F, 0.25F, 0.5F, 1.25F, "vehicle:entity.quad_bike.engine",
            false, new Point(0.0F, 0.0F, 0.0F), null,
            new Part("go_kart_steering_wheel",
                    new Point(0.0F, (0.5F + (-2.0F + 3.08F) * MODEL_UNIT + 0.4F) * 1.25F,
                            -0.15F * 1.25F),
                    0.9F * 1.25F, -45.0F, 0.0F, 0.0F),
            itemPart("fuel_door_closed", -4.5F, 10.0F, 4.5F,
                    0.5F, 0.0F, -90.0F, 0.0F, 1.25F, -2.0F, 3.08F),
            null,
            List.of(
                    wheel(-1, true, 6.0F, 0.0F, 13.5F, 0.77F, 0.77F, 0.77F, 1.25F, -2.0F, 3.08F),
                    wheel(1, true, 6.0F, 0.0F, 13.5F, 0.77F, 0.77F, 0.77F, 1.25F, -2.0F, 3.08F),
                    wheel(-1, false, 5.0F, 0.8F, -10.7F, 0.97F, 0.97F, 0.97F, 1.25F, -2.0F, 3.08F),
                    wheel(1, false, 5.0F, 0.8F, -10.7F, 0.97F, 0.97F, 0.97F, 1.25F, -2.0F, 3.08F)
            ),
            List.of(seat(true, 0.0F, 7.0F, -9.0F, 1.25F, -2.0F, 3.08F)),
            true, new Point(0.0F, 0.0F, -16.0F), new Point(0.0F, -0.01F, -1.0F),
            null, null, true
    );

    public static final LandVehicleSpec QUAD_BIKE = new LandVehicleSpec(
            "quad_bike", "Quad Bike", "quad_bike_body",
            1.5F, 1.0F, 1.0F,
            1.1F, -0.5F, 4.4F,
            15.0F, 35.0F, 9.5F * MODEL_UNIT * 1.1F, -11.0F * MODEL_UNIT * 1.1F, 5.0F,
            20_000.0F, 0.25F, 0.5F, 1.25F, "vehicle:entity.quad_bike.engine",
            false, new Point(0.0F, 0.0F, 0.0F),
            new Part("iron_small_engine", new Point(0.0F, 0.611875F, -0.06875F),
                    0.55F, 0.0F, 180.0F, 0.0F),
            new Part("quad_bike_handles", new Point(0.0F, 1.230625F, 0.20625F),
                    1.1F, -35.0F, 0.0F, 0.0F),
            itemPart("small_fuel_door_closed", 0.0F, 11.288F, 7.5F,
                    0.6F, -90.0F, 0.0F, 0.0F, 1.1F, -0.5F, 4.4F),
            itemPart("key_hole", -5.0F, 4.5F, 6.5F,
                    0.5F, -45.0F, 0.0F, 0.0F, 1.1F, -0.5F, 4.4F),
            List.of(
                    wheel(-1, true, 4.5F, 0.0F, 9.5F, 1.1F, 1.1F, 1.1F, 1.1F, -0.5F, 4.4F),
                    wheel(1, true, 4.5F, 0.0F, 9.5F, 1.1F, 1.1F, 1.1F, 1.1F, -0.5F, 4.4F),
                    wheel(-1, false, 4.5F, 0.0F, -11.0F, 1.1F, 1.1F, 1.1F, 1.1F, -0.5F, 4.4F),
                    wheel(1, false, 4.5F, 0.0F, -11.0F, 1.1F, 1.1F, 1.1F, 1.1F, -0.5F, 4.4F)
            ),
            List.of(
                    seat(true, 0.0F, 5.0F, -4.0F, 1.1F, -0.5F, 4.4F),
                    seat(false, 0.0F, 5.5F, -12.0F, 1.1F, -0.5F, 4.4F)
            ),
            true, new Point(0.0F, 0.0F, -16.0F), new Point(0.0F, 0.0F, -0.55F),
            null, null, false
    );

    /* Source wheelOffset = (8 * 2.8 / 2) - 5.5 = 5.7. The front wheel's
     * auto-scaled Y/Z axes become 5.7 / 4 = 1.425 while X stays at the
     * generated property's serialized 0.938 value. */
    public static final LandVehicleSpec TRACTOR = new LandVehicleSpec(
            "tractor", "Tractor", "tractor_body",
            1.5F, 1.5F, 1.0F,
            1.0F, -3.0F, 5.7F,
            8.0F, 35.0F, 14.0F * MODEL_UNIT, -14.5F * MODEL_UNIT, 5.0F,
            15_000.0F, 0.25F, 0.8F, 1.6F, "vehicle:entity.tractor.engine",
            true, new Point(-2.0F * MODEL_UNIT, 32.0F * MODEL_UNIT, 16.0F * MODEL_UNIT),
            new Part("iron_large_engine", new Point(0.0F, 0.96875F, 7.5F * MODEL_UNIT),
                    0.85F, 0.0F, 0.0F, 0.0F),
            /* TractorRenderer translates to (0, .66, -.475), rotates -67.5 degrees,
             * then translates another -.02 on its local Y axis. */
            new Part("go_kart_steering_wheel", new Point(0.0F, 1.3210963F, -0.4565224F),
                    0.9F, -67.5F, 0.0F, 0.0F),
            itemPart("fuel_door_closed", -6.0F, 12.0F, -0.5F,
                    0.6F, 0.0F, -90.0F, 0.0F, 1.0F, -3.0F, 5.7F),
            itemPart("key_hole", -2.75F, 12.0F, -1.75F,
                    0.5F, -45.0F, 0.0F, 0.0F, 1.0F, -3.0F, 5.7F),
            List.of(
                    wheel(-1, true, 8.0F, 0.0F, 14.0F,
                            0.938F, 1.425F, 1.425F, 1.0F, -3.0F, 5.7F),
                    wheel(1, true, 8.0F, 0.0F, 14.0F,
                            0.938F, 1.425F, 1.425F, 1.0F, -3.0F, 5.7F),
                    wheel(-1, false, 8.0F, 5.5F, -14.5F,
                            1.875F, 2.8F, 2.8F, 1.0F, -3.0F, 5.7F),
                    wheel(1, false, 8.0F, 5.5F, -14.5F,
                            1.875F, 2.8F, 2.8F, 1.0F, -3.0F, 5.7F)
            ),
            List.of(seat(true, 0.0F, 9.0F, -14.0F, 1.0F, -3.0F, 5.7F)),
            true, new Point(0.0F, 0.0F, -24.5F), new Point(0.0F, 0.0F, 0.0F),
            null, null, false
    );

    /* DirtBikeRenderer steers both the handle assembly and the separately rendered
     * front wheel around a fork axis tilted 22.5 degrees toward the rider. */
    public static final LandVehicleSpec DIRT_BIKE = new LandVehicleSpec(
            "dirt_bike", "Dirt Bike", "dirt_bike_body",
            1.0F, 1.5F, 1.0F,
            1.0F, 0.0F, 5.6F,
            16.0F, 35.0F, 14.08F * MODEL_UNIT, -11.61F * MODEL_UNIT, 5.0F,
            20_000.0F, 0.35F, 0.85F, 1.5F, "vehicle:entity.dirt_bike.engine",
            true, new Point(-1.0F * MODEL_UNIT, 16.0F * MODEL_UNIT, -16.0F * MODEL_UNIT),
            new Part("iron_small_engine", new Point(0.0F, 0.7125F, 0.0F),
                    0.6F, 0.0F, 180.0F, 0.0F),
            new Part("dirt_bike_handles", new Point(0.0F, 0.85F, 0.0F),
                    1.0F, 0.0F, 0.0F, 0.0F),
            itemPart("small_fuel_door_closed", 0.0F, 14.775F, 3.603F,
                    0.6F, 67.5F, 180.0F, 0.0F, 1.0F, 0.0F, 5.6F),
            null,
            List.of(
                    wheel(0, true, 0.0F, 0.0F, 14.08F,
                            0.938F, 1.4F, 1.4F, 1.0F, 0.0F, 5.6F),
                    wheel(0, false, 0.0F, 0.0F, -11.61F,
                            0.938F, 1.4F, 1.4F, 1.0F, 0.0F, 5.6F)
            ),
            List.of(
                    seat(true, 0.0F, 8.0F, -2.0F, 1.0F, 0.0F, 5.6F),
                    seat(false, 0.0F, 9.0F, -9.0F, 1.0F, 0.0F, 5.6F)
            ),
            false, new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, -0.062F, -0.312F),
            new Motorcycle(45.0F, 10.5F * MODEL_UNIT, -22.5F, true), null, false
    );

    /* The Moped renderer steers its handles, mud guard, and manually rendered
     * front wheel around a fork axis tilted 22.5 degrees toward the rider. */
    public static final LandVehicleSpec MOPED = new LandVehicleSpec(
            "moped", "Moped", "moped_body",
            1.0F, 1.0F, 1.0F,
            1.2F, -1.0F, 3.2F,
            12.0F, 45.0F, 14.0F * MODEL_UNIT * 1.2F, -6.7F * MODEL_UNIT * 1.2F, 5.0F,
            12_000.0F, 0.225F, 0.5F, 1.2F, "vehicle:entity.moped.engine",
            false, new Point(0.0F, 0.0F, 0.0F), null,
            new Part("moped_handles", new Point(0.0F, 1.0855425F, 0.6305325F),
                    1.2F, 0.0F, 0.0F, 0.0F),
            itemPart("fuel_door_closed", 0.0F, 0.0F, 0.0F,
                    1.0F, 0.0F, 0.0F, 0.0F, 1.2F, -1.0F, 3.2F),
            null,
            List.of(
                    wheel(0, true, 0.0F, 0.0F, 14.088F,
                            0.6F, 0.8F, 0.8F, 1.2F, -1.0F, 3.2F),
                    wheel(0, false, 0.0F, 0.0F, -6.7F,
                            1.0F, 0.8F, 0.8F, 1.2F, -1.0F, 3.2F)
            ),
            List.of(seat(true, 0.0F, 4.0F, -1.0F, 1.2F, -1.0F, 3.2F)),
            false, new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, -0.031F, -0.65F),
            new Motorcycle(45.0F, 11.5F * MODEL_UNIT * 1.2F, -22.5F, false),
            new MopedParts(
                    List.of(
                            new Part("moped_stock_seat", new Point(0.0F, 0.69F, -0.4875F),
                                    1.2F, 0.0F, 0.0F, 0.0F),
                            new Part("moped_stock_tray", new Point(0.0F, 0.69F, -0.4875F),
                                    1.2F, 0.0F, 0.0F, 0.0F),
                            new Part("moped_stock_front_light", new Point(0.0F, 0.915F, 0.7629F),
                                    1.2F, 0.0F, 0.0F, 0.0F)
                    ),
                    List.of(new Part("moped_mud_guard", new Point(0.0F, 0.47283F, 0.8863575F),
                            1.2F, 0.0F, 0.0F, 0.0F)),
                    new Part("minecraft:chest", new Point(0.0F, 1.065F, -0.7875F),
                            0.6F, 0.0F, 180.0F, 0.0F),
                    new Point(0.0F, 1.0F, -0.75F)
            ),
            false
    );

    private static final Map<String, LandVehicleSpec> BY_ID = Map.of(
            GO_KART.id, GO_KART,
            LAWN_MOWER.id, LAWN_MOWER,
            QUAD_BIKE.id, QUAD_BIKE,
            TRACTOR.id, TRACTOR,
            DIRT_BIKE.id, DIRT_BIKE,
            MOPED.id, MOPED
    );

    public static LandVehicleSpec byId(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static List<String> ids() {
        return BY_ID.keySet().stream().sorted().toList();
    }

    public Point bodyOrigin() {
        return new Point(0.0F, (0.5F + (axleOffset + wheelOffset) * MODEL_UNIT) * bodyScale, 0.0F);
    }

    public Point wheeliePivot() {
        return new Point(0.0F, wheelOffset * MODEL_UNIT * bodyScale, rearAxleOffset);
    }

    /**
     * The common source renderer draws the tow bar after cancelling body scale,
     * but before the axle/wheel translations and wheelie matrix. Its model origin
     * therefore keeps the renderer's standalone +0.5 Y correction and never
     * follows a boost wheelie.
     */
    public Point towBarVisualCenter() {
        return new Point(
                towBarOffset.x * bodyScale * MODEL_UNIT,
                0.5F + towBarOffset.y * bodyScale * MODEL_UNIT,
                towBarOffset.z * bodyScale * MODEL_UNIT
        );
    }

    /** Source trailer physics uses the same X/Z offset without visual +0.5 Y. */
    public Point towBarPhysicsOffset() {
        return new Point(
                towBarOffset.x * bodyScale * MODEL_UNIT,
                towBarOffset.y * bodyScale * MODEL_UNIT,
                towBarOffset.z * bodyScale * MODEL_UNIT
        );
    }

    public Wheel firstFrontWheel() {
        return wheels.stream().filter(Wheel::front).findFirst().orElseThrow();
    }

    public Wheel firstRearWheel() {
        return wheels.stream().filter(wheel -> !wheel.front()).findFirst().orElseThrow();
    }

    /** Mirrors MotorcycleEntity#getBodyRotationRoll: steering ratio times speed/30, capped at full lean. */
    public float bodyRoll(float steeringAngle, double speed) {
        if (motorcycle == null || maxSteeringAngle == 0.0F) {
            return 0.0F;
        }
        double speedFactor = Math.max(0.0D, Math.min(1.0D, speed / 30.0D));
        return (float) (-motorcycle.maxLeanAngle() * (steeringAngle / maxSteeringAngle) * speedFactor);
    }

    /**
     * AbstractPoweredRenderer#renderPart translation order for fuel fillers and
     * ignition models: common body origin, property translation, then -0.5 Y,
     * then the property's uniform scale and XYZ rotations.
     */
    private static Part itemPart(String model, float x, float y, float z,
                                 float scale, float rotationX, float rotationY, float rotationZ,
                                 float bodyScale, float axleOffset, float wheelOffset) {
        float bodyY = (0.5F + (axleOffset + wheelOffset) * MODEL_UNIT) * bodyScale;
        return new Part(model, new Point(
                x * MODEL_UNIT * bodyScale,
                bodyY + (y * MODEL_UNIT - 0.5F) * bodyScale,
                z * MODEL_UNIT * bodyScale
        ), scale * bodyScale, rotationX, rotationY, rotationZ);
    }

    private static Wheel wheel(int side, boolean front, float offsetX, float offsetY, float offsetZ,
                               float scaleX, float scaleY, float scaleZ,
                               float bodyScale, float axleOffset, float wheelOffset) {
        float axleX = side * offsetX * MODEL_UNIT * bodyScale;
        float centerY = (wheelOffset + offsetY) * MODEL_UNIT * bodyScale;
        float axleZ = offsetZ * MODEL_UNIT * bodyScale;
        float halfWidth = side * 4.0F * scaleX * 0.5F * MODEL_UNIT * bodyScale;
        float contactY = (wheelOffset + offsetY) * MODEL_UNIT * bodyScale
                - 0.25F * bodyScale * scaleY;
        return new Wheel(side, front, axleX, centerY, axleZ, halfWidth, contactY,
                bodyScale * scaleX, bodyScale * scaleY, bodyScale * scaleZ);
    }

    private static Seat seat(boolean driver, float x, float y, float z,
                             float bodyScale, float axleOffset, float wheelOffset) {
        return new Seat(driver, new Point(
                -x * MODEL_UNIT * bodyScale,
                (y + axleOffset + wheelOffset) * MODEL_UNIT * bodyScale,
                z * MODEL_UNIT * bodyScale
        ));
    }

    public record Point(float x, float y, float z) {
    }

    public record Part(String model, Point center, float scale,
                       float rotationX, float rotationY, float rotationZ) {
    }

    public record Motorcycle(float maxLeanAngle, float steeringPivotZ,
                             float steeringAxisTilt, boolean frontWheelYaw180) {
    }

    public record MopedParts(List<Part> chassisParts, List<Part> forkParts,
                             Part chest, Point chestInteractionOffset) {
    }

    public record Wheel(int side, boolean front, float axleX, float centerY, float axleZ,
                        float halfWidthOffset, float contactY,
                        float scaleX, float scaleY, float scaleZ) {
        public float contactX() {
            return axleX + halfWidthOffset;
        }

        public float contactZ() {
            return axleZ;
        }
    }

    public record Seat(boolean driver, Point sourceOffset) {
    }
}
