package com.mrcrayfish.vehicle.paper.render;

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
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vanilla-display replica of the original mod's gas pump cosmetics: the two-block body,
 * the idle nozzle resting on its holder, and the hose that bends toward whichever player
 * is fueling. Ported 1:1 from {@code GasPumpRenderer} (the original's TileEntityRenderer)
 * and {@code HermiteInterpolator}, with the Forge-only matrix-stack calls translated to
 * vanilla {@link ItemDisplay} entities since display entities are all a vanilla client can
 * actually render.
 *
 * <p>Two disclosed simplifications versus the original (both unavoidable server-side):
 * <ol>
 *   <li>The original picks a different nozzle-hand offset when the fueling player is the
 *   local client in first-person view. The server can't know any player's camera mode, so
 *   this always uses the (more common) non-first-person "third person" hand offset.</li>
 *   <li>The original uses each player's smoothly-interpolated {@code yBodyRot}, which can
 *   briefly lag behind head yaw during a fast head turn. Bukkit does not expose that value
 *   for remote players, so this substitutes the player's plain look yaw.</li>
 * </ol>
 * The hose's distance-based red warning tint and the slim-skin hand-offset nudge are also
 * skipped as minor, disclosed simplifications; everything else (geometry, pivot points,
 * and the Hermite-spline bend itself) is a direct port.</p>
 */
public final class GasPumpRig {
    public static final String ENTITY_TAG = "mcv_plugin_gaspump";
    private static final int VANILLA_ENTITY_LERP_TICKS = 3;
    /** Matches the original's {@code Config.CLIENT.hoseSegments} default of 10. */
    private static final int HOSE_SEGMENTS = 10;
    private static final Vector3f HOSE_START_TANGENT = new Vector3f(0.0F, -5.0F, 0.0F);
    private static final Vector3f IDLE_END_TANGENT = new Vector3f(0.0F, 3.0F, 0.0F);
    private static final Vector3f FORWARD = new Vector3f(0.0F, 0.0F, 1.0F);
    /** Raises the whole rig half a block above the registered pump position, per in-game
     *  testing feedback -- flush with the targeted block looked too low. */
    private static final double VERTICAL_OFFSET = 0.5D;

    private final UUID pumpId;
    private final List<Entity> all = new ArrayList<>();
    private final Interaction interaction;
    private final ItemDisplay bottom;
    private final ItemDisplay top;
    private final ItemDisplay nozzle;
    private final ItemStack nozzleModel;
    private final List<ItemDisplay> hoseSegments = new ArrayList<>();
    private final Vector3f hoseStart;
    private final Vector3f idleEnd;
    private final Location nozzleRestLocation;
    private final Quaternionf nozzleRestRotation;
    private boolean active;
    private boolean nozzleRestTransformApplied;
    private boolean idleHoseApplied;

    private GasPumpRig(UUID pumpId, Interaction interaction, ItemDisplay bottom, ItemDisplay top,
                        ItemDisplay nozzle, ItemStack nozzleModel, Vector3f hoseStart, Vector3f idleEnd,
                        Location nozzleRestLocation, Quaternionf nozzleRestRotation) {
        this.pumpId = pumpId;
        this.interaction = interaction;
        this.bottom = bottom;
        this.top = top;
        this.nozzle = nozzle;
        this.nozzleModel = nozzleModel;
        this.hoseStart = hoseStart;
        this.idleEnd = idleEnd;
        this.nozzleRestLocation = nozzleRestLocation;
        this.nozzleRestRotation = nozzleRestRotation;
    }

    /** Spawns the full rig for a freshly-registered pump at {@code block}, facing {@code facing}. */
    public static GasPumpRig spawn(Block block, BlockFace facing, UUID pumpId) {
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        float entityYaw = entityYawForFacing(facing);

        List<Entity> all = new ArrayList<>();

        Interaction interaction = world.spawn(
                new Location(world, x + 0.5D, y + VERTICAL_OFFSET, z + 0.5D), Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.0F);
            hitbox.setInteractionHeight(2.0F);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
        });
        interaction.addScoreboardTag(ENTITY_TAG);
        interaction.addScoreboardTag("mcv_pump_" + pumpId);
        all.add(interaction);

        ItemDisplay bottom = part(world, new Location(world, x + 0.5D, y + VERTICAL_OFFSET, z + 0.5D),
                "gas_pump_bottom", entityYaw, new Vector3f(-0.5F, 0.0F, -0.5F), new Quaternionf(),
                new Vector3f(1.0F), new Quaternionf(), pumpId, all);
        ItemDisplay top = part(world, new Location(world, x + 0.5D, y + 1 + VERTICAL_OFFSET, z + 0.5D),
                "gas_pump_top", entityYaw, new Vector3f(-0.5F, 0.0F, -0.5F), new Quaternionf(),
                new Vector3f(1.0F), new Quaternionf(), pumpId, all);

        // All offsets below are the original renderer's fixRotation() outputs, measured from the
        // TOP block's own minimum corner (matching how its TileEntityRenderer receives its
        // matrix stack) -- see GasPumpRenderer#render and CollisionHelper#fixRotation.
        double topCornerX = x;
        double topCornerY = y + 1 + VERTICAL_OFFSET;
        double topCornerZ = z;

        double[] hoseStartXZ = fixRotation(facing, 0.620625D, 1.05D, 0.620625D, 1.05D);
        Vector3f hoseStart = new Vector3f(
                (float) (topCornerX + hoseStartXZ[0]),
                (float) (topCornerY + 0.6425D),
                (float) (topCornerZ + hoseStartXZ[1]));

        double[] idleEndXZ = fixRotation(facing, 0.345D, 1.06D, 0.345D, 1.06D);
        Vector3f idleEnd = new Vector3f(
                (float) (topCornerX + idleEndXZ[0]),
                (float) (topCornerY + 0.1D),
                (float) (topCornerZ + idleEndXZ[1]));

        double[] nozzleRestXZ = fixRotation(facing, 0.29D, 1.06D, 0.29D, 1.06D);
        Location nozzleRestLocation = new Location(world,
                topCornerX + nozzleRestXZ[0], topCornerY + 0.5D, topCornerZ + nozzleRestXZ[1]);

        float yAngle = get2DDataValue(facing) * -90.0F;
        Quaternionf nozzleRestRotation = new Quaternionf()
                .rotateY(radians(yAngle)).rotateY(radians(180.0F)).rotateX(radians(90.0F));

        ItemStack nozzleModel = model("gas_pump_nozzle");
        ItemDisplay nozzle = display(world, nozzleRestLocation, nozzleModel, pumpId, all);

        List<ItemDisplay> hoseSegments = new ArrayList<>();
        for (int i = 0; i < HOSE_SEGMENTS; i++) {
            ItemDisplay segment = display(world, nozzleRestLocation, model("gas_hose_segment"), pumpId, all);
            hoseSegments.add(segment);
        }

        GasPumpRig rig = new GasPumpRig(pumpId, interaction, bottom, top, nozzle, nozzleModel,
                hoseStart, idleEnd, nozzleRestLocation, nozzleRestRotation);
        rig.all.addAll(all);
        rig.hoseSegments.addAll(hoseSegments);
        rig.setIdle();
        return rig;
    }

    public boolean valid() {
        return interaction.isValid() && bottom.isValid() && top.isValid() && nozzle.isValid()
                && hoseSegments.stream().allMatch(Entity::isValid);
    }

    /** The invisible hitbox players actually right-click to start/stop fueling, since the
     *  pump has no real block for a vanilla block-click to land on. */
    public UUID interactionId() {
        return interaction.getUniqueId();
    }

    public void remove() {
        for (Entity entity : all) {
            entity.remove();
        }
    }

    /** Rests the nozzle on its holder and drapes the hose in its idle curve. Cheap to call
     *  every tick for a pump nobody is using: once idle, it does nothing further until a
     *  fueling session makes it {@link #updateActive} again. */
    public void setIdle() {
        if (!valid()) {
            return;
        }
        if (active) {
            active = false;
            idleHoseApplied = false;
            nozzle.setItemStack(nozzleModel);
        }
        if (!nozzleRestTransformApplied) {
            nozzleRestTransformApplied = true;
            place(nozzle, nozzleRestLocation, new Vector3f(0.0F), nozzleRestRotation,
                    new Vector3f(0.8F), new Quaternionf());
        }
        if (!idleHoseApplied) {
            idleHoseApplied = true;
            layHose(hoseStart, HOSE_START_TANGENT, idleEnd, IDLE_END_TANGENT);
        }
    }

    /**
     * Bends the hose from the pump toward {@code playerFeet} (that player's current feet
     * position) and moves the nozzle prop to that same point, so it now reads as "in the
     * fueling player's hand" instead of resting on its idle holder (matching
     * {@code FuelingHandler#onRenderHand}/{@code onModelRenderPost}, which likewise draw the
     * nozzle model near the holding player's hand instead of on the pump once picked up).
     * {@code bodyYawDegrees} substitutes the player's plain look yaw for the original's
     * interpolated body yaw (see class javadoc).
     *
     * <p>The original renderer picks between two different hand offsets depending on the
     * fueling player's own client-side camera mode: a small, low, hand-side-dependent offset
     * for third person, and a taller, further-forward, hand-side-independent offset
     * ({@code (-0.25, 0.5, -0.25)} rotated by look yaw) for first person. The server has no way
     * to know any player's camera mode, so this always uses the first-person offset, since that
     * is Minecraft's default view and therefore what most players see while fueling; {@code
     * mainHand} is accepted but unused as a result, since the original's first-person branch
     * does not depend on it either.</p>
     */
    public void updateActive(Vector3f playerFeet, float bodyYawDegrees, MainHand mainHand) {
        if (!valid()) {
            return;
        }
        if (!active) {
            active = true;
            nozzleRestTransformApplied = false;
        }

        Vector3f handOffset = new Vector3f(-0.25F, 0.5F, -0.25F);
        handOffset = yRot(handOffset, -radians(bodyYawDegrees));
        Vector3f nozzleTip = new Vector3f(playerFeet).add(0.0F, 0.8F, 0.0F).add(handOffset);

        Vector3f lookDirection = directionFromRotation(-20.0F, bodyYawDegrees);
        Vector3f endTangent = new Vector3f(lookDirection).mul(3.0F);

        layHose(hoseStart, HOSE_START_TANGENT, nozzleTip, endTangent);

        // Same rotation formula as the idle holder's (rotateY(yAngle).rotateY(180).rotateX(90)),
        // just driven by the player's continuous look yaw instead of the pump's quantized
        // cardinal facing -- see GasPumpManager#cardinalFacing for why these two yaw
        // conventions line up (both treat yaw 0/90/180/270 as south/west/north/east).
        Quaternionf nozzleHandRotation = new Quaternionf()
                .rotateY(radians(-bodyYawDegrees)).rotateY(radians(180.0F)).rotateX(radians(90.0F));
        Location tipLocation = new Location(nozzle.getWorld(), nozzleTip.x, nozzleTip.y, nozzleTip.z);
        place(nozzle, tipLocation, new Vector3f(0.0F), nozzleHandRotation, new Vector3f(0.8F),
                new Quaternionf());
    }

    private void layHose(Vector3f startPos, Vector3f startTangent, Vector3f endPos, Vector3f endTangent) {
        HermiteSpline spline = new HermiteSpline(startPos, startTangent, endPos, endTangent);
        int sampleCount = HOSE_SEGMENTS + 1;
        Vector3f[] samples = new Vector3f[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = spline.point(i / (float) HOSE_SEGMENTS);
        }
        for (int i = 0; i < HOSE_SEGMENTS; i++) {
            Vector3f from = samples[i];
            Vector3f to = samples[i + 1];
            Vector3f direction = new Vector3f(to).sub(from);
            float length = direction.length();
            Quaternionf rotation;
            if (length < 1.0E-5F) {
                rotation = new Quaternionf();
                length = 1.0E-5F;
            } else {
                direction.div(length);
                rotation = new Quaternionf().rotationTo(FORWARD, direction);
            }
            placeSegment(hoseSegments.get(i), from, rotation, length);
        }
    }

    /**
     * Positions one hose segment so it runs exactly from {@code from} to {@code from + length *
     * rotation(FORWARD)}, i.e. {@code from} is the segment's own entity position, not its
     * midpoint. This only works because {@code gas_hose_segment.json}'s element is centered on
     * (and starts at) the model's own local origin -- unlike every other part in this rig
     * (ported straight from real exported block models), this one is new geometry authored for
     * this port, so it doesn't need the universal left/right-rotation compensation {@link
     * #place} and {@link #part} apply for those; a plain rotate-then-scale is enough.
     */
    private static void placeSegment(ItemDisplay segment, Vector3f from, Quaternionf rotation, float length) {
        Location anchor = segment.getLocation();
        anchor.setX(from.x);
        anchor.setY(from.y);
        anchor.setZ(from.z);
        segment.teleport(anchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        segment.setTransformation(new Transformation(new Vector3f(0.0F), rotation,
                new Vector3f(1.0F, 1.0F, length), new Quaternionf()));
    }


    private static ItemDisplay part(World world, Location location, String modelName, float entityYaw,
                                     Vector3f translation, Quaternionf leftRotation, Vector3f scale,
                                     Quaternionf sourceRightRotation, UUID pumpId, List<Entity> all) {
        ItemDisplay display = display(world, location, model(modelName), pumpId, all);
        display.setRotation(entityYaw, 0.0F);
        Quaternionf compensation = new Quaternionf(sourceRightRotation).rotateY((float) Math.PI);
        display.setTransformation(new Transformation(translation, leftRotation, scale, compensation));
        return display;
    }

    private static void place(ItemDisplay display, Location anchor, Vector3f translation,
                               Quaternionf leftRotation, Vector3f scale, Quaternionf sourceRightRotation) {
        display.teleport(anchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        Quaternionf compensation = new Quaternionf(sourceRightRotation).rotateY((float) Math.PI);
        display.setTransformation(new Transformation(translation, leftRotation, scale, compensation));
    }

    private static ItemDisplay display(World world, Location location, ItemStack stack, UUID pumpId,
                                        List<Entity> all) {
        ItemDisplay display = world.spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(stack);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(1);
            entity.setTeleportDuration(VANILLA_ENTITY_LERP_TICKS);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setShadowRadius(0.0F);
            entity.setShadowStrength(0.0F);
        });
        display.addScoreboardTag(ENTITY_TAG);
        display.addScoreboardTag("mcv_pump_" + pumpId);
        all.add(display);
        return display;
    }

    private static ItemStack model(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", name));
        item.setItemMeta(meta);
        return item;
    }

    /** NORTH/EAST/SOUTH/WEST -> the same "y" rotation this pack's blockstates/gas_pump.json
     *  gives the real block model for that facing (north = 0, east = 90, south = 180,
     *  west = 270). The universal item-display 180-degree flip every part in this rig
     *  needs is handled separately, in {@code part()}'s right-rotation compensation --
     *  exactly like every other part of {@link LandVehicleRig}, so it is deliberately not
     *  folded into this table too. */
    static float entityYawForFacing(BlockFace facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    /** Direct port of {@code Direction.get2DDataValue()} (also the F3 debug screen's "f" value). */
    static int get2DDataValue(BlockFace facing) {
        return switch (facing) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /** Direct port of {@code CollisionHelper#fixRotation(Direction, double, double, double, double)}. */
    static double[] fixRotation(BlockFace facing, double x1, double z1, double x2, double z2) {
        switch (facing) {
            case WEST -> {
                double origX1 = x1;
                x1 = 1.0D - x2;
                double origZ1 = z1;
                z1 = 1.0D - z2;
                x2 = 1.0D - origX1;
                z2 = 1.0D - origZ1;
            }
            case NORTH -> {
                double origX1 = x1;
                x1 = z1;
                z1 = 1.0D - x2;
                x2 = z2;
                z2 = 1.0D - origX1;
            }
            case SOUTH -> {
                double origX1 = x1;
                x1 = 1.0D - z2;
                double origZ1 = z1;
                z1 = origX1;
                double origX2 = x2;
                x2 = 1.0D - origZ1;
                z2 = origX2;
            }
            default -> {
            }
        }
        return new double[]{x1, z1, x2, z2};
    }

    /** Direct port of vanilla's {@code Vector3d#yRot(float)}. */
    static Vector3f yRot(Vector3f vector, float angleRadians) {
        float cos = (float) Math.cos(angleRadians);
        float sin = (float) Math.sin(angleRadians);
        return new Vector3f(
                vector.x * cos + vector.z * sin,
                vector.y,
                vector.z * cos - vector.x * sin);
    }

    /** Direct port of vanilla's {@code Vector3d#directionFromRotation(float, float)}. */
    static Vector3f directionFromRotation(float pitchDegrees, float yawDegrees) {
        float f = (float) Math.cos(Math.toRadians(-yawDegrees) - Math.PI);
        float f1 = (float) Math.sin(Math.toRadians(-yawDegrees) - Math.PI);
        float f2 = (float) -Math.cos(Math.toRadians(-pitchDegrees));
        float f3 = (float) Math.sin(Math.toRadians(-pitchDegrees));
        return new Vector3f(f1 * f2, f3, f * f2);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
