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
        int migrated = 0;
        for (MechanicStore.StoredStation stored : store.loadStations("props")) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            // CICLUL AI - migratie: conul si placa vechi (entitati ItemDisplay+
            // Interaction) devin BLOCURI reale exact pe celula salvata, orientarea
            // placii din yaw-ul memorat. Teava ramane prop-entitate (inca).
            if ("traffic_cone".equals(stored.kind()) || "boost_pad".equals(stored.kind())) {
                org.bukkit.block.Block cell = world.getBlockAt(
                        (int) Math.floor(stored.x()), (int) Math.floor(stored.y()),
                        (int) Math.floor(stored.z()));
                if (cell.getType().isAir()) {
                    cell.setBlockData("traffic_cone".equals(stored.kind())
                                    ? com.mrcrayfish.vehicle.paper.world.TrafficProps.coneData()
                                    : com.mrcrayfish.vehicle.paper.world.TrafficProps.padData(
                            com.mrcrayfish.vehicle.paper.world.TrafficProps.yawToFacing(stored.yaw())),
                            false);
                    migrated++;
                }
                continue; // nu mai cream entitatea veche
            }
            spawnRig(stored.id(), stored.kind(),
                    new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), 0.0F),
                    JackManager.snapYaw(stored.yaw()));
        }
        if (migrated > 0) {
            plugin.getLogger().info("[Props] Migratie ciclul AI: " + migrated
                    + " conuri/placi vechi (entitati) convertite la blocuri reale.");
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
        if (!"fluid_pipe".equals(kind)) {
            return; // ciclul AI: con/placa nu se mai spawn-eaza ca entitati (blocuri reale)
        }
        Location ground = clickedFace.getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        spawnRig(UUID.randomUUID(), kind, ground, snappedYaw);
    }

    /** Ciclul AI: mai exista UN SINGUR fel de prop-entitate: teava de fluid. Conul
     *  si placa de boost sunt de acum BLOCURI reale (TrafficProps). */
    private static String modelFor(String kind) {
        return "fluid_pipe";
    }

    private static ItemStack itemFor(String kind) {
        return MechanicItems.fluidPipe();
    }

    private void spawnRig(UUID id, String kind, Location ground, float snappedYaw) {
        World world = ground.getWorld();
        Location spawnAt = ground.clone();
        spawnAt.setYaw(snappedYaw);
        spawnAt.setPitch(0.0F);
        ItemDisplay display = world.spawn(spawnAt, ItemDisplay.class, spawned -> {
            // LEATHER_HORSE_ARMOR in loc de PAPER: PAPER + item_model nu mai randeaza
            // pe server (regresie live); alb explicit => dyed_color opac pe orice client.
            ItemStack stack = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(new NamespacedKey("vehicle", modelFor(kind)));
            if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta armor) {
                armor.setColor(org.bukkit.Color.fromRGB(0xF9FFFE));
            }
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

}
