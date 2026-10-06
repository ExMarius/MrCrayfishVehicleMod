package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.vehicle.GoKartProperties;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Vanilla display-entity implementation of GoKartRenderer,
 * AbstractLandVehicleRenderer, AbstractPoweredRenderer, and
 * AbstractVehicleRenderer. The transform order and constants mirror those
 * renderers; only the final conversion to ItemDisplay coordinates is new.
 */
public final class GoKartRig {
    public static final String ENTITY_TAG = "mcv_plugin_vehicle";

    private static final Vector3f BODY_ORIGIN = new Vector3f(0.0F, GoKartProperties.BODY_RENDER_Y, 0.0F);
    private static final Vector3f WHEELIE_PIVOT = new Vector3f(
            0.0F,
            GoKartProperties.BODY_RENDER_Y - 0.5F - GoKartProperties.AXLE_OFFSET * GoKartProperties.MODEL_UNIT,
            GoKartProperties.REAR_AXLE_OFFSET
    );
    private static final Vector3f ENGINE_CENTER = new Vector3f(0.0F, 0.6F, -11.0F / 16.0F);
    private static final Vector3f STEERING_CENTER = new Vector3f(
            0.0F,
            GoKartProperties.BODY_RENDER_Y + 0.6814F / 16.0F,
            8.0426F / 16.0F
    );

    /* Seat (-3) + axle (-1) + wheel offset (3.2), converted from model units. */
    private static final Vector3f SEAT_OFFSET = new Vector3f(0.0F, -0.05F, -1.0F / 16.0F);
    /* Vanilla non-player entities interpolate teleports over three client ticks. */
    private static final int VANILLA_ENTITY_LERP_TICKS = 3;

    private final UUID vehicleId;
    private final Interaction interaction;
    private final ItemDisplay body;
    private final ItemDisplay engine;
    private final ItemDisplay steeringWheel;
    private final List<WheelDisplay> wheels;
    private final List<Entity> entities;

    private GoKartRig(UUID vehicleId, Interaction interaction, ItemDisplay body,
                      ItemDisplay engine, ItemDisplay steeringWheel, List<WheelDisplay> wheels,
                      List<Entity> entities) {
        this.vehicleId = vehicleId;
        this.interaction = interaction;
        this.body = body;
        this.engine = engine;
        this.steeringWheel = steeringWheel;
        this.wheels = wheels;
        this.entities = entities;
    }

    public static GoKartRig spawn(VehiclePlugin plugin, UUID vehicleId, Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Cannot spawn a vehicle without a world");
        }

        List<Entity> all = new ArrayList<>();
        Interaction interaction = world.spawn(location, Interaction.class, hitbox -> {
            /* The original entity is 1.5 x 0.5; the wider interaction covers its long rendered body. */
            hitbox.setInteractionWidth(2.2F);
            hitbox.setInteractionHeight(1.1F);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
        });
        mark(interaction, vehicleId);
        all.add(interaction);

        ItemDisplay body = display(world, location, model("go_kart_body"));
        mark(body, vehicleId);
        all.add(body);

        ItemDisplay engine = display(world, location, model("iron_small_engine"));
        mark(engine, vehicleId);
        all.add(engine);

        ItemDisplay steering = display(world, location, model("go_kart_steering_wheel"));
        mark(steering, vehicleId);
        all.add(steering);

        List<WheelDisplay> wheels = new ArrayList<>();
        for (GoKartProperties.Wheel properties : GoKartProperties.WHEELS) {
            ItemDisplay wheel = display(world, location, model("standard_wheel"));
            mark(wheel, vehicleId);
            all.add(wheel);
            wheels.add(new WheelDisplay(wheel, properties));
        }

