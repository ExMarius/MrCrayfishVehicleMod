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
}
