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
    /** The original's own {@code Config.CLIENT.hoseSegments} default is 10, but that was tuned
     *  for its continuously-varying, per-vertex-blended procedural quad strip (see
     *  {@code GasPumpRenderer#drawHose}). This port instead chains rigid straight prisms, each
     *  with one constant orientation along its whole length, so the same 10-way split leaves a
     *  visible facet/notch at every joint where the chain bends sharply (most noticeably right
     *  where the hose leaves the pump). Raised well past the original's value, as a disclosed
     *  deviation, to keep those joint angles small enough to read as a smooth curve instead. */
    private static final int HOSE_SEGMENTS = 24;
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
     * position) and moves this rig's own nozzle prop to roughly that same point, so it reads
     * as "in the fueling player's hand" instead of resting on its idle holder.
     *
     * <p>The original mod's real held-nozzle visual is a client-side render hook
     * ({@code FuelingHandler}) that attaches the nozzle model directly to the player model's
     * own right arm bone -- always the right arm, regardless of the player's configured main
     * hand -- so it automatically tracks real arm swing/animation. A Paper plugin has no way to
     * attach a prop to a vanilla player's bones, and routing it through a real off-hand item
     * (tried in an earlier revision of this method) was rejected: it visibly occupied the
     * player's own inventory/off-hand slot, which reads as a bug rather than a cosmetic effect.
     * So this keeps the original (pre-off-hand) approach of positioning this rig's own prop by
     * hand -- always on the player's right side to match the original's always-right-hand
     * bone attachment -- with its forward offset pushed out further than the original's own
     * (near-zero) value, per direct in-game feedback that the literal source value reads as
     * glued to the player's hip instead of visibly held out in front of them.
     */
    public void updateActive(Vector3f playerFeet, float bodyYawDegrees, MainHand mainHand) {
        if (!valid()) {
            return;
        }
        if (!active) {
            active = true;
            nozzleRestTransformApplied = false;
        }

        // Always the right side, matching the original's always-right-arm bone attachment
        // (FuelingHandler#onModelRenderPost's hardcoded HandSide.RIGHT) -- unlike the hose's
        // own terminal point in the original, which does vary with the player's configured
        // main hand, this visible prop never did.
        Vector3f handOffset = new Vector3f(-0.35F, 0.1F, 0.4F);
        handOffset = yRot(handOffset, -radians(bodyYawDegrees));
        Vector3f nozzleTip = new Vector3f(playerFeet).add(0.0F, 0.8F, 0.0F).add(handOffset);

        Vector3f lookDirection = directionFromRotation(-20.0F, bodyYawDegrees);
        Vector3f endTangent = new Vector3f(lookDirection).mul(3.0F);

        layHose(hoseStart, HOSE_START_TANGENT, nozzleTip, endTangent);

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
     * midpoint.
     *
     * <p>An earlier revision of this method assumed {@code from} could be used directly as a
     * plain rotate-then-scale pivot, reasoning that {@code gas_hose_segment.json}'s element
     * starts at the model's own local origin rather than its center. That assumption is wrong
     * for a vanilla {@code ItemDisplay}: per Minecraft's own display-entity documentation,
     * <i>"the rotation pivot of the item display's transformation is the center of the item
     * model"</i> -- unlike a {@code BlockDisplay}, whose pivot is the model's bottom-north-west
     * corner (which is what the old comment's reasoning actually described), and unconditionally
     * true regardless of {@code ItemDisplayTransform}, including {@code NONE}. Concretely, the
     * engine renders {@code center + rotation * scale * (modelPos - center) + translation}
     * (relative to the entity's own position) for a one-unit model space with {@code center =
     * (0.5, 0.5, 0.5)} -- so leaving {@code translation} at zero left every segment's near end
     * dangling half its own length <em>and</em> half its cross-section-width off to the side of
     * {@code from} (the offset rotating into whatever direction that particular segment pointed,
     * since {@code rotation} varies per segment), instead of running cleanly from {@code from}
     * to {@code from + length * direction} -- the exact "scattered, disconnected" look reported
     * in-game, independent of wherever the chain's start and end points themselves are. Solving
     * that equation for {@code translation} with the segment's own local center axis ({@code x =
     * y = 0}, not {@code 0.5}) gives the compensation below: {@code rotation.transform(0.5, 0.5,
     * 0.5 * length) - (0.5, 0.5, 0.5)}, which collapses to exactly zero only in the degenerate
     * unrotated, unscaled (length 1) case.
     */
    private static void placeSegment(ItemDisplay segment, Vector3f from, Quaternionf rotation, float length) {
        Location anchor = segment.getLocation();
        anchor.setX(from.x);
        anchor.setY(from.y);
        anchor.setZ(from.z);
        segment.teleport(anchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        segment.setTransformation(new Transformation(hoseSegmentPivotCompensation(rotation, length),
                rotation, new Vector3f(1.0F, 1.0F, length), new Quaternionf()));
    }

    /** The translation that cancels an {@code ItemDisplay}'s forced center pivot for one hose
     *  segment, given its current orientation and length -- see {@link #placeSegment}'s own
     *  javadoc for the full derivation. Split out purely so the math itself (unlike the real
     *  {@link ItemDisplay} it feeds into) can be unit-tested without a running server. */
    static Vector3f hoseSegmentPivotCompensation(Quaternionf rotation, float length) {
        return rotation.transform(new Vector3f(0.5F, 0.5F, 0.5F * length)).sub(0.5F, 0.5F, 0.5F);
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
            // Unlike LandVehicleRig's parts (which ride a continuously, natively-interpolated
            // vehicle entity and benefit from a few ticks of client-side position smoothing),
            // this rig's nozzle and hose segments are re-teleported to a freshly computed,
            // authoritative position every single tick while a session is active. Any extra
            // teleport smoothing on top of that only fights the fresh target each tick,
            // showing up as the hose/nozzle visibly lagging behind -- or briefly sliding across
            // the whole gap -- right when a player picks up or puts down a nozzle, since that's
            // when the target position jumps the furthest in one tick. Instant teleports keep
            // position and this tick's freshly-set rotation/scale in sync.
            entity.setTeleportDuration(0);
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
