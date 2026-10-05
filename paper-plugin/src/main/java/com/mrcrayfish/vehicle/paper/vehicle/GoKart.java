package com.mrcrayfish.vehicle.paper.vehicle;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.physics.SurfaceProfile;
import com.mrcrayfish.vehicle.paper.render.GoKartRig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side Go Kart state and physics. The force, drag, friction, traction,
 * axle and steering equations are adapted from LandVehicleEntity.
 */
public final class GoKart {
    public static final float ENGINE_POWER = 18.0F;
    public static final float MAX_STEERING_ANGLE = 20.0F;
    public static final float ENERGY_CAPACITY = 15_000.0F;
    public static final float ENERGY_PER_TICK = 0.5F;
    public static final float FRONT_AXLE_OFFSET = 9.0F / 16.0F;
    public static final float REAR_AXLE_OFFSET = -9.5F / 16.0F;
    private static final float MAX_REVERSE_SPEED = 5.0F;
    private static final float STANDARD_TRACTION = 0.8F;
    private static final float SLIDE_TRACTION = 0.05F;
    private static final float DRAG = 0.001F;

    private final VehiclePlugin plugin;
    private final UUID id;
    private final GoKartRig rig;
    private Location location;
    private Vector velocity = new Vector();
    private float steeringAngle;
    private float traction = STANDARD_TRACTION;
    private float throttle;
    private float fuel = ENERGY_CAPACITY;
    private int age;

    private GoKart(VehiclePlugin plugin, UUID id, Location location, GoKartRig rig) {
        this.plugin = plugin;
        this.id = id;
        this.location = location;
        this.rig = rig;
    }

    public static GoKart spawn(VehiclePlugin plugin, UUID id, Location location) {
        Location root = location.clone();
        root.setPitch(0.0F);
        root.setYaw(normalizeYaw(root.getYaw()));
        root.setY(findSpawnY(root));
        GoKartRig rig = GoKartRig.spawn(plugin, id, root);
        return new GoKart(plugin, id, root, rig);
    }

