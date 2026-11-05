package com.mrcrayfish.vehicle.paper.mechanic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Slab;

/**
 * Statiile ca BLOCURI SOLIDE reale - misiunea anti-lag (200+ vehicule pe server):
 * zero entitati, zero riguri display, zero tick.
 *
 * Alegerea blocurilor-gazda (v2, dupa feedback live):
 *  - note_block a picat: e bloc OPAC si taie fetele vecinilor (X-ray spre void).
 *    Stațiile au modele ce NU umplu cubul, deci gazdele trebuie sa fie
 *    NEOPACE (nu occlud): slab/composter nu vizualizeaza gauri langa ele.
 *  - Elevatorul = petrified_oak_slab: jumate de bloc inaltime (ca in mod),
 *    masina urca firesc peste el; slab-ul petrificat NU se obtine in vanilla
 *    survival, deci nicio piesa legitima din lume nu isi schimba aspectul.
 *    JOS = type=bottom (platforma jos), SUS = type=double (platforma plina).
 *  - Bancul = composter[level=7]: neopac, coliziune de masa, se sparge normal,
 *    particule de spargere din model (textura mesei = galben-maro lemn).
 *    Conflictul cosmetic cu compostoarele de ferma (la nivel exact 7) e
 *    acceptat si documentat; hopper-ul deasupra statiei e evitat prin geografie.
 *
 * Particulele de spargere si randarea vin din modelele noastre block din pack;
 * interactiunea pe ele e anulata de listener inainte de orice actiune vanilla.
 */
public final class StationBlocks {
    public enum Kind { WORKSTATION, JACK_DOWN, JACK_UP }

    /** Nivelul de composter „instalat" ca banc de lucru. */
    private static final int WORKSTATION_LEVEL = 7;

    private StationBlocks() {
    }

    private static BlockData workstationData() {
        BlockData data = Bukkit.createBlockData(Material.COMPOSTER);
        ((Levelled) data).setLevel(WORKSTATION_LEVEL);
        return data;
    }

    private static BlockData slabData(Slab.Type type) {
        Slab data = (Slab) Bukkit.createBlockData(Material.PETRIFIED_OAK_SLAB);
        data.setType(type);
        data.setWaterlogged(false);
        return data;
    }

    public static final BlockData WORKSTATION = workstationData();
    /** Elevator JOS: jumatea de jos de bloc - masina intra fix peste el (ca in mod). */
    public static final BlockData JACK_DOWN = slabData(Slab.Type.BOTTOM);
    /** Elevator SUS (cap ridicat): jumatatea DE SUS = platforma la +0.5 sub masina.
     *  Ambele jumatati de slab sunt NEOPACE - nici blocul de dedesubt nu-si mai
     *  ascunde fata de sus (fixul gaurii spre void de sub statii). */
    public static final BlockData JACK_UP = slabData(Slab.Type.TOP);

    /** Identifica statiile noastre dupa bloc+stare; type=double/alte sunt vanilla. */
    public static Kind kindOf(Block block) {
        if (block == null) {
            return null;
        }
        if (block.getType() == Material.COMPOSTER
                && block.getBlockData() instanceof Levelled levelled
                && levelled.getLevel() == WORKSTATION_LEVEL) {
            return Kind.WORKSTATION;
        }
        if (block.getType() == Material.PETRIFIED_OAK_SLAB
                && block.getBlockData() instanceof Slab slab) {
            return switch (slab.getType()) {
                case BOTTOM -> Kind.JACK_DOWN;
                case TOP -> Kind.JACK_UP;
                case DOUBLE -> null; // varianta vanilla (tamplarie), neatinsa
            };
        }
        return null;
    }
}
