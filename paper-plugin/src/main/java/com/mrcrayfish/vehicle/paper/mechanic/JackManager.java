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
 * cricele active (idle = cost zero). Ca in mod: blocul are doua fete vizuale,
 * JOS (cap impaturit) si SUS (cap ridicat +0.5 = lift-height implicit), schimbate
 * la jumatatea animatiei; masina urca/coboara fin pe jack.lift-speed-ticks si
 * intra in starea „pe elevator" (fizica inghetata, fara tractiune).
 * Actiunea e DIRECT pe cric cu cheia de mecanic (pompezi cricul, ca in mod);
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
                        found.setLiftedY(jack.location.getY() + liftHeight());
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
        player.sendRichMessage("<gray>Cric pus jos. Pune masina deasupra si apasa cu "
                + "<gold>cheia de mecanic</gold> ca sa o ridici.</gray>");
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
        player.sendRichMessage("<gray>Click cu <gold>cheia de mecanic</gold> = ridici/cobori. "
                + "Ghemuit + click = strangi cricul.</gray>");
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
                LandVehicle target = vehicles.nearest(jack.location, 2.4D)
                        .filter(vehicle -> !vehicle.onJack() && !vehicle.showcase())
                        .orElse(null);
                if (target != null && target.occupied()) {
                    player.sendRichMessage("<red>Cineva sta in masina - mai intai trebuie sa coboare.</red>");
                    return true;
                }
                jack.state = Jack.State.LIFTING;
                jack.vehicleId = target == null ? null : target.id();
                if (target != null) {
                    target.setJackState(LandVehicle.JackState.LIFTING);
                    target.setJackId(jack.id);
                }
                playSound(jack, HEAD_UP, 0.9F, 1.0F);
                player.sendRichMessage(target == null
                        ? "<gray>Ridici cricul gol...</gray>"
                        : "<gray>Ridic masina pe cric... nu o misca pana se opreste.</gray>");
            }
            case LIFTED -> {
                jack.state = Jack.State.LOWERING;
                Optional<LandVehicle> target = jack.vehicleId == null
                        ? Optional.<LandVehicle>empty() : vehicles.byId(jack.vehicleId);
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

    private void tick() {
        int ticks = liftTicks();
        for (Jack jack : jacks.values()) {
            if (jack.state == Jack.State.LIFTING) {
                advance(jack, +1.0D / ticks);
                if (jack.progress >= 1.0D) {
                    jack.progress = 1.0D;
                    jack.state = Jack.State.LIFTED;
                    linked(jack).ifPresent(vehicle -> vehicle.setJackState(LandVehicle.JackState.LIFTED));
                    playSound(jack, AIR_WRENCH, 0.7F, 1.1F);
                }
            } else if (jack.state == Jack.State.LOWERING) {
                advance(jack, -1.0D / ticks);
                if (jack.progress <= 0.0D) {
                    jack.progress = 0.0D;
                    jack.state = Jack.State.DOWN;
                    linked(jack).ifPresent(vehicle -> {
                        vehicle.setJackState(LandVehicle.JackState.NONE);
                        vehicle.setJackId(null);
                        vehicle.setLiftedY(jack.location.getY());
                    });
                    jack.vehicleId = null;
                    playSound(jack, AIR_WRENCH, 0.7F, 0.9F);
                }
            }
        }
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
                vehicle.setLiftedY(jack.location.getY() + jack.progress * liftHeight()));
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
