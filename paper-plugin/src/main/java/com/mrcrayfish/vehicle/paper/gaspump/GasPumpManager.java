package com.mrcrayfish.vehicle.paper.gaspump;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.GasPumpStore;
import com.mrcrayfish.vehicle.paper.persistence.StoredGasPump;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registry, persistence and per-tick behaviour for placed gas pumps: nozzle
 * pickup/put-down, free/instant fuel transfer while the nozzle is held and
 * aimed near a vehicle, and keeping the hose/nozzle displays in sync.
 *
 * <p>The original mod drains a real {@code FluidTank} and requires the player
 * to have filled it with a bucket first. There is no vanilla/Paper equivalent
 * to Forge's fluid capability system, so (per explicit product decision) this
 * version dispenses fuel for free and without limit; a future, separate
 * "fluid trailer" feature is expected to eventually gate the supply instead.
 */
public final class GasPumpManager {
    private static final double DEFAULT_MAX_HOSE_DISTANCE = 10.0D;
    private static final double DEFAULT_FILL_SECONDS = 15.0D;
    private static final double FUEL_SEARCH_RADIUS = 4.0D;
    private static final int GLUG_INTERVAL_TICKS = 20;

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final GasPumpStore store;
    private final Map<UUID, GasPump> pumps = new HashMap<>();
    private final Map<UUID, GasPump> byInteraction = new HashMap<>();
    private BukkitTask tickTask;
    private BukkitTask saveTask;

    public GasPumpManager(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.store = new GasPumpStore(plugin);
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
        for (GasPump pump : new ArrayList<>(pumps.values())) {
            pump.rig().remove();
        }
        pumps.clear();
        byInteraction.clear();
    }

    private void cleanupOrphanedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(GasPumpRig.ENTITY_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private void load() {
        for (StoredGasPump stored : store.loadAll()) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            Location location = new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), 0.0F);
            GasPumpRig rig = GasPumpRig.spawn(stored.id(), location);
            GasPump pump = new GasPump(stored.id(), location, rig);
            pumps.put(pump.id(), pump);
            byInteraction.put(rig.interaction().getUniqueId(), pump);
        }
    }

    public void save() {
        store.saveAll(pumps.values());
    }

    /** Validates that both target cells are empty before placement, mirroring {@code GasPumpBlock#canSurvive}. */
    public boolean canPlace(Location bottom) {
        World world = bottom.getWorld();
        if (world == null) {
            return false;
        }
        Location top = bottom.clone().add(0.0D, 1.0D, 0.0D);
        return world.getBlockAt(bottom).getType().isAir() && world.getBlockAt(top).getType().isAir();
    }

    public GasPump place(Location bottom, float yaw) {
        UUID id = UUID.randomUUID();
        Location root = bottom.clone();
        root.setYaw(snapYaw(yaw));
        root.setPitch(0.0F);
        GasPumpRig rig = GasPumpRig.spawn(id, root);
        GasPump pump = new GasPump(id, root, rig);
        pumps.put(id, pump);
        byInteraction.put(rig.interaction().getUniqueId(), pump);
        save();
        return pump;
    }

    public void remove(GasPump pump) {
        pumps.remove(pump.id());
        byInteraction.remove(pump.rig().interaction().getUniqueId());
        pump.rig().remove();
        save();
    }

    public Optional<GasPump> byEntity(Entity entity) {
        return Optional.ofNullable(byInteraction.get(entity.getUniqueId()));
    }

    public Collection<GasPump> pumps() {
        return Collections.unmodifiableCollection(pumps.values());
    }

    public void pickUpNozzle(Player player, GasPump pump) {
        pump.setHolderId(player.getUniqueId());
        Location location = pump.location();
        player.playSound(Sound.sound(Key.key("vehicle:block.gas_pump.nozzle_pick_up"),
                Sound.Source.BLOCK, 1.0F, 1.0F), location.getX(), location.getY(), location.getZ());
    }

    public void putDownNozzle(GasPump pump) {
        pump.setHolderId(null);
        pump.rig().update(pump.location(), null, null);
        Location soundLocation = pump.location();
        for (Player nearby : soundLocation.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(soundLocation) <= 64.0D * 64.0D) {
                nearby.playSound(Sound.sound(Key.key("vehicle:block.gas_pump.nozzle_put_down"),
                        Sound.Source.BLOCK, 1.0F, 1.0F),
                        soundLocation.getX(), soundLocation.getY(), soundLocation.getZ());
            }
        }
    }

    private void tick() {
        double maxHoseDistance = plugin.getConfig().getDouble("gas-pump.max-hose-distance", DEFAULT_MAX_HOSE_DISTANCE);
        double fillSeconds = Math.max(1.0D, plugin.getConfig().getDouble("gas-pump.fill-seconds", DEFAULT_FILL_SECONDS));
        for (GasPump pump : new ArrayList<>(pumps.values())) {
            if (!pump.rig().valid()) {
                continue;
            }
            if (!pump.hasHolder()) {
                continue;
            }
            Player holder = Bukkit.getPlayer(pump.holderId());
            if (holder == null || !holder.isOnline()
                    || holder.getWorld() != pump.location().getWorld()
                    || holder.getLocation().distanceSquared(pump.location()) > maxHoseDistance * maxHoseDistance) {
                putDownNozzle(pump);
                continue;
            }

            Location nozzleLocation = handHeldNozzleLocation(holder);
            pump.rig().update(pump.location(), nozzleLocation, holder.getEyeLocation().getDirection());

            fuel(pump, holder, fillSeconds);
        }
    }

    private void fuel(GasPump pump, Player holder, double fillSeconds) {
        Optional<LandVehicle> target = vehicles.nearest(holder.getEyeLocation(), FUEL_SEARCH_RADIUS);
        if (target.isEmpty()) {
            return;
        }
        LandVehicle vehicle = target.get();
        float capacity = vehicle.spec().energyCapacity();
        if (vehicle.fuel() >= capacity) {
            return;
        }
        float ratePerTick = (float) (capacity / (fillSeconds * 20.0D));
        vehicle.setFuel(Math.min(capacity, vehicle.fuel() + ratePerTick));

        int cooldown = pump.glugCooldown() - 1;
        if (cooldown <= 0) {
            // FuelingHandler plays this exact sample every 20 ticks while fuel is
            // actively flowing (originally the Jerry Can's own glug sound).
            Location fuelLocation = vehicle.location();
            holder.playSound(Sound.sound(Key.key("vehicle:item.jerry_can.liquid_glug"),
                    Sound.Source.PLAYER, 0.6F, 1.0F),
                    fuelLocation.getX(), fuelLocation.getY(), fuelLocation.getZ());
            cooldown = GLUG_INTERVAL_TICKS;
        }
        pump.setGlugCooldown(cooldown);
    }

    /** Rough stand-in for the original's per-perspective arm/hip attachment math. */
    private static Location handHeldNozzleLocation(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        Vector right = direction.clone().crossProduct(new Vector(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            right.normalize();
        }
        return eye.clone()
                .subtract(0.0D, 0.4D, 0.0D)
                .add(direction.clone().multiply(0.35D))
                .add(right.multiply(0.3D));
    }

    /** Snaps an arbitrary yaw to the nearest cardinal direction, package-visible for testing. */
    static float snapYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized < 0.0F) {
            normalized += 360.0F;
        }
        return Math.round(normalized / 90.0F) * 90.0F % 360.0F;
    }
}
