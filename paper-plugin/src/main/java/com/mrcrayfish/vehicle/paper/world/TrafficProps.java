package com.mrcrayfish.vehicle.paper.world;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * CICLUL AH — conul de trafic si placa de boost ca BLOCURI REALE (persista in
 * lume zero-plugin-storage, se sparg normal, particule de spargere din pack).
 *
 * Gazda = minecraft:tripwire (firul de sarma), ales pentru ca e singurul bloc
 * vanilla PLASABIL fara NICIO forma de coliziune: jucatorul trece prin ele si
 * masina trece prin ele - exact cum a cerut userul pentru placa de boost
 * („sa poti trece prin ea, gen si masina si tu"; un slab ar fi dat hop la masina).
 *
 * Blocstate-urile hijack-uite sunt ORFANE in vanilla: disarmed=true nu apare
 * niciodata in firul pus manual de jucatori (el ramane disarmed=false; disarmed
 * apare doar dupa forfecarea unui hook, si atunci firul sta de obicei pe o armatura).
 *  - niciun „side" activ            => CON DE TRAFIC
 *  - exact o latura activa          => PLACA DE BOOST, sageata aratand acea directie
 *
 * Realism:
 *  - masina care loveste CONUL il rastoarna = se sparge instant si si-arunca
 *    singur itemul pe asfalt (conul real se deformeaza sub roti, nu opreste masina);
 *  - PLACA impinge masina in directia sagetii (acceleratie + cap de viteza din
 *    config, cooldown 40 tick-uri ca sa nu zboare la infinit pe o rampa lunga);
 *  - pistoanele nu le pot misca (lipite), apa nu le clateste, exploziile le
 *    distrug fara drop (consumabile fragile).
 */
public final class TrafficProps {
    public enum Kind { CONE, BOOST_PAD, FLUID_PIPE }

    private static final NamespacedKey KIND = new NamespacedKey("vehicle", "prop_kind");

    private TrafficProps() {
    }

    /* ================================ itemele ================================ */

    public static ItemStack item(Kind kind, int amount) {
        // Ciclul AI: regresia cunoscuta (commit 7845b33/6810905): PAPER + item_model
        // nu mai randeaza => icon mov/negru. Baza dovedita live este
        // LEATHER_HORSE_ARMOR + alb opac explicit (exact modelul folosit de
        // PropManager la entitatile care userul confirma ca „arata ok").
        ItemStack stack = new ItemStack(Material.LEATHER_HORSE_ARMOR,
                Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta armor) {
            armor.setColor(org.bukkit.Color.fromRGB(0xF9FFFE));
        }
        meta.setItemModel(new NamespacedKey("vehicle",
                switch (kind) {
                    case CONE -> "traffic_cone";
                    case BOOST_PAD -> "boost_pad";
                    case FLUID_PIPE -> "fluid_pipe";
                }));
        meta.getPersistentDataContainer().set(KIND, PersistentDataType.STRING, kind.name());
        meta.displayName(Component.text(switch (kind) {
                    case CONE -> "Con de Trafic";
                    case BOOST_PAD -> "Placa de Boost";
                    case FLUID_PIPE -> "Teava de Fluid";
                }, kind == Kind.CONE ? NamedTextColor.GOLD
                        : kind == Kind.BOOST_PAD ? NamedTextColor.AQUA : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(switch (kind) {
                            case CONE -> "Click-dreapta pe asfalt - conul real, masina il rastoarna";
                            case BOOST_PAD -> "Click-dreapta pe asfalt - sageata arata in directia in care privesti";
                            case FLUID_PIPE -> "Click-dreapta langa mixer/pompa - teava reala, pur decorativ";
                        }, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    public static Kind kindOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(KIND, PersistentDataType.STRING);
        try {
            return raw == null ? null : Kind.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /* ================================ blocurile ================================ */

    /** CICLUL AM - gazda FINALA a conului: minecraft:lectern[has_book=false, powered=true].
     *  Motivatia iteratiei: userul vrea con SOLID (ca in mod: Block.box(2,0,2,14,18,14)),
     *  dar gazda solida ANTERIOARA (petrified_oak_slab[type=double]) e OPACA full-cube
     *  -> oculeaza fata blocului de dedesubt (vizuala "gaura in jos" raportata live).
     *  Lecternul e SOLID + NEOPAC (nu culeaza fetele vecine) => zero gauri.
     *  Combinația has_book=FALSE + powered=TRUE e ORFANA ABSOLUTA in vanilla
     *  (un pupitru nu poate fi "powered" fara carte), deci nicio carte legitima nu isi
     *  schimba aspectul. powered=true emite semnal redstone spre blocul din dedesubt
     *  (irelevant pe drumuri, acceptat si documentat). */
    public static BlockData coneData() {
        org.bukkit.block.data.type.Lectern data = (org.bukkit.block.data.type.Lectern)
                org.bukkit.Bukkit.createBlockData(Material.LECTERN);
        data.setFacing(BlockFace.NORTH);
        data.setHasBook(false);
        data.setPowered(true);
        return data;
    }

    /** Baza tripwire disarmed+zero-fete comună pentru placă/teavă (AH..AK cicluri;
     *  conul a plecat pe slab solid, dar placa/cat teava cer coliziune ZERO). */
    private static Tripwire wireBase() {
        Tripwire data = (Tripwire) org.bukkit.Bukkit.createBlockData(Material.TRIPWIRE);
        data.setAttached(false);
        data.setDisarmed(true);
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            data.setFace(face, false);
        }
        return data;
    }

    /** Placa cu sageata aratand spre `facing` (directia in care priveste jucatorul
     *  la plasare). Codam directia IN latura unica activa a firului. */
    public static BlockData padData(BlockFace facing) {
        Tripwire data = wireBase();
        data.setFace(facing, true);
        return data;
    }

    /** CICLUL AJ - teava de fluid: dreapta, orientata N-S sau E-V dupa privire.
     *  State-hijack: attached=TRUE + disarmed=true + doua laturi OPUSE activate
     *  (combinatie complet orfana: attached nu ajunge niciodata true fara carlige,
     *  iar disarmed=true nu apare natural in firul pus de mana). */
    public static BlockData pipeData(BlockFace facing) {
        Tripwire data = wireBase();
        data.setAttached(true);
        if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
            data.setFace(BlockFace.EAST, true);
            data.setFace(BlockFace.WEST, true);
        } else {
            data.setFace(BlockFace.NORTH, true);
            data.setFace(BlockFace.SOUTH, true);
        }
        return data;
    }

    /** Identifica blocurile noastre; orice alta combinatie de tripwire = vanilla. */
    public static Kind kindOf(Block block) {
        if (block == null) {
            return null;
        }
        // Ciclul AM: conul SOLID = lectern[has_book=false, powered=true] (NEOPAC =>
        // dispar gaurile de sub el). Conversii lazy in-place pentru gazdele legacy:
        // slab[double] (AL) si fir-disarmed (AH..AK) devin lectern automat la atingere.
        if (block.getType() == Material.LECTERN) {
            if (block.getBlockData() instanceof org.bukkit.block.data.type.Lectern lectern
                    && !lectern.hasBook() && lectern.isPowered()) {
                return Kind.CONE;
            }
            return null;
        }
        if (block.getType() == Material.PETRIFIED_OAK_SLAB) {
            if (block.getBlockData() instanceof org.bukkit.block.data.type.Slab slab
                    && slab.getType() == org.bukkit.block.data.type.Slab.Type.DOUBLE) {
                block.setBlockData(coneData(), false);
                return Kind.CONE;
            }
            return null;
        }
        if (block.getType() != Material.TRIPWIRE
                || !(block.getBlockData() instanceof Tripwire data)
                || !data.isDisarmed()) {
            return null;
        }
        int faces = 0;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            if (data.hasFace(face)) {
                faces++;
            }
        }
        if (faces == 0 && !data.isAttached()) {
            // Ciclul AL: conversie lazy in-place a conurilor vechi (tripwire, AH..AK)
            // pe gazda noua solida - dispare si zgomotul "two-systems".
            block.setBlockData(coneData(), false);
            return Kind.CONE;
        }
        if (faces == 1 && !data.isAttached()) {
            return Kind.BOOST_PAD;
        }
        // Ciclul AJ: attached=true + disarmed=true + doua laturi opuse = teava (oricum
        // combinatie orfana pentru firul vanilla pus de mana, care e mereu disarmed=false).
        if (faces == 2 && data.isAttached()
                && ((data.hasFace(BlockFace.NORTH) && data.hasFace(BlockFace.SOUTH))
                        || (data.hasFace(BlockFace.EAST) && data.hasFace(BlockFace.WEST)))) {
            return Kind.FLUID_PIPE;
        }
        return null;
    }

    /** Directia sagetii de pe placa (latura unica activa); null daca blocul nu e placa. */
    public static BlockFace padFacing(Block block) {
        if (kindOf(block) != Kind.BOOST_PAD) {
            return null;
        }
        Tripwire data = (Tripwire) block.getBlockData();
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            if (data.hasFace(face)) {
                return face;
            }
        }
        return null;
    }

    public static Vector facingVector(BlockFace facing) {
        return switch (facing) {
            case NORTH -> new Vector(0.0D, 0.0D, -1.0D);
            case SOUTH -> new Vector(0.0D, 0.0D, 1.0D);
            case EAST -> new Vector(1.0D, 0.0D, 0.0D);
            case WEST -> new Vector(-1.0D, 0.0D, 0.0D);
            default -> new Vector();
        };
    }

    /** CICLUL AJ - migratie unica la pornire: prop-urile vechi (entitati salvate
     *  in sectiunea "props" a MechanicStore) devin blocuri reale pe celulele lor.
     *  PropManager e sters complet; sectiunea dispare la primul saveAll. */
    public static int migrateLegacyProps(
            com.mrcrayfish.vehicle.paper.persistence.MechanicStore store) {
        // Ciclul AJ.3: daca din orice motiv au supravietuit entitati-fantoma
        // mcv_prop (prop-urile vechi erau item-display-uri), le stergem acum -
        // PropManager (care le curata la boot) a fost sters complet in AJ.
        int ghosts = purgeLegacyPropEntities();
        if (ghosts > 0) {
            org.bukkit.Bukkit.getLogger().info("[Props] AJ.3: " + ghosts
                    + " entitati-fantoma mcv_prop eliminate din lume.");
        }
        int migrated = 0;
        for (com.mrcrayfish.vehicle.paper.persistence.MechanicStore.StoredStation stored
                : store.loadStations("props")) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(stored.worldId());
            if (world == null) {
                continue;
            }
            Block cell = world.getBlockAt((int) Math.floor(stored.x()),
                    (int) Math.floor(stored.y()), (int) Math.floor(stored.z()));
            if (cell.getType() != Material.AIR && cell.getType() != Material.TRIPWIRE) {
                continue;
            }
            BlockFace facing = yawToFacing((float) stored.yaw());
            BlockData data = switch (stored.kind()) {
                case "traffic_cone" -> coneData();
                case "boost_pad" -> padData(facing);
                case "fluid_pipe" -> pipeData(facing);
                default -> null;
            };
            if (data != null) {
                cell.setBlockData(data, false);
                migrated++;
            }
        }
        return migrated;
    }

    /** AJ.3: sterge ORICE entitate etichetata mcv_prop / mcv_<uuid> din lumile
     *  incarcate (item-display-urile vechi de prop). Idempotent. */
    public static int purgeLegacyPropEntities() {
        int removed = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("mcv_prop")) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** yaw-ul jucatorului -> cardinalul spre care priveste (conventia Bukkit:
     *  yaw 0 = sud, 90 = vest, 180 = nord, 270 = est). */
    public static BlockFace yawToFacing(float yaw) {
        int index = (int) Math.floor((((yaw % 360.0F) + 360.0F) % 360.0F) / 90.0D + 0.5D) % 4;
        return new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST}[index];
    }

    /** Conul lovit de o masina: rasturnat instant - dispare si isi lasa itemul pe
     *  asfalt (conul real nu trage masina inapoi; e consumabil de drum). */
    public static void knockCone(Block block) {
        if (block.getWorld() == null) {
            return;
        }
        Location dropAt = block.getLocation().add(0.5D, 0.25D, 0.5D);
        block.setType(Material.AIR, false);
        block.getWorld().dropItemNaturally(dropAt, item(Kind.CONE, 1));
        block.getWorld().playSound(dropAt, Sound.BLOCK_BAMBOO_WOOD_FALL, 0.9F, 1.5F);
    }
}
