package com.mrcrayfish.vehicle.paper.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HelicopterPhysicsTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void sourceBladeLimitsCoverGroundNeutralAscentAndDescent() {
        assertEquals(80.0F, HelicopterPhysics.maximumBladeSpeed(0.0F, false, 25.0F), EPSILON);
        assertEquals(200.0F, HelicopterPhysics.maximumBladeSpeed(0.0F, true, 25.0F), EPSILON);
        assertEquals(225.0F, HelicopterPhysics.maximumBladeSpeed(1.0F, true, 25.0F), EPSILON);
        assertEquals(150.0F, HelicopterPhysics.maximumBladeSpeed(-1.0F, true, 25.0F), EPSILON);
    }

    @Test
    void positiveLiftUsesQuarterPowerAndClampsAtTheLimit() {
        assertEquals(6.25F,
                HelicopterPhysics.nextBladeSpeed(0.0F, true, 1.0F, false, 25.0F), EPSILON);
        assertEquals(225.0F,
                HelicopterPhysics.nextBladeSpeed(223.0F, true, 1.0F, true, 25.0F), EPSILON);
    }

    @Test
    void neutralOperationAddsHalfAndOverspeedOrShutdownDecaysFivePercent() {
        assertEquals(10.5F,
                HelicopterPhysics.nextBladeSpeed(10.0F, true, 0.0F, false, 25.0F), EPSILON);
        assertEquals(76.0F,
                HelicopterPhysics.nextBladeSpeed(80.0F, true, 0.0F, false, 25.0F), EPSILON);
        assertEquals(95.0F,
                HelicopterPhysics.nextBladeSpeed(100.0F, false, 1.0F, true, 25.0F), EPSILON);
    }
}
