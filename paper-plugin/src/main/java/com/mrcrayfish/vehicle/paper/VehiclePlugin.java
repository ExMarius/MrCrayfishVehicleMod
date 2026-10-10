package com.mrcrayfish.vehicle.paper;

import com.mrcrayfish.vehicle.paper.command.VehicleCommand;
import com.mrcrayfish.vehicle.paper.gaspump.GasPumpManager;
import com.mrcrayfish.vehicle.paper.listener.GasPumpListener;
import com.mrcrayfish.vehicle.paper.listener.VehicleListener;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VehiclePlugin extends JavaPlugin {
    private VehicleManager vehicleManager;
    private GasPumpManager gasPumpManager;

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
        getServer().getPluginManager().registerEvents(new GasPumpListener(gasPumpManager), this);

        vehicleManager.start();
        gasPumpManager.start();
        getLogger().info("Vehicle plugin enabled. Seventeen land vehicles, three water vehicles, three aircraft, five trailers, and the gas pump are ready.");
    }

    @Override
    public void onDisable() {
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
