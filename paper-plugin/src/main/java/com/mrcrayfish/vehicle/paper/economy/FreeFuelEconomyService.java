package com.mrcrayfish.vehicle.paper.economy;

import org.bukkit.entity.Player;

/** Fallback used when no Vault economy is installed: fuel never costs anything. */
final class FreeFuelEconomyService implements FuelEconomyService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public double balance(Player player) {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        return true;
    }
}
