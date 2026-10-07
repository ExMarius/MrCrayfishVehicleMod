package com.mrcrayfish.vehicle.paper.runtime;

import com.mrcrayfish.vehicle.paper.vehicle.LandVehicleSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineSoundControllerTest {
    @Test
    void replayCadenceUsesActualSourceSampleDurationAndPitch() {
        assertEquals(81.7009D,
                EngineSoundController.replayTicks("vehicle:entity.go_kart.engine", 0.9F), 0.001D);
        assertEquals(36.2154D,
                EngineSoundController.replayTicks("vehicle:entity.go_kart.engine", 2.0F), 0.001D);
        assertEquals(7.0453D,
                EngineSoundController.replayTicks("vehicle:entity.quad_bike.engine", 0.5F), 0.001D);
        assertEquals(2.2181D,
                EngineSoundController.replayTicks("vehicle:entity.quad_bike.engine", 1.25F), 0.001D);
        assertEquals(45.0424D,
                EngineSoundController.replayTicks("vehicle:entity.tractor.engine", 0.8F), 0.001D);
        assertEquals(22.0212D,
                EngineSoundController.replayTicks("vehicle:entity.tractor.engine", 1.6F), 0.001D);
        assertEquals(28.7775D,
                EngineSoundController.replayTicks("vehicle:entity.dirt_bike.engine", 0.85F), 0.001D);
        assertEquals(15.8739D,
                EngineSoundController.replayTicks("vehicle:entity.dirt_bike.engine", 1.5F), 0.001D);
        assertEquals(39.1488D,
                EngineSoundController.replayTicks("vehicle:entity.moped.engine", 0.5F), 0.001D);
        assertEquals(15.7286D,
                EngineSoundController.replayTicks("vehicle:entity.moped.engine", 1.2F), 0.001D);
        assertEquals(25.3231D,
                EngineSoundController.replayTicks("vehicle:entity.jet_ski.engine", 0.8F), 0.001D);
        assertEquals(12.1616D,
                EngineSoundController.replayTicks("vehicle:entity.jet_ski.engine", 1.6F), 0.001D);
        assertEquals(12.7370D,
                EngineSoundController.replayTicks("vehicle:entity.sports_car.engine", 0.9F), 0.001D);
        assertEquals(7.2422D,
                EngineSoundController.replayTicks("vehicle:entity.sports_car.engine", 1.5F), 0.001D);
        assertEquals(62.1167D,
                EngineSoundController.replayTicks("vehicle:entity.mini_bus.engine", 0.75F), 0.001D);
        assertEquals(36.8700D,
                EngineSoundController.replayTicks("vehicle:entity.mini_bus.engine", 1.25F), 0.001D);
        assertEquals(24.3261D,
                EngineSoundController.replayTicks(
                        "vehicle:entity.vehicle.helicopter_rotor", 0.5F), 0.001D);
        assertEquals(11.6630D,
                EngineSoundController.replayTicks(
                        "vehicle:entity.vehicle.helicopter_rotor", 1.0F), 0.001D);
    }

    @Test
    void sourcePitchTracksSpeedChargeAndSlideModes() {
        LandVehicleSpec spec = LandVehicleSpec.GO_KART;
        assertEquals(0.9F, EngineSoundController.targetPitch(spec, 0.0D,
                false, 0.0F, false, false, 0.0F, false), 0.0001F);
        assertEquals(2.0F, EngineSoundController.targetPitch(spec, 25.0D,
                false, 0.0F, false, false, 0.0F, false), 0.0001F);
        assertEquals(1.725F, EngineSoundController.targetPitch(spec, 0.0D,
                true, 1.0F, false, false, 1.0F, true), 0.0001F);
        assertEquals(2.0F, EngineSoundController.targetPitch(spec, 1.0D,
                false, 0.0F, true, false, 1.0F, false), 0.0001F);
        assertTrue(EngineSoundController.replayTicks(spec.engineSound(), spec.minEnginePitch()) > 1.0D);
    }
}
