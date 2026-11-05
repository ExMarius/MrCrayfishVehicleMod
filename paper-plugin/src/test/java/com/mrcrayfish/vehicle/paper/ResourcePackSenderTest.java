package com.mrcrayfish.vehicle.paper;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackSenderTest {
    private static final String PACK = "https://example.invalid/vehicle-r21.zip";

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
        assertFalse(ResourcePackSender.samePack("https://example.invalid/other.zip", PACK));
    }

    @Test
    void matchingServerPackHashAlsoSuppressesTheDuplicatePostJoinSend() {
        assertTrue(ResourcePackSender.sameSha("c0138a8e4732f183f2ccc443895bb4d70cd8e139",
                "c0138a8e4732f183f2ccc443895bb4d70cd8e139"));
        assertTrue(ResourcePackSender.sameSha(" C0138A8E4732F183F2CCC443895BB4D70CD8E139 ",
                "c0138a8e4732f183f2ccc443895bb4d70cd8e139"));
        assertFalse(ResourcePackSender.sameSha("c0138a8e4732f183f2ccc443895bb4d70cd8e139", ""));
        assertFalse(ResourcePackSender.sameSha("c0138a8e4732f183f2ccc443895bb4d70cd8e139", null));
        assertFalse(ResourcePackSender.sameSha("", "c0138a8e4732f183f2ccc443895bb4d70cd8e139"));
        assertFalse(ResourcePackSender.sameSha("63985057d203c9ba067d95ab74b06bd243d25c97",
                "c0138a8e4732f183f2ccc443895bb4d70cd8e139"));
    }

    @Test
    void pluginSentPackCarriesTheSameUuidAsTheLoginTimeServerPack() {
        // Cross-checked against Python's uuid.uuid5(uuid.NAMESPACE_URL, PACK):
        // configure_server_resource_pack.py writes this exact id into
        // server.properties as resource-pack-id, so pushing the same UUID from the
        // plugin makes the client deduplicate instead of reloading the pack twice.
        assertEquals(UUID.fromString("dfbcb220-6b04-5dcd-aba3-fcda76a73981"),
                ResourcePackSender.serverPackId(PACK));
    }
}
