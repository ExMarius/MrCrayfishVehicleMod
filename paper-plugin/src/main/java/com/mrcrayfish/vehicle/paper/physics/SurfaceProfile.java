package com.mrcrayfish.vehicle.paper.physics;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Paper equivalent of the original SurfaceHelper material map for STANDARD
 * wheels. Checks use exact material families rather than substring matching,
 * so solid blocks such as sandstone are not accidentally treated as sand.
 */
public record SurfaceProfile(float friction, float tractionFactor) {
    private static final SurfaceProfile SOLID = new SurfaceProfile(0.9F * 1.1F, 1.0F);
    private static final SurfaceProfile DIRT = new SurfaceProfile(1.1F * 1.3F, 0.9F);
    private static final SurfaceProfile SNOW = new SurfaceProfile(1.5F * 1.7F, 0.9F);
    private static final SurfaceProfile ICE = new SurfaceProfile(1.5F, 0.01F);
    private static final SurfaceProfile NONE = new SurfaceProfile(0.0F, 1.0F);

    public static SurfaceProfile at(Block block) {
        Material material = block.getType();
        if (material.isAir()) {
            return NONE;
        }

        String name = material.name();
        if (name.equals("ICE") || name.equals("PACKED_ICE") || name.equals("BLUE_ICE")
                || name.equals("FROSTED_ICE")) {
            return ICE;
        }
        if (name.equals("SNOW") || name.equals("SNOW_BLOCK") || name.equals("POWDER_SNOW")
                || name.endsWith("_LEAVES") || name.equals("CACTUS") || name.equals("TNT")
                || name.contains("CORAL") || name.equals("CAKE") || name.endsWith("_CAKE")) {
            return SNOW;
        }
        if (isDirtLike(name)) {
            return DIRT;
        }
        return material.isSolid() ? SOLID : NONE;
    }

    private static boolean isDirtLike(String name) {
        if (name.equals("DIRT") || name.endsWith("_DIRT") || name.equals("DIRT_PATH")
                || name.equals("GRASS_BLOCK") || name.equals("MYCELIUM") || name.equals("PODZOL")
                || name.equals("FARMLAND") || name.equals("MUD") || name.equals("MUDDY_MANGROVE_ROOTS")
                || name.equals("SAND") || name.equals("RED_SAND") || name.equals("SOUL_SAND")
                || name.equals("SOUL_SOIL") || name.equals("GRAVEL") || name.equals("CLAY")
                || name.equals("SPONGE") || name.equals("WET_SPONGE")
                || name.endsWith("_WOOL") || name.endsWith("_CARPET") || name.equals("MOSS_BLOCK")) {
            return true;
        }
        return name.equals("SHORT_GRASS") || name.equals("TALL_GRASS") || name.equals("FERN")
                || name.equals("LARGE_FERN") || name.equals("DEAD_BUSH") || name.equals("LILY_PAD")
                || name.equals("SUGAR_CANE") || name.equals("BAMBOO_SAPLING")
                || name.equals("NETHER_SPROUTS") || name.equals("NETHER_WART")
                || name.equals("WARPED_FUNGUS") || name.equals("CRIMSON_FUNGUS")
                || name.endsWith("_FLOWER") || name.endsWith("_SAPLING") || name.endsWith("_TULIP")
                || name.endsWith("_MUSHROOM") || name.endsWith("_ROOTS") || name.endsWith("_VINES")
                || name.endsWith("_BUSH") || name.endsWith("_CROP");
    }
}
