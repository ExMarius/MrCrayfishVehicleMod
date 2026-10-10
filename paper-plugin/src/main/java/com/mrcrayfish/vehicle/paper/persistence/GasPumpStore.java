package com.mrcrayfish.vehicle.paper.persistence;

import com.mrcrayfish.vehicle.paper.gaspump.GasPump;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** YAML persistence for placed gas pumps (location and facing only; fuel is free/instant). */
public final class GasPumpStore {
    private final JavaPlugin plugin;
    private final File file;

    public GasPumpStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gas_pumps.yml");
    }

    public List<StoredGasPump> loadAll() {
        List<StoredGasPump> pumps = new ArrayList<>();
        if (!file.isFile()) {
            return pumps;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("gas-pumps");
        if (root == null) {
            return pumps;
        }
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                pumps.add(new StoredGasPump(
                        UUID.fromString(key),
                        UUID.fromString(section.getString("world")),
                        section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                        (float) section.getDouble("yaw")));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid gas pump entry " + key, exception);
            }
        }
        return pumps;
    }

    public void saveAll(Iterable<GasPump> pumps) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (GasPump pump : pumps) {
            String path = "gas-pumps." + pump.id();
            yaml.set(path + ".world", pump.location().getWorld().getUID().toString());
            yaml.set(path + ".x", pump.location().getX());
            yaml.set(path + ".y", pump.location().getY());
            yaml.set(path + ".z", pump.location().getZ());
            yaml.set(path + ".yaw", pump.location().getYaw());
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save gas_pumps.yml", exception);
        }
    }
}
