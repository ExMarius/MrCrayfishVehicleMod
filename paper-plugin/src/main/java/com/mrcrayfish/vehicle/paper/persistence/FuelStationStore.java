package com.mrcrayfish.vehicle.paper.persistence;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** YAML persistence for admin-registered fuel stations, one file per server (fuelstations.yml). */
public final class FuelStationStore {
    private static final String ROOT_KEY = "stations";

    private final JavaPlugin plugin;
    private final File file;

    public FuelStationStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fuelstations.yml");
    }

    public List<StoredFuelStation> loadAll() {
        List<StoredFuelStation> stations = new ArrayList<>();
        if (!file.isFile()) {
            return stations;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection(ROOT_KEY);
        if (root == null) {
            return stations;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                UUID worldId = UUID.fromString(section.getString("world"));
                BlockFace facing = BlockFace.valueOf(section.getString("facing", "NORTH"));
                stations.add(new StoredFuelStation(id, worldId,
                        section.getInt("x"), section.getInt("y"), section.getInt("z"), facing));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid fuel station entry " + key, exception);
            }
        }
        return stations;
    }

    public void saveAll(Collection<StoredFuelStation> stations) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (StoredFuelStation station : stations) {
            String path = ROOT_KEY + "." + station.id();
            yaml.set(path + ".world", station.worldId().toString());
            yaml.set(path + ".x", station.x());
            yaml.set(path + ".y", station.y());
            yaml.set(path + ".z", station.z());
            yaml.set(path + ".facing", station.facing().name());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save fuelstations.yml", exception);
        }
    }
}
