package com.mrcrayfish.vehicle.paper.vehicle;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.physics.SurfaceProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.mrcrayfish.vehicle.paper.physics.VehicleCollisionMover;
import com.mrcrayfish.vehicle.paper.render.LandVehicleRig;
import com.mrcrayfish.vehicle.paper.runtime.EngineSoundController;
import com.mrcrayfish.vehicle.paper.runtime.TrailerManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side port of PoweredVehicleEntity and LandVehicleEntity for the land vehicles. Variable names, update order, forces, traction, bicycle steering,
 * charging/boosting, and wheel animation follow the original implementation.
 */
public final class LandVehicle {
    private static final int MAX_WHEELIE_TICKS = 10;
    private static final float BRAKE_POWER = -1.0F;
    private static final float DRAG = 0.001F;

    private final VehiclePlugin plugin;
    private final UUID id;
    private final LandVehicleSpec spec;
    private final TrailerManager trailers;
    private final LandVehicleRig rig;
    private final EngineSoundController soundController;
    private final double[] wheelPositions;
    private final Inventory storageInventory;
    private final Map<String, Inventory> compartmentInventories;

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
    private float fuel;
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
    private boolean transported;
    private boolean chestAttached;
    private float waterSpeed;
    private WaterVehiclePhysics.State waterState = WaterVehiclePhysics.State.IN_AIR;
    private WaterVehiclePhysics.State previousWaterState = WaterVehiclePhysics.State.IN_AIR;

    private LandVehicle(VehiclePlugin plugin, UUID id, Location location, LandVehicleSpec spec,
                        TrailerManager trailers, LandVehicleRig rig) {
        this.plugin = plugin;
        this.id = id;
        this.location = location;
        this.spec = spec;
        this.trailers = trailers;
        this.rig = rig;
        this.soundController = new EngineSoundController(spec, rig);
        this.wheelPositions = new double[spec.wheels().size() * 3];
        this.storageInventory = spec.mopedParts() == null ? null
                : Bukkit.createInventory(null, 27, Component.text("Moped Chest"));
        this.compartmentInventories = new LinkedHashMap<>();
        for (LandVehicleSpec.StorageCompartment compartment : spec.storageCompartments()) {
            compartmentInventories.put(compartment.key(), Bukkit.createInventory(null,
                    compartment.size(), Component.text(compartment.title())));
        }
        this.fuel = spec.energyCapacity();
    }

    public static LandVehicle spawn(VehiclePlugin plugin, UUID id, Location location, LandVehicleSpec spec,
                                    TrailerManager trailers) {
        Location root = location.clone();
        root.setPitch(0.0F);
        root.setYaw(normalizeYaw(root.getYaw()));
        root.setY(findSpawnY(root, spec.motionType()));
        LandVehicleRig rig = LandVehicleRig.spawn(id, spec, root);
        return new LandVehicle(plugin, id, root, spec, trailers, rig);
    }

