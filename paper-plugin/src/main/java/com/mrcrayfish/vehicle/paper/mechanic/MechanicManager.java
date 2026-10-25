package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.MechanicStore;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicle;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import com.mrcrayfish.vehicle.paper.vehicle.VehicleManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fata sistemului mecanic: unelte pe vehicul (wrench/CIEL/hammer/spray/motor/roti),
 * reparatii (doar pe cric, din iteme din config, NU bani), vopsire, si tuningul de
 * daune (prag + multiplicator) citit din config.yml. Banii raman la jucatori (RP),
 * exact cum s-a decis.
 */
public final class MechanicManager {
    private static final Key SPRAY = Key.key("vehicle:item.spray_can.spray");

    /** Verificarea unica de permisiune pentru modelul mecanic: permisiunile sunt
     *  default-off in plugin.yml, dar un OPERATOR de server nu poate fi blocat
     *  niciodata de ele (BUG: op primea "no permission" pe bancul de lucru). */
    public static boolean has(Player player, String permission) {
        return player.isOp() || player.hasPermission(permission);
    }

    private final VehiclePlugin plugin;
    private final VehicleManager vehicles;
    private final MechanicStore store;
    private final JackManager jacks;
    private final WorkstationManager workstations;
    private final PropManager props;
    private final FluidMixerManager mixers;
    private final MechanicGui mechanicGui;
    private BukkitTask saveTask;

    public MechanicManager(VehiclePlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.store = new MechanicStore(plugin);
        this.jacks = new JackManager(plugin, vehicles, store);
        this.workstations = new WorkstationManager(plugin, store, jacks);
        // Rack-ul uneltelor: bancul deschide meniul sau si se sincronizeaza cu GUI.
        this.workstations.setToolsOpener((who, stationId) -> gui().openToolsMenu(who, stationId));
        this.workstations.setToolsRefresher(stationId -> gui().refreshToolsMenus(stationId));
        // Butonul RIDICA/COBOARA isi revine singur in meniu la finalul animatiei.
        this.jacks.setStateChangedHook(jackId -> workstations.refreshByJack(jackId));
        this.jacks.setRemovedHook(this.workstations::handleJackRemoved);
        this.props = new PropManager(plugin, vehicles, store);
        this.mixers = new FluidMixerManager(plugin, store);
        this.mechanicGui = new MechanicGui(this);
    }

