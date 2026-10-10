package com.mrcrayfish.vehicle.paper.vehicle;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Source-derived properties for every trailer in the 1.16 vehicle mod.
 * Model-space values are kept in sixteenths of a block, as in the generated
 * vehicle property JSON.
 */
public record TrailerSpec(
        String id,
        String displayName,
        String bodyModel,
        Kind kind,
        float entityWidth,
        float entityHeight,
        float bodyScale,
        float axleOffset,
        float wheelOffset,
        float wheelX,
        float wheelZ,
        float hitchOffset,
        boolean canTowTrailers,
        LandVehicleSpec.Point towBarOffset
) {
    public static final TrailerSpec FERTILIZER = trailer(
            "fertilizer", "Fertilizer", "fertilizer_body", Kind.FERTILIZER,
            1.5F, 1.0F, 9.5F, 0.0F, -17.0F, false);
    public static final TrailerSpec SEEDER = trailer(
            "seeder", "Seeder", "seeder_body", Kind.SEEDER,
            1.5F, 1.0F, 15.5F, 0.0F, -16.0F, false);
    public static final TrailerSpec STORAGE_TRAILER = new TrailerSpec(
            "storage_trailer", "Storage Trailer", "storage_trailer_body", Kind.STORAGE,
            1.0F, 1.0F, 1.1F, -0.5F, 5.0F,
            9.5F, 0.0F, -16.0F, true,
            new LandVehicleSpec.Point(0.0F, 0.0F, -12.0F));
    public static final TrailerSpec FLUID_TRAILER = trailer(
            "fluid_trailer", "Fluid Trailer", "fluid_trailer_body", Kind.FLUID,
            1.5F, 1.5F, 9.5F, -2.5F, -25.0F, false);
    public static final TrailerSpec VEHICLE_TRAILER = trailer(
            "vehicle_trailer", "Vehicle Trailer", "vehicle_trailer_body", Kind.VEHICLE,
            1.5F, 0.75F, 12.5F, -2.5F, -23.0F, false);

    private static final Map<String, TrailerSpec> BY_ID = Map.of(
            FERTILIZER.id, FERTILIZER,
            SEEDER.id, SEEDER,
            STORAGE_TRAILER.id, STORAGE_TRAILER,
            FLUID_TRAILER.id, FLUID_TRAILER,
            VEHICLE_TRAILER.id, VEHICLE_TRAILER
    );

    private static TrailerSpec trailer(String id, String displayName, String bodyModel, Kind kind,
                                       float width, float height, float wheelX, float wheelZ,
                                       float hitchOffset, boolean canTow) {
        return new TrailerSpec(id, displayName, bodyModel, kind, width, height,
                1.1F, -0.5F, 5.0F, wheelX, wheelZ, hitchOffset, canTow,
                new LandVehicleSpec.Point(0.0F, 0.0F, 0.0F));
    }

    public static TrailerSpec byId(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static List<String> ids() {
        return BY_ID.keySet().stream().sorted().toList();
    }

    public float bodyOriginY() {
        return (0.5F + (axleOffset + wheelOffset) * LandVehicleSpec.MODEL_UNIT) * bodyScale;
    }

    /**
     * Converts a block-space translation made by a source trailer renderer into
     * the Paper rig's root space. Source renderers run after the common body
     * scale and axle/wheel translations, so every supplemental body part must
     * inherit both. The first Paper trailer renderer only applied the scale,
     * which left spikers, the storage chest, and the fluid surface almost one
     * block below their original positions.
     */
    public float bodyPartX(float sourceBlocks) {
        return sourceBlocks * bodyScale;
    }

    public float bodyPartY(float sourceBlocks) {
        return bodyOriginY() + sourceBlocks * bodyScale;
    }

    public float bodyPartZ(float sourceBlocks) {
        return sourceBlocks * bodyScale;
    }

    /** Source uses a bottom-anchored ChestModel; ItemDisplay uses a centered item. */
    public float storageChestCenterY() {
        return bodyPartY(-6.0F * LandVehicleSpec.MODEL_UNIT) + 0.5F;
    }

    public float wheelCenterY() {
        return wheelOffset * LandVehicleSpec.MODEL_UNIT * bodyScale;
    }

    public float wheelRadius() {
        return 0.25F * bodyScale * 1.25F;
    }

    public float hitchDistance() {
        return hitchOffset * bodyScale * LandVehicleSpec.MODEL_UNIT;
    }

    public enum Kind {
        FERTILIZER,
        SEEDER,
        STORAGE,
        FLUID,
        VEHICLE
    }
}
