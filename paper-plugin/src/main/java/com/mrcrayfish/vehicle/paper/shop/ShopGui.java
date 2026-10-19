package com.mrcrayfish.vehicle.paper.shop;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The dealer catalog: every purchasable vehicle as its 3D body model, sorted cheap->expensive,
 * price in the lore, click = buy (FiveM dealership menu feel). Vehicle identity travels in PDC.
 */
public final class ShopGui {
    /** Marker holder so the click listener recognises OUR window (and ours only). */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final NamespacedKey SPEC_KEY = new NamespacedKey("vehicle", "shop_spec");

    private final VehicleShop shop;

    public ShopGui(VehicleShop shop) {
        this.shop = shop;
    }

    public static String specOf(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(SPEC_KEY, PersistentDataType.STRING);
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(
                "\uD83D\uDCB0 DEALER AUTO - alegi, cumperi, conduci", NamedTextColor.GOLD));
        holder.inventory = inventory;
        List<String> ids = new ArrayList<>(LandVehicleSpec.ids());
        ids.sort(Comparator.comparingDouble(shop::priceOf));
        int slot = 0;
        for (String id : ids) {
            if (slot >= inventory.getSize()) {
                break;
            }
            LandVehicleSpec spec = LandVehicleSpec.byId(id);
            if (spec == null) {
                continue;
            }
            inventory.setItem(slot++, entry(spec, shop.priceOf(id)));
        }
        player.openInventory(inventory);
    }

    private static ItemStack entry(LandVehicleSpec spec, double price) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("vehicle", spec.bodyModel()));
            meta.displayName(Component.text(spec.displayName(), NamedTextColor.WHITE,
                    TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Pret: " + VehicleShop.priceText(price),
                    NamedTextColor.GOLD));
            lore.add(Component.text("Click = CUMPERI (inapoi nu se dau banii!)",
                    NamedTextColor.GREEN));
            lore.add(Component.text("Masina cumparata are STAPAN = TU.", NamedTextColor.GRAY));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(SPEC_KEY, PersistentDataType.STRING, spec.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
