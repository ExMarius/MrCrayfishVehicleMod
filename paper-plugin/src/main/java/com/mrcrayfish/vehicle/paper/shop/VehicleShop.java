package com.mrcrayfish.vehicle.paper.shop;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * #1 pe lista FiveM (dealer auto): cumperi masina cu bani (Vault). Masina cumparata are
 * STAPAN = cumparatorul, deci intra imediat sub sistemul de trust: numai el si cei carora
 * le da voie pot conduce. Preturile vin din config.yml (dealership.prices), cu fallback
 * la valorile de mai jos (gandite pentru economia unui server mic).
 */
public final class VehicleShop {
    /** VehiclePlugin sets this after the Vault probe - true only when Vault+economy answer. */
    private static volatile boolean economyReady;

    private static final Map<String, Double> DEFAULT_PRICES = new LinkedHashMap<>();
    static {
        DEFAULT_PRICES.put("shopping_cart", 100.0D);
        DEFAULT_PRICES.put("go_kart", 2000.0D);
        DEFAULT_PRICES.put("lawn_mower", 2000.0D);
        DEFAULT_PRICES.put("bumper_car", 2000.0D);
        DEFAULT_PRICES.put("mini_bike", 2000.0D);
        DEFAULT_PRICES.put("moped", 5000.0D);
        DEFAULT_PRICES.put("dirt_bike", 5000.0D);
        DEFAULT_PRICES.put("golf_cart", 5000.0D);
        DEFAULT_PRICES.put("atv", 5000.0D);
        DEFAULT_PRICES.put("quad_bike", 8000.0D);
        DEFAULT_PRICES.put("jet_ski", 8000.0D);
        DEFAULT_PRICES.put("dune_buggy", 12000.0D);
        DEFAULT_PRICES.put("aluminum_boat", 10000.0D);
        DEFAULT_PRICES.put("tractor", 15000.0D);
        DEFAULT_PRICES.put("smart_car", 18000.0D);
        DEFAULT_PRICES.put("speed_boat", 20000.0D);
        DEFAULT_PRICES.put("off_roader", 25000.0D);
        DEFAULT_PRICES.put("mini_bus", 40000.0D);
        DEFAULT_PRICES.put("sports_car", 60000.0D);
        DEFAULT_PRICES.put("compact_helicopter", 250000.0D);
        DEFAULT_PRICES.put("sports_plane", 500000.0D);
    }

    public static void setEconomyReady(boolean ready) {
        economyReady = ready;
    }

    private final VehicleManager vehicles;
    private final FileConfiguration config;

    /** Audit Y: inv-plin NU mai inghite piesa/cheia cumparate - cad la picioare. */

    public VehicleShop(VehicleManager vehicles, FileConfiguration config) {
        this.vehicles = vehicles;
        this.config = config;
    }

    /** The whole catalog, cheapest first (podium cycles in this order too). */
    public java.util.List<String> catalogIds() {
        java.util.List<String> ids = new java.util.ArrayList<>(LandVehicleSpec.ids());
        ids.sort(java.util.Comparator.comparingDouble(this::priceOf));
        return ids;
    }

    public double priceOf(String specId) {
        double fallback = DEFAULT_PRICES.getOrDefault(specId, 10000.0D);
        return config.getDouble("dealership.prices." + specId, fallback);
    }

    /** Pretul unei piese din tab-ul Piese (prefixul id-ului inainte de ':'). */
    public double partPrice(String partId) {
        String configKey = partId == null ? "" : partId.split(":")[0];
        return config.getDouble("mechanic.parts.prices." + configKey, 1000.0D);
    }

