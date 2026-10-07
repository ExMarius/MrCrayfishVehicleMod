package com.mrcrayfish.vehicle.paper.vehicle;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerSpecTest {
    @Test
    void registersEveryOriginalTrailer() {
        assertEquals(Set.of("fertilizer", "seeder", "storage_trailer", "fluid_trailer", "vehicle_trailer"),
                Set.copyOf(TrailerSpec.ids()));
    }

    @Test
    void preservesSourceHitchOffsetsAndScale() {
        assertEquals(-17.0F, TrailerSpec.FERTILIZER.hitchOffset());
        assertEquals(-16.0F, TrailerSpec.SEEDER.hitchOffset());
        assertEquals(-16.0F, TrailerSpec.STORAGE_TRAILER.hitchOffset());
        assertEquals(-25.0F, TrailerSpec.FLUID_TRAILER.hitchOffset());
        assertEquals(-23.0F, TrailerSpec.VEHICLE_TRAILER.hitchOffset());
        for (String id : TrailerSpec.ids()) {
            assertEquals(1.1F, TrailerSpec.byId(id).bodyScale());
        }
    }

    @Test
    void onlyStorageTrailerCanContinueAChain() {
        assertTrue(TrailerSpec.STORAGE_TRAILER.canTowTrailers());
        assertEquals(-12.0F, TrailerSpec.STORAGE_TRAILER.towBarOffset().z());
        assertFalse(TrailerSpec.FERTILIZER.canTowTrailers());
        assertFalse(TrailerSpec.SEEDER.canTowTrailers());
        assertFalse(TrailerSpec.FLUID_TRAILER.canTowTrailers());
        assertFalse(TrailerSpec.VEHICLE_TRAILER.canTowTrailers());
    }

    @Test
    void supplementalPartsInheritTheOriginalBodyMatrix() {
        TrailerSpec spec = TrailerSpec.FERTILIZER;
        assertEquals(0.859375F, spec.bodyOriginY(), 0.000001F);
        assertEquals(0.309375F, spec.bodyPartY(-0.5F), 0.000001F);
        assertEquals(-0.48125F, spec.bodyPartZ(-0.4375F), 0.000001F);

        assertEquals(0.144375F, TrailerSpec.SEEDER.bodyPartY(-0.65F), 0.000001F);
        assertEquals(0.446875F,
                TrailerSpec.STORAGE_TRAILER.bodyPartY(-6.0F * LandVehicleSpec.MODEL_UNIT),
                0.000001F);
        assertEquals(0.946875F, TrailerSpec.STORAGE_TRAILER.storageChestCenterY(), 0.000001F);
    }

    @Test
    void fluidCuboidMatchesTheOriginalRendererCoordinates() {
        TrailerSpec spec = TrailerSpec.FLUID_TRAILER;
        assertEquals(-0.42625F, spec.bodyPartX(-0.3875F), 0.000001F);
        assertEquals(0.653125F, spec.bodyPartY(-0.1875F), 0.000001F);
        assertEquals(-1.089F, spec.bodyPartZ(-0.99F), 0.000001F);
        assertEquals(0.83875F, spec.bodyScale() * 0.7625F, 0.000001F);
        assertEquals(0.680625F,
                spec.bodyScale() * 9.9F * LandVehicleSpec.MODEL_UNIT,
                0.000001F);
        assertEquals(1.837F, spec.bodyScale() * 1.67F, 0.000001F);
    }
}
