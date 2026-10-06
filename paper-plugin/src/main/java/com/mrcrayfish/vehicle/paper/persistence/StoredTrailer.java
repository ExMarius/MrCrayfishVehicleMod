package com.mrcrayfish.vehicle.paper.persistence;

import com.mrcrayfish.vehicle.paper.runtime.PaperTrailer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Serializable snapshot of one Paper trailer. */
public record StoredTrailer(
        UUID id,
        String type,
        UUID worldId,
        double x,
        double y,
        double z,
        float yaw,
        PaperTrailer.PullerType pullerType,
        UUID pullerId,
        UUID loadedVehicleId,
        Material fluidMaterial,
        int fluidAmount,
        ItemStack[] inventory
) {
}
