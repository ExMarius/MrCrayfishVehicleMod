package com.mrcrayfish.vehicle.paper.economy;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.GasPumpStore;
import com.mrcrayfish.vehicle.paper.persistence.StoredGasPump;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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

    private BukkitTask tickTask;
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
        for (StoredGasPump pump : store.loadAll()) {
            pumps.put(pump.id(), pump);
            pumpsByBlock.put(blockKey(pump.worldId(), pump.x(), pump.y(), pump.z()), pump.id());
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        sessions.clear();
        save();
    }

    public void save() {
        store.saveAll(pumps.values());
    }

    public boolean isPump(Block block) {
        return pumpsByBlock.containsKey(blockKey(block));
    }

    public int pumpCount() {
        return pumps.size();
    }

    /** Registers {@code block} as a new gas pump. Returns {@code false} if already registered. */
    public boolean createPump(Block block) {
        if (isPump(block)) {
            return false;
        }
        StoredGasPump pump = new StoredGasPump(UUID.randomUUID(), block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ());
        pumps.put(pump.id(), pump);
        pumpsByBlock.put(blockKey(pump.worldId(), pump.x(), pump.y(), pump.z()), pump.id());
        save();
        return true;
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
        pumps.remove(closest.id());
        pumpsByBlock.remove(blockKey(closest.worldId(), closest.x(), closest.y(), closest.z()));
        sessions.values().removeIf(session -> session.pumpId.equals(closest.id()));
        save();
        return true;
    }

    /** Starts or stops a fueling session for {@code player} at {@code pumpBlock}. */
    public void toggleFueling(Player player, Block pumpBlock) {
        UUID pumpId = pumpsByBlock.get(blockKey(pumpBlock));
        if (pumpId == null) {
            return;
        }
        Session existing = sessions.get(player.getUniqueId());
        if (existing != null && existing.pumpId.equals(pumpId)) {
            sessions.remove(player.getUniqueId());
            player.sendRichMessage("<yellow>Alimentare oprită.</yellow>");
            return;
        }

        Optional<LandVehicle> nearest = vehicles.nearest(player.getLocation(), maxPumpDistance);
        if (nearest.isEmpty()) {
            player.sendRichMessage("<red>Nu există niciun vehicul lângă pompă.</red>");
            return;
        }
        LandVehicle vehicle = nearest.get();
        if (vehicle.fuel() >= vehicle.spec().energyCapacity() - 1.0E-4F) {
            player.sendRichMessage("<yellow>Rezervorul vehiculului este deja plin.</yellow>");
            return;
        }
        sessions.put(player.getUniqueId(), new Session(pumpId, vehicle.id()));
        if (economy.isAvailable()) {
            player.sendRichMessage("<green>Alimentare pornită.</green> <gray>"
                    + String.format(java.util.Locale.ROOT, "%.2f", pricePerPercent)
                    + " pe fiecare 1% din rezervor. Dreapta-click din nou sau îndepărtează-te pentru a opri.</gray>");
        } else {
            player.sendRichMessage("<green>Alimentare pornită (gratuit — nu există economie pe server).</green>");
        }
    }

    public void onPlayerQuit(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private void tick() {
        if (sessions.isEmpty()) {
            return;
        }
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
            StoredGasPump pump = pumps.get(session.pumpId);
            if (pump == null) {
                toRemove.add(playerId);
                continue;
            }
            Optional<LandVehicle> vehicleOptional = vehicles.byId(session.vehicleId);
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

    private static String blockKey(Block block) {
        return blockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static String blockKey(UUID worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    private record Session(UUID pumpId, UUID vehicleId) {
    }
}
