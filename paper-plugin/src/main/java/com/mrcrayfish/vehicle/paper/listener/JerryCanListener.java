package com.mrcrayfish.vehicle.paper.listener;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.gaspump.GasPumpManager;
import com.mrcrayfish.vehicle.paper.jerrycan.JerryCanItem;
import com.mrcrayfish.vehicle.paper.mechanic.MechanicItems;
import org.bukkit.Material;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.Optional;

/**
 * Jerry can interactions (port of the original mod):
 * <ul>
 *   <li>Right-click a gas pump with the can -&gt; fill it to full (fuel is free by current design).</li>
 *   <li>Right-click a vehicle with the can -&gt; pour {@link JerryCanItem#POUR_PER_CLICK}
 *       units per click into its tank until the can is empty or the tank is full.</li>
 * </ul>
 * Handled before the vehicle/gas-pump enter-put handlers (NORMAL vs their HIGH) and cancelled,
 * so clicking with a can never makes the player enter the vehicle by accident.
 */
public final class JerryCanListener implements Listener {
    private static final DecimalFormat FUEL_FORMAT = new DecimalFormat("#,###");
    private static final Key GLUG = Key.key("vehicle:item.jerry_can.liquid_glug");

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final GasPumpManager gasPumps;

    public JerryCanListener(VehiclePlugin plugin, VehicleManager vehicles, GasPumpManager gasPumps) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.gasPumps = gasPumps;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPumpFill(PlayerInteractAtEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!JerryCanItem.isJerryCan(item) || gasPumps.byEntity(event.getRightClicked()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        JerryCanItem.Type type = JerryCanItem.typeOf(item);
        if (JerryCanItem.getFuel(item) >= type.capacity) {
            player.sendActionBar(Component.text("Canistra este deja plină.", NamedTextColor.YELLOW));
            return;
        }
        JerryCanItem.setFuel(item, type.capacity);
        Location pump = event.getRightClicked().getLocation();
        player.playSound(Sound.sound(GLUG, Sound.Source.BLOCK, 0.6F, 1.0F),
                pump.getX(), pump.getY(), pump.getZ());
        player.sendActionBar(Component.text("Canistră umplută: "
                + FUEL_FORMAT.format(type.capacity) + " / " + FUEL_FORMAT.format(type.capacity), NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPourAt(PlayerInteractAtEntityEvent event) {
        pour(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPour(PlayerInteractEntityEvent event) {
        // The At-variant already handles its own clicks (see onPourAt); some dispatches deliver
        // both, so skip it here to never double-pour.
        if (event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        pour(event.getPlayer(), event.getRightClicked(), event);
    }

    private void pour(Player player, org.bukkit.entity.Entity target, PlayerInteractEntityEvent event) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (MechanicItems.bucketFluidOf(item) != null) {
            pourBucket(player, target, event, item);
            return;
        }
        if (!JerryCanItem.isJerryCan(item)) {
            return;
        }
        Optional<LandVehicle> maybeVehicle = vehicles.byEntity(target);
        if (maybeVehicle.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        LandVehicle vehicle = maybeVehicle.get();

        float canFuel = JerryCanItem.getFuel(item);
        if (canFuel <= 0.0F) {
            player.sendActionBar(Component.text(
                    "Canistra este goală - umple-o la o pompă de benzină.", NamedTextColor.RED));
            return;
        }
        float capacity = vehicle.spec().energyCapacity();
        if (vehicle.fuel() >= capacity) {
            player.sendActionBar(Component.text("Rezervorul vehiculului este deja plin.", NamedTextColor.YELLOW));
            return;
        }

        float poured = Math.min(JerryCanItem.POUR_PER_CLICK, Math.min(canFuel, capacity - vehicle.fuel()));
        vehicle.setFuel(vehicle.fuel() + poured);
        JerryCanItem.setFuel(item, canFuel - poured);
        vehicles.save();

        Location at = target.getLocation();
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(at) <= 64.0D * 64.0D) {
                nearby.playSound(Sound.sound(GLUG, Sound.Source.BLOCK, 0.6F, 1.0F),
                        at.getX(), at.getY(), at.getZ());
            }
        }
        player.sendActionBar(Component.text(
                "Vehicul: " + FUEL_FORMAT.format(vehicle.fuel()) + " / " + FUEL_FORMAT.format(capacity)
                        + "  •  Canistră: " + FUEL_FORMAT.format(canFuel - poured),
                NamedTextColor.GREEN));
    }

    /** Galetusa cu fluid (fuelium/ender_sap/blaze_juice): toarna
     *  mechanic.fluid-bucket-amount si inlocuieste itemul cu galetusa goala. */
    private void pourBucket(Player player, org.bukkit.entity.Entity target,
                    PlayerInteractEntityEvent event, ItemStack item) {
        LandVehicle vehicle = vehicles.byEntity(target).orElse(null);
        if (vehicle == null) {
            return;
        }
        event.setCancelled(true);
        MechanicItems.Fluid fluid = MechanicItems.bucketFluidOf(item);
        float capacity = vehicle.spec().energyCapacity();
        if (vehicle.fuel() >= capacity) {
            player.sendActionBar(Component.text("Rezervorul vehiculului este deja plin.",
                    NamedTextColor.YELLOW));
            return;
        }
        float amount = plugin.getConfig().getInt("mechanic.fluid-bucket-amount", 1000);
        float poured = Math.min(amount, capacity - vehicle.fuel());
        if (poured <= 0) {
            return;
        }
        vehicle.setFuel(vehicle.fuel() + poured);
        vehicles.save();
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        }
        Location at = vehicle.location();
        player.playSound(Sound.sound(GLUG, Sound.Source.BLOCK, 0.8F, 1.0F),
                at.getX(), at.getY(), at.getZ());
        player.sendActionBar(Component.text("Ai turnat " + fluid.label + ": +"
                + FUEL_FORMAT.format(poured) + " (rezervor: " + FUEL_FORMAT.format(vehicle.fuel())
                + " / " + FUEL_FORMAT.format(capacity) + ")", NamedTextColor.GREEN));
    }

}
