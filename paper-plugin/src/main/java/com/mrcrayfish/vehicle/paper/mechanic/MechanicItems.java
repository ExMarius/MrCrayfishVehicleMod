package com.mrcrayfish.vehicle.paper.mechanic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fabrica unica a itemelor sistemului mecanic: motoare (3 tipuri x 4 tier), roti (3),
 * wrench/hammer/key/spray can, itemele plasabile (jack/workstation/con/pad/țeavă)
 * si "cufărul de vehicul" dat de hammer la demontare.
 * Acelasți mecanism ca JerryCanItem: PAPER + item_model din pack + identitate in PDC.
 */
public final class MechanicItems {
    public enum Kind {
        ENGINE, WHEEL, WRENCH, HAMMER, KEY, SPRAY_CAN,
        JACK, WORKSTATION, TRAFFIC_CONE, BOOST_PAD, FLUID_PIPE, VEHICLE_CRATE
    }

    private static final NamespacedKey KIND = new NamespacedKey("vehicle", "mechanic_kind");
    private static final NamespacedKey ENGINE_TYPE = new NamespacedKey("vehicle", "engine_type");
    private static final NamespacedKey ENGINE_TIER = new NamespacedKey("vehicle", "engine_tier");
    private static final NamespacedKey WHEEL_TYPE = new NamespacedKey("vehicle", "wheel_type");
    private static final NamespacedKey KEY_TARGET = new NamespacedKey("vehicle", "key_vehicle");
    private static final NamespacedKey SPRAY_COLOR = new NamespacedKey("vehicle", "spray_color");
    private static final NamespacedKey SPRAY_NAME = new NamespacedKey("vehicle", "spray_color_name");
    private static final NamespacedKey CRATE_PAYLOAD = new NamespacedKey("vehicle", "crate_payload");
    private static final NamespacedKey WRENCH_STATION = new NamespacedKey("vehicle", "wrench_station");

    private MechanicItems() {
    }

