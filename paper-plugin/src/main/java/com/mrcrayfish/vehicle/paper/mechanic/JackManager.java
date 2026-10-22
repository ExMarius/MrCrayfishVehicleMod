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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
 * Cricul/elevatorul (port 1-la-1 al ideii din mod, cu inaltime configurabila):
 * cheie de mecanic pe cric = urcare ANIMATA pana la jack.lift-height (implicit 2.5
 * blocuri, ca sa mearga jucatorul pe sub masina); masina intra in starea "pe elevator"
 * (fizica inghetata, fara tractiune); click din nou = coborare animata si revine la
 * fizica normala. Sunetele head_up/head_down/air_wrench_gun sunt cele originale.
 */
public final class JackManager {
    public static final String ENTITY_TAG = "mcv_jack";
    private static final Key HEAD_UP = Key.key("vehicle:block.jack.head_up");
    private static final Key HEAD_DOWN = Key.key("vehicle:block.jack.head_down");
    private static final Key AIR_WRENCH = Key.key("vehicle:block.jack.air_wrench_gun");

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final MechanicStore store;
    private final Map<UUID, Jack> jacks = new ConcurrentHashMap<>();
    private final Map<UUID, Jack> byInteraction = new ConcurrentHashMap<>();
    private BukkitTask tickTask;

    public JackManager(VehiclePlugin plugin, VehicleManager vehicles, MechanicStore store) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.store = store;
    }

    private double liftHeight() {
        return Math.max(0.5D, plugin.getConfig().getDouble("jack.lift-height", 2.5D));
    }

    private int liftTicks() {
        return Math.max(5, plugin.getConfig().getInt("jack.lift-speed-ticks", 50));
    }

    public void start() {
        cleanupOrphaned();
        load();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (Jack jack : jacks.values()) {
            removeRig(jack);
        }
        jacks.clear();
        byInteraction.clear();
    }

    private void cleanupOrphaned() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ENTITY_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private void load() {
        for (MechanicStore.StoredJack stored : store.loadJacks()) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            Location location = new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), 0.0F);
            Jack jack = spawnRig(stored.id(), location);
            jack.progress = stored.raised() ? 1.0D : 0.0D;
            moveHead(jack);
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
            byInteraction.put(jack.interaction.getUniqueId(), jack);
        }
    }

    public List<MechanicStore.StoredJack> snapshot() {
        List<MechanicStore.StoredJack> stored = new ArrayList<>();
        for (Jack jack : jacks.values()) {
            World world = jack.location.getWorld();
            stored.add(new MechanicStore.StoredJack(jack.id, world.getUID(),
                    jack.location.getX(), jack.location.getY(), jack.location.getZ(),
                    jack.location.getYaw(), jack.raised(), jack.vehicleId));
        }
        return stored;
    }

    /* ============================ plasare / rig ============================ */

    public boolean place(Player player, Location clickedFace, float yaw) {
        Location ground = clickedFace.getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        ground.setYaw(normalizeYaw(yaw + 180.0F));
        UUID id = UUID.randomUUID();
        Jack jack = spawnRig(id, ground);
        jacks.put(id, jack);
        byInteraction.put(jack.interaction.getUniqueId(), jack);
        player.sendRichMessage("<gray>Cric pus jos. Pune masina deasupra si apasa cu "
                + "<gold>cheia de mecanic</gold> ca sa o ridici.</gray>");
        return true;
    }

    private Jack spawnRig(UUID id, Location ground) {
        World world = ground.getWorld();
        ItemDisplay base = world.spawn(ground, ItemDisplay.class, display -> {
            display.setItemStack(model("jack"));
            applyBlockTransform(display);
            display.setShadowRadius(0.15F);
            tag(display, id);
        });
        ItemDisplay head = world.spawn(ground, ItemDisplay.class, display -> {
            display.setItemStack(model("jack_head"));
            applyBlockTransform(display);
            display.setInterpolationDuration(2);
            display.setShadowRadius(0.0F);
            tag(display, id);
        });
        Interaction interaction = world.spawn(ground, Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.1F);
            hitbox.setInteractionHeight(0.9F);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
            tag(hitbox, id);
        });
        return new Jack(id, ground, base, head, interaction);
    }

    private static void applyBlockTransform(ItemDisplay display) {
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setTransformation(new Transformation(new Vector3f(-0.5F, 0.0F, -0.5F),
                new Quaternionf(), new Vector3f(1.0F), new Quaternionf()));
    }

    private static ItemStack model(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", name));
        item.setItemMeta(meta);
        return item;
    }

    private void tag(Entity entity, UUID jackId) {
        entity.addScoreboardTag(ENTITY_TAG);
        entity.addScoreboardTag("mcv_" + jackId);
    }

    public Optional<Jack> byInteraction(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byInteraction.get(entity.getUniqueId()));
    }

    public Optional<Jack> byId(UUID id) {
        return Optional.ofNullable(jacks.get(id));
    }

    public Collection<Jack> jacks() {
        return Collections.unmodifiableCollection(jacks.values());
    }

    private void removeRig(Jack jack) {
        jack.base.remove();
        jack.head.remove();
        jack.interaction.remove();
    }

    /** Shift + click cu cheia de mecanic = ridici cricul inapoi in inventar (doar jos). */
    public boolean tryPickup(Player player, Jack jack) {
        if (jack.state != Jack.State.DOWN || jack.vehicleId != null) {
            player.sendRichMessage("<red>Cricul e in uz - mai intai coboara masina.</red>");
            return true;
        }
        jacks.remove(jack.id);
        byInteraction.remove(jack.interaction.getUniqueId());
        removeRig(jack);
        player.getInventory().addItem(MechanicItems.jack());
        player.sendRichMessage("<gray>Ai ridicat cricul.</gray>");
        return true;
    }

    /* ============================ interactiune ============================ */

    /** Click cu cheia de mecanic pe cric: ridica / coboara (animat pe jack.lift-speed-ticks). */
    public boolean handleWrenchClick(Player player, Jack jack) {
        if (!player.hasPermission("mcv.jack.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.jack.use.</red>");
            return true;
        }
        if (player.isSneaking()) {
            return tryPickup(player, jack);
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
        moveHead(jack);
        linked(jack).ifPresent(vehicle ->
                vehicle.setLiftedY(jack.location.getY() + jack.progress * liftHeight()));
        // Sunetul de pneumatic la intervale regulate in timpul miscarii.
        if ((int) (jack.progress * liftTicks()) % 14 == 0) {
            playSound(jack, AIR_WRENCH, 0.35F, jack.state == Jack.State.LIFTING ? 1.2F : 0.8F);
        }
    }

    private void moveHead(Jack jack) {
        Location headLocation = jack.location.clone();
        headLocation.add(0.0D, jack.progress * (liftHeight() + 0.4D), 0.0D);
        jack.head.teleport(headLocation);
        jack.head.setRotation(jack.location.getYaw(), 0.0F);
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

    private static float normalizeYaw(float yaw) {
        float wrapped = yaw % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
