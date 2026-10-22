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
    private com.mrcrayfish.vehicle.paper.shop.ShopGui gui;

    public ShopListener(VehicleShop shop) {
        this.shop = shop;
    }

    /** GUI-ul e legat de listener din VehiclePlugin (openParts/open pentru tab-uri). */
    public void bindGui(com.mrcrayfish.vehicle.paper.shop.ShopGui gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopGui.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        // comutare tab-uri: VEHICULE <-> PIESE
        String page = ShopGui.pageOf(event.getCurrentItem());
        if (page != null) {
            if ("parts".equals(page)) {
                gui.openParts(player);
            } else {
                gui.open(player);
            }
            return;
        }
        String partId = ShopGui.partOf(event.getCurrentItem());
        if (partId != null) {
            player.closeInventory();
            shop.buyPart(player, partId);
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
