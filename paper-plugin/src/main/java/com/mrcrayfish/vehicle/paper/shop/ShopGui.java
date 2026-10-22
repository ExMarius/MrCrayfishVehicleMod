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
        private boolean partsPage;

        public boolean partsPage() {
            return partsPage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final NamespacedKey SPEC_KEY = new NamespacedKey("vehicle", "shop_spec");
    private static final NamespacedKey PART_KEY = new NamespacedKey("vehicle", "shop_part");
    private static final NamespacedKey PAGE_KEY = new NamespacedKey("vehicle", "shop_page");

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
        inventory.setItem(53, pageTab("parts"));
        player.openInventory(inventory);
    }

    /** Tab-ul PIESE: motoare (12), roti (3), spray cans (albe la 0 culori de baza + paleta). */
    public void openParts(Player player) {
        Holder holder = new Holder();
        holder.partsPage = true;
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(
                "\uD83D\uDD27 MAGAZIN PIESE - motori, roti, vopsea", NamedTextColor.AQUA));
        holder.inventory = inventory;
        int slot = 0;
        for (com.mrcrayfish.vehicle.paper.mechanic.EngineType type
                : new com.mrcrayfish.vehicle.paper.mechanic.EngineType[]{
                com.mrcrayfish.vehicle.paper.mechanic.EngineType.SMALL_MOTOR,
                com.mrcrayfish.vehicle.paper.mechanic.EngineType.LARGE_MOTOR,
                com.mrcrayfish.vehicle.paper.mechanic.EngineType.ELECTRIC_MOTOR}) {
            for (com.mrcrayfish.vehicle.paper.mechanic.EngineTier tier
                    : com.mrcrayfish.vehicle.paper.mechanic.EngineTier.values()) {
                String key = "engine_" + tier.name().toLowerCase(java.util.Locale.ROOT) + "_" + type.folder();
                inventory.setItem(slot++, partEntry(
                        com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.engine(type, tier),
                        key, shop.partPrice(key)));
            }
            slot += 5; // rand pe familie
        }
        slot = 28;
        for (com.mrcrayfish.vehicle.paper.mechanic.WheelType type
                : com.mrcrayfish.vehicle.paper.mechanic.WheelType.values()) {
            String key = "wheel_" + type.name().toLowerCase(java.util.Locale.ROOT);
            inventory.setItem(slot, partEntry(
                    com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.wheel(type),
                    key, shop.partPrice(key)));
            slot += 2;
        }
        int canSlot = 37;
        for (com.mrcrayfish.vehicle.paper.mechanic.PaintColors color
                : com.mrcrayfish.vehicle.paper.mechanic.PaintColors.values()) {
            inventory.setItem(canSlot, partEntry(
                    com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.sprayCan(
                            color.rgb(), color.label()),
                    "spray_can:" + color.name(), shop.partPrice("spray_can")));
            canSlot++;
            if (canSlot == 43) {
                canSlot = 46;
            }
        }
        inventory.setItem(45, pageTab("vehicles"));
        player.openInventory(inventory);
    }

    private static ItemStack partEntry(ItemStack source, String partId, double price) {
        ItemMeta meta = source.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.text("Pret: " + VehicleShop.priceText(price), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click = CUMPERI", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(PART_KEY, PersistentDataType.STRING, partId);
        source.setItemMeta(meta);
        return source;
    }

    private static ItemStack pageTab(String targetPage) {
        ItemStack stack = new ItemStack(Material.CHEST);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(
                "parts".equals(targetPage) ? "➜ PIESE (motor/roti/vopsea)" : "« VEHICULE",
                NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.STRING, targetPage);
        stack.setItemMeta(meta);
        return stack;
    }

    public static String partOf(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(PART_KEY, PersistentDataType.STRING);
    }

    public static String pageOf(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(PAGE_KEY, PersistentDataType.STRING);
    }

    /** Reconstruieste stack-ul unui partid din id-ul din PDC (la cumparare). */
    public static ItemStack partStackOf(String partId) {
        if (partId == null) {
            return null;
        }
        if (partId.startsWith("engine_")) {
            String[] segments = partId.substring(7).split("_");
            if (segments.length == 2) {
                return com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.engine(
                        folderType(segments[1]),
                        com.mrcrayfish.vehicle.paper.mechanic.EngineTier.fromName(segments[0]));
            }
            return null;
        }
        if (partId.startsWith("wheel_")) {
            return com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.wheel(
                    com.mrcrayfish.vehicle.paper.mechanic.WheelType.fromName(partId.substring(6)));
        }
        if (partId.startsWith("spray_can:")) {
            String colorName = partId.substring(10);
            com.mrcrayfish.vehicle.paper.mechanic.PaintColors color =
                    com.mrcrayfish.vehicle.paper.mechanic.PaintColors.valueOf(colorName);
            // la cumparare pretul e cel al spray_can-ului generic
            return com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.sprayCan(
                    color.rgb(), color.label());
        }
        return null;
    }

    private static com.mrcrayfish.vehicle.paper.mechanic.EngineType folderType(String folder) {
        return switch (folder) {
            case "small" -> com.mrcrayfish.vehicle.paper.mechanic.EngineType.SMALL_MOTOR;
            case "large" -> com.mrcrayfish.vehicle.paper.mechanic.EngineType.LARGE_MOTOR;
            case "electric" -> com.mrcrayfish.vehicle.paper.mechanic.EngineType.ELECTRIC_MOTOR;
            default -> com.mrcrayfish.vehicle.paper.mechanic.EngineType.NONE;
        };
    }

    private static ItemStack entry(LandVehicleSpec spec, double price) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // GUI icon: the dedicated scale-to-slot model (visuals backed by vehicle_icon/<id>).
            meta.setItemModel(new NamespacedKey("vehicle", "vehicle_icon/" + spec.id()));
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
