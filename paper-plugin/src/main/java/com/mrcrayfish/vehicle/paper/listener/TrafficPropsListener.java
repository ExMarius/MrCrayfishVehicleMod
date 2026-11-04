package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.world.TrafficProps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * CICLUL AH - evenimentele pentru conul de trafic si placa de boost:
 *  - plasare: click dreapta cu itemul custom (placa se orienteaza dupa privire);
 *  - spargere: itemul custom pleaca in lume (niciodata fir string vanilla);
 *  - fizica blocurilor: dezactivata pentru combinatiile noastre - fara asta un
 *    minereu spart langa pad ar reseta „disarmed" si s-ar vedea ca fir vanilla;
 *  - pistoanele nu le pot muta; apa nu le clateste; exploziile le distrug.
 */
public final class TrafficPropsListener implements Listener {

    /** Ciclul AJ.4: orice entitate-fantoma "mcv_prop" (item-display-urile vechi
     *  din epoca PropManager - salvate in chunk NBT desi erau setPersistent(false))
     *  e stearsa la incarcare chunkului; purge-ul de la boot acopera doar spawnul,
     *  asta acopera ORICE chunk pe care paseste un jucator. Idempotent. */
    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
                if (entity.getScoreboardTags().contains("mcv_prop")) {
                    entity.remove();
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getBlockFace() == null) {
            return;
        }
        ItemStack hand = event.getItem();
        TrafficProps.Kind kind = TrafficProps.kindOf(hand);
        if (kind == null) {
            // Ciclul AI: itemele VECHI din ciclurile anterioare (PDC-ul mecanic) si
            // continuous functioneaza - pun acum BLOCUL real, nu entitatea veche.
            com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.Kind legacy =
                    com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.kindOf(hand);
            if (legacy == com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.Kind.TRAFFIC_CONE) {
                kind = TrafficProps.Kind.CONE;
            } else if (legacy == com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.Kind.BOOST_PAD) {
                kind = TrafficProps.Kind.BOOST_PAD;
            } else if (legacy == com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.Kind.FLUID_PIPE) {
                kind = TrafficProps.Kind.FLUID_PIPE;
            } else {
                return;
            }
        }
        event.setCancelled(true); // hartia nu interactioneaza cu nimic
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!target.getType().isAir() && target.getType() != Material.TRIPWIRE) {
            event.getPlayer().sendMessage(Component.text("Aici nu pot pune obiectul - locul e ocupat.",
                    NamedTextColor.RED));
            return;
        }
        BlockFace facing = TrafficProps.yawToFacing(event.getPlayer().getLocation().getYaw());
        BlockData data = switch (kind) {
            case CONE -> TrafficProps.coneData();
            case BOOST_PAD -> TrafficProps.padData(facing);
            case FLUID_PIPE -> TrafficProps.pipeData(facing);
        };
        target.setBlockData(data, false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        target.getWorld().playSound(target.getLocation().add(0.5D, 0.5D, 0.5D),
                kind == TrafficProps.Kind.CONE ? Sound.BLOCK_BAMBOO_WOOD_PLACE : Sound.BLOCK_STONE_PLACE,
                0.9F, 1.1F);
        if (kind == TrafficProps.Kind.BOOST_PAD) {
            event.getPlayer().sendMessage(Component.text("[Drum] ", NamedTextColor.DARK_GREEN)
                    .append(Component.text("Placa de boost apasa spre " + naming(facing)
                            + " - masina care o calca e impinsa in acea directie.", NamedTextColor.GRAY)));
        }
    }

    private static String naming(BlockFace face) {
        return switch (face) {
            case NORTH -> "NORD";
            case SOUTH -> "SUD";
            case EAST -> "EST";
            case WEST -> "VEST";
            default -> "aici";
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        TrafficProps.Kind kind = TrafficProps.kindOf(block);
        if (kind == null) {
            return;
        }
        event.setExpToDrop(0);
        event.setDropItems(false); // fara fir de sarma din greseala
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.25D, 0.5D),
                TrafficProps.item(kind, 1));
    }

    /** Un bloc vecin s-a schimbat: vanilla ar recalcula laturile/disarmed ale
     *  firului si prop-ul nostru s-ar intoarce vizual in fir de sarma. */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (TrafficProps.kindOf(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> TrafficProps.kindOf(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> TrafficProps.kindOf(block) != null)) {
            event.setCancelled(true);
        }
    }

    /** Apa/lava nu clateste conurile si placile altfel - raman lipite pe asfalt. */
    @EventHandler(ignoreCancelled = true)
    public void onLiquid(BlockFromToEvent event) {
        if (TrafficProps.kindOf(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Exploziile le DISTRUG (fara drop) - si le scoatem din lista vanilla ca sa
     *  nu lase in urma fir de sarma ca drop al exploziei. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (TrafficProps.kindOf(block) == null) {
                return false;
            }
            block.setType(Material.AIR, false);
            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (TrafficProps.kindOf(block) == null) {
                return false;
            }
            block.setType(Material.AIR, false);
            return true;
        });
    }
}
