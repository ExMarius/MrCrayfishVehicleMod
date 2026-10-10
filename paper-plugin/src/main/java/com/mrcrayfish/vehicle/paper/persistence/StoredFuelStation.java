package com.mrcrayfish.vehicle.paper.persistence;

import org.bukkit.block.BlockFace;

import java.util.UUID;

/**
 * Serializable snapshot of one admin-registered fuel station: the ground block it sits on
 * and which cardinal direction its nozzle holster faces.
 */
public record StoredFuelStation(
        UUID id,
        UUID worldId,
        int x,
        int y,
        int z,
        BlockFace facing
) {
}
