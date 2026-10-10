package com.mrcrayfish.vehicle.paper.gaspump;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Multipart display rig for a placeable, breakable gas pump.
 *
 * <p>The original mod's {@code GasPumpBlock} is a two-tall real block (a tank
 * TileEntity on the bottom half, the nozzle/fueling TileEntity on the top
 * half) whose {@code GasPumpRenderer} draws the hose as a curved mesh built
 * from a two-point Hermite spline. A vanilla Paper client cannot register a
 * new block type or a custom mesh, so this rig reuses the mod's own gas pump
 * and nozzle models/textures as a two-part {@link ItemDisplay} stack plus a
 * single two-block-tall {@link Interaction} for placement/breaking/clicking,
 * and approximates the hose by sampling {@link HermiteCurve} and placing a
 * short display segment between each consecutive pair of samples.
 */
public final class GasPumpRig {
    public static final String ENTITY_TAG = "mcv_gas_pump";
    private static final int VANILLA_ENTITY_LERP_TICKS = 3;
    static final int HOSE_SEGMENTS = 8;
    private static final float HOSE_THICKNESS = 0.0625F;

    /** Local offset of the hose's pump-side anchor, relative to the bottom block's floor.
     *  Shifted 1 block toward the pump's own left (west when the pump faces north) from the
     *  original mod's anchor, nudged 2 pixels (2/16 block) further north, 2 more pixels
     *  toward the east, then 1 more pixel (1/16 block) further north. */
    private static final Vector3f HOSE_OUTLET_LOCAL = new Vector3f(-0.575F, 1.64F, 0.1125F);
    /** Local offset where the nozzle rests when nobody is holding it.
     *  Net 1.5 blocks toward the pump's own left (west when the pump faces north): 2 blocks
     *  west, 1.5 blocks back east after it overshot, then 1 more block further west. */
    private static final Vector3f NOZZLE_REST_LOCAL = new Vector3f(-1.5F, 1.5F, 0.26F);
    /** The nozzle model's own spout faces back toward the pump instead of outward (confirmed
     *  still backward even with zero extra rotation), so it needs a half-turn on top of the
     *  pump's own yaw whenever its rotation is set. */
    private static final float NOZZLE_YAW_OFFSET = 180.0F;

    private final UUID pumpId;
    private final Interaction interaction;
    private final ItemDisplay bottom;
    private final ItemDisplay top;
    private final ItemDisplay nozzle;
    private final List<ItemDisplay> hose;
    private final List<Entity> entities;

    private GasPumpRig(UUID pumpId, Interaction interaction, ItemDisplay bottom, ItemDisplay top,
                       ItemDisplay nozzle, List<ItemDisplay> hose, List<Entity> entities) {
        this.pumpId = pumpId;
        this.interaction = interaction;
        this.bottom = bottom;
        this.top = top;
        this.nozzle = nozzle;
        this.hose = hose;
        this.entities = entities;
    }

    public static GasPumpRig spawn(UUID pumpId, Location root) {
        World world = root.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Cannot spawn a gas pump without a world");
        }
        List<Entity> all = new ArrayList<>();

        Interaction interaction = world.spawn(root, Interaction.class, entity -> {
            entity.setInteractionWidth(0.8F);
            entity.setInteractionHeight(2.0F);
            entity.setResponsive(true);
            entity.setPersistent(false);
        });
        mark(interaction, pumpId);
        all.add(interaction);

        ItemDisplay bottom = display(world, root, model("gas_pump_bottom"));
        mark(bottom, pumpId);
        all.add(bottom);

        ItemDisplay top = display(world, root, model("gas_pump_top"));
        mark(top, pumpId);
        all.add(top);

        ItemDisplay nozzle = display(world, root, model("nozzle"));
        mark(nozzle, pumpId);
        all.add(nozzle);

        List<ItemDisplay> hose = new ArrayList<>(HOSE_SEGMENTS);
        for (int i = 0; i < HOSE_SEGMENTS; i++) {
            ItemDisplay segment = display(world, root, model("hose_segment"));
            mark(segment, pumpId);
            all.add(segment);
            hose.add(segment);
        }

