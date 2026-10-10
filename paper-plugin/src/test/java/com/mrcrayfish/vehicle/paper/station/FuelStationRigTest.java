package com.mrcrayfish.vehicle.paper.station;

import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the two small pivot/rotation helpers {@link FuelStationRig} relies on for every
 * part it places (cabinet halves, nozzle, hose segments), independently of anything
 * Bukkit-entity related (which needs a running server to exercise). Both helpers under test
 * are package-private, so this test lives in the same package deliberately.
 */
class FuelStationRigTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void anchoredTranslationIsZeroWhenTheAnchorIsAtTheOrigin() {
        Vector3f translation = FuelStationRig.anchoredTranslation(new Vector3f(0.0F, 0.0F, 0.0F),
                new Quaternionf().rotateY((float) Math.PI / 3.0F), new Vector3f(1.3F));
        assertVectorEquals(new Vector3f(0.0F, 0.0F, 0.0F), translation);
    }

    @Test
    void anchoredTranslationWithNoRotationOrScaleIsJustTheNegatedAnchor() {
        Vector3f translation = FuelStationRig.anchoredTranslation(new Vector3f(0.5F, 0.25F, 0.75F), new Quaternionf(), new Vector3f(1.0F));
        assertVectorEquals(new Vector3f(-0.5F, -0.25F, -0.75F), translation);
    }

    @Test
    void anchoredTranslationScalesTheAnchorBeforeRotatingIt() {
        // A 90-degree yaw maps +X to -Z under JOML's right-handed rotateY. With a
        // non-uniform scale, the anchor must be scaled on its own local axes *before* that
        // rotation is applied, not after.
        Quaternionf yaw90 = new Quaternionf().rotateY((float) Math.toRadians(90.0));
        Vector3f rotatedUnitX = yaw90.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        assertVectorEquals(new Vector3f(0.0F, 0.0F, -1.0F), rotatedUnitX);

        Vector3f translation = FuelStationRig.anchoredTranslation(new Vector3f(1.0F, 0.0F, 0.0F), yaw90, new Vector3f(2.0F, 1.0F, 1.0F));
        // scaledAnchor = (2, 0, 0) -> rotated = (0, 0, -2) -> negated = (0, 0, 2).
        assertVectorEquals(new Vector3f(0.0F, 0.0F, 2.0F), translation);
    }

    @Test
    void bodyRotationMatchesTheFourCardinalBlockFacings() {
        assertQuaternionEquals(new Quaternionf(), FuelStationRig.bodyRotation(BlockFace.NORTH));
        assertQuaternionEquals(new Quaternionf().rotateY((float) Math.toRadians(90.0)), FuelStationRig.bodyRotation(BlockFace.EAST));
        assertQuaternionEquals(new Quaternionf().rotateY((float) Math.toRadians(180.0)), FuelStationRig.bodyRotation(BlockFace.SOUTH));
        assertQuaternionEquals(new Quaternionf().rotateY((float) Math.toRadians(270.0)), FuelStationRig.bodyRotation(BlockFace.WEST));
    }

    @Test
    void bodyRotationFallsBackToNorthForAnyNonCardinalFacing() {
        assertQuaternionEquals(new Quaternionf(), FuelStationRig.bodyRotation(BlockFace.UP));
        assertQuaternionEquals(new Quaternionf(), FuelStationRig.bodyRotation(BlockFace.DOWN));
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON, "x");
        assertEquals(expected.y, actual.y, EPSILON, "y");
        assertEquals(expected.z, actual.z, EPSILON, "z");
    }

    private static void assertQuaternionEquals(Quaternionf expected, Quaternionf actual) {
        assertEquals(expected.x, actual.x, EPSILON, "x");
        assertEquals(expected.y, actual.y, EPSILON, "y");
        assertEquals(expected.z, actual.z, EPSILON, "z");
        assertEquals(expected.w, actual.w, EPSILON, "w");
    }
}
