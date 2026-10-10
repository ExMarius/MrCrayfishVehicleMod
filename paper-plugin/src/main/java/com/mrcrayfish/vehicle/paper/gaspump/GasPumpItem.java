package com.mrcrayfish.vehicle.paper.gaspump;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** The placeable "Gas Pump" item: a plain vanilla item wearing the mod's own gas pump item model. */
public final class GasPumpItem {
    private static final NamespacedKey MODEL = new NamespacedKey("vehicle", "gas_pump");

    private GasPumpItem() {
    }

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(MODEL);
        meta.displayName(Component.text("Gas Pump", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Plasează cu click dreapta pe un bloc.",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isGasPump(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return MODEL.equals(meta.getItemModel());
    }
}
