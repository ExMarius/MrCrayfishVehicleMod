package com.mrcrayfish.vehicle.paper.runtime;

import com.mrcrayfish.vehicle.paper.physics.VehicleCollisionMover;
import com.mrcrayfish.vehicle.paper.render.TrailerRig;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.TrailerSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runtime state and source-derived behaviour for one towable trailer. */
public final class PaperTrailer {
    private static final double DETACH_THRESHOLD = 6.0D;
    private static final int FLUID_CAPACITY = 100_000;

    private final TrailerManager manager;
    private final UUID id;
    private final TrailerSpec spec;
    private final TrailerRig rig;
    private final Inventory inventory;
    private final Map<Integer, String> lastWorkedBlocks = new HashMap<>();
    private Location location;
    private double verticalVelocity;
    private boolean onGround;
    private float wheelRotation;
    private int tickCount;
    private PullerType pullerType;
    private UUID pullerId;
    private UUID loadedVehicleId;
    private Material fluidMaterial;
    private int fluidAmount;

    PaperTrailer(TrailerManager manager, UUID id, TrailerSpec spec, Location location,
                 PullerType pullerType, UUID pullerId, UUID loadedVehicleId,
                 Material fluidMaterial, int fluidAmount, ItemStack[] contents) {
        this.manager = manager;
        this.id = id;
        this.spec = spec;
        this.location = location.clone();
        this.pullerType = pullerType == null ? PullerType.NONE : pullerType;
        this.pullerId = pullerId;
        this.loadedVehicleId = loadedVehicleId;
        this.fluidMaterial = fluidMaterial;
        this.fluidAmount = Math.max(0, Math.min(FLUID_CAPACITY, fluidAmount));
        this.inventory = hasInventory() ? Bukkit.createInventory(null, 27, Component.text(spec.displayName())) : null;
        if (inventory != null && contents != null) {
            inventory.setContents(normalizeContents(contents));
        }
        this.rig = TrailerRig.spawn(id, spec, this.location);
    }

    public void tick() {
        if (!rig.valid()) {
            return;
        }
        tickCount++;
        Location previous = location.clone();
        PullTarget target = manager.resolvePullTarget(this);
        if (target == null && pullerType != PullerType.NONE && !manager.pullerTemporarilyUnavailable(this)) {
            detach(false);
        }

        if (target != null) {
            double maximumDistance = DETACH_THRESHOLD + Math.abs(spec.hitchDistance());
            if (!Objects.equals(target.location().getWorld(), location.getWorld())
                    || target.location().distanceSquared(location) > maximumDistance * maximumDistance) {
                detach(true);
                target = null;
            }
        }

        if (target != null) {
            float yaw = (float) Math.toDegrees(Math.atan2(
                    target.location().getZ() - location.getZ(),
                    target.location().getX() - location.getX()) - Math.toRadians(90.0D));
            Location desired = local(target.location(), 0.0D, 0.0D, spec.hitchDistance(), yaw);
            Vector requested = new Vector(desired.getX() - location.getX(), verticalVelocity - 0.08D,
                    desired.getZ() - location.getZ());
            move(requested);
            location.setYaw(yaw);
        } else {
            move(new Vector(0.0D, verticalVelocity - 0.08D, 0.0D));
        }

        double dx = location.getX() - previous.getX();
        double dz = location.getZ() - previous.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > 1.0E-6D) {
            double yawRadians = Math.toRadians(location.getYaw());
            double forwardX = -Math.sin(yawRadians);
            double forwardZ = Math.cos(yawRadians);
            double direction = Math.signum(forwardX * dx + forwardZ * dz);
            double circumference = 24.0D * spec.bodyScale() * 1.25D;
            wheelRotation -= (float) ((distance * 20.0D * direction * 16.0D / circumference) * 20.0D);
            runEquipment();
        }

