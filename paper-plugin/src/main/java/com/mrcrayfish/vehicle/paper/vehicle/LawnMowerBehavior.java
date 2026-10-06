package com.mrcrayfish.vehicle.paper.vehicle;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/** Direct server-side port of LawnMowerEntity's BushBlock cutting loop. */
final class LawnMowerBehavior {
    private static final Set<String> BUSHES = Set.of(
            "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH",
            "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET",
            "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE",
            "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY", "TORCHFLOWER", "PITCHER_PLANT",
            "OPEN_EYEBLOSSOM", "CLOSED_EYEBLOSSOM", "PINK_PETALS", "WILDFLOWERS",
            "SPORE_BLOSSOM", "SMALL_DRIPLEAF", "BIG_DRIPLEAF", "BIG_DRIPLEAF_STEM",
            "BROWN_MUSHROOM", "RED_MUSHROOM", "CRIMSON_FUNGUS", "WARPED_FUNGUS",
            "CRIMSON_ROOTS", "WARPED_ROOTS", "NETHER_SPROUTS", "NETHER_WART",
            "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "PITCHER_CROP", "TORCHFLOWER_CROP",
            "COCOA", "MANGROVE_PROPAGULE", "HANGING_ROOTS", "PALE_HANGING_MOSS",
            "MELON_STEM", "ATTACHED_MELON_STEM", "PUMPKIN_STEM", "ATTACHED_PUMPKIN_STEM",
            "SWEET_BERRY_BUSH", "SUGAR_CANE", "BAMBOO_SAPLING", "LILY_PAD",
            "SEAGRASS", "TALL_SEAGRASS", "CAVE_VINES", "CAVE_VINES_PLANT",
            "TWISTING_VINES", "TWISTING_VINES_PLANT", "WEEPING_VINES", "WEEPING_VINES_PLANT"
    );

    private LawnMowerBehavior() {
    }

    static void cutBushes(Location root, Vector motion, float entityWidth, Player driver,
                          Function<ItemStack, ItemStack> storage) {
        World world = root.getWorld();
        if (world == null) {
            return;
        }

        double radius = entityWidth * 0.5D + 0.25D;
        Vector look = forward(root.getYaw()).multiply(0.5D);
        int minX = floor(root.getX() - radius + look.getX());
        int maxX = (int) Math.ceil(root.getX() + radius + look.getX());
        int minZ = floor(root.getZ() - radius + look.getZ());
        int maxZ = (int) Math.ceil(root.getZ() + radius + look.getZ());
        int y = floor(root.getY() + 0.5D);

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (!isBush(block.getType())) {
                    continue;
                }
                BlockBreakEvent event = new BlockBreakEvent(block, driver);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    continue;
                }
                cut(world, block, motion, root, storage);
            }
        }
    }

    private static void cut(World world, Block block, Vector motion, Location vehicleLocation,
                            Function<ItemStack, ItemStack> storage) {
        BlockData data = block.getBlockData();
        Collection<ItemStack> drops = block.getDrops();
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        block.setType(Material.AIR, false);
        world.playSound(center, data.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
        world.spawnParticle(Particle.BLOCK, center, 12, 0.25D, 0.25D, 0.25D, 0.0D, data);
        for (ItemStack stack : drops) {
            ItemStack remainder = storage.apply(stack);
            if (remainder != null && !remainder.getType().isAir() && remainder.getAmount() > 0) {
                drop(world, vehicleLocation, motion, remainder);
            }
        }
    }

    private static void drop(World world, Location location, Vector motion, ItemStack stack) {
        int remaining = stack.getAmount();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (remaining > 0) {
            int amount = Math.min(remaining, random.nextInt(10, 31));
            ItemStack split = stack.clone();
            split.setAmount(amount);
            remaining -= amount;
            Item item = world.dropItem(location, split);
            item.setPickupDelay(20);
            item.setVelocity(new Vector(-motion.getX() / 4.0D,
                    random.nextGaussian() * 0.05D + 0.2D, -motion.getZ() / 4.0D));
        }
    }

    private static boolean isBush(Material material) {
        String name = material.name();
        return BUSHES.contains(name) || name.endsWith("_SAPLING") || name.endsWith("_TULIP")
                || name.endsWith("_FLOWER") || name.endsWith("_AZALEA")
                || name.endsWith("_MUSHROOM");
    }

    private static Vector forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
