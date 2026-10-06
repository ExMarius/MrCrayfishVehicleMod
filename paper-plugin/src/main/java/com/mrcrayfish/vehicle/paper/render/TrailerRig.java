package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.TrailerSpec;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
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

/** Multipart display rig for a source-compatible towable trailer. */
public final class TrailerRig {
    private static final int VANILLA_ENTITY_LERP_TICKS = 3;

    private final UUID trailerId;
    private final TrailerSpec spec;
    private final Interaction interaction;
    private final ItemDisplay body;
    private final List<WheelDisplay> wheels;
    private final List<ExtraDisplay> extras;
    private final BlockDisplay fluid;
    private final List<Entity> entities;

    private TrailerRig(UUID trailerId, TrailerSpec spec, Interaction interaction, ItemDisplay body,
                       List<WheelDisplay> wheels, List<ExtraDisplay> extras,
                       BlockDisplay fluid, List<Entity> entities) {
        this.trailerId = trailerId;
        this.spec = spec;
        this.interaction = interaction;
        this.body = body;
        this.wheels = wheels;
        this.extras = extras;
        this.fluid = fluid;
        this.entities = entities;
    }

    public static TrailerRig spawn(UUID trailerId, TrailerSpec spec, Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Cannot spawn a trailer without a world");
        }
        List<Entity> all = new ArrayList<>();
        Interaction interaction = world.spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(Math.max(1.5F, spec.entityWidth()));
            entity.setInteractionHeight(Math.max(1.0F, spec.entityHeight()));
            entity.setResponsive(true);
            entity.setPersistent(false);
        });
        mark(interaction, trailerId);
        all.add(interaction);

        ItemDisplay body = display(world, location, model(spec.bodyModel()));
        mark(body, trailerId);
        all.add(body);

        List<WheelDisplay> wheels = new ArrayList<>();
        for (int side : new int[]{-1, 1}) {
            ItemDisplay wheel = display(world, location, model("standard_wheel"));
            mark(wheel, trailerId);
            all.add(wheel);
            wheels.add(new WheelDisplay(wheel, side));
        }

        List<ExtraDisplay> extras = new ArrayList<>();
        if (spec.kind() == TrailerSpec.Kind.FERTILIZER) {
            extra(world, location, trailerId, all, extras, "seed_spiker",
                    new Vector3f(0.0F, -0.5F * spec.bodyScale(), -0.4375F * spec.bodyScale()),
                    new Vector3f(1.25F * spec.bodyScale()), true);
        } else if (spec.kind() == TrailerSpec.Kind.SEEDER) {
            for (int x = -12; x <= 12; x += 4) {
                extra(world, location, trailerId, all, extras, "seed_spiker",
                        new Vector3f(x * LandVehicleSpec.MODEL_UNIT * spec.bodyScale(),
                                -0.65F * spec.bodyScale(), 0.0F),
                        new Vector3f(0.75F * spec.bodyScale()), true);
            }
        } else if (spec.kind() == TrailerSpec.Kind.STORAGE) {
            ItemDisplay chest = display(world, location, new ItemStack(Material.CHEST));
            mark(chest, trailerId);
            all.add(chest);
            extras.add(new ExtraDisplay(chest,
                    new Vector3f(0.0F, -6.0F * LandVehicleSpec.MODEL_UNIT * spec.bodyScale(), 0.0F),
                    new Vector3f(0.9F * spec.bodyScale()), false));
        }

        BlockDisplay fluid = null;
        if (spec.kind() == TrailerSpec.Kind.FLUID) {
            fluid = world.spawn(location, BlockDisplay.class, display -> {
                display.setBlock(Material.WATER.createBlockData());
                configure(display);
            });
            mark(fluid, trailerId);
            all.add(fluid);
        }

        TrailerRig rig = new TrailerRig(trailerId, spec, interaction, body,
                Collections.unmodifiableList(wheels), Collections.unmodifiableList(extras), fluid,
                Collections.unmodifiableList(all));
        rig.update(location, 0.0F, null, 0);
        return rig;
    }

    private static void extra(World world, Location location, UUID id, List<Entity> all,
                              List<ExtraDisplay> extras, String model, Vector3f center,
                              Vector3f scale, boolean spins) {
        ItemDisplay display = display(world, location, model(model));
        mark(display, id);
        all.add(display);
        extras.add(new ExtraDisplay(display, center, scale, spins));
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
        display.setTeleportDuration(VANILLA_ENTITY_LERP_TICKS);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
    }

    private static ItemStack model(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", name));
        item.setItemMeta(meta);
        return item;
    }

    private static void mark(Entity entity, UUID id) {
        entity.addScoreboardTag(LandVehicleRig.ENTITY_TAG);
        entity.addScoreboardTag("mcv_" + id);
    }

    public void update(Location root, float wheelRotation, FluidVisual fluidVisual, int tickCount) {
        if (!valid()) {
            return;
        }
        float yaw = root.getYaw();
        interaction.teleport(root);
        interaction.setRotation(yaw, 0.0F);

        place(body, root, new Vector3f(0.0F, spec.bodyOriginY(), 0.0F), new Quaternionf(),
                new Vector3f(spec.bodyScale()), new Quaternionf());

        float wheelX = spec.wheelX() * LandVehicleSpec.MODEL_UNIT * spec.bodyScale();
        float wheelZ = spec.wheelZ() * LandVehicleSpec.MODEL_UNIT * spec.bodyScale();
        for (WheelDisplay wheel : wheels) {
            float halfWidth = wheel.side * 4.0F * 0.5F * LandVehicleSpec.MODEL_UNIT * spec.bodyScale();
            Vector3f center = new Vector3f(wheel.side * wheelX + halfWidth, spec.wheelCenterY(), wheelZ);
            Quaternionf spin = new Quaternionf().rotateX(radians(-wheelRotation));
            Quaternionf side = wheel.side > 0 ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf();
            place(wheel.entity, root, center, spin,
                    new Vector3f(spec.bodyScale(), spec.bodyScale() * 1.25F, spec.bodyScale() * 1.25F), side);
        }

        for (ExtraDisplay extra : extras) {
            Quaternionf rotation = extra.spins
                    ? new Quaternionf().rotateX(radians(-wheelRotation)) : new Quaternionf();
            place(extra.entity, root, extra.center, rotation, extra.scale, new Quaternionf());
        }

        if (fluid != null) {
            updateFluid(root, fluidVisual);
        }
        if (tickCount % 5 == 0) {
            updateBrightness(root);
        }
    }

    private void updateFluid(Location root, FluidVisual visual) {
        if (visual == null || visual.fraction() <= 0.0F) {
            fluid.setVisibleByDefault(false);
            return;
        }
        fluid.setVisibleByDefault(true);
        BlockData data = visual.material().createBlockData();
        if (!data.matches(fluid.getBlock())) {
            fluid.setBlock(data);
        }
        float height = 0.62F * Math.min(1.0F, visual.fraction());
        fluid.teleport(root, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        fluid.setRotation(root.getYaw(), 0.0F);
        fluid.setTransformation(new Transformation(
                new Vector3f(-0.38F, 0.18F, -0.82F), new Quaternionf(),
                new Vector3f(0.76F, height, 1.64F), new Quaternionf()));
    }

    private static void place(ItemDisplay display, Location root, Vector3f center,
                              Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {
        display.teleport(root, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        display.setRotation(root.getYaw(), 0.0F);
        Quaternionf itemDisplayCompensation = new Quaternionf(rightRotation).rotateY((float) Math.PI);
        display.setTransformation(new Transformation(center, leftRotation, scale, itemDisplayCompensation));
    }

    private void updateBrightness(Location root) {
        Block sample = root.clone().add(0.0D, spec.entityHeight() + 0.01D, 0.0D).getBlock();
        Display.Brightness brightness = new Display.Brightness(sample.getLightFromBlocks(), sample.getLightFromSky());
        for (Entity entity : entities) {
            if (entity instanceof Display display && !brightness.equals(display.getBrightness())) {
                display.setBrightness(brightness);
            }
        }
    }

    public Interaction interaction() {
        return interaction;
    }

    public List<Entity> entities() {
        return entities;
    }

    public boolean valid() {
        return interaction.isValid() && body.isValid()
                && wheels.stream().allMatch(wheel -> wheel.entity.isValid())
                && extras.stream().allMatch(extra -> extra.entity.isValid())
                && (fluid == null || fluid.isValid());
    }

    public void remove() {
        entities.forEach(Entity::remove);
    }

    private static float radians(float value) {
        return (float) Math.toRadians(value);
    }

    private record WheelDisplay(ItemDisplay entity, int side) {
    }

    private record ExtraDisplay(ItemDisplay entity, Vector3f center, Vector3f scale, boolean spins) {
    }

    public record FluidVisual(Material material, float fraction) {
    }
}
