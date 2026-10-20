package com.mrcrayfish.vehicle.paper;

import com.mrcrayfish.vehicle.paper.command.VehicleCommand;
import com.mrcrayfish.vehicle.paper.gaspump.GasPumpManager;
import com.mrcrayfish.vehicle.paper.listener.GasPumpListener;
import com.mrcrayfish.vehicle.paper.listener.JerryCanListener;
import com.mrcrayfish.vehicle.paper.listener.VehicleListener;
import com.mrcrayfish.vehicle.paper.restart.RestartWatcher;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VehiclePlugin extends JavaPlugin {
    private com.mrcrayfish.vehicle.paper.shop.VehicleShop shop;
    private com.mrcrayfish.vehicle.paper.shop.ShopGui shopGui;
    private com.mrcrayfish.vehicle.paper.shop.DealerStationManager dealerStations;
    private VehicleManager vehicleManager;
    private GasPumpManager gasPumpManager;
    private com.mrcrayfish.vehicle.paper.garage.GarageStationManager garageStationManager;
    private RestartWatcher restartWatcher;
    private boolean dataWipedThisBoot;

    /** Explicit total reset: the deploy can upload a WIPE_ON_BOOT marker into the data
     *  folder (only from a commit tagged [wipe]). On the next boot we delete the marker
     *  FIRST (so a crash can never loop the wipe), then the entire data folder — yml
     *  storage, configs, everything — and start from zero. The boot entity sweep checks
     *  wasDataWipedThisBoot() and purges every mcv_-tagged entity left in the world. */
    private boolean wipeDataIfRequested() {
        java.io.File dir = getDataFolder();
        java.io.File marker = new java.io.File(dir, "WIPE_ON_BOOT");
        if (!marker.isFile()) {
            return false;
        }
        getLogger().warning("[Wipe] WIPE_ON_BOOT gasit — sterg TOATE datele pluginului, totul de la 0.");
        if (!marker.delete()) {
            getLogger().severe("[Wipe] Markerul nu s-a sters; ANULEZ wipe-ul ca sa nu ruleze la fiecare boot!");
            return false;
        }
        // Doar cele 3 fisiere de date cerute - config.yml (preturi/setari live) RAMANE INTACT.
        String[] dataFiles = {"vehicles.yml", "garages.yml", "dealer.yml"};
        int count = 0;
        for (String name : dataFiles) {
            java.io.File target = new java.io.File(dir, name);
            if (target.isFile() && target.delete()) {
                count++;
                getLogger().warning("[Wipe] sters: " + name);
            }
        }
        getLogger().warning("[Wipe] config.yml P\u0102STRAT (preturi si setari neatinse).");

        // Curatenie entitati: ORICE ramasita cu tag mcv_* din lumile incarcate dispare -
        // riguri de vehicule orfane, vechile terminale de garaj/fise, holograme de podium,
        // ItemDisplay-uri si hitbox-uri Interaction blocate in chunk-uri din rundele trecute.
        int entities = 0;
        try {
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    for (String tag : entity.getScoreboardTags()) {
                        if (tag.startsWith("mcv_")) {
                            entity.remove();
                            entities++;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable sweepFailure) {
            getLogger().severe("[Wipe] Sweep-ul de entitati a dat eroare: " + sweepFailure);
        }
        getLogger().warning("[Wipe] " + count + " fisiere de date sterse + " + entities
                + " entitati mcv_* eliminate din lumile incarcate. Server curat, de la 0.");
        return true;
    }

    public boolean wasDataWipedThisBoot() {
        return dataWipedThisBoot;
    }

    @Override
    public void onEnable() {
        dataWipedThisBoot = wipeDataIfRequested();
        saveDefaultConfig();

        vehicleManager = new VehicleManager(this);
        gasPumpManager = new GasPumpManager(this, vehicleManager);
        garageStationManager = new com.mrcrayfish.vehicle.paper.garage.GarageStationManager(this, vehicleManager);

        // #1 FiveM: dealer shop. Vault probe FIRST - Vault classes load only when present.
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                com.mrcrayfish.vehicle.paper.shop.ShopEconomy.init(this);
                com.mrcrayfish.vehicle.paper.shop.VehicleShop.setEconomyReady(
                        com.mrcrayfish.vehicle.paper.shop.ShopEconomy.ready());
            } catch (Throwable vaultFailure) {
                getLogger().warning("[Magazin] Pornirea puntii Vault a esuat: " + vaultFailure);
            }
        } else {
            getLogger().warning("[Magazin] Vault NU este instalat - cumpararile sunt adormite "
                    + "pana instalezi Vault + un plugin de economie (EssentialsX).");
        }
        this.shop = new com.mrcrayfish.vehicle.paper.shop.VehicleShop(vehicleManager, getConfig());
        this.shopGui = new com.mrcrayfish.vehicle.paper.shop.ShopGui(this.shop);
        this.dealerStations = new com.mrcrayfish.vehicle.paper.shop.DealerStationManager(this, vehicleManager, this.shop);

        VehicleCommand vehicleCommand = new VehicleCommand(this, vehicleManager, gasPumpManager);
        PluginCommand command = getCommand("vehicle");
        if (command == null) {
            throw new IllegalStateException("Command 'vehicle' is missing from plugin.yml");
        }
        command.setExecutor(vehicleCommand);
        command.setTabCompleter(vehicleCommand);
        // /garaj - the one player-facing garage command (hybrid, location-aware).
        PluginCommand garajCommand = getCommand("garaj");
        if (garajCommand != null) {
            com.mrcrayfish.vehicle.paper.command.GarajCommand garaj =
                    new com.mrcrayfish.vehicle.paper.command.GarajCommand(this, vehicleManager);
            garajCommand.setExecutor(garaj);
            garajCommand.setTabCompleter(garaj);
        }
        getServer().getPluginManager().registerEvents(new VehicleListener(this, vehicleManager), this);
        getServer().getPluginManager().registerEvents(new GasPumpListener(gasPumpManager, vehicleManager), this);
        getServer().getPluginManager().registerEvents(new JerryCanListener(this, vehicleManager, gasPumpManager), this);
        getServer().getPluginManager().registerEvents(new com.mrcrayfish.vehicle.paper.listener.ShopListener(shop), this);
        getServer().getPluginManager().registerEvents(new com.mrcrayfish.vehicle.paper.listener.DealerPodiumListener(dealerStations, vehicleManager), this);
        getServer().getPluginManager().registerEvents(new com.mrcrayfish.vehicle.paper.listener.VehicleChunkListener(vehicleManager), this);

        vehicleManager.start();
        gasPumpManager.start();
        garageStationManager.start();
        dealerStations.start();

        restartWatcher = new RestartWatcher(this);
        restartWatcher.start();

        getLogger().info("Vehicle plugin enabled. Seventeen land vehicles, three water vehicles, three aircraft, five trailers, and the gas pump are ready.");
    }

    public com.mrcrayfish.vehicle.paper.shop.ShopGui shopGui() {
        return shopGui;
    }

    public com.mrcrayfish.vehicle.paper.shop.VehicleShop vehicleShop() {
        return shop;
    }

    public com.mrcrayfish.vehicle.paper.garage.GarageStationManager garageStations() {
        return garageStationManager;
    }

    public com.mrcrayfish.vehicle.paper.shop.DealerStationManager dealerStations() {
        return dealerStations;
    }

    @Override
    public void onDisable() {
        if (dealerStations != null) {
            dealerStations.stop();
        }

        if (restartWatcher != null) {
            restartWatcher.stop();
        }
        if (garageStationManager != null) {
            garageStationManager.stop();
        }
        if (gasPumpManager != null) {
            gasPumpManager.stop();
        }
        if (vehicleManager != null) {
            vehicleManager.stop();
        }
    }

    public VehicleManager vehicles() {
        return vehicleManager;
    }

    public GasPumpManager gasPumps() {
        return gasPumpManager;
    }
}
