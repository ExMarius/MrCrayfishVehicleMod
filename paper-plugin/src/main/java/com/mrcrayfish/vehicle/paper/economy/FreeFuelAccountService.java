package com.mrcrayfish.vehicle.paper.economy;

import org.bukkit.entity.Player;

/** Used whenever no Vault-backed economy plugin is installed: fuel never costs anything. */
public final class FreeFuelAccountService implements FuelAccountService {
    @Override
    public boolean isPriced() {
        return false;
    }

    @Override
    public double balanceOf(Player player) {
        return Double.MAX_VALUE;
    }

    @Override
    public boolean deduct(Player player, double amount) {
        return true;
    }
}
