package com.mrcrayfish.vehicle.paper.garage;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Physical PARKING SPOTS, FiveM style - no terminals, no chest GUIs, no clickable rigs.
 *
 * <ul>
 *   <li><b>The spot:</b> one entry = an INVISIBLE circle (radius {@link #YARD_RADIUS}) saved
 *       to garages.yml under {@code stations.<uuid>}. The only world-side marker is the
 *       ELECTRIC_SPARK ring ticked while someone watches.</li>
 *   <li><b>Public spots</b> (admin, {@code /vehicle parking set public}) are legal parking
 *       for everyone; <b>personal spots</b> ({@code /garaj set}, one per owner)
 *       are legal only for their owner.</li>
 *   <li><b>Parking:</b> drive in, step out - the car stays on the asphalt and slips into
 *       resting mode on its own (0 CPU). Anti-theft stays the trust system on the vehicle
 *       itself ({@code canDrive}).</li>
 * </ul>
 */
public final class GarageStationManager {
    /** Physical radius of one parking circle, in blocks (the spark ring matches it) -
     *  2 blocks = a tight 4x4 footprint, exactly one real parking spot on the asphalt. */
    private static final double YARD_RADIUS = 2.0D;

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    /** One parking spot: its centre, whether everyone may use it, who owns it (null = public). */
    public record Station(Location location, boolean publicStation, UUID owner) {
    }

    private final Map<UUID, Station> stations = new HashMap<>();
    private BukkitTask tickTask;
    private File storageFile;

    public GarageStationManager(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    public void start() {
        storageFile = new File(plugin.getDataFolder(), "garages.yml");
        load();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /** Marks the block under the player as a parking spot (no terminal spawned at all). */
    public UUID place(Player player, boolean publicSpot) {
        UUID id = UUID.randomUUID();
        Location location = player.getLocation().clone();
        location.setX(location.getBlockX() + 0.5D);
        location.setY(location.getBlockY());
        location.setZ(location.getBlockZ() + 0.5D);
        location.setPitch(0.0F);
        stations.put(id, new Station(location, publicSpot,
                publicSpot ? null : player.getUniqueId()));
        save();
        return id;
    }

    /** Personal spots are ONE per owner: re-setting deletes the old circle(s). */
    public int removeOwnedBy(UUID owner) {
        int removed = 0;
        java.util.Iterator<Map.Entry<UUID, Station>> iterator = stations.entrySet().iterator();
        while (iterator.hasNext()) {
            Station station = iterator.next().getValue();
            if (!station.publicStation() && owner.equals(station.owner())) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    /** Quit + dismount probe: is this vehicle standing inside a LEGAL circle for this
     *  player (any public spot, or his own personal spot)? */
    public boolean isLegallyParked(UUID playerId, Location anchor) {
        for (Station station : stations.values()) {
            if (!station.publicStation() && !playerId.equals(station.owner())) {
                continue;
            }
            if (within(station.location(), anchor, YARD_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /** The PUBLIC spot covering this position (paid pull-outs only happen inside one). */
    public Optional<UUID> publicSpotAt(Location anchor) {
        for (Map.Entry<UUID, Station> entry : stations.entrySet()) {
            Station station = entry.getValue();
            if (station.publicStation() && within(station.location(), anchor, YARD_RADIUS)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** The player's personal (private, owned) spot, if he ever set one. */
    public Optional<UUID> personalSpotOf(UUID playerId) {
        for (Map.Entry<UUID, Station> entry : stations.entrySet()) {
            Station station = entry.getValue();
            if (!station.publicStation() && playerId.equals(station.owner())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** Whether the player stands INSIDE his own personal circle right now. */
    public boolean insidePersonalSpot(UUID playerId, Location anchor) {
        for (Station station : stations.values()) {
            if (!station.publicStation() && playerId.equals(station.owner())
                    && within(station.location(), anchor, YARD_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /** Whether all players may use this spot (GTA public) or only its owner. */
    public boolean isPublic(UUID id) {
        Station station = stations.get(id);
        return station == null || station.publicStation();
    }

    public Optional<UUID> owner(UUID id) {
        Station station = stations.get(id);
        return Optional.ofNullable(station == null ? null : station.owner());
    }

    public Optional<Location> location(UUID id) {
        Station station = stations.get(id);
        return Optional.ofNullable(station == null ? null : station.location().clone());
    }

    private static boolean within(Location spot, Location anchor, double radius) {
        if (spot.getWorld() == null || anchor.getWorld() == null
                || !spot.getWorld().equals(anchor.getWorld())) {
            return false;
        }
        return spot.distanceSquared(anchor) <= radius * radius;
    }

    private void tick() {
        for (Station station : stations.values()) {
            Location location = station.location();
            World world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            // GTA zone marker: the spark ring renders only while someone is close enough to see it.
            Player nearest = null;
            double nearestDistance = 16.0D * 16.0D;
            for (Player player : world.getPlayers()) {
                double distance = player.getLocation().distanceSquared(location);
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearest = player;
                }
            }
            if (nearest != null) {
                for (int point = 0; point < 24; point++) {
                    double angle = Math.PI * 2.0D * point / 24.0D;
                    world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                            location.getX() + Math.cos(angle) * YARD_RADIUS,
                            location.getY() + 0.15D,
                            location.getZ() + Math.sin(angle) * YARD_RADIUS,
                            1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Station> entry : stations.entrySet()) {
            Station station = entry.getValue();
            Location location = station.location();
            String path = "stations." + entry.getKey();
            data.set(path + ".world", location.getWorld() == null ? null : location.getWorld().getName());
            data.set(path + ".x", location.getX());
            data.set(path + ".y", location.getY());
            data.set(path + ".z", location.getZ());
            data.set(path + ".yaw", (double) location.getYaw());
            data.set(path + ".public", station.publicStation());
            data.set(path + ".owner", station.owner() == null ? null : station.owner().toString());
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            data.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save garages.yml", exception);
        }
    }

    private void load() {
        if (!storageFile.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection section = data.getConfigurationSection("stations");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "stations." + key;
                World world = Bukkit.getWorld(data.getString(path + ".world", "world"));
                if (world == null) {
                    plugin.getLogger().warning("Parcare " + key + " sarita: lumea nu e incarcata");
                    continue;
                }
                Location location = new Location(world, data.getDouble(path + ".x"),
                        data.getDouble(path + ".y"), data.getDouble(path + ".z"),
                        (float) data.getDouble(path + ".yaw"), 0.0F);
                boolean publicStation = data.getBoolean(path + ".public", true);
                String ownerRaw = data.getString(path + ".owner");
                UUID owner = ownerRaw == null ? null : UUID.fromString(ownerRaw);
                stations.put(id, new Station(location, publicStation, owner));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not load parking spot " + key, exception);
            }
        }
        plugin.getLogger().info("Loaded " + stations.size() + " parking spot(s).");
    }

    /** Exposed for the vehicle manager's parking decisions (kept for API symmetry). */
    public VehicleManager vehicles() {
        return vehicles;
    }
}