    public void tick(double globalSpeedLimit, double fuelConsumptionFactor) {
        age++;
        Player driver = driver().orElse(null);
        VehicleInput input = driver == null ? VehicleInput.idle() : VehicleInput.from(driver.getCurrentInput());
        boolean enginePowered = driver != null
                && (driver.getGameMode() == GameMode.CREATIVE || fuel > 0.0F);
        throttle = enginePowered ? input.throttle() : 0.0F;

        float turnValue = input.steeringDirection();
        float steeringStrength = turnValue == 0.0F ? 0.2F : 0.05F;
        steeringAngle += (MAX_STEERING_ANGLE * turnValue - steeringAngle) * steeringStrength;
        if (driver == null) {
            throttle = 0.0F;
            steeringAngle *= 0.85F;
        }

        boolean handbrake = input.handbrake();
        SurfaceProfile surface = surfaceProfile();
        Vector forward = forward(location.getYaw());

        float forwardForce = handbrake ? 0.0F : ENGINE_POWER * clamp(throttle, -1.0F, 1.0F);
        if (throttle < 0.0F) {
            forwardForce *= 0.4F;
        }

        Vector acceleration = forward.clone().multiply(forwardForce * 0.05D);
        if (velocity.lengthSquared() < 0.0025D) {
            velocity.zero();
        }
        Vector handbrakeForce = velocity.clone().multiply((handbrake ? -1.0F : 0.0F) * 0.05D);
        Vector frictionForce = velocity.clone().multiply(-surface.friction() * 0.05D);
        Vector dragForce = velocity.clone().multiply(-velocity.length() * DRAG * 0.05D);
        velocity.add(acceleration).add(dragForce).add(frictionForce).add(handbrakeForce);
        clampLength(velocity, globalSpeedLimit);

        boolean sliding = isSliding(forward);
        if (sliding && throttle > 0.0F) {
            traction = SLIDE_TRACTION;
        } else if (handbrake) {
            traction = 0.05F;
        } else {
            float targetTraction = STANDARD_TRACTION;
            if (acceleration.lengthSquared() > 1.0E-8D) {
                targetTraction *= clamp((float) (velocity.length() / acceleration.length()), 0.0F, 1.0F);
            }
            float side = 1.0F;
            if (velocity.lengthSquared() > 1.0E-8D) {
                side = clamp(1.0F - (float) velocity.clone().normalize().crossProduct(forward.clone().normalize()).length() / 0.3F,
                        0.0F, 1.0F);
            }
            traction += (targetTraction - traction) * side * 0.15F;
        }

        Vector position = location.toVector();
        Vector worldFrontWheel = position.clone().add(forward.clone().multiply(FRONT_AXLE_OFFSET));
        Vector worldRearWheel = position.clone().add(forward.clone().multiply(REAR_AXLE_OFFSET));
        worldFrontWheel.add(rotateYLikeMinecraft(velocity, Math.toRadians(steeringAngle)).multiply(0.05D));
        worldRearWheel.add(velocity.clone().multiply(0.05D));

        Vector heading = worldFrontWheel.clone().subtract(worldRearWheel);
        if (heading.lengthSquared() < 1.0E-8D) {
            heading = forward;
        } else {
            heading.normalize();
        }
        Vector nextPosition = worldRearWheel.clone().add(heading.clone().multiply(-REAR_AXLE_OFFSET));
        Vector movement = nextPosition.subtract(position);

        float surfaceTraction = surface.tractionFactor() * traction;
        if (velocity.lengthSquared() > 1.0E-8D) {
            if (heading.dot(velocity.clone().normalize()) > 0.0D) {
                velocity = lerp(velocity, heading.clone().multiply(velocity.length()), surfaceTraction);
            } else {
                Vector reverse = heading.clone().multiply(-Math.min(velocity.length(), MAX_REVERSE_SPEED));
                velocity = lerp(velocity, reverse, surfaceTraction);
            }
        }

        float nextYaw = yaw(heading);
        Location target = location.clone().add(movement);
        target.setYaw(nextYaw);
        target.setPitch(0.0F);
        if (moveToGround(target)) {
            location = target;
        } else {
            velocity.multiply(0.15D);
        }

        double signedDirection = forward(location.getYaw()).dot(movement.clone().normalize());
        if (!Double.isFinite(signedDirection)) {
            signedDirection = 0.0D;
        }
        rig.update(location, steeringAngle, (float) (movement.length() * 16.67D * signedDirection));

        if (driver != null && driver.getGameMode() != GameMode.CREATIVE && Math.abs(throttle) > 0.0F) {
            fuel = Math.max(0.0F, fuel - (float) (ENERGY_PER_TICK * fuelConsumptionFactor));
        }
        effects(driver, movement);
    }

    private boolean moveToGround(Location target) {
        Double groundY = groundY(target);
        if (groundY == null) {
            return false;
        }
        double step = groundY - location.getY();
        if (step > 0.625D || step < -1.25D) {
            return false;
        }
        target.setY(groundY);
        return hasClearance(target);
    }

    private boolean hasClearance(Location target) {
        // Approximate the original 1.5 x 0.5 Go Kart hitbox at the front, centre and rear.
        double[] forwardSamples = {-0.78D, 0.0D, 0.78D};
        double[] sideSamples = {-0.62D, 0.62D};
        for (double z : forwardSamples) {
            for (double x : sideSamples) {
                Location sample = local(target, x, 0.18D, z);
                if (sample.getBlock().getType().isSolid()) {
                    return false;
                }
                if (sample.clone().add(0.0D, 0.48D, 0.0D).getBlock().getType().isSolid()) {
                    return false;
                }
            }
        }
        return true;
    }

    private SurfaceProfile surfaceProfile() {
        float friction = 0.0F;
        float tractionFactor = 0.0F;
        int count = 0;
        double[][] wheels = {
                {-0.50D, 0.55D}, {0.50D, 0.55D}, {-0.50D, -0.59D}, {0.50D, -0.59D}
        };
        for (double[] wheel : wheels) {
            Location point = local(location, wheel[0], -0.15D, wheel[1]);
            SurfaceProfile profile = SurfaceProfile.at(point.getBlock());
            if (profile.friction() > 0.0F) {
                friction += profile.friction();
                tractionFactor += profile.tractionFactor();
                count++;
            }
        }
        return count == 0 ? SurfaceProfile.at(location.clone().add(0.0D, -0.2D, 0.0D).getBlock())
                : new SurfaceProfile(friction / count, tractionFactor / count);
    }

