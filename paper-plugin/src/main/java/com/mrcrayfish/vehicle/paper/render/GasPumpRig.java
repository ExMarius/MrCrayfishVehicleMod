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
        // The theoretical "zero extra vertical offset" placement (the registered block's own
        // bottom-corner Y, with no adjustment -- see the removed VERTICAL_OFFSET this comment
        // used to describe) turned out, per direct in-game visual confirmation standing at the
        // actual spawned rig, to render the whole rig half a block into the ground. Unlike that
        // theoretical argument (which was never checked against a live client), this +0.5 is a
        // live-tested correction: the admin-visible "block=(x, y, z)" position names the
        // *ground* block the pump's registered at, not literally the display geometry's own
        // bottom-corner Y, so the whole rig (body, hose, and nozzle alike) needs to render one
        // half-block higher than that registered Y to sit flush on top of it instead of
        // clipping into it.
        double y = block.getY() + 0.5D;
        // Direct instruction, latest pass: rotate the whole rig together now, not just the
        // body -- every facing-driven rotation/offset below (body rotation, hose anchors,
        // nozzle rest position and orientation) now consistently uses the opposite cardinal
        // from the registered facing, instead of only the body doing so while hose/nozzle kept
        // using the registered facing. All of the separately-tuned world-space nudges (body
        // east, hose west, nozzle pixel-east, rig south) stay exactly as they were.
        BlockFace renderFacing = facing.getOppositeFace();
        Quaternionf rotation = bodyRotation(renderFacing);

        // Deliberate, isolated diagnostic per direct instruction: nudges only the clickable
        // body (the interaction hitbox plus the bottom/top blocks) half a block to the east --
        // the admin's right hand when standing facing north, as given -- while every hose/nozzle
        // anchor below (topCornerX/topCornerZ, hoseStart, idleEnd, nozzleRestLocation) keeps
        // using the original, un-nudged x/z so the hose and nozzle don't move at all.
        //
        // Direct instruction, corrected pass: the half-block-south nudge is NOT nozzle-only --
        // it applies to the *whole rig together* (body, hose, and nozzle alike), so it's folded
        // into bodyZ/topCornerZ here instead of being added only to nozzleRestLocation below.
        double rigSouthOffset = 0.5D;
        double bodyX = x + 0.5D + 0.5D;
        double bodyZ = z + 0.5D + rigSouthOffset;

        List<Entity> all = new ArrayList<>();

        Interaction interaction = world.spawn(
                new Location(world, bodyX, y, bodyZ), Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.0F);
            hitbox.setInteractionHeight(2.0F);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
        });
        interaction.addScoreboardTag(ENTITY_TAG);
        interaction.addScoreboardTag("mcv_pump_" + pumpId);
        all.add(interaction);

        ItemDisplay bottom = part(world, new Location(world, bodyX, y, bodyZ),
                "gas_pump_bottom", new Vector3f(-0.5F, 0.0F, -0.5F), rotation,
                new Vector3f(1.0F), pumpId, all);
        ItemDisplay top = part(world, new Location(world, bodyX, y + 1, bodyZ),
                "gas_pump_top", new Vector3f(-0.5F, 0.0F, -0.5F), rotation,
                new Vector3f(1.0F), pumpId, all);

        // All offsets below are the original renderer's fixRotation() outputs, measured from the
        // TOP block's own minimum corner (matching how its TileEntityRenderer receives its
        // matrix stack) -- see GasPumpRenderer#render and CollisionHelper#fixRotation.
        double topCornerX = x;
        double topCornerY = y + 1;
        double topCornerZ = z + rigSouthOffset;

        // Two more isolated diagnostics per direct instruction, on top of the body-only
        // east nudge above: slide the whole idle hose (both its anchor points, so the curve
        // translates as a unit instead of stretching) half a block to the west -- the admin's
        // left hand facing north -- and slide the nozzle's own rest spot 1 pixel (1/16 block,
        // Minecraft's standard texture-pixel unit) to the east, the admin's right hand. Neither
        // offset touches the other part, nor the body position/rotation handled above.
        double hoseOffsetX = -0.5D;
        double nozzlePixelOffsetX = 1.0D / 16.0D;

        double[] hoseStartXZ = fixRotation(renderFacing, 0.620625D, 1.05D, 0.620625D, 1.05D);
        Vector3f hoseStart = new Vector3f(
                (float) (topCornerX + hoseStartXZ[0] + hoseOffsetX),
                (float) (topCornerY + 0.6425D),
                (float) (topCornerZ + hoseStartXZ[1]));

        double[] idleEndXZ = fixRotation(renderFacing, 0.345D, 1.06D, 0.345D, 1.06D);
        Vector3f idleEnd = new Vector3f(
                (float) (topCornerX + idleEndXZ[0] + hoseOffsetX),
                (float) (topCornerY + 0.1D),
                (float) (topCornerZ + idleEndXZ[1]));

        double[] nozzleRestXZ = fixRotation(renderFacing, 0.29D, 1.06D, 0.29D, 1.06D);
        Location nozzleRestLocation = new Location(world,
                topCornerX + nozzleRestXZ[0] + nozzlePixelOffsetX, topCornerY + 0.5D,
                topCornerZ + nozzleRestXZ[1]);


        float yAngle = get2DDataValue(renderFacing) * -90.0F;
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

    /** Raw ground-truth dump of every entity this rig actually spawned -- its live location
     *  and, for the {@link ItemDisplay} parts, the exact {@link Transformation} Bukkit reports
     *  back (not merely what this class intended to set), so a live report can be compared
     *  directly against the formulas in this file without trusting a screenshot's perspective. */
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
     * <p>The original mod's real held-nozzle visual is a client-side render hook
     * ({@code FuelingHandler}) that attaches the nozzle model directly to the player model's
     * own right arm bone -- always the right arm, regardless of the player's configured main
     * hand -- so it automatically tracks real arm swing/animation. A Paper plugin has no way to
     * attach a prop to a vanilla player's bones, and routing it through a real off-hand item
     * (tried in an earlier revision of this method) was rejected: it visibly occupied the
     * player's own inventory/off-hand slot, which reads as a bug rather than a cosmetic effect.
     * So this instead positions this rig's own prop by hand, at the exact same point the
     * original's {@code GasPumpRenderer#getNozzlePosition} (non-first-person branch) computes
     * for the hose's own terminal point -- {@code (-0.35 * handSide, -0.025, -0.025)} rotated
     * by {@code -bodyYaw}, where {@code handSide} is {@code +1} for the player's actual main
     * hand being right and {@code -1} for left -- rather than any further-tuned offset: a
     * previous revision pushed this point out further per in-game feedback that the literal
     * source value read as glued to the player's hip, but per direct instruction this reverts
     * that tuning to keep hose and prop alike at the exact position the original mod uses.
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
     * player's own local space (i.e. before being rotated into world space by {@code -bodyYaw}).
     * {@code handSide} is {@code +1} when {@code mainHand} is the player's actual configured
     * main hand being right, {@code -1} for left -- exactly like the original's own {@code
     * player.getMainArm() == HandSide.RIGHT ? 1 : -1} -- so, unlike an earlier revision of this
     * rig that both hardcoded the right-hand offset (ignoring this method's own {@code mainHand}
     * parameter) and pushed the offset out further per visual feedback, this now reproduces the
     * original's exact {@code (-0.35 * handSide, -0.025, -0.025)} literal values. (The slim-skin
     * nudge the original also applies here is skipped as a disclosed simplification -- see this
     * class's own top-level javadoc.)
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

    /**
     * The translation that cancels an {@code ItemDisplay}'s forced center pivot for a prop
     * that should stay anchored at its own model-space origin -- i.e. so {@code modelPos =
     * (0, 0, 0)} (the point the original's {@code matrixStack.translate(...)} call placed at
     * this entity's own position) renders with zero offset from that position, exactly like
     * {@link #hoseSegmentPivotCompensation} does for one hose segment. That method is the
     * {@code rightRotation = identity}, {@code scale = (1, 1, length)} special case of this
     * same derivation; this is the general form, kept generic over {@code rightRotation} even
     * though every current call site (nozzle included, since {@link #place} now passes its
     * {@code rightRotation} straight through unchanged) happens to use identity -- a lopsided
     * prop like the nozzle still needs this general form rather than the body's shortcut,
     * because its {@code leftRotation} (facing/hold orientation) is non-identity and its model
     * does not span the whole unit cube the way the pump body's does (whose {@code part} calls
     * get away with the constant {@code (-0.5, 0, -0.5)} precisely because their
     * {@code leftRotation} is identity and their model spans the whole unit cube).
     *
     * <p>Solving {@code center + leftRotation.transform(scale * rightRotation.transform(modelPos
     * - center)) + translation = 0} for {@code modelPos = (0, 0, 0)} gives {@code translation =
     * leftRotation.transform(scale * rightRotation.transform(center)) - center}.
     */
    static Vector3f pivotCompensation(Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {
        Vector3f center = new Vector3f(0.5F, 0.5F, 0.5F);
        Vector3f rotatedCenter = rightRotation.transform(new Vector3f(center));
        // Component-wise scale, spelled out rather than calling a Vector3f#mul(Vector3f)
        // overload: see hoseSegmentPivotCompensation's own test for why this file avoids
        // reaching for a JOML overload with no other already-compiling call site in this repo.
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
     * Places {@code display} at {@code anchor} with the given {@code Transformation}. A
     * previous revision had this unconditionally bake an extra {@code rotateY(180)} onto
     * {@code sourceRightRotation} here, justified only as "the compensation every {@code place}
     * call needs" -- asserted, never independently derived against the original. It was wrong:
     * the original's {@code GasPumpRenderer#render} renders the nozzle with the single literal
     * matrix-stack sequence {@code translate(pos) -> rotateY(facing) -> rotateY(180) ->
     * rotateX(90) -> scale(0.8) -> render}, and that one {@code rotateY(180)} is already fully
     * accounted for in {@code leftRotation} (see {@code nozzleRestRotation}/{@code
     * nozzleHandRotation}, both of which chain exactly that three-rotation sequence). Baking a
     * second one on as {@code rightRotation} here flipped the rendered nozzle's orientation by
     * an extra 180 degrees on top of the correct one. {@code sourceRightRotation} is passed
     * straight through unchanged, matching how {@link #part} and {@link #placeSegment} already
     * use an identity right rotation.
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
     *  west = 270). See {@link #bodyRotation} for how this is actually turned into the
     *  rotation the body parts render with. */
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
     * facing}, replacing an earlier revision that instead called the display entity's own
     * {@code setRotation(entityYaw, 0)} with {@link #blockstateYDegrees}'s value directly.
     *
     * <p>That earlier approach was never actually verifiable: Mojang's own documentation of
     * how a display entity's base yaw composes with its {@code Transformation} does not pin
     * down the composition order or the rotation's sign convention precisely enough to confirm
     * from outside a running client, and this rig had no test exercising it -- in-game testing
     * reported the body spawning shifted into a corner of its own block instead of rotated
     * cleanly in place, consistent with that mechanism not doing what the old table assumed.
     *
     * <p>Every other rotation in this class (the nozzle's rest/held orientation, every hose
     * segment) already avoids this problem entirely by baking its whole rotation into the
     * {@code Transformation} instead of the entity's own yaw -- this does the same for the
     * body, using this class's own {@link #yRot} (a direct, already-verified port of vanilla's
     * {@code Vector3d#yRot}) to confirm the exact angle needed: applying {@code yRot(point,
     * -D)} to a model point reproduces precisely what a real block's own {@code "y": D}
     * blockstate rotation does to that point. (Checked against vanilla's own directional
     * blocks: a furnace's {@code "facing=east"} variant uses {@code "y": 90}, and {@code
     * yRot((0, 0, -1), -90°)} -- the unrotated model's own north-pointing front -- lands
     * exactly on {@code (1, 0, 0)}, i.e. east, matching.)
     *
     * <p>The extra "+180 universal item-display compensation" a previous revision composed on
     * top of {@code rotateY(-D)} here (matching a since-removed {@code place}-forced 180-degree
     * right rotation that turned out to be its own separate, equally unverified bug -- see
     * {@link #place}'s own javadoc) turned out to be wrong for the body specifically, and the
     * self-test guarding it never could have caught that: it only checked
     * this method against its own {@code -D} + 180 formula, so it verified internal arithmetic,
     * not which formula is actually correct. The real check is independent of this file: {@code
     * gas_pump_top.json}'s own elements 4-5 model the pump's nozzle-holder bracket sticking out
     * past the model's +X edge, and the hose/nozzle rest position for the same facing is
     * computed completely separately, via {@link #fixRotation} (a verbatim port of the
     * original's own {@code CollisionHelper#fixRotation}, used by the original's real renderer
     * for exactly this purpose). Those two numbers have no shared code path, so for a correct
     * body rotation they must land next to each other -- a nozzle doesn't rest 1+ blocks from
     * its own holder. With the extra 180 included, the bracket (rotated with the body) and the
     * {@code fixRotation}-computed rest point end up {@code 1.11} blocks apart, identically for
     * all four facings (so it never looked "only" broken for one orientation); dropping it to
     * plain {@code rotateY(-D)} brings that down to a steady {@code 0.34} blocks for all four --
     * consistent with the nozzle hanging just beside, not exactly inside, its holder.
     *
     * <p>So the body's rotation is exactly {@code rotateY(-D)}, with no extra composition.
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
