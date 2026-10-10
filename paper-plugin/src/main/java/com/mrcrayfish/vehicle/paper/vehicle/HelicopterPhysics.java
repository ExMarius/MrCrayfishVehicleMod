package com.mrcrayfish.vehicle.paper.vehicle;

/** Pure source equations shared by the Compact Helicopter runtime and regression tests. */
final class HelicopterPhysics {
    private HelicopterPhysics() {
    }

    /** HelicopterEntity#getMaxBladeSpeed. */
    static float maximumBladeSpeed(float liftInput, boolean flying, float enginePower) {
        if (liftInput > 0.0F) {
            return 200.0F + enginePower;
        }
        if (flying) {
            return liftInput < 0.0F ? 150.0F : 200.0F;
        }
        return 80.0F;
    }

    /** HelicopterEntity#updateBladeSpeed, including its strict less-than branch. */
    static float nextBladeSpeed(float bladeSpeed, boolean operating,
                                float liftInput, boolean flying, float enginePower) {
        if (!operating) {
            return bladeSpeed * 0.95F;
        }
        float maximum = maximumBladeSpeed(liftInput, flying, enginePower);
        if (bladeSpeed < maximum) {
            float next = bladeSpeed + (liftInput > 0.0F ? enginePower / 4.0F : 0.5F);
            return Math.min(next, maximum);
        }
        return bladeSpeed * 0.95F;
    }
}
