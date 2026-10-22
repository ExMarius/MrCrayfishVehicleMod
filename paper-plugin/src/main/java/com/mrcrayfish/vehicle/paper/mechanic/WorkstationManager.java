package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.MechanicStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bancul de lucru (echivalentul WorkstationBlock din mod) cu regulile de zona:
 *  1) se pune DOAR daca exista un cric/elevator la maxim workstation.max-distance-to-jack
 *     (implicit 5) blocuri; se leaga 1:1 de cel mai apropiat cric;
 *  2) de la un banc pot lua cheie de mecanic MAXIM workstation.max-wrenches (implicit 2)
 *     jucatori, fiecare cate o SINGURA cheie;
 *  3) cheia data de un banc DISPARA din inventar cand jucatorul iese la mai mult de
 *     workstation.service-radius (implicit 10) blocuri de banc (zona de service);
 *     verificarea e rara (o data la 2 secunde) si scadeaza doar jucatorii care au cheie;
 *  4) bancul si cricul sunt accesibile TUTUROR (nu doar plasatorului);
 *  5) shift + click = ridicarea bancului DOAR de catre plasator sau un operator.
 */
public final class WorkstationManager {
    private static final String ENTITY_TAG = "mcv_workstation";
    private static final long CLICK_COOLDOWN_MS = 2_000L;

    /** Starea runtime a unui banc pus in lume. */
    private static final class Station {
        final UUID id;
        final Location location;
        final ItemDisplay display;
        final Interaction hitbox;
        /** Cricul/elevatorul de care bancul e legat 1:1 (null = orfan). */
        UUID jackId;
        /** Cine a pus bancul - pentru pickup (el sau un operator). */
        UUID ownerId;
        /** Jucatorii care au primit cheie de la banc (max workstations.max-wrenches). */
        final Set<UUID> recipients = new LinkedHashSet<>();

        Station(UUID id, Location location, ItemDisplay display, Interaction hitbox,
                UUID jackId, UUID ownerId) {
            this.id = id;
            this.location = location;
            this.display = display;
            this.hitbox = hitbox;
            this.jackId = jackId;
            this.ownerId = ownerId;
        }
    }

    private final VehiclePlugin plugin;
    private final MechanicStore store;
    private final JackManager jacks;
    private final Map<UUID, Station> stations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private BukkitTask leashTask;

    public WorkstationManager(VehiclePlugin plugin, MechanicStore store, JackManager jacks) {
        this.plugin = plugin;
        this.store = store;
        this.jacks = jacks;
    }

    private double maxJackDistance() {
        return plugin.getConfig().getDouble("workstation.max-distance-to-jack", 5.0D);
    }

    private int maxWrenches() {
        return Math.max(1, plugin.getConfig().getInt("workstation.max-wrenches", 2));
    }

