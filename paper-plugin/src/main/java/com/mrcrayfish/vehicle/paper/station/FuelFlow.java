package com.mrcrayfish.vehicle.paper.station;

/**
 * Pure math for one fueling tick: how much of a vehicle's tank an increment actually adds,
 * and how much that increment costs, given what the player can still afford. Kept separate
 * from {@link FuelStationManager} so it can be unit tested without spinning up a server.
 */
public final class FuelFlow {
    private FuelFlow() {
    }

    public enum StopReason {
        NONE, TANK_FULL, OUT_OF_MONEY
    }

    public record Step(float newFuel, double charge, boolean stopped, StopReason reason) {
    }

    /**
     * @param currentFuel      the vehicle's current fuel, in the same units as {@code capacity}
     * @param capacity         the vehicle's maximum fuel
     * @param availableBalance how much money the player currently has (ignored entirely when
     *                         {@code pricePerPercent} is zero or less)
     * @param pricePerPercent  cost per 1% of {@code capacity}
     * @param percentIncrement how many percent of {@code capacity} one tick would add if money
     *                         and tank space were unlimited
     */
    public static Step next(float currentFuel, float capacity, double availableBalance,
                             double pricePerPercent, double percentIncrement) {
        if (capacity <= 0.0F) {
            return new Step(currentFuel, 0.0D, true, StopReason.TANK_FULL);
        }
        float remainingPercent = 100.0F * (capacity - currentFuel) / capacity;
        if (remainingPercent <= 0.0F) {
            return new Step(currentFuel, 0.0D, true, StopReason.TANK_FULL);
        }

        double appliedPercent = Math.min(percentIncrement, remainingPercent);
        double cost = pricePerPercent > 0.0D ? appliedPercent * pricePerPercent : 0.0D;
        if (cost > availableBalance) {
            // Spend whatever is left rather than stopping with zero progress, matching how a
            // real pump would stop exactly when the player's card/cash runs out mid-fill.
            appliedPercent = pricePerPercent > 0.0D ? availableBalance / pricePerPercent : appliedPercent;
            cost = pricePerPercent > 0.0D ? appliedPercent * pricePerPercent : 0.0D;
        }

        float newFuel = currentFuel + (float) (appliedPercent / 100.0D) * capacity;
        boolean tankFull = newFuel >= capacity - 1.0E-4F;
        boolean brokeOut = appliedPercent < percentIncrement - 1.0E-6D && !tankFull;
        if (tankFull) {
            newFuel = capacity;
        }
        StopReason reason = tankFull ? StopReason.TANK_FULL : (brokeOut ? StopReason.OUT_OF_MONEY : StopReason.NONE);
        return new Step(newFuel, Math.max(0.0D, cost), tankFull || brokeOut, reason);
    }
}
