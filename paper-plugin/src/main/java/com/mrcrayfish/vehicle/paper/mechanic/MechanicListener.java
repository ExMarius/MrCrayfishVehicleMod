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
    private static final net.kyori.adventure.key.Key SPRAY_SHAKE =
            net.kyori.adventure.key.Key.key("vehicle:item.spray_can.shake");

    private final MechanicManager mechanic;
    private final VehicleManager vehicles;

    public MechanicListener(MechanicManager mechanic, VehicleManager vehicles) {
        this.mechanic = mechanic;
        this.vehicles = vehicles;
    }

    /** SprayCanHandler din mod: la selectarea spray can-ului in hotbar se aude un
     *  "shake" cu ton variabil (urca cu pulverizarile ramase). */
    @EventHandler
    public void onHotbarSelect(org.bukkit.event.player.PlayerItemHeldEvent event) {
        ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (MechanicItems.kindOf(next) != MechanicItems.Kind.SPRAY_CAN) {
            return;
        }
        float ratio = MechanicItems.spraysRemainingOf(next)
                / (float) mechanic.sprayCapacity();
        float pitch = 0.85F + 0.15F * ratio;
        Location at = event.getPlayer().getLocation();
        event.getPlayer().playSound(net.kyori.adventure.sound.Sound.sound(SPRAY_SHAKE,
                        net.kyori.adventure.sound.Sound.Source.PLAYER, 0.75F, pitch),
                at.getX(), at.getY(), at.getZ());
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
            case FLUID_MIXER -> {
                event.setCancelled(true);
                if (mechanic.mixers().place(player, face, yaw)) {
                    consumeIfNotCreative(held, creative);
                }
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

    /* ==================== statiile BLOC SOLID (anti-lag) ==================== */

    /** Click-dreapta pe o statie-bloc (banc/elevator). Anulam PRIMA interactiunea:
     *  composterul nu primeste compost, slab-ul nu se waterlog-uieste din galeta. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onStationBlockUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (StationBlocks.kindOf(event.getClickedBlock()) == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (mechanic.handleStationBlockClick(event.getPlayer(), event.getClickedBlock(),
                event.getPlayer().getInventory().getItemInMainHand())) {
            mechanic.save();
        }
    }

    /** Blocurile-statie se SPARG ca blocuri normale (cerinta user): iti dau itemul
     *  inapoi. Exceptiile (cric ridicat/cu masina) le decide managerul. Pickup-ul
     *  cu ghemuit + click dreapta ramane valabil. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (StationBlocks.kindOf(event.getBlock()) != null) {
            mechanic.handleStationBlockBreak(event);
            mechanic.save();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> StationBlocks.kindOf(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(block -> StationBlocks.kindOf(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        for (org.bukkit.block.Block block : event.getBlocks()) {
            if (StationBlocks.kindOf(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        for (org.bukkit.block.Block block : event.getBlocks()) {
            if (StationBlocks.kindOf(block) != null) {
                event.setCancelled(true);
                return;
            }
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
        // Meniul UNIC al mecanicii (refacere 2026-10-24). ATENTIE: holder.stationId()
        // este id-ul BANCULUI, nu al unei masini - rezolvarea pieselor trece prin
        // cricul legat, altfel clickul morfeaza pe masina gresita (bug istoric:
        // vehiculul inexistent aici inchidea meniul uneltelor instant).
        UUID stationId = holder.stationId();
        String action = MechanicGui.actionOf(event.getCurrentItem());
        if (action == null) {
            return;
        }
        if (action.startsWith("take_")) {
            mechanic.workstations().takeTool(player, stationId, action.substring(5));
            return;
        }
        if (action.startsWith("return_")) {
            mechanic.workstations().returnTool(player, stationId, action.substring(7));
            return;
        }
        if ("lift_toggle".equals(action)) {
            // Ridicarea/coborarea masinii din acelasi meniu (cricul legat bancului).
            mechanic.workstations().toggleLiftFromMenu(player, stationId);
            return;
        }
        // Sloturile de piese: target = masina de pe cricul legat, complet ridicata.
        LandVehicle target = mechanic.workstations().menuJack(stationId)
                .filter(jack -> jack.state == Jack.State.LIFTED && jack.vehicleId != null)
                .flatMap(jack -> mechanic.vehicles().byId(jack.vehicleId))
                .orElse(null);
        ItemStack cursor = event.getCursor();
        switch (action) {
            case "engine_slot" -> {
                if (target == null) {
                    player.sendRichMessage("<yellow>Ridica mai intai masina pe cric "
                            + "(butonul de sus din acest meniu).</yellow>");
                    return;
                }
                if (MechanicItems.kindOf(cursor) == MechanicItems.Kind.ENGINE) {
                    mechanic.handleToolClick(player, target, cursor);
                    mechanic.save();
                } else if ((cursor == null || cursor.getType().isAir())
                        && target.engineInstalled()) {
                    mechanic.uninstallEngine(player, target);
                }
                mechanic.gui().openToolsMenu(player, stationId);
            }
            case "wheel_slot" -> {
                if (target == null) {
                    player.sendRichMessage("<yellow>Ridica mai intai masina pe cric "
                            + "(butonul de sus din acest meniu).</yellow>");
                    return;
                }
                if (MechanicItems.kindOf(cursor) == MechanicItems.Kind.WHEEL) {
                    mechanic.handleToolClick(player, target, cursor);
                    mechanic.save();
                } else if ((cursor == null || cursor.getType().isAir())
                        && target.wheelsInstalled()) {
                    mechanic.uninstallWheels(player, target);
                }
                mechanic.gui().openToolsMenu(player, stationId);
            }
            default -> {
            }
        }
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
