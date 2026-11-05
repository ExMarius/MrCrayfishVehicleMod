package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Meniul UNIC al mecanicii (refacere 2026-10-24, inlocuieste complet GUI-ul vechi
 * de tip EditVehicleScreen, sters la cererea userului):
 *  - deschis cu click-dreapta pe bancul de lucru SAU cu cheia de mecanic pe o
 *    masina legata de un cric de langa un banc;
 *  - sus: butonul RIDICA / COBOARA masina de pe cricul legat (cu starea live);
 *  - mijloc: rack-ul uneltelor (EXACT O cheie + UN ciocan - binding, returnare
 *    automata; ocupata = bariera rosie cu nume si niciun click);
 *  - jos (doar cand masina e RIDICATA pe cric): sloturile MOTOR si ROTI + cartea
 *    cu datele masinii. Piesele se monteaza/demonteaza ca in fluxul din mod:
 *    piesa pe cursor + click = instaleaza/schimba; cursor gol + click = scoate;
 *  - spray-ul NU exista ca unealta; vopsirea = capsula de spray DIRECT din mana,
 *    si numai pe masina ridicata.
 */
public final class MechanicGui {
    /** Holderul meniului: identifica BANCUL (nu o masina!). */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final UUID stationId;

        public Holder(UUID stationId) {
            this.stationId = stationId;
        }

