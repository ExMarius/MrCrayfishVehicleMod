package com.mrcrayfish.vehicle.paper.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The placeable FiveM showroom podium: a tiny golden car you set down to build the spinner. */
public final class DealerPodiumItem {
    private static final NamespacedKey PODIUM_KEY = new NamespacedKey("vehicle", "dealer_podium");

    private DealerPodiumItem() {
    }

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", "vehicle_icon/sports_car"));
        meta.getPersistentDataContainer().set(PODIUM_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Podium SHOWROOM (dealer)",
                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Plasează podiumul (click dreapta pe o suprafață).",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Mașina se învârte pe podium, prețul plutește, click pe ea = vine să cumperi.",
                        NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPodium(ItemStack stack) {
        return stack != null && stack.getType() == Material.PAPER && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(PODIUM_KEY,
                        PersistentDataType.BYTE);
    }
}
