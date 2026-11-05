package com.mrcrayfish.vehicle.paper.shop;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault bridge for the dealer shop. Vault classes are touched ONLY when the Vault plugin is
 * actually installed (VehiclePlugin probes first), otherwise this class is never loaded and
 * the shop politely explains what to install instead of crashing.
 */
public final class ShopEconomy {
    private static Economy economy;

    private ShopEconomy() {
    }

    public static void init(Plugin plugin) {
        economy = null;
        RegisteredServiceProvider<Economy> registration =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            plugin.getLogger().warning("[Magazin] Vault e instalat dar NU exista plugin de economie "
                    + "(EssentialsX etc) - cumpararile sunt oprite pana instalezi economia.");
            return;
        }
        economy = registration.getProvider();
        plugin.getLogger().info("[Magazin] Economie conectata: " + economy.getName());
    }

    public static boolean ready() {
        return economy != null;
    }

    public static double balance(Player player) {
        return economy.getBalance(player);
    }

    public static boolean withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** Ramburs: banii inapoi dupa o livrare esuata (audit ciclul Y). */
    public static boolean deposit(Player player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
