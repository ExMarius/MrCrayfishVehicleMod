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
    public enum Kind { CONE, BOOST_PAD }

    private static final NamespacedKey KIND = new NamespacedKey("vehicle", "prop_kind");

    private TrafficProps() {
    }

    /* ================================ itemele ================================ */

    public static ItemStack item(Kind kind, int amount) {
        ItemStack stack = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("vehicle",
                kind == Kind.CONE ? "traffic_cone" : "boost_pad"));
        meta.getPersistentDataContainer().set(KIND, PersistentDataType.STRING, kind.name());
        meta.displayName(Component.text(kind == Kind.CONE ? "Con de Trafic" : "Placa de Boost",
                        kind == Kind.CONE ? NamedTextColor.GOLD : NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(kind == Kind.CONE
                                ? "Click-dreapta pe asfalt - conul real, masina il rastoarna"
                                : "Click-dreapta pe asfalt - sageata arata in directia in care privesti",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
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

    public static BlockData coneData() {
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
        Tripwire data = (Tripwire) coneData();
        data.setFace(facing, true);
        return data;
    }

    /** Identifica blocurile noastre; orice alta combinatie de tripwire = vanilla. */
    public static Kind kindOf(Block block) {
        if (block == null || block.getType() != Material.TRIPWIRE
                || !(block.getBlockData() instanceof Tripwire data)
                || !data.isDisarmed() || data.isAttached()) {
            return null;
        }
        int faces = 0;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            if (data.hasFace(face)) {
                faces++;
            }
        }
        return faces == 0 ? Kind.CONE : (faces == 1 ? Kind.BOOST_PAD : null);
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
