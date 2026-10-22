package com.mrcrayfish.vehicle.paper.mechanic;

import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;

import java.util.UUID;

/** Starea runtime a unui cric pus in lume: pozitia, rig-ul (baza + cap + interaction),
 *  animatia si vehiculul atasat. Echivalentul Paper pentru EntityJack + JackTileEntity. */
final class Jack {
    enum State {
        DOWN, LIFTING, LIFTED, LOWERING
    }

    final UUID id;
    final Location location;
    final ItemDisplay base;
    final ItemDisplay head;
    final Interaction interaction;
    State state = State.DOWN;
    /** 0.0 = jos, 1.0 = complet ridicat. */
    double progress;
    UUID vehicleId;

    Jack(UUID id, Location location, ItemDisplay base, ItemDisplay head, Interaction interaction) {
        this.id = id;
        this.location = location;
        this.base = base;
        this.head = head;
        this.interaction = interaction;
    }

    boolean raised() {
        return state == State.LIFTED || progress > 0.5D;
    }
}
