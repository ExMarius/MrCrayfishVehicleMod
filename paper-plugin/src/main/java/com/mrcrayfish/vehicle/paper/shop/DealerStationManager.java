package com.mrcrayfish.vehicle.paper.shop;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The FiveM dealership MADE OF WORLD: a quartz podium, the featured vehicle spinning on top
 * slowly, a floating price hologram, and "right-click the car = you bought it". The featured
 * vehicle cycles through the whole catalog (~40s each), so the podium always shows something new.
 * Showroom stations persist in dealer.yml; the display vehicles are showcase-only (never saved,
 * never parkable, never drivable - clicking them runs the purchase flow instead).
 */
public final class DealerStationManager {
    private static final long CYCLE_TICKS = 20L * 40;
    private static final float SPIN_STEP = 1.4F;

    private record Station(Location anchor, int catalogIndex, UUID vehicleId, UUID hologramId) {
    }

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final VehicleShop shop;
    private final File dataFile;
    private final List<Station> stations = new ArrayList<>();
    private BukkitTask spinner;
    private BukkitTask cycler;

    public DealerStationManager(VehiclePlugin plugin, VehicleManager vehicles, VehicleShop shop) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.shop = shop;
        this.dataFile = new File(plugin.getDataFolder(), "dealer.yml");
    }

    public void start() {
        load();
        for (Station station : new ArrayList<>(stations)) {
            buildRig(station, true);
        }
        spinner = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Station station : stations) {
                LandVehicle showcase = vehicles.byId(station.vehicleId()).orElse(null);
                if (showcase != null) {
                    float yaw = (showcase.location().getYaw() + SPIN_STEP) % 360.0F;
                    showcase.location().setYaw(yaw);
                }
            }
        }, 5L, 2L);
        cycler = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<String> catalog = shop.catalogIds();
            if (catalog.isEmpty()) {
                return;
            }
            for (Station station : new ArrayList<>(stations)) {
                int next = (station.catalogIndex() + 1) % catalog.size();
                replaceShowcase(station, catalog, next);
            }
        }, CYCLE_TICKS, CYCLE_TICKS);
    }

    public void stop() {
        if (spinner != null) {
            spinner.cancel();
        }
        if (cycler != null) {
            cycler.cancel();
        }
        for (Station station : stations) {
            killHologram(station);
            if (station.vehicleId() != null) {
                vehicles.byId(station.vehicleId()).ifPresent(vehicles::remove);
            }
        }
        stations.clear();
    }

    /** Places a new showroom: quartz platform + spinning showcase + price hologram. */
    public void place(Player player, Location foot) {
        Location anchor = foot.getBlock().getLocation();
        buildPlatform(anchor);
        Station station = new Station(anchor, -1, null, null);
        stations.add(station);
        buildRig(station, false);
        save();
        player.sendRichMessage("<green>Showroom construit!</green> <gray>Mașina de pe podium se "
                + "învârte și se schimbă singură la ~40s. Orice jucător dă click dreapta pe ea și o cumpără.</gray>");
    }

    /** Admin pickup: sneak + punch the featured car -> station removed, podium item back. */
    public boolean tryRemove(Player player, LandVehicle showcase) {
        Station found = null;
        for (Station station : stations) {
            if (showcase.id().equals(station.vehicleId())) {
                found = station;
                break;
            }
        }
        if (found == null) {
            return false;
        }
        killHologram(found);
        vehicles.byId(found.vehicleId()).ifPresent(vehicles::remove);
        clearPlatform(found.anchor());
        stations.remove(found);
        save();
        player.getInventory().addItem(DealerPodiumItem.create());
        player.sendRichMessage("<gold>Showroom ridicat, podiumul ți s-a întors în inventar.</gold>");
        return true;
    }

    private void replaceShowcase(Station station, List<String> catalog, int nextIndex) {
        if (station.vehicleId() != null) {
            vehicles.byId(station.vehicleId()).ifPresent(vehicles::remove);
        }
        Station updated = spawnShowcase(station, catalog, nextIndex);
        stations.set(stations.indexOf(station), updated);
    }

    /** (Re)builds the display rig for a station: showcase car + hologram text with price. */
    private void buildRig(Station station, boolean restore) {
        List<String> catalog = shop.catalogIds();
        if (catalog.isEmpty()) {
            return;
        }
        if (restore) {
            buildPlatform(station.anchor());
            killStaleTextDisplays(station.anchor());
        }
        int index = station.catalogIndex() < 0 ? 0 : station.catalogIndex();
        spawnShowcase(station, catalog, index);
    }

    private Station spawnShowcase(Station station, List<String> catalog, int index) {
        LandVehicleSpec spec = LandVehicleSpec.byId(catalog.get(Math.floorMod(index, catalog.size())));
        Location anchor = station.anchor();
        Location spot = anchor.clone().add(0.5D, 1.05D, 0.5D);
        spot.setYaw(0.0F);
        LandVehicle showcase = vehicles.spawn(spec, spot);
        showcase.setShowcase(true);
        UUID hologramId = station.hologramId();
        if (hologramId == null || Bukkit.getEntity(hologramId) == null) {
            hologramId = spawnHologram(anchor.clone().add(0.5D, 2.6D, 0.5D), spec,
                    shop.priceOf(spec.id()));
        } else {
            Entity existing = Bukkit.getEntity(hologramId);
            if (existing instanceof TextDisplay text) {
                text.text(hologramText(spec, shop.priceOf(spec.id())));
            }
        }
        return new Station(anchor, index, showcase.id(), hologramId);
    }

    private UUID spawnHologram(Location location, LandVehicleSpec spec, double price) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        return world.spawn(location, TextDisplay.class, sign -> {
            sign.text(hologramText(spec, price));
            sign.setBillboard(Display.Billboard.CENTER);
            try {
                sign.setBackgroundColor(Color.fromARGB(0x60000000));
            } catch (Throwable ignored) {
                // Older Paper without setBackgroundColor: default plate is fine.
            }
            sign.setPersistent(true);
        }).getUniqueId();
    }

    private static Component hologramText(LandVehicleSpec spec, double price) {
        return Component.text("🏎️  " + spec.displayName() + "  🏎️", NamedTextColor.GOLD,
                        TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text(VehicleShop.priceText(price), NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("click drept pe mașină = CUMPERI", NamedTextColor.GRAY));
    }

    private void killHologram(Station station) {
        if (station.hologramId() != null) {
            Entity existing = Bukkit.getEntity(station.hologramId());
            if (existing != null) {
                existing.remove();
            }
        }
    }

    private void killStaleTextDisplays(Location anchor) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        Location centre = anchor.clone().add(0.5D, 2.6D, 0.5D);
        for (Entity entity : world.getNearbyEntities(centre, 1.5, 1.5, 1.5)) {
            if (entity instanceof TextDisplay) {
                entity.remove();
            }
        }
    }

    /** 5x5 white-quartz showroom floor, sea-lantern corners; only fills non-solid blocks. */
    private static void buildPlatform(Location anchor) {
        fill(anchor, Material.SMOOTH_QUARTZ, Material.SEA_LANTERN);
    }

    private static void clearPlatform(Location anchor) {
        fill(anchor, Material.AIR, Material.AIR);
    }

    private static void fill(Location anchor, Material floor, Material corner) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block block = anchor.clone().add(dx, 0, dz).getBlock();
                boolean isCorner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                Material wanted = isCorner ? corner : floor;
                if (wanted == null) {
                    continue;
                }
                if (wanted.isAir()) {
                    Material current = block.getType();
                    if (current == Material.SMOOTH_QUARTZ || current == Material.SEA_LANTERN) {
                        block.setType(Material.AIR);
                    }
                } else if (!block.getType().isSolid()) {
                    block.setType(wanted);
                }
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Station station : stations) {
            Location a = station.anchor();
            list.add(a.getWorld().getName() + "," + a.getBlockX() + "," + a.getBlockY() + "," + a.getBlockZ());
        }
        data.set("stations", list);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Nu am putut salva dealer.yml: " + e.getMessage());
        }
    }

    private void load() {
        stations.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        for (String line : data.getStringList("stations")) {
            String[] parts = line.split(",");
            if (parts.length < 4) {
                continue;
            }
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                continue;
            }
            try {
                stations.add(new Station(new Location(world, Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim())), 0, null, null));
            } catch (NumberFormatException ignored) {
                // Corrupt line: skip it, keep loading the rest.
            }
        }
    }
}