    private double serviceRadius() {
        return plugin.getConfig().getDouble("workstation.service-radius", 10.0D);
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ENTITY_TAG)) {
                    entity.remove();
                }
            }
        }
        load();
        // Lezul cheilor: RAR (2 secunde), doar pentru jucatorii care au vreo cheie de
        // mecanic in inventar - niciodata in fiecare tick, niciodata pentru toti.
        leashTask = Bukkit.getScheduler().runTaskTimer(plugin, this::leashWrenches, 40L, 40L);
    }

    public void stop() {
        if (leashTask != null) {
            leashTask.cancel();
        }
        for (Station station : stations.values()) {
            station.display.remove();
            station.hitbox.remove();
        }
        stations.clear();
    }

    private void load() {
        for (MechanicStore.StoredStation stored : store.loadStations("workstations")) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            Station station = spawnRig(stored.id(), new Location(world, stored.x(), stored.y(),
                    stored.z(), stored.yaw(), 0.0F));
            station.jackId = stored.jackId();
            station.ownerId = stored.ownerId();
            station.recipients.addAll(stored.recipients());
            stations.put(station.id, station);
        }
    }

    public List<MechanicStore.StoredStation> snapshot() {
        List<MechanicStore.StoredStation> stored = new ArrayList<>();
        for (Station station : stations.values()) {
            Location location = station.location;
            stored.add(new MechanicStore.StoredStation(station.id, "workstation",
                    location.getWorld().getUID(), location.getX(), location.getY(),
                    location.getZ(), location.getYaw(), station.jackId, station.ownerId,
                    new ArrayList<>(station.recipients)));
        }
        return stored;
    }

    Optional<Station> byId(UUID id) {
        return Optional.ofNullable(stations.get(id));
    }

    /* ============================ plasare / rig ============================ */

    /** Regula de zona #1: fara cric in raza de max-distance-to-jack, bancul NU se pune. */
    public boolean place(Player player, Location clickedFace, float yaw) {
        Location ground = clickedFace.getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        UUID nearestJack = null;
        double nearest = Double.MAX_VALUE;
        double limit = maxJackDistance();
        for (Jack jack : jacks.jacks()) {
            if (jack.location.getWorld() != ground.getWorld()) {
                continue;
            }
            double distance = jack.location.distance(ground);
            if (distance < nearest) {
                nearest = distance;
                nearestJack = jack.id;
            }
        }
        if (nearestJack == null || nearest > limit) {
            player.sendRichMessage("<red>Bancul are nevoie de un <gold>cric</gold> la maxim "
                    + (int) limit + " blocuri - pune intai cricul, apoi masa de lucru.</red>");
            return false;
        }
        Station station = spawnRig(UUID.randomUUID(), ground);
        station.jackId = nearestJack;
        station.ownerId = player.getUniqueId();
        stations.put(station.id, station);
        player.sendRichMessage("<gray>Banc de lucru pus jos (<gold>legat</gold> de cricul de langa). "
                + "Click pe el = cheia de mecanic.</gray>");
        return true;
    }

    private Station spawnRig(UUID id, Location ground) {
        World world = ground.getWorld();
        // Modelul e un bloc directional: yaw fixat la multiplu de 90 grade + rotire
        // aplicata in transformarea display-ului (altfel apare "pe jumatate" - BUG 2b).
        float snapped = JackManager.snapYaw(ground.getYaw());
        Location spawnAt = ground.clone();
        spawnAt.setYaw(0.0F);
        spawnAt.setPitch(0.0F);
        ItemDisplay display = world.spawn(spawnAt, ItemDisplay.class, spawned -> {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(new NamespacedKey("vehicle", "mechanic_workstation"));
            stack.setItemMeta(meta);
            spawned.setItemStack(stack);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setInvulnerable(true);
            spawned.setPersistent(false);
            spawned.setShadowRadius(0.2F);
            JackManager.applyBlockTransform(spawned, snapped);
            spawned.addScoreboardTag(ENTITY_TAG);
            spawned.addScoreboardTag("mcv_" + id);
        });
        Interaction hitbox = world.spawn(spawnAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.2F);
            spawned.setInteractionHeight(1.5F);
            spawned.setResponsive(true);
            spawned.setPersistent(false);
            spawned.addScoreboardTag(ENTITY_TAG);
            spawned.addScoreboardTag("mcv_" + id);
        });
        Location stored = ground.clone();
        stored.setYaw(snapped);
        return new Station(id, stored, display, hitbox, null, null);
    }

    public boolean isWorkstationEntity(Entity entity) {
        return entity != null && findByEntity(entity).isPresent();
    }

    private Optional<Station> findByEntity(Entity entity) {
        for (Station station : stations.values()) {
            if (station.hitbox.getUniqueId().equals(entity.getUniqueId())
                    || station.display.getUniqueId().equals(entity.getUniqueId())) {
                return Optional.of(station);
            }
        }
        return Optional.empty();
    }

    /* ============================ click ============================ */

    /** Click pe banc: shift = ridicare inapoi (doar plasator/op); altfel cheia de
     *  mecanic, cu limitele de zona (max 2 jucatori, cate o singura cheie). */
    public boolean handleClick(Player player, Entity entity) {
        Optional<Station> found = findByEntity(entity);
        if (found.isEmpty()) {
            return false;
        }
        Station station = found.get();
        if (player.isSneaking()) {
            boolean allowed = player.isOp()
                    || (station.ownerId != null && station.ownerId.equals(player.getUniqueId()));
            if (!allowed) {
                player.sendRichMessage("<red>Doar cel care a pus bancul (sau un operator) il poate ridica.</red>");
                return true;
            }
            pickup(station);
            player.getInventory().addItem(MechanicItems.workstation());
            player.sendRichMessage("<gray>Ai ridicat bancul de lucru.</gray>");
            return true;
        }
        if (!MechanicManager.has(player, "mcv.workstation.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.workstation.use.</red>");
            return true;
        }
        long now = System.currentTimeMillis();
        Long previous = cooldowns.get(player.getUniqueId());
        if (previous != null && now - previous < CLICK_COOLDOWN_MS) {
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);
        // Regula #2: o singura cheie DE LA ACEST banc per jucator, la orice moment.
        for (ItemStack contents : player.getInventory().getContents()) {
            if (contents == null) {
                continue;
            }
            UUID origin = MechanicItems.wrenchStationOf(contents);
            if (origin != null && origin.equals(station.id)) {
                player.sendRichMessage("<yellow>Ai deja cheia acestui banc in inventar.</yellow>");
                return true;
            }
        }
        // Regula #3: nu mai mult de maxWrenches jucatori de la acelasi banc.
        boolean isRecipient = station.recipients.contains(player.getUniqueId());
        if (!isRecipient) {
            if (station.recipients.size() >= maxWrenches()) {
                player.sendRichMessage("<red>Acest banc si-a dat deja toate cheile "
                        + "(" + maxWrenches() + " mecanici). Asteapta sa le intoarca cineva.</red>");
                return true;
            }
            station.recipients.add(player.getUniqueId());
        }
        player.getInventory().addItem(MechanicItems.wrench(station.id));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.0F);
        player.sendRichMessage("<gray>Ai primit <gold>cheia de mecanic</gold> de la bancul de lucru. "
                + "Ramai in zona de service!</gray>");
        return true;
    }

    private void pickup(Station station) {
        station.display.remove();
        station.hitbox.remove();
        stations.remove(station.id);
    }

    public boolean handleJackRemoved(UUID jackId) {
        boolean changed = false;
        for (Station station : stations.values()) {
            if (jackId != null && jackId.equals(station.jackId)) {
                station.jackId = null;
                changed = true;
            }
        }
        return changed;
    }

    /* ==================== lezul cheilor (2 secunde) ==================== */

    private void leashWrenches() {
        double radius = serviceRadius();
        double radiusSquared = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            boolean anyWrench = false;
            for (ItemStack contents1 : contents) {
                if (contents1 != null && MechanicItems.kindOf(contents1) == MechanicItems.Kind.WRENCH) {
                    anyWrench = true;
                    break;
                }
            }
            if (!anyWrench) {
                continue;
            }
            boolean removed = false;
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                UUID origin = stack == null ? null : MechanicItems.wrenchStationOf(stack);
                if (origin == null) {
                    continue;
                }
                Optional<Station> station = byId(origin);
                if (station.isPresent()
                        && station.get().location.getWorld() == player.getWorld()
                        && station.get().location.distanceSquared(player.getLocation()) <= radiusSquared) {
                    continue;
                }
                player.getInventory().clear(slot);
                removed = true;
            }
            if (removed) {
                player.sendRichMessage("<yellow>Ai iesit din zona de service - cheia de "
                        + "mecanic s-a intors la bancul ei.</yellow>");
            }
        }
    }

}
