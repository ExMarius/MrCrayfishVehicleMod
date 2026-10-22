package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bancul de mecanic (deschis cu cheia de mecanic pe o masina RIDICATA pe cric):
 * slot motor (tier actual + schimb din piata), slot roti, buton REPARA (consuma
 * itemele din config - fara bani, pretul in bani se negociaza RP), buton VOPSESTE
 * (paleta 16 culori - sistemul nostru de vopsea: GRATUITA, fara consum; o deschide
 *  si comanda /vehicle paint langa o masina proprie/trusted).
 */
public final class MechanicGui {
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final UUID vehicleId;
        private final boolean paintPage;

        public Holder(UUID vehicleId, boolean paintPage) {
            this.vehicleId = vehicleId;
            this.paintPage = paintPage;
        }

        public UUID vehicleId() {
            return vehicleId;
        }

        public boolean paintPage() {
            return paintPage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final NamespacedKey ACTION = new NamespacedKey("vehicle", "mechanic_gui_action");
    public static final int ENGINE_SLOT = 11;
    public static final int WHEEL_SLOT = 13;
    public static final int REPAIR_SLOT = 15;
    public static final int PAINT_SLOT = 17;

    private final MechanicManager mechanic;

    public MechanicGui(MechanicManager mechanic) {
        this.mechanic = mechanic;
    }

    public static String actionOf(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(ACTION, PersistentDataType.STRING);
    }

    public void open(Player player, LandVehicle vehicle) {
        Holder holder = new Holder(vehicle.id(), false);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(
                "🔧 SERVICE - " + vehicle.spec().displayName(), NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;

        inventory.setItem(4, info(vehicle));
        if (vehicle.spec().defaultEngineType() == EngineType.NONE) {
            inventory.setItem(ENGINE_SLOT, tag(make(Material.BARRIER,
                    Component.text("FARA MOTOR", NamedTextColor.RED),
                    List.of(Component.text("Acest vehicul nu primeste upgrade de motor.",
                            NamedTextColor.GRAY))), "none"));
        } else {
            inventory.setItem(ENGINE_SLOT, tag(entry(
                    MechanicItems.engine(vehicle.spec().defaultEngineType(), vehicle.engineTier()),
                    Component.text("MOTOR MONTAT", NamedTextColor.AQUA),
                    List.of(Component.text("Tine motorul NOU in mana si click aici = schimb.",
                            NamedTextColor.GRAY))), "engine_slot"));
        }
        inventory.setItem(WHEEL_SLOT, tag(entry(
                MechanicItems.wheel(vehicle.wheelType()),
                Component.text("ROTI MONTATE", NamedTextColor.AQUA),
                List.of(Component.text("Tine rotile NOI in mana si click aici = schimb.",
                        NamedTextColor.GRAY))), "wheel_slot"));

        List<Component> repairLore = new ArrayList<>();
        repairLore.add(Component.text(String.format(Locale.ROOT, "Stare: %.0f/100", vehicle.health()),
                vehicle.health() < 30.0D ? NamedTextColor.RED : NamedTextColor.GREEN));
        if (!mechanic.damageEnabled()) {
            repairLore.add(Component.text("Masinile nu se uzeaza momentan - nu ai ce repara.",
                    NamedTextColor.DARK_GRAY));
        }
        mechanic.repairItems().forEach(entry -> repairLore.add(Component.text(
                "Consuma: " + entry.getValue() + "x " + entry.getKey().name().toLowerCase(Locale.ROOT),
                NamedTextColor.YELLOW)));
        repairLore.add(Component.text("Fara bani - pretul il negociezi cu mecanicul (RP).",
                NamedTextColor.DARK_GRAY));
        inventory.setItem(REPAIR_SLOT, tag(make(Material.ANVIL,
                Component.text("REPARA COMPLET", NamedTextColor.GREEN, TextDecoration.BOLD),
                repairLore), "repair"));

        inventory.setItem(PAINT_SLOT, tag(entry(
                MechanicItems.sprayCan(0xB02E26, "rosie"),
                Component.text("VOPSESTE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                List.of(Component.text("Deschide paleta - vopsire GRATUITA, nu consuma nimic.",
                        NamedTextColor.GRAY))), "paint"));
        player.openInventory(inventory);
    }

    public void openPaintPage(Player player, LandVehicle vehicle) {
        Holder holder = new Holder(vehicle.id(), true);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(
                "🎨 VOPSEA - " + vehicle.spec().displayName(), NamedTextColor.DARK_PURPLE));
        holder.inventory = inventory;
        int slot = 3;
        for (PaintColors color : PaintColors.values()) {
            inventory.setItem(slot++, tag(entry(
                    MechanicItems.sprayCan(color.rgb(), color.label()),
                    Component.text("Vopsea " + color.label(), NamedTextColor.WHITE),
                    List.of(Component.text("Click = vopsesti instant. GRATUIT - nu consuma nimic.",
                            NamedTextColor.GRAY))), "color:" + color.name()));
            if (slot == 12 || slot == 21) {
                slot += 2;
            }
        }
        inventory.setItem(22, tag(make(Material.ARROW,
                Component.text("« Inapoi la banc", NamedTextColor.GRAY), List.of()), "back"));
        player.openInventory(inventory);
    }

    private static ItemStack info(LandVehicle vehicle) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", "vehicle_icon/" + vehicle.spec().id()));
        meta.displayName(Component.text(vehicle.spec().displayName(), NamedTextColor.WHITE,
                TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format(Locale.ROOT, "Sanatate: %.0f/100", vehicle.health()),
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Motor: " + vehicle.engineTier().label() + " "
                + vehicle.spec().defaultEngineType().label().toLowerCase(Locale.ROOT),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Roti: " + vehicle.wheelType().label(),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Vopsea: " + (vehicle.painted()
                        ? String.format(Locale.ROOT, "#%06X", vehicle.paintColor()) : "nevopsita"),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(vehicle.locked() ? "INCHISA cu cheia" : "deschisa",
                vehicle.locked() ? NamedTextColor.RED : NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack make(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> plain = new ArrayList<>(lore);
        plain.replaceAll(component -> component.decoration(TextDecoration.ITALIC, false));
        meta.lore(plain);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Ia un stack fabricat de MechanicItems si ii suprascrie numele/lore-ul pentru GUI. */
    private static ItemStack entry(ItemStack source, Component name, List<Component> lore) {
        ItemMeta meta = source.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> plain = new ArrayList<>(lore);
        plain.replaceAll(component -> component.decoration(TextDecoration.ITALIC, false));
        meta.lore(plain);
        source.setItemMeta(meta);
        return source;
    }

    private static ItemStack tag(ItemStack stack, String action) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }
}
