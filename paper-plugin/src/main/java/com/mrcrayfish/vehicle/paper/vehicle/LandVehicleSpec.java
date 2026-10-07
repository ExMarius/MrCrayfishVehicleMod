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
        List<Part> bodyParts,
        List<Wheel> wheels,
        List<Seat> seats,
        boolean canTowTrailers,
        String towBarModel,
        Point towBarOffset,
        Point trailerOffset,
        Motorcycle motorcycle,
        MopedParts mopedParts,
        List<StorageCompartment> storageCompartments,
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
            List.of(),
            List.of(
                    wheel(-1, true, 7.0F, 0.0F, 8.75F, 1.0F, 0.8F, 0.8F, 1.0F, -1.0F, 3.2F),
                    wheel(1, true, 7.0F, 0.0F, 8.75F, 1.0F, 0.8F, 0.8F, 1.0F, -1.0F, 3.2F),
                    wheel(-1, false, 7.0F, 0.25F, -9.5F, 1.0F, 0.8625F, 0.8625F, 1.0F, -1.0F, 3.2F),
                    wheel(1, false, 7.0F, 0.25F, -9.5F, 1.0F, 0.8625F, 0.8625F, 1.0F, -1.0F, 3.2F)
            ),
            List.of(seat(true, 0.0F, -3.0F, -1.0F, 1.0F, -1.0F, 3.2F)),
            false, "tow_bar", new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, -0.031F, -0.375F),
            null, null, List.of(), false
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
            List.of(),
            List.of(
                    wheel(-1, true, 6.0F, 0.0F, 13.5F, 0.77F, 0.77F, 0.77F, 1.25F, -2.0F, 3.08F),
                    wheel(1, true, 6.0F, 0.0F, 13.5F, 0.77F, 0.77F, 0.77F, 1.25F, -2.0F, 3.08F),
                    wheel(-1, false, 5.0F, 0.8F, -10.7F, 0.97F, 0.97F, 0.97F, 1.25F, -2.0F, 3.08F),
                    wheel(1, false, 5.0F, 0.8F, -10.7F, 0.97F, 0.97F, 0.97F, 1.25F, -2.0F, 3.08F)
            ),
            List.of(seat(true, 0.0F, 7.0F, -9.0F, 1.25F, -2.0F, 3.08F)),
            true, "tow_bar", new Point(0.0F, 0.0F, -16.0F), new Point(0.0F, -0.01F, -1.0F),
            null, null, List.of(), true
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
            List.of(),
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
            true, "tow_bar", new Point(0.0F, 0.0F, -16.0F), new Point(0.0F, 0.0F, -0.55F),
            null, null, List.of(), false
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
            List.of(),
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
            true, "tow_bar", new Point(0.0F, 0.0F, -24.5F), new Point(0.0F, 0.0F, 0.0F),
            null, null, List.of(), false
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
            List.of(),
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
            false, "tow_bar", new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, -0.062F, -0.312F),
            new Motorcycle(45.0F, 10.5F * MODEL_UNIT, -22.5F, true), null,
            List.of(), false
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
                    new Part("moped_stock_seat", new Point(0.0F, 0.69F, -0.4875F),
                            1.2F, 0.0F, 0.0F, 0.0F),
                    new Part("moped_stock_tray", new Point(0.0F, 0.69F, -0.4875F),
                            1.2F, 0.0F, 0.0F, 0.0F),
                    new Part("moped_stock_front_light", new Point(0.0F, 0.915F, 0.7629F),
                            1.2F, 0.0F, 0.0F, 0.0F)
            ),
            List.of(
                    wheel(0, true, 0.0F, 0.0F, 14.088F,
                            0.6F, 0.8F, 0.8F, 1.2F, -1.0F, 3.2F),
                    wheel(0, false, 0.0F, 0.0F, -6.7F,
                            1.0F, 0.8F, 0.8F, 1.2F, -1.0F, 3.2F)
            ),
            List.of(seat(true, 0.0F, 4.0F, -1.0F, 1.2F, -1.0F, 3.2F)),
            false, "tow_bar", new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, -0.031F, -0.65F),
            new Motorcycle(45.0F, 11.5F * MODEL_UNIT * 1.2F, -22.5F, false),
            new MopedParts(
                    List.of(new Part("moped_mud_guard", new Point(0.0F, 0.47283F, 0.8863575F),
                            1.2F, 0.0F, 0.0F, 0.0F)),
                    new Part("minecraft:chest", new Point(0.0F, 1.065F, -0.7875F),
                            0.6F, 0.0F, 180.0F, 0.0F),
                    new Point(0.0F, 1.0F, -0.75F)
            ),
            List.of(), false
    );

    /* Generated Off Roader geometry uses four 1.4-scale wheels inside a 1.4-scale body.
     * OffRoaderRenderer applies the local -45 degree steering-wheel transform before
     * its final -0.02 local-Y translation. */
    public static final LandVehicleSpec OFF_ROADER = new LandVehicleSpec(
            "off_roader", "Off Roader", "off_roader_body",
            2.0F, 1.0F, 1.0F,
            1.4F, -1.0F, 5.6F,
            16.0F, 35.0F, 14.5F * MODEL_UNIT * 1.4F, -14.5F * MODEL_UNIT * 1.4F, 5.0F,
            25_000.0F, 0.25F, 0.8F, 1.6F, "vehicle:entity.jet_ski.engine",
            false, new Point(0.0F, 0.0F, 0.0F), null,
            new Part("go_kart_steering_wheel",
                    new Point(-0.4375F, 1.572701F, 0.299799F),
                    1.05F, -45.0F, 0.0F, 0.0F),
            itemPart("fuel_door_closed", -12.0F, 10.5F, -6.5F,
                    0.5F, 0.0F, -90.0F, 0.0F, 1.4F, -1.0F, 5.6F),
            itemPart("key_hole", 0.0F, 7.0F, 6.2F,
                    0.5F, -67.5F, 0.0F, 0.0F, 1.4F, -1.0F, 5.6F),
            List.of(),
            List.of(
                    wheel(-1, true, 10.0F, 0.0F, 14.5F,
                            1.4F, 1.4F, 1.4F, 1.4F, -1.0F, 5.6F),
                    wheel(1, true, 10.0F, 0.0F, 14.5F,
                            1.4F, 1.4F, 1.4F, 1.4F, -1.0F, 5.6F),
                    wheel(-1, false, 10.0F, 0.0F, -14.5F,
                            1.4F, 1.4F, 1.4F, 1.4F, -1.0F, 5.6F),
                    wheel(1, false, 10.0F, 0.0F, -14.5F,
                            1.4F, 1.4F, 1.4F, 1.4F, -1.0F, 5.6F)
            ),
            List.of(
                    seat(true, 5.0F, 4.0F, -3.0F, 1.4F, -1.0F, 5.6F),
                    seat(false, -5.0F, 4.0F, -3.0F, 1.4F, -1.0F, 5.6F),
                    /* The source's 11.5-pixel Y relies on a custom standing/hanging
                     * limb pose. Vanilla renders every mounted player seated, so
                     * keep both user-facing rear positions at the lower 3.5-pixel Y. */
                    seat(false, 5.0F, 3.5F, -14.5F, 1.4F, -1.0F, 5.6F),
                    seat(false, -5.0F, 3.5F, -18.9F, 1.4F, -1.0F, 5.6F)
            ),
            false, "tow_bar", new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, 0.0F, 0.0F),
            null, null, List.of(), false
    );

    /* Generated Sports Car geometry uses the default body scale/ground offset and
     * a wheelOffset of (8 * 1.4 / 2) - 3 = 2.6 model pixels. Its seven generated
     * cosmetic models are required to complete the body; four retain the source
     * openable actions around their declared cosmetic pivots. */
    public static final LandVehicleSpec SPORTS_CAR = new LandVehicleSpec(
            "sports_car", "Sports Car", "sports_car_body",
            1.5F, 1.0F, 1.0F,
            1.0F, 0.0F, 2.6F,
            20.0F, 35.0F, 20.0F * MODEL_UNIT, -19.0F * MODEL_UNIT, 5.0F,
            20_000.0F, 0.25F, 0.9F, 1.5F, "vehicle:entity.sports_car.engine",
            false, new Point(0.0F, 0.0F, 0.0F),
            new Part("iron_large_engine", new Point(0.0F, 0.763125F, 1.1875F),
                    0.825F, 0.0F, 0.0F, 0.0F),
            new Part("sports_car_steering_wheel", new Point(-0.25F, 0.59399375F, 0.1023625F),
                    0.7F, -67.5F, 0.0F, 0.0F),
            itemPart("fuel_door_closed", -10.0F, 6.5F, -14.0F,
                    0.4F, 0.0F, -90.0F, 0.0F, 1.0F, 0.0F, 2.6F),
            itemPart("key_hole", -5.0F, 4.5F, 6.5F,
                    0.5F, -45.0F, 0.0F, 0.0F, 1.0F, 0.0F, 2.6F),
            List.of(
                    new Part("sports_car_hood", new Point(0.0F, 1.00625F, 0.84375F),
                            1.0F, 0.0F, 0.0F, 0.0F,
                            new Openable("hood", Axis.X, -60.0F, 12,
                                    "vehicle:entity.vehicle.hood.open", "vehicle:entity.vehicle.hood.close",
                                    new Box(new Point(-0.6875F, -0.198366F, -0.03125F),
                                            new Point(0.6875F, 0.03125F, 0.883688F)))),
                    new Part("sports_car_left_door", new Point(0.9375F, 0.225F, 0.625F),
                            1.0F, 0.0F, 0.0F, 0.0F,
                            new Openable("left_door", Axis.Y, -75.0F, 12,
                                    "vehicle:entity.vehicle.door.open", "vehicle:entity.vehicle.door.close",
                                    new Box(new Point(-0.09375F, 0.0F, -1.1875F),
                                            new Point(0.288713F, 1.25F, 0.010787F)))),
                    new Part("sports_car_right_door", new Point(-0.9375F, 0.225F, 0.625F),
                            1.0F, 0.0F, 0.0F, 0.0F,
                            new Openable("right_door", Axis.Y, 90.0F, 12,
                                    "vehicle:entity.vehicle.door.open", "vehicle:entity.vehicle.door.close",
                                    new Box(new Point(-0.288713F, 0.0F, -1.1875F),
                                            new Point(0.09375F, 1.25F, 0.010787F)))),
                    new Part("sports_car_boot", new Point(0.0F, 1.13125F, -1.46875F),
                            1.0F, 0.0F, 0.0F, 0.0F,
                            new Openable("spoiler", Axis.X, 90.0F, 12,
                                    "vehicle:entity.vehicle.door.open", "vehicle:entity.vehicle.door.close",
                                    new Box(new Point(-0.625F, -0.34375F, -0.468751F),
                                            new Point(0.625F, 0.031251F, 0.03125F)))),
                    new Part("sports_car_seat", new Point(0.0F, 0.225F, 0.0F),
                            1.0F, 0.0F, 0.0F, 0.0F),
                    new Part("sports_car_dashboard", new Point(0.0F, 1.0375F, 0.75F),
                            1.0F, 0.0F, 0.0F, 0.0F),
                    new Part("sports_car_roof", new Point(0.0F, 1.0375F, 0.0F),
                            1.0F, 0.0F, 0.0F, 0.0F)
            ),
            List.of(
                    wheel(-1, true, 12.0F, 3.0F, 20.0F,
                            1.0F, 1.4F, 1.4F, 1.0F, 0.0F, 2.6F),
                    wheel(1, true, 12.0F, 3.0F, 20.0F,
                            1.0F, 1.4F, 1.4F, 1.0F, 0.0F, 2.6F),
                    wheel(-1, false, 12.0F, 3.0F, -19.0F,
                            1.0F, 1.4F, 1.4F, 1.0F, 0.0F, 2.6F),
                    wheel(1, false, 12.0F, 3.0F, -19.0F,
                            1.0F, 1.4F, 1.4F, 1.0F, 0.0F, 2.6F)
            ),
            List.of(
                    seat(true, 7.0F, -2.0F, -5.0F, 1.0F, 0.0F, 2.6F),
                    seat(false, -7.0F, -2.0F, -5.0F, 1.0F, 0.0F, 2.6F)
            ),
            false, "tow_bar", new Point(0.0F, 0.0F, 0.0F), new Point(0.0F, 0.0F, 0.0F),
            null, null,
            List.of(
                    new StorageCompartment("glove_box", "Glove Box", 1,
                            new Box(new Point(0.125F, 0.38125F, 0.1875F),
                                    new Point(0.5F, 0.63125F, 0.3125F))),
                    new StorageCompartment("trunk", "Trunk", 3,
                            new Box(new Point(-0.4375F, 0.4125F, -1.1875F),
                                    new Point(0.4375F, 0.6F, -0.75F)))
            ),
            false
    );

    /* Generated Mini Bus geometry uses body scale 1.3 and wheelOffset
     * (8 * 1.19 / 2) = 4.76 pixels. The source registers a Mini Bus steering-wheel
     * model that is absent from its assets; its ray transforms explicitly use the Go Kart wheel,
     * so that original model is the non-fallback vanilla representation here. */
    public static final LandVehicleSpec MINI_BUS = new LandVehicleSpec(
            "mini_bus", "Mini Bus", "mini_bus_body",
            2.0F, 2.0F, 1.0F,
            1.3F, 1.0F, 4.76F,
            14.0F, 35.0F, 13.5F * MODEL_UNIT * 1.3F, -13.5F * MODEL_UNIT * 1.3F, 5.0F,
            30_000.0F, 0.375F, 0.75F, 1.25F, "vehicle:entity.mini_bus.engine",
            false, new Point(0.0F, 0.0F, 0.0F), null,
            new Part("go_kart_steering_wheel",
                    new Point(-0.40625F, 1.516441875F, 1.27057125F),
                    0.91F, -67.5F, 0.0F, 0.0F),
            itemPart("fuel_door_closed", -12.0F, 10.0F, -8.75F,
                    0.5F, 0.0F, -90.0F, 0.0F, 1.3F, 1.0F, 4.76F),
            itemPart("key_hole", 0.0F, 6.75F, 19.5F,
                    0.5F, -67.5F, 0.0F, 0.0F, 1.3F, 1.0F, 4.76F),
            List.of(
                    new Part("mini_bus_stock_roof", new Point(0.0F, 1.52425F, 0.0F),
                            1.3F, 0.0F, 0.0F, 0.0F),
                    new Part("mini_bus_roof_racks", new Point(0.0F, 2.2555F, 0.0F),
                            1.3F, 0.0F, 0.0F, 0.0F),
                    new Part("mini_bus_left_door", new Point(0.934375F, 0.87425F, 1.665625F),
                            1.3F, 0.0F, 0.0F, 0.0F,
                            new Openable("left_door", Axis.Y, -75.0F, 12,
                                    "vehicle:entity.vehicle.door.open", "vehicle:entity.vehicle.door.close",
                                    new Box(new Point(-0.040625F, 0.0F, -1.096875F),
                                            new Point(0.340886F, 1.305712F, 0.040625F)))),
                    new Part("mini_bus_right_door", new Point(-0.934375F, 0.87425F, 1.665625F),
                            1.3F, 0.0F, 0.0F, 0.0F,
                            new Openable("right_door", Axis.Y, 75.0F, 12,
                                    "vehicle:entity.vehicle.door.open", "vehicle:entity.vehicle.door.close",
                                    new Box(new Point(-0.340886F, 0.0F, -1.096875F),
                                            new Point(0.040625F, 1.305712F, 0.040625F)))),
                    new Part("mini_bus_sliding_door", new Point(0.934375F, 0.87425F, -0.609375F),
                            1.3F, 0.0F, 0.0F, 0.0F,
                            new Openable("left_sliding_door", Axis.Y, 105.0F, 20,
                                    "vehicle:entity.vehicle.door.open", "vehicle:entity.vehicle.door.close",
                                    new Box(new Point(-0.040625F, 0.0F, -0.040625F),
                                            new Point(0.040625F, 1.305712F, 1.015625F)))),
                    new Part("mini_bus_rear", new Point(0.0F, 0.9555F, -1.7875F),
                            1.3F, 0.0F, 0.0F, 0.0F),
                    new Part("mini_bus_seat", new Point(0.0F, 0.87425F, 0.0F),
                            1.3F, 0.0F, 0.0F, 0.0F),
                    new Part("mini_bus_dashboard", new Point(0.0F, 0.87425F, 1.3F),
                            1.3F, 0.0F, 0.0F, 0.0F)
            ),
            List.of(
                    wheel(-1, true, 9.0F, 0.0F, 13.5F,
                            0.938F, 1.19F, 1.19F, 1.3F, 1.0F, 4.76F),
                    wheel(1, true, 9.0F, 0.0F, 13.5F,
                            0.938F, 1.19F, 1.19F, 1.3F, 1.0F, 4.76F),
                    wheel(-1, false, 9.0F, 0.0F, -13.5F,
                            0.938F, 1.19F, 1.19F, 1.3F, 1.0F, 4.76F),
                    wheel(1, false, 9.0F, 0.0F, -13.5F,
                            0.938F, 1.19F, 1.19F, 1.3F, 1.0F, 4.76F)
            ),
            List.of(
                    seat(true, 5.0F, 3.0F, 9.0F, 1.3F, 1.0F, 4.76F),
                    seat(false, -5.0F, 3.0F, 9.0F, 1.3F, 1.0F, 4.76F),
                    seat(false, 5.0F, 3.0F, -3.0F, 1.3F, 1.0F, 4.76F),
                    seat(false, -5.0F, 3.0F, -3.0F, 1.3F, 1.0F, 4.76F),
                    seat(false, 5.0F, 3.0F, -15.0F, 1.3F, 1.0F, 4.76F)
            ),
            true, "big_tow_bar", new Point(0.0F, 0.0F, -25.0F),
            new Point(0.0F, 0.0F, 0.0F),
            null, null, List.of(), false
    );

    /* GolfCartEntity inherits unfinished helicopter motion in the 1.16 source and is unable
     * to drive on the ground (the class itself is marked TODO). The generated wheel, axle,
     * steering and four-seat definition is therefore run through the source land equations
     * so the intended cart remains usable by vanilla clients. */
    public static final LandVehicleSpec GOLF_CART = new LandVehicleSpec(
            "golf_cart", "Golf Cart", "golf_cart_body",
            2.0F, 1.0F, 1.0F,
            1.15F, -0.5F, 4.4F,
            25.0F, 35.0F, 16.0F * MODEL_UNIT * 1.15F,
            -12.5F * MODEL_UNIT * 1.15F, 5.0F,
            15_000.0F, 0.25F, 0.5F, 1.0F, "vehicle:entity.vehicle.helicopter_rotor",
            false, new Point(0.0F, 0.0F, 0.0F), null,
            new Part("go_kart_steering_wheel",
                    new Point(-0.39675F, 1.3277991F, 0.13126346F),
                    1.0925F, -45.0F, 0.0F, 0.0F),
            itemPart("fuel_door_closed", -13.0F, 5.5F, -6.0F,
                    0.5F, 0.0F, -90.0F, 0.0F, 1.15F, -0.5F, 4.4F),
            itemPart("key_hole", -8.5F, 2.75F, 8.5F,
                    0.5F, -67.5F, 0.0F, 0.0F, 1.15F, -0.5F, 4.4F),
            List.of(),
            List.of(
                    wheel(-1, true, 9.0F, 0.0F, 16.0F,
                            1.1F, 1.1F, 1.1F, 1.15F, -0.5F, 4.4F),
                    wheel(1, true, 9.0F, 0.0F, 16.0F,
                            1.1F, 1.1F, 1.1F, 1.15F, -0.5F, 4.4F),
                    wheel(-1, false, 9.0F, 0.0F, -12.5F,
                            1.1F, 1.1F, 1.1F, 1.15F, -0.5F, 4.4F),
                    wheel(1, false, 9.0F, 0.0F, -12.5F,
                            1.1F, 1.1F, 1.1F, 1.15F, -0.5F, 4.4F)
            ),
            List.of(
                    seat(true, 5.5F, 5.0F, -6.0F, 1.15F, -0.5F, 4.4F),
                    seat(false, -5.5F, 5.0F, -6.0F, 1.15F, -0.5F, 4.4F),
                    seat(false, 5.5F, 5.0F, -15.0F, 1.15F, -0.5F, 4.4F, 180.0F),
                    seat(false, -5.5F, 5.0F, -15.0F, 1.15F, -0.5F, 4.4F, 180.0F)
            ),
            false, "tow_bar", new Point(0.0F, 0.0F, 0.0F),
            new Point(0.0F, 0.0F, 0.0F),
            null, null, List.of(), false
    );

    /* BoatEntity's motion is empty in 1.16.X-dev. Runtime uses its last complete released
     * water-state/buoyancy equations while preserving these dev properties and transforms. */
    public static final LandVehicleSpec JET_SKI = new LandVehicleSpec(
            "jet_ski", "Jet Ski", "jet_ski_body",
            1.5F, 1.0F, 0.0F,
            1.25F, 2.75F, 0.0F,
            18.0F, 35.0F, 0.0F, 0.0F, 4.0F,
            15_000.0F, 0.5F, 1.2F, 2.2F, "vehicle:entity.jet_ski.engine",
            false, new Point(0.0F, 0.0F, 0.0F), null,
            new Part("quad_bike_handles",
                    new Point(0.0F, 1.2835938F, 0.53125F),
                    1.25F, -45.0F, 0.0F, 0.0F),
            new Part("small_fuel_door_closed",
                    new Point(0.0F, 0.9375F, 0.9140625F),
                    0.4375F, -90.0F, 0.0F, 0.0F),
            null, List.of(), List.of(),
            List.of(
                    new Seat(true, new Point(0.0F, 0.60546875F, 0.015625F), 0.0F),
                    new Seat(false, new Point(0.0F, 0.60546875F, -0.53125F), 0.0F)
            ),
            false, "tow_bar", new Point(0.0F, 0.0F, 0.0F),
            new Point(0.0F, -0.094F, -0.65F),
            null, null, List.of(), false
    );

    private static final Map<String, LandVehicleSpec> BY_ID = Map.ofEntries(
            Map.entry(GO_KART.id, GO_KART),
            Map.entry(LAWN_MOWER.id, LAWN_MOWER),
            Map.entry(QUAD_BIKE.id, QUAD_BIKE),
            Map.entry(TRACTOR.id, TRACTOR),
            Map.entry(DIRT_BIKE.id, DIRT_BIKE),
            Map.entry(MOPED.id, MOPED),
            Map.entry(OFF_ROADER.id, OFF_ROADER),
            Map.entry(SPORTS_CAR.id, SPORTS_CAR),
            Map.entry(MINI_BUS.id, MINI_BUS),
            Map.entry(GOLF_CART.id, GOLF_CART),
            Map.entry(JET_SKI.id, JET_SKI)
    );

    public static LandVehicleSpec byId(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static List<String> ids() {
        return BY_ID.keySet().stream().sorted().toList();
    }

    public Point bodyOrigin() {
        Point translation = bodyRenderTranslation();
        return new Point(translation.x(),
                translation.y() + (0.5F + (axleOffset + wheelOffset) * MODEL_UNIT) * bodyScale,
                translation.z());
    }

    /** Boat rendering applies its body translation before scale and without the land renderer's pixel conversion. */
    public Point bodyRenderTranslation() {
        return motionType() == MotionType.WATER
                ? new Point(0.0F, 0.0F, 0.25F)
                : new Point(0.0F, 0.0F, 0.0F);
    }

    public MotionType motionType() {
        return "jet_ski".equals(id) ? MotionType.WATER : MotionType.LAND;
    }

    public float steeringVisualAngle() {
        return motionType() == MotionType.WATER ? 15.0F : 25.0F;
    }

    /**
     * Vanilla ItemDisplay presentation needs this small Sports Car cabin correction.
     * The offset aligns the wheel's transformed bounds with the dashboard steering
     * column while preserving the source model, scale, angle, and animation.
     */
    public Point steeringDisplayOffset() {
        return "sports_car".equals(id)
                ? new Point(-3.0F * MODEL_UNIT, 6.0F * MODEL_UNIT, 2.0F * MODEL_UNIT)
                : new Point(0.0F, 0.0F, 0.0F);
    }

    /** Inverse of the resource-pack normalization required by vanilla's -16..32 model limit. */
    public float modelScaleCorrection(String model) {
        if (!"sports_car".equals(id)) {
            return 1.0F;
        }
        return switch (model) {
            case "sports_car_body", "sports_car_hood", "sports_car_left_door",
                    "sports_car_right_door", "sports_car_boot", "sports_car_seat",
                    "sports_car_dashboard", "sports_car_roof" -> 2.0F;
            default -> 1.0F;
        };
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
        return seat(driver, x, y, z, bodyScale, axleOffset, wheelOffset, 0.0F);
    }

    private static Seat seat(boolean driver, float x, float y, float z,
                             float bodyScale, float axleOffset, float wheelOffset,
                             float yawOffset) {
        return new Seat(driver, new Point(
                -x * MODEL_UNIT * bodyScale,
                (y + axleOffset + wheelOffset) * MODEL_UNIT * bodyScale,
                z * MODEL_UNIT * bodyScale
        ), yawOffset);
    }

    public record Point(float x, float y, float z) {
    }

    public record Part(String model, Point center, float scale,
                       float rotationX, float rotationY, float rotationZ,
                       Openable openable) {
        public Part(String model, Point center, float scale,
                    float rotationX, float rotationY, float rotationZ) {
            this(model, center, scale, rotationX, rotationY, rotationZ, null);
        }
    }

    public enum MotionType {
        LAND, WATER
    }

    public enum Axis {
        X, Y, Z
    }

    public record Openable(String id, Axis axis, float angle, int animationLength,
                           String openSound, String closeSound, Box interactionBox) {
    }

    /** Axis-aligned bounds in vehicle-root space, or in an openable part's local pivot space. */
    public record Box(Point min, Point max) {
        public double rayIntersection(Point origin, Point direction, double maximumDistance) {
            double near = 0.0D;
            double far = maximumDistance;
            float[] origins = {origin.x, origin.y, origin.z};
            float[] directions = {direction.x, direction.y, direction.z};
            float[] minimums = {min.x, min.y, min.z};
            float[] maximums = {max.x, max.y, max.z};
            for (int axis = 0; axis < 3; axis++) {
                double component = directions[axis];
                if (Math.abs(component) < 1.0E-8D) {
                    if (origins[axis] < minimums[axis] || origins[axis] > maximums[axis]) {
                        return Double.POSITIVE_INFINITY;
                    }
                    continue;
                }
                double first = (minimums[axis] - origins[axis]) / component;
                double second = (maximums[axis] - origins[axis]) / component;
                if (first > second) {
                    double temporary = first;
                    first = second;
                    second = temporary;
                }
                near = Math.max(near, first);
                far = Math.min(far, second);
                if (near > far) {
                    return Double.POSITIVE_INFINITY;
                }
            }
            return near <= maximumDistance && far >= 0.0D ? Math.max(0.0D, near)
                    : Double.POSITIVE_INFINITY;
        }
    }

    public record StorageCompartment(String key, String title, int rows, Box interactionBox) {
        public int size() {
            return rows * 9;
        }
    }

    public record Motorcycle(float maxLeanAngle, float steeringPivotZ,
                             float steeringAxisTilt, boolean frontWheelYaw180) {
    }

    public record MopedParts(List<Part> forkParts, Part chest, Point chestInteractionOffset) {
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

    public record Seat(boolean driver, Point sourceOffset, float yawOffset) {
    }
}
