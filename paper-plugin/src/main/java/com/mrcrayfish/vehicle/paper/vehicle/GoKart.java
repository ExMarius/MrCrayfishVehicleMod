package com.mrcrayfish.vehicle.paper.vehicle;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.physics.SurfaceProfile;
import com.mrcrayfish.vehicle.paper.physics.VehicleCollisionMover;
import com.mrcrayfish.vehicle.paper.render.GoKartRig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side port of PoweredVehicleEntity and LandVehicleEntity for the Go
 * Kart. Variable names, update order, forces, traction, bicycle steering,
 * charging/boosting, and wheel animation follow the original implementation.
 */
public final class GoKart {
    public static final float ENGINE_POWER = GoKartProperties.ENGINE_POWER;
    public static final float MAX_STEERING_ANGLE = GoKartProperties.MAX_STEERING_ANGLE;
    public static final float ENERGY_CAPACITY = GoKartProperties.ENERGY_CAPACITY;
    public static final float ENERGY_PER_TICK = GoKartProperties.ENERGY_PER_TICK;
    public static final float FRONT_AXLE_OFFSET = GoKartProperties.FRONT_AXLE_OFFSET;
    public static final float REAR_AXLE_OFFSET = GoKartProperties.REAR_AXLE_OFFSET;

    private static final int MAX_WHEELIE_TICKS = 10;
    private static final float BRAKE_POWER = -1.0F;
    private static final float DRAG = 0.001F;

    private final VehiclePlugin plugin;
    private final UUID id;
    private final GoKartRig rig;
    private final double[] wheelPositions = new double[GoKartProperties.WHEELS.length * 3];

    private Location location;
    private Vector velocity = new Vector();
    private Vector motion = new Vector();
    private float traction;
    private float throttle;
    private float steeringAngle;
    private float renderWheelAngle;
    private float frontWheelRotationSpeed;
    private float frontWheelRotation;
    private float rearWheelRotationSpeed;
    private float rearWheelRotation;
    private float fuel = ENERGY_CAPACITY;
    private float speedMultiplier;
    private float boostStrength;
    private float chargingAmount;
    private double verticalVelocity;
    private boolean onGround = true;
    private boolean handbraking;
    private boolean charging;
    private boolean boosting;
    private int boostTimer;
    private int wheelieCount;
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

        /* LandVehicleEntity#onClientUpdate runs before onUpdateVehicle. */
        if (boosting && driver != null) {
            if (wheelieCount < MAX_WHEELIE_TICKS) {
                wheelieCount++;
            }
        } else if (wheelieCount > 0) {
            wheelieCount--;
        }

        updateInput(driver);
        renderWheelAngle += (steeringAngle - renderWheelAngle) * 0.3F;
        updateCharging();
        updateVehicleMotion(globalSpeedLimit);

        verticalVelocity -= 0.08D;
        Vector requestedMovement = new Vector(motion.getX(), verticalVelocity + motion.getY(), motion.getZ());
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        /* The original updates wheel contact positions after yaw, but before Entity#move. */
        updateWheelPositions();
        VehicleCollisionMover.Result collision = VehicleCollisionMover.move(
                world, location, requestedMovement, onGround);
        location.add(collision.movement());
        onGround = collision.onGround();
        if (collision.verticalCollision()) {
            verticalVelocity = 0.0D;
        }

        if (boostTimer > 0 && throttle > 0.0F) {
            boostTimer--;
        } else {
            boostTimer = 0;
            boosting = false;
            speedMultiplier *= 0.85F;
        }

        boolean creativeDriver = driver != null && driver.getGameMode() == GameMode.CREATIVE;
        boolean enginePowered = creativeDriver || fuel > 0.0F;
        if (driver != null && !creativeDriver && enginePowered) {
            fuel = Math.max(0.0F, fuel - (float) (ENERGY_PER_TICK * fuelConsumptionFactor));
        }

