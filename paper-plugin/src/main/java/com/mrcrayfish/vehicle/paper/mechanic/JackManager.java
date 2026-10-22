package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.MechanicStore;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cricul/elevatorul ca BLOC SOLID real (misiunea anti-lag: 200+ vehicule) - zero
 * entitati per statie; doar blocurile lumii + UN task global 1-tick care misca
 * cricele active (idle = cost zero, fara scanari automate). Ca in mod: blocul are
 * doua fete vizuale, JOS (cap impaturit) si SUS (cap ridicat +0.5 = lift-height
 * implicit), schimbate la jumatatea animatiei; masina urca/coboara fin pe
 * jack.lift-speed-ticks si intra in starea „pe elevator" (fizica inghetata).
 * Pozitiile de stat SUNT cele din mod (vehicul montat pe capul cricului):
 * jos = PE suprafata slab-ului (+0.5), sus = +0.5 mai sus (+1.0), deci capul
 * nu strabate niciodata caroseria - nici la masinile joase.
 * Actiunea e EXCLUSIV manuala, cu cheia de mecanic pe cric (cerere ciclu 6:
 * nimic nu se ridica singur - nici la parcare, nici la coborarea din masina);
 * bancul de lucru legat poate actiona si el cricul (vechiul flux, pastrat).
 * Sunetele head_up/head_down/air_wrench_gun sunt cele originale.
 * Ghemuit + click = ridici cricul (doar plasator/op si doar gol si complet jos);
 * spart = se sparge ca un bloc normal si iti da itemul inapoi (doar gol si jos).
 */
public final class JackManager {
    public static final String ENTITY_TAG = "mcv_jack"; // doar pentru curatarea rigurilor vechi
    private static final Key HEAD_UP = Key.key("vehicle:block.jack.head_up");
    private static final Key HEAD_DOWN = Key.key("vehicle:block.jack.head_down");
    private static final Key AIR_WRENCH = Key.key("vehicle:block.jack.air_wrench_gun");

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final MechanicStore store;
    private final Map<UUID, Jack> jacks = new ConcurrentHashMap<>();
    /** Notifica sistemul cand un cric dispare (bancurile legate de el se dezleaga). */
    private java.util.function.Consumer<UUID> removedHook = id -> { };
    private BukkitTask tickTask;

