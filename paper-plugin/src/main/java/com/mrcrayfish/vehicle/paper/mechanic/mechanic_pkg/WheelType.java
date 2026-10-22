package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.physics.SurfaceProfile;

import java.util.Locale;

/**
 * The original mod's wheel physics (IWheelType), values 1-la-1:
 * surface friction modifiers road/dirt/snow + base/slide traction.
 */
public enum WheelType {
    STANDARD("standard_wheel", "Roti standard", 1.1F, 1.3F, 1.7F, 0.8F, 0.05F),
    SPORTS("sports_wheel", "Roti sport", 0.8F, 1.5F, 2.2F, 0.9F, 0.15F),
    OFF_ROAD("off_road_wheel", "Roti off-road", 1.2F, 0.9F, 1.1F, 0.8F, 0.2F);

    private final String model;
    private final String label;
    private final float roadModifier;
    private final float dirtModifier;
    private final float snowModifier;
    private final float baseTraction;
    private final float slideTraction;

    WheelType(String model, String label, float roadModifier, float dirtModifier,
              float snowModifier, float baseTraction, float slideTraction) {
        this.model = model;
        this.label = label;
        this.roadModifier = roadModifier;
        this.dirtModifier = dirtModifier;
        this.snowModifier = snowModifier;
        this.baseTraction = baseTraction;
        this.slideTraction = slideTraction;
    }

    public String model() {
        return model;
    }

    public String label() {
        return label;
    }

    public float baseTraction() {
        return baseTraction;
    }

    public float slideTraction() {
        return slideTraction;
    }

    /** The mod's per-surface friction multiplier for this wheel. */
    public float surfaceModifier(SurfaceProfile.Category category) {
        return switch (category) {
            case ROAD -> roadModifier;
            case DIRT -> dirtModifier;
            case SNOW -> snowModifier;
            default -> 1.0F;
        };
    }

    public static WheelType fromName(String name) {
        if (name == null) {
            return STANDARD;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return STANDARD;
        }
    }
}
