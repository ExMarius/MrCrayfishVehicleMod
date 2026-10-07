package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
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
import java.util.List;
import java.util.UUID;

/** Multipart vanilla-display rig driven by a generated land-vehicle definition. */
public final class LandVehicleRig {
    public static final String ENTITY_TAG = "mcv_plugin_vehicle";
    private static final int VANILLA_ENTITY_LERP_TICKS = 3;
    /* A minimum-scale invisible horse gives vanilla clients their native mounted
     * player pose without adding a visible or colliding animal to the rig. */
    private static final double HORSE_CARRIER_SCALE = 0.0625D;
    private static final float HORSE_PASSENGER_OFFSET = 1.4F * (float) HORSE_CARRIER_SCALE;
    private static final double SEAT_GAUGE_MAX_HEALTH = 20.0D;

    private final UUID vehicleId;
    private final LandVehicleSpec spec;
    private final Interaction interaction;
    private final ItemDisplay body;
    private final ItemDisplay engine;
    private final ItemDisplay steering;
    private final ItemDisplay towBar;
    private final List<WheelDisplay> wheels;
    private final List<SeatCarrier> seats;
    private final List<Entity> entities;
    private float seatGauge = 1.0F;
    private boolean removed;

    private LandVehicleRig(UUID vehicleId, LandVehicleSpec spec, Interaction interaction,
                           ItemDisplay body, ItemDisplay engine, ItemDisplay steering, ItemDisplay towBar,
                           List<WheelDisplay> wheels, List<SeatCarrier> seats, List<Entity> entities) {
        this.vehicleId = vehicleId;
        this.spec = spec;
        this.interaction = interaction;
        this.body = body;
        this.engine = engine;
        this.steering = steering;
        this.towBar = towBar;
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
        LandVehicleSpec.Point tow = spec.towBarOffset();
        ItemDisplay towBar = spec.canTowTrailers()
                ? partDisplay(world, location, new LandVehicleSpec.Part("tow_bar",
                new LandVehicleSpec.Point(
                        tow.x() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                        0.5F + tow.y() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                        tow.z() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT),
                1.0F, 0.0F, 180.0F, 0.0F), vehicleId, all)
                : null;

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

        LandVehicleRig rig = new LandVehicleRig(vehicleId, spec, interaction, body, engine, steering, towBar,
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

    private static Horse horseCarrier(World world, Location location, UUID vehicleId) {
        Horse horse = world.spawn(location, Horse.class, carrier -> {
            carrier.setAdult();
            carrier.setTamed(true);
            carrier.setDomestication(carrier.getMaxDomestication());
            carrier.setJumpStrength(0.0D);
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
                scale.setBaseValue(HORSE_CARRIER_SCALE);
            }
            AttributeInstance maximumHealth = carrier.getAttribute(Attribute.MAX_HEALTH);
            if (maximumHealth != null) {
                maximumHealth.setBaseValue(SEAT_GAUGE_MAX_HEALTH);
            }
            carrier.setHealth(SEAT_GAUGE_MAX_HEALTH);
        });
        mark(horse, vehicleId);
        return horse;
    }

    private Horse createHorse(SeatCarrier carrier) {
        if (removed || !carrier.anchor.isValid()) {
            return null;
        }
        Location location = carrier.anchor.getLocation();
        Horse horse = horseCarrier(location.getWorld(), location, vehicleId);
        if (!carrier.anchor.addPassenger(horse)) {
            horse.remove();
            return null;
        }
        carrier.horse = horse;
        entities.add(horse);
        applySeatGauge(horse);
        return horse;
    }

    private void discardHorse(SeatCarrier carrier) {
        Horse horse = carrier.horse;
        carrier.horse = null;
        if (horse != null) {
            entities.remove(horse);
            horse.remove();
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
        Quaternionf chassisRotation = new Quaternionf()
                .rotateZ(radians(bodyRoll))
                .rotateX(radians(wheelieAngle));
        Vector3f bodyOrigin = point(spec.bodyOrigin());
        Vector3f driverSeat = horseAnchor(chassis(driverSeatOffset(), wheelieAngle, bodyRoll));
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

        float steeringRotation = renderSteeringAngle / spec.maxSteeringAngle() * 25.0F;
        Quaternionf forkRotation = motorcycleSteering(steeringRotation);
        if (steering != null) {
            LandVehicleSpec.Part part = spec.steering();
            Vector3f center;
            Quaternionf rotation;
            if (spec.motorcycle() != null) {
                center = forkPoint(point(part.center()), bodyOrigin, forkRotation);
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

        if (towBar != null) {
            LandVehicleSpec.Point tow = spec.towBarOffset();
            Vector3f center = chassis(new Vector3f(
                    tow.x() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                    0.5F + tow.y() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                    tow.z() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT), wheelieAngle, bodyRoll);
            place(towBar, renderAnchor, relativeToSeat(center, driverSeat), yaw, chassisRotation,
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
                center = forkPoint(unsteeredCenter, bodyOrigin, forkRotation);
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
                Vector3f seatPoint = horseAnchor(chassis(
                        seatOffset(carrier.properties), wheelieAngle, bodyRoll));
                Location seatLocation = local(root, seatPoint);
                carrier.anchor.teleport(seatLocation, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                carrier.anchor.setRotation(yaw, 0.0F);
            }
            maintainHorse(carrier, yaw);
        }
    }

    private void maintainHorse(SeatCarrier carrier, float yaw) {
        Horse horse = carrier.horse;
        if (horse == null) {
            return;
        }
        if (!horse.isValid()) {
            UUID riderId = carrier.rider;
            entities.remove(horse);
            carrier.horse = null;
            if (!removed && riderId != null) {
                Player rider = Bukkit.getPlayer(riderId);
                if (rider != null && rider.isOnline() && !rider.isDead()) {
                    Horse replacement = createHorse(carrier);
                    if (replacement != null && replacement.addPassenger(rider)) {
                        carrier.rider = riderId;
                        replacement.setRotation(yaw, 0.0F);
                        return;
                    }
                    discardHorse(carrier);
                }
            }
            carrier.rider = null;
            return;
        }

        Player rider = horse.getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst().orElse(null);
        if (rider == null) {
            carrier.rider = null;
            discardHorse(carrier);
            return;
        }
        carrier.rider = rider.getUniqueId();
        horse.setRotation(yaw, 0.0F);
    }

    private Quaternionf motorcycleSteering(float steeringRotation) {
        LandVehicleSpec.Motorcycle motorcycle = spec.motorcycle();
        if (motorcycle == null) {
            return new Quaternionf();
        }
        return new Quaternionf()
                .rotateX(radians(motorcycle.steeringAxisTilt()))
                .rotateY(radians(steeringRotation))
                .rotateX(radians(-motorcycle.steeringAxisTilt()));
    }

    private Vector3f forkPoint(Vector3f unsteered, Vector3f bodyOrigin, Quaternionf forkRotation) {
        LandVehicleSpec.Motorcycle motorcycle = spec.motorcycle();
        if (motorcycle == null) {
            return new Vector3f(unsteered);
        }
        Vector3f pivot = new Vector3f(bodyOrigin).add(0.0F, 0.0F, motorcycle.steeringPivotZ());
        Vector3f relative = new Vector3f(unsteered).sub(pivot);
        forkRotation.transform(relative);
        return relative.add(pivot);
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

    private static Vector3f horseAnchor(Vector3f seatPoint) {
        return new Vector3f(seatPoint).sub(0.0F, HORSE_PASSENGER_OFFSET, 0.0F);
    }

    private Vector3f chassis(Vector3f point, float wheelieAngle, float bodyRoll) {
        Vector3f transformed = new Vector3f(point);
        if (wheelieAngle != 0.0F) {
            Vector3f pivot = point(spec.wheeliePivot());
            transformed.sub(pivot);
            rotationX(wheelieAngle).transform(transformed);
            transformed.add(pivot);
        }
        if (bodyRoll != 0.0F) {
            new Quaternionf().rotateZ(radians(bodyRoll)).transform(transformed);
        }
        return transformed;
    }

    private static Vector3f relativeToSeat(Vector3f point, Vector3f seat) {
        return new Vector3f(point).sub(seat);
    }

    private static Vector3f point(LandVehicleSpec.Point point) {
        return new Vector3f(point.x(), point.y(), point.z());
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
        return root.clone().add(point.x * cos - point.z * sin, point.y, point.x * sin + point.z * cos);
    }

    public void setSeatGauge(float fraction) {
        seatGauge = Float.isFinite(fraction) ? Math.max(0.0F, Math.min(1.0F, fraction)) : 1.0F;
        for (SeatCarrier carrier : seats) {
            if (carrier.horse != null && carrier.horse.isValid()) {
                applySeatGauge(carrier.horse);
            }
        }
    }

    private void applySeatGauge(Horse horse) {
        double health = Math.max(1.0D, SEAT_GAUGE_MAX_HEALTH * seatGauge);
        if (Math.abs(horse.getHealth() - health) > 0.01D) {
            horse.setHealth(health);
        }
    }

    public void tickSeats(float yaw) {
        if (removed) {
            return;
        }
        for (SeatCarrier carrier : seats) {
            maintainHorse(carrier, yaw);
        }
    }

    public boolean mount(Player player) {
        if (removed || !valid()) {
            return false;
        }
        for (SeatCarrier carrier : seats) {
            Horse horse = carrier.horse;
            if (horse != null && horse.isValid() && !horse.getPassengers().isEmpty()) {
                continue;
            }
            if (horse != null) {
                discardHorse(carrier);
            }
            horse = createHorse(carrier);
            if (horse != null && horse.addPassenger(player)) {
                carrier.rider = player.getUniqueId();
                return true;
            }
            discardHorse(carrier);
        }
        return false;
    }

    public Entity driverSeat() {
        SeatCarrier driver = seats.stream().filter(seat -> seat.properties.driver())
                .findFirst().orElseThrow();
        return driver.horse != null && driver.horse.isValid() ? driver.horse : driver.anchor;
    }

    public List<Entity> seatCarriers() {
        return seats.stream()
                .map(SeatCarrier::horse)
                .filter(java.util.Objects::nonNull)
                .filter(Entity::isValid)
                .map(Entity.class::cast)
                .toList();
    }

    public List<Entity> entities() {
        return Collections.unmodifiableList(entities);
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public boolean valid() {
        return interaction.isValid() && body.isValid()
                && (engine == null || engine.isValid())
                && (steering == null || steering.isValid())
                && (towBar == null || towBar.isValid())
                && wheels.stream().allMatch(wheel -> wheel.entity.isValid())
                && seats.stream().allMatch(seat -> seat.anchor.isValid());
    }

    public void remove() {
        removed = true;
        /* Never invalidate a minimum-scale Horse while it still has a player.
         * Paper's next ride tick may otherwise loop inside
         * AbstractHorse#getDismountLocationInDirection. Restore an ordinary
         * bounding box and detach synchronously before removing the rig. */
        for (SeatCarrier carrier : seats) {
            Horse horse = carrier.horse;
            if (horse == null || !horse.isValid() || horse.getPassengers().isEmpty()) {
                continue;
            }
            AttributeInstance scale = horse.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(1.0D);
            }
            horse.eject();
            if (!horse.getPassengers().isEmpty()) {
                /* Failing open is safer than deleting a still-ridden carrier. */
                entities.remove(horse);
                horse.setInvisible(false);
                horse.setInvulnerable(false);
                horse.setGravity(true);
            }
        }
        for (Entity entity : new ArrayList<>(entities)) {
            entity.remove();
        }
        entities.clear();
        for (SeatCarrier carrier : seats) {
            carrier.horse = null;
            carrier.rider = null;
        }
    }

    private record WheelDisplay(ItemDisplay entity, LandVehicleSpec.Wheel properties) {
    }

    private static final class SeatCarrier {
        private final ItemDisplay anchor;
        private final LandVehicleSpec.Seat properties;
        private Horse horse;
        private UUID rider;

        private SeatCarrier(ItemDisplay anchor, LandVehicleSpec.Seat properties) {
            this.anchor = anchor;
            this.properties = properties;
        }

        private Horse horse() {
            return horse;
        }
    }
}
