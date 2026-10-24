package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.MechanicStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bancul de lucru (echivalentul WorkstationBlock din mod) ca BLOC SOLID real -
 * misiunea anti-lag pentru 200+ vehicule: NICIO entitate, niciun tick per statie.
 * Doar o sarcina RARA (2 secunde) pentru lezul cheilor, exact ca inainte.
 * Regulile de zona raman identice cu varianta pe entitati:
 *  1) se pune DOAR daca exista un cric/elevator la maxim workstation.max-distance-to-jack
 *     (implicit 5) blocuri; se leaga 1:1 de cel mai apropiat cric;
 *  2) de la un banc pot lua cheie de mecanic MAXIM workstation.max-wrenches (implicit 2)
 *     jucatori, fiecare cate o SINGURA cheie;
 *  3) cheia data de un banc DISPARA din inventar cand jucatorul iese la mai mult de
 *     workstation.service-radius (implicit 10) blocuri de banc;
 *  4) bancul si cricul sunt accesibile TUTUROR (nu doar plasatorului);
 *  5) ghemuit + click dreapta = ridicarea bancului DOAR de catre plasator sau un operator.
 * Blocul folosit = note_block suprascris in pack (vezi StationBlocks): clicul-dreapta
 * e anulat (nu se retuneaza), pling-ul de redstone si spargerea directa sunt blocate.
 */
public final class WorkstationManager {
    private static final String ENTITY_TAG = "mcv_workstation"; // doar pentru curatarea rigurilor vechi
    private static final long CLICK_COOLDOWN_MS = 2_000L;

    /** Starea runtime a unui banc pus in lume: positions.goian <=> bloc solid. */
    static final class Station {
        final UUID id;
        final Location location; // centrul blocului (x+0.5, y, z+0.5)
        /** Cricul/elevatorul de care bancul e legat 1:1 (null = orfan). */
        UUID jackId;
        /** Cine a pus bancul - pentru pickup (el sau un operator). */
        UUID ownerId;
        /** Jucatorii care au primit cheie de la banc (max workstations.max-wrenches). */
        final Set<UUID> recipients = new LinkedHashSet<>();
        /** Binding (brief 2026-10-24): cine are ciocanul / pistolul de vopsea ale
         *  bancului (o singura instanta fiecare). STRICT RAM - revin liberi la
         *  restart/quit/wipe (vezi handleQuit/handleJoin/leashTools). */
        UUID hammerHolder;
        int hammerUsed;

        Station(UUID id, Location location, UUID jackId, UUID ownerId) {
            this.id = id;
            this.location = location;
            this.jackId = jackId;
            this.ownerId = ownerId;
        }

        Block block() {
            return location.getBlock();
        }
    }

    private final VehiclePlugin plugin;
    private final MechanicStore store;
    private final JackManager jacks;
    private final Map<UUID, Station> stations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private BukkitTask leashTask;

    public WorkstationManager(VehiclePlugin plugin, MechanicStore store, JackManager jacks) {
        this.plugin = plugin;
        this.store = store;
        this.jacks = jacks;
    }

    private double maxJackDistance() {
        return plugin.getConfig().getDouble("workstation.max-distance-to-jack", 5.0D);
    }

    private int maxWrenches() {
        // Brief 2026-10-24 (refacere): EXACT O cheie per banc, in orice moment.
        return 1;
    }

    /** Deschide meniul uneltelor unui banc; refresh pentru ceilalti vizitatori. */
    private java.util.function.BiConsumer<Player, UUID> toolsOpener;
    private java.util.function.Consumer<UUID> toolsRefresher;

    public void setToolsOpener(java.util.function.BiConsumer<Player, UUID> opener) {
        this.toolsOpener = opener;
    }

    public void setToolsRefresher(java.util.function.Consumer<UUID> refresher) {
        this.toolsRefresher = refresher;
    }

    private void refreshTools(UUID stationId) {
        if (toolsRefresher != null) {
            toolsRefresher.accept(stationId);
        }
    }

