package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.gaspump.GasPumpItem;
import com.mrcrayfish.vehicle.paper.jerrycan.JerryCanItem;
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

/**
 * /vehicle creative (permisiune mcv.creative, default op): meniul complet al
 * sistemului - motoare (12) / roti (3) / unelte / statii + props. Click pe item =
 * il primesti in inventarul propriu; meniul e read-only in alt fel.
 */
public final class CreativeGui {
    public enum Category {
        ENGINES("Motoare", Material.FURNACE),
        WHEELS("Roti", Material.MINECART),
        TOOLS("Unelte", Material.IRON_AXE),
        STATIONS("Statii & props", Material.CRAFTING_TABLE);

        final String label;
        final Material icon;

        Category(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private Category category = Category.ENGINES;

        public Category category() {
            return category;
        }

        public void setCategory(Category category) {
            this.category = category;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final NamespacedKey TAB = new NamespacedKey("vehicle", "creative_tab");
    private static final NamespacedKey GIVE = new NamespacedKey("vehicle", "creative_give");

    private CreativeGui() {
    }

    public static String tabOf(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(TAB, PersistentDataType.STRING);
    }

    public static boolean isGiveItem(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta()
                .getPersistentDataContainer().has(GIVE, PersistentDataType.BOOLEAN);
    }

    public static void open(Player player) {
        Holder holder = new Holder();
        redraw(player, holder);
    }

    public static void redraw(Player player, Holder holder) {
        Category category = holder.category();
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(
                "⭐ CREATIVE - " + category.label, NamedTextColor.LIGHT_PURPLE));
        holder.inventory = inventory;
        switch (category) {
            case ENGINES -> {
                int slot = 0;
                for (EngineType type : new EngineType[]{EngineType.SMALL_MOTOR,
                        EngineType.LARGE_MOTOR, EngineType.ELECTRIC_MOTOR}) {
                    for (EngineTier tier : EngineTier.values()) {
                        inventory.setItem(slot++, give(MechanicItems.engine(type, tier)));
                    }
                    slot += 6; // coloana urmatoare
                }
            }
            case WHEELS -> {
                int slot = 11;
                for (WheelType type : WheelType.values()) {
                    inventory.setItem(slot += 2, give(MechanicItems.wheel(type)));
                }
            }
            case TOOLS -> {
                inventory.setItem(10, give(MechanicItems.wrench()));
                inventory.setItem(11, give(MechanicItems.hammer()));
                inventory.setItem(12, give(MechanicItems.blankKey()));
                inventory.setItem(13, give(JerryCanItem.create(JerryCanItem.Type.NORMAL)));
                inventory.setItem(14, give(MechanicItems.industrialJerryCan()));
                inventory.setItem(15, give(MechanicItems.panel()));
                inventory.setItem(19, give(MechanicItems.fluidBucket(MechanicItems.Fluid.FUELIUM)));
                inventory.setItem(20, give(MechanicItems.fluidBucket(MechanicItems.Fluid.ENDER_SAP)));
                inventory.setItem(21, give(MechanicItems.fluidBucket(MechanicItems.Fluid.BLAZE_JUICE)));
                int slot = 28;
                for (PaintColors color : PaintColors.values()) {
                    inventory.setItem(slot, give(MechanicItems.sprayCan(color.rgb(), color.label())));
                    slot++;
                    if (slot == 34) {
                        slot = 37;
                    }
                }
            }
            case STATIONS -> {
                inventory.setItem(11, give(GasPumpItem.create()));
                inventory.setItem(13, give(MechanicItems.jack()));
                inventory.setItem(15, give(MechanicItems.workstation()));
                inventory.setItem(29, give(MechanicItems.fluidMixer()));
                inventory.setItem(31, give(MechanicItems.trafficCone()));
                inventory.setItem(33, give(MechanicItems.boostPad()));
                inventory.setItem(35, give(MechanicItems.fluidPipe()));
            }
        }
        int rowSlot = 45;
        for (Category tab : Category.values()) {
            inventory.setItem(rowSlot += 2, tab(tab, tab == category));
        }
        player.openInventory(inventory);
    }

    private static ItemStack give(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(GIVE, PersistentDataType.BOOLEAN, true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack tab(Category category, boolean active) {
        ItemStack stack = new ItemStack(category.icon);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(category.label,
                        active ? NamedTextColor.GREEN : NamedTextColor.GRAY, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(TAB, PersistentDataType.STRING, category.name());
        stack.setItemMeta(meta);
        return stack;
    }
}
