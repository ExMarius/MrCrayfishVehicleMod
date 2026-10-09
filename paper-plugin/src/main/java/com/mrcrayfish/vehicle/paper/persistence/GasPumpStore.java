package com.mrcrayfish.vehicle.paper.persistence;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** YAML persistence for admin-registered gas pump blocks. */
public final class GasPumpStore {
    private final JavaPlugin plugin;
    private final File file;

    public GasPumpStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gaspumps.yml");
    }

    public List<StoredGasPump> loadAll() {
        List<StoredGasPump> pumps = new ArrayList<>();
        if (!file.isFile()) {
            return pumps;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("pumps");
        if (root == null) {
            return pumps;
        }
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                // "facing" was added after the first release of this store; default to
                // NORTH for any pump saved before that so old data keeps loading fine.
                String facingName = section.getString("facing", BlockFace.NORTH.name());
                BlockFace facing;
                try {
                    facing = BlockFace.valueOf(facingName);
                } catch (IllegalArgumentException exception) {
                    facing = BlockFace.NORTH;
                }
                pumps.add(new StoredGasPump(
                        UUID.fromString(key),
                        UUID.fromString(section.getString("world")),
                        section.getInt("x"), section.getInt("y"), section.getInt("z"), facing));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid gas pump entry " + key, exception);
            }
        }
        return pumps;
    }

    public void saveAll(Iterable<StoredGasPump> pumps) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (StoredGasPump pump : pumps) {
            String path = "pumps." + pump.id();
            yaml.set(path + ".world", pump.worldId().toString());
            yaml.set(path + ".x", pump.x());
            yaml.set(path + ".y", pump.y());
            yaml.set(path + ".z", pump.z());
            yaml.set(path + ".facing", pump.facing().name());
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save gaspumps.yml", exception);
        }
    }
}
