package com.mrcrayfish.vehicle.paper.economy;

/**
 * Pure percent-based pricing and incremental fill math for gas pumps. This is a new
 * addition on top of the original mod (which never charged money for fuel) and is kept
 * completely free of Bukkit/Vault types so it can be unit tested in isolation.
 */
public final class FuelPricing {

    private FuelPricing() {
    }

    /** Why a fueling step stopped, or {@code CONTINUE} if the session should keep running. */
    public enum Reason {
        CONTINUE,
        FULL,
        INSUFFICIENT_FUNDS
    }

    /**
     * @param newFuel the vehicle's fuel level after this step (never exceeds capacity)
     * @param charge  the amount actually withdrawn from the player's balance this step
     * @param stopped {@code true} when the fueling session should end after this step
     * @param reason  why the step stopped, or {@code CONTINUE} while still fueling
     */
    public record Step(float newFuel, double charge, boolean stopped, Reason reason) {
    }

    /**
     * Applies one fueling increment, charging proportionally to the percentage of the
     * tank it represents. A real pump halts the instant a card is declined, so this
     * spends whatever fraction of the increment the player can actually afford (which
     * may be nothing) rather than overdrawing their balance or refusing partial sales.
     *
     * @param currentFuel      the vehicle's current fuel level
     * @param capacity         the vehicle's tank capacity
     * @param balance          funds available to the player right now
     * @param pricePerPercent  cost of filling 1% of the tank (0 or less means free)
     * @param incrementPercent the percentage of the tank this step attempts to add
     */
    public static Step step(float currentFuel, float capacity, double balance,
                             double pricePerPercent, double incrementPercent) {
        float safeCapacity = Math.max(1.0F, capacity);
        float clampedFuel = Math.max(0.0F, Math.min(safeCapacity, currentFuel));
        if (clampedFuel >= safeCapacity - 1.0E-4F) {
            return new Step(safeCapacity, 0.0D, true, Reason.FULL);
        }

        float remainingPercent = (safeCapacity - clampedFuel) / safeCapacity * 100.0F;
        float appliedPercent = Math.min((float) Math.max(0.0D, incrementPercent), remainingPercent);
        double price = Math.max(0.0D, pricePerPercent);
        double cost = appliedPercent * price;
        boolean fundsLimited = false;

        if (cost > 0.0D && balance + 1.0E-6D < cost) {
            double affordablePercent = price > 0.0D ? balance / price : appliedPercent;
            if (affordablePercent <= 1.0E-6D) {
                return new Step(clampedFuel, 0.0D, true, Reason.INSUFFICIENT_FUNDS);
            }
            appliedPercent = (float) Math.min(appliedPercent, affordablePercent);
            cost = appliedPercent * price;
            fundsLimited = true;
        }

        float newFuel = Math.min(safeCapacity, clampedFuel + safeCapacity * appliedPercent / 100.0F);
        boolean full = newFuel >= safeCapacity - 1.0E-4F;
        Reason reason = full ? Reason.FULL : (fundsLimited ? Reason.INSUFFICIENT_FUNDS : Reason.CONTINUE);
        return new Step(newFuel, Math.max(0.0D, cost), full || fundsLimited, reason);
    }
}
