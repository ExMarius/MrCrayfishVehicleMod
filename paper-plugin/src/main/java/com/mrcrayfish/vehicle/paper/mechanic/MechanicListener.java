package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Toate evenimentele sistemului mecanic intr-un singur listener coerent. */
public final class MechanicListener implements Listener {
    private final MechanicManager mechanic;
    private final VehicleManager vehicles;
    private final Map<UUID, Long> paintCooldowns = new HashMap<>();

    public MechanicListener(MechanicManager mechanic, VehicleManager vehicles) {
        this.mechanic = mechanic;
        this.vehicles = vehicles;
    }

    /* ============================ plasare iteme ============================ */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.getBlockFace() == null) {
            return;
        }
        ItemStack held = event.getItem();
        MechanicItems.Kind kind = MechanicItems.kindOf(held);
        if (kind == null) {
            return;
        }
        Player player = event.getPlayer();
        // Toate statiile/props-urile se pun DOAR pe fata de sus a unui bloc: pe fete
        // laterale modelul ar ajunge jumatate in perete/pamant (originea e in colt).
        if (event.getBlockFace() != org.bukkit.block.BlockFace.UP) {
            player.sendRichMessage("<yellow>Aseaza-l pe fata de SUS a unui bloc.</yellow>");
            return;
        }
        Location face = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        float yaw = player.getLocation().getYaw();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        switch (kind) {
            case JACK -> {
                event.setCancelled(true);
                mechanic.jacks().place(player, face, yaw);
                consumeIfNotCreative(held, creative);
            }
            case WORKSTATION -> {
                event.setCancelled(true);
                if (mechanic.workstations().place(player, face, yaw)) {
                    consumeIfNotCreative(held, creative);
                }
            }
            case TRAFFIC_CONE -> {
                event.setCancelled(true);
                mechanic.props().place("traffic_cone", face, yaw);
                consumeIfNotCreative(held, creative);
            }
            case BOOST_PAD -> {
                event.setCancelled(true);
                mechanic.props().place("boost_pad", face, yaw);
                consumeIfNotCreative(held, creative);
            }
            case FLUID_PIPE -> {
                event.setCancelled(true);
                mechanic.props().place("fluid_pipe", face, yaw);
                consumeIfNotCreative(held, creative);
            }
            case VEHICLE_CRATE -> {
                event.setCancelled(true);
                placeCrate(player, held, face, yaw);
            }
            default -> {
                // uneltele nu au plasare pe bloc
            }
        }
    }

    private static void consumeIfNotCreative(ItemStack held, boolean creative) {
        if (!creative) {
            held.setAmount(held.getAmount() - 1);
        }
    }

    /** Cufarul de vehicul (dismantle prin hammer): click pe jos = o reapara pe strada. */
    private void placeCrate(Player player, ItemStack crate, Location face, float yaw) {
        String[] data = MechanicItems.parseCratePayload(MechanicItems.cratePayloadOf(crate));
        if (data.length < 8) {
            player.sendRichMessage("<red>Cufarul nu mai are date valide.</red>");
            return;
        }
        if (!vehicles.deployCrate(player, crate, face, yaw)) {
            return;
        }
        crate.setAmount(crate.getAmount() - 1);
    }

    /* ============================ click pe statii ============================ */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (mechanic.handleEntityInteraction(event.getPlayer(), event.getRightClicked(),
                event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            mechanic.save();
        }
    }

    /* ============================ hammer = demontare ============================ */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        Entity clicked = event.getEntity();
        // props: ghemuit + pumn = ridicare inapoi in inventar
        if (mechanic.props().handleBreak(player, clicked)) {
            event.setCancelled(true);
            mechanic.save();
            return;
        }
        // hammer pe vehicul: demontare in cufar (doar proprietarul, perm mcv.vehicle.dismantle)
        Optional<LandVehicle> vehicle = vehicles.byEntity(clicked);
        if (vehicle.isEmpty()) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (MechanicItems.kindOf(held) != MechanicItems.Kind.HAMMER) {
            return;
        }
        event.setCancelled(true);
        if (!MechanicManager.has(player, "mcv.vehicle.dismantle")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.vehicle.dismantle.</red>");
            return;
        }
        LandVehicle target = vehicle.get();
        if (target.showcase() || target.onJack()) {
            player.sendRichMessage("<red>Nu poti demonta aceasta masina acum.</red>");
            return;
        }
        Optional<UUID> owner = target.owner();
        if (owner.isPresent() && !owner.get().equals(player.getUniqueId())) {
            player.sendRichMessage("<red>Doar proprietarul poate demonta masina.</red>");
            return;
        }
        if (vehicles.dismantleToCrate(player, target)) {
            // HammerItem.durability(200) din mod: fiecare demontare toceste ciocanul.
            if (player.getGameMode() != GameMode.CREATIVE
                    && MechanicItems.useHammer(held)) {
                held.setAmount(held.getAmount() - 1);
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.9F, 1.0F);
                player.sendRichMessage("<yellow>Ciocanul s-a uzat si s-a rupt.</yellow>");
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_DESTROY, 0.8F, 0.7F);
            player.sendRichMessage("<gray>Masina demontata - ai primit-o in cufar de transport.</gray>");
            mechanic.save();
        }
    }

    /* ============================ GUI: banc / paleta / creative ============================ */

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof MechanicGui.Holder holder) {
            event.setCancelled(true);
            onMechanicGuiClick(event, holder);
            return;
        }
        if (event.getInventory().getHolder() instanceof CreativeGui.Holder holder) {
            event.setCancelled(true);
            onCreativeGuiClick(event, holder);
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MechanicGui.Holder
                || event.getInventory().getHolder() instanceof CreativeGui.Holder) {
            event.setCancelled(true);
        }
    }

    private void onMechanicGuiClick(InventoryClickEvent event, MechanicGui.Holder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Optional<LandVehicle> vehicle = vehicles.byId(holder.vehicleId());
        if (vehicle.isEmpty()) {
            player.closeInventory();
            return;
        }
        LandVehicle target = vehicle.get();
        if (holder.paintPage()) {
            String action = MechanicGui.actionOf(event.getCurrentItem());
            if (action == null) {
                return;
            }
            if ("back".equals(action)) {
                mechanic.gui().open(player, target);
                return;
            }
            if (action.startsWith("color:")) {
                handlePaintChoice(player, target, PaintColors.valueOf(action.substring(6)));
                mechanic.gui().open(player, target);
            }
            return;
        }

        // sloturile motor/roti accepta itemul NOU de pe cursor (click cu el)
        ItemStack cursor = event.getCursor();
        String action = MechanicGui.actionOf(event.getCurrentItem());
        if (action == null) {
            return;
        }
        switch (action) {
            case "engine_slot" -> {
                if (MechanicItems.kindOf(cursor) == MechanicItems.Kind.ENGINE
                        && mechanicOnVehiclePart(player, target, cursor)) {
                    // cursorul ramane cu itemul vechi schimbat de manager (daca exista)
                }
                mechanic.gui().open(player, target);
            }
            case "wheel_slot" -> {
                if (MechanicItems.kindOf(cursor) == MechanicItems.Kind.WHEEL
                        && mechanicOnVehiclePart(player, target, cursor)) {
                    // idem
                }
                mechanic.gui().open(player, target);
            }
            case "repair" -> {
                mechanic.tryRepair(player, target);
                mechanic.gui().open(player, target);
            }
            case "paint" -> mechanic.gui().openPaintPage(player, target);
            default -> {
            }
        }
    }

    /** Schimb motor/roti FACUT prin GUI (cursor plin) - reutilizam logica manager-ului. */
    private boolean mechanicOnVehiclePart(Player player, LandVehicle vehicle, ItemStack cursor) {
        // managerii actualizeaza starea vehiculului si consuma din acel cursor
        return mechanic.handleToolClick(player, vehicle, cursor);
    }

    private void handlePaintChoice(Player player, LandVehicle vehicle, PaintColors color) {
        if (!MechanicManager.has(player, "mcv.mechanic.paint")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.mechanic.paint.</red>");
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = paintCooldowns.get(player.getUniqueId());
        if (previous != null && now - previous < 400L) {
            return;
        }
        paintCooldowns.put(player.getUniqueId(), now);
        if (player.getGameMode() != GameMode.CREATIVE) {
            int slot = -1;
            ItemStack[] contents = player.getInventory().getContents();
            for (int index = 0; index < contents.length; index++) {
                Integer rgb = MechanicItems.sprayColorOf(contents[index]);
                if (rgb != null && rgb == color.rgb()) {
                    slot = index;
                    break;
                }
            }
            if (slot < 0) {
                player.sendRichMessage("<red>Nu ai un spray can cu vopsea " + color.label()
                        + " in inventar - il cumperi de la magazin (Piese) sau /vehicle creative.</red>");
                return;
            }
            ItemStack can = player.getInventory().getItem(slot);
            if (MechanicItems.spraysRemainingOf(can) <= 0) {
                player.sendRichMessage("<red>Spray-ul s-a golit - reumple-l cumparand alta "
                        + "vopsea (shop > Piese).</red>");
                return;
            }
            MechanicItems.useSpray(can, mechanic.sprayCapacity());
        }
        vehicle.setPaintColor(color.rgb());
        vehicles.save();
        Location at = vehicle.location();
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(at) <= 20.0D * 20.0D) {
                nearby.playSound(net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key("vehicle:item.spray_can.spray"),
                        net.kyori.adventure.sound.Sound.Source.PLAYER, 0.8F, 1.0F),
                        at.getX(), at.getY(), at.getZ());
            }
        }
        player.sendRichMessage("<gray>Ai vopsit masina in <gold>" + color.label() + "</gold>.</gray>");
    }

    private void onCreativeGuiClick(InventoryClickEvent event, CreativeGui.Holder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        String tab = CreativeGui.tabOf(clicked);
        if (tab != null) {
            holder.setCategory(CreativeGui.Category.valueOf(tab));
            CreativeGui.redraw(player, holder);
            return;
        }
        if (CreativeGui.isGiveItem(clicked)) {
            ItemStack copy = clicked.clone();
            copy.setAmount(1);
            player.getInventory().addItem(copy);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.2F);
        }
    }
}
