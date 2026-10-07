package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.vehicle.TrailerSpec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerInventoryLayoutTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void fertilizerCargoUsesSourceBaseSpacingCountAndScale() {
        ItemStack boneMeal = new ItemStack(Material.BONE_MEAL, 64);
        List<TrailerRig.InventoryLayout> layouts = TrailerRig.inventoryLayout(
                TrailerSpec.FERTILIZER, new ItemStack[]{boneMeal});

        assertEquals(2, layouts.size());
        assertLayout(layouts.getFirst(), -0.378125F, 0.653125F, -0.20625F, 0.495F);
        assertLayout(layouts.getLast(), -0.130625F, 0.6840625F, -0.20625F, 0.495F);
        assertEquals(Material.BONE_MEAL, layouts.getFirst().stack().getType());
        assertEquals(64, layouts.getFirst().stack().getAmount());
    }

    @Test
    void seederCargoUsesFourDisplaysForAFullStackAndSourceGrid() {
        ItemStack wheat = new ItemStack(Material.WHEAT_SEEDS, 64);
        List<TrailerRig.InventoryLayout> layouts = TrailerRig.inventoryLayout(
                TrailerSpec.SEEDER, new ItemStack[]{wheat});

        assertEquals(4, layouts.size());
        assertLayout(layouts.getFirst(), -0.721875F, 0.653125F, -0.1375F, 0.495F);
        assertLayout(layouts.get(1), -0.350625F, 0.653125F, -0.1375F, 0.495F);
        assertTrue(Math.abs(layouts.get(1).rotation().z) > EPSILON);
    }

    @Test
    void trailersWithoutSourceCargoRenderingProduceNoLayouts() {
        assertTrue(TrailerRig.inventoryLayout(TrailerSpec.STORAGE_TRAILER,
                new ItemStack[]{new ItemStack(Material.BONE_MEAL, 64)}).isEmpty());
    }

    private static void assertLayout(TrailerRig.InventoryLayout layout,
                                     float x, float y, float z, float scale) {
        assertEquals(x, layout.center().x, EPSILON);
        assertEquals(y, layout.center().y, EPSILON);
        assertEquals(z, layout.center().z, EPSILON);
        assertEquals(scale, layout.scale().x, EPSILON);
        assertEquals(scale, layout.scale().y, EPSILON);
        assertEquals(scale, layout.scale().z, EPSILON);
    }
}
