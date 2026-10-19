package com.mrcrayfish.vehicle.paper.garage;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The FiveM-style garage screen: one chest GUI, top section = your vehicles parked HERE in
 * the world (click = store), bottom section = vehicles already stored (click = pull out).
 * Vehicle identity travels in the item's PDC, so clicks are precise and anti-dupe.
 */
public final class GarageGui {
    private static final DecimalFormat PERCENT = new DecimalFormat("0.##%");

    /** Marker holder so the click listener recognises OUR window (and ours only). */
    static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final NamespacedKey VEHICLE_KEY = new NamespacedKey("vehicle", "garage_vehicle");
    private static final NamespacedKey ACTION_KEY = new NamespacedKey("vehicle", "garage_action");

    public static final String ACTION_PARK = "park";
    public static final String ACTION_RETRIEVE = "retrieve";

    private final VehicleManager vehicles;

    public GarageGui(VehicleManager vehicles) {
        this.vehicles = vehicles;
    }

    public static UUID vehicleOf(ItemStack stack) {
        ItemMeta meta = meta(stack);
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(VEHICLE_KEY, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String actionOf(ItemStack stack) {
        ItemMeta meta = meta(stack);
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
    }

    private static ItemMeta meta(ItemStack stack) {
        return stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta();
    }

    public void open(Player player) {
        List<LandVehicle> parkable = vehicles.parkableNearby(player, 8.0D);
        List<VehicleManager.GarageEntry> garaged = vehicles.garagedOf(player);

        int entries = Math.max(parkable.size() + garaged.size(), 1);
        int size = Math.min(54, Math.max(9, ((entries + 8) / 9) * 9));
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, size,
                Component.text("Garaj — click pentru parcare / scoatere"));
        holder.inventory = inventory;

        int slot = 0;
        for (LandVehicle vehicle : parkable) {
            LandVehicleSpec spec = vehicle.spec();
            boolean claimed = vehicle.owner().isPresent();
            inventory.setItem(slot++, item(spec, vehicle.fuel() / spec.energyCapacity(),
                    vehicle.id(), ACTION_PARK,
                    Component.text("▲ Parchează: " + spec.displayName(), NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false),
                    claimed
                            ? Component.text("Mașina ta — intră în garaj.", NamedTextColor.GRAY)
                            : Component.text("Neașteptată — parcarea o REVENDICĂ ca a ta.", NamedTextColor.YELLOW)));
        }
        for (VehicleManager.GarageEntry entry : garaged) {
            LandVehicleSpec spec = LandVehicleSpec.byId(entry.type());
            if (spec == null) {
                continue;
            }
            inventory.setItem(slot++, item(spec, entry.fuel() / spec.energyCapacity(),
                    entry.id(), ACTION_RETRIEVE,
                    Component.text("▼ Scoate: " + spec.displayName(), NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Apare lângă tine, cu benzina păstrată.", NamedTextColor.GRAY)));
        }

        if (parkable.isEmpty() && garaged.isEmpty()) {
            player.sendRichMessage("<yellow>Nimic prin garaj: niciun vehicul al tău la <8 blocuri, nimic parcat. "
                    + "Apropie o mașină și deschide /vehicle garage din nou.</yellow>");
            return;
        }
        player.openInventory(inventory);
    }

    private ItemStack item(LandVehicleSpec spec, float fuelFraction, UUID id, String action,
                           Component name, Component lore) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        // GUI icon: the dedicated scale-to-slot model (visuals backed by vehicle_icon/<id>).
            meta.setItemModel(new NamespacedKey("vehicle", "vehicle_icon/" + spec.id()));
        meta.displayName(name);
        List<Component> lines = new ArrayList<>();
        lines.add(lore.decoration(TextDecoration.ITALIC, false));
        lines.add(Component.text("Rezervor: " + PERCENT.format(Math.max(0.0F, Math.min(1.0F, fuelFraction))),
                NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lines);
        meta.getPersistentDataContainer().set(VEHICLE_KEY, PersistentDataType.STRING, id.toString());
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }
}
