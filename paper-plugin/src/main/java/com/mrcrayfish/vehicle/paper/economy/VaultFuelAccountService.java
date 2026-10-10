package com.mrcrayfish.vehicle.paper.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

/** Charges real currency through whatever economy plugin Vault has registered. */
public final class VaultFuelAccountService implements FuelAccountService {
    private final Economy economy;

    public VaultFuelAccountService(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean isPriced() {
        return true;
    }

    @Override
    public double balanceOf(Player player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean deduct(Player player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (economy.getBalance(player) < amount) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }
}
