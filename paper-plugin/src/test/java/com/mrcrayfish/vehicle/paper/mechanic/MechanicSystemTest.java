package com.mrcrayfish.vehicle.paper.mechanic;

import com.mrcrayfish.vehicle.paper.physics.SurfaceProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Valorile exacte 1-la-1 cu modul (audit PORT_STATUS #2/#3) - nu deviem. */
class MechanicSystemTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void engineTiersMatchTheOriginalMod() {
        assertEquals(1.0F, EngineTier.IRON.powerMultiplier(), EPSILON);
        assertEquals(0.0F, EngineTier.IRON.additionalMaxSpeed(), EPSILON);
        assertEquals(1.1F, EngineTier.GOLD.powerMultiplier(), 1.0E-5F);
        assertEquals(2.0F, EngineTier.GOLD.additionalMaxSpeed(), EPSILON);
        assertEquals(1.2F, EngineTier.DIAMOND.powerMultiplier(), 1.0E-5F);
        assertEquals(5.0F, EngineTier.DIAMOND.additionalMaxSpeed(), EPSILON);
        assertEquals(1.4F, EngineTier.NETHERITE.powerMultiplier(), 1.0E-5F);
        assertEquals(5.0F, EngineTier.NETHERITE.additionalMaxSpeed(), EPSILON);
    }

    @Test
    void wheelTypesMatchTheOriginalMod() {
        // STANDARD: road 1.1 / dirt 1.3 / snow 1.7, base 0.8, slide 0.05
        assertEquals(1.1F, WheelType.STANDARD.surfaceModifier(SurfaceProfile.Category.ROAD), 1.0E-5F);
        assertEquals(1.3F, WheelType.STANDARD.surfaceModifier(SurfaceProfile.Category.DIRT), 1.0E-5F);
        assertEquals(1.7F, WheelType.STANDARD.surfaceModifier(SurfaceProfile.Category.SNOW), 1.0E-5F);
        assertEquals(0.8F, WheelType.STANDARD.baseTraction(), EPSILON);
        assertEquals(0.05F, WheelType.STANDARD.slideTraction(), EPSILON);
        // SPORTS: 0.8 / 1.5 / 2.2, 0.9, 0.15
        assertEquals(0.8F, WheelType.SPORTS.surfaceModifier(SurfaceProfile.Category.ROAD), 1.0E-5F);
        assertEquals(1.5F, WheelType.SPORTS.surfaceModifier(SurfaceProfile.Category.DIRT), 1.0E-5F);
        assertEquals(2.2F, WheelType.SPORTS.surfaceModifier(SurfaceProfile.Category.SNOW), 1.0E-5F);
        assertEquals(0.9F, WheelType.SPORTS.baseTraction(), EPSILON);
        assertEquals(0.15F, WheelType.SPORTS.slideTraction(), EPSILON);
        // OFF_ROAD: 1.2 / 0.9 / 1.1, 0.8, 0.2
        assertEquals(1.2F, WheelType.OFF_ROAD.surfaceModifier(SurfaceProfile.Category.ROAD), 1.0E-5F);
        assertEquals(0.9F, WheelType.OFF_ROAD.surfaceModifier(SurfaceProfile.Category.DIRT), 1.0E-5F);
        assertEquals(1.1F, WheelType.OFF_ROAD.surfaceModifier(SurfaceProfile.Category.SNOW), 1.0E-5F);
        assertEquals(0.8F, WheelType.OFF_ROAD.baseTraction(), EPSILON);
        assertEquals(0.2F, WheelType.OFF_ROAD.slideTraction(), EPSILON);
    }

    @Test
    void cratePayloadRoundTripsSpecialCharacters() {
        String payload = MechanicItems.cratePayload(
                "sports_car", 12000, "DIAMOND", "SPORTS", 100, -1, true,
                "3f3f4eea-0a7b-4c83-b2b1-9e9c1c00abcd");
        String[] parsed = MechanicItems.parseCratePayload(payload);
        assertEquals(8, parsed.length);
        assertEquals("sports_car", parsed[0]);
        assertEquals("-1", parsed[5]);
        assertEquals("true", parsed[6]);
        assertEquals("3f3f4eea-0a7b-4c83-b2b1-9e9c1c00abcd", parsed[7]);
    }

    @Test
    void cratePayloadFromOldVersionsLacksFieldsGracefully() {
        String[] parsed = MechanicItems.parseCratePayload("go_kart|5000");
        assertArrayEquals(new String[]{"go_kart", "5000"}, parsed);
    }

    @Test
    void paintPaletteHas16DistinctRgbColors() {
        assertEquals(16, PaintColors.values().length);
        long distinct = java.util.Arrays.stream(PaintColors.values())
                .map(PaintColors::rgb).distinct().count();
        assertEquals(distinct, 16);
    }

    @Test
    void engineTypeNamesRoundTrip() {
        assertTrue(EngineType.fromName("small_motor") == EngineType.SMALL_MOTOR);
        assertTrue(EngineType.fromName("gibberish") == EngineType.NONE);
        assertTrue(WheelType.fromName("off_road") == WheelType.OFF_ROAD);
        assertTrue(WheelType.fromName(null) == WheelType.STANDARD);
        assertTrue(EngineTier.fromName("netherite") == EngineTier.NETHERITE);
        assertTrue(EngineTier.fromName(null) == EngineTier.IRON);
    }
}
