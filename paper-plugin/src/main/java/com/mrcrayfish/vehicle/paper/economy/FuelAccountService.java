package com.mrcrayfish.vehicle.paper.economy;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Lets {@code FuelStationManager} charge a player for fuel without caring whether a real
 * economy plugin is installed at all. {@link #resolve} picks the right implementation once,
 * at plugin start-up.
 */
public interface FuelAccountService {
    /** Whether this implementation is backed by a real currency (false for the free fallback). */
    boolean isPriced();

    /** The player's current spendable balance, or {@code Double.MAX_VALUE} if unpriced. */
    double balanceOf(Player player);

    /**
     * Attempts to deduct {@code amount} from {@code player}'s balance.
     *
     * @return true if the deduction succeeded (or this implementation is unpriced and nothing
     * needed to be deducted), false if the player could not afford it.
     */
    boolean deduct(Player player, double amount);

    /** Resolves Vault's Economy service if present and enabled, otherwise the free fallback. */
    static FuelAccountService resolve(VehiclePlugin plugin) {
        try {
            RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration =
                    plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (registration != null) {
                plugin.getLogger().info("Vault economy detected; fuel station purchases will be charged.");
                return new VaultFuelAccountService(registration.getProvider());
            }
        } catch (NoClassDefFoundError error) {
            // Vault isn't installed at all, so its Economy class was never loaded by the
            // server; fall through to the free implementation below.
        }
        plugin.getLogger().info("No Vault economy found; fuel stations will dispense fuel for free.");
        return new FreeFuelAccountService();
    }
}
