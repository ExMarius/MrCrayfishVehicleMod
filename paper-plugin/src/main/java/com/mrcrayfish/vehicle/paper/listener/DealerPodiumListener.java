package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.shop.DealerPodiumItem;
import com.mrcrayfish.vehicle.paper.shop.DealerStationManager;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Podium rules: place the golden-car item on the ground (UP face only) to build a showroom;
 * sneak + punch the featured car as an admin picks the whole showroom back up.
 */
public final class DealerPodiumListener implements Listener {
    private final DealerStationManager stations;
    private final VehicleManager vehicles;
    private final java.util.Map<String, Long> lastPlacements = new java.util.HashMap<>();

    public DealerPodiumListener(DealerStationManager stations, VehicleManager vehicles) {
        this.stations = stations;
        this.vehicles = vehicles;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!DealerPodiumItem.isPodium(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("vehicle.admin")) {
            player.sendRichMessage("<red>Podiumul e doar pentru administratori.</red>");
            return;
        }
        if (event.getBlockFace() != org.bukkit.block.BlockFace.UP) {
            player.sendRichMessage("<red>Așază podiumul pe o suprafață (susul unui bloc).</red>");
            return;
        }
        Location foot = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        // Some right-click paths fire the event twice in the same tick: throttle per player+block.
        String key = player.getUniqueId() + "@" + foot.getWorld().getName() + ","
                + foot.getBlockX() + "," + foot.getBlockY() + "," + foot.getBlockZ();
        long now = System.currentTimeMillis();
        if (now - lastPlacements.getOrDefault(key, 0L) < 700L) {
            return;
        }
        lastPlacements.put(key, now);
        stations.place(player, foot);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (vehicles.byEntity(event.getEntity()).isEmpty()) {
            return;
        }
        LandVehicle vehicle = vehicles.byEntity(event.getEntity()).get();
        if (!vehicle.showcase()) {
            return;
        }
        // Showroom cars never take damage.
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player && player.isSneaking()
                && player.hasPermission("vehicle.admin")) {
            stations.tryRemove(player, vehicle);
        }
    }
}
