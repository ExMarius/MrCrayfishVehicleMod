package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;

public final class VehicleListener implements Listener {
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    public VehicleListener(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (vehicles.byEntity(event.getRightClicked()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        vehicles.handleInteraction(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendResourcePack(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Bukkit removes the player from the seat. Vehicle tick resets its input on the next tick.
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        vehicles.onWorldLoaded(event.getWorld());
    }

    private void sendResourcePack(Player player) {
        if (!player.isOnline()) {
            return;
        }
        String url = plugin.getConfig().getString("resource-pack.url", "").trim();
        if (url.isEmpty()) {
            if (player.isOp()) {
                player.sendRichMessage("<yellow>[Vehicle] Configurează resource-pack.url înainte de testarea modelelor.</yellow>");
            }
            return;
        }
        String hash = plugin.getConfig().getString("resource-pack.sha1", "").trim();
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", true);
        String prompt = plugin.getConfig().getString("resource-pack.prompt",
                "Acest resource pack este necesar pentru vehicule.");
        player.setResourcePack(url, hash.isEmpty() ? null : hash, required, Component.text(prompt));
    }
}
