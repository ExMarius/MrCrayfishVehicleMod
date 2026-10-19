package com.mrcrayfish.vehicle.paper.garage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The physical GTA-style garage station: a placeable workbench terminal. Anyone walks up to
 * it and opens the garage window; driving a vehicle into its yard auto-stores it for the driver.
 */
public final class GarageStationItem {
    private static final NamespacedKey STATION_KEY = new NamespacedKey("vehicle", "garage_station");

    private GarageStationItem() {
    }

    public static final byte FLAG_PRIVATE = 2;

    public static ItemStack create() {
        return create(false);
    }

    /** publicFlag = GTA-style public station (admin); false = private player garage. */
    public static ItemStack create(boolean publicStation) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", "garage_terminal"));
        meta.getPersistentDataContainer().set(STATION_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(new NamespacedKey("vehicle", "garage_public"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) (publicStation ? 1 : 0));
        meta.displayName(Component.text(publicStation ? "Garaj PUBLIC (GTA)" : "Garaj PERSONAL",
                publicStation ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Plasează stația de garaj (click dreapta pe o suprafață).",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Condu în curtea ei = parchează singură • click dreapta pe stație = meniul tău.",
                        NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPublic(ItemStack stack) {
        if (!isStation(stack)) {
            return true;
        }
        Byte value = stack.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey("vehicle", "garage_public"),
                org.bukkit.persistence.PersistentDataType.BYTE);
        return value == null || value == 1;
    }

    public static boolean isStation(ItemStack stack) {
        return stack != null && stack.getType() == Material.PAPER && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(STATION_KEY,
                        org.bukkit.persistence.PersistentDataType.BYTE);
    }
}
