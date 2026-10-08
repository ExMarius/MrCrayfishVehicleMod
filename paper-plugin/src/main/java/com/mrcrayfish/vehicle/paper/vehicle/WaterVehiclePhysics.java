package com.mrcrayfish.vehicle.paper.vehicle;

/**
 * Scalar portions of the last complete 1.16.X BoatEntity physics. The dev branch
 * left BoatEntity#updateVehicleMotion empty; these equations are retained from
 * the released implementation (which referenced a per-vehicle getNormalSpeed()/
 * getMaxSpeed() ratio, e.g. "Math.min(1.0F, getNormalSpeed())") so the Jet Ski
 * has source-authored water motion. Forward/reverse top speed are therefore
 * taken from each vehicle's own LandVehicleSpec (enginePower/maxReverseSpeed)
 * rather than a single shared constant, so a Speed Boat (enginePower 20),
 * Jet Ski (18), and Aluminum Boat (10) are no longer flattened to the same
 * absolute top speed in-game.
 */
final class WaterVehiclePhysics {
    static final float ACCELERATION = 0.5F;

    private WaterVehiclePhysics() {
    }

    static float updateSpeed(float speed, float throttle, boolean operating,
                             boolean inWater, double globalSpeedLimit,
                             float maxForwardSpeed, float maxReverseSpeed) {
        if (operating && inWater) {
            if (throttle > 0.0F) {
                float maximum = (float) Math.min(maxForwardSpeed,
                        Math.max(0.0D, globalSpeedLimit));
                return Math.min(maximum, speed + ACCELERATION * Math.min(1.0F, throttle));
            }
            if (throttle < 0.0F) {
                float reverse = (float) -Math.min(maxReverseSpeed,
                        Math.max(0.0D, globalSpeedLimit));
                return Math.max(reverse, speed + ACCELERATION * Math.max(-1.0F, throttle));
            }
            return speed * 0.9F;
        }
        return speed * (inWater ? 0.85F : 0.98F);
    }

    static float wheelAngle(float steeringAngle, float speed) {
        return steeringAngle * Math.max(0.45F, 1.0F - Math.abs(speed / 20.0F));
    }

    static float deltaYaw(float steeringAngle, float speed, boolean inAir) {
        float delta = wheelAngle(steeringAngle, speed) * (speed / 30.0F) / 2.0F;
        return inAir ? delta * 2.0F : delta;
    }

    /**
     * r37: per explicit user direction, boats no longer sit lower in the water while
     * idle than while moving (the genuine released source formula was
     * "waterLevel - 0.35 + 0.25 * min(1, speed/maxForwardSpeed)", sinking 0.25 blocks
     * at rest versus full throttle). This is a disclosed deviation from the literal
     * source value, not a reproduction of it; see SOURCE_POSITION_AUDIT.md r37 entry.
     */
    static double targetSurfaceY(double waterLevel, float speed, float maxForwardSpeed) {
        return waterLevel - 0.10D;
    }

    /** Matches the disclosed targetSurfaceY height above so a freshly placed boat
     * does not visibly drop the moment it starts ticking. */
    static double restingSurfaceY(double waterLevel) {
        return waterLevel - 0.10D;
    }

    enum State {
        IN_WATER,
        UNDER_WATER,
        UNDER_FLOWING_WATER,
        ON_LAND,
        IN_AIR
    }
}
