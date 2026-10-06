package com.mrcrayfish.vehicle.paper.physics;

import com.mrcrayfish.vehicle.paper.vehicle.GoKartProperties;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Locale;

/**
 * Paper equivalent of SurfaceHelper for STANDARD wheels. The friction and
 * traction values are copied from SurfaceHelper.SurfaceType and WheelType.
 */
public record SurfaceProfile(float friction, float tractionFactor) {
    private static final SurfaceProfile SOLID = new SurfaceProfile(
            0.9F * GoKartProperties.STANDARD_ROAD_FRICTION, 1.0F);
    private static final SurfaceProfile DIRT = new SurfaceProfile(
            1.1F * GoKartProperties.STANDARD_DIRT_FRICTION, 0.9F);
    private static final SurfaceProfile SNOW = new SurfaceProfile(
            1.5F * GoKartProperties.STANDARD_SNOW_FRICTION, 0.9F);
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
        if (name.contains("snow") || name.contains("leaves") || name.contains("cactus")
                || name.contains("coral") || name.equals("tnt") || name.contains("cake")) {
            return SNOW;
        }
        if (name.contains("dirt") || name.contains("grass") || name.contains("sand")
                || name.contains("gravel") || name.contains("clay") || name.contains("wool")
                || name.contains("carpet") || name.contains("sponge") || name.contains("flower")
                || name.contains("sapling") || name.contains("bush") || name.contains("fern")
                || name.contains("roots") || name.contains("crop") || name.contains("vine")
                || name.contains("mushroom") || name.contains("wart") || name.contains("lily_pad")) {
            return DIRT;
        }
        return material.isSolid() ? SOLID : NONE;
    }
}