        public UUID stationId() {
            return stationId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final NamespacedKey ACTION = new NamespacedKey("vehicle", "mechanic_gui_action");

    private static final int LIFT_SLOT = 4;      // sus central: ridica/coboara cricul legat
    private static final int WRENCH_SLOT = 12;   // cheia de mecanic (o singura instanta)
    private static final int HAMMER_SLOT = 14;   // ciocanul de lacatus (o singura instanta)
    private static final int VEHICLE_SLOT = 22;  // jos centru: datele masinii / cartea de ajutor

    private final MechanicManager mechanic;

    public MechanicGui(MechanicManager mechanic) {
        this.mechanic = mechanic;
    }

    public static String actionOf(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(ACTION, PersistentDataType.STRING);
    }

    public void openToolsMenu(Player player, UUID stationId) {
        Holder holder = new Holder(stationId);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Banc de lucru", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;
        mechanic.workstations().stationOf(stationId).ifPresentOrElse(station -> {
            liftButton(inventory, stationId);
            wrenchEntry(inventory, station, player);
            toolEntry(inventory, HAMMER_SLOT, "hammer",
                    MechanicItems.hammer(null, mechanic.workstations().hammerUsedOf(station)),
                    mechanic.workstations().hammerHolderOf(station), player);
            LandVehicle lifted = liftedVehicle(stationId).orElse(null);
            inventory.setItem(VEHICLE_SLOT, make(Material.BOOK,
                    Component.text(lifted == null ? "CUM FUNCTIONEAZA"
                            : lifted.spec().displayName(), NamedTextColor.YELLOW),
                    bookLore(lifted)));
        }, () -> inventory.setItem(VEHICLE_SLOT, make(Material.BARRIER,
                Component.text("BANC INEXISTENT", NamedTextColor.RED), List.of())));
        player.openInventory(inventory);
    }

    /** Masina de pe cricul legat de banc, DAR numai cand e complet ridicata. */
    private Optional<LandVehicle> liftedVehicle(UUID stationId) {
        return mechanic.workstations().menuJack(stationId)
                .filter(jack -> jack.state == Jack.State.LIFTED && jack.vehicleId != null)
                .flatMap(jack -> mechanic.vehicles().byId(jack.vehicleId));
    }

    private static List<Component> bookLore(LandVehicle lifted) {
        if (lifted != null) {
            return List.of(
                    Component.text(String.format("Sanatate: %.0f/100", lifted.health()),
                            NamedTextColor.YELLOW),
                    Component.text(lifted.engineInstalled()
                                    ? "Motor: " + lifted.engineTier().label()
                                    : "Motor: neinstalat (slot gol)", NamedTextColor.GRAY),
                    Component.text(lifted.wheelsInstalled()
                                    ? "Roti: " + lifted.wheelType().label()
                                    : "Roti: neinstalate (slot gol)", NamedTextColor.GRAY),
                    Component.text("Piesele se schimba din sloturile MOTOR/ROTI.",
                            NamedTextColor.DARK_GRAY));
        }
        return List.of(
                Component.text("Iei cheia/ciocanul de aici; se intorc singure la banc",
                        NamedTextColor.GRAY),
                Component.text("cand iesi din zona, te deconectezi sau spargi bancul.",
                        NamedTextColor.GRAY),
                Component.text("Ridica masina cu butonul de sus; abia apoi montezi",
                        NamedTextColor.GRAY),
                Component.text("motorul/rotile si dai cu spray-ul (capsula, din mana).",
                        NamedTextColor.GRAY));
    }

    /* ==================== butonul RIDICA / COBOARA ==================== */

    private void liftButton(Inventory inventory, UUID stationId) {
        Optional<Jack> jack = mechanic.workstations().menuJack(stationId);
        if (jack.isEmpty()) {
            inventory.setItem(LIFT_SLOT, make(Material.BARRIER,
                    Component.text("FARA CRIC IN ZONA", NamedTextColor.RED),
                    List.of(Component.text("Pune un cric langa banc (max cateva blocuri).",
                            NamedTextColor.GRAY))));
            return;
        }
        Jack j = jack.get();
        String vehicleName = j.vehicleId == null ? null
                : mechanic.vehicles().byId(j.vehicleId)
                        .map(vehicle -> vehicle.spec().displayName()).orElse(null);
        switch (j.state) {
            case DOWN -> inventory.setItem(LIFT_SLOT, tag(entry(
                    new ItemStack(Material.LIME_DYE),
                    Component.text("RIDICA pe cric", NamedTextColor.GREEN),
                    List.of(Component.text(vehicleName == null
                                    ? "Cricul e gol - click = ridici capul (test)."
                                    : "Click = aliniez si ridic: " + vehicleName,
                            NamedTextColor.GRAY))), "lift_toggle"));
            case LIFTED -> inventory.setItem(LIFT_SLOT, tag(entry(
                    new ItemStack(Material.GRAY_DYE),
                    Component.text("COBOARA de pe cric", NamedTextColor.YELLOW),
                    List.of(Component.text(vehicleName == null
                                    ? "Click = cobori capul."
                                    : "Click = cobori: " + vehicleName
                                    + " (trebuie sa aiba roti montate).",
                            NamedTextColor.GRAY))), "lift_toggle"));
            default -> inventory.setItem(LIFT_SLOT, make(Material.CLOCK,
                    Component.text("CRICUL SE MISCA...", NamedTextColor.AQUA),
                    List.of(Component.text("Stai departe pana termina.", NamedTextColor.GRAY))));
        }
    }

    /* ==================== rack-ul uneltelor ==================== */

    private void wrenchEntry(Inventory inventory, WorkstationManager.Station station, Player viewer) {
        boolean iHaveWrench = false;
        for (ItemStack stack : viewer.getInventory().getContents()) {
            UUID origin = MechanicItems.wrenchStationOf(stack);
            if (origin != null && origin.equals(station.id)) {
                iHaveWrench = true;
                break;
            }
        }
        if (iHaveWrench) {
            // Brief: unealta luata de TINE apare tot ca BARRIER (X) - o intoarce
            // singura la banc (zona/quit/spargere), nu e nevoie de click aici.
            inventory.setItem(WRENCH_SLOT, make(Material.BARRIER,
                    Component.text("LUATA DE TINE", NamedTextColor.RED),
                    List.of(Component.text("Cheia e in inventarul tau - se intoarce singura",
                                    NamedTextColor.GRAY),
                            Component.text("cand iesi din zona sau te deconectezi.",
                                    NamedTextColor.DARK_GRAY))));
            return;
        }
        UUID holderUuid = mechanic.workstations().wrenchHolderOf(station, 0);
        if (holderUuid == null) {
            inventory.setItem(WRENCH_SLOT, tag(entry(
                    MechanicItems.wrench(null),
                    Component.text("CHEIE DE MECANIC", NamedTextColor.GOLD),
                    List.of(Component.text("Libera - click = o iei (ramai in zona de service!).",
                            NamedTextColor.GRAY))), "take_wrench"));
            return;
        }
        String who = Bukkit.getOfflinePlayer(holderUuid).getName();
        inventory.setItem(WRENCH_SLOT, make(Material.BARRIER,
                Component.text("OCUPATA - " + (who == null ? "cineva" : who), NamedTextColor.RED),
                List.of(Component.text("Cheia e luata de alt mecanic - asteapta sa o intoarca.",
                        NamedTextColor.GRAY))));
    }

    private void toolEntry(Inventory inventory, int slot, String tool, ItemStack icon,
                           UUID holderUuid, Player viewer) {
        if (holderUuid == null) {
            inventory.setItem(slot, tag(entry(icon,
                    Component.text("CIOCAN DE LACATUS", NamedTextColor.GOLD),
                    List.of(Component.text("Liber - click = il iei (se intoarce singur la banc).",
                            NamedTextColor.GRAY))), "take_" + tool));
            return;
        }
        if (holderUuid.equals(viewer.getUniqueId())) {
            inventory.setItem(slot, make(Material.BARRIER,
                    Component.text("LUAT DE TINE", NamedTextColor.RED),
                    List.of(Component.text("Ciocanul e in inventarul tau - se intoarce singur",
                                    NamedTextColor.GRAY),
                            Component.text("cand iesi din zona sau te deconectezi.",
                                    NamedTextColor.DARK_GRAY))));
            return;
        }
        String who = Bukkit.getOfflinePlayer(holderUuid).getName();
        inventory.setItem(slot, make(Material.BARRIER,
                Component.text("OCUPATA - " + (who == null ? "cineva" : who), NamedTextColor.RED),
                List.of(Component.text("Unealta e folosita de alt mecanic - asteapta sa o intoarca.",
                        NamedTextColor.GRAY))));
    }

    /* ==================== refresh live pentru toti vizitatorii ==================== */

    public void refreshToolsMenus(UUID stationId) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder && stationId.equals(holder.stationId())) {
                openToolsMenu(online, stationId);
            }
        }
    }

    /* ==================== helperi iteme ==================== */

    private static ItemStack make(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack entry(ItemStack stack, Component name, List<Component> lore) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack tag(ItemStack stack, String action) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }
}
