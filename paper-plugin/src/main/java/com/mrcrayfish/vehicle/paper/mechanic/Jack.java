package com.mrcrayfish.vehicle.paper.mechanic;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.UUID;

/** Starea runtime a unui cric pus in lume: pozitia BLOCULUI solid (zero entitati -
 * misiunea anti-lag), animatia si vehiculul atasat. Echivalentul Paper pentru
 * EntityJack + JackTileEntity din mod. */
final class Jack {
    enum State {
        DOWN,
        /** Automat (ca in mod): vehicul parcat pe placa se aliniaza la centru. */
        SNAPPING,
        LIFTING, LIFTED, LOWERING
    }

    final UUID id;
    /** Centrul blocului solid al cricului (x+0.5, y, z+0.5). */
    final Location location;
    State state = State.DOWN;
    /** 0.0 = jos, 1.0 = complet ridicat. */
    double progress;
    UUID vehicleId;
    /** Cine a pus cricul: pentru pickup (doar el sau un operator, si doar gol). */
    UUID ownerId;
    /** Y-ul vehiculului inainte de ridicare (inapoi la coborare: slab-top sau sol). */
    double startY;
    /** Ultimul vehicul coborat - nu se re-ridica singur cat timp ramane pe placa. */
    UUID recentReleasedId;
    int snapTicks;

    Jack(UUID id, Location location) {
        this.id = id;
        this.location = location;
    }

    Block block() {
        return location.getBlock();
    }

    boolean raised() {
        return state == State.LIFTED || progress > 0.5D;
    }
}