    public void tick(double globalSpeedLimit, double fuelConsumptionFactor) {
        age++;
        boolean openablesChanged = rig.tickOpenables(location);
        updateSeatGauge();
        rig.tickSeats(location.getYaw());
        if (age % 10 == 0) {
            showFuelOverlay();
        }
        if (transported) {
            soundController.tick(location, false, spec.minEnginePitch(), List.of());
            if (openablesChanged) {
                rig.update(location, renderWheelAngle, frontWheelRotation, rearWheelRotation,
                        0.0F, 0.0F, false, age);
            }
            return;
        }
        if (spec.motionType() == LandVehicleSpec.MotionType.WATER) {
            tickWater(globalSpeedLimit, fuelConsumptionFactor, openablesChanged);
            return;
        }
        Player driver = driver().orElse(null);

        /* LandVehicleEntity#onClientUpdate runs before onUpdateVehicle. */
        if (boosting && driver != null) {
            if (wheelieCount < MAX_WHEELIE_TICKS) {
                wheelieCount++;
            }
        } else if (wheelieCount > 0) {
            wheelieCount--;
        }

        if (driver == null && resting()) {
            soundController.tick(location, false, spec.minEnginePitch(), List.of());
            updateInput(null);
            float previousRenderAngle = renderWheelAngle;
            renderWheelAngle += (steeringAngle - renderWheelAngle) * 0.3F;
            if (Math.abs(renderWheelAngle) < 0.001F) {
                renderWheelAngle = 0.0F;
            }
            traction = LandVehicleSpec.STANDARD_TRACTION;
            if (openablesChanged || previousRenderAngle != renderWheelAngle) {
                rig.update(location, renderWheelAngle, frontWheelRotation, rearWheelRotation,
                        0.0F, spec.bodyRoll(steeringAngle, horizontalSpeed()), false, age);
            } else if (age % 20 == 0) {
                rig.refreshBrightness(location);
            }
            return;
        }

        boolean creativeDriver = driver != null && driver.getGameMode() == GameMode.CREATIVE;
        boolean enginePowered = creativeDriver || fuel > 0.0F;
        updateInput(driver);
        updateCharging(enginePowered);
        float targetRenderAngle = !charging && isSliding(forward(location.getYaw()))
                ? -clamp(steeringAngle * 2.0F, -spec.maxSteeringAngle(), spec.maxSteeringAngle())
                : steeringAngle;
        renderWheelAngle += (targetRenderAngle - renderWheelAngle) * 0.3F;
        updateVehicleMotion(globalSpeedLimit, enginePowered);

        verticalVelocity -= 0.08D;
        Vector requestedMovement = new Vector(motion.getX(), verticalVelocity + motion.getY(), motion.getZ());
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        /* The original updates wheel contact positions after yaw, but before Entity#move. */
        updateWheelPositions();
        VehicleCollisionMover.Result collision = VehicleCollisionMover.move(
                world, location, requestedMovement, onGround,
                spec.entityWidth(), spec.entityHeight(), spec.stepHeight());
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

        if (driver != null && !creativeDriver && enginePowered) {
            fuel = Math.max(0.0F, fuel - (float) (spec.energyPerTick() * fuelConsumptionFactor));
        }
        updateSeatGauge();

        updateWheelRotations();
        float wheelieAngle = -30.0F * boostStrength * wheelieProgress();
        float bodyRoll = spec.bodyRoll(steeringAngle, horizontalSpeed());
        rig.update(location, renderWheelAngle, frontWheelRotation, rearWheelRotation,
                wheelieAngle, bodyRoll, driver != null && enginePowered, age);
        effects(driver, enginePowered);
        if (driver != null && spec.lawnMower()) {
            LawnMowerBehavior.cutBushes(location, motion, spec.entityWidth(), driver,
                    stack -> trailers.storeMowerDrop(id, stack));
        }
    }

    /**
     * Paper translation of the last complete released BoatEntity water equations. The
     * 1.16.X-dev method is empty, so this deliberately restores the source buoyancy,
     * water/air transition momentum, speed damping, and steering rather than inventing
     * land behavior for the Jet Ski.
     */
    private void tickWater(double globalSpeedLimit, double fuelConsumptionFactor,
                           boolean openablesChanged) {
        Player driver = driver().orElse(null);
        WaterStatus status = waterStatus();
        previousWaterState = waterState;
        waterState = status.state();

        boolean creativeDriver = driver != null && driver.getGameMode() == GameMode.CREATIVE;
        boolean enginePowered = creativeDriver || fuel > 0.0F;
        updateInput(driver);
        renderWheelAngle += (steeringAngle - renderWheelAngle) * 0.3F;
        if (Math.abs(renderWheelAngle) < 0.001F) {
            renderWheelAngle = 0.0F;
        }

        boolean inPropellingWater = waterState == WaterVehiclePhysics.State.IN_WATER
                || waterState == WaterVehiclePhysics.State.UNDER_WATER;
        waterSpeed = WaterVehiclePhysics.updateSpeed(waterSpeed, throttle,
                driver != null && enginePowered, inPropellingWater, globalSpeedLimit);
        if (Math.abs(waterSpeed) < 0.001F) {
            waterSpeed = 0.0F;
        }

        float deltaYaw = WaterVehiclePhysics.deltaYaw(steeringAngle, waterSpeed,
                waterState == WaterVehiclePhysics.State.IN_AIR);
        location.setYaw(normalizeYaw(location.getYaw() - deltaYaw));

        if (inPropellingWater) {
            if (waterState == WaterVehiclePhysics.State.UNDER_WATER) {
                velocity.setY(velocity.getY() + 0.08D);
            } else {
                double targetY = WaterVehiclePhysics.targetSurfaceY(status.waterLevel(), waterSpeed);
                double floatingY = (targetY - location.getY()) / spec.entityHeight();
                velocity.setY(velocity.getY() + floatingY * 0.05D);
                if (Math.abs(floatingY) < 0.1D && velocity.getY() > 0.0D
                        && Math.abs(velocity.getY()) < 0.1D) {
                    location.setY(targetY);
                    velocity.setY(0.0D);
                }
                velocity.setY(velocity.getY() * 0.75D);
            }

            Vector forwardMotion = forward(location.getYaw()).multiply(waterSpeed / 20.0D);
            motion.setX(forwardMotion.getX());
            motion.setY(0.0D);
            motion.setZ(forwardMotion.getZ());
            velocity.setX(velocity.getX() * 0.5D);
            velocity.setZ(velocity.getZ() * 0.5D);
        } else if (waterState == WaterVehiclePhysics.State.IN_AIR) {
            velocity.setY(velocity.getY() - 0.08D);
            if (previousWaterState == WaterVehiclePhysics.State.IN_WATER
                    || previousWaterState == WaterVehiclePhysics.State.UNDER_WATER) {
                velocity.setX(motion.getX());
                velocity.setZ(motion.getZ());
                motion.zero();
            }
        } else {
            motion.multiply(0.75D);
        }

        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Vector requestedMovement = velocity.clone().add(motion);
        VehicleCollisionMover.Result collision = VehicleCollisionMover.move(
                world, location, requestedMovement, onGround,
                spec.entityWidth(), spec.entityHeight(), spec.stepHeight());
        location.add(collision.movement());
        onGround = collision.onGround();
        if (collision.verticalCollision()) {
            velocity.setY(0.0D);
        }
        if (onGround) {
            velocity.setX(velocity.getX() * 0.8D);
            velocity.setY(velocity.getY() * 0.98D);
            velocity.setZ(velocity.getZ() * 0.8D);
        } else {
            velocity.multiply(0.98D);
        }
        verticalVelocity = velocity.getY();

        if (driver != null && !creativeDriver && enginePowered) {
            fuel = Math.max(0.0F, fuel - (float) (spec.energyPerTick() * fuelConsumptionFactor));
        }
        updateSeatGauge();
        rig.update(location, renderWheelAngle, 0.0F, 0.0F,
                0.0F, 0.0F, driver != null && enginePowered, age);
        waterEffects(driver, enginePowered);
        if (driver == null && age % 20 == 0 && !openablesChanged) {
            rig.refreshBrightness(location);
        }
    }