    private boolean isSliding(Vector forward) {
        if (velocity.lengthSquared() < 1.0E-8D) {
            return false;
        }
        return velocity.clone().normalize().crossProduct(forward.clone().normalize()).length() >= 0.3D;
    }

    private void effects(Player driver, Vector movement) {
        World world = location.getWorld();
        if (world == null || driver == null) {
            return;
        }
        if (age % 18 == 0 && (fuel > 0.0F || driver.getGameMode() == GameMode.CREATIVE)) {
            float normalizedSpeed = (float) Math.min(1.0D, velocity.length() / 18.0D);
            float pitch = 0.9F + (2.0F - 0.9F) * normalizedSpeed;
            world.playSound(location, "vehicle:entity.go_kart.engine", SoundCategory.NEUTRAL, 0.55F, pitch);
        }
        if (age % 2 == 0 && Math.abs(throttle) > 0.01F) {
            Location exhaust = local(location, 0.0D, 0.50D, -1.0D);
            world.spawnParticle(Particle.SMOKE, exhaust, 1, 0.02D, 0.02D, 0.02D, 0.005D);
        }
        if (movement.lengthSquared() > 0.01D && isSliding(forward(location.getYaw()))) {
            Location rear = local(location, 0.0D, 0.12D, -0.6D);
            world.spawnParticle(Particle.CLOUD, rear, 1, 0.2D, 0.02D, 0.1D, 0.01D);
        }
    }

    public boolean mount(Player player) {
        if (!rig.valid() || driver().isPresent()) {
            return false;
        }
        return rig.seat().addPassenger(player);
    }

    public Optional<Player> driver() {
        List<Entity> passengers = rig.seat().getPassengers();
        if (passengers.isEmpty() || !(passengers.getFirst() instanceof Player player)) {
            return Optional.empty();
        }
        return Optional.of(player);
    }

    public void remove() {
        rig.remove();
    }

    public UUID id() {
        return id;
    }

    public Location location() {
        return location.clone();
    }

    public GoKartRig rig() {
        return rig;
    }

    public float fuel() {
        return fuel;
    }

    public void setFuel(float fuel) {
        this.fuel = clamp(fuel, 0.0F, ENERGY_CAPACITY);
    }

    public void setVelocity(Vector velocity) {
        this.velocity = velocity.clone();
    }

    private static double findSpawnY(Location location) {
        Double y = groundY(location);
        return y == null ? location.getY() : y;
    }

    private static Double groundY(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        int start = (int) Math.floor(location.getY() + 0.625D);
        int minimum = Math.max(world.getMinHeight(), start - 3);
        int x = location.getBlockX();
        int z = location.getBlockZ();
        for (int y = start; y >= minimum; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()) {
                return y + 1.0D;
            }
        }
        return null;
    }

    private static Location local(Location root, double x, double y, double z) {
        double radians = Math.toRadians(root.getYaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return root.clone().add(x * cos - z * sin, y, x * sin + z * cos);
    }

    private static Vector forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    /** Matches Mojang Vector3d#yRot rather than Bukkit's conventional rotation sign. */
    private static Vector rotateYLikeMinecraft(Vector vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector(vector.getX() * cos + vector.getZ() * sin, vector.getY(),
                vector.getZ() * cos - vector.getX() * sin);
    }

    private static Vector lerp(Vector start, Vector end, float amount) {
        return start.clone().multiply(1.0F - amount).add(end.clone().multiply(amount));
    }

    private static void clampLength(Vector vector, double max) {
        if (max < 0.0D || vector.lengthSquared() <= max * max) {
            return;
        }
        if (vector.lengthSquared() > 1.0E-8D) {
            vector.normalize().multiply(max);
        }
    }

    private static float yaw(Vector direction) {
        return normalizeYaw((float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())));
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized < -180.0F ? normalized + 360.0F : normalized >= 180.0F ? normalized - 360.0F : normalized;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