    /** Cumpara o piesa (motor/roti/spray) - intra direct in inventarul jucatorului. */
    public void buyPart(Player player, String partId) {
        org.bukkit.inventory.ItemStack stack = com.mrcrayfish.vehicle.paper.shop.ShopGui.partStackOf(partId);
        if (stack == null) {
            return;
        }
        if (!economyReady) {
            player.sendRichMessage("<red>Magazinul are nevoie de economie: instaleaza Vault + "
                    + "EssentialsX pe server, restart, si revii.</red>");
            return;
        }
        double price = partPrice(partId);
        double balance = ShopEconomy.balance(player);
        if (balance < price) {
            player.sendRichMessage("<red>Nu-ti ajung banii: ai " + priceText(balance)
                    + ", piesa costa " + priceText(price) + ".</red>");
            return;
        }
        if (!ShopEconomy.withdraw(player, price)) {
            player.sendRichMessage("<red>Plata a esuat (banca a refuzat). Incearca din nou.</red>");
            return;
        }
        giveOrDrop(player, stack);
        player.sendRichMessage("<gray>[Magazin Piese] Ai cumparat piesa! "
                + "Monteaza-o cu click dreapta pe vehicul.</gray>");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
    }

    private static void giveOrDrop(org.bukkit.entity.Player player,
            org.bukkit.inventory.ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(left ->
                player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    public static String priceText(double value) {
        return "\uD83D\uDCB0 " + String.format("%,d", (long) value).replace(',', ' ');
    }

    public String catalogLine(String specId) {
        LandVehicleSpec spec = LandVehicleSpec.byId(specId);
        return spec.displayName() + " - " + priceText(priceOf(specId));
    }

    /** One click in the catalog = instant purchase, FiveM-fast. */
    public void buy(Player player, String specId) {
        buy(player, specId, null);
    }

    /** Same, but a podium can force its OWN price (null = global config price). */
    public void buy(Player player, String specId, Double customPrice) {
        LandVehicleSpec spec = LandVehicleSpec.byId(specId);
        if (spec == null) {
            return;
        }
        if (!economyReady) {
            player.sendRichMessage("<red>Magazinul are nevoie de economie: instaleaza Vault + "
                    + "EssentialsX pe server, restart, si revii.</red>");
            return;
        }
        double price = customPrice != null ? customPrice : priceOf(specId);
        double balance = ShopEconomy.balance(player);
        if (balance < price) {
            player.sendRichMessage("<red>Nu-ti ajung banii: ai " + priceText(balance)
                    + ", " + spec.displayName() + " costa " + priceText(price) + ".</red>");
            return;
        }
        if (!ShopEconomy.withdraw(player, price)) {
            player.sendRichMessage("<red>Plata a esuat (banca a refuzat). Incearca din nou.</red>");
            return;
        }
        // LIVRAREA: nu mai apare pe strada - intra DIRECT in garajul global al jucatorului
        // (garage.<uuid>.*), de unde il ridica GRATUIT de acasa sau cu taxa de la public (/garaj).
        // Audit Y: livrare esuata (exceptie) => RAMBURS COMPLET, nu pierzi banii.
        try {
            vehicles.storeNewVehicle(player, spec);
        } catch (RuntimeException boom) {
            ShopEconomy.deposit(player, price);
            org.bukkit.Bukkit.getLogger().severe("[Magazin] Livrarea catre " + player.getName()
                    + " a crapat: " + boom.getMessage() + " - banii returnati automat.");
            player.sendRichMessage("<red>Livrarea a esuat tehnic - banii ti-au fost "
                    + "returnati integral. Spune unui admin.</red>");
            return;
        }
        // Ciclul Z: fara chei de masina - accesul e proprietarul la volan +
        // /vehicle trust pentru prieteni.
        player.sendMessage(net.kyori.adventure.text.Component
                .text("[Magazin Auto] ", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .append(net.kyori.adventure.text.Component.text("Vehiculul a fost achizi\u021bionat! ",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN))
                .append(net.kyori.adventure.text.Component.text("A fost livrat \u00een garajul t\u0103u global. "
                        + "\u00cel po\u021bi ridica gratuit de acas\u0103 folosind ",
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .append(net.kyori.adventure.text.Component.text("/garaj ",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN))
                .append(net.kyori.adventure.text.Component.text("sau de la o parcare public\u0103 "
                        + "pl\u0103tind taxa de transport.",
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW)));
        player.sendRichMessage("<gray>Vrei sa conduca si un prieten? "
                + "<yellow>/vehicle trust <nume></yellow> langa masina.</gray>");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
    }
}
