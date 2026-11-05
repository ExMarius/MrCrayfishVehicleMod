package com.mrcrayfish.vehicle.paper.mechanic;

import org.bukkit.Material;

/** Paleta de vopsit (cele 16 culori clasice de vopsea, cu RGB vanilla). */
public enum PaintColors {
    WHITE("alba", 0xF9FFFE, Material.WHITE_DYE),
    LIGHT_GRAY("gri deschis", 0x9D9D97, Material.LIGHT_GRAY_DYE),
    GRAY("gri", 0x474F52, Material.GRAY_DYE),
    BLACK("neagra", 0x1D1D21, Material.BLACK_DYE),
    BROWN("maro", 0x835432, Material.BROWN_DYE),
    RED("rosie", 0xB02E26, Material.RED_DYE),
    ORANGE("portocalie", 0xF9801D, Material.ORANGE_DYE),
    YELLOW("galbena", 0xFED83D, Material.YELLOW_DYE),
    LIME("verde deschis", 0x80C71F, Material.LIME_DYE),
    GREEN("verde", 0x5E7C16, Material.GREEN_DYE),
    CYAN("cyan", 0x169C9C, Material.CYAN_DYE),
    LIGHT_BLUE("albastru deschis", 0x3AB3DA, Material.LIGHT_BLUE_DYE),
    BLUE("albastra", 0x3C44AA, Material.BLUE_DYE),
    PURPLE("mov", 0x8932B8, Material.PURPLE_DYE),
    MAGENTA("magenta", 0xC74EBD, Material.MAGENTA_DYE),
    PINK("roz", 0xF38BAA, Material.PINK_DYE);

    private final String label;
    private final int rgb;
    private final Material icon;

    PaintColors(String label, int rgb, Material icon) {
        this.label = label;
        this.rgb = rgb;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public int rgb() {
        return rgb;
    }

    public Material icon() {
        return icon;
    }
}
