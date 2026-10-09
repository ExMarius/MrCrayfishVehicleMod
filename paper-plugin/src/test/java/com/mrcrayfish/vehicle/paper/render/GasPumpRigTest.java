package com.mrcrayfish.vehicle.paper.render;

import org.bukkit.block.BlockFace;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the small pieces of {@link GasPumpRig} ported verbatim from the original mod's
 * {@code CollisionHelper#fixRotation}, {@code Direction#get2DDataValue}, and
 * {@code Vector3d#yRot}/{@code directionFromRotation} math, independently of anything
 * Bukkit-entity related (which needs a running server to exercise).
 */
class GasPumpRigTest {
    private static final double EPSILON = 1.0E-9D;
    private static final float EPSILON_F = 1.0E-5F;

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
    void entityYawForFacingMatchesTheBlockstatesYValues() {
        assertEquals(0.0F, GasPumpRig.entityYawForFacing(BlockFace.NORTH));
        assertEquals(90.0F, GasPumpRig.entityYawForFacing(BlockFace.EAST));
        assertEquals(180.0F, GasPumpRig.entityYawForFacing(BlockFace.SOUTH));
        assertEquals(270.0F, GasPumpRig.entityYawForFacing(BlockFace.WEST));
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

    private static void assertVector(Vector3f actual, float x, float y, float z) {
        assertEquals(x, actual.x, EPSILON_F);
        assertEquals(y, actual.y, EPSILON_F);
        assertEquals(z, actual.z, EPSILON_F);
    }

    /**
     * Regression coverage for the pivot bug that made the hose render as scattered,
     * disconnected fragments instead of a smooth chain: a vanilla {@code ItemDisplay}'s
     * {@code Transformation} always pivots rotation and scale on the model's own center
     * (Minecraft's own display-entity documentation: "the rotation pivot of the item display's
     * transformation is the center of the item model" -- unlike a {@code BlockDisplay}, whose
     * pivot is the model's corner), not on {@code gas_hose_segment.json}'s off-center local
     * origin. Each test below reproduces the engine's own composition --
     * {@code center + rotation * scale * (modelPos - center) + translation} for a unit model
     * space with {@code center = (0.5, 0.5, 0.5)} -- using {@link
     * GasPumpRig#hoseSegmentPivotCompensation} for {@code translation}, and asserts the
     * segment's own local center axis ({@code x = y = 0}) lands exactly on {@code from} at
     * {@code t = 0} and {@code from + length * direction} at {@code t = 1}, for several
     * directions a real spline segment can take (including ones with no, and with every, axis
     * in common with the model's native +Z orientation).
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
            Vector3f scaled = new Vector3f(modelPos).sub(center).mul(1.0F, 1.0F, length);
            Vector3f rendered = rotation.transform(new Vector3f(scaled)).add(center).add(translation);
            Vector3f expected = new Vector3f(direction).mul(t * length);
            assertVector(rendered, expected.x, expected.y, expected.z);
        }
    }
}
