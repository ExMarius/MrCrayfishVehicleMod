package com.mrcrayfish.vehicle.paper.gaspump;

// ============================================================================
// [NU SCHIMBA - POZITII VALIDATE DE UTILIZATOR 2026-10-18, commit f7366e7]
// Aspectul + pozitiile pompei/furtunului/pistolului sunt PERFECTE asa cum sunt.
// Orice schimbare in acest pachet cere acordul EXPLICIT al utilizatorului.
// Restaurare instant: git checkout gaspump-perfect-v1 -- paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/gaspump/
// Vezi paper-plugin/SNAPSHOT_GAS_PUMP_OK.md pentru constantele exacte.
// ============================================================================

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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

    /**
     * FINAL combo (user decision 2026-10-18): the nozzle rides as a REAL hand item, rendered
     * natively by Minecraft exactly like the original mod (60fps, native arm animation). The
     * shared chain terminates at the body's hand point so bystanders and F5 see it welded;
     * the holder additionally has the near-face segments hidden so first person stays clean.
     */
    public void pickUpNozzle(Player player, GasPump pump) {
        pump.setHolderId(player.getUniqueId());
        ItemStack nozzle = NozzleItem.create(pump.id().toString());
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            player.getInventory().setItemInMainHand(nozzle);
        } else {
            int slot = player.getInventory().firstEmpty();
            if (slot >= 0) {
                player.getInventory().setItem(slot, nozzle);
            } else {
                player.getInventory().addItem(nozzle); // worst case, nothing dropped
            }
        }
        Location location = pump.location();
        player.playSound(Sound.sound(Key.key("vehicle:block.gas_pump.nozzle_pick_up"),
                Sound.Source.BLOCK, 1.0F, 1.0F), location.getX(), location.getY(), location.getZ());

        // Per-viewer hiding: the holder does NOT see the hose segments parked right in front
        // of their face (first person shows just the floating nozzle); everyone else sees
        // the full chain welded into it.
        GasPumpRig rig = pump.rig();
        rig.setTailViewerId(player.getUniqueId());
        for (ItemDisplay hidden : rig.holderCoveredTail()) {
            player.hideEntity(plugin, hidden);
        }
    }

    /** Puts the nozzle back on the pump and removes the hand item from the holder (anti-dupe). */
    public void putDownNozzle(GasPump pump) {
        Player holder = pump.holderId() == null ? null : Bukkit.getPlayer(pump.holderId());
        if (holder != null) {
            removeNozzleItems(holder);
            // Undo the per-viewer tail swap.
            GasPumpRig rig = pump.rig();
            for (ItemDisplay shown : rig.holderCoveredTail()) {
                holder.showEntity(plugin, shown);
            }
            for (ItemDisplay tail : rig.holderTail()) {
                holder.hideEntity(plugin, tail);
            }
            rig.setTailViewerId(null);
        }
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

    /** Removes every nozzle item from the player's inventory incl. hands/armor slots (anti-dupe). */
    public static void removeNozzleItems(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (NozzleItem.isNozzle(stack)) {
                stack.setAmount(0);
            }
        }
    }

    /** Player quit: pump gets its nozzle back even though the holder is offline now. */
    public void handleQuit(Player player) {
        removeNozzleItems(player);
        for (GasPump pump : new ArrayList<>(pumps.values())) {
            if (player.getUniqueId().equals(pump.holderId())) {
                putDownNozzle(pump);
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

            // The chain welds to the body's hand point (correct for F5 and bystanders);
            // the holder's own first-person view stays clean via the hidden near-face parts.
            pump.rig().update(pump.location(), bodyHandNozzleLocation(holder),
                    holder.getEyeLocation().getDirection(), holder);

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

    /**
     * Where the holder's hand really is on their BODY (what bystanders / F5 render): yaw-only
     * basis so the shared chain ends welded to the visible arm, independent of view pitch.
     */
    private static Location bodyHandNozzleLocation(Player player) {
        Location eye = player.getEyeLocation();
        double rad = Math.toRadians(eye.getYaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -Math.cos(rad), rz = -Math.sin(rad);
        return eye.clone()
                .add(fx * 0.28D + rx * 0.30D, -0.55D, fz * 0.28D + rz * 0.30D)
                .add(player.getVelocity().multiply(2.0D));
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
                .add(right.multiply(0.3D))
                // Movement prediction (~2 ticks): entity sync latency would otherwise pin the
                // hose tail to where the head WAS, so sprinting left the hose trailing behind.
                .add(player.getVelocity().multiply(2.0D));
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
