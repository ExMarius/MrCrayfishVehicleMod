package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.vehicle.FuelGauge;
import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Multipart vanilla-display rig driven by a generated land-vehicle definition. */
public final class LandVehicleRig {
    public static final String ENTITY_TAG = "mcv_plugin_vehicle";
    private static final int VANILLA_ENTITY_LERP_TICKS = 3;
    /* Ciclul BL: scaunul este un cal la scara minima, invizibil, fara saua:
     * acelasi rider pose + HUD de inimi, iar yaw-ul calareului este clamp-uit
     * vanilla la yaw-ul vehiculului (corp blocat, cap liber). Entitatile sunt
     * excluse din selectoarele /kill si sunt ejectate inainte de remove(), deci
     * bucla din cautarea vanilla a locului de demontare nu apare. */
    private static final double SEAT_CARRIER_SCALE = 0.0625D;
    private static final float SEAT_PASSENGER_OFFSET = 0.7F * (float) SEAT_CARRIER_SCALE;
    private static final double SEAT_GAUGE_MAX_HEALTH = FuelGauge.MAX_HALF_HEARTS;

    private final UUID vehicleId;
    private final LandVehicleSpec spec;
    private final Interaction interaction;
    private final ItemDisplay body;
    private final ItemDisplay engine;
    private final ItemDisplay steering;
    private final ItemDisplay towBar;

    /** A vanilla ItemDisplay draws an item's local +Z axis pointing AWAY from the entity's
     * facing direction (items render 'into the screen'), i.e. turned 180 degrees versus
     * how Minecraft entity models face. Every vehicle part therefore receives this one
     * global yaw correction inside place(). */
    private static final Quaternionf ITEM_DISPLAY_YAW_CORRECTION =
            new Quaternionf().rotateY((float) Math.PI);

    /* THE tow bar math that burned the six failed nudge rounds, written down exactly:
     * tow_bar.json is AUTHORED pre-rotated - its drawbar arm already points at the car
     * when rendered with NO net yaw. But place() applies ITEM_DISPLAY_YAW_CORRECTION (+180 grade) to everything. To exempt the tow bar, its SOURCE rotation must
     * cancel that correction. The Part is created with rotationY = 180, and building the
     * source rotation from the Part's own angles (like every other part does) yields
     * rotateY(+pi); composed with the place() correction the net rotation is
     * rotateY(+pi). mul rotateY(+pi) = rotateY(2*pi) = IDENTITY. The old hand-written code reached the same identity by
     * stacking an unexplained second +pi, so any offset edit LOOKED like a no-op while we
     * were actually editing - wrong file (TRACTOR vs SMART_CAR) - but also fighting an
     * invisible double flip. From here on: ONE correction constant, ONE explicit source
     * rotation built from the Part angles, zero hidden flips. */
    private static final Quaternionf TOW_BAR_SOURCE_ROTATION = new Quaternionf()
            .rotateY((float) Math.PI); // == Part rotationY = 180; place() adds +180 again -> net 0
    private final List<PartDisplay> chassisParts;
    private final List<PartDisplay> forkParts;
    private final ItemDisplay storageChest;
    private final Interaction storageInteraction;
    private final List<WheelDisplay> wheels;
    private final List<SeatCarrier> seats;
    private final List<Entity> entities;
    private float seatGauge = 1.0F;
    private float propellerRotation;
    private float flapAngle;
    private float elevatorAngle;
    private float helicopterBladeRotation;
    private float helicopterJoystickForward;
    private float helicopterJoystickStrafe;
    private boolean removed;

    private LandVehicleRig(UUID vehicleId, LandVehicleSpec spec, Interaction interaction,
                           ItemDisplay body, ItemDisplay engine, ItemDisplay steering,
                           ItemDisplay towBar,
                           List<PartDisplay> chassisParts, List<PartDisplay> forkParts,
                           ItemDisplay storageChest, Interaction storageInteraction,
                           List<WheelDisplay> wheels, List<SeatCarrier> seats, List<Entity> entities) {
        this.vehicleId = vehicleId;
        this.spec = spec;
        this.interaction = interaction;
        this.body = body;
        this.engine = engine;
        this.steering = steering;
        this.towBar = towBar;
        this.chassisParts = chassisParts;
        this.forkParts = forkParts;
        this.storageChest = storageChest;
        this.storageInteraction = storageInteraction;
        this.wheels = wheels;
        this.seats = seats;
        this.entities = entities;
    }

    public static LandVehicleRig spawn(UUID vehicleId, LandVehicleSpec spec, Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Cannot spawn a vehicle without a world");
        }

