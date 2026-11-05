package com.mrcrayfish.vehicle.paper.mechanic;

import java.util.Locale;

/**
 * The original mod's {@code EngineTier} verbatim: IRON(1.0, +0), GOLD(1.1, +2),
 * DIAMOND(1.2, +5), NETHERITE(1.4, +5). The power multiplier enters the physics
 * exactly like the source ({@code forwardForce *= powerMultiplier}); the bps
 * delta is shown in the item lore exactly like the source tooltip.
 */
public enum EngineTier {
    IRON(1.0F, 0.0F, "Fier"),
    GOLD(1.1F, 2.0F, "Aur"),
    DIAMOND(1.2F, 5.0F, "Diamant"),
    NETHERITE(1.4F, 5.0F, "Netherita");

    private final float powerMultiplier;
    private final float additionalMaxSpeed;
    private final String label;

    EngineTier(float powerMultiplier, float additionalMaxSpeed, String label) {
        this.powerMultiplier = powerMultiplier;
        this.additionalMaxSpeed = additionalMaxSpeed;
        this.label = label;
    }

    public float powerMultiplier() {
        return powerMultiplier;
    }

    public float additionalMaxSpeed() {
        return additionalMaxSpeed;
    }

    public String label() {
        return label;
    }

    public static EngineTier fromName(String name) {
        if (name == null) {
            return IRON;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return IRON;
        }
    }
}
