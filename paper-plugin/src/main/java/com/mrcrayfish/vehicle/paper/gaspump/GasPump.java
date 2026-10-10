package com.mrcrayfish.vehicle.paper.gaspump;

import org.bukkit.Location;

import java.util.UUID;

/** Runtime state for one placed gas pump: where it is, its rig, and who (if anyone) holds its nozzle. */
public final class GasPump {
    private final UUID id;
    private final Location location;
    private GasPumpRig rig;
    private UUID holderId;
    private int glugCooldown;

    public GasPump(UUID id, Location location, GasPumpRig rig) {
        this.id = id;
        this.location = location;
        this.rig = rig;
    }

    public UUID id() {
        return id;
    }

    public Location location() {
        return location;
    }

    public GasPumpRig rig() {
        return rig;
    }

    public void setRig(GasPumpRig rig) {
        this.rig = rig;
    }

    public UUID holderId() {
        return holderId;
    }

    public void setHolderId(UUID holderId) {
        this.holderId = holderId;
    }

    public boolean hasHolder() {
        return holderId != null;
    }

    public int glugCooldown() {
        return glugCooldown;
    }

    public void setGlugCooldown(int glugCooldown) {
        this.glugCooldown = glugCooldown;
    }
}
