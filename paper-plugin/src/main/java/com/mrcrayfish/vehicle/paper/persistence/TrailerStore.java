package com.mrcrayfish.vehicle.paper.persistence;

import com.mrcrayfish.vehicle.paper.runtime.PaperTrailer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** YAML persistence for trailer links, tanks, transported vehicles and inventories. */
public final class TrailerStore {
    private final JavaPlugin plugin;
    private final File file;

    public TrailerStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trailers.yml");
    }

    public List<StoredTrailer> loadAll() {
        List<StoredTrailer> trailers = new ArrayList<>();
        if (!file.isFile()) {
            return trailers;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("trailers");
        if (root == null) {
            return trailers;
        }
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                List<?> list = section.getList("inventory", List.of());
                ItemStack[] inventory = list.stream()
                        .map(value -> value instanceof ItemStack stack ? stack : null)
                        .toArray(ItemStack[]::new);
                trailers.add(new StoredTrailer(
                        UUID.fromString(key),
                        section.getString("type", ""),
                        UUID.fromString(section.getString("world")),
                        section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                        (float) section.getDouble("yaw"),
                        parsePullerType(section.getString("puller-type")),
                        parseUuid(section.getString("puller-id")),
                        parseUuid(section.getString("loaded-vehicle-id")),
                        parseMaterial(section.getString("fluid-material")),
                        section.getInt("fluid-amount"), inventory));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid trailer entry " + key, exception);
            }
        }
        return trailers;
    }

    public void saveAll(Iterable<PaperTrailer> trailers) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PaperTrailer trailer : trailers) {
            String path = "trailers." + trailer.id();
            yaml.set(path + ".type", trailer.spec().id());
            yaml.set(path + ".world", trailer.location().getWorld().getUID().toString());
            yaml.set(path + ".x", trailer.location().getX());
            yaml.set(path + ".y", trailer.location().getY());
            yaml.set(path + ".z", trailer.location().getZ());
            yaml.set(path + ".yaw", trailer.location().getYaw());
            yaml.set(path + ".puller-type", trailer.pullerType().name());
            yaml.set(path + ".puller-id", asString(trailer.pullerId()));
            yaml.set(path + ".loaded-vehicle-id", asString(trailer.loadedVehicleId()));
            yaml.set(path + ".fluid-material", trailer.fluidMaterial() == null ? null : trailer.fluidMaterial().name());
            yaml.set(path + ".fluid-amount", trailer.fluidAmount());
            yaml.set(path + ".inventory", Arrays.asList(trailer.inventoryContents()));
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save trailers.yml", exception);
        }
    }

    private static PaperTrailer.PullerType parsePullerType(String value) {
        try {
            return value == null ? PaperTrailer.PullerType.NONE : PaperTrailer.PullerType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return PaperTrailer.PullerType.NONE;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Material parseMaterial(String value) {
        return value == null ? null : Material.matchMaterial(value);
    }

    private static String asString(UUID id) {
        return id == null ? null : id.toString();
    }
}
