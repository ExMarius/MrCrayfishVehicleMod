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
        List<Wheel> wheels,
        List<Seat> seats,
        boolean canTowTrailers,
        Point towBarOffset,
        Point trailerOffset,
        boolean lawnMower
) {
    public static final float MODEL_UNIT = 1.0F / 16.0F;
    public static final float STANDARD_TRACTION = 0.8F;
    public static final float SLIDE_TRACTION = 0.05F;
    public static final float RIDER_HEIGHT_CORRECTION = 6.0F * MODEL_UNIT;

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
            List.of(
                    wheel(-1, true, 7.0F, 0.0F, 8.75F, 1.0F, 0.8F, 0.8F, 1.0F, -1.0F, 3.2F),
                    wheel(1, true, 7.0F, 0.0F, 8.75F, 1.0F, 0.8F, 0.8F, 1.0F, -1.0F, 3.2F),
                    wheel(-1, false, 7.0F, 0.25F, -9.5F, 1.0F, 0.8625F, 0.8625F, 1.0F, -1.0F, 3.2F),
                    wheel(1, false, 7.0F, 0.25F, -9.5F, 1.0F, 0.8625F, 0.8625F, 1.0F, -1.0F, 3.2F)
            ),
            List.of(seat(true, 0.0F, -3.0F, -1.0F, 1.0F, -1.0F, 3.2F)),
            false, new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, -0.031F, -0.375F),
            false
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
            List.of(
                    wheel(-1, true, 6.0F, 0.0F, 13.5F, 0.77F, 0.77F, 0.77F, 1.25F, -2.0F, 3.08F),
                    wheel(1, true, 6.0F, 0.0F, 13.5F, 0.77F, 0.77F, 0.77F, 1.25F, -2.0F, 3.08F),
                    wheel(-1, false, 5.0F, 0.8F, -10.7F, 0.97F, 0.97F, 0.97F, 1.25F, -2.0F, 3.08F),
                    wheel(1, false, 5.0F, 0.8F, -10.7F, 0.97F, 0.97F, 0.97F, 1.25F, -2.0F, 3.08F)
            ),
            List.of(seat(true, 0.0F, 7.0F, -9.0F, 1.25F, -2.0F, 3.08F)),
            true, new Point(0.0F, 0.0F, -16.0F), new Point(0.0F, -0.01F, -1.0F),
            true
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
            false
    );

    private static final Map<String, LandVehicleSpec> BY_ID = Map.of(
            GO_KART.id, GO_KART,
            LAWN_MOWER.id, LAWN_MOWER,
            QUAD_BIKE.id, QUAD_BIKE
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

    public Wheel firstFrontWheel() {
        return wheels.stream().filter(Wheel::front).findFirst().orElseThrow();
    }

    public Wheel firstRearWheel() {
        return wheels.stream().filter(wheel -> !wheel.front()).findFirst().orElseThrow();
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
