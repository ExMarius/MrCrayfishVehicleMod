package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.MechanicStore;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Props stradale: con de trafic (decor), placa de boost (da acceleratie cand treci
 * cu masina peste ea), teava de fluid (decor de statie). Plasare cu itemul respectiv,
 * lovire cu pumnul in timp ce stai ghemuit = ridicare inapoi in inventar.
 */
public final class PropManager {
    private static final String ENTITY_TAG = "mcv_prop";
    private static final float BOOST_STRENGTH = 1.2F;
    private static final int BOOST_TICKS = 40;

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final MechanicStore store;
    private final Map<UUID, Prop> props = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private int counter;

    private record Prop(UUID id, String kind, Location location,
                        ItemDisplay display, Interaction hitbox) {
    }

    public PropManager(VehiclePlugin plugin, VehicleManager vehicles, MechanicStore store) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.store = store;
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
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (Prop prop : props.values()) {
            prop.display().remove();
            prop.hitbox().remove();
        }
        props.clear();
    }

    private void load() {
        for (MechanicStore.StoredStation stored : store.loadStations("props")) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            spawnRig(stored.id(), stored.kind(),
                    new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), 0.0F),
                    JackManager.snapYaw(stored.yaw()));
        }
    }

    public List<MechanicStore.StoredStation> snapshot() {
        List<MechanicStore.StoredStation> stored = new ArrayList<>();
        for (Prop prop : props.values()) {
            stored.add(new MechanicStore.StoredStation(prop.id(), prop.kind(),
                    prop.location().getWorld().getUID(), prop.location().getX(),
                    prop.location().getY(), prop.location().getZ(), prop.location().getYaw(),
                    null, null, java.util.List.of()));
        }
        return stored;
    }

    public void place(String kind, Location clickedFace, float yaw) {
        // Spline ca la jack/banc: rotire la multiplu de 90 grade (ca blocul din mod)
        // si orientarea modelului prin transformare (dealtfel piesele isi ies din grila).
        float snappedYaw = JackManager.snapYaw(yaw);
        Location ground = clickedFace.getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        spawnRig(UUID.randomUUID(), kind, ground, snappedYaw);
    }

    private static String modelFor(String kind) {
        return switch (kind) {
            case "boost_pad" -> "boost_pad";
            case "fluid_pipe" -> "fluid_pipe";
            default -> "traffic_cone";
        };
    }

    private static ItemStack itemFor(String kind) {
        return switch (kind) {
            case "boost_pad" -> MechanicItems.boostPad();
            case "fluid_pipe" -> MechanicItems.fluidPipe();
            default -> MechanicItems.trafficCone();
        };
    }

    private void spawnRig(UUID id, String kind, Location ground, float snappedYaw) {
        World world = ground.getWorld();
        Location spawnAt = ground.clone();
        spawnAt.setYaw(snappedYaw);
        spawnAt.setPitch(0.0F);
        ItemDisplay display = world.spawn(spawnAt, ItemDisplay.class, spawned -> {
            // LEATHER_HORSE_ARMOR in loc de PAPER: PAPER + item_model nu mai randeaza
            // pe server (regresie live); fara culoare => default-ul definitiei.
            ItemStack stack = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(new NamespacedKey("vehicle", modelFor(kind)));
            stack.setItemMeta(meta);
            spawned.setItemStack(stack);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setInvulnerable(true);
            spawned.setPersistent(false);
            spawned.setShadowRadius(0.1F);
            JackManager.applyBlockTransform(spawned, spawned.getLocation().getYaw());
            spawned.addScoreboardTag(ENTITY_TAG);
            spawned.addScoreboardTag("mcv_" + id);
        });
        Interaction hitbox = world.spawn(spawnAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth("traffic_cone".equals(kind) ? 0.65F : 1.0F);
            spawned.setInteractionHeight("traffic_cone".equals(kind) ? 0.95F : 0.4F);
            spawned.setResponsive(true);
            spawned.setPersistent(false);
            spawned.addScoreboardTag(ENTITY_TAG);
            spawned.addScoreboardTag("mcv_" + id);
        });
        props.put(id, new Prop(id, kind, spawnAt.clone(), display, hitbox));
    }

    /** Lookup public pentru routing-ul din MechanicManager (mesaje indrumare). */
    public Optional<UUID> byEntityPublic(Entity entity) {
        return byEntity(entity).map(Prop::id);
    }

    private Optional<Prop> byEntity(Entity entity) {
        for (Prop prop : props.values()) {
            if (prop.hitbox().getUniqueId().equals(entity.getUniqueId())
                    || prop.display().getUniqueId().equals(entity.getUniqueId())) {
                return Optional.of(prop);
            }
        }
        return Optional.empty();
    }

    /** Lovire cu pumnul (ghemuit) = ridica prop-ul inapoi. */
    public boolean handleBreak(Player player, Entity entity) {
        Optional<Prop> prop = byEntity(entity);
        if (prop.isEmpty()) {
            return false;
        }
        if (!player.isSneaking()) {
            player.sendRichMessage("<gray>Stai ghemuit si loveste-l ca sa il ridici.</gray>");
            return true;
        }
        Prop target = prop.get();
        props.remove(target.id());
        target.display().remove();
        target.hitbox().remove();
        player.getInventory().addItem(itemFor(target.kind()));
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 0.7F, 1.2F);
        return true;
    }

    /** Placile de boost: vehiculul care trece peste placa primeste accelerare scurta. */
    private void tick() {
        counter++;
        if (counter % 2 != 0) {
            return;
        }
        for (Prop prop : props.values()) {
            if (!"boost_pad".equals(prop.kind())) {
                continue;
            }
            for (LandVehicle vehicle : vehicles.vehiclesWithin(prop.location(), 1.4D)) {
                if (vehicle.horizontalSpeed() >= 0.01D) {
                    vehicle.applyBoost(BOOST_STRENGTH, BOOST_TICKS);
                }
            }
        }
    }
}
