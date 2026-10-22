package com.mrcrayfish.vehicle.paper.gaspump;

// ============================================================================
// [NU SCHIMBA - VERSIUNE INGHETATA V2, VALIDATA DE UTILIZATOR 2026-10-18, commit e5b122f]
// Pompa docata: PERFECTA (v1). Pistol flutant ținut: rotație pe privire, furtunul
// intră 0.22 în corp, ultimele 2 segmente ascunse doar pentru holder (FP curat).
// Limită cunoscută și ACCEPTATĂ: unirea furtun-pistol nu e pixel-perfect.
// + LOGISTICĂ (acord explicit 2026-10-18, după înghețarea V2): click pe vehicul cu
//   duza ținută NU mai urcă în vehicul (doar alimentează); maxim 1 duză per jucător.
// Orice schimbare în acest pachet cere acordul EXPLICIT al utilizatorului.
// Restaurare instant: git checkout gaspump-perfect-v2 -- paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/gaspump/
// Vezi paper-plugin/SNAPSHOT_GAS_PUMP_OK.md pentru constantele exacte.
// ============================================================================

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
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
     *  Restored to the long-tuned value: the hose must stay on the pump's left side
     *  (user feedback 2026-10-18: moving it right was wrong). */
    private static final Vector3f HOSE_OUTLET_LOCAL = new Vector3f(-0.575F, 1.64F, 0.1125F);
    /** Local offset where the nozzle rests when nobody is holding it.
     *  Source puts it on the pump's positive side (flipped correctly from the old -0.5);
     *  nudged +5 model pixels (5/16 = 0.3125) further out toward the pump's own left so the
     *  dock sits on the body edge instead of clipping inside the pump (user-reported). */
    private static final Vector3f NOZZLE_REST_LOCAL = new Vector3f(0.5525F, 1.5F, 0.18F);
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

    /**
     * Extra hose segments the HOLDER alone sees: the shared chain is anchored at the body's
     * hand (what bystanders and F5 render), while these last two segments replace its very end
     * on the holder's screen only and stick exactly into the nozzle item in first person.
     */
    /**
     * How many shared-chain segments (counted from the hand end) are hidden from the HOLDER.
     * First person should show the nozzle item cleanly in view; hose segments parked right in
     * front of the player's own face only clutter the screen (holder feedback 2026-10-18:
     * "in FP want to see the pistol, not the hose"). Bystanders keep seeing the full chain.
     */
    static final int HOLDER_HIDDEN_SEGMENTS = 2;

    /** How deep (blocks, along the view facing) the hose tip is pushed into the held nozzle
     *  body, so the hose plugs into the pistol's CENTER instead of dangling at its edge. */
    private static final float NOZZLE_CONNECT_DEPTH = 0.22F;

    /** Unused spare displays, spawned hidden and parked at the pump root forever. */
    static final int HOLDER_TAIL_SEGMENTS = 2;

    private final List<ItemDisplay> holderTail;
    private UUID tailViewerId;

    private GasPumpRig(UUID pumpId, Interaction interaction, ItemDisplay bottom, ItemDisplay top,
                       ItemDisplay nozzle, List<ItemDisplay> hose, List<ItemDisplay> holderTail,
                       List<Entity> entities) {
        this.pumpId = pumpId;
        this.interaction = interaction;
        this.bottom = bottom;
        this.top = top;
        this.nozzle = nozzle;
        this.hose = hose;
        this.holderTail = holderTail;
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

        // Holder-only tail: born hidden from everybody, shown to whoever holds the nozzle.
        List<ItemDisplay> holderTail = new ArrayList<>(HOLDER_TAIL_SEGMENTS);
        for (int i = 0; i < HOLDER_TAIL_SEGMENTS; i++) {
            ItemDisplay segment = display(world, root, model("hose_segment"));
            segment.setVisibleByDefault(false);
            mark(segment, pumpId);
            all.add(segment);
            holderTail.add(segment);
        }

        GasPumpRig rig = new GasPumpRig(pumpId, interaction, bottom, top, nozzle,
                Collections.unmodifiableList(hose), Collections.unmodifiableList(holderTail), all);
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
        update(root, nozzleLocation, lookDirection, null);
    }

    /**
     * @param holder the player holding the nozzle (their view yaw drives the floating nozzle),
     *               or {@code null} when it is docked / nobody is holding it.
     */
    public void update(Location root, Location nozzleLocation, Vector lookDirection, Player holder) {
        if (!valid()) {
            return;
        }
        World world = root.getWorld();
        float yaw = root.getYaw();

        interaction.teleport(root);
        interaction.setRotation(yaw, 0.0F);

        place(bottom, root, new Vector3f(0.0F, 0.5F, 0.0F));
        place(top, root, new Vector3f(0.0F, 1.5F, 0.0F));

        // Hose outlet anchor must rotate WITH the pump body (Bukkit yaw rotation), otherwise
        // re-placing the pump at a different yaw leaves the hose pouring out of the wrong side.
        float bodyRad = (float) Math.toRadians(yaw);
        double bodyCos = Math.cos(bodyRad), bodySin = Math.sin(bodyRad);
        Vector3f outlet = new Vector3f(
                (float) (root.getX() + HOSE_OUTLET_LOCAL.x * bodyCos - HOSE_OUTLET_LOCAL.z * bodySin),
                (float) root.getY() + HOSE_OUTLET_LOCAL.y,
                (float) (root.getZ() + HOSE_OUTLET_LOCAL.x * bodySin + HOSE_OUTLET_LOCAL.z * bodyCos));
        Vector3f nozzlePoint;
        Vector3f endTangent;
        if (nozzleLocation != null) {
            nozzlePoint = new Vector3f((float) nozzleLocation.getX(), (float) nozzleLocation.getY(),
                    (float) nozzleLocation.getZ());
            // Floating nozzle: rides the holder's view yaw (no extra offset - the 180 deg
            // pump-dock offset used to show its face backwards). The display itself sits at
            // the hand point, while the hose chain tip is pushed INTO the middle of the nozzle
            // body (along the same facing), so the hose plugs into the pistol's center and the
            // junction never visually splits.
            float holdYaw = holder != null ? holder.getEyeLocation().getYaw() : yaw;
            double holdRad = Math.toRadians(holdYaw);
            double fwx = -Math.sin(holdRad), fwz = Math.cos(holdRad);
            nozzlePoint.add((float) (fwx * NOZZLE_CONNECT_DEPTH), 0.0F, (float) (fwz * NOZZLE_CONNECT_DEPTH));
            nozzle.teleport(new Location(world, nozzleLocation.getX(), nozzleLocation.getY(), nozzleLocation.getZ()));
            nozzle.setRotation(holdYaw, 0.0F);
            nozzle.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(1.0F, 1.0F, 1.0F), new Quaternionf()));
            endTangent = lookDirection == null ? new Vector3f(0.0F, 3.0F, 0.0F)
                    : new Vector3f((float) lookDirection.getX(), (float) lookDirection.getY(),
                            (float) lookDirection.getZ()).mul(3.0F);
        } else {
            // Paper rotates the ItemDisplay transformation translation by the entity yaw
            // (setRotation). place() uses yaw + NOZZLE_YAW_OFFSET, so the visual dock point is
            // root + rotY(yaw + NOZZLE_YAW_OFFSET) * NOZZLE_REST_LOCAL (Bukkit yaw convention:
            // x' = x cos - z sin, z' = x sin + z cos). The hose tail must target exactly that
            // same point or it cuts through the pump.
            place(nozzle, root, NOZZLE_REST_LOCAL, NOZZLE_YAW_OFFSET);
            float dockYaw = (float) Math.toRadians(yaw + NOZZLE_YAW_OFFSET);
            double cos = Math.cos(dockYaw), sin = Math.sin(dockYaw);
            nozzlePoint = new Vector3f(
                    (float) (root.getX() + NOZZLE_REST_LOCAL.x * cos - NOZZLE_REST_LOCAL.z * sin),
                    (float) root.getY() + NOZZLE_REST_LOCAL.y,
                    (float) (root.getZ() + NOZZLE_REST_LOCAL.x * sin + NOZZLE_REST_LOCAL.z * cos));
            endTangent = new Vector3f(0.0F, 3.0F, 0.0F);
        }

        Vector3f[] points = HermiteCurve.sample(outlet, new Vector3f(0.0F, -5.0F, 0.0F),
                nozzlePoint, endTangent, HOSE_SEGMENTS);
        for (int i = 0; i < HOSE_SEGMENTS; i++) {
            placeHoseSegment(hose.get(i), points[i], points[i + 1], world);
        }

        // Unused spare tail segments stay parked at the root and strictly hidden from everyone.
        Vector3f rootPoint = new Vector3f((float) root.getX(), (float) root.getY(), (float) root.getZ());
        for (int i = 0; i < HOLDER_TAIL_SEGMENTS; i++) {
            placeHoseSegment(holderTail.get(i), rootPoint, rootPoint, world);
        }

        updateBrightness(root);
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
        // Instant teleports: the default lerping window made the hose visibly trail behind
        // the moving hand (the earlier display()-level 0 was being overwritten back to 3 here).
        display.setTeleportDuration(0);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
    }

    private static ItemStack model(String name) {
        // PAPER + item_model nu mai randeaza pe server (regresie live). Unificat cu
        // LandVehicleRig+coloredStack: LEATHER_HORSE_ARMOR + alb explicit (dyed_color
        // pe stack => tint opac pe orice client, aspect = albul din backup).
        ItemStack item = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", name));
        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta armor) {
            armor.setColor(org.bukkit.Color.fromRGB(0xF9FFFE));
        }
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

    /** The display+interaction entities die whenever their chunk unloads (persistent=false);
     *  false means the manager must respawn the rig before the pump looks like a bare block. */
    public boolean alive() {
        Interaction interaction = interaction();
        return interaction != null && interaction.isValid();
    }

    public Interaction interaction() {
        return interaction;
    }

    public List<Entity> entities() {
        return Collections.unmodifiableList(entities);
    }

    public boolean valid() {
        return interaction.isValid() && bottom.isValid() && top.isValid() && nozzle.isValid()
                && hose.stream().allMatch(Entity::isValid)
                && holderTail.stream().allMatch(Entity::isValid);
    }

    /** Hand-end shared-chain segments, hidden from the holder while they hold the nozzle. */
    public List<ItemDisplay> holderCoveredTail() {
        return hose.subList(HOSE_SEGMENTS - HOLDER_HIDDEN_SEGMENTS, HOSE_SEGMENTS);
    }

    /** Spare segments kept parked and strictly invisible (legacy of the FP tail experiment). */
    public List<ItemDisplay> holderTail() {
        return holderTail;
    }

    public UUID tailViewerId() {
        return tailViewerId;
    }

    public void setTailViewerId(UUID tailViewerId) {
        this.tailViewerId = tailViewerId;
    }

    public void remove() {
        entities.forEach(Entity::remove);
    }
}
