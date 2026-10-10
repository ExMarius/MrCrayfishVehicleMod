package com.mrcrayfish.vehicle.paper;

import com.mrcrayfish.vehicle.paper.command.VehicleCommand;
import com.mrcrayfish.vehicle.paper.economy.FuelAccountService;
import com.mrcrayfish.vehicle.paper.listener.VehicleListener;
import com.mrcrayfish.vehicle.paper.station.FuelStationManager;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VehiclePlugin extends JavaPlugin {
    private VehicleManager vehicleManager;
    private FuelStationManager fuelStations;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        vehicleManager = new VehicleManager(this);
        FuelAccountService account = FuelAccountService.resolve(this);
        fuelStations = new FuelStationManager(this, vehicleManager, account);

        VehicleCommand vehicleCommand = new VehicleCommand(this, vehicleManager, fuelStations);
        PluginCommand command = getCommand("vehicle");
        if (command == null) {
            throw new IllegalStateException("Command 'vehicle' is missing from plugin.yml");
        }
        command.setExecutor(vehicleCommand);
        command.setTabCompleter(vehicleCommand);
        getServer().getPluginManager().registerEvents(new VehicleListener(this, vehicleManager, fuelStations), this);

        vehicleManager.start();
        fuelStations.start();
        getLogger().info("Vehicle plugin enabled. Seventeen land vehicles, three water vehicles, three aircraft, and five trailers are ready.");
    }

    @Override
    public void onDisable() {
        if (fuelStations != null) {
            fuelStations.stop();
        }
        if (vehicleManager != null) {
            vehicleManager.stop();
        }
    }

    public VehicleManager vehicles() {
        return vehicleManager;
    }

    public FuelStationManager fuelStations() {
        return fuelStations;
    }
}
