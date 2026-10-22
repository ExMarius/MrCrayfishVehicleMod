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
 *  - in fereastra desenata de mod sta MODELUL 3D COMPUS (caroserie+roti), MARE,
 *    care se ROTESTE SINGUR lent (cadre server-side: rotatia cu mouse-ul cere
 *    client modat - animatia continua e echivalentul vanilla);
 *  - numele vehiculului pe bara de titlu, pozitia exacta a textului din mod;
 *  - jos pe banda libera: UNICUL extras cerut - butonul VOPSESTE (16 culori,
 *    gratuit). Inventarul jucatorului ramane jos, ca in mod.
 */
public final class MechanicGui {
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final UUID vehicleId;
        private final boolean paintPage;
        /** Preview-ul rotativ: id-ul modelului + cadrul curent (0..FRAMES-1). */
        private String previewId;
        private int frame;

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
    private static final int SIZE = 54; // piept dublu = fereastra 176x222 a texturii modului
    // Layout 1:1 cu edit_vehicle.png (textura reala desenata ca font-backdrop):
    //  - sloturile desenate de mod coincid cu grila chest: MOTOR (8,17) = slot 0,
    //    ROTI (8,35) = slot 9;
    //  - fereastra desenata (26..168 x 17..87) primeste in centru masina 3D MARE,
    //    rotativa (slot 13 = rand 1, col 4);
    //  - butonul VOPSESTE (extrasul cerut) sta pe banda goala de sub fereastra.
    public static final int ENGINE_SLOT = 0;   // pozitia exacta din mod (8,17)
    public static final int WHEEL_SLOT = 9;    // pozitia exacta din mod (8,35)
    public static final int INFO_SLOT = 13;    // centrul ferestrei desenate de mod
    public static final int PAINT_SLOT = 40;   // buton extra, banda de sub fereastra
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
        // Centrul ferestrei: vehiculul MARE (modelul 3D compus caroserie+roti din
        // mod) + datele complete; preview-ul se ROTESTE lent (cadrele vin din
        // advancePreviewFrames, apelat de task-ul din MechanicManager).
        holder.previewId = vehicle.spec().id();
        holder.frame = 0;
        inventory.setItem(INFO_SLOT, info(vehicle, previewModelKey(holder)));

        if (vehicle.spec().defaultEngineType() == EngineType.NONE) {
            inventory.setItem(ENGINE_SLOT, tag(make(Material.BARRIER,
                    Component.text("FARA MOTOR", NamedTextColor.RED),
                    List.of(Component.text("Acest vehicul nu primeste upgrade de motor.",
                            NamedTextColor.GRAY))), "none"));
        } else {
            inventory.setItem(ENGINE_SLOT, tag(entry(
                    MechanicItems.engine(vehicle.spec().defaultEngineType(), vehicle.engineTier()),
                    Component.text("MOTOR", NamedTextColor.AQUA),
                    List.of(Component.text("Tine motorul NOU pe cursor si click aici = schimb.",
                            NamedTextColor.GRAY))), "engine_slot"));
        }
        inventory.setItem(WHEEL_SLOT, tag(entry(
                MechanicItems.wheel(vehicle.wheelType()),
                Component.text("ROTI", NamedTextColor.AQUA),
                List.of(Component.text("Tine rotile NOI pe cursor si click aici = schimb.",
                        NamedTextColor.GRAY))), "wheel_slot"));

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

    /** Numarul de cadre ale preview-ului rotativ (8 unghiuri cate 45°, generate in pack). */
    public static final int FRAMES = 8;

    private static NamespacedKey previewModelKey(Holder holder) {
        return new NamespacedKey("vehicle",
                "vehicle_preview/" + holder.previewId + "/frame_" + holder.frame);
    }

    /** Rotația „slow in continuare" ceruta: la cateva tick-uri mutam cadrul modelului
     *  din fereastra fiecarui jucator care are deschis bancul (nu si pagina de vopsea).
     *  Cost: 1 setItem pe jucator cu meniul deschis per task - nesemnificativ. */
    public void advancePreviewFrames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof Holder holder)
                    || holder.paintPage() || top.getSize() != SIZE || holder.previewId == null) {
                continue;
            }
            ItemStack stack = top.getItem(INFO_SLOT);
            if (stack == null || !stack.hasItemMeta()) {
                continue;
            }
            holder.frame = (holder.frame + 1) % FRAMES;
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(previewModelKey(holder));
            stack.setItemMeta(meta);
            top.setItem(INFO_SLOT, stack);
        }
    }

    private static ItemStack info(LandVehicle vehicle, NamespacedKey modelKey) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(modelKey);
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