    public JackManager(VehiclePlugin plugin, VehicleManager vehicles, MechanicStore store) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.store = store;
    }

    private double liftHeight() {
        // Inaltimea maxima: jack.lift-height (implicit 0.5 blocuri, ca in mod).
        return Math.max(0.1D, plugin.getConfig().getDouble("jack.lift-height", 0.5D));
    }

    private int liftTicks() {
        return Math.max(5, plugin.getConfig().getInt("jack.lift-speed-ticks", 50));
    }

    public void start() {
        // Curatare O SINGURA DATA a rigurilor vechi pe entitati (migrare la blocuri).
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ENTITY_TAG)) {
                    entity.remove();
                }
            }
        }
        load();
        // Singurul task: animatia cricelor ACTIVE (LIFTING/LOWERING). Idle nu face nimic.
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        jacks.clear();
    }

    private void load() {
        for (MechanicStore.StoredJack stored : store.loadJacks()) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            Location location = new Location(world, stored.x(), stored.y(), stored.z());
            Jack jack = placeBlockRig(stored.id(), location, stored.raised());
            jack.ownerId = stored.ownerId();
            jack.progress = stored.raised() ? 1.0D : 0.0D;
            if (stored.vehicleId() != null) {
                jack.vehicleId = stored.vehicleId();
                jack.state = stored.raised() ? Jack.State.LIFTED : Jack.State.DOWN;
                // Relegam masina salvata pe cric: aceeasi inaltime, fizica inghetata.
                Optional<LandVehicle> vehicle = vehicles.byId(stored.vehicleId());
                vehicle.ifPresent(found -> {
                    found.setJackState(stored.raised()
                            ? LandVehicle.JackState.LIFTED : LandVehicle.JackState.NONE);
                    found.setJackId(jack.id);
                    if (stored.raised()) {
                        found.setLiftedY(jack.location.getY() + REST_OFFSET + liftHeight());
                    } else {
                        found.setLiftedY(jack.location.getY() + REST_OFFSET);
                    }
                });
            }
            jacks.put(jack.id, jack);
        }
    }

    public List<MechanicStore.StoredJack> snapshot() {
        List<MechanicStore.StoredJack> stored = new ArrayList<>();
        for (Jack jack : jacks.values()) {
            World world = jack.location.getWorld();
            stored.add(new MechanicStore.StoredJack(jack.id, world.getUID(),
                    jack.location.getX(), jack.location.getY(), jack.location.getZ(),
                    0.0F, jack.raised(), jack.vehicleId, jack.ownerId));
        }
        return stored;
    }

    /* ============================ plasare / rig ============================ */

    public boolean place(Player player, Location clickedFace, float yaw) {
        Block target = clickedFace.getBlock();
        if (!target.getType().isAir() && !target.isReplaceable()) {
            player.sendRichMessage("<red>Aici nu e loc liber pentru cric.</red>");
            return false;
        }
        UUID id = UUID.randomUUID();
        Jack jack = placeBlockRig(id, target.getLocation(), false);
        jack.ownerId = player.getUniqueId();
        jacks.put(id, jack);
        player.sendRichMessage("<gray>Cric pus jos. Parcheaza masina deasupra si da cu "
                + "<gold>cheia de mecanic</gold> pe cric ca sa o ridici.</gray>");
        return true;
    }

    /** In locul rigului de 3 entitati: un bloc solid care nu costa serverul NIMIC. */
    private Jack placeBlockRig(UUID id, Location blockPos, boolean raised) {
        Block block = blockPos.getBlock();
        block.setBlockData(raised ? StationBlocks.JACK_UP : StationBlocks.JACK_DOWN, false);
        Location center = block.getLocation().add(0.5D, 0.0D, 0.5D);
        return new Jack(id, center);
    }

    /* ==================== helperi statici (folositi si de mixer/props) ==================== */

    /** Cea mai apropiata rotire de 90 grade (ca FACING la blocurile directionale). */
    static float snapYaw(float yaw) {
        float snapped = Math.round(yaw / 90.0F) * 90.0F;
        return normalizeYaw(snapped);
    }

    /** Modelul de bloc (origine in colt, geometrie 0..1) centrat pe entitate pe
     *  orizontala; baza ramane pe sol (y 0). Folosit inca de rigurile pe entitati
     *  ale mixerului si props-urilor. */
    static void applyBlockTransform(ItemDisplay display) {
        applyBlockTransform(display, 0.0F);
    }

    static void applyBlockTransform(ItemDisplay display, float yaw) {
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setInvulnerable(true);
        display.setPersistent(false);
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(yaw));
        display.setTransformation(new Transformation(new Vector3f(-0.5F, 0.0F, -0.5F),
                rotation, new Vector3f(1.0F), new Quaternionf()));
    }

    private static float normalizeYaw(float yaw) {
        float wrapped = yaw % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    /* ============================ cautari ============================ */

    public void setRemovedHook(java.util.function.Consumer<UUID> removedHook) {
        this.removedHook = removedHook == null ? id -> { } : removedHook;
    }

    public Optional<Jack> byId(UUID id) {
        return Optional.ofNullable(jacks.get(id));
    }

    public Collection<Jack> jacks() {
        return Collections.unmodifiableCollection(jacks.values());
    }

    /** Cricul al carui bloc e acesta (doar blocurile JACK_DOWN/JACK_UP inregistrate). */
    public Optional<Jack> findByBlock(Block block) {
        StationBlocks.Kind kind = StationBlocks.kindOf(block);
        if (block == null || (kind != StationBlocks.Kind.JACK_DOWN && kind != StationBlocks.Kind.JACK_UP)) {
            return Optional.empty();
        }
        for (Jack jack : jacks.values()) {
            if (jack.location.getWorld() == block.getWorld()
                    && jack.block().getLocation().equals(block.getLocation())) {
                return Optional.of(jack);
            }
        }
        return Optional.empty();
    }

    public boolean isJackBlock(Block block) {
        return findByBlock(block).isPresent();
    }

    /* ============================ interactiune ============================ */

    /** Cea mai apropiata masina deasupra cricului (folosit pentru legarea la ridicare). */
    public Optional<LandVehicle> vehicleOver(Jack jack, double range) {
        return vehicles.nearest(jack.location, range)
                .filter(vehicle -> !vehicle.onJack() && !vehicle.showcase());
    }

    /** Click-dreapta pe cricul-bloc: CHEIA pompeaza cricul DIRECT (ca in mod);
     *  ghemuit + click = ridici cricul (owner/op, gol, complet jos). */
    public boolean handleBlockClick(Player player, Block block, ItemStack held) {
        Optional<Jack> found = findByBlock(block);
        if (found.isEmpty()) {
            return false;
        }
        Jack jack = found.get();
        if (player.isSneaking()) {
            return tryPickup(player, jack);
        }
        if (MechanicItems.kindOf(held) == MechanicItems.Kind.WRENCH) {
            return toggleLift(player, jack);
        }
        player.sendRichMessage("<gray>Parcheaza masina deasupra, apoi da cu "
                + "<gold>cheia de mecanic</gold> pe cric: se aliniaza si se ridica (ca in mod). "
                + "Ghemuit + click = strangi cricul gol.</gray>");
        return true;
    }

    /** Shift + click = ridici cricul inapoi: DOAR cel care l-a pus sau un operator,
     *  si DOAR cand e gol (fara masina) si complet jos. Nimeni altcineva nu-l ia. */
    public boolean tryPickup(Player player, Jack jack) {
        if (jack.vehicleId != null || jack.state != Jack.State.DOWN) {
            player.sendRichMessage("<red>Cricul e in uz - mai intai coboara si da jos masina.</red>");
            return true;
        }
        boolean allowed = player.isOp()
                || (jack.ownerId != null && jack.ownerId.equals(player.getUniqueId()));
        if (!allowed) {
            player.sendRichMessage("<red>Doar cel care a pus cricul (sau un operator) il poate ridica.</red>");
            return true;
        }
        removeJack(jack);
        player.getInventory().addItem(MechanicItems.jack());
        player.sendRichMessage("<gray>Ai ridicat cricul.</gray>");
        return true;
    }

    /** Spart manual (BlockBreakEvent): ca un bloc normal - iti DA itemul inapoi,
     *  dar NU se poate sparge cat e ridicat sau cu masina pe el. */
    public void handleBlockBreak(org.bukkit.event.block.BlockBreakEvent event, Jack jack) {
        if (jack == null) {
            // Bloc-statie fara inregistrare (stare straina): nu lasa dropul vanilla de note_block.
            event.setDropItems(false);
            return;
        }
        if (jack.vehicleId != null || jack.state != Jack.State.DOWN) {
            event.setCancelled(true);
            event.getPlayer().sendRichMessage(
                    "<red>Nu poti sparge cricul cat e ridicat sau cu masina pe el.</red>");
            return;
        }
        event.setDropItems(false);
        removeJack(jack);
        Block block = event.getBlock();
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D),
                MechanicItems.jack());
        event.getPlayer().sendRichMessage("<gray>Cric strans - ai primit itemul inapoi.</gray>");
    }

    private void removeJack(Jack jack) {
        jack.block().setType(Material.AIR, false);
        jacks.remove(jack.id);
        removedHook.accept(jack.id);
    }

    /** Ridicare / coborare (animat pe jack.lift-speed-ticks) - cu cheia, direct pe
     *  cric (ca in mod) sau din bancul legat. */
    public boolean toggleLift(Player player, Jack jack) {
        if (!MechanicManager.has(player, "mcv.jack.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.jack.use.</red>");
            return true;
        }
        switch (jack.state) {
            case DOWN -> {
                // Ridicarea e EXCLUSIV manuala (cerere ciclu 6): cheie pe cric =
                // aliniere la central + ridicare, exact montarea din mod.
                LandVehicle target = snapCandidate(jack).orElse(null);
                if (target != null && target.occupied()) {
                    player.sendRichMessage(
                            "<red>Nu poți ridica mașina cât timp cineva se află în ea!</red>");
                    return true;
                }
                if (target != null) {
                    beginSnap(jack, target);
                    player.sendRichMessage("<gray>Aliniez si ridic masina pe cric... nu o misca.</gray>");
                } else {
                    jack.state = Jack.State.LIFTING;
                    playSound(jack, HEAD_UP, 0.9F, 1.0F);
                    player.sendRichMessage("<gray>Ridici cricul gol...</gray>");
                }
            }
            case SNAPPING ->
                    player.sendRichMessage("<yellow>Mașina se aliniază pe cric - stai departe.</yellow>");
            case LIFTED -> {
                Optional<LandVehicle> target = linked(jack);
                if (target.isPresent() && target.get().occupied()) {
                    player.sendRichMessage(
                            "<red>Nu poți coborî mașina cât timp cineva se află în ea!</red>");
                    return true;
                }
                jack.state = Jack.State.LOWERING;
                target.ifPresent(vehicle -> vehicle.setJackState(LandVehicle.JackState.LOWERING));
                playSound(jack, HEAD_DOWN, 0.9F, 1.0F);
                player.sendRichMessage("<gray>Cobor masina de pe cric...</gray>");
            }
            case LIFTING, LOWERING ->
                    player.sendRichMessage("<yellow>Cricul se misca - stai departe.</yellow>");
        }
        return true;
    }

    /* ============================ animatie (tick) ============================ */

    private static final double SNAP_RANGE = 1.6D;
    private static final double SNAP_DONE_SQ = 0.15D * 0.15D;
    private static final int SNAP_MAX_TICKS = 40;
    /** Cat sta masina PESTE cric in repaus (fata de sus a slab-ului jos = 0.5).
     *  In mod vehiculul stationeaza ca pasager la ~11px deasupra blocului; ale noastre
     *  0.5 = exact suprafata superioara a elevatorului jos (nu intra in model). */
    private static final double REST_OFFSET = 0.5D;

    private void tick() {
        int ticks = liftTicks();
        for (Jack jack : jacks.values()) {
            switch (jack.state) {
                case DOWN, LIFTED -> {
                    // Stari stationare - zero scanari, zero cost (cerere ciclu 6:
                    // nimic automat; ridicarea e exclusiv cu cheia, din toggleLift).
                }
                case SNAPPING -> {
                    LandVehicle vehicle = linked(jack).orElse(null);
                    if (vehicle == null) {
                        // Masina a disparut intre timp - revenim jos linistiti.
                        jack.state = Jack.State.DOWN;
                        jack.vehicleId = null;
                        break;
                    }
                    vehicle.snapToward(jack.location.getX(), jack.location.getZ(), 0.35D);
                    vehicle.setLiftedY(jack.startY);
                    jack.snapTicks++;
                    if (vehicle.horizontalDistanceSquared(jack.location.getX(),
                            jack.location.getZ()) <= SNAP_DONE_SQ
                            || jack.snapTicks >= SNAP_MAX_TICKS) {
                        // Aliniat (sau timeout): incepe ridicarea ca in mod.
                        jack.state = Jack.State.LIFTING;
                        playSound(jack, HEAD_UP, 0.9F, 1.0F);
                    }
                }
                case LIFTING -> {
                    advance(jack, +1.0D / ticks);
                    if (jack.progress >= 1.0D) {
                        jack.progress = 1.0D;
                        jack.state = Jack.State.LIFTED;
                        linked(jack).ifPresent(vehicle -> vehicle.setJackState(LandVehicle.JackState.LIFTED));
                        playSound(jack, AIR_WRENCH, 0.7F, 1.1F);
                    }
                }
                case LOWERING -> {
                    advance(jack, -1.0D / ticks);
                    if (jack.progress <= 0.0D) {
                        jack.progress = 0.0D;
                        jack.state = Jack.State.DOWN;
                        linked(jack).ifPresent(vehicle -> {
                            vehicle.setJackState(LandVehicle.JackState.NONE);
                            vehicle.setJackId(null);
                            vehicle.setLiftedY(jack.startY);
                        });
                        jack.vehicleId = null;
                        jack.snapTicks = 0;
                        playSound(jack, AIR_WRENCH, 0.7F, 0.9F);
                    }
                }
            }
        }
    }

    /** Masina gata de snap: deasupra placii, libera (nu e pe alt cric / podium). */
    private Optional<LandVehicle> snapCandidate(Jack jack) {
        return vehicles.nearest(jack.location, SNAP_RANGE)
                .filter(vehicle -> !vehicle.onJack() && !vehicle.showcase());
    }

    /** Montarea din mod: vehiculul ingheata si e tras exact la centrul placii
     *  (x+0.5, z+0.5, yaw pastrat). Y-ul de stat = REST_OFFSET peste blocul cricului:
     *  masina sta PE elevator (jos = pe slab, sus = pe capul ridicat), nu in el. */
    private void beginSnap(Jack jack, LandVehicle vehicle) {
        jack.vehicleId = vehicle.id();
        jack.startY = jack.location.getY() + REST_OFFSET;
        jack.snapTicks = 0;
        jack.state = Jack.State.SNAPPING;
        vehicle.setJackState(LandVehicle.JackState.LIFTING);
        vehicle.setJackId(jack.id);
    }

    private Optional<LandVehicle> linked(Jack jack) {
        return jack.vehicleId == null ? Optional.empty() : vehicles.byId(jack.vehicleId);
    }

    private void advance(Jack jack, double delta) {
        jack.progress = Math.max(0.0D, Math.min(1.0D, jack.progress + delta));
        // Fata vizuala: SUS cand trece de jumatatea ridicarii, JOS sub (un salt de
        // bloc exact ca in mod - blocul nu poate face animatie fina ca display-urile,
        // dar masina se mișca fin in continuare).
        StationBlocks.Kind want = jack.progress > 0.5D
                ? StationBlocks.Kind.JACK_UP : StationBlocks.Kind.JACK_DOWN;
        if (StationBlocks.kindOf(jack.block()) != want) {
            jack.block().setBlockData(want == StationBlocks.Kind.JACK_UP
                    ? StationBlocks.JACK_UP : StationBlocks.JACK_DOWN, false);
        }
        linked(jack).ifPresent(vehicle ->
                vehicle.setLiftedY(jack.startY + jack.progress * liftHeight()));
        // Sunetul de pneumatic la intervale regulate in timpul miscarii.
        if ((int) (jack.progress * liftTicks()) % 14 == 0) {
            playSound(jack, AIR_WRENCH, 0.35F, jack.state == Jack.State.LIFTING ? 1.2F : 0.8F);
        }
    }

    private static void playSound(Jack jack, Key sound, float volume, float pitch) {
        World world = jack.location.getWorld();
        if (world == null) {
            return;
        }
        Location at = jack.location;
        for (Player nearby : world.getPlayers()) {
            if (nearby.getLocation().distanceSquared(at) <= 20.0D * 20.0D) {
                nearby.playSound(Sound.sound(sound, Sound.Source.BLOCK, volume, pitch),
                        at.getX(), at.getY(), at.getZ());
            }
        }
    }
}
