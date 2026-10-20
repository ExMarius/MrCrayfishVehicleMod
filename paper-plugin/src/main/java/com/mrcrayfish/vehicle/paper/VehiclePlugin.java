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
        java.io.File[] entries = dir.listFiles();
        int count = 0;
        if (entries != null) {
            for (java.io.File entry : entries) {
                count += deleteRecursively(entry);
            }
        }
        getLogger().warning("[Wipe] " + count + " fisiere/foldere sterse din " + dir.getName() + ".");
        return true;
    }

    private int deleteRecursively(java.io.File file) {
        int removed = 0;
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    removed += deleteRecursively(child);
                }
            }
        }
        if (file.delete()) {
            removed++;
        }
        return removed;
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
        // Parking probe: quit + dismount ask whether a vehicle stands inside a LEGAL
        // circle (any public spot, or the owner's personal one) - else it is impounded.
        vehicleManager.setHomeStationProbe(garageStationManager::isLegallyParked);

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