        GoKartRig rig = new GoKartRig(vehicleId, interaction, body, engine, steering,
                Collections.unmodifiableList(wheels), Collections.unmodifiableList(all));
        rig.update(location, 0.0F, 0.0F, 0.0F, 0.0F, false, 0);
        return rig;
    }

    private static ItemDisplay display(World world, Location location, ItemStack stack) {
        return world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            /* Use the vanilla three-tick position interpolation for the complete multipart rig. */
            display.setTeleportDuration(VANILLA_ENTITY_LERP_TICKS);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setShadowRadius(0.0F);
            display.setShadowStrength(0.0F);
        });
    }

    private static ItemStack model(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", name));
        item.setItemMeta(meta);
        return item;
    }

    private static void mark(Entity entity, UUID vehicleId) {
        entity.addScoreboardTag(ENTITY_TAG);
        entity.addScoreboardTag("mcv_" + vehicleId);
    }

    public void update(Location root, float renderSteeringAngle, float frontWheelRotation,
                       float rearWheelRotation, float wheelieAngle, boolean engineRunning,
                       int tickCount) {
        if (!valid()) {
            return;
        }

        float yaw = root.getYaw();
        Quaternionf wheelieRotation = rotationX(wheelieAngle);
        Vector3f renderedSeat = wheelie(SEAT_OFFSET, wheelieAngle);
        /*
         * The body ItemDisplay is also the actual riding entity. A client therefore derives the
         * local player's position from precisely the same interpolated entity that renders the
         * chassis; no ArmorStand-vs-Display interpolation difference can open a gap at speed.
         * All other parts share this anchor and keep their offsets in their local matrices.
         */
        Location renderAnchor = local(root, renderedSeat);
        interaction.teleport(root);
        interaction.setRotation(yaw, 0.0F);
        updateBrightness(root);

        Vector3f bodyCenter = wheelie(BODY_ORIGIN, wheelieAngle);
        place(body, renderAnchor, relativeToSeat(bodyCenter, renderedSeat), yaw, wheelieRotation,
                new Vector3f(1.0F), new Quaternionf());

        Quaternionf engineShake = new Quaternionf();
        if (engineRunning && (tickCount & 1) == 1) {
            engineShake.rotateX(radians(0.5F)).rotateZ(radians(0.5F)).rotateY(radians(-0.5F));
        }
        Vector3f engineRelative = new Vector3f(ENGINE_CENTER).sub(BODY_ORIGIN);
        engineShake.transform(engineRelative);
        Vector3f engineCenter = wheelie(new Vector3f(BODY_ORIGIN).add(engineRelative), wheelieAngle);
        Quaternionf engineRotation = new Quaternionf(wheelieRotation).mul(engineShake);
        place(engine, renderAnchor, relativeToSeat(engineCenter, renderedSeat), yaw, engineRotation,
                new Vector3f(0.8F), new Quaternionf().rotateY((float) Math.PI));

        Vector3f steeringCenter = wheelie(STEERING_CENTER, wheelieAngle);
        float steeringWheelRotation = renderSteeringAngle / GoKartProperties.MAX_STEERING_ANGLE * 25.0F;
        Quaternionf steeringRotation = new Quaternionf(wheelieRotation)
                .rotateX(radians(-45.0F))
                .rotateY(radians(steeringWheelRotation));
        place(steeringWheel, renderAnchor, relativeToSeat(steeringCenter, renderedSeat), yaw, steeringRotation,
                new Vector3f(1.0F), new Quaternionf());

        for (WheelDisplay wheelDisplay : wheels) {
            GoKartProperties.Wheel properties = wheelDisplay.properties;
            float steering = properties.front() ? renderSteeringAngle : 0.0F;
            float spin = properties.front() ? frontWheelRotation : rearWheelRotation;

            float width = properties.halfWidthOffset();
            float steerRadians = radians(steering);
            Vector3f wheelCenter = new Vector3f(
                    properties.axleX() + width * (float) Math.cos(steerRadians),
                    properties.centerY(),
                    properties.axleZ() - width * (float) Math.sin(steerRadians)
            );
            wheelCenter = wheelie(wheelCenter, wheelieAngle);

            Quaternionf wheelRotation = new Quaternionf(wheelieRotation)
                    .rotateY(steerRadians)
                    .rotateX(radians(-spin));
            Quaternionf sideRotation = properties.side() > 0
                    ? new Quaternionf().rotateY((float) Math.PI)
                    : new Quaternionf();
            place(wheelDisplay.entity, renderAnchor, relativeToSeat(wheelCenter, renderedSeat), yaw, wheelRotation,
                    new Vector3f(properties.scaleX(), properties.scaleY(), properties.scaleZ()),
                    sideRotation);
        }
    }

    private static void place(ItemDisplay display, Location renderAnchor, Vector3f translation, float yaw,
                              Quaternionf leftRotation, Vector3f scale, Quaternionf sourceRightRotation) {
        display.teleport(renderAnchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        display.setRotation(yaw, 0.0F);
        /*
         * Since 23w16a, vanilla ItemDisplayRenderer injects a 180-degree Y rotation around the
         * item-model centre. The Forge renderer does not. Post-multiply the inverse half-turn so
         * the resulting matrix is the exact source MatrixStack matrix without moving the part.
         */
        Quaternionf itemDisplayCompensation = new Quaternionf(sourceRightRotation).rotateY((float) Math.PI);
        display.setTransformation(new Transformation(
                translation, leftRotation, scale, itemDisplayCompensation
        ));
    }

    private void updateBrightness(Location root) {
        /*
         * Every display is anchored at the low seat point, which can lie just inside the road's
         * collision block. Sample light above the original 0.5-block entity instead; this is the
         * same open space occupied by the rendered kart and prevents false pitch-black shading.
         */
        Block lightSample = root.clone().add(0.0D, GoKartProperties.ENTITY_HEIGHT + 0.01D, 0.0D).getBlock();
        Display.Brightness brightness = new Display.Brightness(
                lightSample.getLightFromBlocks(), lightSample.getLightFromSky()
        );
        setBrightness(body, brightness);
        setBrightness(engine, brightness);
        setBrightness(steeringWheel, brightness);
        for (WheelDisplay wheel : wheels) {
            setBrightness(wheel.entity, brightness);
        }
    }

    private static void setBrightness(ItemDisplay display, Display.Brightness brightness) {
        if (!brightness.equals(display.getBrightness())) {
            display.setBrightness(brightness);
        }
    }

    private static Vector3f relativeToSeat(Vector3f vehiclePoint, Vector3f renderedSeat) {
        return new Vector3f(vehiclePoint).sub(renderedSeat);
    }

    private static Vector3f wheelie(Vector3f point, float angle) {
        if (angle == 0.0F) {
            return new Vector3f(point);
        }
        Vector3f relative = new Vector3f(point).sub(WHEELIE_PIVOT);
        rotationX(angle).transform(relative);
        return relative.add(WHEELIE_PIVOT);
    }

    private static Quaternionf rotationX(float degrees) {
        return new Quaternionf().rotateX(radians(degrees));
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static Location local(Location root, Vector3f point) {
        double radians = Math.toRadians(root.getYaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double worldX = point.x * cos - point.z * sin;
        double worldZ = point.x * sin + point.z * cos;
        return root.clone().add(worldX, point.y, worldZ);
    }

    /** The chassis display is the seat carrier, guaranteeing one client interpolation path. */
    public ItemDisplay seat() {
        return body;
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public List<Entity> entities() {
        return entities;
    }

    public boolean valid() {
        return interaction.isValid() && body.isValid() && engine.isValid()
                && steeringWheel.isValid() && wheels.stream().allMatch(wheel -> wheel.entity.isValid());
    }

    public void remove() {
        for (Entity entity : entities) {
            entity.remove();
        }
    }

    private record WheelDisplay(ItemDisplay entity, GoKartProperties.Wheel properties) {
    }
}