        TrailerRig.FluidVisual visual = fluidAmount > 0 && fluidMaterial != null
                ? new TrailerRig.FluidVisual(fluidMaterial, fluidAmount / (float) FLUID_CAPACITY) : null;
        rig.update(location, wheelRotation, visual, tickCount);
        if (loadedVehicleId != null) {
            manager.positionLoadedVehicle(this, loadedVehicleId);
        }
    }

    private void move(Vector requested) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        VehicleCollisionMover.Result result = VehicleCollisionMover.move(world, location, requested, onGround,
                spec.entityWidth(), spec.entityHeight(), 1.0D);
        location.add(result.movement());
        onGround = result.onGround();
        verticalVelocity = result.verticalCollision() ? 0.0D : result.movement().getY();
    }

    private void runEquipment() {
        switch (spec.kind()) {
            case FERTILIZER -> fertilize();
            case SEEDER -> seed();
            default -> {
            }
        }
    }

    private void fertilize() {
        ItemStack fertilizer = findSupply(Material.BONE_MEAL);
        if (fertilizer == null) {
            return;
        }
        double yaw = Math.toRadians(location.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        boolean applied = false;
        for (int lane = -1; lane <= 1; lane++) {
            double x = location.getX() - forwardX + rightX * lane;
            double z = location.getZ() - forwardZ + rightZ * lane;
            Block block = location.getWorld().getBlockAt((int) Math.floor(x),
                    (int) Math.floor(location.getY() + 0.25D), (int) Math.floor(z));
            String key = block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (key.equals(lastWorkedBlocks.put(lane, key))) {
                continue;
            }
            if (block.applyBoneMeal(BlockFace.UP)) {
                applied = true;
                block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        block.getLocation().add(0.5D, 0.5D, 0.5D), 8, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        }
        if (applied) {
            fertilizer.setAmount(fertilizer.getAmount() - 1);
        }
    }

    private void seed() {
        double yaw = Math.toRadians(location.getYaw());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        for (int lane = -1; lane <= 1; lane++) {
            double x = location.getX() + rightX * lane * 0.85D;
            double z = location.getZ() + rightZ * lane * 0.85D;
            Block target = location.getWorld().getBlockAt((int) Math.floor(x),
                    (int) Math.floor(location.getY() + 0.25D), (int) Math.floor(z));
            if (target.getType() != Material.AIR || target.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) {
                continue;
            }
            ItemStack seeds = findSeeds();
            if (seeds == null) {
                return;
            }
            Material crop = cropFor(seeds.getType());
            if (crop != null) {
                target.setType(crop, true);
                seeds.setAmount(seeds.getAmount() - 1);
            }
        }
    }

    private ItemStack findSupply(Material material) {
        ItemStack own = find(inventory, stack -> stack.getType() == material);
        return own != null ? own : manager.findUpstreamSupply(this, stack -> stack.getType() == material);
    }

    private ItemStack findSeeds() {
        ItemStack own = find(inventory, stack -> cropFor(stack.getType()) != null);
        return own != null ? own : manager.findUpstreamSupply(this, stack -> cropFor(stack.getType()) != null);
    }

    private static ItemStack find(Inventory inventory, java.util.function.Predicate<ItemStack> predicate) {
        if (inventory == null) {
            return null;
        }
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && !stack.getType().isAir() && predicate.test(stack)) {
                return stack;
            }
        }
        return null;
    }

    static Material cropFor(Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case TORCHFLOWER_SEEDS -> Material.TORCHFLOWER_CROP;
            case PITCHER_POD -> Material.PITCHER_CROP;
            default -> null;
        };
    }

    public void interact(Player player) {
        if (spec.kind() == TrailerSpec.Kind.FLUID && exchangeFluid(player)) {
            return;
        }
        if (spec.kind() == TrailerSpec.Kind.VEHICLE) {
            manager.toggleVehicleLoad(this, player);
            return;
        }
        if (inventory != null) {
            player.openInventory(inventory);
        } else if (spec.kind() == TrailerSpec.Kind.FLUID) {
            showFluid(player);
        }
    }

    private boolean exchangeFluid(Player player) {
        PlayerInventory playerInventory = player.getInventory();
        ItemStack held = playerInventory.getItemInMainHand();
        Material incoming = fluidFromBucket(held.getType());
        if (incoming != null && fluidAmount <= FLUID_CAPACITY - 1_000
                && (fluidAmount == 0 || incoming == fluidMaterial)) {
            fluidMaterial = incoming;
            fluidAmount += 1_000;
            playerInventory.setItemInMainHand(new ItemStack(Material.BUCKET));
            player.getWorld().playSound(location, Sound.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 1.0F, 1.0F);
            showFluid(player);
            return true;
        }
        if (held.getType() == Material.BUCKET && fluidAmount >= 1_000 && fluidMaterial != null) {
            Material bucket = bucketForFluid(fluidMaterial);
            if (bucket != null) {
                fluidAmount -= 1_000;
                if (fluidAmount == 0) {
                    fluidMaterial = null;
                }
                ItemStack filled = new ItemStack(bucket);
                if (held.getAmount() == 1) {
                    playerInventory.setItemInMainHand(filled);
                } else {
                    held.setAmount(held.getAmount() - 1);
                    playerInventory.addItem(filled).values()
                            .forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
                }
                player.getWorld().playSound(location, Sound.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
                showFluid(player);
                return true;
            }
        }
        return false;
    }

    private void showFluid(Player player) {
        String type = fluidMaterial == null ? "empty" : fluidMaterial.name().toLowerCase();
        player.sendActionBar(Component.text("Fluid Trailer: " + (fluidAmount / 1_000) + "/100 buckets (" + type + ")"));
    }

    private static Material fluidFromBucket(Material bucket) {
        return switch (bucket) {
            case WATER_BUCKET -> Material.WATER;
            case LAVA_BUCKET -> Material.LAVA;
            case POWDER_SNOW_BUCKET -> Material.POWDER_SNOW;
            default -> null;
        };
    }

    private static Material bucketForFluid(Material fluid) {
        return switch (fluid) {
            case WATER -> Material.WATER_BUCKET;
            case LAVA -> Material.LAVA_BUCKET;
            case POWDER_SNOW -> Material.POWDER_SNOW_BUCKET;
            default -> null;
        };
    }

    private static ItemStack[] normalizeContents(ItemStack[] source) {
        ItemStack[] result = new ItemStack[27];
        System.arraycopy(source, 0, result, 0, Math.min(source.length, result.length));
        return result;
    }

    public boolean accepts(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        return switch (spec.kind()) {
            case FERTILIZER -> stack.getType() == Material.BONE_MEAL;
            case SEEDER -> cropFor(stack.getType()) != null;
            case STORAGE -> true;
            default -> false;
        };
    }

    public void attach(PullerType type, UUID pullerId) {
        this.pullerType = type;
        this.pullerId = pullerId;
        this.verticalVelocity = 0.0D;
    }

    public void detach(boolean sound) {
        if (sound && location.getWorld() != null) {
            location.getWorld().playSound(location, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        this.pullerType = PullerType.NONE;
        this.pullerId = null;
    }

    public Location towBarLocation() {
        if (!spec.canTowTrailers()) {
            return location.clone();
        }
        LandVehicleSpec.Point tow = spec.towBarOffset();
        return local(location,
                tow.x() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                tow.y() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                tow.z() * spec.bodyScale() * LandVehicleSpec.MODEL_UNIT,
                location.getYaw());
    }

    static Location local(Location root, double x, double y, double z, float yaw) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return root.clone().add(x * cos - z * sin, y, x * sin + z * cos);
    }

    public void remove() {
        rig.remove();
    }

    private boolean hasInventory() {
        return spec.kind() == TrailerSpec.Kind.FERTILIZER
                || spec.kind() == TrailerSpec.Kind.SEEDER
                || spec.kind() == TrailerSpec.Kind.STORAGE;
    }

    public UUID id() {
        return id;
    }

    public TrailerSpec spec() {
        return spec;
    }

    public Location location() {
        return location.clone();
    }

    public PullerType pullerType() {
        return pullerType;
    }

    public UUID pullerId() {
        return pullerId;
    }

    public UUID loadedVehicleId() {
        return loadedVehicleId;
    }

    public void setLoadedVehicleId(UUID loadedVehicleId) {
        this.loadedVehicleId = loadedVehicleId;
    }

    public Material fluidMaterial() {
        return fluidMaterial;
    }

    public int fluidAmount() {
        return fluidAmount;
    }

    public Inventory inventory() {
        return inventory;
    }

    public ItemStack[] inventoryContents() {
        return inventory == null ? new ItemStack[0] : inventory.getContents();
    }

    public TrailerRig rig() {
        return rig;
    }

    public enum PullerType {
        NONE,
        PLAYER,
        VEHICLE,
        TRAILER
    }

    public record PullTarget(Location location) {
    }
}
