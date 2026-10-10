package com.mrcrayfish.vehicle.paper.economy;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Abstraction over "can this player pay for fuel". Real work is delegated to Vault when
 * it is installed; otherwise fuel is dispensed for free so a missing economy plugin
 * never blocks gameplay.
 */
public interface FuelEconomyService {

    /** {@code false} when there is no Vault economy backing this service (fuel is free). */
    boolean isAvailable();

    /** Funds currently available to the player, or {@link Double#POSITIVE_INFINITY} if free. */
    double balance(Player player);

    /**
     * Attempts to withdraw {@code amount} from the player. Always succeeds when no
     * economy is available.
     */
    boolean withdraw(Player player, double amount);

    /**
     * Detects Vault at runtime and returns the matching implementation. Vault's API
     * classes are only referenced inside {@link VaultFuelEconomyService}, which is never
     * loaded unless the Vault plugin is actually present, so a server without Vault
     * never needs the Vault jar on its classpath.
     */
    static FuelEconomyService resolve(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("[Vehicle] Vault nu a fost găsit; pompele de benzină vor livra "
                    + "combustibil gratuit.");
            return new FreeFuelEconomyService();
        }
        try {
            FuelEconomyService service = new VaultFuelEconomyService(plugin);
            plugin.getLogger().info("[Vehicle] Vault găsit; pompele de benzină vor taxa jucătorii "
                    + "prin economia serverului.");
            return service;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "[Vehicle] Vault a fost găsit dar economia nu e "
                    + "disponibilă; pompele de benzină vor livra combustibil gratuit.", exception);
            return new FreeFuelEconomyService();
        }
    }
}
