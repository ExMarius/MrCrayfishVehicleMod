package com.mrcrayfish.vehicle.paper.economy;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.GasPumpStore;
import com.mrcrayfish.vehicle.paper.persistence.StoredGasPump;
import com.mrcrayfish.vehicle.paper.render.GasPumpRig;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers admin-placed gas pump blocks and runs the fueling sessions players start by
 * right-clicking them. New addition on top of the original mod (which never charged
 * money for fuel): see FuelEconomyService for the Vault hookup and FuelPricing for the
 * percent-based charging math.
 */
public final class GasPumpManager {
    private static final long TICK_INTERVAL = 5L;

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final FuelEconomyService economy;
    private final GasPumpStore store;

    private final Map<UUID, StoredGasPump> pumps = new HashMap<>();
    private final Map<String, UUID> pumpsByBlock = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Maps a player to the pump whose nozzle they're currently holding, mirroring the
     *  original's {@code ModDataKeys.GAS_PUMP} player data key. Picking up the nozzle (by
     *  clicking the pump) and actually transferring fuel (by then clicking a vehicle) are
     *  two separate steps here, just like in the original mod. */
    private final Map<UUID, UUID> holding = new HashMap<>();
    private final Map<UUID, GasPumpRig> rigs = new HashMap<>();
    /** Maps each rig's invisible Interaction entity back to its pump, since the pump has no
     *  real block anymore for a vanilla block right-click to land on. */
    private final Map<UUID, UUID> pumpByInteraction = new HashMap<>();

    private BukkitTask tickTask;
    private BukkitTask rigTask;
    /** Counts {@link #tick()} invocations so the glug sound below can be throttled to about
     *  once a second, matching the original's {@code fuelTickCounter % 20 == 0} (that one
     *  counts client ticks 1:1; this one counts {@link #TICK_INTERVAL}-tick invocations, so
     *  it rolls over four times as fast to land on the same real-world cadence). */
    private long tickCounter;
    private double pricePerPercent;
    private double fillPercentPerSecond;
    private double maxPumpDistance;

    public GasPumpManager(VehiclePlugin plugin, VehicleManager vehicles, FuelEconomyService economy) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.economy = economy;
        this.store = new GasPumpStore(plugin);
        reloadConfig();
    }

    public void reloadConfig() {
        this.pricePerPercent = Math.max(0.0D, plugin.getConfig().getDouble("economy.fuel-price-per-percent", 1.0D));
        this.fillPercentPerSecond = Math.max(0.1D, plugin.getConfig().getDouble("economy.fill-percent-per-second", 10.0D));
        this.maxPumpDistance = Math.max(1.0D, plugin.getConfig().getDouble("economy.max-pump-distance", 5.0D));
    }

    public double pricePerPercent() {
        return pricePerPercent;
    }

    public void setPricePerPercent(double value) {
        this.pricePerPercent = Math.max(0.0D, value);
        plugin.getConfig().set("economy.fuel-price-per-percent", this.pricePerPercent);
        plugin.saveConfig();
    }

    public boolean economyAvailable() {
        return economy.isAvailable();
    }

    public void start() {
        cleanupOrphanedRigs();
        for (StoredGasPump pump : store.loadAll()) {
            pumps.put(pump.id(), pump);
            pumpsByBlock.put(blockKey(pump.worldId(), pump.x(), pump.y(), pump.z()), pump.id());
            spawnRig(pump);
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
        // Separate, every-tick task so the hose visibly bends smoothly while a player is
        // fueling; the slower tick() above only drives the (unrelated) fuel/money math.
        rigTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRigs, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (rigTask != null) {
            rigTask.cancel();
            rigTask = null;
        }
        sessions.clear();
        holding.clear();
        for (GasPumpRig rig : rigs.values()) {
            rig.remove();
        }
        rigs.clear();
        pumpByInteraction.clear();
        save();
    }

    /** Mirrors {@code VehicleManager#cleanupOrphanedEntities()}: any pump display rig left
     *  over from a previous run (e.g. a crash) is removed before fresh ones are spawned. */
    private void cleanupOrphanedRigs() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(GasPumpRig.ENTITY_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " orphaned gas pump display entities.");
        }
    }

    private void spawnRig(StoredGasPump pump) {
        World world = Bukkit.getWorld(pump.worldId());
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(pump.x(), pump.y(), pump.z());
        GasPumpRig rig = GasPumpRig.spawn(block, pump.facing(), pump.id());
        rigs.put(pump.id(), rig);
        pumpByInteraction.put(rig.interactionId(), pump.id());
    }

    public void save() {
        store.saveAll(pumps.values());
    }

    public boolean isPump(Block block) {
        return resolvePumpBlock(block) != null;
    }

    /**
     * The pump's visual is two blocks tall, so either half should respond to a right-click.
     * Returns the pump's registered (bottom) block if {@code clicked} is that block or the
     * one directly above it, otherwise {@code null}.
     */
    private Block resolvePumpBlock(Block clicked) {
        if (pumpsByBlock.containsKey(blockKey(clicked))) {
            return clicked;
        }
        Block below = clicked.getRelative(BlockFace.DOWN);
        if (pumpsByBlock.containsKey(blockKey(below))) {
            return below;
        }
        return null;
    }

    public int pumpCount() {
        return pumps.size();
    }

    /**
     * Registers {@code block} as a new gas pump, facing the cardinal direction {@code player}
     * was looking when they ran the command. Returns {@code false} if already registered.
     */
    public boolean createPump(Block block, Player player) {
        if (isPump(block)) {
            return false;
        }
        BlockFace facing = cardinalFacing(player);
        StoredGasPump pump = new StoredGasPump(UUID.randomUUID(), block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ(), facing);
        pumps.put(pump.id(), pump);
        pumpsByBlock.put(blockKey(pump.worldId(), pump.x(), pump.y(), pump.z()), pump.id());
        spawnRig(pump);
        save();
        return true;
    }

    /** Reduces a player's look yaw to the nearest of the four horizontal cardinal directions. */
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

    /** Removes every registered pump and its live rig at once -- e.g. for wiping out test
     *  pumps left scattered around the world during development, where tracking each one
     *  down with {@link #removeNearestPump} would be slow. Returns how many were removed. */
    public int clearAllPumps() {
        int count = pumps.size();
        pumps.clear();
        pumpsByBlock.clear();
        sessions.clear();
        holding.clear();
        for (GasPumpRig rig : rigs.values()) {
            rig.remove();
        }
        rigs.clear();
        pumpByInteraction.clear();
        save();
        return count;
    }

    /** Removes the nearest registered pump within {@code radius} of {@code origin}, if any. */
    public boolean removeNearestPump(Location origin, double radius) {
        StoredGasPump closest = null;
        double closestDistanceSquared = radius * radius;
        for (StoredGasPump pump : pumps.values()) {
            if (!pump.worldId().equals(origin.getWorld().getUID())) {
                continue;
            }
            double dx = pump.x() + 0.5D - origin.getX();
            double dy = pump.y() + 0.5D - origin.getY();
            double dz = pump.z() + 0.5D - origin.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= closestDistanceSquared) {
                closest = pump;
                closestDistanceSquared = distanceSquared;
            }
        }
        if (closest == null) {
            return false;
        }
        UUID removedPumpId = closest.id();
        pumps.remove(removedPumpId);
        pumpsByBlock.remove(blockKey(closest.worldId(), closest.x(), closest.y(), closest.z()));
        sessions.values().removeIf(session -> session.pumpId().equals(removedPumpId));
        holding.values().removeIf(heldPumpId -> heldPumpId.equals(removedPumpId));
        GasPumpRig rig = rigs.remove(removedPumpId);
        if (rig != null) {
            pumpByInteraction.remove(rig.interactionId());
            rig.remove();
        }
        save();
        return true;
    }

    /** Ground-truth diagnostic dump of the nearest registered pump within {@code radius} of
     *  {@code origin} -- its stored facing/coordinates plus every entity its rig actually
     *  spawned, straight from Bukkit, for comparing real server state against the positioning
     *  code instead of a screenshot. Returns {@code null} if none is in range. */
    public String debugNearestPump(Location origin, double radius) {
        StoredGasPump closest = null;
        double closestDistanceSquared = radius * radius;
        for (StoredGasPump pump : pumps.values()) {
            if (!pump.worldId().equals(origin.getWorld().getUID())) {
                continue;
            }
            double dx = pump.x() + 0.5D - origin.getX();
            double dy = pump.y() + 0.5D - origin.getY();
            double dz = pump.z() + 0.5D - origin.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= closestDistanceSquared) {
                closest = pump;
                closestDistanceSquared = distanceSquared;
            }
        }
        if (closest == null) {
            return null;
        }
        return describePump(closest);
    }

    /** Same ground-truth dump as {@link #debugNearestPump}, but for every registered pump at
     *  once -- usable from the server console, which has no in-world location to measure
     *  "nearest" from. Returns one block of text per pump, in no particular order. */
    public String debugAllPumps() {
        if (pumps.isEmpty()) {
            return "(no gas pumps registered)";
        }
        StringBuilder sb = new StringBuilder();
        for (StoredGasPump pump : pumps.values()) {
            sb.append(describePump(pump)).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }

    private String describePump(StoredGasPump pump) {
        World world = Bukkit.getWorld(pump.worldId());
        GasPumpRig rig = rigs.get(pump.id());
        StringBuilder sb = new StringBuilder();
        sb.append("stored pump: block=(").append(pump.x()).append(", ").append(pump.y())
                .append(", ").append(pump.z()).append(") facing=").append(pump.facing()).append('\n');
        if (world != null) {
            Block groundBlock = world.getBlockAt(pump.x(), pump.y() - 1, pump.z());
            sb.append("block directly below the pump's bottom (y-1) = ").append(groundBlock.getType()).append('\n');
        }
        if (rig == null) {
            sb.append("(no live rig found for this pump!)");
        } else {
            sb.append(rig.debugDump());
        }
        return sb.toString();
    }

    /** Whether {@code entity} is a pump's invisible interaction hitbox. */
    public boolean isPumpInteraction(Entity entity) {
        return pumpByInteraction.containsKey(entity.getUniqueId());
    }

    /** Whether {@code player} currently holds any pump's nozzle (picked up, not yet put
     *  back) -- used by {@code VehicleListener} to decide whether a vehicle right-click
     *  should fuel it instead of mounting it. */
    public boolean isHoldingNozzle(Player player) {
        return holding.containsKey(player.getUniqueId());
    }

    /** Picks up or puts back the nozzle of the pump whose interaction hitbox {@code entity}
     *  is -- mirrors {@code GasPumpBlock#use}'s nozzle pick-up/put-down branch. This no
     *  longer starts fueling by itself; the player must then right-click a vehicle while
     *  holding the nozzle, just like in the original mod. */
    public void toggleFuelingByEntity(Player player, Entity entity) {
        UUID pumpId = pumpByInteraction.get(entity.getUniqueId());
        if (pumpId != null) {
            toggleNozzle(player, pumpId);
        }
    }

    /** Picks up or puts back the nozzle of the pump at {@code clickedBlock} (either half of
     *  the pump's two-block-tall visual). Kept for any pump whose registered position still
     *  happens to be a real solid block. */
    public void toggleFueling(Player player, Block clickedBlock) {
        Block pumpBlock = resolvePumpBlock(clickedBlock);
        if (pumpBlock == null) {
            return;
        }
        UUID pumpId = pumpsByBlock.get(blockKey(pumpBlock));
        if (pumpId != null) {
            toggleNozzle(player, pumpId);
        }
    }

    private void toggleNozzle(Player player, UUID pumpId) {
        UUID playerId = player.getUniqueId();
        UUID currentlyHeld = holding.get(playerId);
        if (pumpId.equals(currentlyHeld)) {
            stopFuelingSession(playerId);
            holding.remove(playerId);
            playNozzleSound(pumpId, "vehicle:block.gas_pump.nozzle.put_down");
            player.sendRichMessage("<yellow>Ai pus duza la loc.</yellow>");
            return;
        }
        if (currentlyHeld != null) {
            // Matches the original: you can only hold one pump's nozzle at a time.
            stopFuelingSession(playerId);
            holding.remove(playerId);
            playNozzleSound(currentlyHeld, "vehicle:block.gas_pump.nozzle.put_down");
        }
        holding.put(playerId, pumpId);
        playNozzleSound(pumpId, "vehicle:block.gas_pump.nozzle.pick_up");
        player.sendRichMessage("<green>Ai luat duza.</green> <gray>Apropie-te de un vehicul "
                + "și dă click dreapta pe el pentru a-l alimenta. Click din nou pe pompă pentru "
                + "a pune duza la loc.</gray>");
    }

    /** Matches the original's {@code GasPumpBlock#use} playing
     *  {@code BLOCK_GAS_PUMP_NOZZLE_PICK_UP}/{@code PUT_DOWN} at the pump block itself. */
    private void playNozzleSound(UUID pumpId, String soundKey) {
        StoredGasPump pump = pumps.get(pumpId);
        if (pump == null) {
            return;
        }
        World world = Bukkit.getWorld(pump.worldId());
        if (world == null) {
            return;
        }
        world.playSound(pumpCenter(pump), soundKey, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    /** Called from {@code VehicleListener} when a player holding a pump's nozzle right-clicks
     *  {@code vehicle} instead of mounting it -- mirrors the original's continuous
     *  {@code FUNCTION_FUELING} raytrace, collapsed into a start/stop toggle since a Paper
     *  plugin cannot observe a held-down right click the way a client-side raytrace can.
     *
     * @return true if the click was consumed by fueling (caller should not also mount the
     * vehicle), false if the player isn't holding any nozzle at all. */
    public boolean handleVehicleClick(Player player, LandVehicle vehicle) {
        UUID playerId = player.getUniqueId();
        UUID pumpId = holding.get(playerId);
        if (pumpId == null) {
            return false;
        }
        Session existing = sessions.get(playerId);
        if (existing != null && existing.pumpId().equals(pumpId) && existing.vehicleId().equals(vehicle.id())) {
            sessions.remove(playerId);
            player.sendRichMessage("<yellow>Alimentare întreruptă.</yellow> <gray>Click pe mașină "
                    + "pentru a continua, sau pe pompă pentru a pune duza la loc.</gray>");
            return true;
        }
        StoredGasPump pump = pumps.get(pumpId);
        if (pump == null) {
            player.sendRichMessage("<red>Pompa nu mai există.</red>");
            return true;
        }
        World pumpWorld = Bukkit.getWorld(pump.worldId());
        if (pumpWorld == null || !pumpWorld.equals(player.getWorld())
                || player.getLocation().distance(pumpCenter(pump)) > maxPumpDistance) {
            player.sendRichMessage("<red>Ești prea departe de pompă.</red>");
            return true;
        }
        if (!vehicle.location().getWorld().equals(player.getWorld())
                || player.getLocation().distance(vehicle.location()) > maxPumpDistance) {
            player.sendRichMessage("<red>Ești prea departe de vehicul.</red>");
            return true;
        }
        if (vehicle.fuel() >= vehicle.spec().energyCapacity() - 1.0E-4F) {
            player.sendRichMessage("<yellow>Rezervorul vehiculului este deja plin.</yellow>");
            return true;
        }
        sessions.put(playerId, new Session(pumpId, vehicle.id()));
        if (economy.isAvailable()) {
            player.sendRichMessage("<green>Alimentare pornită.</green> <gray>"
                    + String.format(java.util.Locale.ROOT, "%.2f", pricePerPercent)
                    + " pe fiecare 1% din rezervor. Click din nou pe mașină pentru a opri.</gray>");
        } else {
            player.sendRichMessage("<green>Alimentare pornită (gratuit — nu există economie pe server).</green>");
        }
        return true;
    }

    private void stopFuelingSession(UUID playerId) {
        sessions.remove(playerId);
    }

    private Location pumpCenter(StoredGasPump pump) {
        World world = Bukkit.getWorld(pump.worldId());
        return new Location(world, pump.x() + 0.5D, pump.y() + 0.5D, pump.z() + 0.5D);
    }

    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        sessions.remove(playerId);
        holding.remove(playerId);
    }

    private void tick() {
        dropNozzlesOutOfRange();
        tickCounter++;
        if (sessions.isEmpty()) {
            return;
        }
        boolean playGlug = tickCounter % Math.max(1L, 20L / TICK_INTERVAL) == 0L;
        double incrementPercent = fillPercentPerSecond * (TICK_INTERVAL / 20.0D);
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            UUID playerId = entry.getKey();
            Session session = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                toRemove.add(playerId);
                continue;
            }
            StoredGasPump pump = pumps.get(session.pumpId());
            if (pump == null) {
                toRemove.add(playerId);
                continue;
            }
            Optional<LandVehicle> vehicleOptional = vehicles.byId(session.vehicleId());
            if (vehicleOptional.isEmpty()) {
                toRemove.add(playerId);
                player.sendRichMessage("<red>Vehiculul nu mai există, alimentare oprită.</red>");
                continue;
            }
            LandVehicle vehicle = vehicleOptional.get();

            org.bukkit.World pumpWorld = Bukkit.getWorld(pump.worldId());
            Location pumpLocation = pumpWorld == null ? null
                    : new Location(pumpWorld, pump.x() + 0.5D, pump.y() + 0.5D, pump.z() + 0.5D);
            if (pumpLocation == null || !pumpWorld.equals(player.getWorld())
                    || player.getLocation().distance(pumpLocation) > maxPumpDistance
                    || !vehicle.location().getWorld().equals(player.getWorld())
                    || player.getLocation().distance(vehicle.location()) > maxPumpDistance) {
                toRemove.add(playerId);
                player.sendRichMessage("<yellow>Prea departe de pompă, alimentare oprită.</yellow>");
                continue;
            }

            if (playGlug) {
                // Matches the original's FuelingHandler playing ITEM_JERRY_CAN_LIQUID_GLUG
                // roughly once a second at the nozzle's location while fuel is flowing.
                Location vehicleLocation = vehicle.location();
                vehicleLocation.getWorld().playSound(vehicleLocation, "vehicle:item.jerry_can.liquid_glug",
                        SoundCategory.PLAYERS, 0.6F,
                        1.0F + 0.1F * java.util.concurrent.ThreadLocalRandom.current().nextFloat());
            }

            double balance = economy.balance(player);
            FuelPricing.Step step = FuelPricing.step(vehicle.fuel(), vehicle.spec().energyCapacity(),
                    balance, pricePerPercent, incrementPercent);
            if (step.charge() > 0.0D && !economy.withdraw(player, step.charge())) {
                toRemove.add(playerId);
                player.sendRichMessage("<red>Fonduri insuficiente, alimentare oprită.</red>");
                continue;
            }
            vehicle.setFuel(step.newFuel());
            if (step.stopped()) {
                toRemove.add(playerId);
                switch (step.reason()) {
                    case FULL -> player.sendRichMessage("<green>Rezervorul este plin.</green>");
                    case INSUFFICIENT_FUNDS -> player.sendRichMessage(
                            "<red>Fonduri insuficiente, alimentare oprită.</red>");
                    default -> { }
                }
            }
        }
        toRemove.forEach(sessions::remove);
    }

    /** Mirrors {@code GasPumpTileEntity#tick()}'s distance check: a player who wanders too
     *  far from the pump they're holding the nozzle of loses it entirely (any active fueling
     *  session is also stopped), the same way the original "yanks" the hose out of their
     *  hand. */
    private void dropNozzlesOutOfRange() {
        if (holding.isEmpty()) {
            return;
        }
        List<UUID> toDrop = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : holding.entrySet()) {
            UUID playerId = entry.getKey();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                toDrop.add(playerId);
                continue;
            }
            StoredGasPump pump = pumps.get(entry.getValue());
            if (pump == null) {
                toDrop.add(playerId);
                continue;
            }
            World pumpWorld = Bukkit.getWorld(pump.worldId());
            if (pumpWorld == null || !pumpWorld.equals(player.getWorld())
                    || player.getLocation().distance(pumpCenter(pump)) > maxPumpDistance) {
                toDrop.add(playerId);
                player.sendRichMessage("<red>Te-ai îndepărtat prea mult de pompă, "
                        + "ai scăpat duza din mână.</red>");
            }
        }
        for (UUID playerId : toDrop) {
            holding.remove(playerId);
            stopFuelingSession(playerId);
        }
    }


    /** Runs every tick so the hose visibly tracks whichever player is currently holding a
     *  pump's nozzle; this is intentionally separate from {@link #tick()}, which only
     *  handles the (much less time-sensitive) fuel/money bookkeeping every
     *  {@link #TICK_INTERVAL} ticks.
     *
     *  <p>Matches the original: {@code GasPumpRenderer} bends the hose toward
     *  {@code GasPumpTileEntity#getFuelingEntity()} as soon as the nozzle is picked up, not
     *  only once fuel is actually flowing into a vehicle. */
    private void tickRigs() {
        if (rigs.isEmpty()) {
            return;
        }
        Map<UUID, Player> activePlayerByPump = new HashMap<>();
        for (Map.Entry<UUID, UUID> holdingEntry : holding.entrySet()) {
            Player player = Bukkit.getPlayer(holdingEntry.getKey());
            if (player != null && player.isOnline()) {
                activePlayerByPump.put(holdingEntry.getValue(), player);
            }
        }
        for (Map.Entry<UUID, GasPumpRig> entry : rigs.entrySet()) {
            GasPumpRig rig = entry.getValue();
            if (!rig.valid()) {
                continue;
            }
            Player player = activePlayerByPump.get(entry.getKey());
            if (player == null) {
                rig.setIdle();
                continue;
            }
            Location feet = player.getLocation();
            Vector3f feetVector = new Vector3f((float) feet.getX(), (float) feet.getY(), (float) feet.getZ());
            rig.updateActive(feetVector, feet.getYaw(), player.getMainHand());
        }
    }

    private static String blockKey(Block block) {
        return blockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static String blockKey(UUID worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    private record Session(UUID pumpId, UUID vehicleId) {
    }
}
