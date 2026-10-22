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

    public double collisionDamageMultiplier() {
        return plugin.getConfig().getDouble("mechanic.damage.multiplier", 4.0D);
    }

    /** Mod: Config.SERVER.sprayCanCapacity (default 20). */
    public int sprayCapacity() {
        return Math.max(1, plugin.getConfig().getInt("mechanic.spray-capacity",
                MechanicItems.DEFAULT_SPRAY_CAPACITY));
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
                if (vehicle.onJack()
                        && vehicle.jackState() == LandVehicle.JackState.LIFTED) {
                    mechanicGui.open(player, vehicle);
                    return true;
                }
                player.sendRichMessage(vehicle.onJack()
                        ? "<yellow>Masina e in miscare pe cric - asteapta sa se opreasca.</yellow>"
                        : "<gray>Ridica masina pe un <gold>cric</gold> si apoi deschide bancul cu cheia.</gray>");
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

    /** Spray can pe vehicul: vopseste caroseria direct (modul vopsia tot cu clickul). */
    private boolean handleSpray(Player player, LandVehicle vehicle, ItemStack can) {
        Integer rgb = MechanicItems.sprayColorOf(can);
        if (rgb == null) {
            return false;
        }
        if (!has(player, "mcv.mechanic.paint")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.mechanic.paint.</red>");
            return true;
        }
        if (player.getGameMode() != GameMode.CREATIVE
                && MechanicItems.spraysRemainingOf(can) <= 0) {
            player.sendRichMessage("<red>Spray-ul s-a golit - reumple-l cumparand alta "
                    + "vopsea (shop > Piese).</red>");
            return true;
        }
        vehicle.setPaintColor(rgb);
        vehicles.save();
        String colorName = Objects.requireNonNullElse(
                MechanicItems.sprayColorNameOf(can), "culoare");
        Location at = vehicle.location();
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(at) <= 20.0D * 20.0D) {
                nearby.playSound(Sound.sound(SPRAY, Sound.Source.PLAYER, 0.8F, 1.0F),
                        at.getX(), at.getY(), at.getZ());
            }
        }
        int remaining = player.getGameMode() == GameMode.CREATIVE
                ? MechanicItems.spraysRemainingOf(can)
                : MechanicItems.useSpray(can, sprayCapacity());
        player.sendRichMessage("<gray>Ai vopsit masina in <gold>" + colorName + "</gold>"
                + (player.getGameMode() == GameMode.CREATIVE ? "."
                : " (ramas: " + remaining + "/" + sprayCapacity() + ").") + "</gray>");
        return true;
    }

    /** Motor nou pe vehicul (click cu itemul): tier nou intra imediat in fizica;
     *  motorul vechi, de acelasi rebut de tip, revine in inventar. */
    private boolean handleEngineSwap(Player player, LandVehicle vehicle, ItemStack engineItem) {
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
        vehicle.setEngineTier(tier);
        if (player.getGameMode() != GameMode.CREATIVE) {
            engineItem.setAmount(engineItem.getAmount() - 1);
        }
        if (previous != tier) {
            player.getInventory().addItem(MechanicItems.engine(fits, previous));
        }
        vehicles.save();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7F, 1.3F);
        player.sendRichMessage("<gray>Motor schimbat: <gold>" + tier.label() + " " + fits.label().toLowerCase()
                + "</gold> (x" + String.format(java.util.Locale.ROOT, "%.1f", tier.powerMultiplier())
                + "). Cel vechi l-ai primit inapoi.</gray>");
        return true;
    }

    /** Roti noi: modelul vizual se schimba, iar fizica preia tractiunea tipului. */
    private boolean handleWheelSwap(Player player, LandVehicle vehicle, ItemStack wheelItem) {
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
        if (previous == wanted) {
            player.sendRichMessage("<yellow>Are deja " + wanted.label().toLowerCase() + ".</yellow>");
            return true;
        }
        vehicle.setWheelType(wanted);
        if (player.getGameMode() != GameMode.CREATIVE) {
            wheelItem.setAmount(wheelItem.getAmount() - 1);
        }
        player.getInventory().addItem(MechanicItems.wheel(previous));
        vehicles.save();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7F, 1.0F);
        player.sendRichMessage("<gray>Roti schimbate: <gold>" + wanted.label()
                + "</gold>. Vechile " + previous.label().toLowerCase() + " le-ai primit inapoi.</gray>");
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

    /** Click dreapta pe entitatile sistemului (interactions ale statiilor). */
    public boolean handleEntityInteraction(Player player, Entity entity, ItemStack held) {
        Optional<Jack> jack = jacks.byInteraction(entity);
        if (jack.isPresent()) {
            if (MechanicItems.kindOf(held) == MechanicItems.Kind.WRENCH) {
                return jacks.handleWrenchClick(player, jack.get());
            }
            if (held != null && !held.getType().isAir()) {
                player.sendRichMessage("<gray>Foloseste <gold>cheia de mecanic</gold> pe cric.</gray>");
                return true;
            }
            player.sendRichMessage("<gray>Cricul se actioneaza cu <gold>cheia de mecanic</gold> "
                    + "(de la bancul de lucru).</gray>");
            return true;
        }
        if (workstations.handleClick(player, entity, held)) {
            return true;
        }
        if (mixers.handleClick(player, entity, held)) {
            return true;
        }
        if (props.byEntityPublic(entity).isPresent()) {
            player.sendRichMessage("<gray>Prop stradal - ghemuit + pumn = il ridici.</gray>");
            return true;
        }
        return false;
    }
}
