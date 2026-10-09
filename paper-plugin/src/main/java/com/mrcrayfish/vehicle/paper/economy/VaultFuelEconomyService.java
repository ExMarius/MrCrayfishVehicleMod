package com.mrcrayfish.vehicle.paper.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Charges real server money through Vault. This class is only loaded by the JVM when
 * {@link FuelEconomyService#resolve} actually instantiates it, which only happens after
 * confirming the Vault plugin is installed — so referencing Vault's API types here is
 * safe even on servers that never install Vault.
 */
final class VaultFuelEconomyService implements FuelEconomyService {
    private final Economy economy;

    VaultFuelEconomyService(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> provider =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            throw new IllegalStateException("Vault is installed but no economy plugin is registered with it.");
        }
        this.economy = provider.getProvider();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public double balance(Player player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (economy.getBalance(player) + 1.0E-6D < amount) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
}
