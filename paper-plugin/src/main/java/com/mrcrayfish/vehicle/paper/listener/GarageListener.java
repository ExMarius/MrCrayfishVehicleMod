package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles clicks inside the garage window only (recognised by the marker holder):
 * everything is cancelled - clicking an entry performs the action and refreshes the GUI.
 */
public final class GarageListener implements Listener {
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    public GarageListener(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    private static boolean isGarage(org.bukkit.inventory.Inventory inventory) {
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        return holder != null && holder.getClass().getEnclosingClass() == com.mrcrayfish.vehicle.paper.garage.GarageGui.class;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGarageClick(InventoryClickEvent event) {
        if (!isGarage(event.getInventory())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        UUID vehicleId = com.mrcrayfish.vehicle.paper.garage.GarageGui.vehicleOf(clicked);
        String action = com.mrcrayfish.vehicle.paper.garage.GarageGui.actionOf(clicked);
        if (vehicleId == null || action == null) {
            return;
        }

        if (com.mrcrayfish.vehicle.paper.garage.GarageGui.ACTION_PARK.equals(action)) {
            LandVehicle vehicle = vehicles.byId(vehicleId).orElse(null);
            if (vehicle == null) {
                player.sendRichMessage("<red>Vehiculul nu mai este în lume.</red>");
            } else {
                switch (vehicles.park(vehicle, player)) {
                    case PARKED -> player.sendRichMessage("<green>" + vehicle.spec().displayName()
                            + " parcat în garaj.</green>");
                    case CLAIMED_AND_PARKED -> player.sendRichMessage("<gold>" + vehicle.spec().displayName()
                            + " este acum AL TĂU și parcat în garaj.</gold>");
                    case OCCUPIED -> player.sendRichMessage("<red>Mașina e ocupată - nu o poți parca acum.</red>");
                    case NOT_OWNER -> player.sendRichMessage("<red>Nu ești proprietarul acestei mașini.</red>");
                }
            }
        } else if (com.mrcrayfish.vehicle.paper.garage.GarageGui.ACTION_RETRIEVE.equals(action)) {
            if (!vehicles.retrieve(player, vehicleId)) {
                player.sendRichMessage("<red>Nu am putut scoate vehiculul din garaj.</red>");
            } else {
                player.sendRichMessage("<green>Vehicul scos din garaj - e lângă tine.</green>");
            }
        }

        // Refresh the window so the lists mirror the new state immediately.
        new com.mrcrayfish.vehicle.paper.garage.GarageGui(vehicles).open(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGarageDrag(InventoryDragEvent event) {
        if (isGarage(event.getInventory())) {
            event.setCancelled(true);
        }
    }
}
