package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.gaspump.GasPump;
import com.mrcrayfish.vehicle.paper.gaspump.GasPumpItem;
import com.mrcrayfish.vehicle.paper.gaspump.GasPumpManager;
import com.mrcrayfish.vehicle.paper.gaspump.GasPumpRig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Placement, breaking and nozzle interaction for gas pumps.
 *
 * <p>Any player may place or break a pump, like a normal block (the original
 * mod's {@code GasPumpBlock} has no admin gate either); see
 * {@link GasPumpManager} for the free/instant fuel transfer logic.
 */
public final class GasPumpListener implements Listener {
    private final GasPumpManager gasPumps;

    public GasPumpListener(GasPumpManager gasPumps) {
        this.gasPumps = gasPumps;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!GasPumpItem.isGasPump(item)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getBlockFace() == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        Location bottom = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5D, 0.0D, 0.5D);
        if (!gasPumps.canPlace(bottom)) {
            player.sendActionBar(Component.text(
                    "Nu este suficient spațiu pentru pompa de benzină (are nevoie de 2 blocuri libere).",
                    NamedTextColor.YELLOW));
            return;
        }

        gasPumps.place(bottom, player.getLocation().getYaw());
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        bottom.getWorld().playSound(bottom, Sound.BLOCK_METAL_PLACE, 1.0F, 1.0F);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNozzleInteract(PlayerInteractAtEntityEvent event) {
        Optional<GasPump> maybePump = gasPumps.byEntity(event.getRightClicked());
        if (maybePump.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        GasPump pump = maybePump.get();
        Player player = event.getPlayer();

        // The interaction hitbox spans both blocks; only the top half (the
        // nozzle/fueling half in the original mod) responds to clicks.
        if (event.getClickedPosition().getY() < 1.0D) {
            return;
        }

        if (pump.hasHolder()) {
            if (pump.holderId().equals(player.getUniqueId())) {
                gasPumps.putDownNozzle(pump);
            } else {
                player.sendActionBar(Component.text(
                        "Cineva ține deja duza acestei pompe.", NamedTextColor.YELLOW));
            }
            return;
        }
        gasPumps.pickUpNozzle(player, pump);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!entity.getScoreboardTags().contains(GasPumpRig.ENTITY_TAG)) {
            return;
        }
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent damage) || !(damage.getDamager() instanceof Player)) {
            return;
        }
        gasPumps.byEntity(entity).ifPresent(pump -> {
            World world = pump.location().getWorld();
            world.dropItemNaturally(pump.location(), GasPumpItem.create());
            world.playSound(pump.location(), Sound.BLOCK_METAL_BREAK, 1.0F, 0.8F);
            gasPumps.remove(pump);
        });
    }
}
