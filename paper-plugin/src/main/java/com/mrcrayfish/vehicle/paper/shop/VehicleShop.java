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

    public static String priceText(double value) {
        return "\uD83D\uDCB0 " + String.format("%,d", (long) value).replace(',', ' ');
    }

    public String catalogLine(String specId) {
        LandVehicleSpec spec = LandVehicleSpec.byId(specId);
        return spec.displayName() + " - " + priceText(priceOf(specId));
    }

    /** One click in the catalog = instant purchase, FiveM-fast. */
    public void buy(Player player, String specId) {
        LandVehicleSpec spec = LandVehicleSpec.byId(specId);
        if (spec == null) {
            return;
        }
        if (!economyReady) {
            player.sendRichMessage("<red>Magazinul are nevoie de economie: instaleaza Vault + "
                    + "EssentialsX pe server, restart, si revii.</red>");
            return;
        }
        double price = priceOf(specId);
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
        LandVehicle vehicle = vehicles.spawn(spec, player.getLocation());
        vehicle.setOwner(player.getUniqueId());
        vehicles.save();
        player.sendRichMessage("<green>Ai cumparat <gold>" + spec.displayName() + "</gold> pentru "
                + priceText(price) + "!</green> <gray>Permite cuiva la volan: /vehicle trust <nume>.</gray>");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
    }
}
