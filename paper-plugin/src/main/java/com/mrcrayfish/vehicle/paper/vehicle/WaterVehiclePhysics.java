package com.mrcrayfish.vehicle.paper.vehicle;

/**
 * Scalar portions of the last complete 1.16.X BoatEntity physics. The dev branch
 * left BoatEntity#updateVehicleMotion empty; these equations are retained from
 * the released implementation so the Jet Ski has source-authored water motion.
 */
final class WaterVehiclePhysics {
    static final float MAX_FORWARD_SPEED = 10.0F;
    static final float MAX_REVERSE_SPEED = -4.0F;
    static final float ACCELERATION = 0.5F;

    private WaterVehiclePhysics() {
    }

    static float updateSpeed(float speed, float throttle, boolean operating,
                             boolean inWater, double globalSpeedLimit) {
        if (operating && inWater) {
            if (throttle > 0.0F) {
                float maximum = (float) Math.min(MAX_FORWARD_SPEED,
                        Math.max(0.0D, globalSpeedLimit));
                return Math.min(maximum, speed + ACCELERATION * Math.min(1.0F, throttle));
            }
            if (throttle < 0.0F) {
                float reverse = (float) -Math.min(-MAX_REVERSE_SPEED,
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

    static double targetSurfaceY(double waterLevel, float speed) {
        return waterLevel - 0.35D
                + 0.25D * Math.min(1.0F, speed / MAX_FORWARD_SPEED);
    }

    static double restingSurfaceY(double waterLevel) {
        return targetSurfaceY(waterLevel, 0.0F);
    }

    enum State {
        IN_WATER,
        UNDER_WATER,
        UNDER_FLOWING_WATER,
        ON_LAND,
        IN_AIR
    }
}
