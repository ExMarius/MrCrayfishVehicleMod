package com.mrcrayfish.vehicle.paper.vehicle;

import org.bukkit.Input;

/** A server-side snapshot of the normal movement keys sent by a vanilla client. */
public record VehicleInput(
        boolean forward,
        boolean backward,
        boolean left,
        boolean right,
        boolean handbrake,
        boolean sneak,
        boolean sprint
) {
    public static VehicleInput from(Input input) {
        return new VehicleInput(
                input.isForward(),
                input.isBackward(),
                input.isLeft(),
                input.isRight(),
                input.isJump(),
                input.isSneak(),
                input.isSprint()
        );
    }

    public static VehicleInput idle() {
        return new VehicleInput(false, false, false, false, false, false, false);
    }

    public float throttle() {
        if (forward == backward) {
            return 0.0F;
        }
        return forward ? 1.0F : -1.0F;
    }

    /** Matches the sign used by VehicleHelper#getSteeringAngle in the Forge mod. */
    public float steeringDirection() {
        if (left == right) {
            return 0.0F;
        }
        return left ? 1.0F : -1.0F;
    }
}
