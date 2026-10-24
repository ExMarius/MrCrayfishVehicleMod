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
 * Bancul de service deschis cu cheia de mecanic pe o masina RIDICATA pe cric
 * (exact fluxul din mod: wrench pe vehiculul de pe EntityJack -> EditVehicleScreen).
 * Acesta ESTE ecranul modului, reprodus viu pe client vanilla:
 *  - fundalul = TEXTURA REALA edit_vehicle.png a modului, desenata pixel-perfect
 *    de fontul-backdrop (glifa \uE700 din pack) in spatele inventarului 9x6;
 *  - sloturile MOTOR (8,17) si ROTI (8,35) coincid cu grila chest - pui piesa
 *    noua pe cursor si click = schimb, cursorul pastreaza piesa veche (ca in mod);
 *  - in fereastra desenata de mod sta MODELUL 3D COMPUS (caroserie+roti+motor+
 *    volan/manere), MARE si STATIC, exact unghiul initial al ferestrei din mod
 *    (tilt 20, yaw -135; rotatia cu mouse-ul ar cere client modat);
 *  - numele vehiculului pe bara de titlu, pozitia exacta a textului din mod;
 *  - jos pe banda libera: UNICUL extras cerut - butonul VOPSESTE (16 culori,
 *    gratuit). Inventarul jucatorului ramane jos, ca in mod.
 */
public final class MechanicGui {
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final UUID vehicleId;
        private final boolean paintPage;
        /** Preview-ul live: id-ul spec-ului + cadrul de rotatie curent (0..FRAMES-1). */
        private String previewId;
        private int frame;

        /** Pagina rack-ului de unelte: vehicleId carra id-ul BANCULUI (nu al masinii). */
        private final boolean toolsPage;

        public Holder(UUID vehicleId, boolean paintPage) {
            this(vehicleId, paintPage, false);
        }

        public Holder(UUID vehicleId, boolean paintPage, boolean toolsPage) {
            this.vehicleId = vehicleId;
            this.paintPage = paintPage;
            this.toolsPage = toolsPage;
        }

        public UUID vehicleId() {
            return vehicleId;
        }

        public boolean paintPage() {
            return paintPage;
        }

