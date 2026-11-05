package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.persistence.MechanicStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixerul de fluide (FluidMixerBlock din mod): reteta oficiala a modului
 * Blaze Juice + Ender Sap -> Fuellium. Reguli (toate configurabile):
 *  - click cu o galeata de blaze/ender = toarna in rezervoarele mixerului;
 *  - cand ambele fluide ajung la prag, amestecarea produce Fuellium instantaneu;
 *  - click cu galeata GOALA = scoti Fuellium (daca stocul acopera o galeata);
 *  - click cu mana goala = status (cat blaze/ender/fuellium e inauntru);
 *  - ghemuit + click = ridicare (doar plasator sau operator); galetii pline se
 *    intorc ca iteme, restul sub o galeata se pierde (avertizat in mesaj).
 * Plasarea e ca la celelalte statii: DOAR pe fata de sus a unui bloc (MechanicListener).
 */
public final class FluidMixerManager {
    private static final String ENTITY_TAG = "mcv_fluid_mixer";

    /** Starea runtime a unui mixer pus in lume. */
    private static final class Mixer {
        final UUID id;
        final Location location;
        final ItemDisplay display;
        final Interaction hitbox;
        UUID ownerId;
        double blazeSap;
        double enderSap;
        double fuelium;

        Mixer(UUID id, Location location, ItemDisplay display, Interaction hitbox) {
            this.id = id;
            this.location = location;
            this.display = display;
            this.hitbox = hitbox;
        }
    }

    private final VehiclePlugin plugin;
    private final MechanicStore store;
    private final Map<UUID, Mixer> mixers = new ConcurrentHashMap<>();