    private static ItemStack base(Kind kind, String model) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", model));
        meta.getPersistentDataContainer().set(KIND, PersistentDataType.STRING, kind.name());
        item.setItemMeta(meta);
        return item;
    }

    private static void finish(ItemStack item, Component name, List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> plain = new ArrayList<>(lore);
        plain.replaceAll(component -> component.decoration(TextDecoration.ITALIC, false));
        meta.lore(plain);
        item.setItemMeta(meta);
    }

    public static Kind kindOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String name = stack.getItemMeta().getPersistentDataContainer()
                .get(KIND, PersistentDataType.STRING);
        try {
            return name == null ? null : Kind.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /* ================================ motoare ================================ */

    public static ItemStack engine(EngineType type, EngineTier tier) {
        String model = tier.name().toLowerCase(Locale.ROOT) + "_" + type.folder() + "_engine";
        ItemStack item = base(Kind.ENGINE, model);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ENGINE_TYPE, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(ENGINE_TIER, PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Putere: x" + trim(tier.powerMultiplier()), NamedTextColor.YELLOW));
        lore.add(Component.text("Viteza maxima: +" + (int) tier.additionalMaxSpeed() + " bps",
                NamedTextColor.YELLOW));
        lore.add(Component.text("Pune-l pe un motor " + type.label().toLowerCase(Locale.ROOT)
                + " cu click dreapta pe vehicul.", NamedTextColor.GRAY));
        finish(item, Component.text(tier.label() + " " + type.label().toLowerCase(Locale.ROOT),
                NamedTextColor.GOLD), lore);
        return item;
    }

    public static EngineType engineTypeOf(ItemStack stack) {
        if (kindOf(stack) != Kind.ENGINE) {
            return null;
        }
        return EngineType.fromName(stack.getItemMeta().getPersistentDataContainer()
                .get(ENGINE_TYPE, PersistentDataType.STRING));
    }

    public static EngineTier engineTierOf(ItemStack stack) {
        if (kindOf(stack) != Kind.ENGINE) {
            return null;
        }
        return EngineTier.fromName(stack.getItemMeta().getPersistentDataContainer()
                .get(ENGINE_TIER, PersistentDataType.STRING));
    }

    private static String trim(float value) {
        return String.format(Locale.ROOT, "%.1f", value).replace(".0", "");
    }

    /* ================================ roti ================================== */

    public static ItemStack wheel(WheelType type) {
        ItemStack item = base(Kind.WHEEL, type.model());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(WHEEL_TYPE, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Asfalt x" + trim(type.surfaceModifier(
                com.mrcrayfish.vehicle.paper.physics.SurfaceProfile.Category.ROAD))
                + " / pamant x" + trim(type.surfaceModifier(
                        com.mrcrayfish.vehicle.paper.physics.SurfaceProfile.Category.DIRT))
                + " / zapada x" + trim(type.surfaceModifier(
                        com.mrcrayfish.vehicle.paper.physics.SurfaceProfile.Category.SNOW)),
                NamedTextColor.YELLOW));
        lore.add(Component.text("Monteaza-le cu click dreapta pe vehicul.", NamedTextColor.GRAY));
        finish(item, Component.text(type.label(), NamedTextColor.GOLD), lore);
        return item;
    }

    public static WheelType wheelTypeOf(ItemStack stack) {
        if (kindOf(stack) != Kind.WHEEL) {
            return null;
        }
        return WheelType.fromName(stack.getItemMeta().getPersistentDataContainer()
                .get(WHEEL_TYPE, PersistentDataType.STRING));
    }

    /* ================================ unelte ================================ */

    /** Cheia "libera" (din /vehicle creative) - nu e legata de niciun banc. */
    public static ItemStack wrench() {
        return wrench(null);
    }

    /** Cheia data de un banc de lucru: poarta id-ul bancului in PDC, ca sa
     *  dispara din inventar cand jucatorul iese din zona de service. */
    public static ItemStack wrench(UUID stationId) {
        ItemStack item = base(Kind.WRENCH, "wrench");
        if (stationId != null) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(WRENCH_STATION, PersistentDataType.STRING,
                    stationId.toString());
            item.setItemMeta(meta);
        }
        finish(item, Component.text("Cheie de mecanic", NamedTextColor.GOLD), List.of(
                Component.text("Click pe cric = ridica/coboara masina.", NamedTextColor.GRAY),
                Component.text("Click pe masina ridicata = meniul de mecanic.", NamedTextColor.GRAY)));
        return item;
    }

    /** Id-ul bancului care a dat cheia; null = cheie libera (creative / veche). */
    public static UUID wrenchStationOf(ItemStack stack) {
        if (kindOf(stack) != Kind.WRENCH) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(WRENCH_STATION, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static ItemStack hammer() {
        ItemStack item = base(Kind.HAMMER, "hammer");
        finish(item, Component.text("Ciocan de lăcătuș", NamedTextColor.GOLD), List.of(
                Component.text("Loveste masina TA: o demontezi in cufar de transport.",
                        NamedTextColor.GRAY)));
        return item;
    }

    /** Cheia masinii: legata de vehicul la cumparare; click pe masina = incui/deschide. */
    public static ItemStack key(UUID vehicleId, String vehicleName) {
        ItemStack item = base(Kind.KEY, "key");
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY_TARGET, PersistentDataType.STRING,
                vehicleId.toString());
        item.setItemMeta(meta);
        finish(item, Component.text("Cheie: " + vehicleName, NamedTextColor.GOLD), List.of(
                Component.text("Click dreapta pe masina = incui/deschide.", NamedTextColor.GRAY)));
        return item;
    }

    public static ItemStack blankKey() {
        ItemStack item = base(Kind.KEY, "key");
        finish(item, Component.text("Cheie de masina", NamedTextColor.GOLD), List.of(
                Component.text("Nelegata - primesti cheia legata la cumpararea masinii.",
                        NamedTextColor.GRAY)));
        return item;
    }

    public static UUID keyTargetOf(ItemStack stack) {
        if (kindOf(stack) != Kind.KEY) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(KEY_TARGET, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /* ================================ spray can ============================= */

    public static ItemStack sprayCan(int rgb, String colorName) {
        ItemStack item = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", "spray_can"));
        meta.setColor(Color.fromRGB(rgb));
        meta.getPersistentDataContainer().set(KIND, PersistentDataType.STRING, Kind.SPRAY_CAN.name());
        meta.getPersistentDataContainer().set(SPRAY_COLOR, PersistentDataType.INTEGER, rgb);
        meta.getPersistentDataContainer().set(SPRAY_NAME, PersistentDataType.STRING, colorName);
        item.setItemMeta(meta);
        finish(item, Component.text("Spray vopsea (" + colorName + ")", NamedTextColor.GOLD), List.of(
                Component.text("Click dreapta pe vehicul = vopsesti caroseria.",
                        NamedTextColor.GRAY),
                Component.text("O singura folosinta.", NamedTextColor.DARK_GRAY)));
        return item;
    }

    public static Integer sprayColorOf(ItemStack stack) {
        if (kindOf(stack) != Kind.SPRAY_CAN) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(SPRAY_COLOR, PersistentDataType.INTEGER);
    }

    public static String sprayColorNameOf(ItemStack stack) {
        if (kindOf(stack) != Kind.SPRAY_CAN) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(SPRAY_NAME, PersistentDataType.STRING);
    }

    /* ================================ plasabile ============================= */

    public static ItemStack jack() {
        ItemStack item = base(Kind.JACK, "jack");
        finish(item, Component.text("Cric auto", NamedTextColor.GOLD), List.of(
                Component.text("Plaseaza langa masina, apoi click cu cheia de mecanic.",
                        NamedTextColor.GRAY)));
        return item;
    }

    public static ItemStack workstation() {
        ItemStack item = base(Kind.WORKSTATION, "mechanic_workstation");
        finish(item, Component.text("Banc de lucru (mecanic)", NamedTextColor.GOLD), List.of(
                Component.text("Click pe el = primesti cheia de mecanic.", NamedTextColor.GRAY)));
        return item;
    }

    public static ItemStack trafficCone() {
        ItemStack item = base(Kind.TRAFFIC_CONE, "traffic_cone");
        finish(item, Component.text("Con de trafic", NamedTextColor.GOLD), List.of(
                Component.text("Decor stradal - click dreapta pentru plasare.", NamedTextColor.GRAY)));
        return item;
    }

    public static ItemStack boostPad() {
        ItemStack item = base(Kind.BOOST_PAD, "boost_pad");
        finish(item, Component.text("Placa de boost", NamedTextColor.GOLD), List.of(
                Component.text("Treci cu masina peste ea pentru acceleratie.", NamedTextColor.GRAY)));
        return item;
    }

    public static ItemStack fluidPipe() {
        ItemStack item = base(Kind.FLUID_PIPE, "fluid_pipe");
        finish(item, Component.text("Teava de fluid", NamedTextColor.GOLD), List.of(
                Component.text("Decor de statie - click dreapta pentru plasare.", NamedTextColor.GRAY)));
        return item;
    }

    /* ============================ cufar vehicul ============================= */

    /** Payload text: specId|fuel|engineTier|wheelType|health|color|locked|ownerUuid */
    public static String cratePayload(Object... parts) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                builder.append('|');
            }
            builder.append(parts[index] == null ? "" : parts[index].toString());
        }
        return builder.toString();
    }

    public static String[] parseCratePayload(String payload) {
        return payload == null ? new String[0] : payload.split("\\|", -1);
    }

    public static ItemStack vehicleCrate(String specId, String displayName, String payload) {
        ItemStack item = base(Kind.VEHICLE_CRATE, "vehicle_icon/" + specId);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(CRATE_PAYLOAD, PersistentDataType.STRING, payload);
        item.setItemMeta(meta);
        finish(item, Component.text("Vehicul demontat: " + displayName, NamedTextColor.GOLD), List.of(
                Component.text("Click dreapta pe jos = o repui pe strada.", NamedTextColor.GRAY)));
        return item;
    }

    public static String cratePayloadOf(ItemStack stack) {
        if (kindOf(stack) != Kind.VEHICLE_CRATE) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(CRATE_PAYLOAD, PersistentDataType.STRING);
    }
}