    private void waterEffects(Player driver, boolean enginePowered) {
        if (driver == null) {
            soundController.tick(location, false, spec.minEnginePitch(), List.of());
            return;
        }
        float targetPitch = EngineSoundController.targetPitch(spec, Math.abs(waterSpeed),
                false, 0.0F, false, false, throttle, handbraking);
        List<UUID> riders = rig.seatCarriers().stream()
                .flatMap(seat -> seat.getPassengers().stream())
                .filter(Player.class::isInstance)
                .map(Entity::getUniqueId)
                .toList();
        soundController.tick(location, enginePowered, targetPitch, riders);

        World world = location.getWorld();
        if (world == null || waterState != WaterVehiclePhysics.State.IN_WATER
                || throttle <= 0.0F) {
            return;
        }
        double y = location.getY() + 0.1D;
        double spread = spec.entityWidth() * 0.5D;
        world.spawnParticle(Particle.SPLASH, location.getX(), y, location.getZ(),
                5, spread, 0.0D, spread, Math.max(0.05D, Math.abs(waterSpeed) * 0.02D));
        world.spawnParticle(Particle.BUBBLE, location.getX(), y, location.getZ(),
                5, spread, 0.0D, spread, Math.max(0.01D, Math.abs(waterSpeed) * 0.01D));
    }

    private void updateInput(Player driver) {
        if (driver == null) {
            throttle = 0.0F;
            handbraking = false;
            steeringAngle *= 0.85F;
            if (Math.abs(steeringAngle) < 0.001F) {
                steeringAngle = 0.0F;
            }
            return;
        }

        VehicleInput input = VehicleInput.from(driver.getCurrentInput());
        throttle = clamp(input.throttle(), -1.0F, 1.0F);
        handbraking = input.handbrake();

        float turnValue = clamp(input.steeringDirection(), -1.0F, 1.0F);
        float strengthModifier = turnValue != 0.0F ? 0.05F : 0.2F;
        steeringAngle += (spec.maxSteeringAngle() * turnValue - steeringAngle) * strengthModifier;
    }

    private void updateCharging(boolean enginePowered) {
        boolean oldCharging = charging;
        charging = enginePowered && velocity.length() < 5.0D && handbraking && throttle > 0.0F;
        if (oldCharging && !charging && chargingAmount > 0.0F) {
            releaseCharge(chargingAmount);
        }
    }

