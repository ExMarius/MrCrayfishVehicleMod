package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
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

/** Vanilla display-entity replacement for GoKartRenderer. */
public final class GoKartRig {
    public static final String ENTITY_TAG = "mcv_plugin_vehicle";

    private final UUID vehicleId;
    private final ArmorStand seat;
    private final Interaction interaction;
    private final ItemDisplay body;
    private final ItemDisplay engine;
    private final ItemDisplay steeringWheel;
    private final List<WheelDisplay> wheels;
    private final List<Entity> entities;
    private float wheelRotation;

    private GoKartRig(UUID vehicleId, ArmorStand seat, Interaction interaction, ItemDisplay body,
                      ItemDisplay engine, ItemDisplay steeringWheel, List<WheelDisplay> wheels,
                      List<Entity> entities) {
        this.vehicleId = vehicleId;
        this.seat = seat;
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
        ArmorStand seat = world.spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setSilent(true);
        });
        mark(seat, vehicleId);
        all.add(seat);

        Interaction interaction = world.spawn(location, Interaction.class, hitbox -> {
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
        wheels.add(wheel(world, location, -0.50F, 0.20F, 0.55F, true, vehicleId, all));
        wheels.add(wheel(world, location, 0.50F, 0.20F, 0.55F, true, vehicleId, all));
        wheels.add(wheel(world, location, -0.50F, 0.22F, -0.59F, false, vehicleId, all));
        wheels.add(wheel(world, location, 0.50F, 0.22F, -0.59F, false, vehicleId, all));

        GoKartRig rig = new GoKartRig(vehicleId, seat, interaction, body, engine, steering,
                Collections.unmodifiableList(wheels), Collections.unmodifiableList(all));
        rig.update(location, 0.0F, 0.0F);
        return rig;
    }

    private static WheelDisplay wheel(World world, Location origin, float x, float y, float z,
                                      boolean steering, UUID vehicleId, List<Entity> all) {
        ItemDisplay display = display(world, origin, model("standard_wheel"));
        mark(display, vehicleId);
        all.add(display);
        return new WheelDisplay(display, new Vector3f(x, y, z), steering);
    }

    private static ItemDisplay display(World world, Location location, ItemStack stack) {
        return world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(1);
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

    public void update(Location root, float steeringAngle, float speed) {
        if (!valid()) {
            return;
        }
        float yaw = root.getYaw();
        seat.teleport(local(root, 0.0F, 0.05F, -0.06F));
        seat.setRotation(yaw, 0.0F);
        interaction.teleport(local(root, 0.0F, 0.45F, 0.0F));
        interaction.setRotation(yaw, 0.0F);

        body.teleport(root);
        body.setRotation(yaw, 0.0F);
        body.setTransformation(transform(
                new Vector3f(0.0F, 0.30F, 0.0F),
                new Quaternionf(),
                new Vector3f(1.0F, 1.0F, 1.0F)
        ));

        Location engineLocation = local(root, 0.0F, 0.36F, -0.69F);
        engine.teleport(engineLocation);
        engine.setRotation(yaw + 180.0F, 0.0F);
        engine.setTransformation(transform(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(0.8F)
        ));

        Location steeringLocation = local(root, 0.0F, 0.68F, 0.50F);
        steeringWheel.teleport(steeringLocation);
        steeringWheel.setRotation(yaw, 0.0F);
        steeringWheel.setTransformation(transform(
                new Vector3f(),
                new Quaternionf().rotateX((float) Math.toRadians(-45.0F))
                        .rotateZ((float) Math.toRadians(-steeringAngle * 0.625F)),
                new Vector3f(0.9F)
        ));

        wheelRotation -= speed * 20.0F;
        for (WheelDisplay wheel : wheels) {
            Location wheelLocation = local(root, wheel.offset.x, wheel.offset.y, wheel.offset.z);
            wheel.entity.teleport(wheelLocation);
            wheel.entity.setRotation(yaw, 0.0F);
            float steer = wheel.steering ? steeringAngle : 0.0F;
            Quaternionf rotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(-steer))
                    .rotateX((float) Math.toRadians(wheelRotation));
            if (wheel.offset.x > 0.0F) {
                rotation.rotateY((float) Math.PI);
            }
            wheel.entity.setTransformation(transform(new Vector3f(), rotation,
                    new Vector3f(1.0F, 0.8F, 0.8F)));
        }
    }

    private static Transformation transform(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        return new Transformation(translation, rotation, scale, new Quaternionf());
    }

    private static Location local(Location root, float x, float y, float z) {
        double radians = Math.toRadians(root.getYaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double worldX = x * cos - z * sin;
        double worldZ = x * sin + z * cos;
        return root.clone().add(worldX, y, worldZ);
    }

    public ArmorStand seat() {
        return seat;
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public List<Entity> entities() {
        return entities;
    }

    public boolean valid() {
        return seat.isValid() && interaction.isValid() && body.isValid() && engine.isValid();
    }

    public void remove() {
        for (Entity entity : entities) {
            entity.remove();
        }
    }

    private record WheelDisplay(ItemDisplay entity, Vector3f offset, boolean steering) {
    }
}
