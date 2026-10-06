package com.mrcrayfish.vehicle.paper.vehicle;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.render.LandVehicleRig;
import com.mrcrayfish.vehicle.paper.runtime.PaperTrailer;
import com.mrcrayfish.vehicle.paper.runtime.TrailerManager;
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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class VehicleManager {
    private final VehiclePlugin plugin;
    private final Map<UUID, LandVehicle> vehicles = new HashMap<>();
    private final Map<UUID, LandVehicle> entities = new HashMap<>();
    private final Map<UUID, StoredVehicle> pendingWorlds = new HashMap<>();
    private final Map<UUID, VehicleChunk> chunkTickets = new HashMap<>();
    private final File storageFile;
    private final TrailerManager trailers;
    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private int activationTick;

    public VehicleManager(VehiclePlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "vehicles.yml");
        this.trailers = new TrailerManager(plugin, this);
    }

    public void start() {
        cleanupOrphanedEntities();
        load();
        trailers.start();
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
        trailers.returnCarriedVehicles();
        save();
        trailers.stop();
        for (LandVehicle vehicle : new ArrayList<>(vehicles.values())) {
            releaseChunkTicket(vehicle);
            vehicle.remove();
        }
        vehicles.clear();
        entities.clear();
        chunkTickets.clear();
    }

    public LandVehicle spawn(LandVehicleSpec spec, Location location) {
        return spawn(UUID.randomUUID(), spec, location);
    }

    public PaperTrailer spawn(TrailerSpec spec, Location location) {
        return trailers.spawn(spec, location);
    }

    private LandVehicle spawn(UUID id, LandVehicleSpec spec, Location location) {
        LandVehicle vehicle = LandVehicle.spawn(plugin, id, location, spec, trailers);
        vehicles.put(id, vehicle);
        pendingWorlds.remove(id);
        index(vehicle);
        updateChunkTicket(vehicle);
        return vehicle;
    }

    public CarriedVehicle pickUp(LandVehicle vehicle) {
        if (vehicle.occupied() || vehicles.remove(vehicle.id()) == null) {
            return null;
        }
        CarriedVehicle carried = new CarriedVehicle(vehicle.spec(), vehicle.fuel(), vehicle.velocity(),
                vehicle.traction(), vehicle.verticalVelocity());
        unindex(vehicle);
        releaseChunkTicket(vehicle);
        trailers.onVehicleRemoved(vehicle.id());
        vehicle.remove();
        return carried;
    }

    public LandVehicle place(CarriedVehicle carried, Location location) {
        LandVehicle vehicle = spawn(carried.spec(), location);
        vehicle.setFuel(carried.fuel());
        vehicle.setVelocity(carried.velocity());
        vehicle.setTraction(carried.traction());
        vehicle.setVerticalVelocity(carried.verticalVelocity());
        return vehicle;
    }

    public boolean remove(LandVehicle vehicle) {
        if (vehicles.remove(vehicle.id()) == null) {
            return false;
        }
        unindex(vehicle);
        releaseChunkTicket(vehicle);
        trailers.onVehicleRemoved(vehicle.id());
        vehicle.remove();
        save();
        return true;
    }

    public Optional<LandVehicle> byEntity(Entity entity) {
        return Optional.ofNullable(entities.get(entity.getUniqueId()));
    }

    public Optional<LandVehicle> byId(UUID id) {
        return Optional.ofNullable(vehicles.get(id));
    }

    public boolean hasVehicleId(UUID id) {
        return vehicles.containsKey(id) || pendingWorlds.containsKey(id);
    }

    public TrailerManager trailers() {
        return trailers;
    }

    public Optional<LandVehicle> nearest(Location origin, double maximumDistance) {
        if (origin.getWorld() == null) {
            return Optional.empty();
        }
        LandVehicle nearest = null;
        double nearestDistance = maximumDistance * maximumDistance;
        for (LandVehicle vehicle : vehicles.values()) {
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

    public Collection<LandVehicle> vehicles() {
        return java.util.Collections.unmodifiableCollection(vehicles.values());
    }

    public int pendingVehicleCount() {
        return pendingWorlds.size();
    }

    public void handleInteraction(Player player, Entity clicked) {
        LandVehicle vehicle = entities.get(clicked.getUniqueId());
        if (vehicle == null) {
            return;
        }
        if (!player.hasPermission("vehicle.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea vehicle.use.</red>");
            return;
        }
        if (trailers.attachHeldToVehicle(player, vehicle)) {
            return;
        }
        if (player.isSneaking() && trailers.pickUpVehicle(player, vehicle)) {
            return;
        }
        if (vehicle.mount(player)) {
            player.sendRichMessage("<gray>W/S accelerație, A/D direcție, Space frână de mână, Shift coborâre.</gray>");
        } else {
            player.sendRichMessage("<red>Nu mai este niciun loc liber în acest vehicul.</red>");
        }
    }

    /** Save to a same-directory temporary file and atomically replace the last good snapshot. */
    public void save() {
        trailers.save();
        YamlConfiguration data = new YamlConfiguration();
        for (StoredVehicle stored : pendingWorlds.values()) {
            write(data, stored);
        }
        for (LandVehicle vehicle : vehicles.values()) {
            write(data, StoredVehicle.from(vehicle));
        }

        File temporary = null;
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            temporary = File.createTempFile("vehicles-", ".yml.tmp", plugin.getDataFolder());
            data.save(temporary);
            try {
                Files.move(temporary.toPath(), storageFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not atomically save vehicles.yml", exception);
        } finally {
            if (temporary != null && temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }

    private static void write(YamlConfiguration data, StoredVehicle stored) {
        String path = "vehicles." + stored.id();
        data.set(path + ".type", stored.type());
        data.set(path + ".world", stored.worldId());
        data.set(path + ".world-name", stored.worldName());
        data.set(path + ".x", stored.x());
        data.set(path + ".y", stored.y());
        data.set(path + ".z", stored.z());
        data.set(path + ".yaw", stored.yaw());
        data.set(path + ".fuel", stored.fuel());
        data.set(path + ".velocity.x", stored.velocityX());
        data.set(path + ".velocity.y", stored.velocityY());
        data.set(path + ".velocity.z", stored.velocityZ());
        data.set(path + ".traction", stored.traction());
        data.set(path + ".vertical-velocity", stored.verticalVelocity());
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
                StoredVehicle stored = StoredVehicle.read(data, key);
                LandVehicleSpec spec = LandVehicleSpec.byId(stored.type());
                pendingWorlds.put(stored.id(), stored);
                if (spec == null) {
                    plugin.getLogger().warning("Keeping unsupported vehicle " + stored.id()
                            + " in storage until its type is implemented: " + stored.type());
                } else if (world(stored.worldId(), stored.worldName()) == null) {
                    plugin.getLogger().warning("Deferring vehicle " + stored.id() + ": its world is not loaded");
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not load vehicle entry " + key, exception);
            }
        }
        plugin.getLogger().info("Loaded " + vehicles.size() + " vehicle(s); "
                + pendingWorlds.size() + " deferred for unavailable worlds/types.");
    }

    public void onWorldLoaded(World loadedWorld) {
        activateNearbyVehicles(loadedWorld);
    }

    private void restore(StoredVehicle stored, LandVehicleSpec spec, World world) {
        Location location = new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), 0.0F);
        LandVehicle vehicle = spawn(stored.id(), spec, location);
        vehicle.setFuel(stored.fuel());
        vehicle.setVelocity(new Vector(stored.velocityX(), stored.velocityY(), stored.velocityZ()));
        vehicle.setTraction(stored.traction());
        vehicle.setVerticalVelocity(stored.verticalVelocity());
    }

    private static boolean matchesWorld(StoredVehicle stored, World world) {
        return world.getUID().toString().equals(stored.worldId()) || world.getName().equals(stored.worldName());
    }

    private World world(String uuid, String name) {
        if (uuid != null) {
            try {
                World world = Bukkit.getWorld(UUID.fromString(uuid));
                if (world != null) {
                    return world;
                }
            } catch (IllegalArgumentException ignored) {
                // Try the name fallback below.
            }
        }
        return name == null ? null : Bukkit.getWorld(name);
    }

    private void tick() {
        if (++activationTick % 10 == 0) {
            activateNearbyVehicles(null);
        }
        double globalSpeedLimit = plugin.getConfig().getDouble("physics.global-speed-limit", 100.0D);
        double fuelFactor = plugin.getConfig().getDouble("physics.fuel-consumption-factor", 1.0D);
        double activationDistance = Math.max(16.0D,
                plugin.getConfig().getDouble("performance.activation-distance", 64.0D));
        for (LandVehicle vehicle : new ArrayList<>(vehicles.values())) {
            try {
                if (!vehicle.rig().valid()) {
                    hibernate(vehicle);
                    continue;
                }
                vehicle.tick(globalSpeedLimit, fuelFactor);
                if (vehicle.resting()) {
                    releaseChunkTicket(vehicle);
                    if (!trailers.referencesVehicle(vehicle.id())
                            && !hasNearbyPlayer(vehicle.location(), activationDistance)) {
                        hibernate(vehicle);
                    }
                } else {
                    updateChunkTicket(vehicle);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vehicle tick failed for " + vehicle.id(), exception);
                hibernate(vehicle);
            }
        }
        trailers.tick();
    }

    private void activateNearbyVehicles(World onlyWorld) {
        double activationDistance = Math.max(16.0D,
                plugin.getConfig().getDouble("performance.activation-distance", 64.0D));
        for (StoredVehicle stored : new ArrayList<>(pendingWorlds.values())) {
            LandVehicleSpec spec = LandVehicleSpec.byId(stored.type());
            World world = world(stored.worldId(), stored.worldName());
            if (spec == null || world == null || (onlyWorld != null && !world.equals(onlyWorld))
                    || !matchesWorld(stored, world)) {
                continue;
            }
            Location location = new Location(world, stored.x(), stored.y(), stored.z());
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || !hasNearbyPlayer(location, activationDistance)) {
                continue;
            }
            try {
                restore(stored, spec, world);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not activate vehicle " + stored.id(), exception);
            }
        }
    }

    private static boolean hasNearbyPlayer(Location location, double distance) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        double maximumSquared = distance * distance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= maximumSquared) {
                return true;
            }
        }
        return false;
    }

    private void hibernate(LandVehicle vehicle) {
        if (vehicles.remove(vehicle.id()) == null) {
            return;
        }
        try {
            pendingWorlds.put(vehicle.id(), StoredVehicle.from(vehicle));
        } finally {
            unindex(vehicle);
            releaseChunkTicket(vehicle);
            vehicle.remove();
        }
    }

    private void index(LandVehicle vehicle) {
        for (Entity entity : vehicle.rig().entities()) {
            entities.put(entity.getUniqueId(), vehicle);
        }
    }

    private void unindex(LandVehicle vehicle) {
        for (Entity entity : vehicle.rig().entities()) {
            entities.remove(entity.getUniqueId());
        }
    }

    private void updateChunkTicket(LandVehicle vehicle) {
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

    private void releaseChunkTicket(LandVehicle vehicle) {
        VehicleChunk chunk = chunkTickets.remove(vehicle.id());
        if (chunk != null) {
            chunk.world().removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
        }
    }

    private void cleanupOrphanedEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(LandVehicleRig.ENTITY_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " orphaned vehicle display entities.");
        }
    }

    public record CarriedVehicle(LandVehicleSpec spec, float fuel, Vector velocity,
                                 float traction, double verticalVelocity) {
    }

    private record VehicleChunk(World world, int x, int z) {
    }

    private record StoredVehicle(UUID id, String type, String worldId, String worldName,
                                 double x, double y, double z, float yaw, float fuel,
                                 double velocityX, double velocityY, double velocityZ,
                                 float traction, double verticalVelocity) {
        private static StoredVehicle from(LandVehicle vehicle) {
            Location location = vehicle.location();
            World world = location.getWorld();
            if (world == null) {
                throw new IllegalStateException("Cannot persist a vehicle without a world");
            }
            Vector velocity = vehicle.velocity();
            return new StoredVehicle(vehicle.id(), vehicle.spec().id(), world.getUID().toString(), world.getName(),
                    location.getX(), location.getY(), location.getZ(), location.getYaw(), vehicle.fuel(),
                    velocity.getX(), velocity.getY(), velocity.getZ(), vehicle.traction(), vehicle.verticalVelocity());
        }

        private static StoredVehicle read(YamlConfiguration data, String key) {
            String path = "vehicles." + key;
            UUID id = UUID.fromString(key);
            String type = data.getString(path + ".type", "go_kart");
            String worldId = data.getString(path + ".world");
            String worldName = data.getString(path + ".world-name");
            LandVehicleSpec spec = LandVehicleSpec.byId(type);
            float defaultFuel = spec == null ? 0.0F : spec.energyCapacity();
            return new StoredVehicle(id, type, worldId, worldName,
                    data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"),
                    (float) data.getDouble(path + ".yaw"),
                    (float) data.getDouble(path + ".fuel", defaultFuel),
                    data.getDouble(path + ".velocity.x"), data.getDouble(path + ".velocity.y"),
                    data.getDouble(path + ".velocity.z"),
                    (float) data.getDouble(path + ".traction"),
                    data.getDouble(path + ".vertical-velocity"));
        }
    }
}
