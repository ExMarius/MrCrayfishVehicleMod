package com.mrcrayfish.vehicle.paper.render;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure matrix equivalents of the original 1.16 land-vehicle renderer.
 *
 * <p>The source {@code MatrixStack} post-multiplies in call order. JOML's
 * quaternion helpers use the same composition convention, so keeping these
 * equations in one testable class prevents Paper display transforms from
 * silently changing pivot or rotation order.</p>
 */
final class SourceTransforms {
    private SourceTransforms() {
    }

    /** Source order: motorcycle roll about the entity root, then wheelie about the rear axle. */
    static Quaternionf chassisRotation(float wheelieAngle, float bodyRoll) {
        return new Quaternionf()
                .rotateZ(radians(bodyRoll))
                .rotateX(radians(wheelieAngle));
    }

    static Vector3f chassisPoint(Vector3f point, Vector3f wheeliePivot,
                                 float wheelieAngle, float bodyRoll) {
        Vector3f transformed = new Vector3f(point);
        if (wheelieAngle != 0.0F) {
            transformed.sub(wheeliePivot);
            new Quaternionf().rotateX(radians(wheelieAngle)).transform(transformed);
            transformed.add(wheeliePivot);
        }
        if (bodyRoll != 0.0F) {
            new Quaternionf().rotateZ(radians(bodyRoll)).transform(transformed);
        }
        return transformed;
    }

    /** DirtBikeRenderer: X(-22.5), steering Y, then X(+22.5). */
    static Quaternionf motorcycleSteering(LandVehicleSpec.Motorcycle motorcycle,
                                          float steeringRotation) {
        if (motorcycle == null) {
            return new Quaternionf();
        }
        return new Quaternionf()
                .rotateX(radians(motorcycle.steeringAxisTilt()))
                .rotateY(radians(steeringRotation))
                .rotateX(radians(-motorcycle.steeringAxisTilt()));
    }

    static Vector3f forkPoint(Vector3f unsteered, Vector3f bodyOrigin,
                              LandVehicleSpec.Motorcycle motorcycle,
                              Quaternionf forkRotation) {
        if (motorcycle == null) {
            return new Vector3f(unsteered);
        }
        Vector3f pivot = new Vector3f(bodyOrigin).add(0.0F, 0.0F, motorcycle.steeringPivotZ());
        Vector3f relative = new Vector3f(unsteered).sub(pivot);
        forkRotation.transform(relative);
        return relative.add(pivot);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
