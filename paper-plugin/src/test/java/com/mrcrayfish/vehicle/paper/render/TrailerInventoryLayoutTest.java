package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.vehicle.TrailerSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerInventoryLayoutTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void fertilizerCargoUsesSourceBaseSpacingCountAndScale() {
        List<TrailerRig.InventoryTransform> transforms = TrailerRig.inventoryTransforms(
                TrailerSpec.FERTILIZER, 64);

        assertEquals(2, transforms.size());
        assertTransform(transforms.getFirst(), -0.378125F, 0.653125F, -0.20625F, 0.495F);
        assertTransform(transforms.getLast(), -0.130625F, 0.6840625F, -0.20625F, 0.495F);
        assertEquals(0, transforms.getFirst().slot());
    }

    @Test
    void seederCargoUsesFourDisplaysForAFullStackAndSourceGrid() {
        List<TrailerRig.InventoryTransform> transforms = TrailerRig.inventoryTransforms(
                TrailerSpec.SEEDER, 64);

        assertEquals(4, transforms.size());
        assertTransform(transforms.getFirst(), -0.721875F, 0.653125F, -0.1375F, 0.495F);
        assertTransform(transforms.get(1), -0.350625F, 0.653125F, -0.1375F, 0.495F);
        assertTrue(Math.abs(transforms.get(1).rotation().z) > EPSILON);
    }

    @Test
    void emptySlotsRetainTheirSourceInventoryIndex() {
        List<TrailerRig.InventoryTransform> transforms = TrailerRig.inventoryTransforms(
                TrailerSpec.SEEDER, 0, 16);

        assertEquals(1, transforms.size());
        assertEquals(1, transforms.getFirst().slot());
    }

    @Test
    void trailersWithoutSourceCargoRenderingProduceNoTransforms() {
        assertTrue(TrailerRig.inventoryTransforms(TrailerSpec.STORAGE_TRAILER, 64).isEmpty());
    }

    private static void assertTransform(TrailerRig.InventoryTransform transform,
                                        float x, float y, float z, float scale) {
        assertEquals(x, transform.center().x, EPSILON);
        assertEquals(y, transform.center().y, EPSILON);
        assertEquals(z, transform.center().z, EPSILON);
        assertEquals(scale, transform.scale().x, EPSILON);
        assertEquals(scale, transform.scale().y, EPSILON);
        assertEquals(scale, transform.scale().z, EPSILON);
    }
}
