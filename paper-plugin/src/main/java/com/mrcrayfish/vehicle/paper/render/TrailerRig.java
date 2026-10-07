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
    private final List<InventoryDisplay> inventoryDisplays = new ArrayList<>();
    private final BlockDisplay fluid;
    private final List<Entity> entities;
    private int inventoryFingerprint = Integer.MIN_VALUE;

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

    public static TrailerRig spawn(UUID trailerId, TrailerSpec spec, Location location,
                                   ItemStack[] inventoryContents) {
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
            /* FertilizerTrailerRenderer: body root, translate(0, -.5, -.4375),
             * rotate Z +90, then rotate X by wheel travel. */
            extra(world, location, trailerId, all, extras, "seed_spiker",
                    new Vector3f(spec.bodyPartX(0.0F), spec.bodyPartY(-0.5F),
                            spec.bodyPartZ(-0.4375F)),
                    new Vector3f(1.25F * spec.bodyScale()), true, 90.0F, 0.0F);
        } else if (spec.kind() == TrailerSpec.Kind.SEEDER) {
            /* SeederTrailerRenderer places seven independently spinning spikers
             * at source X offsets -12 through +12 model pixels. */
            for (int x = -12; x <= 12; x += 4) {
                extra(world, location, trailerId, all, extras, "seed_spiker",
                        new Vector3f(spec.bodyPartX(x * LandVehicleSpec.MODEL_UNIT),
                                spec.bodyPartY(-0.65F), spec.bodyPartZ(0.0F)),
                        new Vector3f(0.75F * spec.bodyScale()), true, 0.0F, 0.0F);
            }
        } else if (spec.kind() == TrailerSpec.Kind.STORAGE) {
            ItemDisplay chest = display(world, location, new ItemStack(Material.CHEST));
            mark(chest, trailerId);
            all.add(chest);
            /* StorageTrailerRenderer translates the closed chest -6 model pixels
             * inside the already transformed body matrix. */
            extras.add(new ExtraDisplay(chest,
                    new Vector3f(spec.bodyPartX(0.0F),
                            /* The source ChestModel is bottom-anchored after its
                             * -0.5 X/Z translation. A vanilla chest item model is
                             * center-anchored, so compensate by half a block. */
                            spec.storageChestCenterY(), spec.bodyPartZ(0.0F)),
                    new Vector3f(0.9F * spec.bodyScale()), false, 0.0F, 180.0F));
        }
        if (spec.canTowTrailers()) {
            LandVehicleSpec.Point tow = spec.towBarOffset();
            /* The common source renderer cancels bodyScale before drawing a tow
             * bar, so this transform intentionally does not use bodyPartY(). */
            extra(world, location, trailerId, all, extras, "tow_bar",
                    new Vector3f(tow.x() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                            0.5F + tow.y() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                            tow.z() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT),
                    new Vector3f(1.0F), false, 0.0F, 180.0F);
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
                Collections.unmodifiableList(wheels), Collections.unmodifiableList(extras), fluid, all);
        rig.update(location, 0.0F, null, inventoryContents, 0);
        return rig;
    }

    private static void extra(World world, Location location, UUID id, List<Entity> all,
                              List<ExtraDisplay> extras, String model, Vector3f center,
                              Vector3f scale, boolean spins, float preSpinZ, float sourceYaw) {
        ItemDisplay display = display(world, location, model(model));
        mark(display, id);
        all.add(display);
        extras.add(new ExtraDisplay(display, center, scale, spins, preSpinZ, sourceYaw));
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

    public void update(Location root, float wheelRotation, FluidVisual fluidVisual,
                       ItemStack[] inventoryContents, int tickCount) {
        if (!valid()) {
            return;
        }
        syncInventoryDisplays(root, inventoryContents);
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
            /* Preserve source call order: the fertilizer roller's Z quarter-turn
             * is applied before its wheel-driven local X rotation. */
            Quaternionf rotation = new Quaternionf().rotateZ(radians(extra.preSpinZ));
            if (extra.spins) {
                rotation.rotateX(radians(-wheelRotation));
            }
            place(extra.entity, root, extra.center, rotation, extra.scale,
                    new Quaternionf().rotateY(radians(extra.sourceYaw)));
        }

        for (InventoryDisplay inventoryDisplay : inventoryDisplays) {
            InventoryLayout layout = inventoryDisplay.layout;
            place(inventoryDisplay.entity, root, layout.center, layout.rotation, layout.scale,
                    new Quaternionf());
        }

        if (fluid != null) {
            updateFluid(root, fluidVisual);
        }
        if (tickCount % 5 == 0) {
            updateBrightness(root);
        }
    }

    private void syncInventoryDisplays(Location root, ItemStack[] contents) {
        if (spec.kind() != TrailerSpec.Kind.FERTILIZER && spec.kind() != TrailerSpec.Kind.SEEDER) {
            return;
        }
        int fingerprint = inventoryFingerprint(contents);
        if (fingerprint == inventoryFingerprint
                && inventoryDisplays.stream().allMatch(display -> display.entity.isValid())) {
            return;
        }
        for (InventoryDisplay display : inventoryDisplays) {
            entities.remove(display.entity);
            display.entity.remove();
        }
        inventoryDisplays.clear();
        inventoryFingerprint = fingerprint;

        World world = root.getWorld();
        if (world == null) {
            return;
        }
        for (InventoryLayout layout : inventoryLayout(spec, contents)) {
            ItemDisplay entity = display(world, root, layout.stack.clone());
            mark(entity, trailerId);
            entities.add(entity);
            inventoryDisplays.add(new InventoryDisplay(entity, layout));
        }
    }

    /** Exact item-pile matrices from FertilizerTrailerRenderer and SeederTrailerRenderer. */
    static List<InventoryLayout> inventoryLayout(TrailerSpec spec, ItemStack[] contents) {
        if (contents == null || (spec.kind() != TrailerSpec.Kind.FERTILIZER
                && spec.kind() != TrailerSpec.Kind.SEEDER)) {
            return List.of();
        }
        boolean fertilizer = spec.kind() == TrailerSpec.Kind.FERTILIZER;
        float baseX = (fertilizer ? -5.5F : -10.5F) * LandVehicleSpec.MODEL_UNIT;
        float baseY = -3.0F * LandVehicleSpec.MODEL_UNIT;
        float baseZ = (fertilizer ? -3.0F : -2.0F) * LandVehicleSpec.MODEL_UNIT;
        int divisor = fertilizer ? 32 : 16;
        int width = fertilizer ? 3 : 4;
        int maxLayerCount = fertilizer ? 6 : 8;
        float layerHeight = fertilizer ? 0.1F : 0.05F;
        float xSpacing = fertilizer ? 0.5F : 0.75F;
        float zSpacing = fertilizer ? 0.75F : 0.5F;
        float stagger = fertilizer ? 0.5F : 0.7F;
        float nestedScale = 0.45F;
        float worldScale = spec.bodyScale() * nestedScale;

        List<InventoryLayout> layouts = new ArrayList<>();
        int layer = 0;
        int index = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            int count = Math.max(1, stack.getAmount() / divisor);
            for (int j = 0; j < count; j++) {
                int layerIndex = index % maxLayerCount;
                float localX = (layerIndex % width) * xSpacing + stagger * (layer % 2);
                float localY = layer * layerHeight + (fertilizer ? j * 0.0625F : 0.0F);
                float localZ = (layerIndex / width) * zSpacing;
                Quaternionf rotation = new Quaternionf()
                        .rotateX(radians(90.0F))
                        .rotateZ(radians(47.0F * index))
                        .rotateX(radians(2.0F * layerIndex));
                Vector3f zFight = new Vector3f(layer * 0.001F, layer * 0.001F, layer * 0.001F);
                rotation.transform(zFight);
                localX += zFight.x;
                localY += zFight.y;
                localZ += zFight.z;

                Vector3f center = new Vector3f(
                        spec.bodyPartX(baseX + nestedScale * localX),
                        spec.bodyPartY(baseY + nestedScale * localY),
                        spec.bodyPartZ(baseZ + nestedScale * localZ));
                layouts.add(new InventoryLayout(stack.clone(), center,
                        new Vector3f(worldScale), rotation));
                index++;
                if (index % maxLayerCount == 0) {
                    layer++;
                }
            }
        }
        return List.copyOf(layouts);
    }

    private static int inventoryFingerprint(ItemStack[] contents) {
        int result = 1;
        if (contents != null) {
            for (ItemStack stack : contents) {
                result = 31 * result + (stack == null ? 0 : stack.hashCode());
            }
        }
        return result;
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
        /* FluidTrailerRenderer#drawFluid source cuboid:
         * x=-.3875, y=-.1875, z=-.99, width=.7625, max height=9.9/16,
         * depth=1.67. It is drawn inside the common body transform. */
        float height = spec.bodyScale() * 9.9F * LandVehicleSpec.MODEL_UNIT
                * Math.min(1.0F, visual.fraction());
        fluid.teleport(root, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        fluid.setRotation(root.getYaw(), 0.0F);
        fluid.setTransformation(new Transformation(
                new Vector3f(spec.bodyPartX(-0.3875F), spec.bodyPartY(-0.1875F),
                        spec.bodyPartZ(-0.99F)),
                new Quaternionf(),
                new Vector3f(spec.bodyScale() * 0.7625F, height,
                        spec.bodyScale() * 1.67F),
                new Quaternionf()));
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
        return Collections.unmodifiableList(entities);
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

    private record ExtraDisplay(ItemDisplay entity, Vector3f center, Vector3f scale,
                                boolean spins, float preSpinZ, float sourceYaw) {
    }

    private record InventoryDisplay(ItemDisplay entity, InventoryLayout layout) {
    }

    record InventoryLayout(ItemStack stack, Vector3f center, Vector3f scale, Quaternionf rotation) {
    }

    public record FluidVisual(Material material, float fraction) {
    }
}