        GasPumpRig rig = new GasPumpRig(pumpId, interaction, bottom, top, nozzle,
                Collections.unmodifiableList(hose), all);
        rig.update(root, null, null);
        return rig;
    }

    /**
     * Repositions every part of the rig.
     *
     * @param root           the bottom block's floor-center location (its yaw is the pump's facing).
     * @param nozzleLocation absolute world position of the nozzle, or {@code null} when it is
     *                       resting on the pump (nobody is holding it).
     * @param lookDirection  the holder's look direction, used as the nozzle-side hose tangent
     *                       (matches the original renderer); ignored when {@code nozzleLocation} is null.
     */
    public void update(Location root, Location nozzleLocation, Vector lookDirection) {
        if (!valid()) {
            return;
        }
        World world = root.getWorld();
        float yaw = root.getYaw();

        interaction.teleport(root);
        interaction.setRotation(yaw, 0.0F);

        place(bottom, root, new Vector3f(0.0F, 0.5F, 0.0F));
        place(top, root, new Vector3f(0.0F, 1.5F, 0.0F));

        Vector3f outlet = worldPoint(root, HOSE_OUTLET_LOCAL);
        Vector3f nozzlePoint;
        Vector3f endTangent;
        if (nozzleLocation != null) {
            nozzlePoint = new Vector3f((float) nozzleLocation.getX(), (float) nozzleLocation.getY(),
                    (float) nozzleLocation.getZ());
            nozzle.teleport(new Location(world, nozzlePoint.x, nozzlePoint.y, nozzlePoint.z));
            nozzle.setRotation(yaw + NOZZLE_YAW_OFFSET, 0.0F);
            nozzle.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(1.0F, 1.0F, 1.0F), new Quaternionf()));
            endTangent = lookDirection == null ? new Vector3f(0.0F, 3.0F, 0.0F)
                    : new Vector3f((float) lookDirection.getX(), (float) lookDirection.getY(),
                            (float) lookDirection.getZ()).mul(3.0F);
        } else {
            nozzlePoint = worldPoint(root, NOZZLE_REST_LOCAL);
            place(nozzle, root, NOZZLE_REST_LOCAL, NOZZLE_YAW_OFFSET);
            endTangent = new Vector3f(0.0F, 3.0F, 0.0F);
        }

        Vector3f[] points = HermiteCurve.sample(outlet, new Vector3f(0.0F, -5.0F, 0.0F),
                nozzlePoint, endTangent, HOSE_SEGMENTS);
        for (int i = 0; i < HOSE_SEGMENTS; i++) {
            placeHoseSegment(hose.get(i), points[i], points[i + 1], world);
        }

        updateBrightness(root);
    }

    /** World-space rotation of a local offset by the rig's own facing, for hose/curve math only. */
    private static Vector3f worldPoint(Location root, Vector3f local) {
        double radians = Math.toRadians(root.getYaw());
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float worldX = local.x * cos - local.z * sin;
        float worldZ = local.x * sin + local.z * cos;
        return new Vector3f((float) root.getX() + worldX, (float) root.getY() + local.y,
                (float) root.getZ() + worldZ);
    }

    private static void place(ItemDisplay display, Location root, Vector3f center) {
        place(display, root, center, 0.0F);
    }

    private static void place(ItemDisplay display, Location root, Vector3f center, float yawOffset) {
        display.teleport(root, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        display.setRotation(root.getYaw() + yawOffset, 0.0F);
        display.setTransformation(new Transformation(center, new Quaternionf(),
                new Vector3f(1.0F, 1.0F, 1.0F), new Quaternionf()));
    }

    private static void placeHoseSegment(ItemDisplay segment, Vector3f start, Vector3f end, World world) {
        Vector3f direction = new Vector3f(end).sub(start);
        float length = direction.length();
        if (length < 1.0E-4F) {
            segment.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf()));
            return;
        }
        direction.div(length);
        float yaw = (float) Math.atan2(direction.x, direction.z);
        float horizontal = (float) Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float pitch = (float) Math.atan2(-direction.y, horizontal);
        Quaternionf rotation = new Quaternionf().rotateY(yaw).rotateX(pitch);

        Vector3f midpoint = new Vector3f(start).add(end).mul(0.5F);
        segment.teleport(new Location(world, midpoint.x, midpoint.y, midpoint.z));
        segment.setRotation(0.0F, 0.0F);
        segment.setTransformation(new Transformation(new Vector3f(), rotation,
                new Vector3f(HOSE_THICKNESS * 8.0F, HOSE_THICKNESS * 8.0F, length), new Quaternionf()));
    }

    private static ItemDisplay display(World world, Location location, ItemStack stack) {
        return world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            configure(display);
        });
    }

    private static void configure(Display display) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(VANILLA_ENTITY_LERP_TICKS);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
    }

    private static ItemStack model(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", name));
        item.setItemMeta(meta);
        return item;
    }

    private static void mark(Entity entity, UUID pumpId) {
        entity.addScoreboardTag(ENTITY_TAG);
        entity.addScoreboardTag("mcv_gas_pump_" + pumpId);
    }

    private void updateBrightness(Location root) {
        Display.Brightness brightness = new Display.Brightness(
                root.getBlock().getLightFromBlocks(), root.getBlock().getLightFromSky());
        for (Entity entity : entities) {
            if (entity instanceof Display display && !brightness.equals(display.getBrightness())) {
                display.setBrightness(brightness);
            }
        }
    }

    public UUID pumpId() {
        return pumpId;
    }

    public Interaction interaction() {
        return interaction;
    }

    public List<Entity> entities() {
        return Collections.unmodifiableList(entities);
    }

    public boolean valid() {
        return interaction.isValid() && bottom.isValid() && top.isValid() && nozzle.isValid()
                && hose.stream().allMatch(Entity::isValid);
    }

    public void remove() {
        entities.forEach(Entity::remove);
    }
}
