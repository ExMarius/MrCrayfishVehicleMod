package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

/** Hard memory/CPU cutoff: when a chunk unloads, every vehicle rooted in it is fully
 * hibernated (rig entities removed, complete state - fuel included - serialized through
 * VehicleManager's normal hibernation path), so an unloaded chunk owns zero live rig
 * entities and zero ticking physics. Vehicles re-materialize automatically when a
 * player comes within the activation distance again. */
public final class VehicleChunkListener implements Listener {
    private final VehicleManager vehicleManager;

    public VehicleChunkListener(VehicleManager vehicleManager) {
        this.vehicleManager = vehicleManager;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        vehicleManager.hibernateInChunk(event.getChunk());
    }
}
