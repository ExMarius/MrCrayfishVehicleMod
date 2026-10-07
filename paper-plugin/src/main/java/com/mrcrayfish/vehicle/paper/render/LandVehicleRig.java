package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.vehicle.FuelGauge;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Multipart vanilla-display rig driven by a generated land-vehicle definition. */
public final class LandVehicleRig {
    public static final String ENTITY_TAG = "mcv_plugin_vehicle";
    private static final int VANILLA_ENTITY_LERP_TICKS = 3;
    /* A minimum-scale invisible living carrier gives vanilla clients the same
     * native seated rider pose and mount-heart HUD without using AbstractHorse's
     * unsafe dismount-location search. */
    private static final double SEAT_CARRIER_SCALE = 0.0625D;
    private static final float PIG_PASSENGER_OFFSET = 0.7F * (float) SEAT_CARRIER_SCALE;
    private static final double SEAT_GAUGE_MAX_HEALTH = FuelGauge.MAX_HALF_HEARTS;

    private final UUID vehicleId;
    private final LandVehicleSpec spec;
    private final Interaction interaction;
    private final ItemDisplay body;
    private final ItemDisplay engine;
    private final ItemDisplay steering;
    private final ItemDisplay fuelFiller;
    private final ItemDisplay towBar;
    private final List<PartDisplay> chassisParts;
    private final List<PartDisplay> forkParts;
    private final ItemDisplay storageChest;
    private final Interaction storageInteraction;
    private final List<WheelDisplay> wheels;
    private final List<SeatCarrier> seats;
    private final List<Entity> entities;
    private float seatGauge = 1.0F;
    private boolean removed;

    private LandVehicleRig(UUID vehicleId, LandVehicleSpec spec, Interaction interaction,
                           ItemDisplay body, ItemDisplay engine, ItemDisplay steering,
                           ItemDisplay fuelFiller, ItemDisplay towBar,
                           List<PartDisplay> chassisParts, List<PartDisplay> forkParts,
                           ItemDisplay storageChest, Interaction storageInteraction,
                           List<WheelDisplay> wheels, List<SeatCarrier> seats, List<Entity> entities) {
        this.vehicleId = vehicleId;
        this.spec = spec;
        this.interaction = interaction;
        this.body = body;
        this.engine = engine;
        this.steering = steering;
        this.fuelFiller = fuelFiller;
        this.towBar = towBar;
        this.chassisParts = chassisParts;
        this.forkParts = forkParts;
        this.storageChest = storageChest;
        this.storageInteraction = storageInteraction;
        this.wheels = wheels;
        this.seats = seats;
        this.entities = entities;
    }

    public static LandVehicleRig spawn(UUID vehicleId, LandVehicleSpec spec, Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Cannot spawn a vehicle without a world");
        }

