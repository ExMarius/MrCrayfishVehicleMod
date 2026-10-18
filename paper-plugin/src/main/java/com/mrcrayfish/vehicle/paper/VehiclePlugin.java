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
    private VehicleManager vehicleManager;
    private GasPumpManager gasPumpManager;
    private RestartWatcher restartWatcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        vehicleManager = new VehicleManager(this);
        gasPumpManager = new GasPumpManager(this, vehicleManager);

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

        vehicleManager.start();
        gasPumpManager.start();

        restartWatcher = new RestartWatcher(this);
        restartWatcher.start();

        getLogger().info("Vehicle plugin enabled. Seventeen land vehicles, three water vehicles, three aircraft, five trailers, and the gas pump are ready.");
    }

    @Override
    public void onDisable() {
        if (restartWatcher != null) {
            restartWatcher.stop();
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
