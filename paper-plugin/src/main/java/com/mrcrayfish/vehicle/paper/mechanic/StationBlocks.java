package com.mrcrayfish.vehicle.paper.mechanic;

import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;

/**
 * Statiile ca BLOCURI SOLIDE reale - misiunea anti-lag (200+ vehicule pe server):
 * zero entitati, zero riguri display, zero tick. Trucul: note_block - vanilla
 * randeaza ACELASI model pentru toate starile, asa ca suprascriem in resource
 * pack cateva combinatii instrument+nota (fixa 17) cu modelele statiilor.
 * Jucatorul nu le poate „retuna": click-dreapta pe ele e anulat de listener
 * inainte de schimbarea notei, iar „pling"-ul de redstone e suprimat prin
 * NotePlayEvent. powered=true (puls redstone) arata identic - pack-ul mapeaza
 * ambele variante powered pe acelasi model.
 */
public final class StationBlocks {
    public enum Kind { WORKSTATION, JACK_BASE, JACK_HEAD }

    /** Nota fixa comuna tuturor statiilor noastre (distinctiva si greu de nimicit accidental). */
    private static final int STATION_NOTE = 17;

    private StationBlocks() {
    }

    private static BlockData noteData(Instrument instrument) {
        NoteBlock data = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
        data.setInstrument(instrument);
        data.setNote(new Note(STATION_NOTE));
        data.setPowered(false);
        return data;
    }

    public static final BlockData WORKSTATION = noteData(Instrument.WITHER_SKELETON);
    public static final BlockData JACK_BASE = noteData(Instrument.ZOMBIE);
    public static final BlockData JACK_HEAD = noteData(Instrument.SKELETON);

    /** Identifica statiile noastre dupa instrument+nota, ignorand powered (puls redstone). */
    public static Kind kindOf(Block block) {
        if (block == null || block.getType() != Material.NOTE_BLOCK
                || !(block.getBlockData() instanceof NoteBlock noteBlock)
                || noteBlock.getNote().getId() != STATION_NOTE) {
            return null;
        }
        return switch (noteBlock.getInstrument()) {
            case WITHER_SKELETON -> Kind.WORKSTATION;
            case ZOMBIE -> Kind.JACK_BASE;
            case SKELETON -> Kind.JACK_HEAD;
            default -> null;
        };
    }
}
