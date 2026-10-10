package com.mrcrayfish.vehicle.paper.station;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vanilla-display replica of a fuel station: a two-block cabinet, plus a hand-held nozzle and
 * a bending hose that only exist while a player is actually holding the nozzle. Every part is
 * a plain {@link ItemDisplay} (a Paper plugin cannot register a real block-entity renderer for
 * a vanilla client), positioned with a single, uniform trick everywhere in this class: each
 * display entity is teleported exactly to the world point it should visually pivot around, and
 * its {@link Transformation}'s translation is set to {@code -rotation * (scale * localAnchor)}
 * so that rotating/scaling the model appears to happen around {@code localAnchor} (a point in
 * the model's own 0-1 "one block" coordinate space) instead of the model's coordinate origin.
 * See {@link #anchoredTranslation}.
 */
public final class FuelStationRig {
    public static final String ENTITY_TAG = "mcv_plugin_fuelstation";

    private static final int HOSE_SEGMENTS = 20;
    private static final Vector3f BODY_ANCHOR = new Vector3f(0.5F, 0.0F, 0.0F); // y not used for a Y-only rotation
    private static final Vector3f OUTLET_LOCAL = new Vector3f(12.5F / 16.0F, 5.5F / 16.0F, 10.5F / 16.0F);
    private static final Vector3f NOZZLE_ANCHOR = new Vector3f(7.5F / 16.0F, 2.0F / 16.0F, 7.5F / 16.0F);
    private static final Vector3f NOZZLE_SCALE = new Vector3f(0.7F);
    private static final Vector3f HOSE_SCALE_XY = new Vector3f(0.6F, 0.6F, 1.0F);
    private static final Vector3f SAG_DIRECTION = new Vector3f(0.0F, -1.0F, 0.0F);

    private final UUID stationId;
    private final World world;
    private final Location bottomAnchor;
    private final Location topAnchor;
    private final Vector3f outletWorldOffset;
    private final Quaternionf bodyRotation;
    private final List<Entity> all = new ArrayList<>();
    private final Interaction interaction;
    private final ItemDisplay bottom;
    private final ItemDisplay top;
    private ItemDisplay nozzle;
    private final List<ItemDisplay> hoseSegments = new ArrayList<>();
    private boolean active;

    private FuelStationRig(UUID stationId, World world, Location bottomAnchor, Location topAnchor,
                            Vector3f outletWorldOffset, Quaternionf bodyRotation,
                            Interaction interaction, ItemDisplay bottom, ItemDisplay top) {
        this.stationId = stationId;
        this.world = world;
        this.bottomAnchor = bottomAnchor;
        this.topAnchor = topAnchor;
        this.outletWorldOffset = outletWorldOffset;
        this.bodyRotation = bodyRotation;
        this.interaction = interaction;
        this.bottom = bottom;
        this.top = top;
    }

    public static FuelStationRig spawn(Block block, BlockFace facing, UUID stationId) {
        World world = block.getWorld();
        Location bottomAnchor = new Location(world, block.getX() + 0.5D, block.getY(), block.getZ() + 0.5D);
        Location topAnchor = new Location(world, block.getX() + 0.5D, block.getY() + 1.0D, block.getZ() + 0.5D);
        Quaternionf rotation = bodyRotation(facing);

        ItemDisplay bottomDisplay = spawnBodyPart(world, bottomAnchor, rotation, "fuel_station_bottom");
        ItemDisplay topDisplay = spawnBodyPart(world, topAnchor, rotation, "fuel_station_top");

        Vector3f outletOffset = rotation.transform(new Vector3f(OUTLET_LOCAL).sub(BODY_ANCHOR));

        Location hitboxLocation = new Location(world, block.getX() + 0.5D, block.getY(), block.getZ() + 0.5D);
        Interaction interaction = world.spawn(hitboxLocation, Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.0F);
            hitbox.setInteractionHeight(2.0F);
            hitbox.setPersistent(false);
            hitbox.addScoreboardTag(ENTITY_TAG);
        });

        FuelStationRig rig = new FuelStationRig(stationId, world, bottomAnchor, topAnchor,
                outletOffset, rotation, interaction, bottomDisplay, topDisplay);
        rig.all.add(interaction);
        rig.all.add(bottomDisplay);
        rig.all.add(topDisplay);
        return rig;
    }

    private static ItemDisplay spawnBodyPart(World world, Location anchor, Quaternionf rotation, String modelName) {
        ItemDisplay display = world.spawn(anchor, ItemDisplay.class, entity -> {
            entity.setItemStack(modelItem(modelName));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setInterpolationDelay(0);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setShadowRadius(0.0F);
            entity.addScoreboardTag(ENTITY_TAG);
        });
        Vector3f translation = anchoredTranslation(BODY_ANCHOR, rotation, new Vector3f(1.0F));
        display.setTransformation(new Transformation(translation, rotation, new Vector3f(1.0F), new Quaternionf()));
        return display;
    }

    public UUID interactionId() {
        return interaction.getUniqueId();
    }

    public boolean valid() {
        return interaction.isValid() && bottom.isValid() && top.isValid();
    }

    public void remove() {
        for (Entity entity : all) {
            entity.remove();
        }
        despawnActiveEntities();
    }

    public Location outletLocation() {
        return bottomAnchor.clone().add(outletWorldOffset.x, outletWorldOffset.y, outletWorldOffset.z);
    }

    public void setIdle() {
        if (!active) {
            return;
        }
        active = false;
        despawnActiveEntities();
    }

    /**
     * Bends the hose from the station's outlet to {@code nozzleTip} and places the nozzle
     * prop there. Spawns the nozzle and every hose segment the first time this is called for
     * a freshly started session.
     */
    public void updateActive(Vector3f nozzleTip, Quaternionf nozzleRotation) {
        if (!valid()) {
            return;
        }
        if (!active) {
            active = true;
            spawnActiveEntities();
        }

        Location nozzleLocation = new Location(world, nozzleTip.x, nozzleTip.y, nozzleTip.z);
        Vector3f nozzleTranslation = anchoredTranslation(NOZZLE_ANCHOR, nozzleRotation, NOZZLE_SCALE);
        nozzle.teleport(nozzleLocation, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        nozzle.setTransformation(new Transformation(nozzleTranslation, nozzleRotation, NOZZLE_SCALE, new Quaternionf()));

        Vector3f outlet = toVector(outletLocation());
        float distance = outlet.distance(nozzleTip);
        float sagAmount = Math.min(1.5F, distance * 0.25F);

        for (int i = 0; i < HOSE_SEGMENTS; i++) {
            float t0 = (float) i / HOSE_SEGMENTS;
            float t1 = (float) (i + 1) / HOSE_SEGMENTS;
            Vector3f p0 = HoseCurve.point(outlet, nozzleTip, SAG_DIRECTION, sagAmount, t0);
            Vector3f p1 = HoseCurve.point(outlet, nozzleTip, SAG_DIRECTION, sagAmount, t1);
            placeHoseSegment(hoseSegments.get(i), p0, p1);
        }
    }

    private void placeHoseSegment(ItemDisplay segment, Vector3f from, Vector3f to) {
        Vector3f direction = new Vector3f(to).sub(from);
        float length = direction.length();
        if (length < 1.0E-4F) {
            segment.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
            return;
        }
        direction.div(length);
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), direction);
        Vector3f scale = new Vector3f(HOSE_SCALE_XY.x, HOSE_SCALE_XY.y, length);
        Vector3f translation = anchoredTranslation(new Vector3f(0.5F, 0.5F, 0.0F), rotation, scale);

        segment.teleport(new Location(world, from.x, from.y, from.z), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        segment.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
    }

    private void spawnActiveEntities() {
        nozzle = world.spawn(bottomAnchor, ItemDisplay.class, entity -> {
            entity.setItemStack(modelItem("fuel_station_nozzle"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setInterpolationDelay(0);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setShadowRadius(0.0F);
            entity.addScoreboardTag(ENTITY_TAG);
        });
        all.add(nozzle);
        for (int i = 0; i < HOSE_SEGMENTS; i++) {
            ItemDisplay segment = world.spawn(bottomAnchor, ItemDisplay.class, entity -> {
                entity.setItemStack(modelItem("fuel_station_hose_segment"));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setInterpolationDelay(0);
                entity.setInvulnerable(true);
                entity.setPersistent(false);
                entity.setShadowRadius(0.0F);
                entity.addScoreboardTag(ENTITY_TAG);
            });
            hoseSegments.add(segment);
            all.add(segment);
        }
    }

    private void despawnActiveEntities() {
        if (nozzle != null) {
            all.remove(nozzle);
            nozzle.remove();
            nozzle = null;
        }
        for (ItemDisplay segment : hoseSegments) {
            all.remove(segment);
            segment.remove();
        }
        hoseSegments.clear();
    }

    private static Vector3f toVector(Location location) {
        return new Vector3f((float) location.getX(), (float) location.getY(), (float) location.getZ());
    }

    /**
     * The translation a {@link Transformation} needs so that rotating/scaling the model
     * appears to pivot around {@code anchor} (in local 0-1 "one block" units) rather than the
     * model's own coordinate origin, given the display entity itself is positioned exactly at
     * the world point {@code anchor} should land on.
     */
    static Vector3f anchoredTranslation(Vector3f anchor, Quaternionf rotation, Vector3f scale) {
        Vector3f scaledAnchor = new Vector3f(anchor.x * scale.x, anchor.y * scale.y, anchor.z * scale.z);
        return rotation.transform(scaledAnchor).negate();
    }

    static Quaternionf bodyRotation(BlockFace facing) {
        float degrees = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        return new Quaternionf().rotateY((float) Math.toRadians(degrees));
    }

    private static ItemStack modelItem(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", name));
        item.setItemMeta(meta);
        return item;
    }
}
