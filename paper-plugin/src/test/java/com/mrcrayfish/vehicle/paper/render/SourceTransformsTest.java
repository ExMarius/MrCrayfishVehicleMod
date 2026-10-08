package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceTransformsTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void wheelieRotatesAroundGeneratedRearAxleThenMotorcycleRollsAroundRoot() {
        LandVehicleSpec spec = LandVehicleSpec.GO_KART;
        Vector3f wheelied = SourceTransforms.chassisPoint(
                point(spec.bodyOrigin()), point(spec.wheeliePivot()), -30.0F, 0.0F);
        assertVector(wheelied, 0.0F, 0.8757611F, -0.2982974F);

        Vector3f wheeliedAndRolled = SourceTransforms.chassisPoint(
                point(spec.bodyOrigin()), point(spec.wheeliePivot()), -30.0F, 20.0F);
        assertVector(wheeliedAndRolled, -0.29952794F, 0.82294625F, -0.2982974F);
    }

    @Test
    void aircraftAppliesSourceRollThenPitchPointOrder() {
        Vector3f transformed = SourceTransforms.aircraftPoint(
                new Vector3f(1.0F, 2.0F, 3.0F), 30.0F, 20.0F);
        assertVector(transformed, 0.25565234F, 0.4237935F, 3.7087789F);
    }

    @Test
    void dirtBikeForkUsesTiltSteerUntiltedMatrixAroundTenAndAHalfPixelPivot() {
        LandVehicleSpec spec = LandVehicleSpec.DIRT_BIKE;
        Quaternionf fork = SourceTransforms.motorcycleSteering(spec.motorcycle(), 25.0F);

        Vector3f handles = SourceTransforms.forkPoint(
                point(spec.steering().center()), point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(handles, -0.25623173F, 0.87173843F, 0.05248117F);

        LandVehicleSpec.Wheel front = spec.firstFrontWheel();
        Vector3f wheel = SourceTransforms.forkPoint(
                new Vector3f(front.axleX(), front.centerY(), front.axleZ()),
                point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(wheel, 0.006498318F, 0.34944868F, 0.878669F);
    }

    @Test
    void mopedForkSteersHandlesMudGuardAndFrontWheelAroundScaledElevenAndAHalfPixelPivot() {
        LandVehicleSpec spec = LandVehicleSpec.MOPED;
        Quaternionf fork = SourceTransforms.motorcycleSteering(spec.motorcycle(), 25.0F);

        Vector3f handles = SourceTransforms.forkPoint(
                point(spec.steering().center()), point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(handles, -0.03873031F, 1.0888283F, 0.6384652F);

        Vector3f mudGuard = SourceTransforms.forkPoint(
                point(spec.mopedParts().forkParts().getFirst().center()),
                point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(mudGuard, -0.037937243F, 0.47604856F, 0.8941278F);

        LandVehicleSpec.Wheel front = spec.firstFrontWheel();
        Vector3f wheel = SourceTransforms.forkPoint(
                new Vector3f(front.axleX(), front.centerY(), front.axleZ()),
                point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(wheel, -0.009121702F, 0.24077387F, 1.0584683F);
    }

    @Test
    void duneBuggySteersHandlesAndManuallyRenderedFrontWheelThroughSourceTiltedMatrix() {
        LandVehicleSpec spec = LandVehicleSpec.DUNE_BUGGY;
        Quaternionf fork = SourceTransforms.motorcycleSteering(
                spec.motorcycle(), spec.steeringVisualAngle());

        Vector3f handles = SourceTransforms.forkPoint(
                point(spec.steering().center()), point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(handles, -0.04857442F, 0.6681491F, -0.0015089406F);

        LandVehicleSpec.Wheel front = spec.firstFrontWheel();
        Vector3f wheel = SourceTransforms.forkPoint(
                point(front.visualCenter()), point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(wheel, -0.0041399524F, 0.20491186F, 0.42329702F);
    }

    @Test
    void zeroSteeringLeavesDirtBikeHandlesAndForkWheelAtSourceCenters() {
        LandVehicleSpec spec = LandVehicleSpec.DIRT_BIKE;
        Quaternionf fork = SourceTransforms.motorcycleSteering(spec.motorcycle(), 0.0F);
        Vector3f handles = SourceTransforms.forkPoint(
                point(spec.steering().center()), point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(handles, 0.0F, 0.85F, 0.0F);

        LandVehicleSpec.Wheel front = spec.firstFrontWheel();
        Vector3f wheel = SourceTransforms.forkPoint(
                new Vector3f(front.axleX(), front.centerY(), front.axleZ()),
                point(spec.bodyOrigin()), spec.motorcycle(), fork);
        assertVector(wheel, 0.0F, 0.35F, 0.88F);
    }

    @Test
    void openableCosmeticsUseTheSourceEaseOutBackCurve() {
        assertEquals(0.0D, LandVehicleRig.easeOutBack(0.0D), 1.0E-9D);
        assertEquals(1.0D, LandVehicleRig.easeOutBack(1.0D), 1.0E-9D);
        assertEquals(1.0641365625D, LandVehicleRig.easeOutBack(0.75D), 1.0E-9D);
    }

    private static Vector3f point(LandVehicleSpec.Point point) {
        return new Vector3f(point.x(), point.y(), point.z());
    }

    private static void assertVector(Vector3f actual, float x, float y, float z) {
        assertEquals(x, actual.x, EPSILON);
        assertEquals(y, actual.y, EPSILON);
        assertEquals(z, actual.z, EPSILON);
    }
}
