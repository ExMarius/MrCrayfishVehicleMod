package com.mrcrayfish.vehicle.paper;

import com.mrcrayfish.vehicle.paper.command.VehicleCommand;
import com.mrcrayfish.vehicle.paper.listener.VehicleListener;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VehiclePlugin extends JavaPlugin {
    private VehicleManager vehicleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        vehicleManager = new VehicleManager(this);
        VehicleCommand vehicleCommand = new VehicleCommand(this, vehicleManager);
        PluginCommand command = getCommand("vehicle");
        if (command == null) {
            throw new IllegalStateException("Command 'vehicle' is missing from plugin.yml");
        }
        command.setExecutor(vehicleCommand);
        command.setTabCompleter(vehicleCommand);
        getServer().getPluginManager().registerEvents(new VehicleListener(this, vehicleManager), this);

        vehicleManager.start();
        getLogger().info("Vehicle plugin enabled. Ten land vehicles, one water vehicle, and five trailers are ready.");
    }

    @Override
    public void onDisable() {
        if (vehicleManager != null) {
            vehicleManager.stop();
        }
    }

    public VehicleManager vehicles() {
        return vehicleManager;
    }
}
