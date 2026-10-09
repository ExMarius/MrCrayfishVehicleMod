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
    /** The effective right rotation every {@link #place} call renders with, since it always
     *  bakes an extra 180-degree yaw onto whatever {@code sourceRightRotation} it's given (see
     *  {@link #place}'s own body) and both of this rig's {@code place} call sites for the
     *  nozzle pass an identity {@code sourceRightRotation}. Needed here too, separately, so
     *  {@link #pivotCompensation} can be fed the rotation it actually has to cancel out. */
    private static final Quaternionf PLACE_RIGHT_ROTATION = new Quaternionf().rotateY((float) Math.PI);

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
        Quaternionf rotation = bodyRotation(facing);

        List<Entity> all = new ArrayList<>();

        // The original mod's two pump halves are ordinary blocks, not displays: the bottom
        // block occupies exactly [x, x+1] x [y, y+1] x [z, z+1] and the top block the one
        // directly above it, with zero extra vertical offset -- see GasPumpTileEntity/
        // GasPumpBlock (a vanilla two-tall block pair) and GasPumpManager#createPump, which
        // stores the *bottom* block's own coordinates as the pump's position. An earlier
        // revision of this rig added a fabricated "VERTICAL_OFFSET = 0.5" here (justified only
        // as "per in-game testing feedback"), floating the whole rig half a block above the
        // registered position -- removed per direct instruction to stop tuning by guesswork
        // and instead match the original's real, flush block placement exactly.
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

        // All offsets below are the original renderer's fixRotation() outputs, measured from the
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
            Vector3f translation = pivotCompensation(nozzleRestRotation, NOZZLE_SCALE, PLACE_RIGHT_ROTATION);
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
        Vector3f translation = pivotCompensation(nozzleHandRotation, NOZZLE_SCALE, PLACE_RIGHT_ROTATION);
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
     * {@code rightRotation = identity} special case of this same derivation; this is the
     * general form, needed for the nozzle prop since unlike a hose segment it has both a real
     * (non-identity) {@code leftRotation} (its facing/hold orientation) <em>and</em> a
     * {@code rightRotation} (the 180-degree compensation every {@link #place} call bakes in --
     * see {@link #PLACE_RIGHT_ROTATION}), neither of which commutes away for a lopsided prop
     * the way they harmlessly do for a full, symmetric cube like the pump's own body (whose
     * {@code part} calls get away with the constant {@code (-0.5, 0, -0.5)} precisely because
     * their {@code leftRotation} is identity and their model spans the whole unit cube).
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
     * <p>So the body's combined rotation is exactly {@code rotateY(-D)}, composed with this
     * rig's universal 180-degree item-display compensation (see this class's own top-level
     * javadoc and {@link #PLACE_RIGHT_ROTATION}) -- both pure Y-axis rotations, which always
     * commute, so the two collapse into the single {@code rotateY(180 - D)} below with no
     * separate left/right split needed.
     */
    static Quaternionf bodyRotation(BlockFace facing) {
        float degrees = 180.0F - blockstateYDegrees(facing);
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
