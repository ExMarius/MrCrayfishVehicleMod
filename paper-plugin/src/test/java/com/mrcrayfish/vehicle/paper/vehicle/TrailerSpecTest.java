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
}
