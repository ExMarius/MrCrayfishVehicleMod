package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.garage.GarageGui;
import com.mrcrayfish.vehicle.paper.garage.GarageStationItem;
import com.mrcrayfish.vehicle.paper.garage.GarageStationManager;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Physical station interactions (GTA 1:1):
 * <ul>
 *   <li>RIGHT CLICK a surface with the station item = place the terminal there.</li>
 *   <li>RIGHT CLICK the terminal = open YOUR garage menu; pulls spawn AT THIS station
 *       (anchor remembered per open window, like walking out of a FiveM garage).</li>
 *   <li>PUNCH the terminal with any hand = pick it up (stored vehicles are never lost).</li>
 * </ul>
 */
public final class GarageStationListener implements Listener {
    /** Per-player retrieval anchor while their garage window is open (session-scoped). */
    private static final Map<UUID, Location> WINDOW_ANCHOR = new HashMap<>();

    private final GarageStationManager stations;
    private final VehicleManager vehicles;

    public GarageStationListener(GarageStationManager stations, VehicleManager vehicles) {
        this.stations = stations;
        this.vehicles = vehicles;
    }

    /** Vanilla-visible anchor used when pulling a car out of THIS station (null = at player). */
    public static Location anchorOf(Player player) {
        return WINDOW_ANCHOR.get(player.getUniqueId());
    }

    /** Clears the retrieval anchor when the window closes (session hygiene). */
    public static void clearAnchor(Player player) {
        WINDOW_ANCHOR.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!GarageStationItem.isStation(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Location foot = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        if (event.getBlockFace() != org.bukkit.block.BlockFace.UP) {
            player.sendRichMessage("<red>Plasează stația pe o suprafață (susul unui bloc).</red>");
            return;
        }
        stations.place(player, foot);
        player.sendRichMessage("<green>Garaj STAȚIE plasat!</green> "
                + "<gray>Condu o mașină în curtea (~5 blocuri) și se parchează singură. "
                + "Click dreapta pe stație = meniul tău de garaj.</gray>");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        UUID stationId = stations.stationId(event.getRightClicked()).orElse(null);
        if (stationId == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        stations.location(stationId).ifPresent(loc -> WINDOW_ANCHOR.put(player.getUniqueId(), loc));
        new GarageGui(vehicles).open(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBreak(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!stations.remove(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        player.sendRichMessage("<yellow>Stația de garaj ridicată înapoi.</yellow> "
                + "<gray>(Vehiculele parcate rămân în siguranță - nu se pierd!)</gray>");
    }
}
