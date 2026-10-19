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
    private VehicleManager vehicleManager;
    private GasPumpManager gasPumpManager;
    private com.mrcrayfish.vehicle.paper.garage.GarageStationManager garageStationManager;
    private RestartWatcher restartWatcher;

    @Override
    public void onEnable() {
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
        getServer().getPluginManager().registerEvents(new com.mrcrayfish.vehicle.paper.listener.GarageListener(this, vehicleManager), this);
        getServer().getPluginManager().registerEvents(new com.mrcrayfish.vehicle.paper.listener.GarageStationListener(garageStationManager, vehicleManager), this);
        getServer().getPluginManager().registerEvents(new com.mrcrayfish.vehicle.paper.listener.ShopListener(shop), this);

        vehicleManager.start();
        gasPumpManager.start();
        garageStationManager.start();

        restartWatcher = new RestartWatcher(this);
        restartWatcher.start();

        getLogger().info("Vehicle plugin enabled. Seventeen land vehicles, three water vehicles, three aircraft, five trailers, and the gas pump are ready.");
    }

    public com.mrcrayfish.vehicle.paper.shop.ShopGui shopGui() {
        return shopGui;
    }

    @Override
    public void onDisable() {
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
