package com.mrcrayfish.vehicle.paper.mechanic;

import java.util.Locale;

/**
 * The original mod's {@code EngineType} (1-la-1): which engine FAMILY a vehicle
 * accepts. Values copied from the 1.16 source, unchanged.
 */
public enum EngineType {
    SMALL_MOTOR("small", "Motor mic"),
    LARGE_MOTOR("large", "Motor mare"),
    ELECTRIC_MOTOR("electric", "Motor electric"),
    NONE("", "Fara motor");

    private final String folder;
    private final String label;

    EngineType(String folder, String label) {
        this.folder = folder;
        this.label = label;
    }

    public String folder() {
        return folder;
    }

    public String label() {
        return label;
    }

    public static EngineType fromName(String name) {
        if (name == null) {
            return NONE;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
