package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.MechanicStore;
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
 * Bancul de lucru (echivalentul WorkstationBlock din mod): click = primesti cheia de
 * mecanic, daca ai permisiunea mcv.workstation.use si nu ai deja una in inventar.
 * Plasabil prin /vehicle creative (admin), persistent in mechanics.yml.
 */
public final class WorkstationManager {
    private static final String ENTITY_TAG = "mcv_workstation";
    private static final long CLICK_COOLDOWN_MS = 2_000L;

    private final VehiclePlugin plugin;
    private final MechanicStore store;
    private final Map<UUID, Interaction> hitboxes = new ConcurrentHashMap<>();
    private final Map<UUID, ItemDisplay> displays = new ConcurrentHashMap<>();
    private final Map<UUID, Location> stations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public WorkstationManager(VehiclePlugin plugin, MechanicStore store) {
        this.plugin = plugin;
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
        for (ItemDisplay display : displays.values()) {
            display.remove();
        }
        for (Interaction hitbox : hitboxes.values()) {
            hitbox.remove();
        }
        displays.clear();
        hitboxes.clear();
        stations.clear();
    }

    private void load() {
        for (MechanicStore.StoredStation stored : store.loadStations("workstations")) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            spawnRig(stored.id(), new Location(world, stored.x(), stored.y(), stored.z(),
                    stored.yaw(), 0.0F));
        }
    }

    public List<MechanicStore.StoredStation> snapshot() {
        List<MechanicStore.StoredStation> stored = new ArrayList<>();
        for (Map.Entry<UUID, Location> entry : stations.entrySet()) {
            Location location = entry.getValue();
            stored.add(new MechanicStore.StoredStation(entry.getKey(), "workstation",
                    location.getWorld().getUID(), location.getX(), location.getY(),
                    location.getZ(), location.getYaw()));
        }
        return stored;
    }

    public void place(Location clickedFace, float yaw) {
        Location ground = clickedFace.getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        ground.setYaw(yaw);
        spawnRig(UUID.randomUUID(), ground);
    }

    private void spawnRig(UUID id, Location ground) {
        World world = ground.getWorld();
        ItemDisplay display = world.spawn(ground, ItemDisplay.class, spawned -> {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(new NamespacedKey("vehicle", "mechanic_workstation"));
            stack.setItemMeta(meta);
            spawned.setItemStack(stack);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setInvulnerable(true);
            spawned.setPersistent(false);
            spawned.setShadowRadius(0.2F);
            spawned.setTransformation(new Transformation(new Vector3f(-0.5F, 0.0F, -0.5F),
                    new Quaternionf(), new Vector3f(1.35F), new Quaternionf()));
            spawned.addScoreboardTag(ENTITY_TAG);
            spawned.addScoreboardTag("mcv_" + id);
        });
        Interaction hitbox = world.spawn(ground.clone().add(0.0D, 0.0D, 0.0D), Interaction.class,
                spawned -> {
                    spawned.setInteractionWidth(1.2F);
                    spawned.setInteractionHeight(1.5F);
                    spawned.setResponsive(true);
                    spawned.setPersistent(false);
                    spawned.addScoreboardTag(ENTITY_TAG);
                    spawned.addScoreboardTag("mcv_" + id);
                });
        displays.put(id, display);
        hitboxes.put(id, hitbox);
        stations.put(id, ground);
    }

    public boolean isWorkstationEntity(Entity entity) {
        return entity != null && findByEntity(entity).isPresent();
    }

    private Optional<UUID> findByEntity(Entity entity) {
        for (Map.Entry<UUID, Interaction> entry : hitboxes.entrySet()) {
            if (entry.getValue().getUniqueId().equals(entity.getUniqueId())) {
                return Optional.of(entry.getKey());
            }
        }
        for (Map.Entry<UUID, ItemDisplay> entry : displays.entrySet()) {
            if (entry.getValue().getUniqueId().equals(entity.getUniqueId())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** Click pe banc: shift+click = il iei inapoi; altfel primesti cheia de mecanic. */
    public boolean handleClick(Player player, Entity entity) {
        Optional<UUID> station = findByEntity(entity);
        if (station.isEmpty()) {
            return false;
        }
        if (player.isSneaking()) {
            UUID id = station.get();
            displays.remove(id).remove();
            hitboxes.remove(id).remove();
            stations.remove(id);
            player.getInventory().addItem(MechanicItems.workstation());
            player.sendRichMessage("<gray>Ai ridicat bancul de lucru.</gray>");
            return true;
        }
        if (!player.hasPermission("mcv.workstation.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.workstation.use.</red>");
            return true;
        }
        long now = System.currentTimeMillis();
        Long previous = cooldowns.get(player.getUniqueId());
        if (previous != null && now - previous < CLICK_COOLDOWN_MS) {
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);
        for (ItemStack contents : player.getInventory().getContents()) {
            if (MechanicItems.kindOf(contents) == MechanicItems.Kind.WRENCH) {
                player.sendRichMessage("<yellow>Ai deja o cheie de mecanic in inventar.</yellow>");
                return true;
            }
        }
        player.getInventory().addItem(MechanicItems.wrench());
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.0F);
        player.sendRichMessage("<gray>Ai primit <gold>cheia de mecanic</gold> de la bancul de lucru.</gray>");
        return true;
    }
}
