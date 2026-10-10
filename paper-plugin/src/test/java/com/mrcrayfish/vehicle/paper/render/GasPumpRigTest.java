package com.mrcrayfish.vehicle.paper.render;

import org.bukkit.block.BlockFace;
import org.bukkit.inventory.MainHand;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the small pieces of {@link GasPumpRig} ported verbatim from the original mod's
 * {@code CollisionHelper#fixRotation}, {@code Direction#get2DDataValue}, and
 * {@code Vector3d#yRot}/{@code directionFromRotation} math, independently of anything
 * Bukkit-entity related (which needs a running server to exercise).
 */
class GasPumpRigTest {
    private static final double EPSILON = 1.0E-9D;
    private static final float EPSILON_F = 1.0E-5F;
    private static final Vector3f NOZZLE_SCALE = new Vector3f(0.8F);

    @Test
    void fixRotationLeavesEastUntouchedAndMirrorsTheOriginalSwitchForTheOtherThreeFacings() {
        // EAST is the original's default/no-op case -- the authored geometry's own facing.
        assertArrayEquals(new double[]{0.29D, 1.06D, 0.29D, 1.06D},
                GasPumpRig.fixRotation(BlockFace.EAST, 0.29D, 1.06D, 0.29D, 1.06D), EPSILON);

        assertArrayEquals(new double[]{1.06D, 0.71D, 1.06D, 0.71D},
                GasPumpRig.fixRotation(BlockFace.NORTH, 0.29D, 1.06D, 0.29D, 1.06D), EPSILON);
        assertArrayEquals(new double[]{-0.06D, 0.29D, -0.06D, 0.29D},
                GasPumpRig.fixRotation(BlockFace.SOUTH, 0.29D, 1.06D, 0.29D, 1.06D), EPSILON);
        assertArrayEquals(new double[]{0.71D, -0.06D, 0.71D, -0.06D},
                GasPumpRig.fixRotation(BlockFace.WEST, 0.29D, 1.06D, 0.29D, 1.06D), EPSILON);
    }

    @Test
    void get2DDataValueMatchesTheF3DebugScreenOrdering() {
        assertEquals(0, GasPumpRig.get2DDataValue(BlockFace.SOUTH));
        assertEquals(1, GasPumpRig.get2DDataValue(BlockFace.WEST));
        assertEquals(2, GasPumpRig.get2DDataValue(BlockFace.NORTH));
        assertEquals(3, GasPumpRig.get2DDataValue(BlockFace.EAST));
    }

    @Test
    void blockstateYDegreesMatchesThePacksVariantTable() {
        assertEquals(0.0F, GasPumpRig.blockstateYDegrees(BlockFace.NORTH));
        assertEquals(90.0F, GasPumpRig.blockstateYDegrees(BlockFace.EAST));
        assertEquals(180.0F, GasPumpRig.blockstateYDegrees(BlockFace.SOUTH));
        assertEquals(270.0F, GasPumpRig.blockstateYDegrees(BlockFace.WEST));
    }

    /** {@link GasPumpRig#bodyRotation} bakes the block's own {@code "y": D} blockstate
     *  rotation directly into the body's {@code Transformation}, as plain {@code
     *  rotateY(-D)}. Verified here against {@link GasPumpRig#yRot}, an independent, already
     *  vanilla-verified rotation helper. */
    @Test
    void bodyRotationMatchesTheBlockstatesOwnYRotation() {
        for (BlockFace facing : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            float degrees = GasPumpRig.blockstateYDegrees(facing);
            Vector3f point = new Vector3f(0.3F, 0.4F, -0.2F);

            Vector3f expected = GasPumpRig.yRot(point, (float) Math.toRadians(-degrees));

            Vector3f actual = GasPumpRig.bodyRotation(facing).transform(new Vector3f(point));

            assertVector(actual, expected.x, expected.y, expected.z);
        }
    }

    /**
     * Cross-checks {@link GasPumpRig#bodyRotation} against something with no shared code
     * path: {@code gas_pump_top.json}'s elements 4 and 5 are the only ones that stick out
     * past the model's own +X edge -- the nozzle-holder bracket modeled on the mesh -- and
     * the nozzle's idle rest position for the same facing is computed completely separately,
     * via {@link GasPumpRig#fixRotation}. A correctly-facing body has to put those two close
     * to each other.
     */
    @Test
    void bodyRotationPutsTheModeledNozzleBracketRightNextToFixRotationsIndependentlyComputedRestPoint() {
        // gas_pump_top.json elements 4 and 5, averaged and converted from their 16-units-per-
        // block pixel space to this file's normalized [0, 1] space.
        Vector3f bracketLocal = new Vector3f(1.0434048F, 0.6696271F, 0.375F);
        Vector3f center = new Vector3f(0.5F, 0.5F, 0.5F);

        for (BlockFace facing : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Vector3f bracketWorld = GasPumpRig.bodyRotation(facing)
                    .transform(new Vector3f(bracketLocal).sub(center)).add(center);

            double[] nozzleXZ = GasPumpRig.fixRotation(facing, 0.29D, 1.06D, 0.29D, 1.06D);

            double dx = bracketWorld.x - nozzleXZ[0];
            double dz = bracketWorld.z - nozzleXZ[1];
            double distance = Math.sqrt(dx * dx + dz * dz);

            assertTrue(distance < 0.4D,
                    facing + ": bracket/nozzle-rest distance was " + distance + ", expected well under 1 block");
        }
    }

    @Test
    void directionFromRotationMatchesTheProjectsOwnEstablishedPitchYawConvention() {
        assertVector(GasPumpRig.directionFromRotation(0.0F, 0.0F), 0.0F, 0.0F, 1.0F);
        assertVector(GasPumpRig.directionFromRotation(0.0F, 90.0F), -1.0F, 0.0F, 0.0F);
        // Looking straight down (positive pitch) points mostly -Y, matching vanilla's
        // "positive pitch looks down" convention used throughout this project.
        Vector3f down = GasPumpRig.directionFromRotation(90.0F, 0.0F);
        assertVector(down, 0.0F, -1.0F, 0.0F);
    }

    @Test
    void yRotMatchesVanillasVector3dYRot() {
        Vector3f rotated = GasPumpRig.yRot(new Vector3f(1.0F, 2.0F, 0.0F), (float) (Math.PI / 2));
        assertVector(rotated, 0.0F, 2.0F, -1.0F);
    }

    /** {@link GasPumpRig#nozzleHandOffset} reproduces {@code GasPumpRenderer#getNozzlePosition}'s
     *  literal {@code (-0.35 * handSide, -0.025, -0.025)} constants, for both hands, at
     *  {@code bodyYaw = 0} where {@link GasPumpRig#yRot} is a no-op. */
    @Test
    void nozzleHandOffsetMatchesTheOriginalsLiteralGetNozzlePositionConstants() {
        assertVector(GasPumpRig.nozzleHandOffset(0.0F, MainHand.RIGHT), -0.35F, -0.025F, -0.025F);
        assertVector(GasPumpRig.nozzleHandOffset(0.0F, MainHand.LEFT), 0.35F, -0.025F, -0.025F);
    }

    private static void assertVector(Vector3f actual, float x, float y, float z) {
        assertEquals(x, actual.x, EPSILON_F);
        assertEquals(y, actual.y, EPSILON_F);
        assertEquals(z, actual.z, EPSILON_F);
    }

    /**
     * A vanilla {@code ItemDisplay}'s {@code Transformation} always pivots rotation and scale
     * on the model's own center, not on {@code gas_hose_segment.json}'s off-center local
     * origin. Each case below reproduces the engine's own composition -- {@code center +
     * rotation * scale * (modelPos - center) + translation} for a unit model space with
     * {@code center = (0.5, 0.5, 0.5)} -- using {@link GasPumpRig#hoseSegmentPivotCompensation}
     * for {@code translation}, and asserts the segment's own local center axis ({@code x = y =
     * 0}) lands exactly on {@code from} at {@code t = 0} and {@code from + length * direction}
     * at {@code t = 1}, for several directions a real spline segment can take.
     */
    @Test
    void hoseSegmentPivotCompensationKeepsEachSegmentRunningFromItsAnchorToItsTarget() {
        assertSegmentRunsFromAnchorToTarget(new Vector3f(1.0F, 0.0F, 0.0F), 0.15F);
        assertSegmentRunsFromAnchorToTarget(new Vector3f(0.0F, -1.0F, 0.3F), 0.08F);
        assertSegmentRunsFromAnchorToTarget(new Vector3f(0.0F, 0.0F, 1.0F), 1.0F);
        assertSegmentRunsFromAnchorToTarget(new Vector3f(-0.2F, 0.6F, -0.77F), 0.0421F);
    }

    private static void assertSegmentRunsFromAnchorToTarget(Vector3f rawDirection, float length) {
        Vector3f direction = new Vector3f(rawDirection).normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), direction);
        Vector3f translation = GasPumpRig.hoseSegmentPivotCompensation(rotation, length);

        for (float t : new float[]{0.0F, 0.5F, 1.0F}) {
            Vector3f modelPos = new Vector3f(0.0F, 0.0F, t);
            Vector3f center = new Vector3f(0.5F, 0.5F, 0.5F);
            // scale is (1, 1, length) for a hose segment, so only Z actually needs multiplying.
            Vector3f diff = new Vector3f(modelPos).sub(center);
            Vector3f scaled = new Vector3f(diff.x, diff.y, diff.z * length);
            Vector3f rendered = rotation.transform(new Vector3f(scaled)).add(center).add(translation);
            Vector3f expected = new Vector3f(direction).mul(t * length);
            assertVector(rendered, expected.x, expected.y, expected.z);
        }
    }

    /**
     * {@link GasPumpRig#pivotCompensation} makes the engine's transform composition (the
     * same formula cited above) place the model's local origin -- the original's own {@code
     * matrixStack.translate(...)} point -- exactly at the anchor, for several rotation/scale/
     * right-rotation combinations spanning the ones {@code GasPumpRig} actually uses.
     */
    @Test
    void pivotCompensationPlacesTheModelsLocalOriginExactlyAtTheAnchor() {
        Quaternionf place180 = new Quaternionf().rotateY((float) Math.PI);
        assertPivotCompensationPlacesOriginAtAnchor(new Quaternionf(), new Vector3f(1.0F), new Quaternionf());
        assertPivotCompensationPlacesOriginAtAnchor(new Quaternionf(), new Vector3f(0.8F), place180);
        for (float yAngleDegrees : new float[]{0.0F, -90.0F, 90.0F, 180.0F, 270.0F, 37.0F}) {
            Quaternionf restRotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(yAngleDegrees))
                    .rotateY((float) Math.PI)
                    .rotateX((float) Math.toRadians(90.0D));
            assertPivotCompensationPlacesOriginAtAnchor(restRotation, new Vector3f(0.8F), new Quaternionf());
            assertPivotCompensationPlacesOriginAtAnchor(restRotation, new Vector3f(0.8F), place180);
        }
    }

    /**
     * The original's {@code GasPumpRenderer#render} (non-fueling branch) renders the nozzle
     * with the single literal sequence {@code rotateY(facing * -90) -> rotateY(180) ->
     * rotateX(90)}; that one 180 is already fully accounted for by {@code nozzleRestRotation}
     * / {@code nozzleHandRotation}, so {@link GasPumpRig}'s {@code place} must pass {@code
     * rightRotation} straight through unchanged rather than baking on a second one. This
     * compares, for each facing, the engine's own render-offset formula evaluated at two model
     * points against the difference the original's literal matrix stack produces for those
     * same two points -- a formulation where the translation and pivot-center terms cancel out
     * of the difference entirely, leaving a check that depends only on the rotation applied.
     */
    @Test
    void placeRendersTheNozzleWithExactlyTheOriginalsRotationNoExtra180() {
        Vector3f probeA = new Vector3f(0.9F, 0.3F, 0.1F);
        Vector3f probeB = new Vector3f(0.1F, 0.3F, 0.9F);
        Quaternionf identity = new Quaternionf();
        Quaternionf buggyExtra180 = new Quaternionf().rotateY((float) Math.PI);
        for (float yAngleDegrees : new float[]{0.0F, -90.0F, 90.0F, 180.0F, 270.0F, 37.0F}) {
            Quaternionf leftRotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(yAngleDegrees))
                    .rotateY((float) Math.PI)
                    .rotateX((float) Math.toRadians(90.0D));

            Vector3f originalDifference = originalMatrixStackDifference(leftRotation, NOZZLE_SCALE, probeA, probeB);

            Vector3f fixedDifference = engineRenderOffsetDifference(leftRotation, NOZZLE_SCALE, identity, probeA, probeB);
            assertVector(fixedDifference, originalDifference.x, originalDifference.y, originalDifference.z);

            // A spurious extra rightRotation of rotateY(180) must NOT match the original.
            Vector3f buggyDifference = engineRenderOffsetDifference(leftRotation, NOZZLE_SCALE, buggyExtra180, probeA, probeB);
            assertTrue(new Vector3f(buggyDifference).sub(originalDifference).length() > 0.1F,
                    "Expected an extra-180 rightRotation to visibly diverge from the original at "
                            + yAngleDegrees + " degrees, but it matched: " + buggyDifference);
        }
    }

    /** The original's literal, Forge matrix-stack-derived world-space difference between two
     *  raw model-space points for the nozzle: {@code translate(anchor) -> leftRotation ->
     *  scale -> render}. The anchor/recentering terms are constant across both points and
     *  cancel out of the subtraction, leaving only the rotation-and-scale dependent part. */
    private static Vector3f originalMatrixStackDifference(
            Quaternionf leftRotation, Vector3f scale, Vector3f pointA, Vector3f pointB) {
        Vector3f delta = new Vector3f(pointA).sub(pointB);
        Vector3f scaled = new Vector3f(delta.x * scale.x, delta.y * scale.y, delta.z * scale.z);
        return leftRotation.transform(scaled);
    }

    /** The engine's own {@code Transformation} render-offset formula, evaluated at two model
     *  points and subtracted -- the {@code center}/{@code translation} terms are identical
     *  for both points and cancel out, leaving only the rotation-and-scale dependent part,
     *  directly comparable with {@link #originalMatrixStackDifference}. */
    private static Vector3f engineRenderOffsetDifference(
            Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation, Vector3f pointA, Vector3f pointB) {
        Vector3f deltaA = rightRotation.transform(new Vector3f(pointA));
        Vector3f scaledA = new Vector3f(deltaA.x * scale.x, deltaA.y * scale.y, deltaA.z * scale.z);
        Vector3f offsetA = leftRotation.transform(scaledA);

        Vector3f deltaB = rightRotation.transform(new Vector3f(pointB));
        Vector3f scaledB = new Vector3f(deltaB.x * scale.x, deltaB.y * scale.y, deltaB.z * scale.z);
        Vector3f offsetB = leftRotation.transform(scaledB);

        return new Vector3f(offsetA).sub(offsetB);
    }

    private static void assertPivotCompensationPlacesOriginAtAnchor(
            Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {
        Vector3f translation = GasPumpRig.pivotCompensation(leftRotation, scale, rightRotation);
        Vector3f center = new Vector3f(0.5F, 0.5F, 0.5F);
        Vector3f modelPos = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f diff = new Vector3f(modelPos).sub(center);
        Vector3f rotated = rightRotation.transform(new Vector3f(diff));
        Vector3f scaled = new Vector3f(rotated.x * scale.x, rotated.y * scale.y, rotated.z * scale.z);
        Vector3f rendered = leftRotation.transform(new Vector3f(scaled)).add(center).add(translation);
        assertVector(rendered, 0.0F, 0.0F, 0.0F);
    }
}
