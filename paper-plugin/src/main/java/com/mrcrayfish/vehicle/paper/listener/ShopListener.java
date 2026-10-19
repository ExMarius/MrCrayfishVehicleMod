package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.shop.ShopGui;
import com.mrcrayfish.vehicle.paper.shop.VehicleShop;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Clicks inside the dealer catalog: always cancelled (anti-dupe), a click on a vehicle entry
 * closes the window and runs the purchase flow.
 */
public final class ShopListener implements Listener {
    private final VehicleShop shop;

    public ShopListener(VehicleShop shop) {
        this.shop = shop;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopGui.Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String specId = ShopGui.specOf(event.getCurrentItem());
        if (specId == null) {
            return;
        }
        player.closeInventory();
        shop.buy(player, specId);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopGui.Holder) {
            event.setCancelled(true);
        }
    }
}