    public FluidMixerManager(VehiclePlugin plugin, MechanicStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /* ============================ configuratie ============================ */

    /** Cantitatea de blaze juice / ender sap consumata per amestecare (ca in mod, mB). */
    private double inputAmount() {
        return Math.max(1.0D, plugin.getConfig().getDouble("mechanic.fluid-mixer.input-amount", 1000.0D));
    }

    /** Cantitatea de fuellium produsa per amestecare. */
    private double outputAmount() {
        return Math.max(1.0D, plugin.getConfig().getDouble("mechanic.fluid-mixer.output-amount", 1000.0D));
    }

    private double inputCapacity() {
        return Math.max(inputAmount(), plugin.getConfig().getDouble("mechanic.fluid-mixer.input-capacity", 5000.0D));
    }

    private double outputCapacity() {
        return Math.max(outputAmount(), plugin.getConfig().getDouble("mechanic.fluid-mixer.output-capacity", 5000.0D));
    }

    /** Cat toarna / umple o galeata (aceeasi valoare ca la galetile de fluid). */
    private double bucketAmount() {
        return Math.max(1.0D, plugin.getConfig().getDouble("mechanic.fluid-bucket-amount", 1000.0D));
    }

    /* ============================ ciclu de viata ============================ */

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ENTITY_TAG)) {
                    entity.remove();
                }
            }
        }
        load();
    }

    public void stop() {
        for (Mixer mixer : mixers.values()) {
            mixer.display.remove();
            mixer.hitbox.remove();
        }
        mixers.clear();
    }

    private void load() {
        for (MechanicStore.StoredMixer stored : store.loadMixers()) {
            World world = Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            Mixer mixer = spawnRig(stored.id(), new Location(world, stored.x(), stored.y(),
                    stored.z(), stored.yaw(), 0.0F));
            mixer.ownerId = stored.ownerId();
            mixer.blazeSap = stored.blazeSap();
            mixer.enderSap = stored.enderSap();
            mixer.fuelium = stored.fuelium();
            mixers.put(mixer.id, mixer);
        }
    }

    public List<MechanicStore.StoredMixer> snapshot() {
        List<MechanicStore.StoredMixer> stored = new ArrayList<>();
        for (Mixer mixer : mixers.values()) {
            Location location = mixer.location;
            stored.add(new MechanicStore.StoredMixer(mixer.id, location.getWorld().getUID(),
                    location.getX(), location.getY(), location.getZ(), location.getYaw(),
                    mixer.ownerId, mixer.blazeSap, mixer.enderSap, mixer.fuelium));
        }
        return stored;
    }

    /* ============================ plasare / rig ============================ */

    public boolean place(Player player, Location clickedFace, float yaw) {
        Location ground = clickedFace.getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        Mixer mixer = spawnRig(UUID.randomUUID(), ground);
        mixer.ownerId = player.getUniqueId();
        mixers.put(mixer.id, mixer);
        player.sendRichMessage("<gray>Mixer de fluide pus jos. Reteta: <gold>Blaze Juice</gold>"
                + " + <gold>Ender Sap</gold> = <gold>Fuellium</gold>. Torni cu galetile,"
                + " scoti cu galeata goala.</gray>");
        return true;
    }

    private Mixer spawnRig(UUID id, Location ground) {
        World world = ground.getWorld();
        // Conventia dovedita vizual (GasPumpRig): yaw pe entitate + stack LEATHER_HORSE_ARMOR
        // cu alb explicit (dyed_color opac pe orice client) - vezi WorkstationManager.
        float snapped = JackManager.snapYaw(ground.getYaw());
        Location spawnAt = ground.clone();
        spawnAt.setYaw(snapped);
        spawnAt.setPitch(0.0F);
        ItemDisplay display = world.spawn(spawnAt, ItemDisplay.class, spawned -> {
            ItemStack stack = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(new NamespacedKey("vehicle", "fluid_mixer"));
            if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta armor) {
                armor.setColor(org.bukkit.Color.fromRGB(0xF9FFFE));
            }
            stack.setItemMeta(meta);
            spawned.setItemStack(stack);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setInvulnerable(true);
            spawned.setPersistent(false);
            spawned.setShadowRadius(0.2F);
            JackManager.applyBlockTransform(spawned, spawned.getLocation().getYaw());
            spawned.addScoreboardTag(ENTITY_TAG);
            spawned.addScoreboardTag("mcv_" + id);
        });
        Interaction hitbox = world.spawn(spawnAt, Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.2F);
            spawned.setInteractionHeight(1.5F);
            spawned.setResponsive(true);
            spawned.setPersistent(false);
            spawned.addScoreboardTag(ENTITY_TAG);
            spawned.addScoreboardTag("mcv_" + id);
        });
        Location stored = ground.clone();
        stored.setYaw(snapped);
        return new Mixer(id, stored, display, hitbox);
    }

    public boolean isMixerEntity(Entity entity) {
        return entity != null && findByEntity(entity).isPresent();
    }

    private Optional<Mixer> findByEntity(Entity entity) {
        for (Mixer mixer : mixers.values()) {
            if (mixer.hitbox.getUniqueId().equals(entity.getUniqueId())
                    || mixer.display.getUniqueId().equals(entity.getUniqueId())) {
                return Optional.of(mixer);
            }
        }
        return Optional.empty();
    }

    /* ============================ interactiune ============================ */

    /** Click pe mixer. Returneaza true daca entitatea e un mixer (click tratat). */
    public boolean handleClick(Player player, Entity entity, ItemStack held) {
        Optional<Mixer> found = findByEntity(entity);
        if (found.isEmpty()) {
            return false;
        }
        Mixer mixer = found.get();
        if (player.isSneaking()) {
            tryPickup(player, mixer);
            return true;
        }
        if (!MechanicManager.has(player, "mcv.fluidmixer.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea mcv.fluidmixer.use.</red>");
            return true;
        }
        MechanicItems.Kind kind = MechanicItems.kindOf(held);
        if (kind == MechanicItems.Kind.FLUID_BUCKET) {
            pour(player, mixer, held);
            return true;
        }
        if (held != null && held.getType() == Material.BUCKET) {
            takeFuelium(player, mixer, held);
            return true;
        }
        status(player, mixer);
        return true;
    }

    /** Turna o galeata de blaze juice / ender sap in mixer si incearca amestecarea. */
    private void pour(Player player, Mixer mixer, ItemStack bucket) {
        MechanicItems.Fluid fluid = MechanicItems.bucketFluidOf(bucket);
        if (fluid == null || fluid == MechanicItems.Fluid.FUELIUM) {
            player.sendRichMessage("<yellow>Fuellium e produsul finit - nu il torni inapoi;"
                    + " scoate-l cu o <gold>galeata goala</gold>.</yellow>");
            return;
        }
        double amount = bucketAmount();
        if (fluid == MechanicItems.Fluid.BLAZE_JUICE) {
            if (mixer.blazeSap + amount > inputCapacity()) {
                player.sendRichMessage("<gold>Rezervorul de Blaze Juice e plin"
                        + " (" + (int) inputCapacity() + ").</gold>");
                return;
            }
            mixer.blazeSap += amount;
        } else {
            if (mixer.enderSap + amount > inputCapacity()) {
                player.sendRichMessage("<gold>Rezervorul de Ender Sap e plin"
                        + " (" + (int) inputCapacity() + ").</gold>");
                return;
            }
            mixer.enderSap += amount;
        }
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BUCKET_EMPTY, 0.8F, 1.0F);
        player.sendRichMessage("<gray>Ai turnat <gold>" + (int) amount + "</gold> "
                + fluid.label + " in mixer. Amestecare: <gold>" + (int) mixer.blazeSap
                + "</gold> blaze + <gold>" + (int) mixer.enderSap + "</gold> ender.</gray>");
        tryMix(player, mixer);
    }

    /** Daca ambele fluide au ajuns la prag, consuma si produce Fuellium (instantaneu). */
    private void tryMix(Player player, Mixer mixer) {
        double need = inputAmount();
        if (mixer.blazeSap < need || mixer.enderSap < need) {
            return;
        }
        double output = outputAmount();
        if (mixer.fuelium + output > outputCapacity()) {
            player.sendRichMessage("<gold>Rezervorul de Fuellium e plin"
                    + " (" + (int) outputCapacity() + ") - scoate intai cu galeata goala.</gold>");
            return;
        }
        mixer.blazeSap -= need;
        mixer.enderSap -= need;
        mixer.fuelium += output;
        player.playSound(mixer.location, org.bukkit.Sound.BLOCK_BREWING_STAND_BREW, 0.9F, 1.2F);
        player.sendRichMessage("<green>Mixerul a produs <gold>" + (int) output
                + " Fuellium</gold>! (stoc: <gold>" + (int) mixer.fuelium + "</gold>)</green>");
    }

    /** Galeata goala pe mixer: scoate Fuellium daca stocul acopera o galeata. */
    private void takeFuelium(Player player, Mixer mixer, ItemStack emptyBucket) {
        double amount = bucketAmount();
        if (mixer.fuelium < amount) {
            player.sendRichMessage("<yellow>Nu e destul Fuellium - ai <gold>"
                    + (int) mixer.fuelium + "</gold>, o galeata cere <gold>" + (int) amount
                    + "</gold>.</yellow>");
            return;
        }
        mixer.fuelium -= amount;
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            emptyBucket.setAmount(emptyBucket.getAmount() - 1);
        }
        java.util.HashMap<Integer, ItemStack> rest = player.getInventory()
                .addItem(MechanicItems.fluidBucket(MechanicItems.Fluid.FUELIUM));
        for (ItemStack leftover : rest.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BUCKET_FILL, 0.8F, 1.0F);
        player.sendRichMessage("<gray>Ai scos o <gold>galeata de Fuellium</gold> din mixer"
                + " (ramas: <gold>" + (int) mixer.fuelium + "</gold>).</gray>");
    }

    /** Statusul mixerului (click cu mana goala). */
    private void status(Player player, Mixer mixer) {
        player.sendRichMessage("<gray>Mixer: <gold>" + (int) mixer.blazeSap
                + "</gold> Blaze Juice + <gold>" + (int) mixer.enderSap
                + "</gold> Ender Sap -> <gold>" + (int) mixer.fuelium + "</gold> Fuellium"
                + " (reteta: " + (int) inputAmount() + "+" + (int) inputAmount()
                + " = " + (int) outputAmount() + ").</gray>");
    }

    /** Ghemuit + click = ridicare. Galetii pline se intorc ca iteme, restul se pierde. */
    private void tryPickup(Player player, Mixer mixer) {
        if (mixer.ownerId != null && !mixer.ownerId.equals(player.getUniqueId())
                && !player.isOp()) {
            player.sendRichMessage("<red>Doar <gold>plasatorul</gold> (sau un operator) poate"
                    + " ridica mixerul.</red>");
            return;
        }
        double amount = bucketAmount();
        int blazeBuckets = (int) (mixer.blazeSap / amount);
        int enderBuckets = (int) (mixer.enderSap / amount);
        int fueliumBuckets = (int) (mixer.fuelium / amount);
        dropBuckets(mixer.location, MechanicItems.Fluid.BLAZE_JUICE, blazeBuckets);
        dropBuckets(mixer.location, MechanicItems.Fluid.ENDER_SAP, enderBuckets);
        dropBuckets(mixer.location, MechanicItems.Fluid.FUELIUM, fueliumBuckets);
        double lost = (mixer.blazeSap % amount) + (mixer.enderSap % amount) + (mixer.fuelium % amount);
        if (lost > 0.0D) {
            player.sendRichMessage("<yellow>Ramasa sub o galeata (" + (int) lost
                    + ") s-a pierdut la ridicare.</yellow>");
        }
        mixer.display.remove();
        mixer.hitbox.remove();
        mixers.remove(mixer.id);
        java.util.HashMap<Integer, ItemStack> rest = player.getInventory()
                .addItem(MechanicItems.fluidMixer());
        for (ItemStack leftover : rest.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.0F);
        player.sendRichMessage("<gray>Ai ridicat mixerul de fluide.</gray>");
    }

    private static void dropBuckets(Location at, MechanicItems.Fluid fluid, int count) {
        for (int index = 0; index < count; index++) {
            at.getWorld().dropItemNaturally(at, MechanicItems.fluidBucket(fluid));
        }
    }
}
