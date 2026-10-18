package com.mrcrayfish.vehicle.paper.gaspump;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The gas pump nozzle as a REAL hand item: the player receives it while holding the
 * nozzle, so Minecraft renders it natively in first and third person. PDC-tagged with
 * the owning pump id so it can never be duped, dropped or lost.
 */
public final class NozzleItem {
    public static final NamespacedKey MODEL = new NamespacedKey("vehicle", "nozzle");
    public static final NamespacedKey PUMP_KEY = new NamespacedKey("vehicle", "nozzle_pump");

    private NozzleItem() {
    }

    public static ItemStack create(String pumpId) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(MODEL);
        meta.displayName(Component.text("Duza pompei de benzina", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Click dreapta pe pompa pentru a o pune la loc.",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(PUMP_KEY, PersistentDataType.STRING, pumpId);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isNozzle(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(PUMP_KEY, PersistentDataType.STRING);
    }
}
