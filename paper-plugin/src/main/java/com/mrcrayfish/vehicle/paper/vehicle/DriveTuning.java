// Built once per tick in VehicleManager.tick from config.yml values.
package com.mrcrayfish.vehicle.paper.vehicle;

/** Per-tick, config-backed driving/fuel tuning. VehicleManager builds ONE instance per
 * server tick from config.yml so every knob is live-editable (config + save + restart)
 * without recompiling, and every vehicle sees identical values within the same tick.
 *
 * fuelIdleRatio      - fraction of energyPerTick burned while idling (engine on, no
 *                      speed, no throttle). 0.1 = the requested 10% idle burn.
 * fuelSpeedWeight    - weight of the speed term in the dynamic fuel load.
 * fuelAccelWeight    - weight of the throttle (acceleration demand) term.
 * fuelFullBurnSpeed  - horizontal speed in blocks/s at which the speed term saturates
 *                      to 1.0. At full throttle AND this speed the load is exactly 1.0.
 * steeringAngleMultiplier - scales every spec maxSteeringAngle (e.g. 1.2 = 20 percent more
 *                      steering lock) without touching LandVehicleSpec. */
public record DriveTuning(
        double globalSpeedLimit,
        double fuelConsumptionFactor,
        double fuelIdleRatio,
        double fuelSpeedWeight,
        double fuelAccelWeight,
        double fuelFullBurnSpeed,
        double steeringAngleMultiplier) {
}
