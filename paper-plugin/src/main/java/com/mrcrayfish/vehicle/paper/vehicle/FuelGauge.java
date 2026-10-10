package com.mrcrayfish.vehicle.paper.vehicle;

import java.util.Locale;

/** Vanilla-client presentation of the original mod's continuous fuel value. */
public final class FuelGauge {
    public static final int MAX_HALF_HEARTS = 20;

    private FuelGauge() {
    }

    /**
     * Rounds down so the first consumed fuel is visible immediately. The final
     * half-heart is retained because a living carrier cannot remain alive at
     * zero health.
     */
    public static double mountHealth(float fraction) {
        float clamped = Float.isFinite(fraction) ? Math.max(0.0F, Math.min(1.0F, fraction)) : 1.0F;
        int halfHearts = (int) Math.floor(MAX_HALF_HEARTS * clamped + 1.0E-6F);
        return Math.max(1, Math.min(MAX_HALF_HEARTS, halfHearts));
    }

    /** Matches the original mod overlay's two-decimal current/capacity values. */
    public static String overlay(float fuel, float capacity) {
        float safeCapacity = Float.isFinite(capacity) ? Math.max(1.0F, capacity) : 1.0F;
        float safeFuel = Float.isFinite(fuel) ? Math.max(0.0F, Math.min(safeCapacity, fuel)) : safeCapacity;
        return String.format(Locale.ROOT, "Fuel: %.2f / %.2f (%.1f%%)",
                safeFuel, safeCapacity, safeFuel / safeCapacity * 100.0F);
    }
}