    /** Direct Paper-vector translation of LandVehicleEntity#updateVehicleMotion. */
    private void updateVehicleMotion(double globalSpeedLimit, boolean enginePowered) {
        motion = new Vector();
        Vector forward = forward(location.getYaw());

        if (charging) {
            float speed = 0.1F;
            Vector frontWheel = forward.clone().multiply(spec.frontAxleOffset());
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
        float enginePower = onGround ? spec.enginePower() : 0.0F;
        float brakePower = onGround ? BRAKE_POWER : 0.0F;

        float effectiveThrottle = !enginePowered || handbraking || charging ? 0.0F : throttle;
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
            traction = LandVehicleSpec.SLIDE_TRACTION;
        } else if (handbraking) {
            traction = 0.05F;
        } else {
            float targetTraction = LandVehicleSpec.STANDARD_TRACTION;
            if (acceleration.length() > 0.0D) {
                targetTraction *= clamp((float) (velocity.length() / acceleration.length()), 0.0F, 1.0F);
            }
            float side = clamp(1.0F - (float) normalized(velocity).crossProduct(normalized(forward)).length() / 0.3F,
                    0.0F, 1.0F);
            traction += (targetTraction - traction) * side * 0.15F;
        }

        Vector position = location.toVector();
        Vector worldFrontWheel = position.clone().add(forward.clone().multiply(spec.frontAxleOffset()));
        Vector worldRearWheel = position.clone().add(forward.clone().multiply(spec.rearAxleOffset()));
        worldFrontWheel.add(rotateYLikeMinecraft(velocity, Math.toRadians(steeringAngle)).multiply(0.05D));
        worldRearWheel.add(velocity.clone().multiply(0.05D));

        Vector heading = normalized(worldFrontWheel.clone().subtract(worldRearWheel));
        if (heading.lengthSquared() < 1.0E-12D) {
            heading = forward.clone();
        }
        Vector nextPosition = worldRearWheel.clone().add(heading.clone().multiply(-spec.rearAxleOffset()));
        motion.add(nextPosition.subtract(position));

        float surfaceTraction = surface.tractionFactor() * traction;
        if (heading.dot(normalized(velocity)) > 0.0D) {
            velocity = lerp(velocity, heading.clone().multiply(velocity.length()), surfaceTraction);
        } else {
            Vector reverse = heading.clone().multiply(-Math.min(velocity.length(), spec.maxReverseSpeed()));
            velocity = lerp(velocity, reverse, surfaceTraction);
        }

        if (!charging) {
            float vehicleDeltaYaw = wrapDegrees(yaw(forward) - yaw(heading));
            location.setYaw(normalizeYaw(location.getYaw() - vehicleDeltaYaw));
        }
    }