        List<Entity> all = new ArrayList<>();
        Interaction interaction = world.spawn(location, Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(Math.max(2.2F, spec.entityWidth()));
            hitbox.setInteractionHeight(Math.max(1.1F, spec.entityHeight()));
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
        });
        mark(interaction, vehicleId);
        all.add(interaction);

        ItemDisplay body = display(world, location, model(spec.bodyModel()));
        mark(body, vehicleId);
        all.add(body);

        ItemDisplay engine = partDisplay(world, location, spec.engine(), vehicleId, all);
        ItemDisplay steering = partDisplay(world, location, spec.steering(), vehicleId, all);
        ItemDisplay towBar = spec.canTowTrailers()
                ? partDisplay(world, location, new LandVehicleSpec.Part(spec.towBarModel(),
                spec.towBarVisualCenter(), 1.0F, 0.0F, 180.0F, 0.0F), vehicleId, all)
                : null;

        List<PartDisplay> chassisParts = new ArrayList<>();
        for (LandVehicleSpec.Part part : spec.bodyParts()) {
            chassisParts.add(new PartDisplay(
                    partDisplay(world, location, part, vehicleId, all), part));
        }
        List<PartDisplay> forkParts = new ArrayList<>();
        ItemDisplay storageChest = null;
        Interaction storageInteraction = null;
        if (spec.mopedParts() != null) {
            for (LandVehicleSpec.Part part : spec.mopedParts().forkParts()) {
                forkParts.add(new PartDisplay(
                        partDisplay(world, location, part, vehicleId, all), part));
            }
            storageChest = display(world, location, new ItemStack(Material.CHEST));
            storageChest.setVisibleByDefault(false);
            mark(storageChest, vehicleId);
            all.add(storageChest);

            storageInteraction = world.spawn(location, Interaction.class, hitbox -> {
                hitbox.setInteractionWidth(0.8F);
                hitbox.setInteractionHeight(0.8F);
                hitbox.setResponsive(true);
                hitbox.setPersistent(false);
            });
            mark(storageInteraction, vehicleId);
            all.add(storageInteraction);
        }

        List<WheelDisplay> wheels = new ArrayList<>();
        for (LandVehicleSpec.Wheel properties : spec.wheels()) {
            ItemDisplay wheel = display(world, location, model("standard_wheel"));
            mark(wheel, vehicleId);
            all.add(wheel);
            wheels.add(new WheelDisplay(wheel, properties));
        }

        List<SeatCarrier> seats = new ArrayList<>();
        boolean bodyAssigned = false;
        for (LandVehicleSpec.Seat seat : spec.seats()) {
            /* BL17: fiecare scaun are ancora LUI (display invizibil, item gol).
             * Inainte scaunul soferului folosea display-ul corpului masinii, iar
             * rotatia corpului se compunea cu rotatia scaunului => oscilatie. */
            ItemDisplay anchor = display(world, location, new ItemStack(Material.AIR));
            mark(anchor, vehicleId);
            all.add(anchor);
            if (seat.driver() && !bodyAssigned) {
                bodyAssigned = true;
            }
            seats.add(new SeatCarrier(anchor, seat));
        }
        if (!bodyAssigned) {
            throw new IllegalArgumentException("Land vehicle " + spec.id() + " has no driver seat");
        }

        LandVehicleRig rig = new LandVehicleRig(vehicleId, spec, interaction, body, engine, steering,
                towBar, Collections.unmodifiableList(chassisParts),
                Collections.unmodifiableList(forkParts), storageChest, storageInteraction,
                Collections.unmodifiableList(wheels), Collections.unmodifiableList(seats), all);
        rig.update(location, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0);
        return rig;
    }

    private static ItemDisplay partDisplay(World world, Location location, LandVehicleSpec.Part part,
                                           UUID vehicleId, List<Entity> all) {
        if (part == null) {
            return null;
        }
        ItemDisplay display = display(world, location, model(part.model()));
        mark(display, vehicleId);
        all.add(display);
        return display;
    }

    private static ItemDisplay display(World world, Location location, ItemStack stack) {
        return world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(VANILLA_ENTITY_LERP_TICKS);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setShadowRadius(0.0F);
            display.setShadowStrength(0.0F);
        });
    }

    /** BL16: scaunul poate fi si ARMOR STAND (entitate vie fara AI, deci nu se
     *  roteste dupa camera cum face calul). Implicit: calul = varianta C
     *  validata de user; comutabil live cu /vehicle seatkind. */
    private static volatile String seatKind = "cal";

    /** BL34: mod "tinut pe scaun de server" (nu pasager). Doar asa serverul
     *  poate roti si CORPUL si PRIVIREA odata cu vehiculul; pretul: jucatorul
     *  nu mai este pasager, deci nu are pose-ul de barca. */
    /* BL43 (decizie user: "asa ar trebui sa faci si cu mine... nu conteaza
     * capul acum, vreau sa reusim doar asta"): IMPLICIT = tinut pe scaun de
     * server, fara calarie. Corpul + privirea se rotesc cu masina, mouse-ul nu
     * te mai intoarce. /vehicle seatride on revine la calarie. */
    /* BL45 (cerinta user: "vreau sezut si fara tremurat"): pasager din nou
     * (doar asa exista poza de sezut), dar fara corectiile fortate care
     * tremurau - calul e tinut de ancora scaunului. */
    private static volatile boolean seatRide = true;

    public static boolean seatRide() {
        return seatRide;
    }

    public static void setSeatRide(boolean value) {
        seatRide = value;
    }

    public static void setSeatKind(String kind) {
        if (java.util.List.of("porc", "cal", "armorstand", "barca").contains(kind)) {
            seatKind = kind;
        }
    }

    public static String seatKind() {
        return seatKind;
    }

    /** BL18: sursa rotatiei scaunului, comutabila live:
     *  anchor = rotim doar ancora (scaunul fiind pasager, mosteneste rotatia)
     *  mount  = rotim doar scaunul (ancora 0)
     *  both   = ambele
     *  off    = niciuna (scaunul rămâne cum e) */
    /* BL42: sursa de rotatie implicita = ANCORA. Pasagerii (calul/barca/
     * armor stand-ul) primesc in fiecare tick rotatia vehiculului-parinte, deci
     * rotirea directa a scaunului era anulata ("dau viraj si raman in aceeasi
     * pozitie"). Rotind ancora, scaunul si corpul merg cu masina. */
    private static volatile String seatYawMode = "anchor";

    public static void setSeatYawMode(String mode) {
        seatYawMode = mode;
    }

    public static String seatYawMode() {
        return seatYawMode;
    }

    /* BL51 (cerinta user: "o comanda care sa imi spawneze mai multe vehicule si
     * variante pana dam de cea mai buna"): fiecare vehicul poate purta propria
     * varianta de scaun, ca sa le testam una langa alta, simultan. null =
     * mosteneste setarea globala (/vehicle seatkind etc.). */
    private String variantName;
    private String variantKind;
    private Boolean variantRide;
    private String variantYawMode;
    private Boolean variantLookLock;
    private boolean variantKeepPosition;
    /** BL61 (cerinta user: "lipim entitatea de cal/barca de masina"): scaunul
     *  devine PASAGER al corpului masinii, deci il urmeaza nativ (fara teleport
     *  in fiecare tick => fara tremurat si fara "moved too quickly"). */
    private boolean variantAttachToBody;
    /** BL62 (clarificare user: "de lipit e lipit, dar trebuie lipit de tot, cu
     *  tot cu pozitia masinii"): scaunul primeste pozitia + rotatia ancorei in
     *  FIECARE tick, fara niciun prag - practic sudat pe masina. */
    private boolean variantGlueEveryTick;

    public void applyVariant(String name, String kind, boolean ride, String yawMode, boolean lockLook) {
        this.applyVariant(name, kind, ride, yawMode, lockLook, false);
    }

    public void applyVariant(String name, String kind, boolean ride, String yawMode,
                             boolean lockLook, boolean keepPosition) {
        this.applyVariant(name, kind, ride, yawMode, lockLook, keepPosition, false);
    }

    public void applyVariant(String name, String kind, boolean ride, String yawMode,
                             boolean lockLook, boolean keepPosition, boolean attachToBody) {
        applyVariant(name, kind, ride, yawMode, lockLook, keepPosition, attachToBody, false);
    }

    public void applyVariant(String name, String kind, boolean ride, String yawMode,
                             boolean lockLook, boolean keepPosition, boolean attachToBody,
                             boolean glueEveryTick) {
        this.variantGlueEveryTick = glueEveryTick;
        this.variantName = name;
        this.variantKind = kind;
        this.variantRide = ride;
        this.variantYawMode = yawMode;
        this.variantLookLock = lockLook;
        this.variantKeepPosition = keepPosition;
        this.variantAttachToBody = attachToBody;
    }

    /** BL52: fortarea privirii se poate activa si PER VEHICUL (testul A/B). */
    private boolean effLookLock() {
        return variantLookLock != null ? variantLookLock : lookLock();
    }

    public String variantName() {
        return variantName;
    }

    private String effSeatKind() {
        return variantKind != null ? variantKind : seatKind;
    }

    private boolean effSeatRide() {
        return variantRide != null ? variantRide : seatRide;
    }

    private String effSeatYawMode() {
        return variantYawMode != null ? variantYawMode : seatYawMode;
    }

    /** BL20 (cerinta user: "sa nu ma lase sa ma invart cu mouse-ul ... calul una
     *  cu masina"): privirea calareului este tinuta pe directia vehiculului.
     *  Toleranta in grade: 0 = blocaj total, 45 = semnificativ liber,
     *  999 = dezactivat. Serverul retrimite rotatia jucatorului in fiecare tick
     *  cand abaterea depaseste toleranta, deci camera se roteste cu masina. */
    private static volatile float lookLockTolerance = 999.0F;

    public static void setLookLockTolerance(float degrees) {
        lookLockTolerance = Math.max(0.0F, Math.min(999.0F, degrees));
    }

    public static float lookLockTolerance() {
        return lookLockTolerance;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    /** Recreaza scaunele (calaretii sunt dati jos si trebuie sa urce din nou). */
    public void resetSeats() {
        for (SeatCarrier carrier : seats) {
            discardSeat(carrier);
            carrier.rider = null;
        }
    }

    /** BL19 (cerinta user: scaunul de la inceput): PORC cu sa, fara AI, tăiat
     *  de la orice input (MOVEMENT_SPEED 0), invizibil. Corpul calareului stă
     *  lipit de vehicul pentru ca serverul ii impune yaw-ul in fiecare tick. */
    private static Entity pigCarrier(World world, Location location, UUID vehicleId) {
        org.bukkit.entity.Pig pig = world.spawn(location, org.bukkit.entity.Pig.class, carrier -> {
            carrier.setSaddle(true);
            carrier.setAI(false);
            carrier.setGravity(false);
            carrier.setCollidable(false);
            /* BL45: calul este VIZIBIL si la marime normala (cerinta user). */
            carrier.setInvisible(false);
            carrier.setInvulnerable(true);
            carrier.setSilent(true);
            carrier.setPersistent(false);
            carrier.setRemoveWhenFarAway(false);
            AttributeInstance scale = carrier.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(1.0D);
            }
            AttributeInstance speed = carrier.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.0D);
            }
            AttributeInstance maximumHealth = carrier.getAttribute(Attribute.MAX_HEALTH);
            if (maximumHealth != null) {
                maximumHealth.setBaseValue(SEAT_GAUGE_MAX_HEALTH);
            }
            carrier.setHealth(SEAT_GAUGE_MAX_HEALTH);
        });
        mark(pig, vehicleId);
        return pig;
    }

    private static Entity armStandCarrier(World world, Location location, UUID vehicleId) {
        org.bukkit.entity.ArmorStand stand = world.spawn(location,
                org.bukkit.entity.ArmorStand.class, carrier -> {
            carrier.setInvisible(true);
            carrier.setMarker(false);
            carrier.setGravity(false);
            carrier.setInvulnerable(true);
            carrier.setSilent(true);
            carrier.setPersistent(false);
            carrier.setRemoveWhenFarAway(false);
            AttributeInstance scale = carrier.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(SEAT_CARRIER_SCALE);
            }
            AttributeInstance maximumHealth = carrier.getAttribute(Attribute.MAX_HEALTH);
            if (maximumHealth != null) {
                maximumHealth.setBaseValue(SEAT_GAUGE_MAX_HEALTH);
            }
            carrier.setHealth(SEAT_GAUGE_MAX_HEALTH);
        });
        mark(stand, vehicleId);
        return stand;
    }

    private static Entity seatCarrier(World world, Location location, UUID vehicleId, boolean saddled) {
        /* Ciclul BL (feedback user): scaun = cal FARA saua. Calul are clampa
         * vanilla de riding: yaw-ul calareului este legat de yaw-ul vehiculului,
         * deci corpul ramane blocat pe scaun si doar capul se roteste liber
         * (~45 grade), exact ca in mod. Fara saua entitatea NU este
         * "controllable" de catre client (era problema cu porcul cu saua:
         * clientul ii impunea rotatia dupa camera si se batea cap in cap cu
         * setRotation-ul server-ului -> corpul se invartea/oscila). */
        Horse seat = world.spawn(location, Horse.class, carrier -> {
            /* Ciclul BM (seattest a confirmat: varianta C tine corpul fix):
             * cal CU saua + tamed => vanilla activeaza clampa de riding
             * (yaw-ul calareului legat de yaw-ul monturii -> corp blocat,
             * cap liber stanga-dreapta). MOVEMENT_SPEED 0 => jucatorul nu
             * poate plimba montura cu WASD; rotatia prin mouse ramane. */
            /* BL41 (raport user: "dau viraj si eu raman intr-o pozitie"):
             * calul CU sa este "controllable" de client - clientul ii impune
             * rotatia dupa camera, deci serverul il roteste degeaba si calul
             * ramane pe directia veche cand masina vireaza. FARA sa, calul nu
             * mai are controlling passenger, deci serverul e singurul care ii
             * da yaw-ul (yaw-ul masinii). */
            carrier.setTamed(true);
            if (saddled) {
                /* BL51: varianta "cal cu sa" - controlling passenger, adica
                 * exact comportamentul din vanilla cand calaresti. */
                carrier.getInventory().setItem(0, new ItemStack(Material.SADDLE));
            }
            carrier.setAI(false);
            carrier.setGravity(false);
            carrier.setCollidable(false);
            carrier.setInvisible(true);
            carrier.setInvulnerable(true);
            carrier.setSilent(true);
            carrier.setPersistent(false);
            carrier.setRemoveWhenFarAway(false);
            AttributeInstance scale = carrier.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(SEAT_CARRIER_SCALE);
            }
            AttributeInstance speed = carrier.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.0D);
            }
            AttributeInstance maximumHealth = carrier.getAttribute(Attribute.MAX_HEALTH);
            if (maximumHealth != null) {
                maximumHealth.setBaseValue(SEAT_GAUGE_MAX_HEALTH);
            }
            carrier.setHealth(SEAT_GAUGE_MAX_HEALTH);
        });
        mark(seat, vehicleId);
        return seat;
    }

    /** BL28 (cerinta user, preluata din discutia cu celalalt AI): scaun = BARCA
     *  vanilla. Barca da EXACT pose-ul din joc ("sezut in barca", picioarele
     *  inainte), iar serverul ii impune yaw-ul vehiculului in fiecare tick
     *  (maintainSeat), deci barca merge cu masina la viraje; soferul controleaza
     *  masina, nu barca. Barca e pasager al ancorei scaunului, deci pozitia ei
     *  este fortata de server in fiecare tick.
     *  Atentie: la Boat NU exista setCollidable (verificat in BL7) si
     *  setInvisible nu are efect pe barci - modelul barcii se vede. */
    private static Entity boatCarrier(World world, Location location, UUID vehicleId) {
        /* BL32: in 1.21.4 NU exista EntityType.BOAT (barcile sunt OAK_BOAT,
         * BIRCH_BOAT...) - world.spawn(..., Boat.class) arunca
         * "Cannot spawn an entity for org.bukkit.entity.Boat" si scaunul nu
         * aparea deloc. Spawnam pe tipul concret si il configuram apoi. */
        Entity spawned = world.spawnEntity(location, org.bukkit.entity.EntityType.OAK_BOAT);
        if (spawned instanceof org.bukkit.entity.Boat boat) {
            boat.setGravity(false);
            boat.setInvulnerable(true);
            boat.setSilent(true);
            boat.setPersistent(false);
        }
        mark(spawned, vehicleId);
        return spawned;
    }

    /* BL31: barca are UN SINGUR loc de pasager in vanilla, deci nu putem pune
     * o entitate-neutra pe locul ei (jucatorul nu mai putea urca - "nu pot sa
     * ma urc la barca"). Barca ramane normala, vizibila, cu jucatorul in ea, si
     * este rotita de server pe yaw-ul vehiculului in fiecare tick. */

    private Entity createSeatMount(SeatCarrier carrier) {
        if (removed || !carrier.anchor.isValid()) {
            return null;
        }
        Location location = carrier.anchor.getLocation();
        Entity seat = switch (effSeatKind()) {
            case "armorstand" -> armStandCarrier(location.getWorld(), location, vehicleId);
            case "cal" -> seatCarrier(location.getWorld(), location, vehicleId, false);
            case "calsa" -> seatCarrier(location.getWorld(), location, vehicleId, true);
            case "barca", "barcafixa", "barcah" -> boatCarrier(location.getWorld(), location, vehicleId);
            default -> pigCarrier(location.getWorld(), location, vehicleId);
        };
        if (seat instanceof org.bukkit.entity.Boat) {
            /* Barca are UN singur loc de pasager si, daca pe el sta un jucator,
             * clientul conduce barca (rotire dupa camera). Punem pe locul ei un
             * suport invizibil, iar jucatorul sta pe suport -> barca ramane
             * "nemiscata, ca si cum ar fi pe masina". */
            Entity support = location.getWorld().spawnEntity(location,
                    org.bukkit.entity.EntityType.ARMOR_STAND);
            if (support instanceof org.bukkit.entity.ArmorStand stand) {
                stand.setInvisible(true);
                stand.setMarker(false);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setPersistent(false);
                AttributeInstance scale = stand.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(SEAT_CARRIER_SCALE);
                }
            }
            mark(support, vehicleId);
            if (seat.addPassenger(support)) {
                carrier.support = support;
                entities.add(support);
            } else {
                support.remove();
            }
        }
        /* BL46 (cauza dovedita): daca scaunul este PASAGER al ancorei, Minecraft
         * ii rescrie rotatia din parinte in fiecare tick, deci calul nu putea
         * fi rotit de server si ramanea pe directia veche la viraje. Scaunul
         * ramane entitate LIBERA: serverul il muta si il roteste el in fiecare
         * tick (vezi maintainSeat), deci merge cu masina. */
        carrier.mount = seat;
        entities.add(seat);
        if (seat instanceof org.bukkit.entity.LivingEntity living) {
            applySeatGauge(living);
        }
        return seat;
    }

    /** BL59: la barca "barcah" jucatorul NU sta direct pe barca (altfel clientul
     *  poate conduce barca), ci pe suportul invizibil din interiorul ei. Barca
     *  ramane complet sub controlul serverului. */
    private Entity riderHost(SeatCarrier carrier, Entity seat) {
        if ("barcah".equals(effSeatKind())
                && carrier.support != null && carrier.support.isValid()) {
            return carrier.support;
        }
        return seat;
    }

    private void discardSeat(SeatCarrier carrier) {
        Entity seat = carrier.mount;
        carrier.mount = null;
        if (carrier.support != null) {
            entities.remove(carrier.support);
            carrier.support.remove();
            carrier.support = null;
        }
        if (seat != null) {
            entities.remove(seat);
            seat.remove();
        }
    }

    private static ItemStack model(String name) {
        // Regresie dovedita live: stack-ul PAPER cu item_model nu mai randeaza pe
        // acest server (vehiculele nevopsite erau invizibile; vopsitele - carefolosesc
        // LEATHER_HORSE_ARMOR via coloredStack - apareau). Unificam pe materialul
        // dovedit-vizibil. Culoarea ALBA explicita pune dyed_color pe stack: clientul
        // tine din stack (calea dovedita la vopsire) si nu mai cade pe default-ul
        // definitiei, pe care clientii noi il interpreteaza ARGB cu alpha 0 (fetele
        // cu tintindex deveneau transparente - "gol la interior" la roti, body invizibil).
        ItemStack item = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle", name));
        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta armor) {
            armor.setColor(org.bukkit.Color.fromRGB(0xF9FFFE));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static void mark(Entity entity, UUID vehicleId) {
        entity.addScoreboardTag(ENTITY_TAG);
        entity.addScoreboardTag("mcv_" + vehicleId);
    }

    public void update(Location root, float renderSteeringAngle, float frontWheelRotation,
                       float rearWheelRotation, float wheelieAngle, float bodyRoll,
                       boolean engineRunning, int tickCount) {
        if (!valid()) {
            return;
        }

        float yaw = root.getYaw();
        Quaternionf chassisRotation = usesAircraftBodyRotation()
                ? SourceTransforms.aircraftRotation(wheelieAngle, bodyRoll)
                : SourceTransforms.chassisRotation(wheelieAngle, bodyRoll);
        Vector3f bodyOrigin = point(spec.bodyOrigin());
        Vector3f driverSeat = pigAnchor(chassis(driverSeatOffset(), wheelieAngle, bodyRoll));
        Location renderAnchor = local(root, driverSeat);

        interaction.teleport(root);
        interaction.setRotation(yaw, 0.0F);
        if (tickCount % 5 == 0) {
            updateBrightness(root);
        }

        Vector3f bodyCenter = chassis(bodyOrigin, wheelieAngle, bodyRoll);
        place(body, renderAnchor, relativeToSeat(bodyCenter, driverSeat), yaw, chassisRotation,
                new Vector3f(spec.bodyScale() * spec.modelScaleCorrection(spec.bodyModel())),
                new Quaternionf());

        if (engine != null) {
            LandVehicleSpec.Part part = spec.engine();
            Quaternionf shake = new Quaternionf();
            if (engineRunning && (tickCount & 1) == 1) {
                shake.rotateX(radians(0.5F)).rotateZ(radians(0.5F)).rotateY(radians(-0.5F));
            }
            Vector3f relative = point(part.center()).sub(bodyOrigin);
            shake.transform(relative);
            Vector3f center = chassis(new Vector3f(bodyOrigin).add(relative), wheelieAngle, bodyRoll);
            Quaternionf rotation = new Quaternionf(chassisRotation).mul(shake);
            Quaternionf sourceRotation = new Quaternionf()
                    .rotateX(radians(part.rotationX()))
                    .rotateY(radians(part.rotationY()))
                    .rotateZ(radians(part.rotationZ()));
            place(engine, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                    new Vector3f(part.scale()), sourceRotation);
        }

        float steeringRotation = renderSteeringAngle / spec.maxSteeringAngle()
                * spec.steeringVisualAngle();
        Quaternionf forkRotation = SourceTransforms.motorcycleSteering(spec.motorcycle(), steeringRotation);
        if (steering != null) {
            LandVehicleSpec.Part part = spec.steering();
            Vector3f unsteeredCenter = point(part.center()).add(point(spec.steeringDisplayOffset()));
            Vector3f center;
            Quaternionf rotation;
            if (spec.motorcycle() != null) {
                center = SourceTransforms.forkPoint(
                        unsteeredCenter, bodyOrigin, spec.motorcycle(), forkRotation);
                center = chassis(center, wheelieAngle, bodyRoll);
                rotation = new Quaternionf(chassisRotation).mul(forkRotation)
                        .rotateX(radians(part.rotationX()))
                        .rotateY(radians(part.rotationY()))
                        .rotateZ(radians(part.rotationZ()));
            } else {
                center = chassis(unsteeredCenter, wheelieAngle, bodyRoll);
                rotation = new Quaternionf(chassisRotation)
                        .rotateX(radians(part.rotationX()))
                        .rotateY(radians(part.rotationY() + steeringRotation))
                        .rotateZ(radians(part.rotationZ()));
            }
            place(steering, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                    new Vector3f(part.scale()), new Quaternionf());
        }

        for (PartDisplay partDisplay : chassisParts) {
            placePropertyPart(partDisplay, renderAnchor, driverSeat,
                    yaw, wheelieAngle, bodyRoll, chassisRotation);
        }

        for (PartDisplay partDisplay : forkParts) {
            LandVehicleSpec.Part part = partDisplay.properties;
            Vector3f center = SourceTransforms.forkPoint(
                    point(part.center()), bodyOrigin, spec.motorcycle(), forkRotation);
            center = chassis(center, wheelieAngle, bodyRoll);
            Quaternionf rotation = new Quaternionf(chassisRotation).mul(forkRotation);
            Quaternionf sourceRotation = new Quaternionf()
                    .rotateX(radians(part.rotationX()))
                    .rotateY(radians(part.rotationY()))
                    .rotateZ(radians(part.rotationZ()));
            place(partDisplay.entity, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                    new Vector3f(part.scale()), sourceRotation);
        }

        if (storageChest != null && spec.mopedParts() != null) {
            placePropertyPart(storageChest, spec.mopedParts().chest(), renderAnchor, driverSeat,
                    yaw, wheelieAngle, bodyRoll, chassisRotation);
            Vector3f interactionPoint = chassis(point(spec.mopedParts().chest().center()),
                    wheelieAngle, bodyRoll);
            storageInteraction.teleport(local(root, interactionPoint));
            storageInteraction.setRotation(yaw, 0.0F);
        }

        if (towBar != null) {
            /* AbstractLandVehicleRenderer renders the tow bar before applying the
             * axle/wheel translations and wheelie matrix. Keep its root-space
             * center and orientation fixed while the chassis wheelies. */
            Vector3f center = point(spec.towBarVisualCenter());
            place(towBar, renderAnchor, relativeToSeat(center, driverSeat), yaw, new Quaternionf(),
                    new Vector3f(1.0F), TOW_BAR_SOURCE_ROTATION);
        }

        for (WheelDisplay wheelDisplay : wheels) {
            LandVehicleSpec.Wheel wheel = wheelDisplay.properties;
            float spin = wheel.front() ? frontWheelRotation : rearWheelRotation;
            Vector3f center;
            Quaternionf rotation;
            Quaternionf sourceRotation;
            if (wheel.front() && spec.motorcycle() != null) {
                Vector3f unsteeredCenter = point(wheel.visualCenter());
                center = SourceTransforms.forkPoint(
                        unsteeredCenter, bodyOrigin, spec.motorcycle(), forkRotation);
                center = chassis(center, wheelieAngle, bodyRoll);
                rotation = new Quaternionf(chassisRotation).mul(forkRotation)
                        .rotateX(radians(-spin));
                sourceRotation = spec.motorcycle().frontWheelYaw180()
                        ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf();
            } else {
                float steeringAngle = wheel.front() ? renderSteeringAngle : 0.0F;
                float steerRadians = radians(steeringAngle);
                center = new Vector3f(
                        wheel.axleX() + wheel.halfWidthOffset() * (float) Math.cos(steerRadians),
                        wheel.centerY(),
                        wheel.axleZ() - wheel.halfWidthOffset() * (float) Math.sin(steerRadians)
                );
                center = chassis(center, wheelieAngle, bodyRoll);
                rotation = new Quaternionf(chassisRotation)
                        .rotateY(steerRadians)
                        .rotateX(radians(-spin));
                sourceRotation = wheel.side() > 0
                        ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf();
            }
            place(wheelDisplay.entity, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                    new Vector3f(wheel.scaleX(), wheel.scaleY(), wheel.scaleZ()), sourceRotation);
        }

        for (SeatCarrier carrier : seats) {
            Vector3f seatPoint = pigAnchor(chassis(
                    seatOffset(carrier.properties), wheelieAngle, bodyRoll));
            Location seatLocation = local(root, seatPoint);
            carrier.anchor.teleport(seatLocation, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            /* BL18: modul alege cine poarta yaw-ul (ancora si/sau scaunul). */
            boolean anchorYaw = "anchor".equals(effSeatYawMode()) || "both".equals(effSeatYawMode());
            carrier.anchor.setRotation(anchorYaw ? yaw + carrier.properties.yawOffset() : 0.0F, 0.0F);
            maintainSeat(carrier, yaw);
        }
    }

    private void placePropertyPart(PartDisplay partDisplay,
                                   Location renderAnchor, Vector3f driverSeat, float yaw,
                                   float wheelieAngle, float bodyRoll, Quaternionf chassisRotation) {
        LandVehicleSpec.Part part = partDisplay.properties;
        Vector3f center = chassis(point(part.center()), wheelieAngle, bodyRoll);
        Quaternionf rotation = new Quaternionf(chassisRotation);
        if (part.openable() != null) {
            float angle = radians(partDisplay.openAngle());
            switch (part.openable().axis()) {
                case X -> rotation.rotateX(angle);
                case Y -> rotation.rotateY(angle);
                case Z -> rotation.rotateZ(angle);
            }
        }
        applyPlanePartRotation(rotation, part.model());
        Quaternionf sourceRotation = new Quaternionf()
                .rotateX(radians(part.rotationX()))
                .rotateY(radians(part.rotationY()))
                .rotateZ(radians(part.rotationZ()));
        float visualScale = part.scale() * spec.modelScaleCorrection(part.model());
        place(partDisplay.entity, renderAnchor, relativeToSeat(center, driverSeat), yaw, rotation,
                new Vector3f(visualScale), sourceRotation);
    }

    /** Reproduces the actions in the original plane and helicopter complex-model files. */
    private void applyPlanePartRotation(Quaternionf rotation, String model) {
        switch (model) {
            case "sports_plane_propeller" -> rotation.rotateZ(radians(propellerRotation));
            case "sports_plane_left_aileron" -> rotation
                    .rotateY(radians(-5.0F))
                    .rotateX(radians(flapAngle * 0.5F))
                    .rotateY(radians(5.0F));
            case "sports_plane_right_aileron" -> rotation
                    .rotateY(radians(5.0F))
                    .rotateX(radians(flapAngle * -0.5F))
                    .rotateY(radians(-5.0F));
            case "sports_plane_elevator" -> rotation.rotateX(radians(elevatorAngle * 0.5F));
            case "sports_plane_joystick" -> rotation
                    .rotateX(radians(elevatorAngle * -0.25F))
                    .rotateZ(radians(flapAngle * -0.25F));
            case "compact_helicopter_blades", "sofacopter_blades" ->
                    rotation.rotateY(radians(helicopterBladeRotation));
            case "compact_helicopter_tail_rotor" -> rotation.rotateX(radians(helicopterBladeRotation));
            case "compact_helicopter_joystick" -> rotation
                    .rotateX(radians(helicopterJoystickForward * 10.0F))
                    .rotateZ(radians(helicopterJoystickStrafe * -10.0F));
            default -> {
            }
        }
    }

    public void setPlaneAnimations(float propellerRotation, float flapAngle, float elevatorAngle) {
        this.propellerRotation = propellerRotation;
        this.flapAngle = flapAngle;
        this.elevatorAngle = elevatorAngle;
    }

    public void setHelicopterAnimations(float bladeRotation, float joystickForward,
                                        float joystickStrafe) {
        this.helicopterBladeRotation = bladeRotation;
        this.helicopterJoystickForward = joystickForward;
        this.helicopterJoystickStrafe = joystickStrafe;
    }

    private void placePropertyPart(ItemDisplay display, LandVehicleSpec.Part part,
                                   Location renderAnchor, Vector3f driverSeat, float yaw,
                                   float wheelieAngle, float bodyRoll, Quaternionf chassisRotation) {
        if (display == null || part == null) {
            return;
        }
        Vector3f center = chassis(point(part.center()), wheelieAngle, bodyRoll);
        Quaternionf sourceRotation = new Quaternionf()
                .rotateX(radians(part.rotationX()))
                .rotateY(radians(part.rotationY()))
                .rotateZ(radians(part.rotationZ()));
        place(display, renderAnchor, relativeToSeat(center, driverSeat), yaw, chassisRotation,
                new Vector3f(part.scale()), sourceRotation);
    }

    /** BL34: tine jucatorul pe scaun fara sa fie pasager: il aseaza in fiecare
     *  tick la pozitia scaunului, cu yaw-ul vehiculului - deci se roteste cu
     *  masina, iar mouse-ul nu il mai poate intoarce. Coborare = Shift. */
    private void holdRider(SeatCarrier carrier, float seatYaw) {
        Player rider = Bukkit.getPlayer(carrier.rider);
        if (rider == null || !rider.isOnline() || rider.isDead()) {
            carrier.rider = null;
            return;
        }
        if (rider.isSneaking()) {
            carrier.rider = null;
            rider.sendActionBar(net.kyori.adventure.text.Component.text("Ai coborat din vehicul",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return;
        }
        Location target = carrier.anchor.getLocation().clone();
        float currentYaw = rider.getLocation().getYaw();
        float yawDelta = Math.abs(wrapDegrees(seatYaw - currentYaw));
        /* BL39: teleport doar cand chiar e nevoie (deriva de pozitie peste 5 cm
         * sau rotatie peste 1.5 grade). Teleportul in fiecare tick era cauza
         * senzatiei de "buguit": pachete multe si micro-sacadari. */
        /* BL44 (cerinta finala user: "corpul sa fie fixat pe scaun, sa nu isi
         * schimbe pozitia nici la viraje"): toleranta minima - corectam la
         * orice abatere de peste 2 cm sau 0.5 grade, deci corpul sta practic
         * sudat pe scaun. */
        /* BL52 (feedback user pe varianta D: "capul ramane cu masina dar tot
         * dardaie"): praguri mai blande - teleport doar la deriva peste 8 cm
         * sau 3 grade, deci fara un pachet in fiecare tick (asta dardaia). */
        boolean needsMove = rider.getLocation().distanceSquared(target) > 0.0064D;
        if (needsMove || yawDelta > 3.0F) {
            target.setYaw(seatYaw);
            target.setPitch(rider.getPitch());
            rider.teleport(target);
        }
        rider.setFallDistance(0.0F);
        /* BL52: poza de sezut, best effort (clientul poate sa o ignore pentru
         * propriul corp; nu strica nimic daca o ignora). */
        if (rider.getTicksLived() % 10 == 0) {
            applySitPose(rider);
        }
        rider.setVelocity(new org.bukkit.util.Vector());
    }

    /** BL52: poza de sezut pentru modul "tinut pe scaun" (fara calarie). */
    private static void applySitPose(Player player) {
        try {
            Class<?> poseClass = Class.forName("org.bukkit.entity.Pose");
            Object sitting = null;
            for (Object constant : poseClass.getEnumConstants()) {
                if ("SITTING".equals(((Enum<?>) constant).name())) {
                    sitting = constant;
                    break;
                }
            }
            if (sitting == null) {
                return;
            }
            player.getClass().getMethod("setPose", poseClass).invoke(player, sitting);
        } catch (Throwable ignored) {
            // clientul decide poza proprie; lipsa metodei nu e o eroare de runtime
        }
    }

    /** Elibereaza un jucator tinut pe scaun (modul fara calarie). */
    public void releaseRider(Player player) {
        for (SeatCarrier carrier : seats) {
            if (player.getUniqueId().equals(carrier.rider)) {
                carrier.rider = null;
            }
        }
    }

    /** Soferul tinut pe scaun (modul fara calarie), sau null. */
    public Player seatedDriver() {
        if (effSeatRide()) {
            return null;
        }
        for (SeatCarrier carrier : seats) {
            if (!carrier.properties.driver() || carrier.rider == null) {
                continue;
            }
            Player rider = Bukkit.getPlayer(carrier.rider);
            if (rider == null || !rider.isOnline() || rider.isDead() || rider.isSneaking()) {
                return null;
            }
            if (!rider.getWorld().equals(carrier.anchor.getWorld())
                    || rider.getLocation().distanceSquared(carrier.anchor.getLocation()) > 9.0D) {
                return null;
            }
            return rider;
        }
        return null;
    }

    private void maintainSeat(SeatCarrier carrier, float yaw) {
        float seatYaw = yaw + carrier.properties.yawOffset();
        Entity seat = carrier.mount;
        if (seat == null) {
            if (!effSeatRide() && carrier.rider != null) {
                holdRider(carrier, seatYaw);
            }
            return;
        }
        if (!seat.isValid()) {
            UUID riderId = carrier.rider;
            entities.remove(seat);
            carrier.mount = null;
            if (!removed && riderId != null) {
                Player rider = Bukkit.getPlayer(riderId);
                if (rider != null && rider.isOnline() && !rider.isDead()) {
                    Entity replacement = createSeatMount(carrier);
                    if (replacement != null && replacement.addPassenger(rider)) {
                        carrier.rider = riderId;
                        replacement.setRotation(seatYaw, 0.0F);
                        return;
                    }
                    discardSeat(carrier);
                }
            }
            carrier.rider = null;
            return;
        }

        /* BL46: tinem scaunul liber lipit de ancora scaunului (pozitie + yaw),
         * cu calareții pastrati pe el. */
        Location seatTarget = carrier.anchor.getLocation().clone();
        seatTarget.setYaw(wrapDegrees(seatYaw));
        seatTarget.setPitch(0.0F);
        boolean drift = !seat.getWorld().equals(seatTarget.getWorld())
                || seat.getLocation().distanceSquared(seatTarget) > 1.0E-4D
                || Math.abs(wrapDegrees(seat.getYaw() - seatYaw)) > 0.5F;
        /* BL61: daca scaunul e pasager al corpului masinii, pozitia si rotatia
         * ii sunt date de parinte - nu-l mai teleportam (asta tremura). */
        /* BL63 (cauza dovedita de user: "barca pleaca pe alte locuri si masina pe
         * altele"): corpul masinii este un ItemDisplay cu teleportDuration=1, deci
         * clientul il deseneaza cu UN TICK MAI TARZIU. Daca scaunul il mutam pe
         * pozitia de ACUM, apare cu un tick inaintea masinii => "pe alt loc".
         * Solutie: scaunul primeste pozitia din tickul precedent (aceeasi
         * intarziere ca desenul masinii). */
        if (variantGlueEveryTick && !seat.isInsideVehicle() && !(seat instanceof org.bukkit.entity.Boat)) {
            Location delayed = carrier.boatDelayReady
                    ? carrier.boatDelayLocation.clone() : seatTarget.clone();
            delayed.setYaw(carrier.boatDelayReady ? carrier.boatDelayYaw : seatTarget.getYaw());
            seat.teleport(delayed, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            carrier.boatDelayLocation = seatTarget.clone();
            carrier.boatDelayYaw = seatTarget.getYaw();
            carrier.boatDelayReady = true;
        } else if (drift && !seat.isInsideVehicle()) {
            seat.teleport(seatTarget, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
        Player rider = riderHost(carrier, seat).getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst().orElse(null);
        /* BL54 (feedback user pe E: "aproape, dar tot are trepidatii"):
         * trepidatia venea din faptul ca trimiteam rotirea din 2 in 2 ticki si
         * doar la abateri mari => camera se rotea in TREPTE. Acum corectam in
         * fiecare tick, cu toleranta mica, deci urmarirea e continua. */
        if (rider != null && effLookLock()) {
            if (variantKeepPosition) {
                /* BL57 (cerinta finala user, formulata clar): "jucatorul sa poata
                 * misca capul din mouse ca la barca, iar daca ia viraje capul sa
                 * ramana in aceeasi pozitie cu masina". Deci NU fortam camera pe
                 * yaw-ul masinii - adaugam la privire doar CAT a virat masina in
                 * acest tick. Mouse-ul ramane liber: te uiti unde vrei, iar la
                 * viraje privirea se roteste odata cu masina. */
                float delta = carrier.lastSeatYawValid
                        ? wrapDegrees(seatYaw - carrier.lastSeatYaw) : 0.0F;
                carrier.lastSeatYaw = seatYaw;
                carrier.lastSeatYawValid = true;
                if (Math.abs(delta) > 0.02F) {
                    forceLook(rider, rider.getLocation().getYaw() + delta, rider.getPitch(), true);
                }
            } else {
                /* Modul "blocat": camera e tinuta pe yaw-ul masinii, gradual
                 * (BL56) - nu mai sare, dar nici nu te mai lasa sa te uiti. */
                carrier.lastSeatYaw = seatYaw;
                carrier.lastSeatYawValid = true;
                float currentYaw = rider.getLocation().getYaw();
                float diff = wrapDegrees(seatYaw - currentYaw);
                if (Math.abs(diff) > 0.1F) {
                    float step = diff * 0.6F;
                    if (Math.abs(step) < 0.35F) {
                        step = Math.abs(diff) < 0.35F ? 0.0F : Math.signum(diff) * 0.35F;
                    }
                    if (Math.abs(step) > 0.0F) {
                        forceLook(rider, currentYaw + step, rider.getPitch(), false);
                    }
                }
            }
        } else {
            carrier.lastSeatYawValid = false;
        }
        if (rider == null) {
            carrier.rider = null;
            discardSeat(carrier);
            return;
        }
        carrier.rider = rider.getUniqueId();
        /* CICLUL BL17 (cerinta user: "corpul fix pe scaun, sa se ia dupa masina
         * cand fac cerculete"): scaunul este ROTIT de server la yaw-ul
         * vehiculului in fiecare tick, deci corpul calareului sta lipit de
         * masina si se roteste o data cu ea. Ancora rămâne nerotita, deci nu se
         * mai compune nicio rotatie (cauza veche a oscilatiei). */
        boolean mountYaw = "mount".equals(effSeatYawMode()) || "both".equals(effSeatYawMode());
        if (mountYaw && seat.getYaw() != seatYaw) {
            seat.setRotation(seatYaw, 0.0F);
        }
        /* BL36: barca are fizica ei si primeste input de la client (A/D o misca
         * pe scaun). O fortam rigid in fiecare tick: pozitia scaunului, yaw-ul
         * vehiculului si viteza ZERO - deci sta ca si cum ar fi sudata pe masina. */
        /* BL45: calul NU mai este fortat manual (teleport/viteza/rotire in plus
         * = tremurat). Este pasager al ancorei, iar ancora este teleportata si
         * rotita pe yaw-ul masinii in fiecare update, deci calul o urmeaza
         * natural, fara corectii care sa se bata cu clientul. */
        if (seat instanceof org.bukkit.entity.Boat boat && !variantAttachToBody) {
            Location seatLocation = carrier.anchor.getLocation();
            if (!carrier.boatDelayReady) {
                carrier.boatDelayLocation = seatLocation.clone();
                carrier.boatDelayYaw = seatYaw;
                carrier.boatDelayReady = true;
            }
            /* BL58 (cerinta user): "barca fixata de masina, sa nu se invarta nici
             * cand dai A/D; doar masina sa se miste, eu sa stau in barca normal".
             * Viteza e zero in fiecare tick, iar rotatia se impune mereu (nu doar
             * la abateri mari), deci barca nu are cum sa se rasuceasca singura. */
            boat.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            Location target = carrier.boatDelayLocation.clone();
            float targetYaw = carrier.boatDelayYaw;
            Location boatLocation = boat.getLocation();
            if (!boatLocation.getWorld().equals(target.getWorld())
                    || boatLocation.distanceSquared(target) > 1.0E-4D) {
                boat.teleport(target, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            if (Math.abs(wrapDegrees(boat.getYaw() - targetYaw)) > 0.01F || boat.getPitch() != 0.0F) {
                boat.setRotation(targetYaw, 0.0F);
            }
            carrier.boatDelayLocation = seatLocation.clone();
            carrier.boatDelayYaw = seatYaw;
        }
        /* BL21: camera ramane pe directia vehiculului (mouse-ul nu mai roteste
         * jucatorul). Pitch-ul rămâne liber. 999 = dezactivat. */
        // BL25: blocarea camerei din server este IMPOSIBILA cat timp jucatorul
        // este pasager - clientul vanilla deconecteaza cu
        // "Invalid move player packet received". Vezi /vehicle seatlook.
    }

    private void place(ItemDisplay display, Location renderAnchor, Vector3f translation, float yaw,
                       Quaternionf leftRotation, Vector3f scale, Quaternionf sourceRightRotation) {
        display.teleport(renderAnchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        display.setRotation(yaw, 0.0F);
        Quaternionf itemDisplayCompensation = new Quaternionf(sourceRightRotation).mul(ITEM_DISPLAY_YAW_CORRECTION);
        display.setTransformation(new Transformation(translation, leftRotation, scale, itemDisplayCompensation));
    }

    public void refreshBrightness(Location root) {
        if (valid()) {
            updateBrightness(root);
        }
    }

    private void updateBrightness(Location root) {
        Block sample = root.clone().add(0.0D, spec.entityHeight() + 0.01D, 0.0D).getBlock();
        Display.Brightness brightness = new Display.Brightness(sample.getLightFromBlocks(), sample.getLightFromSky());
        for (Entity entity : entities) {
            if (entity instanceof ItemDisplay display && display.getType() != null
                    && !brightness.equals(display.getBrightness())) {
                display.setBrightness(brightness);
            }
        }
    }

    private Vector3f driverSeatOffset() {
        LandVehicleSpec.Seat seat = spec.seats().stream().filter(LandVehicleSpec.Seat::driver)
                .findFirst().orElseThrow();
        return seatOffset(seat);
    }

    private static Vector3f seatOffset(LandVehicleSpec.Seat seat) {
        return point(seat.sourceOffset()).add(0.0F, LandVehicleSpec.RIDER_HEIGHT_CORRECTION, 0.0F);
    }

    private static Vector3f pigAnchor(Vector3f seatPoint) {
        return new Vector3f(seatPoint).sub(0.0F, SEAT_PASSENGER_OFFSET, 0.0F);
    }

    private Vector3f chassis(Vector3f sourcePoint, float wheelieAngle, float bodyRoll) {
        if (usesAircraftBodyRotation()) {
            return SourceTransforms.aircraftPoint(sourcePoint, wheelieAngle, bodyRoll);
        }
        return SourceTransforms.chassisPoint(
                sourcePoint, point(spec.wheeliePivot()), wheelieAngle, bodyRoll);
    }

    private boolean usesAircraftBodyRotation() {
        return spec.motionType() == LandVehicleSpec.MotionType.AIR
                || spec.motionType() == LandVehicleSpec.MotionType.HELICOPTER;
    }

    private static Vector3f relativeToSeat(Vector3f point, Vector3f seat) {
        return new Vector3f(point).sub(seat);
    }

    private static Vector3f point(LandVehicleSpec.Point point) {
        return new Vector3f(point.x(), point.y(), point.z());
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static Location local(Location root, Vector3f point) {
        double radians = Math.toRadians(root.getYaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return root.clone().add(point.x * cos - point.z * sin, point.y, point.x * sin + point.z * cos);
    }

    public void setSeatGauge(float fraction) {
        seatGauge = Float.isFinite(fraction) ? Math.max(0.0F, Math.min(1.0F, fraction)) : 1.0F;
        for (SeatCarrier carrier : seats) {
            if (carrier.mount instanceof org.bukkit.entity.LivingEntity living && living.isValid()) {
                applySeatGauge(living);
            }
        }
    }

    private void applySeatGauge(org.bukkit.entity.LivingEntity seat) {
        double health = FuelGauge.mountHealth(seatGauge);
        if (Math.abs(seat.getHealth() - health) > 0.01D) {
            seat.setHealth(health);
        }
    }

    public void tickSeats(float yaw) {
        if (removed) {
            return;
        }
        for (SeatCarrier carrier : seats) {
            maintainSeat(carrier, yaw);
        }
    }

    public boolean mount(Player player) {
        if (removed || !valid()) {
            return false;
        }

        /* VehicleEntity/SeatTracker selects the closest available source seat,
         * rather than the first seat in declaration order. This is particularly
         * important for the Off Roader: approaching its rear selects one of the
         * two hanging rear positions without requiring the front seats to fill. */
        SeatCarrier closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        double playerHalfHeight = player.getBoundingBox().getHeight() * 0.5D;
        Location playerLocation = player.getLocation();
        for (SeatCarrier carrier : seats) {
            Entity seat = carrier.mount;
            if (!effSeatRide() && carrier.rider != null) {
                continue;
            }
            if (seat != null && seat.isValid()
                    && seat.getPassengers().stream().anyMatch(Player.class::isInstance)) {
                continue;
            }
            Location seatLocation = carrier.anchor.getLocation();
            if (!seatLocation.getWorld().equals(playerLocation.getWorld())) {
                continue;
            }
            /* SeatTracker compares the player's feet against seatY minus half
             * the player's bounding-box height. Preserve that source equation. */
            seatLocation.subtract(0.0D, playerHalfHeight, 0.0D);
            double distance = seatLocation.distanceSquared(playerLocation);
            if (distance < closestDistance) {
                closest = carrier;
                closestDistance = distance;
            }
        }
        if (closest == null) {
            return false;
        }

        if (closest.mount != null) {
            discardSeat(closest);
        }
        if (!effSeatRide()) {
            closest.rider = player.getUniqueId();
            return true;
        }
        Entity seat = createSeatMount(closest);
        if (seat != null && variantAttachToBody && !seat.isInsideVehicle()) {
            /* BL61: "lipim" scaunul de masina - devine pasager al corpului, deci
             * merge cu ea fara teleportari facute de noi. */
            body.addPassenger(seat);
        }
        Entity host = seat == null ? null : riderHost(closest, seat);
        if (host != null && host.addPassenger(player)) {
            closest.rider = player.getUniqueId();
            return true;
        }
        discardSeat(closest);
        return false;
    }

    /* BL48: ultima cale server-side pentru corp/camera pe directia masinii:
     * pachetul de pozitionare ABSOLUT (pozitia curenta + yaw-ul vehiculului).
     * Varianta RELATIVA (BL24) a fost respinsa de client ("Invalid move player
     * packet received"); absoluta e alta cale. Daca clientul deconecteaza,
     * feature-ul se dezactiveaza SINGUR (vezi noteQuit). */
    /* BL50 (cerinta user: "il lasam asa"): opresc experimentalul prin care
     * fortam capul/camera din server (risc de deconectare). Implicit OFF;
     * se poate activa manual cu /vehicle seatlock on daca vreodata e nevoie. */
    private static volatile boolean lookLock = false;
    private static volatile boolean lookLockAutoOff = false;
    private static volatile long lookLockLastPacket;
    private static java.lang.reflect.Constructor<?> lookPacketCtor;
    private static java.lang.reflect.Constructor<?> lookMoveCtor;
    private static java.lang.reflect.Constructor<?> lookVecCtor;
    private static Object lookVecZero;
    private static int lookYawIndex = 2;
    private static java.lang.reflect.Field lookConnectionField;
    private static java.lang.reflect.Method lookSendMethod;
    private static final java.util.concurrent.atomic.AtomicInteger LOOK_PACKET_ID =
            new java.util.concurrent.atomic.AtomicInteger(1000);
    /* BL55 (feedback user: "E e aproape dar tot are trepidatii"): pachetul
     * absolut suprascrie si POZITIA, iar serverul e cu un tick in urma - deci
     * corpul e tras inapoi de ~20 ori pe secunda. Varianta noua trimite pozitia
     * RELATIV cu delta 0 (clientul isi pastreaza pozitia) si rotatia absoluta. */
    private static java.util.Set<Object> lookRelativePosition;

    public static boolean lookLock() {
        return lookLock && !lookLockAutoOff;
    }

    public static void setLookLock(boolean enabled) {
        lookLock = enabled;
        lookLockAutoOff = false;
    }

    public static boolean lookLockAutoOff() {
        return lookLockAutoOff;
    }

    /** Apelat la deconectare: daca pachetul nostru tocmai a fost trimis, il oprim. */
    public static void noteQuit() {
        if (System.currentTimeMillis() - lookLockLastPacket < 10_000L && lookLock) {
            lookLock = false;
            lookLockAutoOff = true;
            Bukkit.getLogger().warning("[SEATLOCK] Clientul a deconectat imediat dupa pachetul de "
                    + "rotatie - opresc automat blocarea camerei/corpului.");
        }
    }

    private static synchronized void initLookPacket() {
        try {
            Class<?> packetClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket");
            for (java.lang.reflect.Constructor<?> candidate : packetClass.getConstructors()) {
                Class<?>[] types = candidate.getParameterTypes();
                if (types.length != 3 || types[0] != int.class || types[2] != java.util.Set.class) {
                    continue;
                }
                Class<?> changeClass = types[1];
                for (java.lang.reflect.Constructor<?> moveCtor : changeClass.getConstructors()) {
                    Class<?>[] moveTypes = moveCtor.getParameterTypes();
                    if (moveTypes.length == 4 && moveTypes[2] == float.class && moveTypes[3] == float.class) {
                        lookMoveCtor = moveCtor;
                        Class<?> vecClass = moveTypes[0];
                        lookVecZero = vecClass.getField("ZERO").get(null);
                        lookVecCtor = vecClass.getConstructor(double.class, double.class, double.class);
                        lookYawIndex = 2;
                        if (changeClass.isRecord()) {
                            java.lang.reflect.RecordComponent[] parts = changeClass.getRecordComponents();
                            for (int index = 0; index < parts.length; index++) {
                                String name = parts[index].getName().toLowerCase(java.util.Locale.ROOT);
                                if (name.contains("yrot") || name.contains("y_rot")) {
                                    lookYawIndex = index;
                                }
                            }
                        }
                        break;
                    }
                }
                if (lookMoveCtor != null) {
                    lookPacketCtor = candidate;
                    break;
                }
            }
            Class<?> relativeClass = Class.forName("net.minecraft.world.entity.Relative");
            java.util.Set<Object> relativePosition = new java.util.HashSet<>();
            for (Object constant : relativeClass.getEnumConstants()) {
                String name = ((Enum<?>) constant).name();
                if ("X".equals(name) || "Y".equals(name) || "Z".equals(name)) {
                    relativePosition.add(constant);
                }
            }
            if (!relativePosition.isEmpty()) {
                lookRelativePosition = java.util.Collections.unmodifiableSet(relativePosition);
            }
            Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
            lookConnectionField = serverPlayer.getField("connection");
            lookSendMethod = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl")
                    .getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
        } catch (Throwable error) {
            lookLockAutoOff = true;
            Bukkit.getLogger().warning("[SEATLOCK] pachetul de rotatie nu este disponibil: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    /** Trimite pozitia curenta + yaw-ul dorit (absolut), fara sa miste jucatorul. */
    private static void forceLook(Player player, float yaw, float pitch) {
        forceLook(player, yaw, pitch, false);
    }

    /** BL55: cu keepPosition=true pozitia se trimite RELATIV (delta 0), deci
     *  clientul isi pastreaza pozitia si primeste doar yaw-ul nou. */
    private static void forceLook(Player player, float yaw, float pitch, boolean keepPosition) {
        if (lookPacketCtor == null) {
            initLookPacket();
            if (lookPacketCtor == null) {
                return;
            }
        }
        try {
            boolean relative = keepPosition && lookRelativePosition != null;
            Object vec = relative
                    ? lookVecCtor.newInstance(0.0D, 0.0D, 0.0D)
                    : lookVecCtor.newInstance(player.getX(), player.getY(), player.getZ());
            Object[] change = new Object[4];
            change[0] = vec;
            change[1] = lookVecZero;
            change[lookYawIndex] = yaw;
            change[lookYawIndex == 2 ? 3 : 2] = pitch;
            Object moveRotation = lookMoveCtor.newInstance(change);
            Object packet = lookPacketCtor.newInstance(
                    LOOK_PACKET_ID.getAndIncrement(), moveRotation,
                    relative ? lookRelativePosition : java.util.Set.of());
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = lookConnectionField.get(handle);
            lookSendMethod.invoke(connection, packet);
            /* BL53: oglindim yaw-ul si in starea serverului, ca diagnosticul
             * (SEATDBG riderYaw) sa arate daca pachetele chiar pleaca. */
            player.setRotation(yaw, pitch);
            lookLockLastPacket = System.currentTimeMillis();
        } catch (Throwable error) {
            lookLockAutoOff = true;
            Bukkit.getLogger().warning("[SEATLOCK] nu pot trimite rotatia: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    /** BL47: diagnostic - yaw-ul ancorei, al scaunului si al calaretului,
     *  ca sa stim daca serverul roteste corect (nu ghicim). */
    public String debugSeats(float vehicleYaw) {
        /* BL51: fiecare vehicul de test isi spune varianta in log, ca sa stim
         * exact care litera (A/B/C/D) a tinut capul dupa masina. */
        StringBuilder text = new StringBuilder("vehicleYaw=").append(String.format("%.1f", vehicleYaw))
                .append(" variant=").append(variantName == null ? "global" : variantName);
        for (SeatCarrier carrier : seats) {
            text.append(" | anchor=").append(String.format("%.1f", carrier.anchor.getYaw()));
            if (carrier.mount != null && carrier.mount.isValid()) {
                text.append(" mount=").append(carrier.mount.getType())
                        .append(" yaw=").append(String.format("%.1f", carrier.mount.getYaw()))
                        .append(" pitch=").append(String.format("%.1f", carrier.mount.getPitch()))
                        .append(" pass=").append(carrier.mount.getPassengers().size());
            } else {
                text.append(" mount=none");
            }
            if (carrier.rider != null) {
                Player rider = Bukkit.getPlayer(carrier.rider);
                if (rider != null) {
                    text.append(" riderYaw=").append(String.format("%.1f", rider.getLocation().getYaw()))
                            .append(" riding=").append(rider.isInsideVehicle());
                }
            }
        }
        return text.toString();
    }

    public Entity driverSeat() {
        SeatCarrier driver = seats.stream().filter(seat -> seat.properties.driver())
                .findFirst().orElseThrow();
        return driver.mount != null && driver.mount.isValid() ? driver.mount : driver.anchor;
    }

    public List<Entity> seatCarriers() {
        return seats.stream()
                .map(SeatCarrier::seat)
                .filter(java.util.Objects::nonNull)
                .filter(Entity::isValid)
                .map(Entity.class::cast)
                .toList();
    }

    public List<Entity> entities() {
        return Collections.unmodifiableList(entities);
    }

    /** Advances source OpenableAction animations. Closing sounds fire when the part reaches zero. */
    public boolean tickOpenables(Location root) {
        boolean changed = false;
        for (PartDisplay part : chassisParts) {
            if (part.tick()) {
                changed = true;
                if (!part.open && part.animationTick == 0) {
                    playCustomSound(root, part.properties.openable().closeSound());
                }
            }
        }
        return changed;
    }

    public boolean toggleOpenable(String id, Location root) {
        for (PartDisplay part : chassisParts) {
            LandVehicleSpec.Openable openable = part.properties.openable();
            if (openable == null || !openable.id().equals(id)) {
                continue;
            }
            part.open = !part.open;
            if (part.open) {
                playCustomSound(root, openable.openSound());
            }
            return true;
        }
        return false;
    }

    public float openAngle(String id) {
        for (PartDisplay part : chassisParts) {
            LandVehicleSpec.Openable openable = part.properties.openable();
            if (openable != null && openable.id().equals(id)) {
                return part.openAngle();
            }
        }
        return 0.0F;
    }

    public Map<String, Boolean> openStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (PartDisplay part : chassisParts) {
            if (part.properties.openable() != null) {
                states.put(part.properties.openable().id(), part.open);
            }
        }
        return states;
    }

    public void restoreOpenStates(Map<String, Boolean> states) {
        for (PartDisplay part : chassisParts) {
            LandVehicleSpec.Openable openable = part.properties.openable();
            if (openable == null) {
                continue;
            }
            part.open = states != null && Boolean.TRUE.equals(states.get(openable.id()));
            part.animationTick = part.open ? openable.animationLength() : 0;
        }
    }

    private static void playCustomSound(Location root, String sound) {
        if (root.getWorld() != null && sound != null && !sound.isBlank()) {
            float pitch = 0.8F + 0.2F * ThreadLocalRandom.current().nextFloat();
            root.getWorld().playSound(root, sound, SoundCategory.NEUTRAL, 1.0F, pitch);
        }
    }

    public boolean isStorageInteraction(Entity entity) {
        return storageInteraction != null && storageInteraction.getUniqueId().equals(entity.getUniqueId());
    }

    public void setStorageChestAttached(boolean attached) {
        if (storageChest != null && storageChest.isValid()) {
            storageChest.setVisibleByDefault(attached);
        }
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public boolean valid() {
        return interaction.isValid() && body.isValid()
                && (engine == null || engine.isValid())
                && (steering == null || steering.isValid())
                && (towBar == null || towBar.isValid())
                && chassisParts.stream().allMatch(part -> part.entity.isValid())
                && forkParts.stream().allMatch(part -> part.entity.isValid())
                && (storageChest == null || storageChest.isValid())
                && (storageInteraction == null || storageInteraction.isValid())
                && wheels.stream().allMatch(wheel -> wheel.entity.isValid())
                && seats.stream().allMatch(seat -> seat.anchor.isValid());
    }

    public void remove() {
        removed = true;
        /* Detach riders synchronously while the living carrier is still valid.
         * Restoring an ordinary bounding box also keeps the fallback dismount
         * location independent of the carrier's minimum render scale. */
        for (SeatCarrier carrier : seats) {
            Entity seat = carrier.mount;
            if (seat == null || !seat.isValid() || seat.getPassengers().isEmpty()) {
                continue;
            }
            if (seat instanceof org.bukkit.entity.LivingEntity living) {
                AttributeInstance scale = living.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(1.0D);
                }
            }
            seat.eject();
            if (!seat.getPassengers().isEmpty()) {
                /* Failing open is safer than deleting a still-ridden carrier. */
                entities.remove(seat);
                seat.setInvisible(false);
                seat.setInvulnerable(false);
                seat.setGravity(true);
            }
        }
        for (Entity entity : new ArrayList<>(entities)) {
            entity.remove();
        }
        entities.clear();
        for (SeatCarrier carrier : seats) {
            carrier.mount = null;
            carrier.rider = null;
        }
    }

    private record WheelDisplay(ItemDisplay entity, LandVehicleSpec.Wheel properties) {
    }

    private static final class PartDisplay {
        private final ItemDisplay entity;
        private final LandVehicleSpec.Part properties;
        private boolean open;
        private int animationTick;

        private PartDisplay(ItemDisplay entity, LandVehicleSpec.Part properties) {
            this.entity = entity;
            this.properties = properties;
        }

        private boolean tick() {
            LandVehicleSpec.Openable openable = properties.openable();
            if (openable == null) {
                return false;
            }
            int previous = animationTick;
            if (open && animationTick < openable.animationLength()) {
                animationTick++;
            } else if (!open && animationTick > 0) {
                animationTick--;
            }
            return previous != animationTick;
        }

        private float openAngle() {
            LandVehicleSpec.Openable openable = properties.openable();
            if (openable == null || animationTick == 0) {
                return 0.0F;
            }
            double progress = animationTick / (double) openable.animationLength();
            return (float) (openable.angle() * easeOutBack(progress));
        }
    }

    static double easeOutBack(double progress) {
        double c1 = 1.70158D;
        double c3 = c1 + 1.0D;
        return 1.0D + c3 * Math.pow(progress - 1.0D, 3.0D)
                + c1 * Math.pow(progress - 1.0D, 2.0D);
    }

    /* ===== Sistem mecanic: schimbare model roti + vopsea caroserie ============= */

    /** Cand slotul de motor e gol, piesa NU trebuie sa apara pe model (brief
     *  2026-10-24): stack gol = ItemDisplay complet invizibil. Re-montarea se face
     *  prin setEngineModel(...), care inlocuieste stack-ul - nu e nevoie de "show". */
    public void hideEngine() {
        if (!removed && engine != null && engine.isValid()) {
            engine.setItemStack(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
        }
    }

    /** La fel ca motorul: rotile scoase din slot NU mai au voie sa apara pe model
     *  (nici in lume, nici in GUI) pana la re-montare. Inversul = setWheelModel. */
    public void hideWheels() {
        if (removed) {
            return;
        }
        org.bukkit.inventory.ItemStack empty =
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR);
        for (WheelDisplay wheel : wheels) {
            if (wheel.entity != null && wheel.entity.isValid()) {
                wheel.entity.setItemStack(empty);
            }
        }
    }

    /** Schimba si PART-ul vizual al motorului la upgrade (ex. iron_small_engine ->
     *  diamond_small_engine), exact ca in mod, la schimbarea tier-ului. */
    public void setEngineModel(String modelName) {
        if (!removed && engine != null && engine.isValid()) {
            engine.setItemStack(model(modelName));
        }
    }

    /** Leaps rotile pe noul model (STANDARD/SPORTS/OFF_ROAD - roti schimbate la mecanic). */
    public void setWheelModel(String modelName) {
        if (removed) {
            return;
        }
        ItemStack stack = model(modelName);
        for (WheelDisplay wheel : wheels) {
            if (wheel.entity != null && wheel.entity.isValid()) {
                wheel.entity.setItemStack(stack);
            }
        }
    }

    /** Vopsire cu spray can: caroseria trece pe un stack vopsibil (dyed_color) purtand
     *  acelasi model; tint-ul "dye" din definitia pack-ului recoloreaza fetele marcate.
     *  rgb null = revine la modelul nevopsit (paper, default alb). */
    public void applyColor(Integer rgb) {
        if (removed) {
            return;
        }
        if (body != null && body.isValid()) {
            body.setItemStack(coloredStack(spec.bodyModel(), rgb));
        }
        // Modul inroseste TOATE partile caroseriei (capota/usile/acoperisul etc.), nu
        // doar corpul principal: replicam acelasi comportament ca sa nu ramana masina
        // vopsita "pe jumatate".
        for (PartDisplay part : chassisParts) {
            if (part.entity != null && part.entity.isValid()) {
                part.entity.setItemStack(coloredStack(part.properties.model(), rgb));
            }
        }
        for (PartDisplay part : forkParts) {
            if (part.entity != null && part.entity.isValid()) {
                part.entity.setItemStack(coloredStack(part.properties.model(), rgb));
            }
        }
    }

    /** Stack vopsit (dyed_color) pentru un model de caroserie; stack normal cand rgb e
     *  null (nevopsit). Pack-ul are pentru modelele de caroserie definitie cu tint
     *  "dye" implicit alb, deci pe stack-uri nevopsite nu se schimba NIMIC vizual. */
    private static ItemStack coloredStack(String modelName, Integer rgb) {
        if (rgb == null) {
            return model(modelName);
        }
        ItemStack stack = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta armor) {
            armor.setItemModel(new NamespacedKey("vehicle", modelName));
            armor.setColor(org.bukkit.Color.fromRGB(rgb));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class SeatCarrier {
        private final ItemDisplay anchor;
        private final LandVehicleSpec.Seat properties;
        private Entity mount;
        private UUID rider;
        /** BL35: suportul invizibil care ocupa PRIMUL loc de pasager al barcii,
         *  ca barca sa nu fie condusa de client; jucatorul urca pe al doilea
         *  loc, deci are vizualul si pose-ul normal de barca. */
        private Entity support;
        /** BL37: intarziere de 1 tick pentru barca (display-urile vehiculului au
         *  interpolationDuration=1, deci masina se deseneaza cu un tick mai
         *  tarziu; barca teleportata instant pare sa "o ia inainte"). */
        private Location boatDelayLocation;
        private float boatDelayYaw;
        private boolean boatDelayReady;
        /** BL57: ultimul yaw al scaunului, ca sa stim cu CAT a virat masina
         *  (rotatia se adauga la privire, nu o inlocuieste). */
        private float lastSeatYaw;
        private boolean lastSeatYawValid;

        private SeatCarrier(ItemDisplay anchor, LandVehicleSpec.Seat properties) {
            this.anchor = anchor;
            this.properties = properties;
        }

        private Entity seat() {
            return mount;
        }
    }
}
