package com.mrcrayfish.vehicle.paper.runtime;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperTrailerSourceParityTest {
    @Test
    void seederInventoryMatchesTheOriginalForgeSeedsTagSet() {
        assertTrue(PaperTrailer.isSeederSupply(Material.WHEAT_SEEDS));
        assertTrue(PaperTrailer.isSeederSupply(Material.BEETROOT_SEEDS));
        assertTrue(PaperTrailer.isSeederSupply(Material.MELON_SEEDS));
        assertTrue(PaperTrailer.isSeederSupply(Material.PUMPKIN_SEEDS));

        assertFalse(PaperTrailer.isSeederSupply(Material.CARROT));
        assertFalse(PaperTrailer.isSeederSupply(Material.POTATO));
        assertFalse(PaperTrailer.isSeederSupply(Material.BONE_MEAL));
    }

    @Test
    void seederPlantingKeepsTheSourceCropBlockRestriction() {
        assertEquals(Material.WHEAT, PaperTrailer.cropFor(Material.WHEAT_SEEDS));
        assertEquals(Material.BEETROOTS, PaperTrailer.cropFor(Material.BEETROOT_SEEDS));
        assertEquals(Material.CARROTS, PaperTrailer.cropFor(Material.CARROT));
        assertEquals(Material.POTATOES, PaperTrailer.cropFor(Material.POTATO));
        assertNull(PaperTrailer.cropFor(Material.MELON_SEEDS));
        assertNull(PaperTrailer.cropFor(Material.PUMPKIN_SEEDS));
    }
}