        public boolean toolsPage() {
            return toolsPage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final NamespacedKey ACTION = new NamespacedKey("vehicle", "mechanic_gui_action");
    private static final int SIZE = 54; // piept dublu = fereastra 176x222 a texturii modului
    // Layout 1:1 cu edit_vehicle.png (textura reala desenata ca font-backdrop):
    //  - sloturile desenate de mod coincid cu grila chest: MOTOR (8,17) = slot 0,
    //    ROTI (8,35) = slot 9;
    //  - fereastra desenata (26..168 x 17..87) primeste in centru masina 3D MARE,
    //    rotativa (slot 13 = rand 1, col 4);
    //  - butonul VOPSESTE (extrasul cerut) sta pe banda goala de sub fereastra.
    public static final int ENGINE_SLOT = 0;   // pozitia exacta din mod (8,17)
    public static final int WHEEL_SLOT = 9;    // pozitia exacta din mod (8,35)
    public static final int INFO_SLOT = 14;    // preview ULTRACENTRAT pe fereastra
                                              // modului (brief: mutat un slot la dreapta)
    public static final int PAINT_SLOT = 18;   // spray-ul: SUB slotul de roti (8,53), cerut explicit
    /** Fontul-backdrop din pack: deseneaza textura edit_vehicle.png in spatele meniului. */
    private static final net.kyori.adventure.key.Key GUI_FONT =
            net.kyori.adventure.key.Key.key("vehicle", "jack_gui");
    /** Glifele din font: shift la marginea ferestrei + glifa texturii + shift inapoi la x=8. */
    private static final String BACKDROP = "\uE7F0\uE700\uE7F1";

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
        // Titlul = EFECTIV ecranul din mod: TEXTURA edit_vehicle.png desenata de
        // fontul-backdrop (glifa) in spatele inventarului + numele vehiculului pe
        // bara de titlu, exact pozitia la care modul scrie titlul (8,6).
        Component title = Component.text(BACKDROP).font(GUI_FONT)
                .append(Component.text(vehicle.spec().displayName(), NamedTextColor.DARK_GRAY));
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.inventory = inventory;

        // Niciun panou de umplutura: fundalul e textura reala a modului (randata
        // de font); singurele elemente deasupra ei sunt sloturile functionale.
        // Centrul ferestrei: vehiculul MARE (modelul 3D compus complet din mod) +
        // datele complete; preview-ul e STATIC, unghiul initial din mod.
        holder.previewId = vehicle.spec().id();
        holder.frame = 0;
        inventory.setItem(INFO_SLOT, info(vehicle, previewStateKey(vehicle), holder.frame));

        if (vehicle.spec().defaultEngineType() == EngineType.NONE) {
            inventory.setItem(ENGINE_SLOT, tag(make(Material.BARRIER,
                    Component.text("FARA MOTOR", NamedTextColor.RED),
                    List.of(Component.text("Acest vehicul nu primeste upgrade de motor.",
                            NamedTextColor.GRAY))), "none"));
        } else if (!vehicle.engineInstalled()) {
            // Slot functional (virtual) dar GOL: piesa apare pe model abia dupa
            // instalare - exact comportamentul din mod la vehiculele fara motor
            // "din fabrica" (ATV, Dune Buggy).
            inventory.setItem(ENGINE_SLOT, tag(make(Material.PAPER,
                    Component.text("MOTOR (slot gol)", NamedTextColor.GRAY),
                    List.of(Component.text("Vehiculul nu are motor montat inca.",
                            NamedTextColor.DARK_GRAY),
                            Component.text("Tine motorul potrivit pe cursor si click aici = instalare.",
                            NamedTextColor.GRAY))), "engine_slot"));
        } else {
            inventory.setItem(ENGINE_SLOT, tag(entry(
                    MechanicItems.engine(vehicle.spec().defaultEngineType(), vehicle.engineTier()),
                    Component.text("MOTOR", NamedTextColor.AQUA),
                    List.of(Component.text("Tine motorul NOU pe cursor si click aici = schimb.",
                            NamedTextColor.GRAY),
                            Component.text("Cursor gol + click = scoti motorul (piesa dispare de pe model).",
                            NamedTextColor.DARK_GRAY))), "engine_slot"));
        }
        if (!vehicle.spec().hasWheels()) {
            inventory.setItem(WHEEL_SLOT, tag(make(Material.BARRIER,
                    Component.text("FARA ROTI", NamedTextColor.RED),
                    List.of(Component.text("Acest vehicul nu primeste roti.",
                            NamedTextColor.GRAY))), "none"));
        } else if (!vehicle.wheelsInstalled()) {
            // Slot functional dar GOL - rotile au fost scoase (masina nu porneste
            // si nu poate fi coborata de pe cric pana la re-montare).
            inventory.setItem(WHEEL_SLOT, tag(make(Material.PAPER,
                    Component.text("ROTI (slot gol)", NamedTextColor.GRAY),
                    List.of(Component.text("Vehiculul ramane fara roti montate.",
                            NamedTextColor.DARK_GRAY),
                            Component.text("Tine rotile pe cursor si click aici = instalare.",
                            NamedTextColor.GRAY))), "wheel_slot"));
        } else {
            inventory.setItem(WHEEL_SLOT, tag(entry(
                    MechanicItems.wheel(vehicle.wheelType()),
                    Component.text("ROTI", NamedTextColor.AQUA),
                    List.of(Component.text("Tine rotile NOI pe cursor si click aici = schimb.",
                            NamedTextColor.GRAY),
                            Component.text("Cursor gol + click = scoti rotile (dispar si de pe model).",
                            NamedTextColor.DARK_GRAY))), "wheel_slot"));
        }

        // Butonul EXTRA cerut de tine (in mod nu exista aici): paleta noastra de vopsea.
        inventory.setItem(PAINT_SLOT, tag(entry(
                MechanicItems.sprayCan(0xB02E26, "rosie"),
                Component.text("VOPSESTE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                List.of(Component.text("Deschide paleta - vopsire GRATUITA, nu consuma nimic.",
                        NamedTextColor.GRAY))), "paint"));
        player.openInventory(inventory);
    }

    /** Panou de rama/fereastra fara nume - inutilizat de la backdrop-ul cu textura
     *  reala a modului, pastrat pentru eventuale pagini auxiliare viitoare. */
    @SuppressWarnings("unused")
    private static ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.BLACK));
        stack.setItemMeta(meta);
        return stack;
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

    /* ==================== Rack-ul uneltelor bancului (binding, GUI sincron) ==================== */

    private static final int TOOLS_LIFT_SLOT = 4;   // sus, central: ridica/coboara
    private static final int TOOLS_WRENCH_SLOT = 12;
    private static final int TOOLS_HAMMER_SLOT = 14;

    /** Meniul bancului (refacere 2026-10-24): EXACT o cheie + un ciocan disponibile;
     *  spray-ul NU exista ca unealta aici (se aplica doar din capsula tinuta in mana,
     *  si numai pe masina ridicata). Ridicarea/coborarea cricului = butonul de sus. */
    public void openToolsMenu(Player player, UUID stationId) {
        Holder holder = new Holder(stationId, false, true);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Banc de lucru", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;
        mechanic.workstations().stationOf(stationId).ifPresentOrElse(station -> {
            liftButton(inventory, stationId);
            // CHEIE: o singura instanta; ocupata de altul = bariera cu nume + niciun
            // click; a ta = returnare; libera = poti s-o iei.
            boolean iHaveWrench = false;
            for (ItemStack stack : player.getInventory().getContents()) {
                java.util.UUID origin = MechanicItems.wrenchStationOf(stack);
                if (origin != null && origin.equals(stationId)) { iHaveWrench = true; break; }
            }
            if (iHaveWrench) {
                inventory.setItem(TOOLS_WRENCH_SLOT, tag(entry(
                        MechanicItems.wrench(null),
                        Component.text("LA TINE (cheie)", NamedTextColor.GREEN),
                        List.of(Component.text("Click = o returnezi la banc.",
                                NamedTextColor.GRAY))), "return_wrench"));
            } else {
                java.util.UUID holderUuid = mechanic.workstations().wrenchHolderOf(station, 0);
                if (holderUuid == null) {
                    inventory.setItem(TOOLS_WRENCH_SLOT, tag(entry(
                            MechanicItems.wrench(null),
                            Component.text("CHEIE DE MECANIC", NamedTextColor.GOLD),
                            List.of(Component.text("Libera - click = o iei (ramai in zona de service!).",
                                    NamedTextColor.GRAY))), "take_wrench"));
                } else {
                    String who = Bukkit.getOfflinePlayer(holderUuid).getName();
                    inventory.setItem(TOOLS_WRENCH_SLOT, make(Material.BARRIER,
                            Component.text("OCUPATA - " + (who == null ? "cineva" : who), NamedTextColor.RED),
                            List.of(Component.text("Cheia e luata de alt mecanic - asteapta sa o intoarca.",
                                    NamedTextColor.GRAY))));
                }
            }
            toolEntry(inventory, TOOLS_HAMMER_SLOT, "hammer",
                    MechanicItems.hammer(null, mechanic.workstations().hammerUsedOf(station)),
                    mechanic.workstations().hammerHolderOf(station), player, stationId);
            inventory.setItem(22, make(Material.BOOK,
                    Component.text("CUM FUNCTIONEAZA", NamedTextColor.YELLOW), List.of(
                            Component.text("Iei cheia/ciocanul de aici; se intorc singure la banc",
                                    NamedTextColor.GRAY),
                            Component.text("cand iesi din zona, te deconectezi sau spargi bancul.",
                                    NamedTextColor.GRAY),
                            Component.text("Vopsirea: doar cu capsula de spray din mana,",
                                    NamedTextColor.GRAY),
                            Component.text("si numai cu masina ridicata pe cric.",
                                    NamedTextColor.GRAY))));
        }, () -> inventory.setItem(13, make(Material.BARRIER,
                Component.text("BANC INEXISTENT", NamedTextColor.RED), List.of())));
        player.openInventory(inventory);
    }

    /** Butonul RIDICA/COBOARA: starea cricului legat + masina de deasupra, live. */
    private void liftButton(Inventory inventory, UUID stationId) {
        java.util.Optional<JackManager.Jack> jack = mechanic.workstations().menuJack(stationId);
        if (jack.isEmpty()) {
            inventory.setItem(TOOLS_LIFT_SLOT, make(Material.BARRIER,
                    Component.text("FARA CRIC IN ZONA", NamedTextColor.RED),
                    List.of(Component.text("Pune un cric langa banc (max cateva blocuri).",
                            NamedTextColor.GRAY))));
            return;
        }
        JackManager.Jack j = jack.get();
        String vehicleName = j.vehicleId == null ? null
                : mechanic.vehicles().byId(j.vehicleId)
                        .map(vehicle -> vehicle.spec().displayName()).orElse(null);
        switch (j.state) {
            case DOWN -> inventory.setItem(TOOLS_LIFT_SLOT, tag(entry(
                    new ItemStack(Material.LIME_DYE),
                    Component.text("RIDICA pe cric", NamedTextColor.GREEN),
                    List.of(Component.text(vehicleName == null
                                    ? "Cricul e gol - click = ridici capul (test)."
                                    : "Click = aliniez si ridic: " + vehicleName,
                            NamedTextColor.GRAY))), "lift_toggle"));
            case LIFTED -> inventory.setItem(TOOLS_LIFT_SLOT, tag(entry(
                    new ItemStack(Material.GRAY_DYE),
                    Component.text("COBOARA de pe cric", NamedTextColor.YELLOW),
                    List.of(Component.text(vehicleName == null
                                    ? "Click = cobori capul."
                                    : "Click = cobori: " + vehicleName
                                    + " (trebuie sa aiba roti montate).",
                            NamedTextColor.GRAY))), "lift_toggle"));
            default -> inventory.setItem(TOOLS_LIFT_SLOT, make(Material.CLOCK,
                    Component.text("CRICUL SE MISCA...", NamedTextColor.AQUA),
                    List.of(Component.text("Stai departe pana termina.", NamedTextColor.GRAY))));
        }
    }

    private void toolEntry(Inventory inventory, int slot, String tool, ItemStack icon,
                           UUID holderUuid, Player viewer, UUID stationId) {
        if (holderUuid == null) {
            inventory.setItem(slot, tag(entry(icon,
                    Component.text("hammer".equals(tool) ? "CIOCAN DE LACATUS" : "PISTOL DE VOPSEA",
                            NamedTextColor.GOLD),
                    List.of(Component.text("Libera - click = o iei (se intoarce singura la banc).",
                            NamedTextColor.GRAY))), "take_" + tool));
            return;
        }
        if (holderUuid.equals(viewer.getUniqueId())) {
            inventory.setItem(slot, tag(entry(icon,
                    Component.text("LA TINE (" + ("hammer".equals(tool) ? "ciocan" : "pistol") + ")",
                            NamedTextColor.GREEN),
                    List.of(Component.text("Click = o returnezi la banc.", NamedTextColor.GRAY))),
                    "return_" + tool));
            return;
        }
        String who = Bukkit.getOfflinePlayer(holderUuid).getName();
        inventory.setItem(slot, make(Material.BARRIER,
                Component.text("OCUPATA - " + (who == null ? "cineva" : who), NamedTextColor.RED),
                List.of(Component.text("Unealta e folosita de alt mecanic - asteapta sa o intoarca.",
                        NamedTextColor.GRAY))));
    }

    /** Refresh live al meniurilor de unelte deschise pt. acelasi banc (brief:
     *  blocarea dinamica in GUI cand altcineva ia/returneaza o unealta). */
    public void refreshToolsMenus(UUID stationId) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder && holder.toolsPage
                    && stationId.equals(holder.vehicleId())) {
                openToolsMenu(online, stationId);
            }
        }
    }

    /** 24 de cadre de rotatie lina lenta (5 tick-uri/cadru => 6s pe rotatie),
     *  alese prin custom_model_data - monitorul arata masina din toate unghiurile. */
    public static final int FRAMES = 24;

    /** Cheia de stare LIVE a preview-ului (brief 2026-10-24, sincronizare 1:1):
     *  roti curente x motor instalat? x vopsea curenta = exact masina de pe cric.
     *  Orice schimbare (roti/motor/vopsea) se reflecta IMEDIAT si in monitor. */
    private static NamespacedKey previewStateKey(LandVehicle vehicle) {
        String wheel = vehicle.spec().hasWheels() && vehicle.wheelsInstalled()
                ? vehicle.wheelType().model().replace("_wheel", "")
                : "none";
        String engine = vehicle.spec().engine() != null && vehicle.engineInstalled()
                ? "eng" : "noe";
        String color = "default";
        if (vehicle.painted()) {
            for (PaintColors paint : PaintColors.values()) {
                if (paint.rgb() == vehicle.paintColor()) {
                    color = paint.name().toLowerCase(Locale.ROOT);
                    break;
                }
            }
        }
        return new NamespacedKey("vehicle", "vehicle_preview/" + vehicle.spec().id()
                + "/" + wheel + "/" + engine + "/" + color);
    }

    /** Rotația automata FOARTE lenta si lina (brief: vizualizare 3D din toate
     *  unghiurile, pentru ca drag-ul cu mouse-ul nu exista pe chest vanilla).
     *  La fiecare avans reconstruim itemul din starea LIVE a vehiculului:
     *  schimbarile de roti/motor/vopsea se vad instant, fara re-intrare in meniu. */
    public void advancePreviewFrames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof Holder holder)
                    || holder.paintPage() || top.getSize() != SIZE || holder.previewId == null) {
                continue;
            }
            java.util.Optional<LandVehicle> vehicle = mechanic.vehicles().byId(holder.vehicleId());
            if (vehicle.isEmpty()) {
                player.closeInventory();
                continue;
            }
            holder.frame = (holder.frame + 1) % FRAMES;
            top.setItem(INFO_SLOT, info(vehicle.get(), previewStateKey(vehicle.get()), holder.frame));
        }
    }

    private static ItemStack info(LandVehicle vehicle, NamespacedKey modelKey, int frame) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(modelKey);
        // cadrul de rotatie: range_dispatch pe custom_model_data (vezi builderul pack-ului)
        meta.setCustomModelData(Integer.valueOf(frame));
        meta.displayName(Component.text(vehicle.spec().displayName(), NamedTextColor.WHITE,
                TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format(Locale.ROOT, "Sanatate: %.0f/100", vehicle.health()),
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(vehicle.engineInstalled()
                        ? "Motor: " + vehicle.engineTier().label() + " "
                        + vehicle.spec().defaultEngineType().label().toLowerCase(Locale.ROOT)
                        : "Motor: neinstalat (slot gol)",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(vehicle.spec().hasWheels()
                        ? (vehicle.wheelsInstalled()
                            ? "Roti: " + vehicle.wheelType().label()
                            : "Roti: neinstalate (slot gol)")
                        : "Roti: n/a",
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
