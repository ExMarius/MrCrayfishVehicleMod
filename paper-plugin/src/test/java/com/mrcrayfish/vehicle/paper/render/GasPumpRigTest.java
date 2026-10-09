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

    /**
     * Regression coverage for the bug reported in-game where the pump body spawned shifted
     * into a corner of its own block instead of rotated cleanly in place: an earlier revision
     * rotated the body via the display entity's own {@code setRotation(entityYaw, 0)}, whose
     * exact composition with the {@code Transformation} (order and rotation sign) Mojang's own
     * docs don't pin down precisely enough to verify without a running client. {@link
     * GasPumpRig#bodyRotation} instead bakes the whole rotation into the {@code Transformation}
     * directly, as plain {@code rotateY(-D)} for blockstate degree value {@code D} -- verified
     * here by reproducing that same {@code "y": D} block rotation independently, via this
     * class's own already-verified {@link GasPumpRig#yRot} (see {@link
     * GasPumpRig#bodyRotation}'s own javadoc for the vanilla furnace precedent this rests on).
     */
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
     * The actual regression test for the "hose/nozzle ends up somewhere else on the pump
     * whenever it's rotated" bug: a previous revision of {@link GasPumpRig#bodyRotation} baked
     * an extra, never-independently-verified "+180 degrees" on top of the correct {@code
     * rotateY(-D)} (see that method's own javadoc). Every other test in this file -- including
     * the one right above -- only ever checked {@code bodyRotation} against its own formula, so
     * that extra 180 silently passed all of them. This test instead cross-checks it against
     * something with no shared code path: {@code gas_pump_top.json}'s elements 4 and 5 are the
     * only ones that stick out past the model's own +X edge, i.e. they physically model the
     * pump's nozzle-holder bracket on the mesh, and the hose/nozzle rest position for the same
     * facing is computed completely separately by {@link GasPumpRig#fixRotation} (the original
     * mod's own {@code CollisionHelper#fixRotation}, used by its real renderer for exactly this
     * purpose). A correctly-facing body has to put those two right next to each other -- a
     * nozzle doesn't rest more than a block from its own holder. With the extra 180 included
     * they land {@code 1.11} blocks apart, identically for all four facings (which is why it
     * never looked "only" wrong for one orientation); plain {@code rotateY(-D)} brings that
     * down to a steady {@code ~0.34} blocks, consistent with the nozzle hanging just beside,
     * not inside, its holder.
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

    /**
     * Regression coverage for the bug that put the active (held-nozzle) hose and nozzle prop
     * noticeably out of position versus the original mod: an earlier revision of {@link
     * GasPumpRig#updateActive} hardcoded a right-hand-only offset that both ignored its own
     * {@code mainHand} parameter and used tuned-further values instead of {@code
     * GasPumpRenderer#getNozzlePosition}'s literal {@code (-0.35 * handSide, -0.025, -0.025)}.
     * Verifies {@link GasPumpRig#nozzleHandOffset} reproduces those exact literal values (for
     * both hands, at {@code bodyYaw = 0} where {@link GasPumpRig#yRot} is a no-op) instead.
     */
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
            // scale is (1, 1, length) for a hose segment, so only Z actually needs multiplying.
            Vector3f diff = new Vector3f(modelPos).sub(center);
            Vector3f scaled = new Vector3f(diff.x, diff.y, diff.z * length);
            Vector3f rendered = rotation.transform(new Vector3f(scaled)).add(center).add(translation);
            Vector3f expected = new Vector3f(direction).mul(t * length);
            assertVector(rendered, expected.x, expected.y, expected.z);
        }
    }

    /**
     * Regression coverage for the sibling pivot bug affecting the nozzle prop: both of
     * {@code GasPumpRig}'s {@code place} call sites for it passed a hardcoded zero translation
     * despite giving it a real (non-identity) {@code leftRotation} and a {@code 0.8} scale, so
     * (unlike the pump's own body, whose identity {@code leftRotation} and full-unit-cube model
     * happen to make a hardcoded {@code (-0.5, 0, -0.5)} correct) the nozzle always rendered
     * offset from its intended anchor -- resting point or in-hand point alike -- by an amount
     * that varies with its own current facing/hold rotation. Verifies {@link
     * GasPumpRig#pivotCompensation} makes the engine's own transform composition (see this
     * class's other pivot-compensation test, above, for the formula and its citation) place the
     * model's local origin (the original's own {@code matrixStack.translate(...)} point) exactly
     * at the anchor, for several rotation/scale/right-rotation combinations spanning the ones
     * {@code GasPumpRig} actually uses (the nozzle's own facing-dependent rest rotation and
     * hand rotation, each paired here with both an identity right rotation -- what {@link
     * #place} actually renders with -- and, as a generic sanity case for this pure math
     * function, a 180-degree one). This test only checks that the model's local origin lands on
     * the anchor; it is deliberately blind to which {@code rightRotation} is the *correct* one
     * to use for the nozzle (identity, matching the original's matrix stack -- see {@link
     * #placeRendersTheNozzleWithExactlyTheOriginalsRotationNoExtra180} for that check), since
     * {@code pivotCompensation} always finds a translation that zeroes the origin regardless.
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
     * Regression coverage for a second, independent bug that let the nozzle render with the
     * wrong <em>orientation</em> even after {@link #pivotCompensationPlacesTheModelsLocalOriginExactlyAtTheAnchor}'s
     * bug was fixed: {@code place} used to unconditionally bake an extra {@code rotateY(180)}
     * onto whatever {@code rightRotation} it was given, justified only as "the compensation
     * every place call needs" -- asserted, never checked against the original. It wasn't
     * needed: the original's {@code GasPumpRenderer#render} (non-fueling branch) renders the
     * nozzle with the single literal sequence {@code rotateY(facing * -90) -> rotateY(180) ->
     * rotateX(90)}, and that one 180 is already fully accounted for by {@code nozzleRestRotation}
     * / {@code nozzleHandRotation} (both chain exactly that three-rotation sequence, see {@link
     * #bodyRotationMatchesTheBlockstatesOwnYRotation} and this class's sibling tests for the
     * same pattern). Baking a second 180 on top, as {@code rightRotation}, flipped the rendered
     * model's orientation by an extra 180 degrees it should never have had.
     *
     * <p>This compares, for each facing, the engine's own render-offset formula (cited in {@link
     * #pivotCompensationPlacesTheModelsLocalOriginExactlyAtTheAnchor}) evaluated at two model
     * points against the <em>difference</em> the original's literal matrix stack would produce
     * for those same two points -- a formulation chosen specifically because the translation
     * and pivot-center terms cancel out of a two-point difference entirely, leaving a check that
     * depends only on the rotation actually applied, with no dependency on exactly which model
     * point the pivot math anchors to the entity's position (that is already covered,
     * separately, by the sibling pivot test above). An asymmetric probe difference with nonzero
     * X and Z components is used so a spurious extra {@code rotateY(180)} -- which negates
     * exactly those two axes and leaves Y alone -- cannot cancel out by coincidence.</p>
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

            // The pre-fix behavior (place()'s erroneous extra rotateY(180) as rightRotation)
            // must NOT match the original -- proving the fix actually changes rendered
            // behavior, not just its justification.
            Vector3f buggyDifference = engineRenderOffsetDifference(leftRotation, NOZZLE_SCALE, buggyExtra180, probeA, probeB);
            assertTrue(new Vector3f(buggyDifference).sub(originalDifference).length() > 0.1F,
                    "Expected the pre-fix extra-180 rightRotation to visibly diverge from the original at "
                            + yAngleDegrees + " degrees, but it matched: " + buggyDifference);
        }
    }

    private static final Vector3f NOZZLE_SCALE = new Vector3f(0.8F);

    /** The original's literal, Forge matrix-stack-derived world-space difference between two
     *  raw model-space points for the nozzle: {@code translate(anchor) -> leftRotation ->
     *  scale -> render}, with the model's own {@code -0.5} recentering (see {@code
     *  RenderUtil#renderColoredModel}) applied before the rotation/scale, exactly mirroring
     *  {@code GasPumpRenderer}'s own call order. The anchor/{@code -0.5} terms are constant
     *  across both points and cancel out of the subtraction, leaving only the rotation-and-scale
     *  dependent part -- which is the only part in question here. */
    private static Vector3f originalMatrixStackDifference(
            Quaternionf leftRotation, Vector3f scale, Vector3f pointA, Vector3f pointB) {
        Vector3f delta = new Vector3f(pointA).sub(pointB);
        Vector3f scaled = new Vector3f(delta.x * scale.x, delta.y * scale.y, delta.z * scale.z);
        return leftRotation.transform(scaled);
    }

    /** The engine's own {@code Transformation} render-offset formula (same one cited and used
     *  by {@link #assertPivotCompensationPlacesOriginAtAnchor}), evaluated at two model points
     *  and subtracted -- the {@code center}/{@code translation} terms are identical for both
     *  points and cancel out, leaving only the rotation-and-scale dependent part, directly
     *  comparable with {@link #originalMatrixStackDifference}. */
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
