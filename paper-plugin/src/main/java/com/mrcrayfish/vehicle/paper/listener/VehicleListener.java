package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.ResourcePackSender;
import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.render.LandVehicleRig;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.util.RayTraceResult;

import java.util.Locale;
import java.util.regex.Pattern;

public final class VehicleListener implements Listener {
    private static final Pattern GLOBAL_ENTITY_KILL = Pattern.compile(
            "(?:^|[\\s/])(?:minecraft:)?kill\\s+@e(?:\\b|\\[)", Pattern.CASE_INSENSITIVE);
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    public VehicleListener(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String protectedCommand = protectEntityKill(event.getMessage());
        if (!protectedCommand.equals(event.getMessage())) {
            dismountPlayersFromUnprotectedVehicles();
            event.setMessage(protectedCommand);
            event.getPlayer().sendRichMessage(
                    "<yellow>Selector protejat automat:</yellow> "
                            + "<gray>jucătorii și entitățile vehiculelor au fost excluse.</gray>");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerCommand(ServerCommandEvent event) {
        String protectedCommand = protectEntityKill(event.getCommand());
        if (!protectedCommand.equals(event.getCommand())) {
            dismountPlayersFromUnprotectedVehicles();
            event.setCommand(protectedCommand);
            event.getSender().sendRichMessage(
                    "<yellow>Selector protejat automat: jucătorii și vehiculele au fost excluse.</yellow>");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains(LandVehicleRig.ENTITY_TAG)) {
            if (event instanceof EntityDamageByEntityEvent damage
                    && damage.getDamager() instanceof Player player) {
                vehicles.handleAttack(player, event.getEntity());
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEntityTransform(EntityTransformEvent event) {
        if (event.getEntity().getScoreboardTags().contains(LandVehicleRig.ENTITY_TAG)) {
            event.setCancelled(true);
        }
    }

    /**
     * Paper can loop in AbstractHorse's dismount-location search if a ridden
     * ordinary horse is removed directly by /kill. Plugin seat carriers are
     * excluded by the rewritten selector; riders of every other mount are
     * detached while that mount is still valid, before the command executes.
     */
    private static void dismountPlayersFromUnprotectedVehicles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Entity mount = player.getVehicle();
            if (mount != null && !mount.getScoreboardTags().contains(LandVehicleRig.ENTITY_TAG)) {
                player.leaveVehicle();
            }
        }
    }

    static String protectEntityKill(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher matcher = GLOBAL_ENTITY_KILL.matcher(command);
        StringBuilder protectedCommand = new StringBuilder(command.length() + 80);
        int copiedUntil = 0;
        boolean changed = false;
        while (matcher.find()) {
            int selectorStart = lower.indexOf("@e", matcher.start());
            int selectorEnd = selectorStart + 2;
            if (selectorEnd < command.length() && command.charAt(selectorEnd) == '[') {
                int closingBracket = command.indexOf(']', selectorEnd + 1);
                if (closingBracket < 0) {
                    continue;
                }
                selectorEnd = closingBracket + 1;
            }
            String selector = command.substring(selectorStart, selectorEnd);
            String replacement = protectSelector(selector);
            if (!replacement.equals(selector)) {
                protectedCommand.append(command, copiedUntil, selectorStart).append(replacement);
                copiedUntil = selectorEnd;
                changed = true;
            }
        }
        if (!changed) {
            return command;
        }
        return protectedCommand.append(command, copiedUntil, command.length()).toString();
    }

    private static String protectSelector(String selector) {
        String lower = selector.toLowerCase(Locale.ROOT);
        boolean excludesVehicles = lower.contains("tag=!mcv_plugin_vehicle");
        boolean excludesPlayers = lower.contains("type=!minecraft:player")
                || lower.contains("type=!player");
        if (excludesVehicles && excludesPlayers) {
            return selector;
        }

        StringBuilder additions = new StringBuilder();
        if (!excludesVehicles) {
            additions.append("tag=!mcv_plugin_vehicle");
        }
        if (!excludesPlayers) {
            if (!additions.isEmpty()) {
                additions.append(',');
            }
            additions.append("type=!minecraft:player");
        }
        if (selector.length() == 2) {
            return "@e[" + additions + "]";
        }
        String content = selector.substring(3, selector.length() - 1);
        return "@e[" + content + (content.isEmpty() ? "" : ",") + additions + "]";
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlaceCarriedVehicle(PlayerInteractEvent event) {
        boolean carryingWaterVehicle = vehicles.trailers().isCarryingWaterVehicle(event.getPlayer());
        if (event.getHand() == null
                || event.getHand() != EquipmentSlot.HAND && !carryingWaterVehicle
                || !acceptsCarriedVehiclePlacement(
                event.getAction(), carryingWaterVehicle, event.isCancelled())) {
            return;
        }

        Location location = carryingWaterVehicle ? waterPlacement(event.getPlayer()) : null;
        if (location == null && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null && event.getBlockFace() != null) {
            location = event.getClickedBlock().getRelative(event.getBlockFace())
                    .getLocation().add(0.5D, 0.0D, 0.5D);
            location.setYaw(event.getPlayer().getLocation().getYaw());
        }
        if (location != null && vehicles.trailers().placeCarriedVehicle(event.getPlayer(), location)) {
            event.setCancelled(true);
        }
    }

    /**
     * Empty-hand water interaction is RIGHT_CLICK_AIR because carrying is virtual.
     * Paper pre-cancels air interactions whose vanilla result is a no-op, so that
     * specific cancelled event must still reach the fluid ray trace.
     */
    static boolean acceptsCarriedVehiclePlacement(Action action, boolean carryingWaterVehicle,
                                                   boolean cancelled) {
        if (cancelled && !(carryingWaterVehicle && action == Action.RIGHT_CLICK_AIR)) {
            return false;
        }
        return action == Action.RIGHT_CLICK_BLOCK
                || carryingWaterVehicle && action == Action.RIGHT_CLICK_AIR;
    }

    private static Location waterPlacement(Player player) {
        RayTraceResult result = player.rayTraceBlocks(6.0D, FluidCollisionMode.ALWAYS);
        Block block = result == null ? null : result.getHitBlock();
        if (block == null) {
            return null;
        }
        double y = LandVehicle.restingWaterRootY(block);
        if (Double.isNaN(y)) {
            return null;
        }
        return new Location(block.getWorld(), block.getX() + 0.5D, y, block.getZ() + 0.5D,
                player.getLocation().getYaw(), 0.0F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            vehicles.trailers().releaseHeldTrailer(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline() && !ResourcePackSender.suppliedByServer(plugin)) {
                ResourcePackSender.send(plugin, event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> event.getPlayer().sendRichMessage(
                    "<green>[Vehicle] Resource pack-ul r22 a fost încărcat.</green>");
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
    public void onInventoryClose(InventoryCloseEvent event) {
        vehicles.handleInventoryClose(event.getInventory());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        vehicles.onWorldLoaded(event.getWorld());
    }
}
