package com.mrcrayfish.vehicle.paper.vehicle;

import com.mrcrayfish.vehicle.paper.VehiclePlugin;
import com.mrcrayfish.vehicle.paper.render.LandVehicleRig;
import com.mrcrayfish.vehicle.paper.runtime.PaperTrailer;
import com.mrcrayfish.vehicle.paper.runtime.TrailerManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class VehicleManager {
    private final VehiclePlugin plugin;
    private final Map<UUID, LandVehicle> vehicles = new HashMap<>();
    private final Map<UUID, LandVehicle> entities = new HashMap<>();
    private final Map<UUID, StoredVehicle> pendingWorlds = new HashMap<>();
    private final Map<UUID, StoredVehicle> garage = new HashMap<>();
    private final Map<UUID, VehicleChunk> chunkTickets = new HashMap<>();
    private final File storageFile;
    private final TrailerManager trailers;
    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private BukkitTask sweepTask;
    /** CICLUL AF: cate secunde consecutive a stat fiecare vehicul GOAL intr-un cerc
     *  de parcare. Cheia dispare cand vehiculul pleaca din cerc / se occupa / iese
     *  din lume (retainAll la inceputul fiecarui sweep). */
    private final Map<UUID, Integer> parkedInSpotSeconds = new HashMap<>();
    /** CICLUL AH: cooldown (tick-uri) intre doua impulsuri consecutive primite de
     *  acelasi vehicul de la o placa de boost - 40t = 2s, ca o rampa lunga sa nu
     *  accelereze la infinit. Cheile invechite se curata pasiv la re-intalnire. */
    /** Ciclul AL: edge-trigger pentru sunetul oficial de boost pad (la intrari). */
    private final Map<UUID, Integer> lastPadIngress = new HashMap<>();
    /* CICLUL AU -> AV (user: "o bara frumos deasupra selectorului de iteme, sa nu
     * se mai vada chestiile de acolo, iar scrisul il faci tu"): vitezometrul este
     * bara de XP a jucatorului, dar cu NIVELUL TINUT PE 0 - vanilla nu deseneaza
     * numarul verde cand nivelul e 0, deci ramane doar umplerea barei (frumos,
     * nativ, zero conflict cu scoreboard-ul TAB din dreapta). Textul cu viteza il
     * scrie pluginul in action bar (LandVehicle.showFuelOverlay). XP-ul REAL se
     * salveaza in memorie + in PDC (anti-crash) si se restaureaza intocmai cand
     * jucatorul coboara/delogheaza - nimeni nu pierde nivelele de enchanting. */
    private static final org.bukkit.NamespacedKey XP_HUD_BACKUP =
            new org.bukkit.NamespacedKey("vehicle", "xp_hud_backup");
    private final Map<UUID, double[]> hijackedXp = new HashMap<>();
    private int activationTick;
    /** Short per-vehicle cooldown after a bumper-car bonk so overlapping cars don't
     * retrigger the jolt and sound every single tick while still touching. */
    private final Map<UUID, Integer> bumperCarCooldowns = new HashMap<>();

    public VehicleManager(VehiclePlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "vehicles.yml");
        this.trailers = new TrailerManager(plugin, this);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanupOrphanRigEntities, 40L);
        cleanupOrphanedEntities();
        load();
        trailers.start();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        long autosave = Math.max(30L, plugin.getConfig().getLong("persistence.autosave-seconds", 300L));
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, autosave * 20L, autosave * 20L);
        // Ciclul AF: parcare-cu-timer, verificat o data pe secunda.
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoStoreSweep, 20L, 20L);
    }

    /** Boot cleanup: rig entity corpses from crashy/ungraceful restarts (tagged mcv_plugin_vehicle
     *  but unknown to this boot's entity index). They never came back from a save, so they
     *  only cost the server. After an explicit data wipe (WIPE_ON_BOOT) we purge EVERY
     *  mcv_-tagged entity instead — vehicles, pumps, garage/dealer rigs — so the world is
     *  truly back to zero alongside the deleted storage. */
    public int cleanupOrphanRigEntities() {
        boolean wiped = plugin.wasDataWipedThisBoot();
        int removed = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                boolean ours;
                if (wiped) {
                    ours = false;
                    for (String tag : entity.getScoreboardTags()) {
                        if (tag.startsWith("mcv_")) {
                            ours = true;
                            break;
                        }
                    }
                } else {
                    ours = entity.getScoreboardTags().contains(com.mrcrayfish.vehicle.paper.render.LandVehicleRig.ENTITY_TAG);
                }
                if (!ours) {
                    continue;
                }
                if (wiped || byEntity(entity).isEmpty()) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info(wiped
                    ? "[Curatenie] WIPE: " + removed + " entitati mcv_* sterse din lume (reset total)."
                    : "[Curatenie] " + removed + " entitati-rig orfane sterse la pornire.");
        }
        return removed;
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        trailers.returnCarriedVehicles();
        saveNow(); // onDisable: flush SINCRON - un task async ar putea fi pierdut la oprire
        trailers.stop();
        for (LandVehicle vehicle : new ArrayList<>(vehicles.values())) {
            releaseChunkTicket(vehicle);
            vehicle.remove();
        }
        vehicles.clear();
        entities.clear();
        chunkTickets.clear();
    }

    public LandVehicle spawn(LandVehicleSpec spec, Location location) {
        return spawn(UUID.randomUUID(), spec, location);
    }

    public PaperTrailer spawn(TrailerSpec spec, Location location) {
        return trailers.spawn(spec, location);
    }

    /** Ciclul AC: vehiculele de apa stau la suprafata apei, nu se snap-uiesc pe fund. */
    private static boolean isWaterVehicle(LandVehicleSpec spec) {
        String id = spec.id();
        return "speed_boat".equals(id) || "aluminum_boat".equals(id) || "jet_ski".equals(id);
    }

    private LandVehicle spawn(UUID id, LandVehicleSpec spec, Location location) {
        // Ciclul AC (din spec-ul AI al userului, modulul 4): raza verticala in jos la
        // spawn - masina nu mai ramane suspendata daca Y-ul tinta e in aer. Apa = skip.
        if (!isWaterVehicle(spec) && location.getWorld() != null) {
            org.bukkit.util.RayTraceResult ground = location.getWorld().rayTraceBlocks(
                    location, new Vector(0, -1, 0), 20.0, org.bukkit.FluidCollisionMode.NEVER, true);
            if (ground != null && ground.getHitBlock() != null) {
                location.setY(ground.getHitBlock().getY() + 1.0);
            }
        }
        LandVehicle vehicle = LandVehicle.spawn(plugin, id, location, spec, trailers);
        vehicles.put(id, vehicle);
        pendingWorlds.remove(id);
        index(vehicle);
        updateChunkTicket(vehicle);
        return vehicle;
    }

    /**
     * FiveM garages: vehicles stored away per-owner. A garaged vehicle is NOT in the world
     * (no entities, no chunk ticket) and re-enters the world at the player's feet on demand.
     * Parking an UNOWNED vehicle claims it (first-park rule, so legacy vehicles become owned).
     */
    public enum ParkResult { PARKED, CLAIMED_AND_PARKED, OCCUPIED, NOT_OWNER }

    public ParkResult park(LandVehicle vehicle, Player player) {
        if (vehicle.occupied()) {
            return ParkResult.OCCUPIED;
        }
        java.util.Optional<UUID> owner = vehicle.owner();
        boolean claiming = owner.isEmpty();
        if (!claiming && !owner.get().equals(player.getUniqueId())) {
            return ParkResult.NOT_OWNER;
        }
        if (!vehicles.containsKey(vehicle.id())) {
            return ParkResult.NOT_OWNER;
        }
        if (claiming) {
            vehicle.setOwner(player.getUniqueId());
        }
        if (corruptLocation(vehicle.location())) {
            player.sendRichMessage("<red>Vehiculul are coordonate corupte si nu poate fi parcat; a fost eliminat.</red>");
            unindex(vehicle);
            releaseChunkTicket(vehicle);
            trailers.onVehicleRemoved(vehicle.id());
            vehicles.remove(vehicle.id());
            pendingWorlds.remove(vehicle.id());
            vehicle.remove();
            save();
            plugin.getLogger().severe("[Protectie] Vehicul " + vehicle.id()
                    + " eliminat la park: coordonate NaN/Inf, nu a intrat in garaj.");
            return ParkResult.NOT_OWNER;
        }
        StoredVehicle stored = StoredVehicle.from(vehicle);
        unindex(vehicle);
        releaseChunkTicket(vehicle);
        trailers.onVehicleRemoved(vehicle.id());
        vehicles.remove(vehicle.id());
        pendingWorlds.remove(vehicle.id());
        vehicle.remove();
        garage.put(stored.id(), stored);
        save();
        return claiming ? ParkResult.CLAIMED_AND_PARKED : ParkResult.PARKED;
    }

    /** QUIT AUTO-GARAGE (server economy): when the owner leaves, every vehicle of theirs is
     *  stored in the garage immediately - nothing of theirs stays in the world consuming
     *  tick/bandwidth while they're offline. Forced (ignores occupancy: the quitting player
     *  may still be seated during the event). */
    /** Magazin auto: masina cumparata intra DIRECT in garajul global (nu mai apare pe strada).
     *  Rezervor plin, stare imaculata, proprietar = cumparatorul. */
    public UUID storeNewVehicle(org.bukkit.entity.Player player, LandVehicleSpec spec) {
        StoredVehicle stored = new StoredVehicle(UUID.randomUUID(), spec.id(),
                player.getWorld().getUID().toString(), player.getWorld().getName(),
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(),
                player.getLocation().getYaw(), 0.0F, spec.energyCapacity(),
                0.0D, 0.0D, 0.0D, LandVehicleSpec.STANDARD_TRACTION, 0.0D,
                0.0F, 0.0F, 0.0F, 0.0F, false, java.util.Map.of(), java.util.Map.of(),
                player.getUniqueId().toString(), "",
                "IRON", "STANDARD", 100.0D, -1, "NONE", null,
                spec.factoryInstalledEngine(), spec.hasWheels(), 0.0D,
                spec.factoryInstalledEngine()); // motorul de fabrica e VIRTUAL la cumparare
        garage.put(stored.id(), stored);
        save();
        return stored.id();
    }

    /** Pull-out fee charged ONLY when a vehicle is taken out at a PUBLIC parking spot.
     *  At the personal spot the SAME global list leaves for free (location-based pricing). */
    public double impoundFee() {
        return Math.max(0.0D, plugin.getConfig().getDouble("economy.public-garage-retrieval-fee", 500.0D));
    }

    /**
     * Quit flow of the ONE global garage: every vehicle of the leaving owner is stored
     * under garage.<uuid>.* immediately - no flags, no differentiation, nothing of his
     * stays in the world ticking while he is offline. Where he pulls it OUT decides the
     * price: personal circle = free, public circle = the config fee.
     */
    public int parkAllOwnedBy(java.util.UUID playerId) {
        int storedCount = 0;
        for (LandVehicle vehicle : new java.util.ArrayList<>(vehicles.values())) {
            if (!vehicle.owner().map(playerId::equals).orElse(false)) {
                continue;
            }
            if (vehicle.showcase() || vehicle.onJack()) {
                // showcase = al podiumului; PE CRIC = in service: ramane in lume
                // peste restart (garajul nu ia masini ridicate la mecanic).
                continue;
            }
            StoredVehicle stored = StoredVehicle.from(vehicle);
            unindex(vehicle);
            releaseChunkTicket(vehicle);
            trailers.onVehicleRemoved(vehicle.id());
            vehicles.remove(vehicle.id());
            pendingWorlds.remove(vehicle.id());
            vehicle.remove();
            garage.put(stored.id(), stored);
            storedCount++;
        }
        if (storedCount > 0) {
            save();
            plugin.getLogger().info("[Garaj] Quit: " + storedCount
                    + " vehicule au intrat in garajul global (acasa = gratuit, public = "
                    + impoundFee() + "$).");
        }
        return storedCount;
    }

    /** CICLUL AD (decizia userului - reguli trust real-life): trustul = VOLAN cu
     *  proprietarul in preajma, PUNCT. Soferul cu trust iese de pe server -> masina
     *  intra INSTANT in garajul PROPRIETARULUI: nu apare in garajul altcuiva (garajul
     *  ramane strict owner-only), nu ramane abandonata in lume (anti-abuz/anti-lag).
     *  Masinile fara proprietar (taxi public) nu sunt atinse. Aceleasi exceptii ca la
     *  quit-clasic: podium-ul si masina pe cric raman in lume. */
    public int parkDrivenByNonOwner(java.util.UUID quitterId) {
        int storedCount = 0;
        for (LandVehicle vehicle : new java.util.ArrayList<>(vehicles.values())) {
            if (vehicle.showcase() || vehicle.onJack()) {
                continue;
            }
            if (vehicle.owner().map(quitterId::equals).orElse(false)) {
                continue; // masinile LUI le acopera parkAllOwnedBy
            }
            java.util.UUID ownerId = vehicle.owner().orElse(null);
            boolean quitterDrivesIt = vehicle.driver()
                    .map(d -> d.getUniqueId().equals(quitterId)).orElse(false);
            if (ownerId == null || !quitterDrivesIt) {
                continue;
            }
            StoredVehicle stored = StoredVehicle.from(vehicle);
            String displayName = vehicle.spec().displayName();
            unindex(vehicle);
            releaseChunkTicket(vehicle);
            trailers.onVehicleRemoved(vehicle.id());
            vehicles.remove(vehicle.id());
            pendingWorlds.remove(vehicle.id());
            vehicle.remove();
            garage.put(stored.id(), stored);
            storedCount++;
            org.bukkit.entity.Player ownerOnline = plugin.getServer().getPlayer(ownerId);
            if (ownerOnline != null) {
                String quitterName = org.bukkit.Bukkit.getOfflinePlayer(quitterId).getName();
                ownerOnline.sendRichMessage("<gray>[Garaj] Masina ta <gold>" + displayName
                        + "</gold> a intrat in garaj: soferul " + (quitterName == null ? "" : quitterName)
                        + " (trust) a iesit de pe server.</gray>");
            }
        }
        if (storedCount > 0) {
            save();
            plugin.getLogger().info("[Garaj] " + storedCount
                    + " vehicule conduse cu trust au intrat in garajul proprietarului (soferul a iesit).");
        }
        return storedCount;
    }

    /** CICLUL AF: nimeni nu e in niciun scaun/carrier al rig-ului. Scanner complet
     *  peste toate entitatile (sofer + pasageri + carrieri tehnici). */
    private static boolean nobodyAboard(LandVehicle vehicle) {
        for (Entity entity : vehicle.rig().entities()) {
            if (!entity.getPassengers().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** CICLUL AF: limita de stationare in cerc, din config (minute -> secunde).
     *  0 sau negativ = sistemul e oprit complet. Default 5 minute. */
    private int autoStoreLimitSeconds() {
        return Math.max(0, (int) (plugin.getConfig().getDouble("garage.auto-store-park-minutes", 5.0D) * 60.0D));
    }

    /** CICLUL AF: pune vehiculul in garajul proprietarului ACUM (aceeasi ruta de
     *  serializare ca parkAllOwnedBy / parkDrivenByNonOwner) si anunta proprietarul
     *  online cu un mesaj de motiv. Returneaza true daca s-a mutat. */
    private boolean storeVehicleNow(LandVehicle vehicle, String ownerMessageRich) {
        StoredVehicle stored = StoredVehicle.from(vehicle);
        String displayName = vehicle.spec().displayName();
        UUID ownerId = vehicle.owner().orElse(null);
        unindex(vehicle);
        releaseChunkTicket(vehicle);
        trailers.onVehicleRemoved(vehicle.id());
        vehicles.remove(vehicle.id());
        pendingWorlds.remove(vehicle.id());
        parkedInSpotSeconds.remove(vehicle.id());
        vehicle.remove();
        garage.put(stored.id(), stored);
        if (ownerId != null && ownerMessageRich != null && !ownerMessageRich.isEmpty()) {
            Player ownerOnline = plugin.getServer().getPlayer(ownerId);
            if (ownerOnline != null) {
                ownerOnline.sendRichMessage("<gray>[Garaj] Masina ta <gold>" + displayName
                        + "</gold> " + ownerMessageRich + "</gray>");
            }
        }
        return true;
    }

    /** CICLUL AF - parcare-cu-timer (ruleaza la 1 secunda): masina GOALA sta intr-un
     *  cerc marcat (personal sau public) mai mult de limita configurata -> intra
     *  singura in garajul proprietarului. Podiumul si masina de pe cric sunt exceptate.
     *  Taxi-urile fara proprietar nu au garaj - raman in lume (cost 0, hibernam). */
    public void autoStoreSweep() {
        final int limitSeconds = autoStoreLimitSeconds();
        if (limitSeconds <= 0) {
            return;
        }
        parkedInSpotSeconds.keySet().retainAll(vehicles.keySet());
        int storedNow = 0;
        for (LandVehicle vehicle : new ArrayList<>(vehicles.values())) {
            boolean eligible = !vehicle.showcase() && !vehicle.onJack()
                    && nobodyAboard(vehicle)
                    && vehicle.owner().isPresent()
                    && plugin.garageStations().insideAnySpot(vehicle.location());
            if (!eligible) {
                parkedInSpotSeconds.remove(vehicle.id());
                continue;
            }
            int seconds = parkedInSpotSeconds.merge(vehicle.id(), 1, Integer::sum);
            if (seconds < limitSeconds) {
                continue;
            }
            if (storeVehicleNow(vehicle, "a intrat la garaj: a stat in parcare peste "
                    + (limitSeconds / 60) + " minute.")) {
                storedNow++;
            }
        }
        if (storedNow > 0) {
            save();
            plugin.getLogger().info("[Garaj] Auto-store: " + storedNow
                    + " vehicule mutate in garajul proprietarilor (parcare expirata).");
        }
    }

    /** CICLUL AH: contactele cu conurile/placile de boost, scanate doar cat masina
     *  se misca. Conul atins in viteza se rastoarna (dispare cu drop de item); placa
     *  impinge masina pe directia sagetii. Scanare mica: 3x3 orizontal x 3 niveluri
     *  (groapa/sub slab/pardiune acoperite) = max 27 citiri per masina in miscare. */
    private void handleTrafficProps(LandVehicle vehicle) {
        Location center = vehicle.location();
        org.bukkit.World world = center.getWorld();
        if (world == null) {
            return;
        }
        org.bukkit.util.Vector velocity = vehicle.velocity();
        boolean movingFast = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ()
                >= 0.05D * 0.05D;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    org.bukkit.block.Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind kind =
                            com.mrcrayfish.vehicle.paper.world.TrafficProps.kindOf(block);
                    if (kind == com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.CONE) {
                        if (movingFast) {
                            com.mrcrayfish.vehicle.paper.world.TrafficProps.knockCone(block);
                        } else {
                            // CICLUL AN - gard virtual: gazda de fir (vezi TrafficProps
                            // .coneData) nu are coliziune nativa, deci la atingere
                            // LENTA taiem DOAR componenta de viteza care intra in con.
                            // Componenta care iese ramane libera: cu spatele iesi oricand.
                            // Rezultat: conul se simte ca un gard, DAR randarea ramane
                            // incapabila de culling (firul transparent nu oculeaza nimic).
                            double toConeX = block.getX() + 0.5D - center.getX();
                            double toConeZ = block.getZ() + 0.5D - center.getZ();
                            double flatDist = Math.hypot(toConeX, toConeZ);
                            if (flatDist > 1.0E-4D) {
                                double dirX = toConeX / flatDist;
                                double dirZ = toConeZ / flatDist;
                                double intoCone = velocity.getX() * dirX
                                        + velocity.getZ() * dirZ;
                                if (intoCone > 0.0D) {
                                    velocity.setX(velocity.getX() - intoCone * dirX);
                                    velocity.setZ(velocity.getZ() - intoCone * dirZ);
                                    vehicle.setVelocity(velocity);
                                }
                            }
                        }
                    } else if (kind == com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.BOOST_PAD) {
                        applyBoostPad(vehicle, block, velocity);
                    } else if (kind == com.mrcrayfish.vehicle.paper.world.TrafficProps.Kind.STEEP_RAMP) {
                        applyBoostRamp(vehicle, block, velocity);
                    }
                }
            }
        }
    }

    /** CICLUL AL - placa de boost EXACT ca in mod (BoostPadBlock.entityInside):
     *  boost CONTINUU cat timp masina e deasupra plăcii (0.06/tick, fără cooldown),
     *  doar daca masina se misca IN DIRECTIA sagetii (yaw la cardinalul placii,
     *  ca in mod `facing == entity.getDirection()`), cap de viteza din config.
     *  Sunetul formal "vehicle:block.boost_pad.boost" la INTRARE (edge-trigger). */
    private void applyBoostPad(LandVehicle vehicle, org.bukkit.block.Block pad,
                               org.bukkit.util.Vector velocity) {
        org.bukkit.block.BlockFace facing =
                com.mrcrayfish.vehicle.paper.world.TrafficProps.padFacing(pad);
        if (facing == null) {
            return;
        }
        // Poarta de directie ca in mod: masina merge pe directia sagetii cardinale.
        float yaw = vehicle.location().getYaw();
        org.bukkit.block.BlockFace carFacing = com.mrcrayfish.vehicle.paper.world
                .TrafficProps.yawToFacing(yaw);
        if (carFacing != facing) {
            lastPadIngress.remove(vehicle.id());
            return;
        }
        double power = plugin.getConfig().getDouble("physics.boost-pad-accel", 0.06D);
        double maxSpeed = plugin.getConfig().getDouble("physics.boost-pad-max-speed", 1.6D);
        org.bukkit.util.Vector dir = com.mrcrayfish.vehicle.paper.world.TrafficProps.facingVector(facing);
        double px = velocity.getX() + dir.getX() * power;
        double pz = velocity.getZ() + dir.getZ() * power;
        if (px * px + pz * pz <= maxSpeed * maxSpeed) {
            vehicle.setVelocity(new org.bukkit.util.Vector(px, velocity.getY(), pz));
        }
        Location at = pad.getLocation().add(0.5D, 0.25D, 0.5D);
        // Sunetul oficial de boost doar la INTRAREA pe aceasta placa (edge-trigger)
        Integer last = lastPadIngress.get(vehicle.id());
        if (last == null || last != pad.hashCode()) {
            lastPadIngress.put(vehicle.id(), pad.hashCode());
            for (org.bukkit.entity.Player near : pad.getWorld().getPlayers()) {
                if (near.getLocation().distanceSquared(at) <= 40.0D * 40.0D) {
                    near.playSound(net.kyori.adventure.sound.Sound.sound(
                            net.kyori.adventure.key.Key.key("vehicle", "block.boost_pad.boost"),
                            net.kyori.adventure.sound.Sound.Source.NEUTRAL, 1.0F, 0.5F),
                            at.getX(), at.getY(), at.getZ());
                }
            }
        }
        pad.getWorld().spawnParticle(org.bukkit.Particle.FLAME, at, 4, 0.35D, 0.05D, 0.35D, 0.008D);
    }

    /** CICLUL AO - rampa de boost abrupta EXACT ca in mod (SteepBoostRampBlock
     *  .entityInside): aceeasi poarta de directie ca la placa, dar tile-ul ei e
     *  BoostTileEntity(1.0F) = boost DUBLU fata de placa (0.5F), sunetul oficial
     *  "vehicle:block.boost_pad.boost" la intrare (edge) cu VOLUM 2.0 (in mod placa
     *  are volum 1.0), iar descrierea oficiala a blocului (en_us.lang) spune
     *  "sends your vehicle high into the air": aici lift vertical continuu cat timp
     *  esti pe rampa - inlocuieste urcarea pe mesh-ul inclinat din mod, care nu
     *  exista fizic intr-un pack de blocuri. setLaunching(3) din mod = doar 3 tick
     *  de scutire de fall-damage; vehiculele noastre nu iau fall-damage => no-op. */
    private void applyBoostRamp(LandVehicle vehicle, org.bukkit.block.Block ramp,
                                org.bukkit.util.Vector velocity) {
        org.bukkit.block.BlockFace ascent =
                com.mrcrayfish.vehicle.paper.world.TrafficProps.rampFacing(ramp);
        if (ascent == null) {
            return;
        }
        // Poarta de directie identica cu modul: facing-ul rampei == directia masinii.
        float yaw = vehicle.location().getYaw();
        org.bukkit.block.BlockFace carFacing = com.mrcrayfish.vehicle.paper.world
                .TrafficProps.yawToFacing(yaw);
        if (carFacing != ascent) {
            lastPadIngress.remove(vehicle.id());
            return;
        }
        double power = plugin.getConfig().getDouble("physics.boost-ramp-accel", 0.12D);
        double maxSpeed = plugin.getConfig().getDouble("physics.boost-pad-max-speed", 1.6D);
        double lift = plugin.getConfig().getDouble("physics.boost-ramp-lift", 0.38D);
        org.bukkit.util.Vector dir = com.mrcrayfish.vehicle.paper.world.TrafficProps.facingVector(ascent);
        double px = velocity.getX() + dir.getX() * power;
        double pz = velocity.getZ() + dir.getZ() * power;
        double vy = Math.max(velocity.getY(), lift);
        if (px * px + pz * pz <= maxSpeed * maxSpeed) {
            vehicle.setVelocity(new org.bukkit.util.Vector(px, vy, pz));
        } else {
            vehicle.setVelocity(new org.bukkit.util.Vector(velocity.getX(), vy, velocity.getZ()));
        }
        Location at = ramp.getLocation().add(0.5D, 0.6D, 0.5D);
        // Sunetul oficial de boost la INTRAREA pe aceasta rampa (edge-trigger),
        // volum 2.0F ca in SteepBoostRampBlock (placa are 1.0F), pitch 0.5F ambele.
        Integer last = lastPadIngress.get(vehicle.id());
        if (last == null || last != ramp.hashCode()) {
            lastPadIngress.put(vehicle.id(), ramp.hashCode());
            for (org.bukkit.entity.Player near : ramp.getWorld().getPlayers()) {
                if (near.getLocation().distanceSquared(at) <= 40.0D * 40.0D) {
                    near.playSound(net.kyori.adventure.sound.Sound.sound(
                            net.kyori.adventure.key.Key.key("vehicle", "block.boost_pad.boost"),
                            net.kyori.adventure.sound.Sound.Source.NEUTRAL, 2.0F, 0.5F),
                            at.getX(), at.getY(), at.getZ());
                }
            }
        }
        ramp.getWorld().spawnParticle(org.bukkit.Particle.FLAME, at, 4, 0.35D, 0.1D, 0.35D, 0.01D);
    }

    /* ============== CICLUL AU - vitezometrul pe bara de XP a jucatorului ==============
     * Cerinta userului: ceas de viteza FRUMOS si nativ, fara sa atin
     * scoreboard-ul serverului (TAB are sidebar-ul) si cu valori de o masina
     * normala (max ~150 km/h). Solutia: NIVELUL de XP = km/h (numar mare, verde,
     * deasupra hotbar-ului), iar bara de XP se umple pe scala configurabila.
     * XP-ul adevarat nu se pierde: la prima urcare retinem nivel/progres/total si
     * le restauream la coborare, delogare sau join dupa un crash (PDC backup). */

    /** Ruleaza la fiecare 5 tick-uri pentru un vehicul activ. */
    private void tickSpeedo(LandVehicle vehicle) {
        if (!plugin.getConfig().getBoolean("hud.speedo", true)) {
            return;
        }
        Player driver = vehicle.driver().orElse(null);
        if (driver == null) {
            return;
        }
        UUID id = driver.getUniqueId();
        if (!hijackedXp.containsKey(id)) {
            double level = driver.getLevel();
            double progress = driver.getExp();
            double total = driver.getTotalExperience();
            hijackedXp.put(id, new double[]{level, progress, total});
            driver.getPersistentDataContainer().set(XP_HUD_BACKUP,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    level + "," + progress + "," + total);
        }
        // Ciclul AV->AW: NIVELUL RAMANE 0 (fara cifra verde); umplerea barei =
        // viteza REALA (aceeasi baza ca odometrul, via smoothedSpeedBps) pe scala
        // maxima configurabila. Nu mai citim velocity (inflat de pad-uri).
        double bps = vehicle.smoothedSpeedBps();
        int fullScale = Math.max(60,
                plugin.getConfig().getInt("hud.speed-fullscale-kmh", 150));
        int kmh = Math.max(0, Math.min(fullScale, (int) Math.round(bps * 3.6D)));
        driver.setLevel(0);
        driver.setExp((float) Math.max(0.0D, Math.min(1.0D, (double) kmh / fullScale)));
    }

    /** Scoate ceasul celor care au coborat/delogat intre doua curatenii (1s). */
    private void cleanupXpHud() {
        if (hijackedXp.isEmpty()) {
            return;
        }
        java.util.Set<UUID> driving = new java.util.HashSet<>();
        for (LandVehicle vehicle : vehicles.values()) {
            vehicle.driver().ifPresent(driver -> driving.add(driver.getUniqueId()));
        }
        hijackedXp.keySet().removeIf(id -> {
            if (driving.contains(id)) {
                return false;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                restoreXp(player);
            }
            return true;
        });
    }

    /** Repune EXACT XP-ul de dinainte de urcare si sterge markerul de backup. */
    public void restoreXp(Player player) {
        if (player == null) {
            return;
        }
        double[] backup = hijackedXp.remove(player.getUniqueId());
        if (backup == null) {
            // Recuperare dupa crash/restart: markerul supravietuieste in PDC.
            String marker = player.getPersistentDataContainer().get(XP_HUD_BACKUP,
                    org.bukkit.persistence.PersistentDataType.STRING);
            if (marker == null) {
                return;
            }
            String[] parts = marker.split(",");
            if (parts.length == 3) {
                try {
                    backup = new double[]{Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]), Double.parseDouble(parts[2])};
                } catch (NumberFormatException ignored) {
                    return;
                }
            }
        }
        if (backup == null) {
            return;
        }
        player.setLevel((int) backup[0]);
        player.setExp((float) Math.max(0.0D, Math.min(1.0D, backup[1])));
        player.setTotalExperience(Math.max(0, (int) backup[2]));
        player.getPersistentDataContainer().remove(XP_HUD_BACKUP);
    }

    /** True daca jucatorul are in prezent ceasul de viteza pe XP. */
    public boolean isXpHijacked(UUID playerId) {
        return hijackedXp.containsKey(playerId);
    }

    /** Spawns the garaged vehicle next to its owner, exactly like pulling out of a garage. */
    public boolean retrieve(Player player, UUID vehicleId) {
        return retrieve(player, vehicleId, null);
    }

    /** Same, but the vehicle exits at the physical STATION anchor when one is known (GTA 1:1). */
    public boolean retrieve(Player player, UUID vehicleId, Location anchor) {
        StoredVehicle stored = garage.get(vehicleId);
        if (stored == null) {
            return false;
        }
        if (stored.owner() != null && !UUID.fromString(stored.owner()).equals(player.getUniqueId())) {
            return false;
        }
        LandVehicleSpec spec = LandVehicleSpec.byId(stored.type());
        if (spec == null) {
            return false;
        }
        Location base = anchor != null ? anchor.clone() : player.getLocation();
        base.setPitch(0.0F);
        // Ciclul AE: garajul scoate masina EXACT din centrul cercului (anchor-ul statiei).
        // Offset-ul de 2.5m in fata ramane doar pentru fallback-ul fara anchor
        // (pozitie jucator) ca sa nu spawneze fix sub picioarele lui.
        Location spot = base.clone();
        if (anchor == null) {
            spot.add(base.getDirection().multiply(2.5));
        }
        spot.setY(base.getY());
        spot.setYaw(base.getYaw());
        // CICLUL AF - tow la livrare: punctul de scoatere e blocat de vehicule GOALE
        // cu proprietar? Fiecare blocant intra SINGUR in garajul PROPRIETARULUI lui
        // (ridicare automata, fara pierderi), apoi masina ceruta apare liber in centru.
        // Ignorate: podium, masina pe cric, vehicul OCUPAT (nu furam de sub nimeni),
        // taxi fara proprietar (nu are garaj in care sa intre).
        int clearedNow = 0;
        for (LandVehicle blocker : new ArrayList<>(vehicles.values())) {
            Location blockerLoc = blocker.location();
            if (blockerLoc.getWorld() == null || !blockerLoc.getWorld().equals(spot.getWorld())) {
                continue;
            }
            if (blockerLoc.distanceSquared(spot) > 4.0D * 4.0D) {
                continue;
            }
            if (blocker.showcase() || blocker.onJack() || !nobodyAboard(blocker)
                    || blocker.owner().isEmpty()) {
                continue;
            }
            if (storeVehicleNow(blocker, "a intrat la garaj: ocupa locul de livrare al altui vehicul.")) {
                clearedNow++;
            }
        }
        if (clearedNow > 0) {
            save();
            player.sendRichMessage("<gray>[Garaj] Locul de livrare era ocupat: " + clearedNow
                    + " mas. mutate automat la garajul proprietarilor.</gray>");
        }
        LandVehicle vehicle = spawn(stored.id(), spec, spot);
        vehicle.refreshGroundState();
        vehicle.setFuel(stored.fuel());
        vehicle.restoreStorage(stored.chestAttached(), stored.storageContents());
        vehicle.restoreOpenPartStates(stored.openPartStates());
        vehicle.restorePlaneState(0.0F, stored.planeRoll(), stored.propellerSpeed(),
                stored.flapAngle(), stored.elevatorAngle());
        if (stored.owner() != null) {
            vehicle.setOwner(UUID.fromString(stored.owner()));
        }
        vehicle.restoreTrustedDrivers(parseTrusted(stored.trusted()));
        garage.remove(vehicleId);
        save();
        return true;
    }

    /** One garaged vehicle, exposed to the GUI: id + type + saved fuel level. */
    public record GarageEntry(UUID id, String type, float fuel) {
    }

    /** All vehicles this player currently has stored in the garage. */
    public List<GarageEntry> garagedOf(Player player) {
        List<GarageEntry> own = new ArrayList<>();
        for (StoredVehicle stored : garage.values()) {
            if (stored.owner() != null && stored.owner().equals(player.getUniqueId().toString())) {
                own.add(new GarageEntry(stored.id(), stored.type(), stored.fuel()));
            }
        }
        return own;
    }

    /** Vehicles the player can park from HERE: owned by them, or still unowned (claimable). */
    public List<LandVehicle> parkableNearby(Player player, double radius) {
        List<LandVehicle> result = new ArrayList<>();
        for (LandVehicle vehicle : vehicles.values()) {
            if (vehicle.showcase()) {
                continue;
            }
            java.util.Optional<UUID> owner = vehicle.owner();
            if (owner.isPresent() && !owner.get().equals(player.getUniqueId())) {
                continue;
            }
            if (vehicle.location().getWorld() != player.getWorld()) {
                continue;
            }
            if (vehicle.location().distance(player.getLocation()) <= radius) {
                result.add(vehicle);
            }
        }
        return result;
    }

    /** This player's OWN vehicles within the radius (E-key lock toggle targets). */
    public List<LandVehicle> ownedWithin(Player player, double radius) {
        List<LandVehicle> result = new ArrayList<>();
        UUID uid = player.getUniqueId();
        for (LandVehicle vehicle : vehicles.values()) {
            if (vehicle.location().getWorld() == player.getWorld()
                    && vehicle.location().distance(player.getLocation()) <= radius
                    && vehicle.owner().map(uid::equals).orElse(false)) {
                result.add(vehicle);
            }
        }
        return result;
    }

    /** Ca vehiclesWithin, DAR include si vehiculele showcase (podium dealer) -
     *  folosit de adoptarea/repararea podiumurilor la boot (bug: podiile vechi
     *  duplicau masina dupa restart pentru ca sweep-ul ignora showcase-urile). */
    public List<LandVehicle> showcasesWithin(Location centre, double radius) {
        List<LandVehicle> result = new ArrayList<>();
        World world = centre.getWorld();
        for (LandVehicle vehicle : vehicles.values()) {
            if (!vehicle.showcase()) {
                continue;
            }
            Location location = vehicle.location();
            if (world != null && world != location.getWorld()) {
                continue;
            }
            if (location.distance(centre) <= radius) {
                result.add(vehicle);
            }
        }
        return result;
    }

    /** All of our live vehicles within the radius (station auto-park yard sweep). */
    public List<LandVehicle> vehiclesWithin(Location centre, double radius) {
        List<LandVehicle> result = new ArrayList<>();
        World world = centre.getWorld();
        for (LandVehicle vehicle : vehicles.values()) {
            if (vehicle.showcase()) {
                continue;
            }
            Location location = vehicle.location();
            if (world != null && world != location.getWorld()) {
                continue;
            }
            if (location.distance(centre) <= radius) {
                result.add(vehicle);
            }
        }
        return result;
    }

    public CarriedVehicle pickUp(LandVehicle vehicle) {
        if (vehicle.occupied() || vehicles.remove(vehicle.id()) == null) {
            return null;
        }
        CarriedVehicle carried = new CarriedVehicle(vehicle.spec(), vehicle.fuel(), vehicle.velocity(),
                vehicle.traction(), vehicle.verticalVelocity(), vehicle.chestAttached(),
                vehicle.storageContents(), vehicle.openPartStates());
        unindex(vehicle);
        releaseChunkTicket(vehicle);
        trailers.onVehicleRemoved(vehicle.id());
        vehicle.remove();
        return carried;
    }

    public LandVehicle place(CarriedVehicle carried, Location location) {
        LandVehicle vehicle = spawn(carried.spec(), location);
        vehicle.setFuel(carried.fuel());
        vehicle.setVelocity(carried.velocity());
        vehicle.setTraction(carried.traction());
        vehicle.setVerticalVelocity(carried.verticalVelocity());
        vehicle.restoreStorage(carried.chestAttached(), carried.storageContents());
        vehicle.restoreOpenPartStates(carried.openPartStates());
        return vehicle;
    }

    public boolean remove(LandVehicle vehicle) {
        if (vehicles.remove(vehicle.id()) == null) {
            return false;
        }
        unindex(vehicle);
        releaseChunkTicket(vehicle);
        trailers.onVehicleRemoved(vehicle.id());
        vehicle.remove();
        save();
        return true;
    }

    public Optional<LandVehicle> byEntity(Entity entity) {
        return Optional.ofNullable(entities.get(entity.getUniqueId()));
    }

    public Optional<LandVehicle> byId(UUID id) {
        return Optional.ofNullable(vehicles.get(id));
    }

    public boolean hasVehicleId(UUID id) {
        return vehicles.containsKey(id) || pendingWorlds.containsKey(id);
    }

    public TrailerManager trailers() {
        return trailers;
    }

    public Optional<LandVehicle> nearest(Location origin, double maximumDistance) {
        return nearest(origin, maximumDistance, true);
    }

    /** includeShowcase=false: masinile de podium (decorative) nu pot fi tinta
     *  comenzilor destructive (/vehicle remove le sare complet). */
    public Optional<LandVehicle> nearest(Location origin, double maximumDistance,
                                         boolean includeShowcase) {
        if (origin.getWorld() == null) {
            return Optional.empty();
        }
        LandVehicle nearest = null;
        double nearestDistance = maximumDistance * maximumDistance;
        for (LandVehicle vehicle : vehicles.values()) {
            if (!includeShowcase && vehicle.showcase()) {
                continue;
            }
            Location location = vehicle.location();
            if (location.getWorld() == null || !location.getWorld().equals(origin.getWorld())) {
                continue;
            }
            double distance = location.distanceSquared(origin);
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = vehicle;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Collection<LandVehicle> vehicles() {
        return java.util.Collections.unmodifiableCollection(vehicles.values());
    }

    public int pendingVehicleCount() {
        return pendingWorlds.size();
    }

    /** Cate vehicule stau acum in garajul global (pentru /vehicle perf, ciclul AB). */
    public int garageSize() {
        return garage.size();
    }

    public void handleInteraction(Player player, Entity clicked) {
        LandVehicle vehicle = entities.get(clicked.getUniqueId());
        if (vehicle == null) {
            return;
        }
        if (!player.hasPermission("vehicle.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea vehicle.use.</red>");
            return;
        }
        if (vehicle.showcase()) {
            return; // podium cars: never pushed, carried, attached or driven (buy = right-click)
        }
        // Sistemul mecanic: uneltele (cheie de mecanic/spray/motor/roti)
        // consuma interactiunea - jucatorul NU urca in masina.
        if (plugin.mechanic() != null && plugin.mechanic().handleToolClick(player, vehicle,
                player.getInventory().getItemInMainHand())) {
            save();
            return;
        }
        // Pe cric: nu urci in masina (e in service).
        if (vehicle.onJack()) {
            player.sendRichMessage("<yellow>Masina e pe cric in service - nu poti urca in ea.</yellow>");
            return;
        }
        // Ciclul Z: nu mai exista chei / incuiere - accesul la volan e filtrat
        // exclusiv de trust (canDrive: proprietar + cei autorizati), pasagerii urca liber.
        if (vehicle.handleSpecialInteraction(player, clicked)) {
            save();
            return;
        }
        if ("shopping_cart".equals(vehicle.spec().id()) && player.isSneaking()) {
            togglePush(player, vehicle);
            return;
        }
        if (trailers.attachHeldToVehicle(player, vehicle)) {
            return;
        }
        if (player.isSneaking() && trailers.pickUpVehicle(player, vehicle)) {
            return;
        }
        /* r37 bug fix: a player who was still registered as the Shopping Cart's pusher
         * (e.g. they sneak-interacted to grab it, then clicked it again without
         * sneaking) could also mount its seat. LandVehicle#tick runs tickPushed()
         * first whenever a pusher is set, which re-derives the cart's position from
         * the pushing player's own location every tick; but a mounted player's
         * location is itself re-derived from the seat's position by the server's
         * native passenger sync. Together those formed a feedback loop that pushed
         * both the cart and the player upward indefinitely - the reported "infinite
         * flight" exploit. Mounting while still the pusher is refused outright. */
        if (player.getUniqueId().equals(vehicle.pusher())) {
            player.sendRichMessage("<red>Lasă căruciorul (ghemuit + interacționează) "
                    + "înainte să urci în el.</red>");
            return;
        }
        if (vehicle.mount(player)) {
            if (vehicle.spec().motionType() == LandVehicleSpec.MotionType.HELICOPTER) {
                player.sendRichMessage("<gray>W/S înainte/înapoi, A/D deplasare laterală, "
                        + "Space urcare, Sprint coborâre, privește pentru direcție. "
                        + "Inimile monturii indică nivelul combustibilului.</gray>");
            } else {
                player.sendRichMessage("<gray>W/S accelerație, A/D direcție, Space frână de mână, "
                        + "Shift coborâre. Inimile monturii indică nivelul combustibilului.</gray>");
            }
        } else {
            player.sendRichMessage("<red>Nu mai este niciun loc liber în acest vehicul.</red>");
        }
    }

    /** ShoppingCartEntity has no interact code of its own in the source; sneak-clicking it
     * to grab/release from behind is this port's stand-in for walking into its hitbox. */
    private void togglePush(Player player, LandVehicle vehicle) {
        UUID current = vehicle.pusher();
        if (player.getUniqueId().equals(current)) {
            vehicle.setPusher(null);
            player.sendRichMessage("<gray>Ai lăsat căruciorul.</gray>");
        } else if (current != null) {
            player.sendRichMessage("<red>Căruciorul este deja împins de altcineva.</red>");
        } else if (vehicle.occupied()) {
            /* r37: the same mount/push feedback loop described in handleInteraction()
             * can also be entered this way around (mount first, then sneak-interact to
             * start pushing while still seated), so pushing a cart with anyone already
             * sitting in it is refused too. */
            player.sendRichMessage("<red>Cineva stă deja în cărucior.</red>");
        } else {
            vehicle.setPusher(player.getUniqueId());
            player.sendRichMessage("<gray>Împingi căruciorul din spate. Mergi pentru a-l deplasa; "
                    + "interacționează din nou ghemuit pentru a-l lăsa.</gray>");
        }
    }

    /** Hammer: masina se demonteaza in "cufar de transport" (item) pentru proprietar.
     *  Continutul depozitelor se varsa pe jos (nu bagam inventare intregi in PDC). */
    public boolean dismantleToCrate(Player player, LandVehicle vehicle) {
        if (!hasVehicleId(vehicle.id())) {
            return false;
        }
        for (org.bukkit.inventory.ItemStack content : vehicle.storageContents()
                .values().stream().flatMap(java.util.List::stream).toList()) {
            if (content != null && !content.getType().isAir()) {
                vehicle.location().getWorld().dropItemNaturally(vehicle.location(), content);
            }
        }
        String payload = com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.cratePayload(
                vehicle.spec().id(), (int) vehicle.fuel(), vehicle.engineTier().name(),
                vehicle.wheelType().name(), (int) Math.round(vehicle.health()),
                vehicle.paintColor(), false, // slot 6 = legacy "locked" (scos in ciclul Z)
                vehicle.owner().map(UUID::toString).orElse(""));
        player.getInventory().addItem(com.mrcrayfish.vehicle.paper.mechanic.MechanicItems
                .vehicleCrate(vehicle.spec().id(), vehicle.spec().displayName(), payload));
        remove(vehicle);
        save();
        return true;
    }

    /** Click pe jos cu cufarul: vehiculul revine pe strada cu starea salvata in payload.
     *  Plasarea se face DOAR pe fata de sus a unui bloc (altfel masina ajunge ingropata
     *  in perete/"dispare" - spec final, bug randare), centrata pe blocul tintit. */
    public boolean deployCrate(Player player, org.bukkit.inventory.ItemStack crate,
                               Location face, float yaw) {
        com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.Kind kind =
                com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.kindOf(crate);
        if (kind != com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.Kind.VEHICLE_CRATE) {
            return false;
        }
        if (!player.hasPermission("vehicle.use")) {
            player.sendRichMessage("<red>Nu ai permisiunea vehicle.use.</red>");
            return false;
        }
        String[] data = com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.parseCratePayload(
                com.mrcrayfish.vehicle.paper.mechanic.MechanicItems.cratePayloadOf(crate));
        LandVehicleSpec spec = LandVehicleSpec.byId(data[0]);
        if (spec == null) {
            player.sendRichMessage("<red>Cufarul tine un vehicul necunoscut.</red>");
            return false;
        }
        if (!vehiclesWithin(face, 1.6D).isEmpty()) {
            player.sendRichMessage("<red>Nu e loc - alta masina e deja aici.</red>");
            return false;
        }
        // Centrare stricta pe blocul tintit (spawn-ul pe colt punea masina jumatate
        // ingropata in blocurile vecine) si rotire catre jucator, pitch 0.
        Location location = new Location(face.getWorld(), face.getBlockX() + 0.5D,
                face.getBlockY(), face.getBlockZ() + 0.5D, yaw, 0.0F);
        LandVehicle vehicle = spawn(spec, location);
        try {
            vehicle.setFuel(Math.max(0.0F, Integer.parseInt(data[1])));
            vehicle.setEngineTier(com.mrcrayfish.vehicle.paper.mechanic.EngineTier.fromName(data[2]));
            vehicle.setWheelType(com.mrcrayfish.vehicle.paper.mechanic.WheelType.fromName(data[3]));
            double health = Double.parseDouble(data[4]);
            if (health < 100.0D) {
                vehicle.applyDamage(100.0D - health, null);
            }
            int color = Integer.parseInt(data[5]);
            if (color >= 0) {
                vehicle.setPaintColor(color);
            }
            // data[6] = legacy "locked" din cufar - concept scos in ciclul Z; il ignoram,
            // dar slotul ramane in format ca cufarele vechi sa se deschida corect.
            if (!data[7].isEmpty()) {
                vehicle.setOwner(UUID.fromString(data[7]));
            }
        } catch (RuntimeException parseFailure) {
            plugin.getLogger().warning("Crate payload partial corupt: " + parseFailure.getMessage());
        }
        // Aplica starea finala (model/pivoti/culoare/piese) INAINTE de primul frame:
        // un update fortat al rig-ului in acelasi tick cu aplicatorii de stare.
        vehicle.settleVisualState();
        save();
        return true;
    }

    public void handleAttack(Player player, Entity clicked) {
        LandVehicle vehicle = entities.get(clicked.getUniqueId());
        if (vehicle == null || !player.hasPermission("vehicle.use")) {
            return;
        }
        if (vehicle.handleSpecialAttack(player, clicked)) {
            save();
        }
    }

    public void handleInventoryClose(Inventory inventory) {
        for (LandVehicle vehicle : vehicles.values()) {
            if (vehicle.ownsStorage(inventory)) {
                vehicle.storageClosed(inventory);
                save();
                return;
            }
        }
    }

    /** The sync COLLECTION half of save(): entity state is read on the main thread here. */
    private YamlConfiguration collectYaml() {
        trailers.save();
        YamlConfiguration data = new YamlConfiguration();
        for (StoredVehicle stored : pendingWorlds.values()) {
            write(data, stored);
        }
        for (LandVehicle vehicle : vehicles.values()) {
            if (vehicle.showcase()) {
                continue; // showroom podium cars are display-only, never persisted
            }
            write(data, StoredVehicle.from(vehicle));
        }
        for (StoredVehicle stored : garage.values()) {
            write(data, "garage." + stored.id(), stored);
        }
        return data;
    }

    /** Anti-lag async write: consecutive saves COALESCE - only the newest snapshot is queued. */
    private final Object asyncSaveLock = new Object();
    private YamlConfiguration pendingAsyncYaml;
    private boolean asyncSaveQueued;

    /** Save to a same-directory temporary file and atomically replace the last good snapshot.
     *  Collection stays synchronous; the disk I/O itself runs on an async worker (0 TPS cost). */
    public void save() {
        YamlConfiguration data = collectYaml();
        synchronized (asyncSaveLock) {
            pendingAsyncYaml = data;
            if (asyncSaveQueued) {
                return;
            }
            asyncSaveQueued = true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (;;) {
                YamlConfiguration snapshot;
                synchronized (asyncSaveLock) {
                    snapshot = pendingAsyncYaml;
                }
                writeYamlAtomically(snapshot);
                synchronized (asyncSaveLock) {
                    if (pendingAsyncYaml == snapshot) {
                        asyncSaveQueued = false;
                        return;
                    }
                }
            }
        });
    }

    /** Synchronous flush - used ONLY in stop()/onDisable, where async work would be lost. */
    public void saveNow() {
        writeYamlAtomically(collectYaml());
    }

    /** Save a ready snapshot to a same-directory temporary file and atomically move it in place. */
    private void writeYamlAtomically(YamlConfiguration data) {
        File temporary = null;
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            temporary = File.createTempFile("vehicles-", ".yml.tmp", plugin.getDataFolder());
            data.save(temporary);
            try {
                Files.move(temporary.toPath(), storageFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not atomically save vehicles.yml", exception);
        } finally {
            if (temporary != null && temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }

    private static void write(YamlConfiguration data, StoredVehicle stored) {
        write(data, "vehicles." + stored.id(), stored);
    }

    private static void write(YamlConfiguration data, String path, StoredVehicle stored) {
        data.set(path + ".type", stored.type());
        data.set(path + ".world", stored.worldId());
        data.set(path + ".world-name", stored.worldName());
        data.set(path + ".x", stored.x());
        data.set(path + ".y", stored.y());
        data.set(path + ".z", stored.z());
        data.set(path + ".yaw", stored.yaw());
        data.set(path + ".pitch", stored.pitch());
        data.set(path + ".fuel", stored.fuel());
        data.set(path + ".velocity.x", stored.velocityX());
        data.set(path + ".velocity.y", stored.velocityY());
        data.set(path + ".velocity.z", stored.velocityZ());
        data.set(path + ".traction", stored.traction());
        data.set(path + ".vertical-velocity", stored.verticalVelocity());
        data.set(path + ".plane.roll", stored.planeRoll());
        data.set(path + ".plane.propeller-speed", stored.propellerSpeed());
        data.set(path + ".plane.flap-angle", stored.flapAngle());
        data.set(path + ".plane.elevator-angle", stored.elevatorAngle());
        data.set(path + ".storage.chest-attached", stored.chestAttached());
        data.set(path + ".storage.items",
                stored.storageContents().getOrDefault("moped_chest", List.of()));
        for (Map.Entry<String, List<ItemStack>> entry : stored.storageContents().entrySet()) {
            data.set(path + ".storage.compartments." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Boolean> entry : stored.openPartStates().entrySet()) {
            data.set(path + ".cosmetics.open." + entry.getKey(), entry.getValue());
        }
        if (stored.owner() != null) {
            data.set(path + ".owner", stored.owner());
        }
        if (stored.trusted() != null && !stored.trusted().isEmpty()) {
            data.set(path + ".trusted", stored.trusted());
        }
        if (!"IRON".equals(stored.engineTier())) {
            data.set(path + ".mechanic.engine-tier", stored.engineTier());
        }
        if (!"STANDARD".equals(stored.wheelType())) {
            data.set(path + ".mechanic.wheel-type", stored.wheelType());
        }
        if (stored.health() < 100.0D) {
            data.set(path + ".mechanic.health", stored.health());
        }
        if (stored.paintColor() >= 0) {
            data.set(path + ".mechanic.paint-color", stored.paintColor());
        }
        if (stored.jackState() != null && !"NONE".equals(stored.jackState())) {
            data.set(path + ".mechanic.jack-state", stored.jackState());
        }
        if (stored.jackId() != null && !stored.jackId().isEmpty()) {
            data.set(path + ".mechanic.jack-id", stored.jackId());
        }
        if (!stored.engineInstalled()) {
            data.set(path + ".mechanic.engine-installed", false);
        }
        if (!stored.wheelsInstalled()) {
            data.set(path + ".mechanic.wheels-installed", false);
        }
        if (stored.odometer() > 0.0D) {
            data.set(path + ".mechanic.odometer", stored.odometer());
        }
        data.set(path + ".mechanic.engine-virtual", stored.engineVirtual());
    }

    private void load() {
        if (storageFile.isFile()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(storageFile);
            ConfigurationSection section = data.getConfigurationSection("vehicles");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        StoredVehicle stored = StoredVehicle.read(data, key);
                        LandVehicleSpec spec = LandVehicleSpec.byId(stored.type());
                        pendingWorlds.put(stored.id(), stored);
                        if (spec == null) {
                            plugin.getLogger().warning("Keeping unsupported vehicle " + stored.id()
                                    + " in storage until its type is implemented: " + stored.type());
                        } else if (world(stored.worldId(), stored.worldName()) == null) {
                            plugin.getLogger().warning("Deferring vehicle " + stored.id() + ": its world is not loaded");
                        }
                    } catch (RuntimeException exception) {
                        plugin.getLogger().log(Level.WARNING, "Could not load vehicle entry " + key, exception);
                    }
                }
            }
            ConfigurationSection garagedSection = data.getConfigurationSection("garage");
            if (garagedSection != null) {
                for (String key : garagedSection.getKeys(false)) {
                    try {
                        garage.put(UUID.fromString(key),
                                StoredVehicle.read(data, "garage." + key, key));
                    } catch (RuntimeException exception) {
                        plugin.getLogger().log(Level.WARNING, "Could not load garage entry " + key, exception);
                    }
                }
            }
        }
        plugin.getLogger().info("Loaded " + vehicles.size() + " vehicle(s); "
                + pendingWorlds.size() + " deferred, " + garage.size() + " stored in garages.");
    }

    public void onWorldLoaded(World loadedWorld) {
        activateNearbyVehicles(loadedWorld);
    }

    private void restore(StoredVehicle stored, LandVehicleSpec spec, World world) {
        if (!Double.isFinite(stored.x()) || !Double.isFinite(stored.y()) || !Double.isFinite(stored.z())
                || !Float.isFinite(stored.fuel())) {
            plugin.getLogger().severe("[Protectie] Vehicul salvat " + stored.id()
                    + " avea date corupte (NaN/Inf) - ignorat la restore.");
            return;
        }
        Location location = new Location(world, stored.x(), stored.y(), stored.z(), stored.yaw(), 0.0F);
        // Salvare (bug-ul „masina dispare la coborare", cric startY nesetat dupa
        // restart): vehiculele ramase sub lume sau INGROPATE in stanca reapar la
        // suprafata la primul restore, nu pierdute definitiv. Peșterile cu aer
        // si parcarile normale nu sunt atinse (ambele blocuri trebuie sa fie opace).
        org.bukkit.block.Block at = location.getBlock();
        boolean buried = location.getY() < world.getMinHeight() + 2
                || (at.getType().isOccluding()
                        && at.getRelative(org.bukkit.block.BlockFace.UP).getType().isOccluding());
        if (buried) {
            location.setY(world.getHighestBlockYAt(location) + 1.0D);
            plugin.getLogger().warning("[Salvare] Vehiculul " + stored.id()
                    + " era sub lume/in stanca - a fost adus inapoi la suprafata.");
        }
        LandVehicle vehicle = spawn(stored.id(), spec, location);
        vehicle.refreshGroundState();
        vehicle.setFuel(stored.fuel());
        vehicle.setVelocity(new Vector(stored.velocityX(), stored.velocityY(), stored.velocityZ()));
        vehicle.setTraction(stored.traction());
        vehicle.setVerticalVelocity(stored.verticalVelocity());
        vehicle.restoreStorage(stored.chestAttached(), stored.storageContents());
        vehicle.restoreOpenPartStates(stored.openPartStates());
        vehicle.restorePlaneState(stored.pitch(), stored.planeRoll(), stored.propellerSpeed(),
                stored.flapAngle(), stored.elevatorAngle());
        if (stored.owner() != null) {
            vehicle.setOwner(UUID.fromString(stored.owner()));
        }
        vehicle.restoreTrustedDrivers(parseTrusted(stored.trusted()));
        // Starea mecanica (motoare/roti/daune/vopsea/incuiere/cric) - persistata 1-la-1.
        vehicle.setEngineTier(com.mrcrayfish.vehicle.paper.mechanic.EngineTier.fromName(stored.engineTier()));
        vehicle.setEngineInstalled(stored.engineInstalled());
        vehicle.restoreOdometer(stored.odometer());
        vehicle.setEngineVirtual(stored.engineVirtual());
        vehicle.setWheelType(com.mrcrayfish.vehicle.paper.mechanic.WheelType.fromName(stored.wheelType()));
        if (stored.health() < 100.0D) {
            vehicle.applyDamage(100.0D - stored.health(), null);
        }
        if (stored.paintColor() >= 0) {
            vehicle.setPaintColor(stored.paintColor());
        }
        if (stored.jackState() != null) {
            try {
                vehicle.setJackState(LandVehicle.JackState.valueOf(stored.jackState()));
            } catch (IllegalArgumentException ignored) {
                // stare cric necunoscuta - ramanem cu fizica normala
            }
        }
        if (stored.jackId() != null) {
            try {
                vehicle.setJackId(UUID.fromString(stored.jackId()));
            } catch (IllegalArgumentException ignored) {
                // id cric corupt - ignorat
            }
        }
        // Rig complet inainte de primul frame al re-activarii: modelul, culoarea si
        // piesele din starea persistata (la join/restart/activare) - BUG randare.
        vehicle.settleVisualState();
    }

    /** Re-aplica starea vizuala (model/body/culoare/piese) pentru toate vehiculele
     *  incarcate din jurul unui jucator proaspat conectat, ca pachetele clientului sa
     *  aiba totul sanatos din primul frame (spec final - BUG 1: body invizibil la join). */
    public void syncVisualsAround(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        double range = Math.max(32.0D,
                plugin.getConfig().getDouble("performance.activation-distance", 64.0D));
        double rangeSquared = range * range;
        for (LandVehicle vehicle : new ArrayList<>(vehicles.values())) {
            Location location = vehicle.location();
            if (location.getWorld() != player.getWorld()
                    || location.distanceSquared(player.getLocation()) > rangeSquared) {
                continue;
            }
            try {
                if (vehicle.rig().valid()) {
                    vehicle.settleVisualState();
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Visual sync failed for " + vehicle.id()
                        + ": " + exception.getMessage());
            }
        }
    }

    private static boolean matchesWorld(StoredVehicle stored, World world) {
        return world.getUID().toString().equals(stored.worldId()) || world.getName().equals(stored.worldName());
    }

    private World world(String uuid, String name) {
        if (uuid != null) {
            try {
                World world = Bukkit.getWorld(UUID.fromString(uuid));
                if (world != null) {
                    return world;
                }
            } catch (IllegalArgumentException ignored) {
                // Try the name fallback below.
            }
        }
        return name == null ? null : Bukkit.getWorld(name);
    }

    private volatile com.mrcrayfish.vehicle.paper.vehicle.DriveTuning driveTuning;

    private void tick() {
        long perfStart = com.mrcrayfish.vehicle.paper.runtime.PerfTracker.begin();
        try {
        if (++activationTick % 10 == 0) {
            activateNearbyVehicles(null);
        }
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        driveTuning = new com.mrcrayfish.vehicle.paper.vehicle.DriveTuning(
                config.getDouble("physics.global-speed-limit", 100.0D),
                config.getDouble("physics.fuel-consumption-factor", 1.0D),
                Math.min(1.0D, Math.max(0.0D, config.getDouble("physics.fuel-idle-ratio", 0.1D))),
                config.getDouble("physics.fuel-speed-weight", 0.6D),
                config.getDouble("physics.fuel-accel-weight", 0.4D),
                Math.max(1.0D, config.getDouble("physics.fuel-full-burn-speed", 18.0D)),
                Math.max(0.1D, config.getDouble("physics.steering-angle-multiplier", 1.0D)));
        double activationDistance = Math.max(16.0D,
                config.getDouble("performance.activation-distance", 64.0D));
        for (LandVehicle vehicle : new ArrayList<>(vehicles.values())) {
            try {
                if (!vehicle.rig().valid()) {
                    hibernate(vehicle);
                    continue;
                }
                vehicle.tick(driveTuning);
                if (vehicle.resting()) {
                    releaseChunkTicket(vehicle);
                    if (!trailers.referencesVehicle(vehicle.id())
                            && !hasNearbyPlayer(vehicle.location(), activationDistance)) {
                        hibernate(vehicle);
                    }
                } else {
                    updateChunkTicket(vehicle);
                    handleTrafficProps(vehicle);
                    // Ciclul AU: vitezometrul XP la cadenta de 5 tick-uri.
                    if (activationTick % 5 == 0) {
                        tickSpeedo(vehicle);
                    }
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Vehicle tick failed for " + vehicle.id(), exception);
                hibernate(vehicle);
            }
        }
        trailers.tick();
        handleBumperCarCollisions();
        if (activationTick % 20 == 0) {
            cleanupXpHud();
        }
        } finally {
            com.mrcrayfish.vehicle.paper.runtime.PerfTracker.end(perfStart);
        }
    }

    /**
     * BumperCarEntity#push adds the colliding car's own current motion back onto
     * itself (a sudden jolt in whatever direction it was already travelling) and
     * immediately cuts its drive speed, rather than bouncing the two cars apart by
     * their relative velocity. This reproduces that same jolt-then-settle feel
     * using the exposed velocity vector, since the internal currentSpeed state
     * that the original debuffs isn't part of this port's public surface.
     */
    private void handleBumperCarCollisions() {
        List<LandVehicle> bumperCars = new ArrayList<>();
        for (LandVehicle vehicle : vehicles.values()) {
            if ("bumper_car".equals(vehicle.spec().id()) && !vehicle.transported() && !vehicle.resting()) {
                bumperCars.add(vehicle);
            }
        }
        for (Map.Entry<UUID, Integer> entry : bumperCarCooldowns.entrySet()) {
            entry.setValue(entry.getValue() - 1);
        }
        bumperCarCooldowns.values().removeIf(ticks -> ticks <= 0);
        for (int i = 0; i < bumperCars.size(); i++) {
            LandVehicle a = bumperCars.get(i);
            for (int j = i + 1; j < bumperCars.size(); j++) {
                LandVehicle b = bumperCars.get(j);
                if (bumperCarCooldowns.containsKey(a.id()) || bumperCarCooldowns.containsKey(b.id())) {
                    continue;
                }
                Location la = a.location();
                Location lb = b.location();
                if (la.getWorld() == null || !la.getWorld().equals(lb.getWorld())
                        || Math.abs(la.getY() - lb.getY()) > 1.5D) {
                    continue;
                }
                double dx = la.getX() - lb.getX();
                double dz = la.getZ() - lb.getZ();
                double distanceSquared = dx * dx + dz * dz;
                double threshold = a.spec().entityWidth();
                if (distanceSquared >= threshold * threshold) {
                    continue;
                }
                double distance = Math.sqrt(Math.max(distanceSquared, 1.0E-4D));
                double nx = dx / distance;
                double nz = dz / distance;
                boltBumperCar(a, nx, nz);
                boltBumperCar(b, -nx, -nz);
                World world = la.getWorld();
                Location midpoint = la.clone().add(lb).multiply(0.5D);
                float pitch = 0.6F + 0.1F * (float) Math.min(1.0D,
                        (a.velocity().length() + b.velocity().length()) / 2.0D);
                world.playSound(midpoint, "vehicle:entity.bumper_car.bonk",
                        org.bukkit.SoundCategory.NEUTRAL, 1.0F, pitch);
                bumperCarCooldowns.put(a.id(), 10);
                bumperCarCooldowns.put(b.id(), 10);
            }
        }
    }

    private static void boltBumperCar(LandVehicle vehicle, double nx, double nz) {
        Vector current = vehicle.velocity();
        Vector jolted = new Vector(current.getX() + nx * 0.5D, current.getY(), current.getZ() + nz * 0.5D)
                .multiply(0.75D);
        vehicle.setVelocity(jolted);
    }

    private void activateNearbyVehicles(World onlyWorld) {
        double activationDistance = Math.max(16.0D,
                plugin.getConfig().getDouble("performance.activation-distance", 64.0D));
        for (StoredVehicle stored : new ArrayList<>(pendingWorlds.values())) {
            LandVehicleSpec spec = LandVehicleSpec.byId(stored.type());
            World world = world(stored.worldId(), stored.worldName());
            if (spec == null || world == null || (onlyWorld != null && !world.equals(onlyWorld))
                    || !matchesWorld(stored, world)) {
                continue;
            }
            Location location = new Location(world, stored.x(), stored.y(), stored.z());
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || !hasNearbyPlayer(location, activationDistance)) {
                continue;
            }
            try {
                restore(stored, spec, world);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not activate vehicle " + stored.id(), exception);
            }
        }
    }

    private static boolean hasNearbyPlayer(Location location, double distance) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        double maximumSquared = distance * distance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= maximumSquared) {
                return true;
            }
        }
        return false;
    }

    /** ChunkUnloadEvent hook: every vehicle rooted in the unloading chunk is fully
     * hibernated — rig entities removed, complete state (fuel included) serialized
     * through StoredVehicle — so an unloaded chunk costs zero tick CPU and zero live
     * entities. Reactivation happens automatically in activateNearbyVehicles() once a
     * player is near and the chunk is loaded again. */
    public int hibernateInChunk(org.bukkit.Chunk chunk) {
        int hibernated = 0;
        for (LandVehicle vehicle : new ArrayList<>(vehicles.values())) {
            org.bukkit.Location location = vehicle.location();
            org.bukkit.World world = location.getWorld();
            if (world != null && world.equals(chunk.getWorld())
                    && (location.getBlockX() >> 4) == chunk.getX()
                    && (location.getBlockZ() >> 4) == chunk.getZ()
                    && !vehicle.showcase()) {
                hibernate(vehicle);
                hibernated++;
            }
        }
        return hibernated;
    }

    /** A vehicle with NaN/Infinity coordinates is unrecoverable AND would poison
     * vehicles.yml if serialized. Persisting is skipped (drop, severe log) while the
     * world entity is still removed, so a corrupted vehicle can never crash a boot. */
    private static boolean corruptLocation(org.bukkit.Location location) {
        return location == null
                || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ());
    }

    private void hibernate(LandVehicle vehicle) {
        // MASINILE DE PODIUM nu se hiberneaza NICIODATA: sunt display-only, trebuie
        // sa ramana vizibile mereu, iar StoredVehicle NU are camp showcase - daca ar
        // ajunge in pendingWorlds/arhiva, ar reveni ca masina NORMALA (furabila).
        if (vehicle.showcase()) {
            return;
        }
        if (vehicles.remove(vehicle.id()) == null) {
            return;
        }
        try {
            if (corruptLocation(vehicle.location())) {
                pendingWorlds.remove(vehicle.id());
                plugin.getLogger().severe("[Protectie] Vehicul " + vehicle.id()
                        + " avea coordonate corupte (NaN/Inf) - sters, nu salvat.");
            } else {
                pendingWorlds.put(vehicle.id(), StoredVehicle.from(vehicle));
            }
        } finally {
            unindex(vehicle);
            releaseChunkTicket(vehicle);
            vehicle.remove();
        }
    }

    private void index(LandVehicle vehicle) {
        for (Entity entity : vehicle.rig().entities()) {
            entities.put(entity.getUniqueId(), vehicle);
        }
    }

    private void unindex(LandVehicle vehicle) {
        for (Entity entity : vehicle.rig().entities()) {
            entities.remove(entity.getUniqueId());
        }
    }

    private void updateChunkTicket(LandVehicle vehicle) {
        Location location = vehicle.location();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        VehicleChunk next = new VehicleChunk(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
        VehicleChunk previous = chunkTickets.get(vehicle.id());
        if (next.equals(previous)) {
            return;
        }
        next.world().addPluginChunkTicket(next.x(), next.z(), plugin);
        if (previous != null) {
            previous.world().removePluginChunkTicket(previous.x(), previous.z(), plugin);
        }
        chunkTickets.put(vehicle.id(), next);
    }

    private void releaseChunkTicket(LandVehicle vehicle) {
        VehicleChunk chunk = chunkTickets.remove(vehicle.id());
        if (chunk != null) {
            chunk.world().removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
        }
    }

    private void cleanupOrphanedEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(LandVehicleRig.ENTITY_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " orphaned vehicle display entities.");
        }
    }

    public record CarriedVehicle(LandVehicleSpec spec, float fuel, Vector velocity,
                                 float traction, double verticalVelocity, boolean chestAttached,
                                 Map<String, List<ItemStack>> storageContents,
                                 Map<String, Boolean> openPartStates) {
    }

    private record VehicleChunk(World world, int x, int z) {
    }

    private static String trustedCsv(LandVehicle vehicle) {
        return vehicle.trustedDrivers().stream().map(UUID::toString)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static java.util.Set<UUID> parseTrusted(String csv) {
        java.util.Set<UUID> result = new java.util.HashSet<>();
        if (csv != null && !csv.isBlank()) {
            for (String part : csv.split(",")) {
                try {
                    result.add(UUID.fromString(part.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Bad uuid fragment: skip it, keep loading the rest.
                }
            }
        }
        return result;
    }

    private record StoredVehicle(UUID id, String type, String worldId, String worldName,
                                 double x, double y, double z, float yaw, float pitch, float fuel,
                                 double velocityX, double velocityY, double velocityZ,
                                 float traction, double verticalVelocity,
                                 float planeRoll, float propellerSpeed,
                                 float flapAngle, float elevatorAngle, boolean chestAttached,
                                 Map<String, List<ItemStack>> storageContents,
                                 Map<String, Boolean> openPartStates, String owner, String trusted,
                                 String engineTier, String wheelType, double health, int paintColor,
                                 String jackState, String jackId, boolean engineInstalled,
                                 boolean wheelsInstalled, double odometer, boolean engineVirtual) {

        private static StoredVehicle from(LandVehicle vehicle) {
            Location location = vehicle.location();
            World world = location.getWorld();
            if (world == null) {
                throw new IllegalStateException("Cannot persist a vehicle without a world");
            }
            Vector velocity = vehicle.velocity();
            return new StoredVehicle(vehicle.id(), vehicle.spec().id(), world.getUID().toString(), world.getName(),
                    location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
                    vehicle.fuel(), velocity.getX(), velocity.getY(), velocity.getZ(), vehicle.traction(),
                    vehicle.verticalVelocity(), vehicle.planeRoll(), vehicle.propellerSpeed(),
                    vehicle.flapAngle(), vehicle.elevatorAngle(), vehicle.chestAttached(),
                    vehicle.storageContents(), vehicle.openPartStates(),
                    vehicle.owner().map(UUID::toString).orElse(null), trustedCsv(vehicle),
                    vehicle.engineTier().name(), vehicle.wheelType().name(), vehicle.health(),
                    vehicle.paintColor(), vehicle.jackState().name(),
                    vehicle.jackId() == null ? null : vehicle.jackId().toString(),
                    vehicle.engineInstalled(),
                    vehicle.wheelsInstalled(), vehicle.odometerMeters(), vehicle.engineVirtual());
        }

        private static StoredVehicle read(YamlConfiguration data, String key) {
            return read(data, "vehicles." + key, key);
        }

        private static StoredVehicle read(YamlConfiguration data, String path, String key) {
            UUID id = UUID.fromString(key);
            String type = data.getString(path + ".type", "go_kart");
            String worldId = data.getString(path + ".world");
            String worldName = data.getString(path + ".world-name");
            LandVehicleSpec spec = LandVehicleSpec.byId(type);
            float defaultFuel = spec == null ? 0.0F : spec.energyCapacity();
            return new StoredVehicle(id, type, worldId, worldName,
                    data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"),
                    (float) data.getDouble(path + ".yaw"),
                    (float) data.getDouble(path + ".pitch"),
                    (float) data.getDouble(path + ".fuel", defaultFuel),
                    data.getDouble(path + ".velocity.x"), data.getDouble(path + ".velocity.y"),
                    data.getDouble(path + ".velocity.z"),
                    (float) data.getDouble(path + ".traction"),
                    data.getDouble(path + ".vertical-velocity"),
                    (float) data.getDouble(path + ".plane.roll"),
                    (float) data.getDouble(path + ".plane.propeller-speed"),
                    (float) data.getDouble(path + ".plane.flap-angle"),
                    (float) data.getDouble(path + ".plane.elevator-angle"),
                    data.getBoolean(path + ".storage.chest-attached", false),
                    readStorage(data, path), readOpenPartStates(data, path),
                    data.getString(path + ".owner"), data.getString(path + ".trusted"),
                    data.getString(path + ".mechanic.engine-tier"),
                    data.getString(path + ".mechanic.wheel-type"),
                    data.getDouble(path + ".mechanic.health", 100.0D),
                    data.getInt(path + ".mechanic.paint-color", -1),
                    data.getString(path + ".mechanic.jack-state"),
                    data.getString(path + ".mechanic.jack-id"),
                    data.getBoolean(path + ".mechanic.engine-installed", true),
                    data.getBoolean(path + ".mechanic.wheels-installed", true),
                    data.getDouble(path + ".mechanic.odometer", 0.0D),
                    data.getBoolean(path + ".mechanic.engine-virtual",
                            data.getBoolean(path + ".mechanic.engine-installed", true)));
        }

        private static Map<String, List<ItemStack>> readStorage(YamlConfiguration data, String vehiclePath) {
            Map<String, List<ItemStack>> contents = new LinkedHashMap<>();
            List<ItemStack> legacyMoped = readStorageList(data, vehiclePath + ".storage.items");
            if (!legacyMoped.isEmpty()
                    || data.getBoolean(vehiclePath + ".storage.chest-attached", false)) {
                contents.put("moped_chest", legacyMoped);
            }
            ConfigurationSection section = data.getConfigurationSection(
                    vehiclePath + ".storage.compartments");
            if (section != null) {
                for (String storageKey : section.getKeys(false)) {
                    contents.put(storageKey, readStorageList(data,
                            vehiclePath + ".storage.compartments." + storageKey));
                }
            }
            return contents;
        }

        private static List<ItemStack> readStorageList(YamlConfiguration data, String path) {
            List<?> serialized = data.getList(path, List.of());
            List<ItemStack> contents = new ArrayList<>(serialized.size());
            for (Object value : serialized) {
                contents.add(value instanceof ItemStack stack ? stack.clone() : null);
            }
            return contents;
        }

        private static Map<String, Boolean> readOpenPartStates(YamlConfiguration data,
                                                                String vehiclePath) {
            Map<String, Boolean> states = new LinkedHashMap<>();
            ConfigurationSection section = data.getConfigurationSection(vehiclePath + ".cosmetics.open");
            if (section != null) {
                for (String part : section.getKeys(false)) {
                    states.put(part, section.getBoolean(part));
                }
            }
            return states;
        }
    }

}