    public void start() {
        jacks.start();
        workstations.start();
        props.start();
        mixers.start();
        long autosave = Math.max(30L, plugin.getConfig().getLong("persistence.autosave-seconds", 300L));
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, autosave * 20L, autosave * 20L);
        // Rotatia automata in monitorul cricului (brief): un cadru la 5 tick-uri =>
        // o rotatie completa FOARTE lina si lenta la ~6s; si sincronizarea live
        // roti/motor/vopsea cat timp meniul e deschis (reconstruie itemul din stare).
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        save();
        props.stop();
        workstations.stop();
        jacks.stop();
        mixers.stop();
    }

    public void save() {
        List<Object> mixed = new ArrayList<>();
        mixed.addAll(jacks.snapshot());
        mixed.addAll(workstations.snapshot());
        mixed.addAll(props.snapshot());
        mixed.addAll(mixers.snapshot());
        store.saveAll(mixed);
    }

    public JackManager jacks() {
        return jacks;
    }

    public WorkstationManager workstations() {
        return workstations;
    }

    public PropManager props() {
        return props;
    }

    public FluidMixerManager mixers() {
        return mixers;
    }

    public MechanicGui gui() {
        return mechanicGui;
    }

    public VehiclePlugin plugin() {
        return plugin;
    }

    public VehicleManager vehicles() {
        return vehicles;
    }

    /* ============================ config / daune ============================ */

    /** Viteza minima de impact (blocuri/secunda) de la care se aplica daune. */
    public double collisionSpeedThreshold() {
        return plugin.getConfig().getDouble("mechanic.damage.collision-speed-threshold", 3.0D);
    }

    /** Daune mecanice ACTIVE? Dezactivate temporar la cerere (ciclu 6): masinile
     *  nu se strica deloc - reparatia ramane in meniu doar ca rezerva viitoare. */
    public boolean damageEnabled() {
        return plugin.getConfig().getBoolean("mechanic.damage.enabled", false);
    }

    public double collisionDamageMultiplier() {
        return plugin.getConfig().getDouble("mechanic.damage.multiplier", 4.0D);
    }

    /** Mod: Config.SERVER.sprayCanCapacity (default 20). */
    public int sprayCapacity() {
        return Math.max(1, plugin.getConfig().getInt("mechanic.spray-capacity",
                MechanicItems.DEFAULT_SPRAY_CAPACITY));
    }

    /** Paleta noastra de vopsea GRATUITA (sistem propriu, fara consum de iteme):
     *  /vehicle paint + butonul VOPSESTE din banc o deschid amandoua. */
    public boolean paintMenuEnabled() {
        return plugin.getConfig().getBoolean("mechanic.paint-menu.enabled", true);
    }

    /** Distanta maxima (blocuri) la care /vehicle paint gaseste masina jucatorului. */
    public double paintMenuRadius() {
        return Math.max(2.0D, plugin.getConfig().getDouble("mechanic.paint-menu.radius", 6.0D));
    }

    /** Cat combustibil toarna o galetusa cu fluid intr-un rezervor. */
    public float fluidBucketAmount() {
        return (float) plugin.getConfig().getDouble("mechanic.fluid-bucket-amount", 1000.0D);
    }

    public boolean requiresLiftForRepair() {
        return plugin.getConfig().getBoolean("mechanic.require-lift", true);
    }

    /** Itemele consumate la o reparatie completa, din config (format MATERIAL:cantitate). */
    public List<Map.Entry<Material, Integer>> repairItems() {
        List<Map.Entry<Material, Integer>> items = new ArrayList<>();
        for (String raw : plugin.getConfig()
                .getStringList("mechanic.repair-items")) {
            String[] parts = raw.split(":");
            if (parts.length == 0) {
                continue;
            }
            Material material = Material.matchMaterial(parts[0]);
            if (material == null) {
                continue;
            }
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            items.add(Map.entry(material, Math.max(1, amount)));
        }
        if (items.isEmpty()) {
            items.add(Map.entry(Material.IRON_INGOT, 2));
        }
        return items;
    }

    /* ==================== click cu unelte pe vehicul ==================== */

    /**
     * Apelat DIN VehicleManager.handleInteraction inainte de montare. Returneaza
     * true cand click-ul a fost consumat de sistem (si nu trebuie sa urce jucatorul).
     */
    public boolean handleToolClick(Player player, LandVehicle vehicle, ItemStack held) {
        MechanicItems.Kind kind = MechanicItems.kindOf(held);
        if (kind == null) {
            return false;
        }
        switch (kind) {
            case WRENCH -> {
                // Brief 2026-10-24: pe masina RIDICATA, cheia demonteaza ROTILE
                // (nu din GUI!); in rest deschide meniul unic al bancului din zona.
                if (vehicle.jackState() == LandVehicle.JackState.LIFTED) {
                    if (vehicle.spec().hasWheels() && vehicle.wheelsInstalled()) {
                        return uninstallWheels(player, vehicle);
                    }
                    player.sendRichMessage(vehicle.spec().hasWheels()
                            ? "<yellow>Rotile sunt deja demontate.</yellow>"
                            : "<yellow>Acest vehicul nu are roti de demontat.</yellow>");
                    return true;
                }
                if (vehicle.jackId() != null) {
                    var linked = workstations.byJackId(vehicle.jackId());
                    if (linked.isPresent()) {
                        gui().openToolsMenu(player, linked.get().id);
                        return true;
                    }
                }
                var near = workstations.nearestStation(player.getLocation(),
                        workstations.serviceRadiusPublic());
                if (near.isPresent()) {
                    gui().openToolsMenu(player, near.get().id);
                    return true;
                }
                player.sendRichMessage("<gray>Niciun banc de lucru in zona - du-te la "
                        + "mecanic si da click dreapta pe bancul lui.</gray>");
                return true;
            }
            case KEY -> {
                return handleKey(player, vehicle, held);
            }
            case SPRAY_CAN -> {
                return handleSpray(player, vehicle, held);
            }
            case FLUID_BUCKET -> {
                return handleFluidBucket(player, vehicle, held);
            }
            case ENGINE -> {
                return handleEngineSwap(player, vehicle, held);
            }
            case WHEEL -> {
                return handleWheelSwap(player, vehicle, held);
            }
            case HAMMER -> {
                // Ciclul N: ciocanul e functional si cu CLICK DREAPTA (nu doar
                // lovitura): masina ridicata = scoti motorul; jos = explicatie.
                if (vehicle.jackState() == LandVehicle.JackState.LIFTED) {
                    return uninstallEngine(player, vehicle);
                }
                player.sendRichMessage(vehicle.onJack()
                        ? "<yellow>Ridica intai masina pe cric (din meniul bancului), apoi dai cu ciocanul.</yellow>"
                        : "<yellow>Motorul se scoate doar la mecanic: masina trebuie ridicata pe cric.</yellow>");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Cheia: click pe masina ei = incui/deschide (perm mcv.vehicle.lock, doar proprietarul). */
    private boolean handleKey(Player player, LandVehicle vehicle, ItemStack key) {
        UUID target = MechanicItems.keyTargetOf(key);
        if (!vehicle.id().equals(target)) {
            return false; // cheia altei masini - comportament normal
        }
        if (!has(player, "mcv.vehicle.lock")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.vehicle.lock.</red>");
            return true;
        }
        if (!vehicle.canDrive(player)) {
            player.sendRichMessage("<red>Nu poti incuia masina altcuiva.</red>");
            return true;
        }
        boolean nowLocked = !vehicle.locked();
        vehicle.setLocked(nowLocked);
        vehicles.save();
        player.playSound(player.getLocation(),
                nowLocked ? org.bukkit.Sound.BLOCK_CHEST_LOCKED : org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN,
                0.8F, 1.2F);
        player.sendRichMessage(nowLocked
                ? "<gray>Ai <yellow>INCHIS</yellow> masina. Doar tu si cei autorizati pot conduce.</gray>"
                : "<gray>Ai <green>DESCHIS</green> masina.</gray>");
        return true;
    }

    /** Spray pe vehicul (brief 2026-10-24, "rotile/motor/vopsea doar pe elevator"):
     *  - DOAR cu masina ridicata pe cric aliniaz (JackState.LIFTED);
     *  - capsula reala: culoarea ei, consum 1 din RemainingSprays (ca in mod);
     *  - pistolul de banc (binding, fara rezervor): ia culoarea + consumul din
     *    PRIMA capsula reala din inventar; fara capsula = refuz cu mesaj;
     *  - aceeasi culoare = silent no-op (ca in mod); consum si in creative. */
    private boolean handleSpray(Player player, LandVehicle vehicle, ItemStack can) {
        if (!has(player, "mcv.mechanic.paint")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.mechanic.paint.</red>");
            return true;
        }
        if (vehicle.jackState() != LandVehicle.JackState.LIFTED) {
            player.sendRichMessage("<red>Nu poti vopsi aici! Du masina la mecanic: "
                    + "o ridici pe cric (din meniul bancului) si apoi dai cu spray-ul.</red>");
            return true;
        }
        ItemStack capsule = can;
        if (MechanicItems.isBenchTool(can)) {
            capsule = findCapsule(player);
            if (capsule == null) {
                player.sendRichMessage("<yellow>Pistolul de banc nu are rezervor propriu - "
                        + "tine o capsula de vopsea in inventar.</yellow>");
                return true;
            }
        } else {
            Integer direct = MechanicItems.sprayColorOf(can);
            if (direct == null) {
                return false;
            }
        }
        Integer rgb = MechanicItems.sprayColorOf(capsule);
        if (rgb == null) {
            return false;
        }
        if (MechanicItems.spraysRemainingOf(capsule) <= 0) {
            player.sendRichMessage("<red>Capsula de vopsea s-a golit - ia alta.</red>");
            return true;
        }
        if (vehicle.paintColor() == rgb) {
            return true; // aceeasi culoare: ca in mod, nimic nu se intampla
        }
        vehicle.setPaintColor(rgb);
        vehicles.save();
        String colorName = Objects.requireNonNullElse(
                MechanicItems.sprayColorNameOf(capsule), "culoare");
        Location at = vehicle.location();
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(at) <= 20.0D * 20.0D) {
                nearby.playSound(Sound.sound(SPRAY, Sound.Source.PLAYER, 0.8F, 1.0F),
                        at.getX(), at.getY(), at.getZ());
            }
        }
        int remaining = MechanicItems.useSpray(capsule, sprayCapacity());
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Vopsea " + colorName + " (ramas: " + remaining + "/" + sprayCapacity() + ")",
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
        return true;
    }

    /** Prima capsula de vopsea REALA (far marcaj de banc, cu continut) din inventar. */
    private static ItemStack findCapsule(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null
                    && MechanicItems.kindOf(stack) == MechanicItems.Kind.SPRAY_CAN
                    && !MechanicItems.isBenchTool(stack)
                    && MechanicItems.spraysRemainingOf(stack) > 0) {
                return stack;
            }
        }
        return null;
    }

    /** Motor nou pe vehicul (click cu itemul): tier nou intra imediat in fizica;
     *  motorul vechi, de acelasi rebut de tip, revine in inventar. */
    private boolean handleEngineSwap(Player player, LandVehicle vehicle, ItemStack engineItem) {
        if (vehicle.jackState() != LandVehicle.JackState.LIFTED) {
            player.sendRichMessage("<red>Motorul se monteaza/demonteaza doar cu masina ridicata pe cric.</red>");
            return true;
        }
        if (!has(player, "mcv.mechanic.engine")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.mechanic.engine.</red>");
            return true;
        }
        EngineType wanted = MechanicItems.engineTypeOf(engineItem);
        EngineTier tier = MechanicItems.engineTierOf(engineItem);
        EngineType fits = vehicle.spec().defaultEngineType();
        if (fits == EngineType.NONE) {
            player.sendRichMessage("<red>Acest vehicul nu are motor schimbabil.</red>");
            return true;
        }
        if (wanted != fits) {
            player.sendRichMessage("<red>Aici intra un <gold>" + fits.label().toLowerCase()
                    + "</gold>, nu " + wanted.label().toLowerCase() + ".</red>");
            return true;
        }
        EngineTier previous = vehicle.engineTier();
        boolean wasInstalled = vehicle.engineInstalled();
        vehicle.setEngineTier(tier);
        // La vehiculele cu slot gol (ATV/Dune Buggy) asta e INSTALARE: piesa apare
        // imediat pe model, in lume si in GUI - cerinta brief 2026-10-24.
        vehicle.setEngineInstalled(true);
        vehicle.setEngineVirtual(false); // montat din item real - acum e fizic
        if (player.getGameMode() != GameMode.CREATIVE) {
            engineItem.setAmount(engineItem.getAmount() - 1);
        }
        if (wasInstalled && previous != tier) {
            player.getInventory().addItem(MechanicItems.engine(fits, previous));
        }
        vehicles.save();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7F, 1.3F);
        if (wasInstalled) {
            player.sendRichMessage("<gray>Motor schimbat: <gold>" + tier.label() + " " + fits.label().toLowerCase()
                    + "</gold> (x" + String.format(java.util.Locale.ROOT, "%.1f", tier.powerMultiplier())
                    + "). Cel vechi l-ai primit inapoi.</gray>");
        } else {
            player.sendRichMessage("<gray>Motor INSTALAT: <gold>" + tier.label() + " " + fits.label().toLowerCase()
                    + "</gold> - piesa a aparut acum si pe model.</gray>");
        }
        return true;
    }

    /** Demontare motor (cursor gol pe slotul de motor): motorul trece pe cursorul
     *  jucatorului, slotul ramane functional dar gol, iar piesa dispare de pe
     *  model (in lume si in GUI) - regula brief 2026-10-24. */
    public boolean uninstallEngine(Player player, LandVehicle vehicle) {
        if (vehicle.jackState() != LandVehicle.JackState.LIFTED) {
            player.sendRichMessage("<red>Motorul se monteaza/demonteaza doar cu masina ridicata pe cric.</red>");
            return true;
        }
        if (!has(player, "mcv.mechanic.engine")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.mechanic.engine.</red>");
            return true;
        }
        if (!vehicle.engineInstalled()) {
            return true; // slotul e deja gol - nimic de scos
        }
        EngineType fits = vehicle.spec().defaultEngineType();
        if (fits == EngineType.NONE) {
            return true;
        }
        if (vehicle.engineVirtual()) {
            // Ciclul R („baga putina logistica"): motorul de fabrica IRON e VIRTUAL -
            // nu a fost niciodata un item fizic. Demontat, pur si simplu dispare
            // (altfel orice masina ieftina would printa motoare de iron la infinit).
            player.sendRichMessage("<gray>Motorul de fabrica (Iron, virtual) a disparut "
                    + "- nu se transforma in item. Slotul ramane functional.</gray>");
        } else {
            player.setItemOnCursor(MechanicItems.engine(fits, vehicle.engineTier()));
            player.sendRichMessage("<gray>Motor scos - slotul ramane functional, "
                    + "dar piesa nu se mai vede pe model.</gray>");
        }
        vehicle.setEngineInstalled(false);
        vehicle.setEngineVirtual(false);
        vehicles.save();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.6F, 0.9F);
        return true;
    }

    /** Scoate rotile din vehicul si le pune pe cursor (fluxul din mod). Fara roti
     *  montate masina nu porneste si nu poate fi coborata de pe cric. */
    public boolean uninstallWheels(Player player, LandVehicle vehicle) {
        if (vehicle.jackState() != LandVehicle.JackState.LIFTED) {
            player.sendRichMessage("<red>Rotile se monteaza/demonteaza doar cu masina ridicata pe cric.</red>");
            return true;
        }
        if (!has(player, "mcv.mechanic.wheel")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.mechanic.wheel.</red>");
            return true;
        }
        if (!vehicle.spec().hasWheels() || !vehicle.wheelsInstalled()) {
            return true; // slot gol deja / vehicul fara slot de roti
        }
        player.setItemOnCursor(MechanicItems.wheel(vehicle.wheelType()));
        vehicle.setWheelsInstalled(false);
        vehicles.save();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.6F, 0.9F);
        player.sendRichMessage("<gray>Roti scoase - masina ramane fara ele (nu porneste "
                + "si nici nu o poti cobori de pe cric).</gray>");
        return true;
    }

    /** Roti noi: modelul vizual se schimba, iar fizica preia tractiunea tipului. */
    private boolean handleWheelSwap(Player player, LandVehicle vehicle, ItemStack wheelItem) {
        if (vehicle.jackState() != LandVehicle.JackState.LIFTED) {
            player.sendRichMessage("<red>Rotile se monteaza/demonteaza doar cu masina ridicata pe cric.</red>");
            return true;
        }
        if (!has(player, "mcv.mechanic.wheel")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.mechanic.wheel.</red>");
            return true;
        }
        WheelType wanted = MechanicItems.wheelTypeOf(wheelItem);
        if (!vehicle.spec().hasWheels()) {
            player.sendRichMessage("<red>Acest vehicul nu primeste roti.</red>");
            return true;
        }
        WheelType previous = vehicle.wheelType();
        boolean wasInstalled = vehicle.wheelsInstalled();
        if (wasInstalled && previous == wanted) {
            player.sendRichMessage("<yellow>Are deja " + wanted.label().toLowerCase() + ".</yellow>");
            return true;
        }
        vehicle.setWheelsInstalled(true);
        vehicle.setWheelType(wanted);
        if (player.getGameMode() != GameMode.CREATIVE) {
            wheelItem.setAmount(wheelItem.getAmount() - 1);
        }
        if (wasInstalled) {
            player.getInventory().addItem(MechanicItems.wheel(previous));
        }
        vehicles.save();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7F, 1.0F);
        if (wasInstalled) {
            player.sendRichMessage("<gray>Roti schimbate: <gold>" + wanted.label()
                    + "</gold>. Vechile " + previous.label().toLowerCase() + " le-ai primit inapoi.</gray>");
        } else {
            player.sendRichMessage("<gray>Roti INSTALATE: <gold>" + wanted.label()
                    + "</gold>. Masina poate porni si cobori de pe cric din nou.</gray>");
        }
        return true;
    }

    /** Galetusa cu fluid (fuelium/ender_sap/blaze_juice) pe vehicul: toarna
     *  mechanic.fluid-bucket-amount in rezervor si ramane galetusa goala. */
    private boolean handleFluidBucket(Player player, LandVehicle vehicle, ItemStack bucket) {
        MechanicItems.Fluid fluid = MechanicItems.bucketFluidOf(bucket);
        if (fluid == null) {
            return false;
        }
        float capacity = vehicle.spec().energyCapacity();
        if (vehicle.fuel() >= capacity) {
            player.sendRichMessage("<yellow>Rezervorul vehiculului este deja plin.</yellow>");
            return true;
        }
        float poured = Math.min(fluidBucketAmount(), capacity - vehicle.fuel());
        vehicle.setFuel(vehicle.fuel() + poured);
        vehicles.save();
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BUCKET_FILL, 0.8F, 1.0F);
        player.sendRichMessage("<gray>Ai turnat <gold>" + (int) poured + "</gold> din galetusa de "
                + fluid.label + " (rezervor: <gold>" + (int) vehicle.fuel() + "</gold>/"
                + (int) capacity + ").</gray>");
        return true;
    }

    /** Reparatie completa (din GUI, de obicei pe cric): consuma itemele din config. */
    public boolean tryRepair(Player player, LandVehicle vehicle) {
        if (!has(player, "mcv.mechanic.repair")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.mechanic.repair.</red>");
            return false;
        }
        if (vehicle.health() >= vehicle.maxHealth()) {
            player.sendRichMessage("<yellow>Masina nu are daune.</yellow>");
            return false;
        }
        if (requiresLiftForRepair()
                && vehicle.jackState() != LandVehicle.JackState.LIFTED) {
            player.sendRichMessage("<red>Poti repara doar cu masina RIDICATA pe cric.</red>");
            return false;
        }
        List<Map.Entry<Material, Integer>> needed = repairItems();
        for (Map.Entry<Material, Integer> entry : needed) {
            if (player.getGameMode() != GameMode.CREATIVE
                    && !player.getInventory().containsAtLeast(
                    new ItemStack(entry.getKey()), entry.getValue())) {
                player.sendRichMessage("<red>Iti lipsesc materiale: " + entry.getValue()
                        + "x " + entry.getKey().name().toLowerCase(java.util.Locale.ROOT) + ".</red>");
                return false;
            }
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            for (Map.Entry<Material, Integer> entry : needed) {
                for (int amount = entry.getValue(); amount > 0;) {
                    int slot = player.getInventory().first(entry.getKey());
                    if (slot < 0) {
                        break;
                    }
                    ItemStack stack = player.getInventory().getItem(slot);
                    int take = Math.min(amount, stack.getAmount());
                    stack.setAmount(stack.getAmount() - take);
                    amount -= take;
                    if (stack.getAmount() <= 0) {
                        player.getInventory().clear(slot);
                    }
                }
            }
        }
        vehicle.repairFully();
        vehicles.save();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.9F, 1.0F);
        player.sendRichMessage("<green>Masina reparata - sanatate 100/100.</green>");
        return true;
    }

    /* ======================== routing entitati / pauza ======================== */

    /** Click dreapta pe entitatile sistemului (interactions ale statiilor inca
     *  pe entitati: mixer/props; cricul si bancul sunt BLOCURI solide acum). */
    public boolean handleEntityInteraction(Player player, Entity entity, ItemStack held) {
        if (mixers.handleClick(player, entity, held)) {
            return true;
        }
        if (props.byEntityPublic(entity).isPresent()) {
            player.sendRichMessage("<gray>Prop stradal - ghemuit + pumn = il ridici.</gray>");
            return true;
        }
        return false;
    }

    /** Click-dreapta pe un BLOC-statie solid (note_block suprascris in pack):
     *  vine din MechanicListener DUPA ce interactiunea a fost anulata (fara
     *  retunare a notei). Returneaza true mereu (blocul e al sistemului). */
    public boolean handleStationBlockClick(Player player, Block block, ItemStack held) {
        if (workstations.isStationBlock(block)) {
            // Anti meniu-instant: al doilea click rapid din animatia de plasare nu
            // mai deschide meniul (fereastra scurta dupa place, in WorkstationManager).
            if (workstations.wasJustPlaced(block)) {
                return true;
            }
            return workstations.handleBlockClick(player, block, held);
        }
        if (jacks.isJackBlock(block)) {
            return jacks.handleBlockClick(player, block, held);
        }
        return true;
    }

    /** Blocurile-statie se SPARG ca blocuri normale (cerinta user): si da itemul
     *  inapoi. Exceptie: cricul ridicat/cu masina nu se poate sparge. */
    public void handleStationBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        StationBlocks.Kind kind = StationBlocks.kindOf(event.getBlock());
        if (kind == null) {
            return;
        }
        switch (kind) {
            case WORKSTATION -> workstations.handleBlockBreak(event);
            case JACK_DOWN, JACK_UP ->
                    jacks.handleBlockBreak(event, jacks.findByBlock(event.getBlock()).orElse(null));
        }
    }
}
