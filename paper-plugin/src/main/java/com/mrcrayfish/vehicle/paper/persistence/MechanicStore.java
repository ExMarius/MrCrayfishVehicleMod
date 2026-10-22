package com.mrcrayfish.vehicle.paper.persistence;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stocare YAML (acelasi pattern ca GasPumpStore) pentru statiile mecanicului:
 * cricuri (cu starea ridicat/jos + vehiculul atasat), bancuri de lucru si props
 * (conuri, placute de boost, tevi). Un singur fisier, sectiuni separate.
 */
public final class MechanicStore {
    public record StoredJack(UUID id, UUID worldId, double x, double y, double z, float yaw,
                             boolean raised, UUID vehicleId) {
    }

    public record StoredStation(UUID id, String kind, UUID worldId,
                                double x, double y, double z, float yaw) {
    }

    private final JavaPlugin plugin;
    private final File file;

    public MechanicStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mechanics.yml");
    }

    public List<StoredJack> loadJacks() {
        List<StoredJack> jacks = new ArrayList<>();
        ConfigurationSection root = section("jacks");
        if (root == null) {
            return jacks;
        }
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection entry = root.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                String vehicleRaw = entry.getString("vehicle");
                jacks.add(new StoredJack(
                        UUID.fromString(key),
                        UUID.fromString(entry.getString("world")),
                        entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                        (float) entry.getDouble("yaw"),
                        entry.getBoolean("raised", false),
                        vehicleRaw == null || vehicleRaw.isEmpty() ? null : UUID.fromString(vehicleRaw)));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid jack entry " + key, exception);
            }
        }
        return jacks;
    }

    public List<StoredStation> loadStations(String rootName) {
        List<StoredStation> stations = new ArrayList<>();
        ConfigurationSection root = section(rootName);
        if (root == null) {
            return stations;
        }
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection entry = root.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                stations.add(new StoredStation(
                        UUID.fromString(key),
                        entry.getString("kind", ""),
                        UUID.fromString(entry.getString("world")),
                        entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                        (float) entry.getDouble("yaw")));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Skipping invalid " + rootName + " entry " + key, exception);
            }
        }
        return stations;
    }

    public void saveAll(Iterable<? extends Object> mixed) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Object entry : mixed) {
            if (entry instanceof StoredJack jack) {
                String path = "jacks." + jack.id();
                writeStation(yaml, path, jack.worldId(), jack.x(), jack.y(), jack.z(), jack.yaw());
                yaml.set(path + ".raised", jack.raised());
                yaml.set(path + ".vehicle",
                        jack.vehicleId() == null ? null : jack.vehicleId().toString());
            } else if (entry instanceof StoredStation station) {
                String section = sectionName(station.kind());
                if (section == null) {
                    continue;
                }
                writeStation(yaml, section + "." + station.id(),
                        station.worldId(), station.x(), station.y(), station.z(), station.yaw());
            }
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mechanics.yml", exception);
        }
    }

    private static void writeStation(YamlConfiguration yaml, String path, UUID worldId,
                                     double x, double y, double z, float yaw) {
        yaml.set(path + ".world", worldId.toString());
        yaml.set(path + ".x", x);
        yaml.set(path + ".y", y);
        yaml.set(path + ".z", z);
        yaml.set(path + ".yaw", yaw);
    }

    /** kind pe StoredStation: "workstation" | "traffic_cone" | "boost_pad" | "fluid_pipe" */
    public static String sectionName(String kind) {
        return switch (kind) {
            case "workstation" -> "workstations";
            case "traffic_cone", "boost_pad", "fluid_pipe" -> "props";
            default -> null;
        };
    }

    private ConfigurationSection section(String name) {
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return yaml.getConfigurationSection(name);
    }
}
