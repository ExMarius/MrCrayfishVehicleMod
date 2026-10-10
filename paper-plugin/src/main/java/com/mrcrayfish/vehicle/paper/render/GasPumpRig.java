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
 * Vanilla-display replica of the original mod's gas pump: the two-block body, the idle
 * nozzle resting on its holder, and the hose that bends toward whichever player is fueling.
 * A Paper plugin cannot register a custom {@code TileEntityRenderer} for a vanilla client, so
 * every part is a plain {@link ItemDisplay} entity instead; every position, rotation, and
 * curve is a direct 1:1 port of the original's {@code GasPumpRenderer}, its
 * {@code CollisionHelper#fixRotation}, and {@code HermiteInterpolator} (see {@link HermiteSpline}),
 * with one deliberate exception: the idle hose's own Hermite tangents are rescaled (see
 * {@link #HOSE_START_TANGENT}) because the original's own values make the curve dip well
 * below ground here, as confirmed on a live server -- see that field's own javadoc.
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
    /** The original's own tangents for this same pair of points are {@code (0, -5, 0)} and
     *  {@code (0, 3, 0)} -- fine for its continuously-interpolated ribbon mesh, but those
     *  magnitudes are 2-9x the {@code 0.54}-block vertical gap between {@link #hoseStart} and
     *  {@link #idleEnd}, so the Hermite curve massively overshoots: sampling it confirms the
     *  curve dips to about {@code 1.27} blocks below the hose's own start point, i.e. well
     *  below this rig's own bottom block and partway into the ground it's standing on. These
     *  values are deliberately NOT a 1:1 port of the original's: they're scaled down to the
     *  same order of magnitude as that vertical gap, which keeps the idle hose's little
     *  resting loop near the nozzle holder instead of clipping through the floor. */
    private static final Vector3f HOSE_START_TANGENT = new Vector3f(0.0F, -1.0F, 0.0F);
    private static final Vector3f IDLE_END_TANGENT = new Vector3f(0.0F, 0.5F, 0.0F);
    private static final Vector3f FORWARD = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final Vector3f NOZZLE_SCALE = new Vector3f(0.8F);

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
        int z = block.getZ();
        // The admin-visible "block=(x, y, z)" names the ground block the pump is registered
        // at, not the display geometry's own bottom-corner Y, so the whole rig renders one
        // half-block higher than that registered Y to sit flush on top of it.
        double y = block.getY() + 0.5D;
        Quaternionf rotation = bodyRotation(facing);

        List<Entity> all = new ArrayList<>();

        Interaction interaction = world.spawn(
                new Location(world, x + 0.5D, y, z + 0.5D), Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.0F);
            hitbox.setInteractionHeight(2.0F);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
        });
        interaction.addScoreboardTag(ENTITY_TAG);
        interaction.addScoreboardTag("mcv_pump_" + pumpId);
        all.add(interaction);

        ItemDisplay bottom = part(world, new Location(world, x + 0.5D, y, z + 0.5D),
                "gas_pump_bottom", new Vector3f(-0.5F, 0.0F, -0.5F), rotation,
                new Vector3f(1.0F), pumpId, all);
        ItemDisplay top = part(world, new Location(world, x + 0.5D, y + 1, z + 0.5D),
                "gas_pump_top", new Vector3f(-0.5F, 0.0F, -0.5F), rotation,
                new Vector3f(1.0F), pumpId, all);

        // All offsets below are the original renderer's fixRotation() outputs, measured from
        // the TOP block's own minimum corner (matching how its TileEntityRenderer receives its
        // matrix stack) -- see GasPumpRenderer#render and CollisionHelper#fixRotation.
        double topCornerX = x;
        double topCornerY = y + 1;
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

    /** Raw dump of every entity this rig spawned -- its live location and, for the
     *  {@link ItemDisplay} parts, the exact {@link Transformation} Bukkit reports back --
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
        sb.append("nozzle      @ ").append(describe(nozzle.getLocation()))
                .append(' ').append(describe(nozzle.getTransformation())).append('\n');
        sb.append("nozzleRestLocation=").append(describe(nozzleRestLocation)).append('\n');
        sb.append("active=").append(active).append(" hoseStart=").append(hoseStart)
                .append(" idleEnd=").append(idleEnd).append('\n');
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
            Vector3f translation = pivotCompensation(nozzleRestRotation, NOZZLE_SCALE, new Quaternionf());
            place(nozzle, nozzleRestLocation, translation, nozzleRestRotation, NOZZLE_SCALE, new Quaternionf());
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
            nozzleRestTransformApplied = false;
        }

        Vector3f handOffset = nozzleHandOffset(bodyYawDegrees, mainHand);
        Vector3f nozzleTip = new Vector3f(playerFeet).add(0.0F, 0.8F, 0.0F).add(handOffset);

        Vector3f lookDirection = directionFromRotation(-20.0F, bodyYawDegrees);
        Vector3f endTangent = new Vector3f(lookDirection).mul(3.0F);

        layHose(hoseStart, HOSE_START_TANGENT, nozzleTip, endTangent);

        Quaternionf nozzleHandRotation = new Quaternionf()
                .rotateY(radians(-bodyYawDegrees)).rotateY(radians(180.0F)).rotateX(radians(90.0F));
        Location tipLocation = new Location(nozzle.getWorld(), nozzleTip.x, nozzleTip.y, nozzleTip.z);
        Vector3f translation = pivotCompensation(nozzleHandRotation, NOZZLE_SCALE, new Quaternionf());
        place(nozzle, tipLocation, translation, nozzleHandRotation, NOZZLE_SCALE, new Quaternionf());
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
                                     UUID pumpId, List<Entity> all) {
        ItemDisplay display = display(world, location, model(modelName), pumpId, all);
        display.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
        return display;
    }

    /**
     * Places {@code display} at {@code anchor} with the given {@code Transformation}. The
     * original's {@code GasPumpRenderer#render} renders the nozzle with the single literal
     * matrix-stack sequence {@code translate(pos) -> rotateY(facing) -> rotateY(180) ->
     * rotateX(90) -> scale(0.8) -> render}; that whole sequence is already baked into {@code
     * leftRotation} (see {@code nozzleRestRotation}/{@code nozzleHandRotation}), so {@code
     * sourceRightRotation} is passed straight through unchanged, matching {@link #part} and
     * {@link #placeSegment}.
     */
    private static void place(ItemDisplay display, Location anchor, Vector3f translation,
                               Quaternionf leftRotation, Vector3f scale, Quaternionf sourceRightRotation) {
        display.teleport(anchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        display.setTransformation(new Transformation(translation, leftRotation, scale, sourceRightRotation));
    }

    private static ItemDisplay display(World world, Location location, ItemStack stack, UUID pumpId,
                                        List<Entity> all) {
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
