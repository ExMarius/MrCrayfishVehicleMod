package com.mrcrayfish.vehicle.paper.station;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.economy.FuelAccountService;
import com.mrcrayfish.vehicle.paper.persistence.FuelStationStore;
import com.mrcrayfish.vehicle.paper.persistence.StoredFuelStation;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns every admin-registered fuel station and the refueling sessions players run from them.
 * Stations are placed with {@code /vehicle station create}, never by survival block placement.
 */
public final class FuelStationManager {
    private static final long BILLING_INTERVAL_TICKS = 4L;

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final FuelAccountService account;
    private final FuelStationStore store;

    private final Map<UUID, StoredFuelStation> stations = new HashMap<>();
    private final Map<String, UUID> stationByBlock = new HashMap<>();
    private final Map<UUID, UUID> stationByInteraction = new HashMap<>();
    private final Map<UUID, FuelStationRig> rigs = new HashMap<>();
    /** Which station each player currently has the nozzle of picked up. */
    private final Map<UUID, UUID> nozzleHolder = new HashMap<>();
    /** Active fueling sessions, keyed by the player doing the fueling. */
    private final Map<UUID, Session> sessions = new HashMap<>();

    private BukkitTask animationTask;
    private BukkitTask billingTask;
    private long billingTicks;
    private double pricePerPercent;
    private double fillPercentPerSecond;
    private double maxDistance;