        List<Entity> all = new ArrayList<>();
        Interaction interaction = world.spawn(location, Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(Math.max(2.2F, spec.entityWidth()));
            hitbox.setInteractionHeight(Math.max(1.1F, spec.entityHeight()));
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
        });
        mark(interaction, vehicleId);
        all.add(interaction);

        ItemDisplay body = display(world, location, model(spec.bodyModel()));
        mark(body, vehicleId);
        all.add(body);

        ItemDisplay engine = partDisplay(world, location, spec.engine(), vehicleId, all);
        ItemDisplay steering = partDisplay(world, location, spec.steering(), vehicleId, all);
        ItemDisplay fuelFiller = partDisplay(world, location, spec.fuelFiller(), vehicleId, all);
        ItemDisplay towBar = spec.canTowTrailers()
                ? partDisplay(world, location, new LandVehicleSpec.Part(spec.towBarModel(),
                spec.towBarVisualCenter(), 1.0F, 0.0F, 180.0F, 0.0F), vehicleId, all)
                : null;

        List<PartDisplay> chassisParts = new ArrayList<>();
        for (LandVehicleSpec.Part part : spec.bodyParts()) {
            chassisParts.add(new PartDisplay(
                    partDisplay(world, location, part, vehicleId, all), part));
        }
        List<PartDisplay> forkParts = new ArrayList<>();
        ItemDisplay storageChest = null;
        Interaction storageInteraction = null;
        if (spec.mopedParts() != null) {
            for (LandVehicleSpec.Part part : spec.mopedParts().forkParts()) {
                forkParts.add(new PartDisplay(
                        partDisplay(world, location, part, vehicleId, all), part));
            }
            storageChest = display(world, location, new ItemStack(Material.CHEST));
            storageChest.setVisibleByDefault(false);
            mark(storageChest, vehicleId);
            all.add(storageChest);

            storageInteraction = world.spawn(location, Interaction.class, hitbox -> {
                hitbox.setInteractionWidth(0.8F);
                hitbox.setInteractionHeight(0.8F);
                hitbox.setResponsive(true);
                hitbox.setPersistent(false);
            });
            mark(storageInteraction, vehicleId);
            all.add(storageInteraction);
        }

        List<WheelDisplay> wheels = new ArrayList<>();
        for (LandVehicleSpec.Wheel properties : spec.wheels()) {
            ItemDisplay wheel = display(world, location, model("standard_wheel"));
            mark(wheel, vehicleId);
            all.add(wheel);
            wheels.add(new WheelDisplay(wheel, properties));
        }

        List<SeatCarrier> seats = new ArrayList<>();
        boolean bodyAssigned = false;
        for (LandVehicleSpec.Seat seat : spec.seats()) {
            ItemDisplay anchor;
            if (seat.driver() && !bodyAssigned) {
                anchor = body;
                bodyAssigned = true;
            } else {
                anchor = display(world, location, new ItemStack(Material.AIR));
                mark(anchor, vehicleId);
                all.add(anchor);
            }
            seats.add(new SeatCarrier(anchor, seat));
        }
        if (!bodyAssigned) {
            throw new IllegalArgumentException("Land vehicle " + spec.id() + " has no driver seat");
        }

        LandVehicleRig rig = new LandVehicleRig(vehicleId, spec, interaction, body, engine, steering,
                fuelFiller, towBar, Collections.unmodifiableList(chassisParts),
                Collections.unmodifiableList(forkParts), storageChest, storageInteraction,
                Collections.unmodifiableList(wheels), Collections.unmodifiableList(seats), all);
        rig.update(location, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0);
        return rig;
    }

    private static ItemDisplay partDisplay(World world, Location location, LandVehicleSpec.Part part,
                                           UUID vehicleId, List<Entity> all) {
        if (part == null) {
            return null;
        }
        ItemDisplay display = display(world, location, model(part.model()));
        mark(display, vehicleId);
        all.add(display);
        return display;
    }

    private static ItemDisplay display(World world, Location location, ItemStack stack) {
        return world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(VANILLA_ENTITY_LERP_TICKS);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setShadowRadius(0.0F);
            display.setShadowStrength(0.0F);
        });
    }

    private static Pig pigCarrier(World world, Location location, UUID vehicleId) {
        Pig pig = world.spawn(location, Pig.class, carrier -> {
            carrier.setAdult();
            carrier.setSaddle(true);
            carrier.setAI(false);
            carrier.setGravity(false);
            carrier.setCollidable(false);
            carrier.setInvisible(true);
            carrier.setInvulnerable(true);
            carrier.setSilent(true);
            carrier.setPersistent(false);
            carrier.setRemoveWhenFarAway(false);
            AttributeInstance scale = carrier.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(SEAT_CARRIER_SCALE);
            }
            AttributeInstance maximumHealth = carrier.getAttribute(Attribute.MAX_HEALTH);
            if (maximumHealth != null) {
                maximumHealth.setBaseValue(SEAT_GAUGE_MAX_HEALTH);
            }
            carrier.setHealth(SEAT_GAUGE_MAX_HEALTH);
        });
        mark(pig, vehicleId);
        return pig;
    }

    private Pig createPig(SeatCarrier carrier) {
        if (removed || !carrier.anchor.isValid()) {
            return null;
        }
        Location location = carrier.anchor.getLocation();
        Pig pig = pigCarrier(location.getWorld(), location, vehicleId);
        if (!carrier.anchor.addPassenger(pig)) {
            pig.remove();
            return null;
        }
        carrier.pig = pig;
        entities.add(pig);
        applySeatGauge(pig);
        return pig;
    }

    private void discardPig(SeatCarrier carrier) {
        Pig pig = carrier.pig;
        carrier.pig = null;
        if (pig != null) {
            entities.remove(pig);
            pig.remove();
        }
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
                       float rearWheelRotation, float wheelieAngle, float bodyRoll,
                       boolean engineRunning, int tickCount) {
        if (!valid()) {
            return;
        }

        float yaw = root.getYaw();
        Quaternionf chassisRotation = SourceTransforms.chassisRotation(wheelieAngle, bodyRoll);
        Vector3f bodyOrigin = point(spec.bodyOrigin());
        Vector3f driverSeat = pigAnchor(chassis(driverSeatOffset(), wheelieAngle, bodyRoll));
        Location renderAnchor = local(root, driverSeat);

        interaction.teleport(root);
        interaction.setRotation(yaw, 0.0F);
        if (tickCount % 5 == 0) {
            updateBrightness(root);
        }

        Vector3f bodyCenter = chassis(bodyOrigin, wheelieAngle, bodyRoll);
        place(body, renderAnchor, relativeToSeat(bodyCenter, driverSeat), yaw, chassisRotation,
                new Vector3f(spec.bodyScale()), new Quaternionf());

        if (engine != null) {
            LandVehicleSpec.Part part = spec.engine();
            Quaternionf shake = new Quaternionf();
            if (engineRunning && (tickCount & 1) == 1) {
                shake.rotateX(radians(0.5F)).rotateZ(radians(0.5F)).rotateY(radians(-0.5F));
            }
            Vector3f relative = point(part.center()).sub(bodyOrigin);
            shake.transform(relative);
            Vector3f center = chassis(new Vector3f(bodyOrigin).add(relative), wheelieAngle, bodyRoll);
            Quaternionf rotation = new Quaternionf(chassisRotation).mul(shake);
            Quaternionf sourceRotation = new Quaternionf()
                    .rotateX(radians(part.rotationX()))
                    .rotateY(radians(part.rotationY()))
                    .rotateZ(radians(part.rotationZ()));
            place(engine, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                    new Vector3f(part.scale()), sourceRotation);
        }

        float steeringRotation = renderSteeringAngle / spec.maxSteeringAngle()
                * spec.steeringVisualAngle();
        Quaternionf forkRotation = SourceTransforms.motorcycleSteering(spec.motorcycle(), steeringRotation);
        if (steering != null) {
            LandVehicleSpec.Part part = spec.steering();
            Vector3f center;
            Quaternionf rotation;
            if (spec.motorcycle() != null) {
                center = SourceTransforms.forkPoint(
                        point(part.center()), bodyOrigin, spec.motorcycle(), forkRotation);
                center = chassis(center, wheelieAngle, bodyRoll);
                rotation = new Quaternionf(chassisRotation).mul(forkRotation)
                        .rotateX(radians(part.rotationX()))
                        .rotateY(radians(part.rotationY()))
                        .rotateZ(radians(part.rotationZ()));
            } else {
                center = chassis(point(part.center()), wheelieAngle, bodyRoll);
                rotation = new Quaternionf(chassisRotation)
                        .rotateX(radians(part.rotationX()))
                        .rotateY(radians(part.rotationY() + steeringRotation))
                        .rotateZ(radians(part.rotationZ()));
            }
            place(steering, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                    new Vector3f(part.scale()), new Quaternionf());
        }

        for (PartDisplay partDisplay : chassisParts) {
            placePropertyPart(partDisplay, renderAnchor, driverSeat,
                    yaw, wheelieAngle, bodyRoll, chassisRotation);
        }

        for (PartDisplay partDisplay : forkParts) {
            LandVehicleSpec.Part part = partDisplay.properties;
            Vector3f center = SourceTransforms.forkPoint(
                    point(part.center()), bodyOrigin, spec.motorcycle(), forkRotation);
            center = chassis(center, wheelieAngle, bodyRoll);
            Quaternionf rotation = new Quaternionf(chassisRotation).mul(forkRotation);
            Quaternionf sourceRotation = new Quaternionf()
                    .rotateX(radians(part.rotationX()))
                    .rotateY(radians(part.rotationY()))
                    .rotateZ(radians(part.rotationZ()));
            place(partDisplay.entity, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                    new Vector3f(part.scale()), sourceRotation);
        }

        if (storageChest != null && spec.mopedParts() != null) {
            placePropertyPart(storageChest, spec.mopedParts().chest(), renderAnchor, driverSeat,
                    yaw, wheelieAngle, bodyRoll, chassisRotation);
            Vector3f interactionPoint = chassis(point(spec.mopedParts().chest().center()),
                    wheelieAngle, bodyRoll);
            storageInteraction.teleport(local(root, interactionPoint));
            storageInteraction.setRotation(yaw, 0.0F);
        }

        placePropertyPart(fuelFiller, spec.fuelFiller(), renderAnchor, driverSeat,
                yaw, wheelieAngle, bodyRoll, chassisRotation);

        if (towBar != null) {
            /* AbstractLandVehicleRenderer renders the tow bar before applying the
             * axle/wheel translations and wheelie matrix. Keep its root-space
             * center and orientation fixed while the chassis wheelies. */
            Vector3f center = point(spec.towBarVisualCenter());
            place(towBar, renderAnchor, relativeToSeat(center, driverSeat), yaw, new Quaternionf(),
                    new Vector3f(1.0F), new Quaternionf().rotateY((float) Math.PI));
        }

        for (WheelDisplay wheelDisplay : wheels) {
            LandVehicleSpec.Wheel wheel = wheelDisplay.properties;
            float spin = wheel.front() ? frontWheelRotation : rearWheelRotation;
            Vector3f center;
            Quaternionf rotation;
            Quaternionf sourceRotation;
            if (wheel.front() && spec.motorcycle() != null) {
                Vector3f unsteeredCenter = new Vector3f(wheel.axleX(), wheel.centerY(), wheel.axleZ());
                center = SourceTransforms.forkPoint(
                        unsteeredCenter, bodyOrigin, spec.motorcycle(), forkRotation);
                center = chassis(center, wheelieAngle, bodyRoll);
                rotation = new Quaternionf(chassisRotation).mul(forkRotation)
                        .rotateX(radians(-spin));
                sourceRotation = spec.motorcycle().frontWheelYaw180()
                        ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf();
            } else {
                float steeringAngle = wheel.front() ? renderSteeringAngle : 0.0F;
                float steerRadians = radians(steeringAngle);
                center = new Vector3f(
                        wheel.axleX() + wheel.halfWidthOffset() * (float) Math.cos(steerRadians),
                        wheel.centerY(),
                        wheel.axleZ() - wheel.halfWidthOffset() * (float) Math.sin(steerRadians)
                );
                center = chassis(center, wheelieAngle, bodyRoll);
                rotation = new Quaternionf(chassisRotation)
                        .rotateY(steerRadians)
                        .rotateX(radians(-spin));
                sourceRotation = wheel.side() > 0
                        ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf();
            }
            place(wheelDisplay.entity, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                    new Vector3f(wheel.scaleX(), wheel.scaleY(), wheel.scaleZ()), sourceRotation);
        }

        for (SeatCarrier carrier : seats) {
            if (carrier.anchor != body) {
                Vector3f seatPoint = pigAnchor(chassis(
                        seatOffset(carrier.properties), wheelieAngle, bodyRoll));
                Location seatLocation = local(root, seatPoint);
                carrier.anchor.teleport(seatLocation, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                carrier.anchor.setRotation(yaw + carrier.properties.yawOffset(), 0.0F);
            }
            maintainPig(carrier, yaw);
        }
    }

    private void placePropertyPart(PartDisplay partDisplay,
                                   Location renderAnchor, Vector3f driverSeat, float yaw,
                                   float wheelieAngle, float bodyRoll, Quaternionf chassisRotation) {
        LandVehicleSpec.Part part = partDisplay.properties;
        Vector3f center = chassis(point(part.center()), wheelieAngle, bodyRoll);
        Quaternionf rotation = new Quaternionf(chassisRotation);
        if (part.openable() != null) {
            float angle = radians(partDisplay.openAngle());
            switch (part.openable().axis()) {
                case X -> rotation.rotateX(angle);
                case Y -> rotation.rotateY(angle);
                case Z -> rotation.rotateZ(angle);
            }
        }
        Quaternionf sourceRotation = new Quaternionf()
                .rotateX(radians(part.rotationX()))
                .rotateY(radians(part.rotationY()))
                .rotateZ(radians(part.rotationZ()));
        place(partDisplay.entity, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                new Vector3f(part.scale()), sourceRotation);
    }

    private void placePropertyPart(ItemDisplay display, LandVehicleSpec.Part part,
                                   Location renderAnchor, Vector3f driverSeat, float yaw,
                                   float wheelieAngle, float bodyRoll, Quaternionf chassisRotation) {
        if (display == null || part == null) {
            return;
        }
        Vector3f center = chassis(point(part.center()), wheelieAngle, bodyRoll);
        Quaternionf sourceRotation = new Quaternionf()
                .rotateX(radians(part.rotationX()))
                .rotateY(radians(part.rotationY()))
                .rotateZ(radians(part.rotationZ()));
        place(display, renderAnchor, relativeToSeat(center, driverSeat), yaw, chassisRotation,
                new Vector3f(part.scale()), sourceRotation);
    }

    private void maintainPig(SeatCarrier carrier, float yaw) {
        float seatYaw = yaw + carrier.properties.yawOffset();
        Pig pig = carrier.pig;
        if (pig == null) {
            return;
        }
        if (!pig.isValid()) {
            UUID riderId = carrier.rider;
            entities.remove(pig);
            carrier.pig = null;
            if (!removed && riderId != null) {
                Player rider = Bukkit.getPlayer(riderId);
                if (rider != null && rider.isOnline() && !rider.isDead()) {
                    Pig replacement = createPig(carrier);
                    if (replacement != null && replacement.addPassenger(rider)) {
                        carrier.rider = riderId;
                        replacement.setRotation(seatYaw, 0.0F);
                        return;
                    }
                    discardPig(carrier);
                }
            }
            carrier.rider = null;
            return;
        }

        Player rider = pig.getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst().orElse(null);
        if (rider == null) {
            carrier.rider = null;
            discardPig(carrier);
            return;
        }
        carrier.rider = rider.getUniqueId();
        pig.setRotation(seatYaw, 0.0F);
    }

    private void place(ItemDisplay display, Location renderAnchor, Vector3f translation, float yaw,
                       Quaternionf leftRotation, Vector3f scale, Quaternionf sourceRightRotation) {
        display.teleport(renderAnchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        display.setRotation(yaw, 0.0F);
        Quaternionf itemDisplayCompensation = new Quaternionf(sourceRightRotation).rotateY((float) Math.PI);
        display.setTransformation(new Transformation(translation, leftRotation, scale, itemDisplayCompensation));
    }

    public void refreshBrightness(Location root) {
        if (valid()) {
            updateBrightness(root);
        }
    }

    private void updateBrightness(Location root) {
        Block sample = root.clone().add(0.0D, spec.entityHeight() + 0.01D, 0.0D).getBlock();
        Display.Brightness brightness = new Display.Brightness(sample.getLightFromBlocks(), sample.getLightFromSky());
        for (Entity entity : entities) {
            if (entity instanceof ItemDisplay display && display.getType() != null
                    && !brightness.equals(display.getBrightness())) {
                display.setBrightness(brightness);
            }
        }
    }

    private Vector3f driverSeatOffset() {
        LandVehicleSpec.Seat seat = spec.seats().stream().filter(LandVehicleSpec.Seat::driver)
                .findFirst().orElseThrow();
        return seatOffset(seat);
    }

    private static Vector3f seatOffset(LandVehicleSpec.Seat seat) {
        return point(seat.sourceOffset()).add(0.0F, LandVehicleSpec.RIDER_HEIGHT_CORRECTION, 0.0F);
    }

    private static Vector3f pigAnchor(Vector3f seatPoint) {
        return new Vector3f(seatPoint).sub(0.0F, PIG_PASSENGER_OFFSET, 0.0F);
    }

    private Vector3f chassis(Vector3f sourcePoint, float wheelieAngle, float bodyRoll) {
        return SourceTransforms.chassisPoint(
                sourcePoint, point(spec.wheeliePivot()), wheelieAngle, bodyRoll);
    }

    private static Vector3f relativeToSeat(Vector3f point, Vector3f seat) {
        return new Vector3f(point).sub(seat);
    }

    private static Vector3f point(LandVehicleSpec.Point point) {
        return new Vector3f(point.x(), point.y(), point.z());
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static Location local(Location root, Vector3f point) {
        double radians = Math.toRadians(root.getYaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return root.clone().add(point.x * cos - point.z * sin, point.y, point.x * sin + point.z * cos);
    }

    public void setSeatGauge(float fraction) {
        seatGauge = Float.isFinite(fraction) ? Math.max(0.0F, Math.min(1.0F, fraction)) : 1.0F;
        for (SeatCarrier carrier : seats) {
            if (carrier.pig != null && carrier.pig.isValid()) {
                applySeatGauge(carrier.pig);
            }
        }
    }

    private void applySeatGauge(Pig pig) {
        double health = FuelGauge.mountHealth(seatGauge);
        if (Math.abs(pig.getHealth() - health) > 0.01D) {
            pig.setHealth(health);
        }
    }

    public void tickSeats(float yaw) {
        if (removed) {
            return;
        }
        for (SeatCarrier carrier : seats) {
            maintainPig(carrier, yaw);
        }
    }

    public boolean mount(Player player) {
        if (removed || !valid()) {
            return false;
        }

        /* VehicleEntity/SeatTracker selects the closest available source seat,
         * rather than the first seat in declaration order. This is particularly
         * important for the Off Roader: approaching its rear selects one of the
         * two hanging rear positions without requiring the front seats to fill. */
        SeatCarrier closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        double playerHalfHeight = player.getBoundingBox().getHeight() * 0.5D;
        Location playerLocation = player.getLocation();
        for (SeatCarrier carrier : seats) {
            Pig pig = carrier.pig;
            if (pig != null && pig.isValid() && !pig.getPassengers().isEmpty()) {
                continue;
            }
            Location seatLocation = carrier.anchor.getLocation();
            if (!seatLocation.getWorld().equals(playerLocation.getWorld())) {
                continue;
            }
            /* SeatTracker compares the player's feet against seatY minus half
             * the player's bounding-box height. Preserve that source equation. */
            seatLocation.subtract(0.0D, playerHalfHeight, 0.0D);
            double distance = seatLocation.distanceSquared(playerLocation);
            if (distance < closestDistance) {
                closest = carrier;
                closestDistance = distance;
            }
        }
        if (closest == null) {
            return false;
        }

        if (closest.pig != null) {
            discardPig(closest);
        }
        Pig pig = createPig(closest);
        if (pig != null && pig.addPassenger(player)) {
            closest.rider = player.getUniqueId();
            return true;
        }
        discardPig(closest);
        return false;
    }

    public Entity driverSeat() {
        SeatCarrier driver = seats.stream().filter(seat -> seat.properties.driver())
                .findFirst().orElseThrow();
        return driver.pig != null && driver.pig.isValid() ? driver.pig : driver.anchor;
    }

    public List<Entity> seatCarriers() {
        return seats.stream()
                .map(SeatCarrier::pig)
                .filter(java.util.Objects::nonNull)
                .filter(Entity::isValid)
                .map(Entity.class::cast)
                .toList();
    }

    public List<Entity> entities() {
        return Collections.unmodifiableList(entities);
    }

    /** Advances source OpenableAction animations. Closing sounds fire when the part reaches zero. */
    public boolean tickOpenables(Location root) {
        boolean changed = false;
        for (PartDisplay part : chassisParts) {
            if (part.tick()) {
                changed = true;
                if (!part.open && part.animationTick == 0) {
                    playCustomSound(root, part.properties.openable().closeSound());
                }
            }
        }
        return changed;
    }

    public boolean toggleOpenable(String id, Location root) {
        for (PartDisplay part : chassisParts) {
            LandVehicleSpec.Openable openable = part.properties.openable();
            if (openable == null || !openable.id().equals(id)) {
                continue;
            }
            part.open = !part.open;
            if (part.open) {
                playCustomSound(root, openable.openSound());
            }
            return true;
        }
        return false;
    }

    public float openAngle(String id) {
        for (PartDisplay part : chassisParts) {
            LandVehicleSpec.Openable openable = part.properties.openable();
            if (openable != null && openable.id().equals(id)) {
                return part.openAngle();
            }
        }
        return 0.0F;
    }

    public Map<String, Boolean> openStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (PartDisplay part : chassisParts) {
            if (part.properties.openable() != null) {
                states.put(part.properties.openable().id(), part.open);
            }
        }
        return states;
    }

    public void restoreOpenStates(Map<String, Boolean> states) {
        for (PartDisplay part : chassisParts) {
            LandVehicleSpec.Openable openable = part.properties.openable();
            if (openable == null) {
                continue;
            }
            part.open = states != null && Boolean.TRUE.equals(states.get(openable.id()));
            part.animationTick = part.open ? openable.animationLength() : 0;
        }
    }

    private static void playCustomSound(Location root, String sound) {
        if (root.getWorld() != null && sound != null && !sound.isBlank()) {
            float pitch = 0.8F + 0.2F * ThreadLocalRandom.current().nextFloat();
            root.getWorld().playSound(root, sound, SoundCategory.NEUTRAL, 1.0F, pitch);
        }
    }

    public boolean isStorageInteraction(Entity entity) {
        return storageInteraction != null && storageInteraction.getUniqueId().equals(entity.getUniqueId());
    }

    public void setStorageChestAttached(boolean attached) {
        if (storageChest != null && storageChest.isValid()) {
            storageChest.setVisibleByDefault(attached);
        }
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public boolean valid() {
        return interaction.isValid() && body.isValid()
                && (engine == null || engine.isValid())
                && (steering == null || steering.isValid())
                && (fuelFiller == null || fuelFiller.isValid())
                && (towBar == null || towBar.isValid())
                && chassisParts.stream().allMatch(part -> part.entity.isValid())
                && forkParts.stream().allMatch(part -> part.entity.isValid())
                && (storageChest == null || storageChest.isValid())
                && (storageInteraction == null || storageInteraction.isValid())
                && wheels.stream().allMatch(wheel -> wheel.entity.isValid())
                && seats.stream().allMatch(seat -> seat.anchor.isValid());
    }

    public void remove() {
        removed = true;
        /* Detach riders synchronously while the living carrier is still valid.
         * Restoring an ordinary bounding box also keeps the fallback dismount
         * location independent of the carrier's minimum render scale. */
        for (SeatCarrier carrier : seats) {
            Pig pig = carrier.pig;
            if (pig == null || !pig.isValid() || pig.getPassengers().isEmpty()) {
                continue;
            }
            AttributeInstance scale = pig.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(1.0D);
            }
            pig.eject();
            if (!pig.getPassengers().isEmpty()) {
                /* Failing open is safer than deleting a still-ridden carrier. */
                entities.remove(pig);
                pig.setInvisible(false);
                pig.setInvulnerable(false);
                pig.setGravity(true);
            }
        }
        for (Entity entity : new ArrayList<>(entities)) {
            entity.remove();
        }
        entities.clear();
        for (SeatCarrier carrier : seats) {
            carrier.pig = null;
            carrier.rider = null;
        }
    }

    private record WheelDisplay(ItemDisplay entity, LandVehicleSpec.Wheel properties) {
    }

    private static final class PartDisplay {
        private final ItemDisplay entity;
        private final LandVehicleSpec.Part properties;
        private boolean open;
        private int animationTick;

        private PartDisplay(ItemDisplay entity, LandVehicleSpec.Part properties) {
            this.entity = entity;
            this.properties = properties;
        }

        private boolean tick() {
            LandVehicleSpec.Openable openable = properties.openable();
            if (openable == null) {
                return false;
            }
            int previous = animationTick;
            if (open && animationTick < openable.animationLength()) {
                animationTick++;
            } else if (!open && animationTick > 0) {
                animationTick--;
            }
            return previous != animationTick;
        }

        private float openAngle() {
            LandVehicleSpec.Openable openable = properties.openable();
            if (openable == null || animationTick == 0) {
                return 0.0F;
            }
            double progress = animationTick / (double) openable.animationLength();
            return (float) (openable.angle() * easeOutBack(progress));
        }
    }

    static double easeOutBack(double progress) {
        double c1 = 1.70158D;
        double c3 = c1 + 1.0D;
        return 1.0D + c3 * Math.pow(progress - 1.0D, 3.0D)
                + c1 * Math.pow(progress - 1.0D, 2.0D);
    }

    private static final class SeatCarrier {
        private final ItemDisplay anchor;
        private final LandVehicleSpec.Seat properties;
        private Pig pig;
        private UUID rider;

        private SeatCarrier(ItemDisplay anchor, LandVehicleSpec.Seat properties) {
            this.anchor = anchor;
            this.properties = properties;
        }

        private Pig pig() {
            return pig;
        }
    }
}
