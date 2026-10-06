package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.ResourcePackSender;
import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                ResourcePackSender.send(plugin, event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> event.getPlayer().sendRichMessage(
                    "<green>[Vehicle] Resource pack-ul r7 a fost încărcat.</green>");
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> {
                plugin.getLogger().warning("Resource pack " + event.getStatus() + " for "
                        + event.getPlayer().getName());
                event.getPlayer().sendRichMessage(
                        "<red>[Vehicle] Resource pack-ul nu s-a încărcat: " + event.getStatus()
                                + ". Folosește /vehicle pack pentru reîncercare.</red>");
            }
            default -> {
                // ACCEPTED and DOWNLOADED are intermediate states.
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Bukkit removes the player from the seat. Vehicle tick resets its input on the next tick.
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        vehicles.onWorldLoaded(event.getWorld());
    }
}
