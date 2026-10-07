package com.mrcrayfish.vehicle.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackSenderTest {
    private static final String PACK = "https://example.invalid/vehicle-r17.zip";

    @Test
    void identicalServerPackSuppressesTheDuplicatePostJoinSend() {
        assertTrue(ResourcePackSender.samePack(PACK, PACK));
        assertTrue(ResourcePackSender.samePack("  " + PACK, PACK + "  "));
    }

    @Test
    void missingOrDifferentServerPackKeepsPluginFallbackEnabled() {
        assertFalse(ResourcePackSender.samePack(PACK, ""));
        assertFalse(ResourcePackSender.samePack(PACK, null));
        assertFalse(ResourcePackSender.samePack("", PACK));
        assertFalse(ResourcePackSender.samePack(PACK, "https://example.invalid/other.zip"));
    }
}
