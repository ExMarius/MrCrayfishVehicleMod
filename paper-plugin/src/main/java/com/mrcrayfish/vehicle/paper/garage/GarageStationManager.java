package com.mrcrayfish.vehicle.paper.garage;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Physical garage stations (GTA 1:1): persistent workbench terminals in the world.
 * <ul>
 *   <li><b>Brotherhood with the world:</b> each station is a small Interaction + ItemDisplay
 *       rig tagged with its station id, saved to garages.yml and re-spawned on load.</li>
 *   <li><b>Auto-park:</b> once per second, the station scans ~5 blocks: a vehicle someone is
 *       driving in its yard gets parked for the DRIVER (claims it if unowned) - drive into
 *       the garage and it stores itself, exactly like pulling into a GTA garage.</li>
 *   <li><b>Menu:</b> right-clicking the station opens YOUR garage window and pulled vehicles
 *       spawn at the station (not your pocket) - see {@link GarageStationListener}.</li>
 * </ul>
 */
public final class GarageStationManager {
    private static final double YARD_RADIUS = 5.0D;

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;

    /** One placed terminal: where it stands, whether everyone may use it, who owns it. */
    private record Station(Location location, boolean publicStation, UUID owner) {
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

    /** Places a station at the given foot position, keeps the player's facing for the menu.
     *  Public stations (GTA, admin-placed) serve everyone; private ones serve only their owner. */
    public void place(Player player, Location foot, boolean publicStation) {
        UUID id = UUID.randomUUID();
        Location location = foot.clone().add(0.5D, 0.0D, 0.5D);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0.0F);
        stations.put(id, new Station(location, publicStation,
                publicStation ? null : player.getUniqueId()));
        spawnRig(id, location, publicStation);
        save();
    }

    /** Breaks a station (returns the placeable item); the garage STORAGE itself never dies. */
    public boolean remove(Entity entity) {
        UUID id = stationId(entity).orElse(null);
        if (id == null) {
            return false;
        }
        Station station = stations.remove(id);
        if (station != null) {
            Location location = station.location();
            despawnRig(location, id);
            location.getWorld().dropItemNaturally(location.clone().add(0.0D, 0.5D, 0.0D),
                    GarageStationItem.create());
            save();
        }
        return station != null;
    }

    /** Identifies a station interaction/display entity (null when it belongs to no station). */
    public Optional<UUID> stationId(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("mcv_garage_station_")) {
                try {
                    return Optional.of(UUID.fromString(tag.substring("mcv_garage_station_".length())));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Location> location(UUID id) {
        Station station = stations.get(id);
        return Optional.ofNullable(station == null ? null : station.location());
    }

    /** Whether all players may use this station (GTA public) or only its owner. */
    public boolean isPublic(UUID id) {
        Station station = stations.get(id);
        return station == null || station.publicStation();
    }

    public Optional<UUID> owner(UUID id) {
        Station station = stations.get(id);
        return Optional.ofNullable(station == null ? null : station.owner());
    }

    private void tick() {
        for (Map.Entry<UUID, Station> entry : stations.entrySet()) {
            Station station = entry.getValue();
            Location location = station.location();
            World world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            for (LandVehicle vehicle : vehicles.vehiclesWithin(location, YARD_RADIUS)) {
                Player driver = vehicle.driver().orElse(null);
                if (driver == null) {
                    continue;
                }
                // Private garage: only the owner's cars may park here (GTA public = anyone).
                if (!station.publicStation() && !driver.getUniqueId().equals(station.owner())) {
                    continue;
                }
                VehicleManager.ParkResult result = vehicles.park(vehicle, driver);
                switch (result) {
                    case PARKED -> driver.sendRichMessage("<green>🏁 " + vehicle.spec().displayName()
                            + " s-a parcat SINGUR în garaj (ca în GTA).</green>");
                    case CLAIMED_AND_PARKED -> driver.sendRichMessage("<gold>🏁 " + vehicle.spec().displayName()
                            + " e acum AL TĂU și parcat în garaj.</gold>");
                    case NOT_OWNER -> driver.sendRichMessage("<red>Mașina aceasta nu e a ta - nu o poți garaj-a aici.</red>");
                    default -> { }
                }
            }
            // GTA zone marker: a soft golden dust ring on the yard, only while someone watches.
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
                // Yellow electric sparks = a GTA-style zone marker ring on the yard.
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

    private void spawnRig(UUID id, Location location, boolean publicStation) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Interaction box = world.spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(0.95F);
            entity.setInteractionHeight(0.95F);
            entity.addScoreboardTag("mcv_garage_station_" + id);
            entity.setPersistent(true);
        });
        world.spawn(location.clone().add(0.0D, 0.5D, 0.0D), ItemDisplay.class, display -> {
            ItemStack terminal = new ItemStack(Material.PAPER);
            terminal.editMeta(meta -> meta.setItemModel(new NamespacedKey("vehicle", "garage_terminal")));
            display.setItemStack(terminal);
            display.setTransformation(new Transformation(new Vector3f(0F, 0F, 0F),
                    new AxisAngle4f(), new Vector3f(1.0F, 1.0F, 1.0F), new AxisAngle4f(
                            (float) Math.toRadians(location.getYaw()), 0F, 1F, 0F)));
            display.setTeleportDuration(0);
            display.addScoreboardTag("mcv_garage_station_" + id);
            display.setPersistent(true);
        });
        box.setGravity(true);
        // GTA sell: a hanging "🚗 GARAJ" hologram floats above the terminal - unmissable.
        world.spawn(location.clone().add(0.0D, 1.9D, 0.0D), org.bukkit.entity.TextDisplay.class, sign -> {
            sign.text(net.kyori.adventure.text.Component.text(
                    publicStation ? "🚗  G A R A J  🚗" : "🔒  GARAJ PRIVAT  🔒",
                    publicStation ? net.kyori.adventure.text.format.NamedTextColor.GOLD
                            : net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                    .append(net.kyori.adventure.text.Component.newline())
                    .append(net.kyori.adventure.text.Component.text("condu aici = parchezi singură",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY)));
            sign.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            try {
                sign.setBackgroundColor(org.bukkit.Color.fromARGB(0x60000000));
            } catch (Throwable ignored) {
                // Older Paper without setBackgroundColor: default plate is fine.
            }
            sign.setPersistent(true);
            sign.addScoreboardTag("mcv_garage_station_" + id);
        });
    }

    private void despawnRig(Location location, UUID id) {
        String tag = "mcv_garage_station_" + id;
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2.0D, 3.0D, 2.0D)) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
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
        org.bukkit.configuration.ConfigurationSection section = data.getConfigurationSection("stations");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "stations." + key;
                World world = Bukkit.getWorld(data.getString(path + ".world", "world"));
                if (world == null) {
                    plugin.getLogger().warning("Garage station " + key + " skipped: world not loaded");
                    continue;
                }
                Location location = new Location(world, data.getDouble(path + ".x"),
                        data.getDouble(path + ".y"), data.getDouble(path + ".z"),
                        (float) data.getDouble(path + ".yaw"), 0.0F);
                boolean publicStation = data.getBoolean(path + ".public", true);
                String ownerRaw = data.getString(path + ".owner");
                UUID owner = ownerRaw == null ? null : UUID.fromString(ownerRaw);
                stations.put(id, new Station(location, publicStation, owner));
                spawnRig(id, location, publicStation);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not load garage station " + key, exception);
            }
        }
        plugin.getLogger().info("Loaded " + stations.size() + " garage station(s).");
    }
}