    private void updateWheelPositions() {
        float yaw = location.getYaw();
        for (int index = 0; index < spec.wheels().size(); index++) {
            LandVehicleSpec.Wheel wheel = spec.wheels().get(index);
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
        for (int index = 0; index < spec.wheels().size(); index++) {
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

        if (!charging) {
            double frontCircumference = 24.0D * spec.firstFrontWheel().scaleY();
            frontWheelRotation -= (float) ((frontWheelRotationSpeed * 16.0D / frontCircumference) * 20.0D);
        }

        if (handbraking && !charging) {
            return;
        }
        if (charging) {
            rearWheelRotationSpeed = spec.enginePower() * chargingAmount;
        }
        double rearCircumference = 24.0D * spec.firstRearWheel().scaleY();
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

    private double horizontalSpeed() {
        return Math.hypot(motion.getX(), motion.getZ()) * 20.0D;
    }

    private boolean isSliding(Vector forward) {
        return normalized(velocity).crossProduct(normalized(forward)).length() >= 0.3D;
    }

    private void effects(Player driver, boolean enginePowered) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (driver == null) {
            soundController.tick(location, false, spec.minEnginePitch(), List.of());
            return;
        }

        boolean sliding = isSliding(forward(location.getYaw()));
        float targetPitch = EngineSoundController.targetPitch(spec, motion.length() * 20.0D,
                charging, chargingAmount, sliding, boosting, throttle, handbraking);
        List<UUID> riders = rig.seatCarriers().stream()
                .flatMap(seat -> seat.getPassengers().stream())
                .filter(Player.class::isInstance)
                .map(Entity::getUniqueId)
                .toList();
        soundController.tick(location, enginePowered, targetPitch, riders);

        if (age % 2 == 0 && enginePowered && spec.exhaustFumes()) {
            LandVehicleSpec.Point point = spec.exhaustPosition();
            Location exhaust = local(location, point.x(), point.y(), point.z());
            world.spawnParticle(Particle.SMOKE, exhaust, 1, 0.02D, 0.02D, 0.02D, 0.005D);
        }
        if (motion.lengthSquared() > 0.01D && isSliding(forward(location.getYaw()))) {
            Location rear = local(location, 0.0D, 0.12D, spec.rearAxleOffset());
            world.spawnParticle(Particle.CLOUD, rear, 1, 0.2D, 0.02D, 0.1D, 0.01D);
        }
    }

    /**
     * Vanilla interaction replacement for source client-side ray tracing. Sports Car storage
     * boxes and openable cosmetics compete by nearest ray hit, preserving the source order in
     * which the closed boot must be opened before the trunk box behind it can be reached.
     * Moped chest attachment preserves the original behavior of not consuming the selected chest.
     */
    public boolean handleSpecialInteraction(Player player, Entity clicked) {
        return handleSpecialInteraction(player, clicked, true);
    }

    public boolean handleSpecialAttack(Player player, Entity clicked) {
        return handleSpecialInteraction(player, clicked, false);
    }

    private boolean handleSpecialInteraction(Player player, Entity clicked, boolean rightClick) {
        SpecialTarget target = targetedSpecialPart(player);
        if (target != null) {
            if (target.openable() != null) {
                return rig.toggleOpenable(target.openable().id(), location);
            }
            Inventory inventory = compartmentInventories.get(target.compartment().key());
            if (inventory != null) {
                if (rightClick) {
                    Location soundLocation = compartmentLocation(target.compartment());
                    player.getWorld().playSound(soundLocation, Sound.BLOCK_CHEST_OPEN, 0.5F, 0.9F);
                    player.openInventory(inventory);
                }
                return true;
            }
        }

        if (storageInventory == null) {
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!chestAttached && held.getType() == Material.CHEST) {
            attachChest(held);
            player.getWorld().playSound(location, Sound.BLOCK_WOOD_PLACE, 1.0F, 1.0F);
            player.sendRichMessage("<green>Lada a fost atașată Moped-ului.</green> "
                    + "<gray>Apasă pe lada din spate pentru inventar.</gray>");
            return true;
        }
        if (!rig.isStorageInteraction(clicked) && !aimingAtMopedStorage(player)) {
            return false;
        }
        if (!rightClick) {
            return true;
        }
        if (!chestAttached) {
            player.sendRichMessage("<yellow>Ține o ladă în mână pentru a o atașa Moped-ului.</yellow>");
            return true;
        }
        if (player.isSneaking()) {
            detachChest();
            player.sendRichMessage("<green>Lada Moped-ului a fost detașată.</green>");
            return true;
        }
        player.getWorld().playSound(chestRuntimeLocation(), Sound.BLOCK_CHEST_OPEN, 0.5F, 0.9F);
        player.openInventory(storageInventory);
        return true;
    }

    private SpecialTarget targetedSpecialPart(Player player) {
        if (spec.storageCompartments().isEmpty()
                && spec.bodyParts().stream().noneMatch(part -> part.openable() != null)) {
            return null;
        }
        Location eye = player.getEyeLocation();
        if (eye.getWorld() == null || !eye.getWorld().equals(location.getWorld())) {
            return null;
        }
        LandVehicleSpec.Point origin = vehicleLocalPoint(eye.toVector());
        LandVehicleSpec.Point direction = vehicleLocalDirection(eye.getDirection().normalize());
        double nearest = 6.0D;
        LandVehicleSpec.StorageCompartment nearestCompartment = null;
        LandVehicleSpec.Openable nearestOpenable = null;

        for (LandVehicleSpec.StorageCompartment compartment : spec.storageCompartments()) {
            double distance = compartment.interactionBox().rayIntersection(origin, direction, nearest);
            if (distance <= nearest) {
                nearest = distance;
                nearestCompartment = compartment;
                nearestOpenable = null;
            }
        }
        if (!player.isSneaking()) {
            for (LandVehicleSpec.Part part : spec.bodyParts()) {
                LandVehicleSpec.Openable openable = part.openable();
                if (openable == null) {
                    continue;
                }
                LandVehicleSpec.Point partOrigin = subtract(origin, part.center());
                LandVehicleSpec.Point inverseOrigin = rotate(partOrigin, openable.axis(),
                        -rig.openAngle(openable.id()));
                LandVehicleSpec.Point inverseDirection = rotate(direction, openable.axis(),
                        -rig.openAngle(openable.id()));
                double distance = openable.interactionBox()
                        .rayIntersection(inverseOrigin, inverseDirection, nearest);
                if (distance < nearest) {
                    nearest = distance;
                    nearestCompartment = null;
                    nearestOpenable = openable;
                }
            }
        }
        return nearestCompartment == null && nearestOpenable == null ? null
                : new SpecialTarget(nearestCompartment, nearestOpenable, nearest);
    }

    private LandVehicleSpec.Point vehicleLocalPoint(Vector worldPoint) {
        Vector relative = worldPoint.clone().subtract(location.toVector());
        double radians = Math.toRadians(location.getYaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new LandVehicleSpec.Point(
                (float) (relative.getX() * cos + relative.getZ() * sin),
                (float) relative.getY(),
                (float) (-relative.getX() * sin + relative.getZ() * cos));
    }

    private LandVehicleSpec.Point vehicleLocalDirection(Vector worldDirection) {
        double radians = Math.toRadians(location.getYaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new LandVehicleSpec.Point(
                (float) (worldDirection.getX() * cos + worldDirection.getZ() * sin),
                (float) worldDirection.getY(),
                (float) (-worldDirection.getX() * sin + worldDirection.getZ() * cos));
    }

    private static LandVehicleSpec.Point subtract(LandVehicleSpec.Point left,
                                                   LandVehicleSpec.Point right) {
        return new LandVehicleSpec.Point(left.x() - right.x(), left.y() - right.y(),
                left.z() - right.z());
    }

    private static LandVehicleSpec.Point rotate(LandVehicleSpec.Point point,
                                                 LandVehicleSpec.Axis axis, float degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return switch (axis) {
            case X -> new LandVehicleSpec.Point(point.x(),
                    (float) (point.y() * cos - point.z() * sin),
                    (float) (point.y() * sin + point.z() * cos));
            case Y -> new LandVehicleSpec.Point(
                    (float) (point.x() * cos + point.z() * sin), point.y(),
                    (float) (-point.x() * sin + point.z() * cos));
            case Z -> new LandVehicleSpec.Point(
                    (float) (point.x() * cos - point.y() * sin),
                    (float) (point.x() * sin + point.y() * cos), point.z());
        };
    }

    private void attachChest(ItemStack chestItem) {
        storageInventory.clear();
        if (chestItem.getItemMeta() instanceof BlockStateMeta meta
                && meta.getBlockState() instanceof InventoryHolder holder) {
            ItemStack[] stored = holder.getInventory().getContents();
            for (int slot = 0; slot < Math.min(stored.length, storageInventory.getSize()); slot++) {
                ItemStack stack = stored[slot];
                if (stack != null && !stack.getType().isAir()) {
                    storageInventory.setItem(slot, stack.clone());
                }
            }
        }
        chestAttached = true;
        rig.setStorageChestAttached(true);
    }

    private void detachChest() {
        closeStorageViewers();
        World world = location.getWorld();
        Location target = chestRuntimeLocation();
        if (world != null) {
            for (ItemStack stack : storageInventory.getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    world.dropItemNaturally(target, stack.clone());
                }
            }
            world.dropItemNaturally(target, new ItemStack(Material.CHEST));
            world.playSound(location, Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
        }
        storageInventory.clear();
        chestAttached = false;
        rig.setStorageChestAttached(false);
    }

    public boolean ownsStorage(Inventory inventory) {
        return (storageInventory != null && storageInventory == inventory)
                || compartmentInventories.containsValue(inventory);
    }

    public void storageClosed(Inventory inventory) {
        Location soundLocation = null;
        if (storageInventory != null && chestAttached && storageInventory == inventory
                && storageInventory.getViewers().size() <= 1) {
            soundLocation = chestRuntimeLocation();
        } else if (inventory.getViewers().size() <= 1) {
            for (LandVehicleSpec.StorageCompartment compartment : spec.storageCompartments()) {
                if (compartmentInventories.get(compartment.key()) == inventory) {
                    soundLocation = compartmentLocation(compartment);
                    break;
                }
            }
        }
        if (soundLocation != null && soundLocation.getWorld() != null) {
            soundLocation.getWorld().playSound(soundLocation, Sound.BLOCK_CHEST_CLOSE, 0.5F, 0.9F);
        }
    }

    public boolean chestAttached() {
        return chestAttached;
    }

    public Map<String, List<ItemStack>> storageContents() {
        Map<String, List<ItemStack>> contents = new LinkedHashMap<>();
        if (storageInventory != null && chestAttached) {
            contents.put("moped_chest", copyContents(storageInventory));
        }
        for (Map.Entry<String, Inventory> entry : compartmentInventories.entrySet()) {
            contents.put(entry.getKey(), copyContents(entry.getValue()));
        }
        return contents;
    }

    private static List<ItemStack> copyContents(Inventory inventory) {
        List<ItemStack> contents = new ArrayList<>(inventory.getSize());
        for (ItemStack stack : inventory.getContents()) {
            contents.add(stack == null || stack.getType().isAir() ? null : stack.clone());
        }
        return contents;
    }

    public void restoreStorage(boolean attached, Map<String, List<ItemStack>> contents) {
        if (storageInventory != null) {
            storageInventory.clear();
            chestAttached = attached;
            if (attached) {
                restoreContents(storageInventory, contents == null ? null : contents.get("moped_chest"));
            }
            rig.setStorageChestAttached(attached);
        }
        for (Map.Entry<String, Inventory> entry : compartmentInventories.entrySet()) {
            entry.getValue().clear();
            restoreContents(entry.getValue(), contents == null ? null : contents.get(entry.getKey()));
        }
    }

    private static void restoreContents(Inventory inventory, List<ItemStack> contents) {
        if (contents == null) {
            return;
        }
        for (int slot = 0; slot < Math.min(contents.size(), inventory.getSize()); slot++) {
            ItemStack stack = contents.get(slot);
            if (stack != null && !stack.getType().isAir()) {
                inventory.setItem(slot, stack.clone());
            }
        }
    }

    public Map<String, Boolean> openPartStates() {
        return rig.openStates();
    }

    public void restoreOpenPartStates(Map<String, Boolean> states) {
        rig.restoreOpenStates(states);
        rig.update(location, renderWheelAngle, frontWheelRotation, rearWheelRotation,
                0.0F, 0.0F, false, age);
    }

    private Location chestRuntimeLocation() {
        LandVehicleSpec.Point offset = spec.mopedParts().chestInteractionOffset();
        return local(location, offset.x(), offset.y(), offset.z());
    }

    private Location compartmentLocation(LandVehicleSpec.StorageCompartment compartment) {
        LandVehicleSpec.Point min = compartment.interactionBox().min();
        LandVehicleSpec.Point max = compartment.interactionBox().max();
        return local(location, (min.x() + max.x()) * 0.5D,
                (min.y() + max.y()) * 0.5D, (min.z() + max.z()) * 0.5D);
    }

    private boolean aimingAtMopedStorage(Player player) {
        Location eye = player.getEyeLocation();
        if (eye.getWorld() == null || !eye.getWorld().equals(location.getWorld())) {
            return false;
        }
        Vector direction = eye.getDirection().normalize();
        Vector toChest = chestRuntimeLocation().toVector().subtract(eye.toVector());
        double distanceAlongRay = toChest.dot(direction);
        if (distanceAlongRay < 0.0D || distanceAlongRay > 6.0D) {
            return false;
        }
        Vector closest = eye.toVector().add(direction.multiply(distanceAlongRay));
        return closest.distanceSquared(chestRuntimeLocation().toVector()) <= 0.36D;
    }

    private void closeStorageViewers() {
        List<Inventory> inventories = new ArrayList<>(compartmentInventories.values());
        if (storageInventory != null) {
            inventories.add(storageInventory);
        }
        for (Inventory inventory : inventories) {
            for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
    }

    private record SpecialTarget(LandVehicleSpec.StorageCompartment compartment,
                                 LandVehicleSpec.Openable openable, double distance) {
    }

    private record WaterStatus(WaterVehiclePhysics.State state, double waterLevel) {
    }

    public boolean mount(Player player) {
        return !transported && rig.mount(player);
    }

    public Optional<Player> driver() {
        List<Entity> passengers = rig.driverSeat().getPassengers();
        if (passengers.isEmpty() || !(passengers.getFirst() instanceof Player player)) {
            return Optional.empty();
        }
        return Optional.of(player);
    }

    public void remove() {
        closeStorageViewers();
        soundController.stop(location);
        rig.remove();
    }

    public Location towBarLocation() {
        LandVehicleSpec.Point tow = spec.towBarPhysicsOffset();
        return local(location, tow.x(), tow.y(), tow.z());
    }

    public void placeOnTrailer(Location trailerLocation) {
        LandVehicleSpec.Point offset = spec.trailerOffset();
        Location carried = local(trailerLocation, offset.x(), 0.5D + offset.y(), offset.z());
        carried.setYaw(trailerLocation.getYaw());
        this.location = carried;
        this.velocity.zero();
        this.motion.zero();
        this.verticalVelocity = 0.0D;
        this.waterSpeed = 0.0F;
        this.transported = true;
        rig.update(location, 0.0F, frontWheelRotation, rearWheelRotation,
                0.0F, 0.0F, false, age);
    }

    public void releaseFromTrailer(Location releaseLocation) {
        this.location = releaseLocation.clone();
        this.location.setPitch(0.0F);
        this.transported = false;
        this.onGround = false;
        rig.update(location, 0.0F, frontWheelRotation, rearWheelRotation,
                0.0F, 0.0F, false, age);
    }

    public boolean transported() {
        return transported;
    }

    public boolean occupied() {
        return rig.seatCarriers().stream().anyMatch(seat -> !seat.getPassengers().isEmpty());
    }

    public UUID id() {
        return id;
    }

    public Location location() {
        return location.clone();
    }

    public LandVehicleRig rig() {
        return rig;
    }

    public LandVehicleSpec spec() {
        return spec;
    }

    public float fuel() {
        return fuel;
    }

    public void setFuel(float fuel) {
        this.fuel = clamp(fuel, 0.0F, spec.energyCapacity());
        updateSeatGauge();
    }

    private void updateSeatGauge() {
        float capacity = Math.max(1.0F, spec.energyCapacity());
        rig.setSeatGauge(fuel / capacity);
    }

    /** Vanilla replacement for the original mod's continuously updated fuel overlay. */
    private void showFuelOverlay() {
        Component overlay = Component.text(FuelGauge.overlay(fuel, spec.energyCapacity()), NamedTextColor.GOLD);
        for (Entity carrier : rig.seatCarriers()) {
            for (Entity passenger : carrier.getPassengers()) {
                if (passenger instanceof Player player) {
                    player.sendActionBar(overlay);
                }
            }
        }
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

    public boolean resting() {
        if (spec.motionType() == LandVehicleSpec.MotionType.WATER) {
            boolean supported = onGround || waterState == WaterVehiclePhysics.State.IN_WATER;
            return driver().isEmpty() && supported && Math.abs(waterSpeed) < 0.001F
                    && velocity.lengthSquared() < 1.0E-6D && motion.lengthSquared() < 1.0E-6D;
        }
        return driver().isEmpty() && onGround && verticalVelocity == 0.0D
                && velocity.lengthSquared() < 1.0E-8D && !boosting && wheelieCount == 0;
    }

    public double verticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(double verticalVelocity) {
        this.verticalVelocity = verticalVelocity;
        if (spec.motionType() == LandVehicleSpec.MotionType.WATER) {
            this.velocity.setY(verticalVelocity);
        }
    }

    private WaterStatus waterStatus() {
        World world = location.getWorld();
        if (world == null) {
            return new WaterStatus(WaterVehiclePhysics.State.IN_AIR, Double.MIN_VALUE);
        }
        double halfWidth = spec.entityWidth() * 0.5D;
        int minX = floor(location.getX() - halfWidth);
        int maxX = (int) Math.ceil(location.getX() + halfWidth);
        int minZ = floor(location.getZ() - halfWidth);
        int maxZ = (int) Math.ceil(location.getZ() + halfWidth);

        double top = location.getY() + spec.entityHeight() + 0.001D;
        boolean underSourceWater = false;
        for (int x = minX; x < maxX; x++) {
            for (int y = floor(location.getY() + spec.entityHeight()); y < Math.ceil(top); y++) {
                for (int z = minZ; z < maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    double surface = fluidSurface(block);
                    if (Double.isNaN(surface) || top >= surface) {
                        continue;
                    }
                    if (!sourceWater(block)) {
                        return new WaterStatus(WaterVehiclePhysics.State.UNDER_FLOWING_WATER, surface);
                    }
                    underSourceWater = true;
                }
            }
        }
        if (underSourceWater) {
            return new WaterStatus(WaterVehiclePhysics.State.UNDER_WATER, top);
        }

        double waterLevel = Double.MIN_VALUE;
        boolean inWater = false;
        int bottomY = floor(location.getY());
        int bottomMaxY = (int) Math.ceil(location.getY() + 0.001D);
        for (int x = minX; x < maxX; x++) {
            for (int y = bottomY; y < bottomMaxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    double surface = fluidSurface(world.getBlockAt(x, y, z));
                    if (!Double.isNaN(surface)) {
                        waterLevel = Math.max(waterLevel, surface);
                        inWater |= location.getY() < surface;
                    }
                }
            }
        }
        if (inWater) {
            return new WaterStatus(WaterVehiclePhysics.State.IN_WATER, waterLevel);
        }
        return new WaterStatus(onGround ? WaterVehiclePhysics.State.ON_LAND
                : WaterVehiclePhysics.State.IN_AIR, waterLevel);
    }

    private static double fluidSurface(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return block.getY() + 1.0D;
        }
        if (block.getType() == Material.BUBBLE_COLUMN) {
            return block.getY() + 1.0D;
        }
        if (block.getType() != Material.WATER) {
            return Double.NaN;
        }
        if (!(data instanceof Levelled levelled)) {
            return block.getY() + 1.0D;
        }
        int level = levelled.getLevel();
        double height = level == 0 ? 1.0D : level >= 8 ? 8.0D / 9.0D
                : (8.0D - level) / 9.0D;
        return block.getY() + height;
    }

    private static boolean sourceWater(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return true;
        }
        return block.getType() == Material.BUBBLE_COLUMN
                || block.getType() == Material.WATER
                && (!(data instanceof Levelled levelled) || levelled.getLevel() == 0);
    }

    private static double findSpawnY(Location location, LandVehicleSpec.MotionType motionType) {
        World world = location.getWorld();
        if (world == null) {
            return location.getY();
        }
        if (motionType == LandVehicleSpec.MotionType.WATER
                && (!Double.isNaN(fluidSurface(location.getBlock()))
                || !Double.isNaN(fluidSurface(location.clone().subtract(0.0D, 1.0D, 0.0D).getBlock())))) {
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
