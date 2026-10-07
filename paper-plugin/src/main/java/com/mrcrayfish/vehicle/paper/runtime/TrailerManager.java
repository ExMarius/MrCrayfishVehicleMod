package com.mrcrayfish.vehicle.paper.runtime;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.StoredTrailer;
import com.mrcrayfish.vehicle.paper.persistence.TrailerStore;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.TrailerSpec;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/** Lifecycle, hitch graph, persistence and interactions for all five trailers. */
public final class TrailerManager {
    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final TrailerStore store;
    private final Map<UUID, PaperTrailer> trailers = new HashMap<>();
    private final Map<UUID, PaperTrailer> entities = new HashMap<>();
    private final Map<Inventory, PaperTrailer> inventories = new HashMap<>();
    private final Map<UUID, UUID> playerHeldTrailer = new HashMap<>();
    private final Map<UUID, VehicleManager.CarriedVehicle> playerHeldVehicle = new HashMap<>();
    private int entityIndexTicks;

    public TrailerManager(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.store = new TrailerStore(plugin);
    }

    public void start() {
        int releasedPlayerLinks = 0;
        for (StoredTrailer stored : store.loadAll()) {
            TrailerSpec spec = TrailerSpec.byId(stored.type());
            World world = Bukkit.getWorld(stored.worldId());
            if (spec == null || world == null) {
                plugin.getLogger().warning("Deferring unsupported/unavailable trailer " + stored.id());
                continue;
            }
            PaperTrailer.PullerType pullerType = stored.pullerType();
            UUID pullerId = stored.pullerId();
            // A player-held link is session state and has no owner-map entry after restart.
            if (pullerType == PaperTrailer.PullerType.PLAYER) {
                pullerType = PaperTrailer.PullerType.NONE;
                pullerId = null;
                releasedPlayerLinks++;
            }
            spawn(stored.id(), spec,
                    new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), 0.0F),
                    pullerType, pullerId, stored.loadedVehicleId(),
                    stored.fluidMaterial(), stored.fluidAmount(), stored.inventory());
        }
        plugin.getLogger().info("Loaded " + trailers.size() + " trailer(s)."
                + (releasedPlayerLinks == 0 ? "" : " Released " + releasedPlayerLinks
                + " stale player-held link(s)."));
    }

    public void stop() {
        save();
        for (PaperTrailer trailer : new ArrayList<>(trailers.values())) {
            trailer.remove();
        }
        trailers.clear();
        entities.clear();
        inventories.clear();
        playerHeldTrailer.clear();
        playerHeldVehicle.clear();
    }

    public PaperTrailer spawn(TrailerSpec spec, Location location) {
        return spawn(UUID.randomUUID(), spec, location, PaperTrailer.PullerType.NONE,
                null, null, null, 0, null);
    }

    private PaperTrailer spawn(UUID id, TrailerSpec spec, Location location,
                               PaperTrailer.PullerType pullerType, UUID pullerId,
                               UUID loadedVehicleId, Material fluid, int amount, ItemStack[] contents) {
        PaperTrailer trailer = new PaperTrailer(this, id, spec, location, pullerType, pullerId,
                loadedVehicleId, fluid, amount, contents);
        trailers.put(id, trailer);
        for (Entity entity : trailer.rig().entities()) {
            entities.put(entity.getUniqueId(), trailer);
        }
        if (trailer.inventory() != null) {
            inventories.put(trailer.inventory(), trailer);
        }
        return trailer;
    }

    public void tick() {
        for (PaperTrailer trailer : new ArrayList<>(trailers.values())) {
            try {
                trailer.tick();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Trailer tick failed for " + trailer.id(), exception);
            }
        }
        /* Fertilizer/Seeder cargo displays are rebuilt only when inventory
         * contents change. Refresh the reverse index periodically so newly
         * created tagged displays participate in normal lifecycle handling. */
        if (++entityIndexTicks >= 20) {
            entityIndexTicks = 0;
            entities.clear();
            for (PaperTrailer trailer : trailers.values()) {
                for (Entity entity : trailer.rig().entities()) {
                    entities.put(entity.getUniqueId(), trailer);
                }
            }
        }
    }

    public void save() {
        store.saveAll(trailers.values());
    }

    public boolean remove(PaperTrailer trailer) {
        if (trailers.remove(trailer.id()) == null) {
            return false;
        }
        releaseLoadedVehicle(trailer);
        for (PaperTrailer child : trailers.values()) {
            if (child.pullerType() == PaperTrailer.PullerType.TRAILER
                    && trailer.id().equals(child.pullerId())) {
                child.detach(false);
            }
        }
        playerHeldTrailer.values().removeIf(trailer.id()::equals);
        trailer.rig().entities().forEach(entity -> entities.remove(entity.getUniqueId()));
        if (trailer.inventory() != null) {
            inventories.remove(trailer.inventory());
            dropInventory(trailer);
        }
        trailer.remove();
        save();
        return true;
    }

    public Optional<PaperTrailer> byEntity(Entity entity) {
        return Optional.ofNullable(entities.get(entity.getUniqueId()));
    }

    public Optional<PaperTrailer> byId(UUID id) {
        return Optional.ofNullable(trailers.get(id));
    }

    public Collection<PaperTrailer> trailers() {
        return Collections.unmodifiableCollection(trailers.values());
    }

    public Optional<PaperTrailer> nearest(Location origin, double maximumDistance) {
        PaperTrailer nearest = null;
        double best = maximumDistance * maximumDistance;
        for (PaperTrailer trailer : trailers.values()) {
            if (!java.util.Objects.equals(origin.getWorld(), trailer.location().getWorld())) {
                continue;
            }
            double distance = origin.distanceSquared(trailer.location());
            if (distance <= best) {
                best = distance;
                nearest = trailer;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public void handleInteraction(Player player, Entity clicked) {
        PaperTrailer trailer = entities.get(clicked.getUniqueId());
        if (trailer == null) {
            return;
        }
        if (!player.hasPermission("vehicle.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea vehicle.use.</red>");
            return;
        }
        if (attachHeldToTrailer(player, trailer)) {
            return;
        }
        if (player.isSneaking()) {
            hitchInteraction(player, trailer);
        } else {
            trailer.interact(player);
        }
    }

    private boolean attachHeldToTrailer(Player player, PaperTrailer clicked) {
        PaperTrailer held = heldTrailer(player.getUniqueId());
        if (held == null || held == clicked || !clicked.spec().canTowTrailers()) {
            return false;
        }
        if (hasChild(clicked.id())) {
            player.sendRichMessage("<red>Acest Storage Trailer tractează deja altă remorcă.</red>");
            return true;
        }
        if (wouldCreateCycle(held, clicked.id())) {
            player.sendRichMessage("<red>Nu poți crea un lanț circular de remorci.</red>");
            return true;
        }
        held.attach(PaperTrailer.PullerType.TRAILER, clicked.id());
        playerHeldTrailer.remove(player.getUniqueId());
        player.sendRichMessage("<green>Remorcă atașată în lanț la Storage Trailer.</green>");
        playHitch(clicked.location());
        return true;
    }

    private void hitchInteraction(Player player, PaperTrailer clicked) {
        if (clicked.spec().kind() == TrailerSpec.Kind.VEHICLE && mountCarriedVehicle(player, clicked)) {
            return;
        }
        PaperTrailer held = heldTrailer(player.getUniqueId());
        if (!canPlayerClaimTrailer(held == null ? null : held.id(), clicked.id())) {
            player.sendRichMessage("<red>Tragi deja o remorcă. Las-o jos sau atașeaz-o înainte să ridici alta.</red>");
            return;
        }
        clicked.detach(false);
        clicked.attach(PaperTrailer.PullerType.PLAYER, player.getUniqueId());
        playerHeldTrailer.put(player.getUniqueId(), clicked.id());
        player.sendRichMessage("<yellow>Tragi remorca. Click dreapta pe Lawn Mower, Quad Bike, Tractor sau Storage Trailer pentru atașare; apasă din nou Shift pentru a o lăsa jos.</yellow>");
        playHitch(clicked.location());
    }

    private PaperTrailer heldTrailer(UUID playerId) {
        UUID heldId = playerHeldTrailer.get(playerId);
        PaperTrailer held = heldId == null ? null : trailers.get(heldId);
        if (held == null || held.pullerType() != PaperTrailer.PullerType.PLAYER
                || !playerId.equals(held.pullerId())) {
            if (heldId != null) {
                playerHeldTrailer.remove(playerId, heldId);
            }
            return null;
        }
        return held;
    }

    static boolean canPlayerClaimTrailer(UUID currentlyHeldId, UUID clickedId) {
        return currentlyHeldId == null || currentlyHeldId.equals(clickedId);
    }

    static boolean ownsPlayerPullLink(UUID trackedTrailerId, UUID trailerId) {
        return Objects.equals(trackedTrailerId, trailerId);
    }

    /**
     * Mirrors CommonEvents#onPlayerTick from the original mod: pressing crouch
     * while pulling a trailer clears the player/trailer link and leaves the
     * trailer at its current physical position.
     */
    public boolean releaseHeldTrailer(Player player) {
        UUID trailerId = playerHeldTrailer.remove(player.getUniqueId());
        if (trailerId == null) {
            return false;
        }
        PaperTrailer trailer = trailers.get(trailerId);
        if (trailer == null) {
            return false;
        }
        if (trailer.pullerType() == PaperTrailer.PullerType.PLAYER
                && player.getUniqueId().equals(trailer.pullerId())) {
            trailer.detach(false);
            save();
            player.sendRichMessage("<green>Remorca a fost lăsată jos.</green>");
            return true;
        }
        return false;
    }

    public boolean pickUpVehicle(Player player, LandVehicle vehicle) {
        if (playerHeldVehicle.containsKey(player.getUniqueId())) {
            player.sendRichMessage("<red>Porți deja un vehicul.</red>");
            return true;
        }
        VehicleManager.CarriedVehicle carried = vehicles.pickUp(vehicle);
        if (carried == null) {
            player.sendRichMessage("<red>Vehiculul este ocupat și nu poate fi ridicat.</red>");
            return true;
        }
        playerHeldVehicle.put(player.getUniqueId(), carried);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        String placement = vehicle.spec().motionType() == LandVehicleSpec.MotionType.WATER
                ? "apă cu ambele mâini goale" : "sol";
        player.sendRichMessage("<yellow>Vehicul ridicat. Shift-click pe Vehicle Trailer sau click dreapta pe "
                + placement + ".</yellow>");
        return true;
    }

    public boolean isCarryingWaterVehicle(Player player) {
        VehicleManager.CarriedVehicle carried = playerHeldVehicle.get(player.getUniqueId());
        return carried != null && carried.spec().motionType() == LandVehicleSpec.MotionType.WATER;
    }

    public boolean placeCarriedVehicle(Player player, Location location) {
        VehicleManager.CarriedVehicle carried = playerHeldVehicle.remove(player.getUniqueId());
        if (carried == null) {
            return false;
        }
        vehicles.place(carried, location);
        player.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_STRONG,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        player.sendRichMessage("<green>Vehicul așezat.</green>");
        return true;
    }

    private boolean mountCarriedVehicle(Player player, PaperTrailer trailer) {
        VehicleManager.CarriedVehicle carried = playerHeldVehicle.get(player.getUniqueId());
        if (carried == null) {
            return false;
        }
        if (trailer.loadedVehicleId() != null) {
            player.sendRichMessage("<red>Vehicle Trailer transportă deja un vehicul.</red>");
            return true;
        }
        LandVehicle vehicle = vehicles.place(carried, trailer.location());
        playerHeldVehicle.remove(player.getUniqueId());
        trailer.setLoadedVehicleId(vehicle.id());
        positionLoadedVehicle(trailer, vehicle.id());
        playHitch(trailer.location());
        player.sendRichMessage("<green>" + vehicle.spec().displayName() + " încărcat pe Vehicle Trailer.</green>");
        return true;
    }

    public boolean attachHeldToVehicle(Player player, LandVehicle vehicle) {
        PaperTrailer trailer = heldTrailer(player.getUniqueId());
        if (trailer == null) {
            return false;
        }
        if (!vehicle.spec().canTowTrailers()) {
            player.sendRichMessage("<red>Acest vehicul nu poate tracta remorci.</red>");
            return true;
        }
        if (hasVehicleChild(vehicle.id())) {
            player.sendRichMessage("<red>Vehiculul tractează deja o remorcă.</red>");
            return true;
        }
        trailer.attach(PaperTrailer.PullerType.VEHICLE, vehicle.id());
        playerHeldTrailer.remove(player.getUniqueId());
        player.sendRichMessage("<green>Remorcă cuplată la " + vehicle.spec().displayName() + ".</green>");
        playHitch(vehicle.location());
        return true;
    }

    boolean pullerTemporarilyUnavailable(PaperTrailer trailer) {
        return trailer.pullerType() == PaperTrailer.PullerType.VEHICLE
                && trailer.pullerId() != null
                && vehicles.hasVehicleId(trailer.pullerId())
                && vehicles.byId(trailer.pullerId()).isEmpty();
    }

    PaperTrailer.PullTarget resolvePullTarget(PaperTrailer trailer) {
        UUID pullerId = trailer.pullerId();
        if (pullerId == null) {
            return null;
        }
        return switch (trailer.pullerType()) {
            case PLAYER -> {
                if (!ownsPlayerPullLink(playerHeldTrailer.get(pullerId), trailer.id())) {
                    yield null;
                }
                Player player = Bukkit.getPlayer(pullerId);
                yield player == null || !player.isOnline() ? null
                        : new PaperTrailer.PullTarget(player.getLocation(), player.getLocation());
            }
            case VEHICLE -> vehicles.byId(pullerId)
                    .filter(vehicle -> vehicle.spec().canTowTrailers())
                    .map(vehicle -> new PaperTrailer.PullTarget(vehicle.towBarLocation(), vehicle.location())).orElse(null);
            case TRAILER -> Optional.ofNullable(trailers.get(pullerId))
                    .filter(parent -> parent.spec().canTowTrailers())
                    .map(parent -> new PaperTrailer.PullTarget(parent.towBarLocation(), parent.location())).orElse(null);
            case NONE -> null;
        };
    }

    public ItemStack storeMowerDrop(UUID vehicleId, ItemStack original) {
        ItemStack remainder = original.clone();
        PaperTrailer current = trailers.values().stream()
                .filter(trailer -> trailer.pullerType() == PaperTrailer.PullerType.VEHICLE
                        && vehicleId.equals(trailer.pullerId())
                        && trailer.spec().kind() == TrailerSpec.Kind.STORAGE)
                .findFirst().orElse(null);
        Set<UUID> visited = new HashSet<>();
        while (current != null && visited.add(current.id())) {
            Map<Integer, ItemStack> leftovers = current.inventory().addItem(remainder);
            if (leftovers.isEmpty()) {
                return null;
            }
            remainder = leftovers.values().iterator().next();
            UUID parentId = current.id();
            current = trailers.values().stream()
                    .filter(trailer -> trailer.pullerType() == PaperTrailer.PullerType.TRAILER
                            && parentId.equals(trailer.pullerId())
                            && trailer.spec().kind() == TrailerSpec.Kind.STORAGE)
                    .findFirst().orElse(null);
        }
        return remainder;
    }

    ItemStack findUpstreamSupply(PaperTrailer trailer, Predicate<ItemStack> predicate) {
        Set<UUID> visited = new HashSet<>();
        PaperTrailer current = trailer;
        while (current.pullerType() == PaperTrailer.PullerType.TRAILER && current.pullerId() != null
                && visited.add(current.pullerId())) {
            current = trailers.get(current.pullerId());
            if (current == null) {
                return null;
            }
            if (current.spec().kind() == TrailerSpec.Kind.STORAGE && current.inventory() != null) {
                for (ItemStack stack : current.inventory().getStorageContents()) {
                    if (stack != null && !stack.getType().isAir() && predicate.test(stack)) {
                        return stack;
                    }
                }
            }
        }
        return null;
    }

    void toggleVehicleLoad(PaperTrailer trailer, Player player) {
        if (trailer.loadedVehicleId() != null) {
            player.sendRichMessage("<yellow>Shift-click pe vehiculul de pe platformă pentru a-l ridica, exact ca în mod.</yellow>");
        } else {
            player.sendRichMessage("<yellow>Shift-click pe un vehicul liber, apoi shift-click pe Vehicle Trailer.</yellow>");
        }
    }

    void positionLoadedVehicle(PaperTrailer trailer, UUID vehicleId) {
        LandVehicle vehicle = vehicles.byId(vehicleId).orElse(null);
        if (vehicle == null) {
            return;
        }
        vehicle.placeOnTrailer(trailer.location());
    }

    private void releaseLoadedVehicle(PaperTrailer trailer) {
        UUID loaded = trailer.loadedVehicleId();
        if (loaded == null) {
            return;
        }
        vehicles.byId(loaded).ifPresent(vehicle -> {
            Location release = PaperTrailer.local(trailer.location(), trailer.spec().entityWidth() + 1.0D,
                    0.2D, 0.0D, trailer.location().getYaw());
            vehicle.releaseFromTrailer(release);
        });
        trailer.setLoadedVehicleId(null);
    }

    public boolean referencesVehicle(UUID vehicleId) {
        return trailers.values().stream().anyMatch(trailer ->
                (trailer.pullerType() == PaperTrailer.PullerType.VEHICLE && vehicleId.equals(trailer.pullerId()))
                        || vehicleId.equals(trailer.loadedVehicleId()));
    }

    public void onVehicleRemoved(UUID vehicleId) {
        for (PaperTrailer trailer : trailers.values()) {
            if (trailer.pullerType() == PaperTrailer.PullerType.VEHICLE && vehicleId.equals(trailer.pullerId())) {
                trailer.detach(false);
            }
            if (vehicleId.equals(trailer.loadedVehicleId())) {
                trailer.setLoadedVehicleId(null);
            }
        }
    }

    public void onPlayerQuit(Player player) {
        UUID trailerId = playerHeldTrailer.remove(player.getUniqueId());
        PaperTrailer trailer = trailerId == null ? null : trailers.get(trailerId);
        if (trailer != null && trailer.pullerType() == PaperTrailer.PullerType.PLAYER
                && player.getUniqueId().equals(trailer.pullerId())) {
            trailer.detach(false);
        }
        VehicleManager.CarriedVehicle carried = playerHeldVehicle.remove(player.getUniqueId());
        if (carried != null) {
            vehicles.place(carried, player.getLocation());
        }
    }

    public void returnCarriedVehicles() {
        // Player pull links are transient. Clear every such link, including any
        // orphan created by an older version overwriting playerHeldTrailer.
        for (PaperTrailer trailer : trailers.values()) {
            if (trailer.pullerType() == PaperTrailer.PullerType.PLAYER) {
                trailer.detach(false);
            }
        }
        playerHeldTrailer.clear();
        for (Map.Entry<UUID, VehicleManager.CarriedVehicle> entry
                : new ArrayList<>(playerHeldVehicle.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                vehicles.place(entry.getValue(), player.getLocation());
            }
        }
        playerHeldVehicle.clear();
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        PaperTrailer trailer = inventories.get(event.getView().getTopInventory());
        if (trailer == null) {
            return;
        }
        ItemStack entering = null;
        if (event.isShiftClick() && event.getClickedInventory() == event.getView().getBottomInventory()) {
            entering = event.getCurrentItem();
        } else if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            entering = event.getCursor();
            if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
                entering = player.getInventory().getItem(event.getHotbarButton());
            }
        }
        if (!trailer.accepts(entering)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                String message = switch (trailer.spec().kind()) {
                    case FERTILIZER -> "Fertilizer accepts only bone meal.";
                    case SEEDER -> "Seeder accepts only the original seed-tag items.";
                    case STORAGE -> "The original Storage Trailer GUI accepts bone meal; mower drops are inserted automatically.";
                    default -> "This item is not accepted.";
                };
                player.sendActionBar(net.kyori.adventure.text.Component.text(message));
            }
        }
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        PaperTrailer trailer = inventories.get(event.getView().getTopInventory());
        if (trailer == null || trailer.accepts(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private boolean hasVehicleChild(UUID vehicleId) {
        return trailers.values().stream().anyMatch(trailer -> trailer.pullerType() == PaperTrailer.PullerType.VEHICLE
                && vehicleId.equals(trailer.pullerId()));
    }

    private boolean hasChild(UUID trailerId) {
        return trailers.values().stream().anyMatch(trailer -> trailer.pullerType() == PaperTrailer.PullerType.TRAILER
                && trailerId.equals(trailer.pullerId()));
    }

    private boolean wouldCreateCycle(PaperTrailer child, UUID proposedParent) {
        UUID current = proposedParent;
        Set<UUID> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(child.id())) {
                return true;
            }
            PaperTrailer parent = trailers.get(current);
            if (parent == null || parent.pullerType() != PaperTrailer.PullerType.TRAILER) {
                return false;
            }
            current = parent.pullerId();
        }
        return current != null;
    }

    private static void playHitch(Location location) {
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.9F, 0.8F);
        }
    }

    private static void dropInventory(PaperTrailer trailer) {
        World world = trailer.location().getWorld();
        if (world == null || trailer.inventory() == null) {
            return;
        }
        for (ItemStack stack : trailer.inventory().getStorageContents()) {
            if (stack != null && !stack.getType().isAir()) {
                world.dropItemNaturally(trailer.location(), stack);
            }
        }
    }
}
