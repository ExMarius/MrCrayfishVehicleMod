package com.mrcrayfish.vehicle.paper.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleListenerTest {
    @Test
    void blocksEntityWideKillCommandsThatCanRemoveSeatCarriers() {
        assertTrue(VehicleListener.unsafeGlobalKill("/kill @e"));
        assertTrue(VehicleListener.unsafeGlobalKill("kill @e[type=minecraft:horse]"));
        assertTrue(VehicleListener.unsafeGlobalKill("minecraft:kill @e[distance=..10]"));
        assertTrue(VehicleListener.unsafeGlobalKill("execute at @s run kill @e[type=horse]"));
        assertTrue(VehicleListener.unsafeGlobalKill(
                "execute as @e[tag=!mcv_plugin_vehicle] run kill @e"));
    }

    @Test
    void allowsSafeTargetsAndExplicitVehicleTagExclusion() {
        assertFalse(VehicleListener.unsafeGlobalKill("/kill @a"));
        assertFalse(VehicleListener.unsafeGlobalKill("/kill SomePlayer"));
        assertFalse(VehicleListener.unsafeGlobalKill(
                "/kill @e[type=minecraft:horse,tag=!mcv_plugin_vehicle]"));
    }
}
