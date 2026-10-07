package com.mrcrayfish.vehicle.paper.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleListenerTest {
    @Test
    void protectsBareEntityWideKillCommands() {
        assertEquals(
                "/kill @e[tag=!mcv_plugin_vehicle,type=!minecraft:player]",
                VehicleListener.protectEntityKill("/kill @e")
        );
        assertEquals(
                "minecraft:kill @e[distance=..10,tag=!mcv_plugin_vehicle,type=!minecraft:player]",
                VehicleListener.protectEntityKill("minecraft:kill @e[distance=..10]")
        );
    }

    @Test
    void preservesFiltersAndProtectsOnlyTheKillTarget() {
        assertEquals(
                "kill @e[type=minecraft:horse,tag=!mcv_plugin_vehicle,type=!minecraft:player]",
                VehicleListener.protectEntityKill("kill @e[type=minecraft:horse]")
        );
        assertEquals(
                "execute as @e[tag=!mcv_plugin_vehicle] run kill "
                        + "@e[tag=!mcv_plugin_vehicle,type=!minecraft:player]",
                VehicleListener.protectEntityKill(
                        "execute as @e[tag=!mcv_plugin_vehicle] run kill @e")
        );
    }

    @Test
    void leavesSafeOrMalformedCommandsUntouched() {
        assertEquals("/kill @a", VehicleListener.protectEntityKill("/kill @a"));
        assertEquals("/kill SomePlayer", VehicleListener.protectEntityKill("/kill SomePlayer"));
        assertEquals(
                "/kill @e[tag=!mcv_plugin_vehicle,type=!minecraft:player]",
                VehicleListener.protectEntityKill(
                        "/kill @e[tag=!mcv_plugin_vehicle,type=!minecraft:player]")
        );
        assertEquals(
                "/kill @e[type=minecraft:horse",
                VehicleListener.protectEntityKill("/kill @e[type=minecraft:horse")
        );
    }
}
