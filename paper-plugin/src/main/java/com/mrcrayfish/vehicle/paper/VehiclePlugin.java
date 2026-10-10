package com.mrcrayfish.vehicle.paper;

import com.mrcrayfish.vehicle.paper.command.VehicleCommand;
import com.mrcrayfish.vehicle.paper.economy.FuelEconomyService;
import com.mrcrayfish.vehicle.paper.economy.GasPumpManager;
import com.mrcrayfish.vehicle.paper.listener.VehicleListener;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VehiclePlugin extends JavaPlugin {
    private VehicleManager vehicleManager;
    private FuelEconomyService economyService;
    private GasPumpManager gasPumps;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        vehicleManager = new VehicleManager(this);
        economyService = FuelEconomyService.resolve(this);
        gasPumps = new GasPumpManager(this, vehicleManager, economyService);

        VehicleCommand vehicleCommand = new VehicleCommand(this, vehicleManager, gasPumps);
        PluginCommand command = getCommand("vehicle");
        if (command == null) {
            throw new IllegalStateException("Command 'vehicle' is missing from plugin.yml");
        }
        command.setExecutor(vehicleCommand);
        command.setTabCompleter(vehicleCommand);
        getServer().getPluginManager().registerEvents(new VehicleListener(this, vehicleManager, gasPumps), this);

        vehicleManager.start();
        gasPumps.start();
        getLogger().info("Vehicle plugin enabled. Seventeen land vehicles, three water vehicles, three aircraft, and five trailers are ready.");
    }

    @Override
    public void onDisable() {
        if (gasPumps != null) {
            gasPumps.stop();
        }
        if (vehicleManager != null) {
            vehicleManager.stop();
        }
    }

    public VehicleManager vehicles() {
        return vehicleManager;
    }

    public GasPumpManager gasPumps() {
        return gasPumps;
    }
}