    public FuelStationManager(VehiclePlugin plugin, VehicleManager vehicles, FuelAccountService account) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.account = account;
        this.store = new FuelStationStore(plugin);
        reloadConfig();
    }

    public void reloadConfig() {
        pricePerPercent = Math.max(0.0D, plugin.getConfig().getDouble("fuel-station.price-per-percent", 1.0D));
        fillPercentPerSecond = Math.max(0.1D, plugin.getConfig().getDouble("fuel-station.fill-rate-percent-per-second", 12.0D));
        maxDistance = Math.max(1.0D, plugin.getConfig().getDouble("fuel-station.max-distance", 5.0D));
    }

    public double pricePerPercent() {
        return pricePerPercent;
    }

    public void setPricePerPercent(double value) {
        pricePerPercent = Math.max(0.0D, value);
        plugin.getConfig().set("fuel-station.price-per-percent", pricePerPercent);
        plugin.saveConfig();
    }

    public boolean isPriced() {
        return account.isPriced();
    }

    public int stationCount() {
        return stations.size();
    }

    public void start() {
        removeOrphanedEntities();
        for (StoredFuelStation station : store.loadAll()) {
            register(station);
        }
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::animate, 1L, 1L);
        billingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::bill, BILLING_INTERVAL_TICKS, BILLING_INTERVAL_TICKS);
    }

    public void stop() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        if (billingTask != null) {
            billingTask.cancel();
            billingTask = null;
        }
        sessions.clear();
        nozzleHolder.clear();
        for (FuelStationRig rig : rigs.values()) {
            rig.remove();
        }
        rigs.clear();
        stationByInteraction.clear();
        persist();
    }

    private void removeOrphanedEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(FuelStationRig.ENTITY_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " orphaned fuel station display entities.");
        }
    }

    private void register(StoredFuelStation station) {
        stations.put(station.id(), station);
        stationByBlock.put(blockKey(station.worldId(), station.x(), station.y(), station.z()), station.id());
        World world = Bukkit.getWorld(station.worldId());
        if (world == null) {
            return;
        }
        FuelStationRig rig = FuelStationRig.spawn(world.getBlockAt(station.x(), station.y(), station.z()),
                station.facing(), station.id());
        rigs.put(station.id(), rig);
        stationByInteraction.put(rig.interactionId(), station.id());
    }

    private void persist() {
        store.saveAll(stations.values());
    }

    public boolean isRegistered(Block block) {
        return stationByBlock.containsKey(blockKey(block));
    }

    public boolean createStation(Block block, Player placer) {
        if (isRegistered(block)) {
            return false;
        }
        BlockFace facing = cardinalFacing(placer).getOppositeFace();
        StoredFuelStation station = new StoredFuelStation(UUID.randomUUID(), block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ(), facing);
        register(station);
        persist();
        return true;
    }

    public boolean removeNearestStation(Location origin, double radius) {
        StoredFuelStation nearest = findNearest(origin, radius);
        if (nearest == null) {
            return false;
        }
        stations.remove(nearest.id());
        stationByBlock.remove(blockKey(nearest.worldId(), nearest.x(), nearest.y(), nearest.z()));
        sessions.values().removeIf(session -> session.stationId().equals(nearest.id()));
        nozzleHolder.values().removeIf(id -> id.equals(nearest.id()));
        FuelStationRig rig = rigs.remove(nearest.id());
        if (rig != null) {
            stationByInteraction.remove(rig.interactionId());
            rig.remove();
        }
        persist();
        return true;
    }

    public List<String> describeAll() {
        List<String> lines = new ArrayList<>();
        for (StoredFuelStation station : stations.values()) {
            World world = Bukkit.getWorld(station.worldId());
            String worldName = world == null ? "?" : world.getName();
            lines.add(String.format(Locale.ROOT, "%s: (%d, %d, %d) facing %s",
                    worldName, station.x(), station.y(), station.z(), station.facing()));
        }
        return lines;
    }

    private StoredFuelStation findNearest(Location origin, double radius) {
        StoredFuelStation closest = null;
        double closestDistanceSquared = radius * radius;
        for (StoredFuelStation station : stations.values()) {
            if (!station.worldId().equals(origin.getWorld().getUID())) {
                continue;
            }
            double dx = station.x() + 0.5D - origin.getX();
            double dy = station.y() + 0.5D - origin.getY();
            double dz = station.z() + 0.5D - origin.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= closestDistanceSquared) {
                closest = station;
                closestDistanceSquared = distanceSquared;
            }
        }
        return closest;
    }

    private static BlockFace cardinalFacing(Player player) {
        float yaw = player.getLocation().getYaw() % 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }
        if (yaw >= 315.0F || yaw < 45.0F) {
            return BlockFace.SOUTH;
        } else if (yaw < 135.0F) {
            return BlockFace.WEST;
        } else if (yaw < 225.0F) {
            return BlockFace.NORTH;
        } else {
            return BlockFace.EAST;
        }
    }

    public boolean isStationInteraction(Entity entity) {
        return stationByInteraction.containsKey(entity.getUniqueId());
    }

    public void toggleNozzleByInteraction(Player player, Entity entity) {
        UUID stationId = stationByInteraction.get(entity.getUniqueId());
        if (stationId != null) {
            toggleNozzle(player, stationId);
        }
    }

    public void toggleNozzleByBlock(Player player, Block block) {
        UUID stationId = stationByBlock.get(blockKey(block));
        if (stationId != null) {
            toggleNozzle(player, stationId);
        }
    }

    private void toggleNozzle(Player player, UUID stationId) {
        UUID playerId = player.getUniqueId();
        UUID currentlyHeld = nozzleHolder.get(playerId);
        if (stationId.equals(currentlyHeld)) {
            releaseNozzle(player, stationId);
            return;
        }
        if (currentlyHeld != null) {
            releaseNozzle(player, currentlyHeld);
        }
        nozzleHolder.put(playerId, stationId);
        playStationSound(stationId, Sound.BLOCK_DISPENSER_DISPENSE);
        player.sendRichMessage("<green>Ai luat pistolul pompei.</green> <gray>Click dreapta pe un "
                + "vehicul din apropiere pentru a-l alimenta. Click din nou pe pompă pentru a-l pune la loc.</gray>");
    }

    private void releaseNozzle(Player player, UUID stationId) {
        nozzleHolder.remove(player.getUniqueId());
        sessions.remove(player.getUniqueId());
        playStationSound(stationId, Sound.BLOCK_LEVER_CLICK);
    }

    private void playStationSound(UUID stationId, Sound sound) {
        StoredFuelStation station = stations.get(stationId);
        if (station == null) {
            return;
        }
        World world = Bukkit.getWorld(station.worldId());
        if (world != null) {
            world.playSound(stationCenter(station), sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * Called when a player holding a nozzle right-clicks a vehicle instead of mounting it.
     *
     * @return true if this consumed the click (the caller should not also mount the vehicle).
     */
    public boolean handleVehicleClick(Player player, LandVehicle vehicle) {
        UUID playerId = player.getUniqueId();
        UUID stationId = nozzleHolder.get(playerId);
        if (stationId == null) {
            return false;
        }
        Session existing = sessions.get(playerId);
        if (existing != null && existing.stationId().equals(stationId) && existing.vehicleId().equals(vehicle.id())) {
            sessions.remove(playerId);
            player.sendRichMessage("<yellow>Alimentare oprită.</yellow>");
            return true;
        }
        StoredFuelStation station = stations.get(stationId);
        if (station == null) {
            player.sendRichMessage("<red>Pompa nu mai există.</red>");
            return true;
        }
        if (!withinRange(player, stationCenter(station)) || !withinRange(player, vehicle.location())) {
            player.sendRichMessage("<red>Ești prea departe.</red>");
            return true;
        }
        if (vehicle.fuel() >= vehicle.spec().energyCapacity() - 1.0E-4F) {
            player.sendRichMessage("<yellow>Rezervorul este deja plin.</yellow>");
            return true;
        }
        sessions.put(playerId, new Session(stationId, vehicle.id()));
        if (account.isPriced()) {
            player.sendRichMessage("<green>Alimentare pornită.</green> <gray>"
                    + String.format(Locale.ROOT, "%.2f", pricePerPercent)
                    + " per 1% din rezervor. Click din nou pe mașină pentru a opri.</gray>");
        } else {
            player.sendRichMessage("<green>Alimentare pornită (gratuit).</green>");
        }
        return true;
    }

    private boolean withinRange(Player player, Location target) {
        return target.getWorld().equals(player.getWorld()) && player.getLocation().distance(target) <= maxDistance;
    }

    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        sessions.remove(playerId);
        nozzleHolder.remove(playerId);
    }

    private Location stationCenter(StoredFuelStation station) {
        World world = Bukkit.getWorld(station.worldId());
        return new Location(world, station.x() + 0.5D, station.y() + 0.5D, station.z() + 0.5D);
    }

    private void bill() {
        dropOutOfRangeNozzles();
        if (sessions.isEmpty()) {
            return;
        }
        double percentIncrement = fillPercentPerSecond * (BILLING_INTERVAL_TICKS / 20.0D);
        boolean playGlug = (++billingTicks * BILLING_INTERVAL_TICKS) % 20L < BILLING_INTERVAL_TICKS;
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            UUID playerId = entry.getKey();
            Session session = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                finished.add(playerId);
                continue;
            }
            StoredFuelStation station = stations.get(session.stationId());
            Optional<LandVehicle> vehicleOptional = station == null ? Optional.empty() : vehicles.byId(session.vehicleId());
            if (station == null || vehicleOptional.isEmpty()) {
                finished.add(playerId);
                player.sendRichMessage("<red>Alimentare oprită.</red>");
                continue;
            }
            LandVehicle vehicle = vehicleOptional.get();
            if (!withinRange(player, stationCenter(station)) || !withinRange(player, vehicle.location())) {
                finished.add(playerId);
                player.sendRichMessage("<yellow>Prea departe de pompă, alimentare oprită.</yellow>");
                continue;
            }

            if (playGlug) {
                Location at = vehicle.location();
                at.getWorld().playSound(at, Sound.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 0.6F,
                        1.0F + 0.1F * ThreadLocalRandom.current().nextFloat());
            }

            FuelFlow.Step step = FuelFlow.next(vehicle.fuel(), vehicle.spec().energyCapacity(),
                    account.balanceOf(player), pricePerPercent, percentIncrement);
            if (step.charge() > 0.0D && !account.deduct(player, step.charge())) {
                finished.add(playerId);
                player.sendRichMessage("<red>Fonduri insuficiente, alimentare oprită.</red>");
                continue;
            }
            vehicle.setFuel(step.newFuel());
            if (step.stopped()) {
                finished.add(playerId);
                switch (step.reason()) {
                    case TANK_FULL -> player.sendRichMessage("<green>Rezervorul este plin.</green>");
                    case OUT_OF_MONEY -> player.sendRichMessage("<red>Fonduri insuficiente, alimentare oprită.</red>");
                    default -> { }
                }
            }
        }
        finished.forEach(sessions::remove);
    }

    private void dropOutOfRangeNozzles() {
        if (nozzleHolder.isEmpty()) {
            return;
        }
        List<UUID> dropped = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : nozzleHolder.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            StoredFuelStation station = stations.get(entry.getValue());
            if (player == null || !player.isOnline() || station == null || !withinRange(player, stationCenter(station))) {
                dropped.add(entry.getKey());
            }
        }
        for (UUID playerId : dropped) {
            nozzleHolder.remove(playerId);
            sessions.remove(playerId);
        }
    }

    private void animate() {
        if (rigs.isEmpty()) {
            return;
        }
        Map<UUID, Player> activePlayerByStation = new HashMap<>();
        for (Map.Entry<UUID, UUID> entry : nozzleHolder.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                activePlayerByStation.put(entry.getValue(), player);
            }
        }
        for (Map.Entry<UUID, FuelStationRig> entry : rigs.entrySet()) {
            FuelStationRig rig = entry.getValue();
            if (!rig.valid()) {
                continue;
            }
            Player player = activePlayerByStation.get(entry.getKey());
            if (player == null) {
                rig.setIdle();
                continue;
            }
            Location feet = player.getLocation();
            float yawDegrees = feet.getYaw();
            double yawRadians = Math.toRadians(yawDegrees);
            float forward = 0.35F;
            float right = 0.3F;
            float offsetX = (float) (-Math.sin(yawRadians) * forward + Math.cos(yawRadians) * right);
            float offsetZ = (float) (Math.cos(yawRadians) * forward + Math.sin(yawRadians) * right);
            Vector3f nozzleTip = new Vector3f((float) feet.getX() + offsetX, (float) feet.getY() + 1.1F,
                    (float) feet.getZ() + offsetZ);
            Quaternionf nozzleRotation = new Quaternionf()
                    .rotateY((float) -yawRadians)
                    .rotateX((float) Math.toRadians(25.0F));
            rig.updateActive(nozzleTip, nozzleRotation);
        }
    }

    private static String blockKey(Block block) {
        return blockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static String blockKey(UUID worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    private record Session(UUID stationId, UUID vehicleId) {
    }
}
