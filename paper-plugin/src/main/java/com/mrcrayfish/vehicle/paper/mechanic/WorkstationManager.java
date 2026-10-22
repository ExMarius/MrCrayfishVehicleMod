package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.MechanicStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bancul de lucru (echivalentul WorkstationBlock din mod) ca BLOC SOLID real -
 * misiunea anti-lag pentru 200+ vehicule: NICIO entitate, niciun tick per statie.
 * Doar o sarcina RARA (2 secunde) pentru lezul cheilor, exact ca inainte.
 * Regulile de zona raman identice cu varianta pe entitati:
 *  1) se pune DOAR daca exista un cric/elevator la maxim workstation.max-distance-to-jack
 *     (implicit 5) blocuri; se leaga 1:1 de cel mai apropiat cric;
 *  2) de la un banc pot lua cheie de mecanic MAXIM workstation.max-wrenches (implicit 2)
 *     jucatori, fiecare cate o SINGURA cheie;
 *  3) cheia data de un banc DISPARA din inventar cand jucatorul iese la mai mult de
 *     workstation.service-radius (implicit 10) blocuri de banc;
 *  4) bancul si cricul sunt accesibile TUTUROR (nu doar plasatorului);
 *  5) ghemuit + click dreapta = ridicarea bancului DOAR de catre plasator sau un operator.
 * Blocul folosit = note_block suprascris in pack (vezi StationBlocks): clicul-dreapta
 * e anulat (nu se retuneaza), pling-ul de redstone si spargerea directa sunt blocate.
 */
public final class WorkstationManager {
    private static final String ENTITY_TAG = "mcv_workstation"; // doar pentru curatarea rigurilor vechi
    private static final long CLICK_COOLDOWN_MS = 2_000L;

    /** Starea runtime a unui banc pus in lume: positions.goian <=> bloc solid. */
    private static final class Station {
        final UUID id;
        final Location location; // centrul blocului (x+0.5, y, z+0.5)
        /** Cricul/elevatorul de care bancul e legat 1:1 (null = orfan). */
        UUID jackId;
        /** Cine a pus bancul - pentru pickup (el sau un operator). */
        UUID ownerId;
        /** Jucatorii care au primit cheie de la banc (max workstations.max-wrenches). */
        final Set<UUID> recipients = new LinkedHashSet<>();

        Station(UUID id, Location location, UUID jackId, UUID ownerId) {
            this.id = id;
            this.location = location;
            this.jackId = jackId;
            this.ownerId = ownerId;
        }

        Block block() {
            return location.getBlock();
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
        // Curatare O SINGURA DATA a rigurilor vechi pe entitati (migrare la blocuri solide).
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
        stations.clear();
    }

    private void load() {
        for (MechanicStore.StoredStation stored : store.loadStations("workstations")) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            Station station = placeBlockRig(stored.id(), new Location(world,
                    stored.x(), stored.y(), stored.z()));
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
                    location.getZ(), 0.0F, station.jackId, station.ownerId,
                    new ArrayList<>(station.recipients)));
        }
        return stored;
    }

    Optional<Station> byId(UUID id) {
        return Optional.ofNullable(stations.get(id));
    }

    /** Statia de la aceasta pozitie de bloc (sau gol daca blocul nu e al nostru). */
    public boolean isStationBlock(Block block) {
        return findByBlock(block).isPresent();
    }

    private Optional<Station> findByBlock(Block block) {
        if (block == null || StationBlocks.kindOf(block) != StationBlocks.Kind.WORKSTATION) {
            return Optional.empty();
        }
        for (Station station : stations.values()) {
            if (station.location.getWorld() == block.getWorld()
                    && station.block().getLocation().equals(block.getLocation())) {
                return Optional.of(station);
            }
        }
        return Optional.empty();
    }

    /* ============================ plasare / rig ============================ */

    /** Regula de zona #1: fara cric in raza de max-distance-to-jack, bancul NU se pune. */
    public boolean place(Player player, Location clickedFace, float yaw) {
        Block target = clickedFace.getBlock();
        if (!target.getType().isAir() && !target.isReplaceable()) {
            player.sendRichMessage("<red>Aici nu e loc liber pentru bancul de lucru.</red>");
            return false;
        }
        Location ground = target.getLocation().add(0.5D, 0.0D, 0.5D);
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
        Station station = placeBlockRig(UUID.randomUUID(), target.getLocation());
        station.jackId = nearestJack;
        station.ownerId = player.getUniqueId();
        stations.put(station.id, station);
        player.sendRichMessage("<gray>Banc de lucru pus jos (<gold>legat</gold> de cricul de langa). "
                + "Click pe el = cheia de mecanic.</gray>");
        return true;
    }

    /** In locul rigului ItemDisplay+Interaction: un bloc solid care nu costa serverul NIMIC. */
    private Station placeBlockRig(UUID id, Location blockPos) {
        Block block = blockPos.getBlock();
        block.setBlockData(StationBlocks.WORKSTATION, false);
        Location center = block.getLocation().add(0.5D, 0.0D, 0.5D);
        return new Station(id, center, null, null);
    }

    /* ============================ click pe bloc ============================ */

    /** Click-dreapta pe bancul-bloc (apelat din MechanicListener dupa anularea
     *  tune-arii note_block):
     *   - ghemuit = ridicare inapoi (doar plasator/op);
     *   - cu CHEIA in mana = ridicare/coborare a masinii de pe cricul legat (ca in mod);
     *   - cu mana goala = primesti cheia de mecanic, cu limitele de zona. */
    public boolean handleBlockClick(Player player, Block block, ItemStack held) {
        Optional<Station> found = findByBlock(block);
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
        if (MechanicItems.kindOf(held) == MechanicItems.Kind.WRENCH) {
            handleLiftToggle(player, station);
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

    /** Cheia pe banc: ridici/cobori masina aflata deasupra cricului legat. */
    private void handleLiftToggle(Player player, Station station) {
        Optional<Jack> jack = station.jackId == null ? Optional.empty() : jacks.byId(station.jackId);
        if (jack.isEmpty()) {
            // Cricul legat a disparut: incercam sa relegam la cel mai apropiat din zona.
            double nearest = Double.MAX_VALUE;
            Jack found = null;
            for (Jack candidate : jacks.jacks()) {
                if (candidate.location.getWorld() != station.location.getWorld()) {
                    continue;
                }
                double distance = candidate.location.distance(station.location);
                if (distance < nearest) {
                    nearest = distance;
                    found = candidate;
                }
            }
            if (found == null || nearest > maxJackDistance()) {
                player.sendRichMessage("<red>Bancul nu mai are cric in zona - pune unul langa el.</red>");
                return;
            }
            station.jackId = found.id;
            jack = Optional.of(found);
        }
        jacks.toggleLift(player, jack.get());
    }

    private void pickup(Station station) {
        station.block().setType(Material.AIR, false);
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