    private double serviceRadius() {
        return plugin.getConfig().getDouble("workstation.service-radius", 10.0D);
    }

    public void start() {
        // Curatare O SINGURA DATA a rigurilor vechi pe entitati (migrare la blocuri solide).
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ENTITY_TAG)) {
                    entity.remove();
                }
            }
        }
        load();
        // Lezul cheilor: RAR (2 secunde), doar pentru jucatorii care au vreo cheie de
        // mecanic in inventar - niciodata in fiecare tick, niciodata pentru toti.
        leashTask = Bukkit.getScheduler().runTaskTimer(plugin, this::leashWrenches, 40L, 40L);
    }

    public void stop() {
        if (leashTask != null) {
            leashTask.cancel();
        }
        stations.clear();
    }

    private void load() {
        for (MechanicStore.StoredStation stored : store.loadStations("workstations")) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            Station station = placeBlockRig(stored.id(), new Location(world,
                    stored.x(), stored.y(), stored.z()));
            station.jackId = stored.jackId();
            station.ownerId = stored.ownerId();
            stations.put(station.id, station);
        }
    }

    public List<MechanicStore.StoredStation> snapshot() {
        List<MechanicStore.StoredStation> stored = new ArrayList<>();
        for (Station station : stations.values()) {
            Location location = station.location;
            stored.add(new MechanicStore.StoredStation(station.id, "workstation",
                    location.getWorld().getUID(), location.getX(), location.getY(),
                    location.getZ(), 0.0F, station.jackId, station.ownerId,
                    java.util.List.of()));
        }
        return stored;
    }

    Optional<Station> byId(UUID id) {
        return Optional.ofNullable(stations.get(id));
    }

    /** Statia de la aceasta pozitie de bloc (sau gol daca blocul nu e al nostru). */
    public boolean isStationBlock(Block block) {
        return findByBlock(block).isPresent();
    }

    private Optional<Station> findByBlock(Block block) {
        if (block == null || StationBlocks.kindOf(block) != StationBlocks.Kind.WORKSTATION) {
            return Optional.empty();
        }
        for (Station station : stations.values()) {
            if (station.location.getWorld() == block.getWorld()
                    && station.block().getLocation().equals(block.getLocation())) {
                return Optional.of(station);
            }
        }
        return Optional.empty();
    }

    /* ============================ plasare / rig ============================ */

    /** Regula de zona #1: fara cric in raza de max-distance-to-jack, bancul NU se pune. */
    public boolean place(Player player, Location clickedFace, float yaw) {
        Block target = clickedFace.getBlock();
        if (!target.getType().isAir() && !target.isReplaceable()) {
            player.sendRichMessage("<red>Aici nu e loc liber pentru bancul de lucru.</red>");
            return false;
        }
        Location ground = target.getLocation().add(0.5D, 0.0D, 0.5D);
        UUID nearestJack = null;
        double nearest = Double.MAX_VALUE;
        double limit = maxJackDistance();
        for (Jack jack : jacks.jacks()) {
            if (jack.location.getWorld() != ground.getWorld()) {
                continue;
            }
            double distance = jack.location.distance(ground);
            if (distance < nearest) {
                nearest = distance;
                nearestJack = jack.id;
            }
        }
        if (nearestJack == null || nearest > limit) {
            player.sendRichMessage("<red>Bancul are nevoie de un <gold>cric</gold> la maxim "
                    + (int) limit + " blocuri - pune intai cricul, apoi masa de lucru.</red>");
            return false;
        }
        // Regula (brief): UN SINGUR banc de lucru per elevator (1:1 strict).
        if (jackHasBench(nearestJack)) {
            player.sendRichMessage("<red>Acest elevator are deja un banc de lucru "
                    + "asociat - un cric = un singur banc.</red>");
            return false;
        }
        Station station = placeBlockRig(UUID.randomUUID(), target.getLocation());
        station.jackId = nearestJack;
        station.ownerId = player.getUniqueId();
        stations.put(station.id, station);
        player.sendRichMessage("<gray>Banc de lucru pus jos (<gold>legat</gold> de cricul de langa). "
                + "Click pe el = cheia de mecanic.</gray>");
        return true;
    }

    /** In locul rigului ItemDisplay+Interaction: un bloc solid care nu costa serverul NIMIC. */
    private Station placeBlockRig(UUID id, Location blockPos) {
        Block block = blockPos.getBlock();
        block.setBlockData(StationBlocks.WORKSTATION, false);
        Location center = block.getLocation().add(0.5D, 0.0D, 0.5D);
        return new Station(id, center, null, null);
    }

    /* ============================ click pe bloc ============================ */

    /** Click-dreapta pe bancul-bloc (apelat din MechanicListener dupa anularea
     *  tune-arii note_block):
     *   - ghemuit = ridicare inapoi (doar plasator/op);
     *   - cu CHEIA in mana = ridicare/coborare a masinii de pe cricul legat (ca in mod);
     *   - cu mana goala = primesti cheia de mecanic, cu limitele de zona. */
    public boolean handleBlockClick(Player player, Block block, ItemStack held) {
        Optional<Station> found = findByBlock(block);
        if (found.isEmpty()) {
            return false;
        }
        Station station = found.get();
        if (player.isSneaking()) {
            // Brief: ghemuit + click dreapta pe banc = NIMIC (bancul se ia doar
            // prin spargerea normala a blocului, ca orice bloc - vezi handleBlockBreak).
            return true;
        }
        if (MechanicItems.kindOf(held) == MechanicItems.Kind.WRENCH) {
            handleLiftToggle(player, station);
            return true;
        }
        if (!MechanicManager.has(player, "mcv.workstation.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.workstation.use.</red>");
            return true;
        }
        long now = System.currentTimeMillis();
        Long previous = cooldowns.get(player.getUniqueId());
        if (previous != null && now - previous < CLICK_COOLDOWN_MS) {
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);
        // Rack-ul uneltelor (brief 2026-10-24): clickul simplu DESCHIDE meniul
        // bancului; uneltele se iau/se returneaza de acolo, cu stare de ocupare.
        if (toolsOpener != null) {
            toolsOpener.accept(player, station.id);
        }
        return true;
    }

    /* ==================== rack-ul uneltelor (binding, RAM-only) ==================== */

    /** Viitoare unelte ale bancului care sunt libere chiar acum (pt. GUI/API). */
    public UUID wrenchHolderOf(Station station, int index) {
        return station.recipients.size() > index
                ? (UUID) station.recipients.toArray()[index] : null;
    }

    public UUID hammerHolderOf(Station station) {
        return station.hammerHolder;
    }

    public int hammerUsedOf(Station station) {
        return station.hammerUsed;
    }

    /** Parcurge toate bancurile (pt. meniul uneltelor, ce identifica station). */
    public Optional<Station> stationOf(UUID id) {
        return byId(id);
    }

    /** Luați o unealta din rack. tool = "wrench"|"hammer"|"spray". */
    public void takeTool(Player player, UUID stationId, String tool) {
        Optional<Station> found = byId(stationId);
        if (found.isEmpty()) {
            player.sendRichMessage("<red>Acest banc nu mai exista.</red>");
            return;
        }
        Station station = found.get();
        if (!MechanicManager.has(player, "mcv.workstation.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.workstation.use.</red>");
            return;
        }
        switch (tool) {
            case "wrench" -> {
                // Regula: o singura cheie DE LA ACEST banc per jucator.
                for (ItemStack contents : player.getInventory().getContents()) {
                    UUID origin = MechanicItems.wrenchStationOf(contents);
                    if (origin != null && origin.equals(station.id)) {
                        player.sendRichMessage("<yellow>Ai deja cheia acestui banc in inventar.</yellow>");
                        return;
                    }
                }
                boolean isRecipient = station.recipients.contains(player.getUniqueId());
                if (!isRecipient) {
                    if (station.recipients.size() >= maxWrenches()) {
                        player.sendRichMessage("<red>Acest banc si-a dat deja toate cheile ("
                                + maxWrenches() + " mecanici). Asteapta sa le intoarca cineva.</red>");
                        return;
                    }
                    station.recipients.add(player.getUniqueId());
                }
                player.getInventory().addItem(MechanicItems.wrench(station.id));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.0F);
                player.sendRichMessage("<gray>Ai luat <gold>cheia de mecanic</gold>. "
                        + "Ramai in zona de service!</gray>");
            }
            case "hammer" -> {
                if (station.hammerHolder != null) {
                    String who = holderName(station.hammerHolder);
                    player.sendRichMessage("<red>Ciocanul e ocupat de <gold>" + who + "</gold>.</red>");
                    return;
                }
                station.hammerHolder = player.getUniqueId();
                player.getInventory().addItem(MechanicItems.hammer(station.id, station.hammerUsed));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.0F);
                player.sendRichMessage("<gray>Ai luat <gold>ciocanul de lacatus</gold>. "
                        + "Ramai in zona de service!</gray>");
            }
            default -> {
                return;
            }
        }
        refreshTools(station.id);
    }

    /** Returnezi manual unealta tinuta de tine (click pe slotul tau ocupat in GUI). */
    public void returnTool(Player player, UUID stationId, String tool) {
        Optional<Station> found = byId(stationId);
        if (found.isEmpty()) {
            return;
        }
        Station station = found.get();
        ItemStack[] contents = player.getInventory().getContents();
        boolean removed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !station.id.equals(MechanicItems.benchToolStationOf(stack))) {
                continue;
            }
            MechanicItems.Kind kind = MechanicItems.kindOf(stack);
            boolean matches = ("wrench".equals(tool) && kind == MechanicItems.Kind.WRENCH)
                    || ("hammer".equals(tool) && kind == MechanicItems.Kind.HAMMER);
            if (!matches) {
                continue;
            }
            if ("hammer".equals(tool)) {
                station.hammerUsed = MechanicItems.HAMMER_MAX_DAMAGE
                        - MechanicItems.hammerUsesLeft(stack);
            }
            player.getInventory().clear(slot);
            removed = true;
            break;
        }
        if (!removed) {
            player.sendRichMessage("<yellow>Nu ai aceasta unealta in inventar.</yellow>");
            return;
        }
        switch (tool) {
            case "wrench" -> station.recipients.remove(player.getUniqueId());
            case "hammer" -> station.hammerHolder = null;
            default -> {
                return;
            }
        }
        player.sendRichMessage("<gray>Ai intors unealta la banc.</gray>");
        refreshTools(station.id);
    }

    private static String holderName(UUID holder) {
        String name = Bukkit.getOfflinePlayer(holder).getName();
        return name == null ? "altcineva" : name;
    }

    /** Quit: unealta revine la banc IMEDIAT (brief: returnare automata la deconectare).
     *  Itemul in sine dispare cu jucatorul; la relog e sters de handleJoin (wipe). */
    public void handleQuit(Player player) {
        for (Station station : stations.values()) {
            boolean changed = station.recipients.remove(player.getUniqueId());
            if (player.getUniqueId().equals(station.hammerHolder)) {
                for (ItemStack stack : player.getInventory().getContents()) {
                    if (stack != null && station.id.equals(MechanicItems.benchToolStationOf(stack))
                            && MechanicItems.kindOf(stack) == MechanicItems.Kind.HAMMER) {
                        station.hammerUsed = MechanicItems.HAMMER_MAX_DAMAGE
                                - MechanicItems.hammerUsesLeft(stack);
                    }
                }
                station.hammerHolder = null;
                changed = true;
            }
            if (changed) {
                refreshTools(station.id);
            }
        }
    }

    /** Wipe la join: uneltele legate NU supravietuiesc relog-ului/restart-ului
     *  (brief: strict RAM; altfel ar putea exista duplicate fantoma). */
    public void handleJoin(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        int removed = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            if (MechanicItems.benchToolStationOf(contents[slot]) != null) {
                player.getInventory().clear(slot);
                removed++;
            }
        }
        if (removed > 0) {
            player.sendRichMessage("<yellow>Uneltele de banc s-au intors la bancurile lor "
                    + "(nu pot fi pastrate intre sesiuni).</yellow>");
        }
    }

    /** Cheia pe banc: ridici/cobori masina aflata deasupra cricului legat. */
    private void handleLiftToggle(Player player, Station station) {
        Optional<Jack> jack = resolveJack(station);
        if (jack.isEmpty()) {
            player.sendRichMessage("<red>Bancul nu mai are cric liber in zona - pune unul "
                    + "langa el (cricurile din zona au deja bancuri asociate).</red>");
            return;
        }
        jacks.toggleLift(player, jack.get());
    }

    /** Apelat de hook-ul cricurilor: la orice schimbare de stare a cricului legat
     *  reimprospatam meniurile bancului (butonul RIDICA/COBOARA isi revine singur). */
    public void refreshByJack(UUID jackId) {
        byJackId(jackId).ifPresent(station -> refreshTools(station.id));
    }

    /** Cricul are deja un banc asociat? (regula 1:1 - un singur banc pe elevator) */
    private boolean jackHasBench(UUID jackId) {
        for (Station station : stations.values()) {
            if (jackId.equals(station.jackId)) {
                return true;
            }
        }
        return false;
    }

    /** Cricul legat de banc; daca a disparut, bancul se re-lega la cel mai
     *  apropiat cric LIBER din raza maxima (1:1: niciodata la unul cu banc deja). */
    private Optional<Jack> resolveJack(Station station) {
        Optional<Jack> jack = station.jackId == null ? Optional.empty() : jacks.byId(station.jackId);
        if (jack.isPresent()) {
            return jack;
        }
        double nearest = Double.MAX_VALUE;
        Jack found = null;
        for (Jack candidate : jacks.jacks()) {
            if (candidate.location.getWorld() != station.location.getWorld()) {
                continue;
            }
            if (jackHasBench(candidate.id)) {
                continue; // 1 cric = 1 banc, nu furam bancul altuia
            }
            double distance = candidate.location.distance(station.location);
            if (distance < nearest) {
                nearest = distance;
                found = candidate;
            }
        }
        if (found == null || nearest > maxJackDistance()) {
            return Optional.empty();
        }
        station.jackId = found.id;
        return Optional.of(found);
    }

    /** Butonul RIDICA/COBOARA din meniul uneltelor (brief F: ridicarea se face din
     *  acelasi GUI din care iei ciocanul si cheia, nu doar cu cheia pe cric). */
    public void toggleLiftFromMenu(Player player, UUID stationId) {
        Optional<Station> found = byId(stationId);
        if (found.isEmpty()) {
            player.sendRichMessage("<red>Acest banc nu mai exista.</red>");
            return;
        }
        handleLiftToggle(player, found.get());
        refreshTools(found.get().id);
    }

    /** Informatii pentru butonul de cric din meniu (stare + masina, daca e). */
    public Optional<Jack> menuJack(UUID stationId) {
        return byId(stationId).flatMap(this::resolveJack);
    }

    /** Bancul legat de un anumit cric (pt. cheia pe masina ridicata). */
    public Optional<Station> byJackId(UUID jackId) {
        if (jackId == null) {
            return Optional.empty();
        }
        for (Station station : stations.values()) {
            if (jackId.equals(station.jackId)) {
                return Optional.of(station);
            }
        }
        return Optional.empty();
    }

    /** Cel mai apropiat banc de o locatie, in raza data (sau gol). */
    public Optional<Station> nearestStation(Location origin, double radius) {
        Station found = null;
        double nearest = radius * radius;
        for (Station station : stations.values()) {
            if (station.location.getWorld() != origin.getWorld()) {
                continue;
            }
            double distance = station.location.distanceSquared(origin);
            if (distance <= nearest) {
                nearest = distance;
                found = station;
            }
        }
        return Optional.ofNullable(found);
    }

    /** Raza de service expusa public (pt. fluxul cheii pe masina). */
    public double serviceRadiusPublic() {
        return serviceRadius();
    }

    private void pickup(Station station) {
        station.block().setType(Material.AIR, false);
        stations.remove(station.id);
    }

    /** Spart manual ca un bloc normal (cerinta user): iti DA itemul inapoi si statia
     *  iese din registru. Cheile emise de el sunt stranse la urmatorul lez (2 sec),
     *  pentru ca bancul de origine a disparut. Dropul vanilla de note_block nu exista. */
    public void handleBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Optional<Station> found = findByBlock(event.getBlock());
        event.setDropItems(false);
        if (found.isEmpty()) {
            return;
        }
        Station station = found.get();
        stations.remove(station.id);
        // Uneltele legate ale bancului desfiintat dispar din mainile oricui.
        for (Player online : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = online.getInventory().getContents();
            boolean cleaned = false;
            for (int slot = 0; slot < contents.length; slot++) {
                if (station.id.equals(MechanicItems.benchToolStationOf(contents[slot]))) {
                    online.getInventory().clear(slot);
                    cleaned = true;
                }
            }
            if (cleaned) {
                online.sendRichMessage("<yellow>Bancul a fost strans - uneltele lui au disparut.</yellow>");
            }
        }
        refreshTools(station.id);
        event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D),
                MechanicItems.workstation());
        event.getPlayer().sendRichMessage("<gray>Banc strans - ai primit itemul inapoi.</gray>");
    }

    public boolean handleJackRemoved(UUID jackId) {
        boolean changed = false;
        for (Station station : stations.values()) {
            if (jackId != null && jackId.equals(station.jackId)) {
                station.jackId = null;
                changed = true;
            }
        }
        return changed;
    }

    /* ==================== lezul cheilor (2 secunde) ==================== */

    private void leashWrenches() {
        double radius = serviceRadius();
        double radiusSquared = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            boolean anyTool = false;
            for (ItemStack contents1 : contents) {
                if (MechanicItems.benchToolStationOf(contents1) != null) {
                    anyTool = true;
                    break;
                }
            }
            if (!anyTool) {
                continue;
            }
            boolean removed = false;
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                UUID origin = MechanicItems.benchToolStationOf(stack);
                if (origin == null) {
                    continue;
                }
                Optional<Station> station = byId(origin);
                if (station.isPresent()
                        && station.get().location.getWorld() == player.getWorld()
                        && station.get().location.distanceSquared(player.getLocation()) <= radiusSquared) {
                    continue;
                }
                // Iesit din raza: unealta REVINE la banc si slotul se elibereaza.
                MechanicItems.Kind kind = MechanicItems.kindOf(stack);
                if (station.isPresent()) {
                    Station st = station.get();
                    if (kind == MechanicItems.Kind.WRENCH) {
                        st.recipients.remove(player.getUniqueId());
                    } else if (kind == MechanicItems.Kind.HAMMER
                            && player.getUniqueId().equals(st.hammerHolder)) {
                        st.hammerUsed = MechanicItems.HAMMER_MAX_DAMAGE
                                - MechanicItems.hammerUsesLeft(stack);
                        st.hammerHolder = null;
                    }
                    refreshTools(st.id);
                }
                player.getInventory().clear(slot);
                removed = true;
            }
            if (removed) {
                player.sendRichMessage("<yellow>Ai iesit din zona de service - unealta "
                        + "de banc s-a intors la bancul ei.</yellow>");
            }
        }
    }

}
