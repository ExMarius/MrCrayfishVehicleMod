package com.mrcrayfish.vehicle.paper.vehicle;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.render.GoKartRig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class VehicleManager {
    private final VehiclePlugin plugin;
    private final Map<UUID, GoKart> vehicles = new HashMap<>();
    private final Map<UUID, GoKart> entities = new HashMap<>();
    private final Map<UUID, VehicleChunk> chunkTickets = new HashMap<>();
    private final File storageFile;
    private BukkitTask tickTask;
    private BukkitTask saveTask;

    public VehicleManager(VehiclePlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "vehicles.yml");
    }

    public void start() {
        cleanupOrphanedEntities();
        load();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        long autosave = Math.max(30L, plugin.getConfig().getLong("persistence.autosave-seconds", 300L));
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, autosave * 20L, autosave * 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        save();
        for (GoKart vehicle : new ArrayList<>(vehicles.values())) {
            releaseChunkTicket(vehicle);
            vehicle.remove();
        }
        vehicles.clear();
        entities.clear();
        chunkTickets.clear();
    }

    public GoKart spawn(Location location) {
        return spawn(UUID.randomUUID(), location);
    }

    private GoKart spawn(UUID id, Location location) {
        GoKart vehicle = GoKart.spawn(plugin, id, location);
        vehicles.put(id, vehicle);
        index(vehicle);
        updateChunkTicket(vehicle);
        return vehicle;
    }

    public boolean remove(GoKart vehicle) {
        if (vehicles.remove(vehicle.id()) == null) {
            return false;
        }
        for (Entity entity : vehicle.rig().entities()) {
            entities.remove(entity.getUniqueId());
        }
        releaseChunkTicket(vehicle);
        vehicle.remove();
        save();
        return true;
    }

    public Optional<GoKart> byEntity(Entity entity) {
        return Optional.ofNullable(entities.get(entity.getUniqueId()));
    }

    public Optional<GoKart> nearest(Location origin, double maximumDistance) {
        if (origin.getWorld() == null) {
            return Optional.empty();
        }
        GoKart nearest = null;
        double nearestDistance = maximumDistance * maximumDistance;
        for (GoKart vehicle : vehicles.values()) {
            Location location = vehicle.location();
            if (location.getWorld() == null || !location.getWorld().equals(origin.getWorld())) {
                continue;
            }
            double distance = location.distanceSquared(origin);
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = vehicle;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Collection<GoKart> vehicles() {
        return java.util.Collections.unmodifiableCollection(vehicles.values());
    }

    public void handleInteraction(Player player, Entity clicked) {
        GoKart vehicle = entities.get(clicked.getUniqueId());
        if (vehicle == null) {
            return;
        }
        if (!player.hasPermission("vehicle.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea vehicle.use.</red>");
            return;
        }
        if (player.isSneaking() && player.hasPermission("vehicle.admin")) {
            remove(vehicle);
            player.sendRichMessage("<green>Vehicul eliminat.</green>");
            return;
        }
        if (vehicle.mount(player)) {
            player.sendRichMessage("<gray>W/S accelerație, A/D direcție, Space frână de mână, Shift coborâre.</gray>");
        } else {
            player.sendRichMessage("<red>Acest vehicul are deja un șofer.</red>");
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (GoKart vehicle : vehicles.values()) {
            Location location = vehicle.location();
            if (location.getWorld() == null) {
                continue;
            }
            String path = "vehicles." + vehicle.id();
            data.set(path + ".type", "go_kart");
            data.set(path + ".world", location.getWorld().getUID().toString());
            data.set(path + ".world-name", location.getWorld().getName());
            data.set(path + ".x", location.getX());
            data.set(path + ".y", location.getY());
            data.set(path + ".z", location.getZ());
            data.set(path + ".yaw", location.getYaw());
            data.set(path + ".fuel", vehicle.fuel());
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            data.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save vehicles.yml", exception);
        }
    }

    private void load() {
        if (!storageFile.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection section = data.getConfigurationSection("vehicles");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                String path = "vehicles." + key;
                UUID id = UUID.fromString(key);
                World world = world(data.getString(path + ".world"), data.getString(path + ".world-name"));
                if (world == null) {
                    plugin.getLogger().warning("Skipping vehicle " + id + ": its world is not loaded");
                    continue;
                }
                Location location = new Location(world,
                        data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"),
                        (float) data.getDouble(path + ".yaw"), 0.0F);
                GoKart vehicle = spawn(id, location);
                vehicle.setFuel((float) data.getDouble(path + ".fuel", GoKart.ENERGY_CAPACITY));
                vehicle.setVelocity(new Vector());
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not load vehicle entry " + key, exception);
            }
        }
        plugin.getLogger().info("Loaded " + vehicles.size() + " persisted vehicle(s).");
    }

    private World world(String uuid, String name) {
        if (uuid != null) {
            try {
                World world = Bukkit.getWorld(UUID.fromString(uuid));
                if (world != null) {
                    return world;
                }
            } catch (IllegalArgumentException ignored) {
                // Try the legacy/name fallback below.
            }
        }
        return name == null ? null : Bukkit.getWorld(name);
    }

    private void tick() {
        double globalSpeedLimit = plugin.getConfig().getDouble("physics.global-speed-limit", 100.0D);
        double fuelFactor = plugin.getConfig().getDouble("physics.fuel-consumption-factor", 1.0D);
        for (GoKart vehicle : new ArrayList<>(vehicles.values())) {
            try {
                if (!vehicle.rig().valid()) {
                    remove(vehicle);
                    continue;
                }
                vehicle.tick(globalSpeedLimit, fuelFactor);
                updateChunkTicket(vehicle);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vehicle tick failed for " + vehicle.id(), exception);
                remove(vehicle);
            }
        }
    }

    private void index(GoKart vehicle) {
        for (Entity entity : vehicle.rig().entities()) {
            entities.put(entity.getUniqueId(), vehicle);
        }
    }

    private void updateChunkTicket(GoKart vehicle) {
        Location location = vehicle.location();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        VehicleChunk next = new VehicleChunk(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
        VehicleChunk previous = chunkTickets.get(vehicle.id());
        if (next.equals(previous)) {
            return;
        }
        next.world().addPluginChunkTicket(next.x(), next.z(), plugin);
        if (previous != null) {
            previous.world().removePluginChunkTicket(previous.x(), previous.z(), plugin);
        }
        chunkTickets.put(vehicle.id(), next);
    }

    private void releaseChunkTicket(GoKart vehicle) {
        VehicleChunk chunk = chunkTickets.remove(vehicle.id());
        if (chunk != null) {
            chunk.world().removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
        }
    }

    private void cleanupOrphanedEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(GoKartRig.ENTITY_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " orphaned vehicle display entities.");
        }
    }

    private record VehicleChunk(World world, int x, int z) {
    }
}