        updateWheelRotations();
        float wheelieAngle = -30.0F * boostStrength * wheelieProgress();
        rig.update(location, renderWheelAngle, frontWheelRotation, rearWheelRotation,
                wheelieAngle, driver != null && enginePowered, age);
        effects(driver, enginePowered);
    }

    private void updateInput(Player driver) {
        if (driver == null) {
            throttle = 0.0F;
            handbraking = false;
            steeringAngle *= 0.85F;
            return;
        }

        VehicleInput input = VehicleInput.from(driver.getCurrentInput());
        throttle = clamp(input.throttle(), -1.0F, 1.0F);
        handbraking = input.handbrake();

        float turnValue = clamp(input.steeringDirection(), -1.0F, 1.0F);
        float strengthModifier = turnValue != 0.0F ? 0.05F : 0.2F;
        steeringAngle += (MAX_STEERING_ANGLE * turnValue - steeringAngle) * strengthModifier;
    }

    private void updateCharging() {
        boolean oldCharging = charging;
        charging = velocity.length() < 5.0D && handbraking && throttle > 0.0F;
        if (oldCharging && !charging && chargingAmount > 0.0F) {
            releaseCharge(chargingAmount);
        }
    }

    /** Direct Paper-vector translation of LandVehicleEntity#updateVehicleMotion. */
    private void updateVehicleMotion(double globalSpeedLimit) {
        motion = new Vector();
        Vector forward = forward(location.getYaw());

        if (charging) {
            float speed = 0.1F;
            Vector frontWheel = forward.clone().multiply(FRONT_AXLE_OFFSET);
            Vector nextPosition = frontWheel.clone().subtract(
                    rotateYLikeMinecraft(frontWheel, Math.toRadians(steeringAngle)));
            motion.add(nextPosition.multiply(speed));
            location.setYaw(normalizeYaw(location.getYaw() - steeringAngle * speed));
            float forwardForce = clamp(throttle, -1.0F, 1.0F); // Iron engine multiplier is 1.0.
            chargingAmount = clamp(chargingAmount + forwardForce * 0.025F, 0.0F, 1.0F);
        } else {
            chargingAmount = 0.0F;
        }

        SurfaceProfile surface = surfaceProfile();
        float enginePower = onGround ? ENGINE_POWER : 0.0F;
        float brakePower = onGround ? BRAKE_POWER : 0.0F;

        float effectiveThrottle = handbraking || charging ? 0.0F : throttle;
        float forwardForce = enginePower * clamp(effectiveThrottle, -1.0F, 1.0F);
        if (boosting) {
            forwardForce += forwardForce * speedMultiplier;
        }
        if (throttle < 0.0F) {
            forwardForce *= 0.4F;
        }

        Vector acceleration = forward.clone().multiply(forwardForce * 0.05D);
        if (velocity.length() < 0.05D) {
            velocity.zero();
        }
        Vector handbrakeForce = velocity.clone().multiply((handbraking ? brakePower : 0.0F) * 0.05D);
        Vector frictionForce = velocity.clone().multiply(-surface.friction() * 0.05D);
        Vector dragForce = velocity.clone().multiply(velocity.length()).multiply(-DRAG * 0.05D);
        acceleration.add(dragForce).add(frictionForce).add(handbrakeForce);
        velocity.add(acceleration);
        clampLength(velocity, Math.max(0.0D, globalSpeedLimit));

        if (isSliding(forward) && throttle > 0.0F) {
            traction = GoKartProperties.SLIDE_TRACTION;
        } else if (handbraking) {
            traction = 0.05F;
        } else {
            float targetTraction = GoKartProperties.STANDARD_TRACTION;
            if (acceleration.length() > 0.0D) {
                targetTraction *= clamp((float) (velocity.length() / acceleration.length()), 0.0F, 1.0F);
            }
            float side = clamp(1.0F - (float) normalized(velocity).crossProduct(normalized(forward)).length() / 0.3F,
                    0.0F, 1.0F);
            traction += (targetTraction - traction) * side * 0.15F;
        }

        Vector position = location.toVector();
        Vector worldFrontWheel = position.clone().add(forward.clone().multiply(FRONT_AXLE_OFFSET));
        Vector worldRearWheel = position.clone().add(forward.clone().multiply(REAR_AXLE_OFFSET));
        worldFrontWheel.add(rotateYLikeMinecraft(velocity, Math.toRadians(steeringAngle)).multiply(0.05D));
        worldRearWheel.add(velocity.clone().multiply(0.05D));

        Vector heading = normalized(worldFrontWheel.clone().subtract(worldRearWheel));
        if (heading.lengthSquared() < 1.0E-12D) {
            heading = forward.clone();
        }
        Vector nextPosition = worldRearWheel.clone().add(heading.clone().multiply(-REAR_AXLE_OFFSET));
        motion.add(nextPosition.subtract(position));

        float surfaceTraction = surface.tractionFactor() * traction;
        if (heading.dot(normalized(velocity)) > 0.0D) {
            velocity = lerp(velocity, heading.clone().multiply(velocity.length()), surfaceTraction);
        } else {
            Vector reverse = heading.clone().multiply(-Math.min(velocity.length(), GoKartProperties.MAX_REVERSE_SPEED));
            velocity = lerp(velocity, reverse, surfaceTraction);
        }

        if (!charging) {
            float vehicleDeltaYaw = wrapDegrees(yaw(forward) - yaw(heading));
            location.setYaw(normalizeYaw(location.getYaw() - vehicleDeltaYaw));
        }
    }

    private void updateWheelPositions() {
        float yaw = location.getYaw();
        for (int index = 0; index < GoKartProperties.WHEELS.length; index++) {
            GoKartProperties.Wheel wheel = GoKartProperties.WHEELS[index];
            Vector wheelPosition = rotateLocal(wheel.contactX(), wheel.contactY(), wheel.contactZ(), yaw);
            wheelPositions[index * 3] = wheelPosition.getX();
            wheelPositions[index * 3 + 1] = wheelPosition.getY();
            wheelPositions[index * 3 + 2] = wheelPosition.getZ();
        }
    }

    private SurfaceProfile surfaceProfile() {
        World world = location.getWorld();
        if (world == null) {
            return new SurfaceProfile(0.0F, 1.0F);
        }

        float friction = 0.0F;
        float tractionFactor = 0.0F;
        int wheelCount = 0;
        for (int index = 0; index < GoKartProperties.WHEELS.length; index++) {
            int x = floor(location.getX() + wheelPositions[index * 3]);
            int y = floor(location.getY() + wheelPositions[index * 3 + 1] - 0.2D);
            int z = floor(location.getZ() + wheelPositions[index * 3 + 2]);
            SurfaceProfile profile = SurfaceProfile.at(world.getBlockAt(x, y, z));
            if (profile.friction() == 0.0F) {
                continue;
            }
            friction += profile.friction();
            tractionFactor += profile.tractionFactor();
            wheelCount++;
        }
        if (wheelCount == 0) {
            return new SurfaceProfile(0.0F, 1.0F);
        }
        return new SurfaceProfile(friction / wheelCount, tractionFactor / wheelCount);
    }

    private void updateWheelRotations() {
        double direction = forward(location.getYaw()).dot(normalized(motion));
        if (onGround || throttle != 0.0F) {
            rearWheelRotationSpeed = (float) (motion.length() * direction * 20.0D);
        } else {
            rearWheelRotationSpeed *= 0.9F;
        }
        if (onGround) {
            frontWheelRotationSpeed = (float) (motion.length() * direction * 20.0D);
        } else {
            frontWheelRotationSpeed *= 0.9F;
        }

        double frontCircumference = 24.0D * GoKartProperties.FRONT_LEFT.scaleY();
        frontWheelRotation -= (float) ((frontWheelRotationSpeed * 16.0D / frontCircumference) * 20.0D);

        if (handbraking && !charging) {
            return;
        }
        if (charging) {
            rearWheelRotationSpeed = ENGINE_POWER * chargingAmount;
        }
        double rearCircumference = 24.0D * GoKartProperties.REAR_LEFT.scaleY();
        rearWheelRotation -= (float) ((rearWheelRotationSpeed * 16.0D / rearCircumference) * 20.0D);
    }

    private void releaseCharge(float strength) {
        boosting = true;
        boostStrength = clamp(strength, 0.0F, 1.0F);
        boostTimer = (int) (20.0F * boostStrength);
        speedMultiplier = 0.5F * boostStrength;
    }

    private float wheelieProgress() {
        float progress = wheelieCount / (float) MAX_WHEELIE_TICKS;
        return 1.0F - (1.0F - progress) * (1.0F - progress);
    }

    private boolean isSliding(Vector forward) {
        return normalized(velocity).crossProduct(normalized(forward)).length() >= 0.3D;
    }

    private void effects(Player driver, boolean enginePowered) {
        World world = location.getWorld();
        if (world == null || driver == null) {
            return;
        }

        if (age % 18 == 0 && enginePowered) {
            float pitch = GoKartProperties.MIN_ENGINE_PITCH
                    + (GoKartProperties.MAX_ENGINE_PITCH - GoKartProperties.MIN_ENGINE_PITCH)
                    * (float) Math.abs(motion.length() * 20.0D / 25.0D);
            if ((isSliding(forward(location.getYaw())) && throttle > 0.0F && !handbraking) || boosting) {
                pitch = GoKartProperties.MIN_ENGINE_PITCH
                        + (GoKartProperties.MAX_ENGINE_PITCH - GoKartProperties.MIN_ENGINE_PITCH) * throttle;
            }
            world.playSound(location, "vehicle:entity.go_kart.engine", SoundCategory.NEUTRAL, 1.0F, pitch);
        }

        if (age % 2 == 0 && enginePowered) {
            Location exhaust = local(location, 0.0D, 8.0D / 16.0D, -1.0D);
            world.spawnParticle(Particle.SMOKE, exhaust, 1, 0.02D, 0.02D, 0.02D, 0.005D);
        }
        if (motion.lengthSquared() > 0.01D && isSliding(forward(location.getYaw()))) {
            Location rear = local(location, 0.0D, 0.12D, GoKartProperties.REAR_AXLE_OFFSET);
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

    public Vector velocity() {
        return velocity.clone();
    }

    public void setVelocity(Vector velocity) {
        this.velocity = velocity.clone();
    }

    public float traction() {
        return traction;
    }

    public void setTraction(float traction) {
        this.traction = traction;
    }

    public double verticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(double verticalVelocity) {
        this.verticalVelocity = verticalVelocity;
    }

    private static double findSpawnY(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return location.getY();
        }

        int start = floor(location.getY() + 0.625D);
        int minimum = Math.max(world.getMinHeight(), start - 3);
        double best = Double.NEGATIVE_INFINITY;
        for (int y = start; y >= minimum; y--) {
            Block block = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
            for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                if (location.getX() >= box.getMinX() && location.getX() <= box.getMaxX()
                        && location.getZ() >= box.getMinZ() && location.getZ() <= box.getMaxZ()) {
                    best = Math.max(best, box.getMaxY());
                }
            }
            if (best != Double.NEGATIVE_INFINITY) {
                return best;
            }
        }
        return location.getY();
    }

    private static Location local(Location root, double x, double y, double z) {
        Vector offset = rotateLocal(x, y, z, root.getYaw());
        return root.clone().add(offset);
    }

    private static Vector rotateLocal(double x, double y, double z, float yaw) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vector(x * cos - z * sin, y, x * sin + z * cos);
    }

    private static Vector forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    /** Matches Mojang Vector3d#yRot. */
    private static Vector rotateYLikeMinecraft(Vector vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector(vector.getX() * cos + vector.getZ() * sin, vector.getY(),
                vector.getZ() * cos - vector.getX() * sin);
    }

    private static Vector normalized(Vector vector) {
        if (vector.lengthSquared() < 1.0E-8D) {
            return new Vector();
        }
        return vector.clone().normalize();
    }

    private static Vector lerp(Vector start, Vector end, float amount) {
        return start.clone().multiply(1.0F - amount).add(end.clone().multiply(amount));
    }

    private static void clampLength(Vector vector, double maximum) {
        if (vector.lengthSquared() > maximum * maximum && vector.lengthSquared() > 1.0E-8D) {
            vector.normalize().multiply(maximum);
        }
    }

    private static float yaw(Vector vector) {
        return (float) (Math.toDegrees(Math.atan2(vector.getZ(), vector.getX())) - 90.0D);
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private static float normalizeYaw(float yaw) {
        return wrapDegrees(yaw);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
