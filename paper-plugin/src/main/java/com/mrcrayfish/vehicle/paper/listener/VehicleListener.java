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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Locale;
import java.util.regex.Pattern;

public final class VehicleListener implements Listener {
    private static final Pattern GLOBAL_ENTITY_KILL = Pattern.compile(
            "(?:^|\\s)(?:minecraft:)?kill\\s+@e(?:\\b|\\[)", Pattern.CASE_INSENSITIVE);
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    public VehicleListener(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (unsafeGlobalKill(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendRichMessage(
                    "<red>Comanda a fost blocată: ar elimina scaunele vehiculelor și jucătorii.</red> "
                            + "<yellow>Exclude tag-ul cu tag=!mcv_plugin_vehicle.</yellow>");
            plugin.getLogger().warning("Blocked an unsafe entity-wide kill command from "
                    + event.getPlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (unsafeGlobalKill(event.getCommand())) {
            event.setCancelled(true);
            event.getSender().sendRichMessage(
                    "<red>Comanda a fost blocată. Folosește tag=!mcv_plugin_vehicle în selector.</red>");
            plugin.getLogger().warning("Blocked an unsafe entity-wide kill command from "
                    + event.getSender().getName());
        }
    }

    static boolean unsafeGlobalKill(String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        java.util.regex.Matcher matcher = GLOBAL_ENTITY_KILL.matcher(normalized);
        while (matcher.find()) {
            int selectorStart = normalized.toLowerCase(Locale.ROOT).indexOf("@e", matcher.start());
            int selectorEnd = selectorStart + 2;
            if (selectorEnd < normalized.length() && normalized.charAt(selectorEnd) == '[') {
                int closingBracket = normalized.indexOf(']', selectorEnd + 1);
                selectorEnd = closingBracket < 0 ? normalized.length() : closingBracket + 1;
            }
            String selector = normalized.substring(selectorStart, selectorEnd).toLowerCase(Locale.ROOT);
            if (!selector.contains("tag=!mcv_plugin_vehicle")) {
                return true;
            }
        }
        return false;
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
                    "<green>[Vehicle] Resource pack-ul r12 a fost încărcat.</green>");
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
