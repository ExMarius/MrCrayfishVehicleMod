package com.mrcrayfish.vehicle.paper.persistence;

import java.util.UUID;

/** Serializable snapshot of one admin-registered gas pump block. */
public record StoredGasPump(UUID id, UUID worldId, int x, int y, int z) {
}
