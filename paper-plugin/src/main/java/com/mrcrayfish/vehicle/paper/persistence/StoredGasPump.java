package com.mrcrayfish.vehicle.paper.persistence;

import java.util.UUID;

/** Serializable snapshot of one placed gas pump. */
public record StoredGasPump(
        UUID id,
        UUID worldId,
        double x,
        double y,
        double z,
        float yaw
) {
}
