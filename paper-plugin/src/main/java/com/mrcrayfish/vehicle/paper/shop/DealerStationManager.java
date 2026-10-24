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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * STATIC Showroom podium (user design: minimum consumption): /vehicle podium set <vehicul> <pret>
 * places a quartz platform with EXACTLY that vehicle on top, standing still - no spin task,
 * no cycling, nothing ticking (the showcase car also skips its physics tick). It's a pure
 * day-use display: right-click the car = buy it, sneak+punch as admin = pick the podium up.
 */
public final class DealerStationManager {
    private record Station(Location anchor, String specId, UUID vehicleId, UUID hologramId,
                           double price) {
    }

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final VehicleShop shop;
    private final File dataFile;
    private final List<Station> stations = new ArrayList<>();

    public DealerStationManager(VehiclePlugin plugin, VehicleManager vehicles, VehicleShop shop) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.shop = shop;
        this.dataFile = new File(plugin.getDataFolder(), "dealer.yml");
    }

    public void start() {
        load();
        // FIX (bug restart-dublare): pas IDEMPOTENT per podium. Niciodata nu
        // respawnam orb o masina noua; mai intai ADOPTAM ce e deja acolo (dupa id
        // persistat, altfel showcase-ul gasit pe podium) si REPARAM duplicatele
        // lasate de versiunile vechi (orice showcase suplimentar din jur e sters).
        for (Station station : new ArrayList<>(stations)) {
            buildPlatform(station.anchor());
            killStaleTextDisplays(station.anchor());
            ensureShowcase(station);
        }
        save(); // id-urile (re)adoptate intra imediat pe disc
    }

    public void stop() {
        // Oprire curata: hologramele se sterg (vizual); masinile podiumurilor sunt
        // scoase explicit DUPA ID-ul persistat (nu din memorie volatila).
        for (Station station : stations) {
            killHologram(station);
            if (station.vehicleId() != null) {
                vehicles.byId(station.vehicleId()).ifPresent(vehicles::remove);
            }
        }
        save();
        stations.clear();
    }

    /** Item-based placement (podium in hand): price falls back to the global shop config. */
    public boolean place(Player player, Location foot, String specId) {
        return place(player, foot, specId, shop.priceOf(specId));
    }

    /** Places a new static showroom; returns false when another is closer than 8 blocks. */
    public boolean place(Player player, Location foot, String specId, double price) {
        LandVehicleSpec spec = LandVehicleSpec.byId(specId);
        if (spec == null) {
            player.sendRichMessage("<red>Tip necunoscut: " + specId + ".</red> <gray>Scrie <tab> pentru lista.</gray>");
            return false;
        }
        Location anchor = foot.getBlock().getLocation();
        for (Station station : stations) {
            Location other = station.anchor();
            if (other.getWorld() == anchor.getWorld() && other.distanceSquared(anchor) < 64.0D) {
                player.sendRichMessage("<red>Aici e deja un showroom (minim 8 blocuri distanta).</red>");
                return false;
            }
        }
        buildPlatform(anchor);
        Station station = new Station(anchor, specId, null, null, price);
        stations.add(station);
        rebuild(station);
        save();
        player.sendRichMessage("<green>Showroom STATIC construit cu: " + spec.displayName()
                + ".</green> <gray>Sta nemiscata, zero consum. Click drept pe ea = cumperi.</gray>");
        return true;
    }

    /** Admin pickup: sneak + punch the displayed car -> station removed, podium item back. */
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
        player.sendRichMessage("<gold>Showroom ridicat, podiumul ti s-a intors in inventar.</gold>");
        return true;
    }

    /** /vehicle podium remove: the station under/near the admin (max 6 blocks) dissolves. */
    public boolean removeAt(Player player) {
        Station found = null;
        double best = 6.0D * 6.0D;
        for (Station station : stations) {
            Location anchor = station.anchor();
            if (anchor.getWorld() != player.getWorld()) {
                continue;
            }
            double distance = anchor.distanceSquared(player.getLocation());
            if (distance <= best) {
                best = distance;
                found = station;
            }
        }
        if (found == null) {
            return false;
        }
        killHologram(found);
        if (found.vehicleId() != null) {
            vehicles.byId(found.vehicleId()).ifPresent(vehicles::remove);
        }
        clearPlatform(found.anchor());
        stations.remove(found);
        save();
        return true;
    }

    /** Per-podium price (null = fallback to the global shop price). */
    public Double priceForVehicle(UUID vehicleId) {
        for (Station station : stations) {
            if (vehicleId.equals(station.vehicleId())) {
                return station.price();
            }
        }
        return null;
    }

    private void rebuild(Station station) {
        sweepLeftovers(station.anchor());
        killStaleTextDisplays(station.anchor());
        ensureShowcase(station);
    }

    /** Garanteaza EXACT O masina showcase pe podium, idempotent (nucleul fixului):
     *  1) daca id-ul persistat e inca incarcat in lume -> nu facem nimic;
     *  2) altfel adoptam showcase-ul aflat pe podium (self-heal dupa crash/kill
     *     fara onDisable), STERGAND orice duplicate suplimentare din jur;
     *  3) altfel spawnam exact una noua. Cost: zero task-uri, zero tick. */
    private void ensureShowcase(Station station) {
        Location spot = station.anchor().clone().add(0.5D, 1.05D, 0.5D);
        // (1) adoptare dupa id persistat
        if (station.vehicleId() != null) {
            java.util.Optional<LandVehicle> known = vehicles.byId(station.vehicleId());
            if (known.isPresent()) {
                known.get().setShowcase(true);
                respawnHologram(station);
                return;
            }
        }
        // (2) adoptare pe loc + repararea duplicatelor
        java.util.List<LandVehicle> found = vehicles.showcasesWithin(spot, 2.0D);
        if (!found.isEmpty()) {
            LandVehicle adopted = found.get(0);
            adopted.setShowcase(true);
            int index = stations.indexOf(station);
            stations.set(index, new Station(station.anchor(), station.specId(),
                    adopted.id(), station.hologramId(), station.price()));
            for (int i = 1; i < found.size(); i++) {
                vehicles.remove(found.get(i)); // duplicatele moarte ale bug-ului vechi
            }
            respawnHologram(stations.get(index));
            return;
        }
        // (3) spawn unic
        spawnFresh(station);
    }

    private void respawnHologram(Station station) {
        LandVehicleSpec spec = LandVehicleSpec.byId(station.specId());
        if (spec == null) {
            spec = LandVehicleSpec.byId("sports_car");
        }
        killHologram(station);
        UUID hologramId = spawnHologram(station.anchor().clone().add(0.5D, 2.6D, 0.5D),
                spec, station.price());
        int index = stations.indexOf(station);
        stations.set(index, new Station(station.anchor(), station.specId(),
                station.vehicleId(), hologramId, station.price()));
    }

    private void spawnFresh(Station station) {
        LandVehicleSpec spec = LandVehicleSpec.byId(station.specId());
        if (spec == null) {
            spec = LandVehicleSpec.byId("sports_car");
        }
        Location anchor = station.anchor();
        Location spot = anchor.clone().add(0.5D, 1.05D, 0.5D);
        spot.setYaw(0.0F);
        LandVehicle showcase = vehicles.spawn(spec, spot);
        // ANTI-FURT: showcase refortat la orice (re)creere.
        showcase.setShowcase(true);
        UUID hologramId = spawnHologram(anchor.clone().add(0.5D, 2.6D, 0.5D), spec,
                station.price());
        int index = stations.indexOf(station);
        stations.set(index, new Station(anchor, station.specId(), showcase.id(), hologramId,
                station.price()));
    }

    private UUID spawnHologram(Location location, LandVehicleSpec spec, double price) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        return world.spawn(location, TextDisplay.class, sign -> {
            sign.text(Component.text("🏎️  " + spec.displayName() + "  🏎️", NamedTextColor.GOLD,
                            TextDecoration.BOLD)
                    .append(Component.newline())
                    .append(Component.text(VehicleShop.priceText(price), NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("click drept pe masina = CUMPERI", NamedTextColor.GRAY)));
            sign.setBillboard(Display.Billboard.CENTER);
            try {
                sign.setBackgroundColor(Color.fromARGB(0x60000000));
            } catch (Throwable ignored) {
                // Older Paper without setBackgroundColor: default plate is fine.
            }
            sign.setPersistent(true);
            sign.addScoreboardTag("mcv_podium_hologram");
        }).getUniqueId();
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

    /** Removes any leftover showcase cars and orphaned rig entities around the podium. */
    private void sweepLeftovers(Location anchor) {
        Location centre = anchor.clone().add(0.5D, 1.0D, 0.5D);
        for (LandVehicle vehicle : vehicles.vehiclesWithin(centre, 4.5D)) {
            if (vehicle.showcase()) {
                vehicles.remove(vehicle);
            }
        }
        if (centre.getWorld() == null) {
            return;
        }
        for (Entity entity : centre.getWorld().getNearbyEntities(centre, 3.0D, 3.0D, 3.0D)) {
            if ((entity instanceof org.bukkit.entity.ItemDisplay
                    || entity instanceof org.bukkit.entity.Interaction)
                    && vehicles.byEntity(entity).isEmpty()) {
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
            list.add(a.getWorld().getName() + "," + a.getBlockX() + "," + a.getBlockY() + ","
                    + a.getBlockZ() + "," + station.specId() + "," + station.price() + ","
                    + (station.vehicleId() == null ? "" : station.vehicleId()));
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
            String specId = parts.length >= 5 ? parts[4] : "sports_car";
            try {
                Location anchor = new Location(world, Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
                boolean duplicate = false;
                for (Station existing : stations) {
                    if (existing.anchor().distanceSquared(anchor) < 2.25D) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    double price = parts.length >= 6 ? Double.parseDouble(parts[5].trim())
                            : shop.priceOf(specId);
                    UUID knownVehicle = null;
                    if (parts.length >= 7 && !parts[6].trim().isEmpty()) {
                        knownVehicle = UUID.fromString(parts[6].trim());
                    }
                    stations.add(new Station(anchor, specId, knownVehicle, null, price));
                }
            } catch (NumberFormatException ignored) {
                // Corrupt line: skip it, keep loading the rest.
            }
        }
    }
}
