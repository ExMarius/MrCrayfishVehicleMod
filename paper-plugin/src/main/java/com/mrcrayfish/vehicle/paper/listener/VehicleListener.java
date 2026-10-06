package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.ResourcePackSender;
import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
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
        if (vehicles.byEntity(event.getRightClicked()).isPresent()) {
            event.setCancelled(true);
            vehicles.handleInteraction(event.getPlayer(), event.getRightClicked());
        } else if (vehicles.trailers().byEntity(event.getRightClicked()).isPresent()) {
            event.setCancelled(true);
            vehicles.trailers().handleInteraction(event.getPlayer(), event.getRightClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceCarriedVehicle(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.getBlockFace() == null) {
            return;
        }
        org.bukkit.Location location = event.getClickedBlock().getRelative(event.getBlockFace())
                .getLocation().add(0.5D, 0.0D, 0.5D);
        location.setYaw(event.getPlayer().getLocation().getYaw());
        if (vehicles.trailers().placeCarriedVehicle(event.getPlayer(), location)) {
            event.setCancelled(true);
        }
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
                    "<green>[Vehicle] Resource pack-ul r11 a fost încărcat.</green>");
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
        vehicles.trailers().onPlayerQuit(event.getPlayer());
        // Bukkit removes the player from the seat. Vehicle tick resets its input on the next tick.
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        vehicles.trailers().handleInventoryClick(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        vehicles.trailers().handleInventoryDrag(event);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        vehicles.onWorldLoaded(event.getWorld());
    }
}
