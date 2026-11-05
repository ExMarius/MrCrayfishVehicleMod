package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.render.LandVehicleRig;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.UUID;

/** Hard memory/CPU cutoff: when a chunk unloads, every vehicle rooted in it is fully
 * hibernated (rig entities removed, complete state - fuel included - serialized through
 * VehicleManager's normal hibernation path), so an unloaded chunk owns zero live rig
 * entities and zero ticking physics. Vehicles re-materialize automatically when a
 * player comes within the activation distance again.
 *
 * La chunk-LOAD facem si curatenie de siguranta (bug-ul podium-duplicate la restart):
 * orice entitate de rig taguita al carei vehicul NU mai exista in registry si nici in
 * dosarele in asteptare e orfana (crash/kill fara onDisable, versiuni vechi) si se
 * sterge instant. Cost: o parcurgere mica a entitatilor chunkului, doar la load. */
public final class VehicleChunkListener implements Listener {
    private final VehicleManager vehicleManager;

    public VehicleChunkListener(VehicleManager vehicleManager) {
        this.vehicleManager = vehicleManager;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        vehicleManager.hibernateInChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Entity[] entities = event.getChunk().getEntities();
        if (entities.length == 0) {
            return;
        }
        for (Entity entity : entities) {
            if (!entity.getScoreboardTags().contains(LandVehicleRig.ENTITY_TAG)) {
                continue;
            }
            UUID ownerId = null;
            for (String tag : entity.getScoreboardTags()) {
                if (tag.startsWith("mcv_") && tag.length() == 4 + 36) {
                    try {
                        ownerId = UUID.fromString(tag.substring(4));
                    } catch (IllegalArgumentException ignored) {
                        // tag pus de altceva - nu e al masinii, ignoram
                    }
                }
            }
            if (ownerId == null) {
                continue; // rig fara owner-tag: nu atingem (cazuri straine, safe)
            }
            if (!vehicleManager.hasVehicleId(ownerId)) {
                entity.remove(); // orfan pur: vehiculul nu mai exista nicaieri
            }
        }
    }
}
