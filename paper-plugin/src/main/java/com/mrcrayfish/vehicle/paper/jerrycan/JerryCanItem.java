package com.mrcrayfish.vehicle.paper.jerrycan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The jerry can: stores fuel in its own PDC, fills for free at a gas pump (click) and pours
 * into vehicles at a fixed rate per click - a faithful port of the original mod's JerryCanItem
 * (fluid capability -> PDC float, durability bar -> live lore, same fill-rate behaviour).
 */
public final class JerryCanItem {
    private static final DecimalFormat FUEL_FORMAT = new DecimalFormat("#,###");

    public enum Type {
        NORMAL("jerry_can", "Canistră de benzină", 3_000.0F),
        INDUSTRIAL("industrial_jerry_can", "Canistră industrială", 9_000.0F);

        public final String model;
        public final String label;
        public final float capacity;

        Type(String model, String label, float capacity) {
            this.model = model;
            this.label = label;
            this.capacity = capacity;
        }
    }

    private static final NamespacedKey TYPE_KEY = new NamespacedKey("vehicle", "jerry_can_type");
    private static final NamespacedKey FUEL_KEY = new NamespacedKey("vehicle", "jerry_can_fuel");

    /** Fuel poured per right-click, mirroring the mod's configurable jerryCanFillRate. */
    public static final float POUR_PER_CLICK = 500.0F;

    private JerryCanItem() {
    }

    public static ItemStack create(Type type) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", type.model));
        meta.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        // Name/lore refresh must run AFTER the meta landed on the stack (typeOf reads it).
        setFuel(item, 0.0F);
        return item;
    }

    public static boolean isJerryCan(ItemStack stack) {
        return stack != null && stack.getType() == Material.PAPER && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(TYPE_KEY, PersistentDataType.STRING);
    }

    public static Type typeOf(ItemStack stack) {
        if (!isJerryCan(stack)) {
            return null;
        }
        String name = stack.getItemMeta().getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
        try {
            return Type.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    public static float getFuel(ItemStack stack) {
        if (!isJerryCan(stack)) {
            return 0.0F;
        }
        Double value = stack.getItemMeta().getPersistentDataContainer().get(FUEL_KEY, PersistentDataType.DOUBLE);
        return value == null ? 0.0F : value.floatValue();
    }

    /** Writes the fuel amount and refreshes the visible name/lore on the live stack. */
    public static void setFuel(ItemStack stack, float fuel) {
        ItemMeta meta = stack.getItemMeta();
        setFuel(meta, stack, fuel);
        stack.setItemMeta(meta);
    }

    private static void setFuel(ItemMeta meta, ItemStack stack, float fuel) {
        Type type = typeOf(stack);
        if (type == null) {
            return;
        }
        float clamped = Math.max(0.0F, Math.min(type.capacity, fuel));
        meta.getPersistentDataContainer().set(FUEL_KEY, PersistentDataType.DOUBLE, (double) clamped);
        meta.displayName(Component.text(type.label, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format(Locale.ROOT, "  %.0f%%", 100.0F * clamped / type.capacity),
                        NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        List<Component> lore = new ArrayList<>();
        if (clamped <= 0.0F) {
            lore.add(Component.text("Goală", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(FUEL_FORMAT.format(clamped) + " / " + FUEL_FORMAT.format(type.capacity),
                    NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Click dreapta pe pompă = umple canistra.",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click dreapta pe vehicul = toarnă în rezervor.",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
    }
}
