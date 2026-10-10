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
 * Vanilla-display replica of the original mod's gas pump: the two-block body, plus the nozzle
 * and hose that only exist while a player is actually holding the nozzle. A Paper plugin
 * cannot register a custom {@code TileEntityRenderer} for a vanilla client, so every part is a
 * plain {@link ItemDisplay} entity instead; the body placement, the hose's bend toward the
 * fueling player, and the nozzle's in-hand offset are all direct 1:1 ports of the original's
 * {@code GasPumpRenderer}, its {@code CollisionHelper#fixRotation}, and
 * {@code HermiteInterpolator} (see {@link HermiteSpline}).
 *
 * <p>Unlike the original -- which always shows the nozzle resting on a holder with the hose
 * draped between it and the pump -- this rig has no idle nozzle/hose display at all: nobody
 * is holding anything until a player actually picks the nozzle up, so there's nothing to
 * anchor a resting pose to that wouldn't just be floating prop geometry. The nozzle and every
 * hose segment are spawned the moment a player picks up the nozzle and removed the moment they
 * put it back, so the only things ever visible on an unused pump are its two body blocks.</p>
 *
 * <p>Two small simplifications are unavoidable on a server:
 * <ol>
 *   <li>The original nudges the held nozzle's offset when the fueling player is the local
 *   client in first-person view. The server can't know any player's camera mode, so this
 *   always uses the (more common) third-person hand offset.</li>
 *   <li>The original uses the player's smoothly interpolated {@code yBodyRot}. Bukkit doesn't
 *   expose that for remote players, so this uses the player's plain look yaw instead.</li>
 * </ol>
 * The hose's distance-based red warning tint and the slim-skin hand-offset nudge are skipped
 * for the same reason; everything else is a direct port.</p>
 */
public final class GasPumpRig {
    public static final String ENTITY_TAG = "mcv_plugin_gaspump";
    /** The original renders the hose as a continuously-interpolated quad strip; this port
     *  chains rigid straight prisms instead, so it needs more of them than the original's
     *  default ({@code Config.CLIENT.hoseSegments} = 10) to keep the joints reading as a
     *  smooth curve rather than a faceted chain. */
    private static final int HOSE_SEGMENTS = 24;
    /** The direction the hose leaves {@link #hoseStart}, the fixed end of the hose -- straight
     *  down out of the pump's outlet, same as the original. Unlike the original's hardcoded
     *  {@code (0, -5, 0)} tangent, {@link #updateActive} scales this to the actual run length
     *  each tick instead of using it as a fixed-magnitude tangent directly, so it looks right
     *  whether the fueling player is standing right against the pump or several blocks away. */
    private static final Vector3f HOSE_START_DIRECTION = new Vector3f(0.0F, -1.0F, 0.0F);
    private static final Vector3f FORWARD = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final Vector3f NOZZLE_SCALE = new Vector3f(0.8F);

    private final UUID pumpId;
    private final World world;
    private final List<Entity> all = new ArrayList<>();
    private final Interaction interaction;
    private final ItemDisplay bottom;
    private final ItemDisplay top;
    private final Vector3f hoseStart;
    private ItemDisplay nozzle;
    private final List<ItemDisplay> hoseSegments = new ArrayList<>();
    private boolean active;

    private GasPumpRig(UUID pumpId, World world, Interaction interaction, ItemDisplay bottom,
                        ItemDisplay top, Vector3f hoseStart) {
        this.pumpId = pumpId;
        this.world = world;
        this.interaction = interaction;
        this.bottom = bottom;
        this.top = top;
        this.hoseStart = hoseStart;
    }

    /** Spawns the body for a freshly-registered pump at {@code block}, facing {@code facing}.
     *  The nozzle and hose don't exist yet -- see {@link #updateActive}. */
    public static GasPumpRig spawn(Block block, BlockFace facing, UUID pumpId) {
        World world = block.getWorld();
        int x = block.getX();
        int z = block.getZ();
        // The admin-visible "block=(x, y, z)" names the ground block the pump is registered
        // at, not the display geometry's own bottom-corner Y, so the whole rig renders one
        // half-block higher than that registered Y to sit flush on top of it.
        double y = block.getY() + 0.5D;
        Quaternionf rotation = bodyRotation(facing);

        Interaction interaction = world.spawn(
                new Location(world, x + 0.5D, y, z + 0.5D), Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.0F);
            hitbox.setInteractionHeight(2.0F);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
        });
        tag(interaction, pumpId);

        ItemDisplay bottom = part(world, new Location(world, x + 0.5D, y, z + 0.5D),
                "gas_pump_bottom", new Vector3f(-0.5F, 0.0F, -0.5F), rotation,
                new Vector3f(1.0F), pumpId);
        ItemDisplay top = part(world, new Location(world, x + 0.5D, y + 1, z + 0.5D),
                "gas_pump_top", new Vector3f(-0.5F, 0.0F, -0.5F), rotation,
                new Vector3f(1.0F), pumpId);

        // The offset below is the original renderer's fixRotation() output, measured from the
        // TOP block's own minimum corner (matching how its TileEntityRenderer receives its
        // matrix stack) -- see GasPumpRenderer#render and CollisionHelper#fixRotation.
        double topCornerX = x;
        double topCornerY = y + 1;
        double topCornerZ = z;

        double[] hoseStartXZ = fixRotation(facing, 0.620625D, 1.05D, 0.620625D, 1.05D);
        Vector3f hoseStart = new Vector3f(
                (float) (topCornerX + hoseStartXZ[0]),
                (float) (topCornerY + 0.6425D),
                (float) (topCornerZ + hoseStartXZ[1]));

        GasPumpRig rig = new GasPumpRig(pumpId, world, interaction, bottom, top, hoseStart);
        rig.all.add(interaction);
        rig.all.add(bottom);
        rig.all.add(top);
        return rig;
    }

    /** Whether this rig's permanent body entities are still alive. Doesn't depend on the
     *  nozzle/hose, which are expected to not exist at all while nobody holds the nozzle. */
    public boolean valid() {
        return interaction.isValid() && bottom.isValid() && top.isValid();
    }

    /** The invisible hitbox players actually right-click to start/stop fueling, since the
     *  pump has no real block for a vanilla block-click to land on. */
    public UUID interactionId() {
        return interaction.getUniqueId();
    }

    public void remove() {
        despawnActiveEntities();
        for (Entity entity : all) {
            entity.remove();
        }
    }

    /** Raw dump of every entity this rig currently has spawned -- its live location and, for
     *  the {@link ItemDisplay} parts, the exact {@link Transformation} Bukkit reports back --
     *  for comparing a live pump directly against the formulas in this file. */
    public String debugDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("interaction @ ").append(describe(interaction.getLocation())).append('\n');
        sb.append("bottom      @ ").append(describe(bottom.getLocation()))
                .append(" model=").append(bottom.getItemStack().getItemMeta().getItemModel())
                .append(' ').append(describe(bottom.getTransformation())).append('\n');
        sb.append("top         @ ").append(describe(top.getLocation()))
                .append(" model=").append(top.getItemStack().getItemMeta().getItemModel())
                .append(' ').append(describe(top.getTransformation())).append('\n');
        sb.append("active=").append(active).append(" hoseStart=").append(hoseStart).append('\n');
        if (!active || nozzle == null) {
            sb.append("(nozzle/hose not spawned -- nobody is holding this pump's nozzle)\n");
            return sb.toString();
        }
        sb.append("nozzle      @ ").append(describe(nozzle.getLocation()))
                .append(' ').append(describe(nozzle.getTransformation())).append('\n');
        for (int i = 0; i < hoseSegments.size(); i++) {
            ItemDisplay segment = hoseSegments.get(i);
            Transformation t = segment.getTransformation();
            sb.append("hoseSegment[").append(i).append("] @ ").append(describe(segment.getLocation()))
                    .append(" scaleZ=").append(String.format("%.4f", t.getScale().z))
                    .append(" leftRotation=").append(describe(t.getLeftRotation())).append('\n');
        }
        return sb.toString();
    }

    private static String describe(Location location) {
        return String.format("(%.4f, %.4f, %.4f)", location.getX(), location.getY(), location.getZ());
    }

    private static String describe(Vector3f v) {
        return String.format("(%.4f, %.4f, %.4f)", v.x, v.y, v.z);
    }

    private static String describe(Transformation t) {
        return "translation=" + describe(t.getTranslation())
                + " leftRotation=" + describe(t.getLeftRotation())
                + " scale=" + describe(t.getScale())
                + " rightRotation=" + describe(t.getRightRotation());
    }

    private static String describe(Quaternionf q) {
        return String.format("(%.4f, %.4f, %.4f, %.4f)", q.x, q.y, q.z, q.w);
    }

    /** Removes the nozzle and hose, if anyone was holding them. Cheap to call every tick for
     *  a pump nobody is using: once idle, there's nothing left to do until a fueling session
     *  makes it {@link #updateActive} again. */
    public void setIdle() {
        if (!active) {
            return;
        }
        active = false;
        despawnActiveEntities();
    }

    /**
     * Bends the hose from the pump toward {@code playerFeet} (that player's current feet
     * position) and moves this rig's own nozzle prop to roughly that same point, so it reads
     * as "in the fueling player's hand". Spawns the nozzle and every hose segment first if
     * this is the start of a new session.
     *
     * <p>The original's real held-nozzle visual is a client-side render hook that attaches the
     * nozzle model directly to the player model's own right arm bone. A Paper plugin has no
     * way to attach a prop to a vanilla player's bones, so this instead positions this rig's
     * own prop by hand, at the exact point the original's {@code
     * GasPumpRenderer#getNozzlePosition} (non-first-person branch) computes for the hose's own
     * terminal point.
     */
    public void updateActive(Vector3f playerFeet, float bodyYawDegrees, MainHand mainHand) {
        if (!valid()) {
            return;
        }
        if (!active) {
            active = true;
            spawnActiveEntities();
        }

        Vector3f handOffset = nozzleHandOffset(bodyYawDegrees, mainHand);
        Vector3f nozzleTip = new Vector3f(playerFeet).add(0.0F, 0.8F, 0.0F).add(handOffset);

        Vector3f lookDirection = directionFromRotation(-20.0F, bodyYawDegrees);
        // The original authored its tangent lengths (hoseStart's (0, -5, 0), the end's
        // lookDirection * 3) around its own typical fueling distance, a few blocks out from
        // the pump. A fixed magnitude like that only looks right at roughly that distance: a
        // player fueling from right up against the pump (confirmed live -- about a 1.8 block
        // hoseStart-to-nozzle run) gets tangents several times LONGER than the entire curve,
        // which forces the spline to overshoot well past the nozzle before whipping back,
        // reading as "bent in several directions" instead of one smooth bend. Scaling both
        // tangents to the actual run length fixes every distance at once instead of just this
        // one measured case.
        float distance = new Vector3f(nozzleTip).sub(hoseStart).length();
        float tangentScale = distance * 0.5F;
        Vector3f startTangent = new Vector3f(HOSE_START_DIRECTION).mul(tangentScale);
        Vector3f endTangent = new Vector3f(lookDirection).mul(tangentScale);

        layHose(hoseStart, startTangent, nozzleTip, endTangent);

        Quaternionf nozzleHandRotation = new Quaternionf()
                .rotateY(radians(-bodyYawDegrees)).rotateY(radians(180.0F)).rotateX(radians(90.0F));
        Location tipLocation = new Location(world, nozzleTip.x, nozzleTip.y, nozzleTip.z);
        Vector3f translation = pivotCompensation(nozzleHandRotation, NOZZLE_SCALE, new Quaternionf());
        place(nozzle, tipLocation, translation, nozzleHandRotation, NOZZLE_SCALE, new Quaternionf());
    }

    /** Creates the nozzle and every hose segment at a throwaway location -- {@link
     *  #updateActive} repositions all of them for real in the same call, before any of this
     *  is ever sent to a client. */
    private void spawnActiveEntities() {
        Location placeholder = new Location(world, hoseStart.x, hoseStart.y, hoseStart.z);
        nozzle = display(world, placeholder, model("gas_pump_nozzle"), pumpId);
        for (int i = 0; i < HOSE_SEGMENTS; i++) {
            hoseSegments.add(display(world, placeholder, model("gas_hose_segment"), pumpId));
        }
    }

    private void despawnActiveEntities() {
        if (nozzle != null) {
            nozzle.remove();
            nozzle = null;
        }
        for (ItemDisplay segment : hoseSegments) {
            segment.remove();
        }
        hoseSegments.clear();
    }

    /**
     * Direct port of {@code GasPumpRenderer#getNozzlePosition}'s non-first-person branch: the
     * fixed offset from the fueling player's eye-height feet position to the nozzle, in that
     * player's own local space, before being rotated into world space by {@code -bodyYaw}.
     * {@code handSide} is {@code +1} when {@code mainHand} is the player's actual configured
     * main hand being right, {@code -1} for left, exactly like the original's own {@code
     * player.getMainArm() == HandSide.RIGHT ? 1 : -1}.
     */
    static Vector3f nozzleHandOffset(float bodyYawDegrees, MainHand mainHand) {
        float handSide = mainHand == MainHand.RIGHT ? 1.0F : -1.0F;
        Vector3f local = new Vector3f(-0.35F * handSide, -0.025F, -0.025F);
        return yRot(local, -radians(bodyYawDegrees));
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
     * <p>A vanilla {@code ItemDisplay}'s {@code Transformation} always pivots rotation and
     * scale on the model's own center, regardless of where the model's geometry actually sits
     * in its own local space (unlike a {@code BlockDisplay}, whose pivot is the model's
     * bottom-north-west corner). Concretely, the engine renders {@code center + rotation *
     * scale * (modelPos - center) + translation} for a one-unit model space with {@code center
     * = (0.5, 0.5, 0.5)}. Solving that for {@code translation} with the segment's own local
     * center axis ({@code x = y = 0}, not {@code 0.5}) gives {@link
     * #hoseSegmentPivotCompensation}.
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
     *  javadoc for the derivation. Split out so the math can be unit-tested without a server. */
    static Vector3f hoseSegmentPivotCompensation(Quaternionf rotation, float length) {
        return rotation.transform(new Vector3f(0.5F, 0.5F, 0.5F * length)).sub(0.5F, 0.5F, 0.5F);
    }

    /**
     * The translation that cancels an {@code ItemDisplay}'s forced center pivot for a prop
     * that should stay anchored at its own model-space origin -- i.e. so {@code modelPos =
     * (0, 0, 0)} (the point the original's {@code matrixStack.translate(...)} call placed at
     * this entity's own position) renders with zero offset from that position. {@link
     * #hoseSegmentPivotCompensation} is the {@code rightRotation = identity}, {@code scale =
     * (1, 1, length)} special case of this same derivation; this is the general form, needed
     * for the nozzle since its {@code leftRotation} is non-identity and its model does not
     * span the whole unit cube the way the pump body's does.
     *
     * <p>Solving {@code center + leftRotation.transform(scale * rightRotation.transform(modelPos
     * - center)) + translation = 0} for {@code modelPos = (0, 0, 0)} gives {@code translation =
     * leftRotation.transform(scale * rightRotation.transform(center)) - center}.
     */
    static Vector3f pivotCompensation(Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {
        Vector3f center = new Vector3f(0.5F, 0.5F, 0.5F);
        Vector3f rotatedCenter = rightRotation.transform(new Vector3f(center));
        Vector3f scaled = new Vector3f(rotatedCenter.x * scale.x, rotatedCenter.y * scale.y, rotatedCenter.z * scale.z);
        return leftRotation.transform(scaled).sub(center);
    }

    private static ItemDisplay part(World world, Location location, String modelName,
                                     Vector3f translation, Quaternionf rotation, Vector3f scale,
                                     UUID pumpId) {
        ItemDisplay display = display(world, location, model(modelName), pumpId);
        display.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
        return display;
    }

    /**
     * Places {@code display} at {@code anchor} with the given {@code Transformation}. The
     * original's {@code GasPumpRenderer#render} renders the nozzle with the single literal
     * matrix-stack sequence {@code translate(pos) -> rotateY(facing) -> rotateY(180) ->
     * rotateX(90) -> scale(0.8) -> render}; that whole sequence is already baked into {@code
     * leftRotation} (see {@code nozzleHandRotation}), so {@code sourceRightRotation} is passed
     * straight through unchanged, matching {@link #part} and {@link #placeSegment}.
     */
    private static void place(ItemDisplay display, Location anchor, Vector3f translation,
                               Quaternionf leftRotation, Vector3f scale, Quaternionf sourceRightRotation) {
        display.teleport(anchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        display.setTransformation(new Transformation(translation, leftRotation, scale, sourceRightRotation));
    }

    private static ItemDisplay display(World world, Location location, ItemStack stack, UUID pumpId) {
        ItemDisplay display = world.spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(stack);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(1);
            // This rig's nozzle and hose segments are re-teleported to a freshly computed,
            // authoritative position every tick while a session is active, so an instant
            // teleport (rather than any extra client-side smoothing) keeps position and this
            // tick's freshly-set rotation/scale in sync.
            entity.setTeleportDuration(0);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setShadowRadius(0.0F);
            entity.setShadowStrength(0.0F);
        });
        tag(display, pumpId);
        return display;
    }

    private static void tag(Entity entity, UUID pumpId) {
        entity.addScoreboardTag(ENTITY_TAG);
        entity.addScoreboardTag("mcv_pump_" + pumpId);
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
     *  west = 270). */
    static float blockstateYDegrees(BlockFace facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    /**
     * The rotation each body part's own {@code Transformation} needs to bake in for {@code
     * facing}: plain {@code rotateY(-D)} for blockstate degree value {@code D}, reproducing
     * exactly what a real block's own {@code "y": D} blockstate rotation does to that model
     * (checked against vanilla's own directional blocks, e.g. a furnace's
     * {@code facing=east} variant uses {@code "y": 90}).
     */
    static Quaternionf bodyRotation(BlockFace facing) {
        float degrees = -blockstateYDegrees(facing);
        return new Quaternionf().rotateY(radians(degrees));
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
