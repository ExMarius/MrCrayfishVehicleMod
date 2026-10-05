package com.mrcrayfish.vehicle.paper.physics;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Locale;

/**
 * Paper equivalent of SurfaceHelper for standard wheels. Values are copied from
 * SurfaceHelper.SurfaceType and WheelType.STANDARD in the Forge implementation.
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

        String name = material.name().toLowerCase(Locale.ROOT);
        if (name.contains("ice")) {
            return ICE;
        }
        if (name.contains("snow") || name.contains("leaves") || name.contains("cactus")) {
            return SNOW;
        }
        if (name.contains("dirt") || name.contains("grass") || name.contains("sand")
                || name.contains("gravel") || name.contains("clay") || name.contains("wool")
                || name.contains("soul_soil") || name.contains("soul_sand")) {
            return DIRT;
        }
        return material.isSolid() ? SOLID : NONE;
    }
}
