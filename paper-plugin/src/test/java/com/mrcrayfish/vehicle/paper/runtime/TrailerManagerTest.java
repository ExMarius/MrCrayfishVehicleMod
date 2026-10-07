package com.mrcrayfish.vehicle.paper.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerManagerTest {
    @Test
    void emptyHandsReceiveAJetSkiPlacementInputToken() {
        assertTrue(TrailerManager.needsWaterPlacementToken(true, true));
        assertFalse(TrailerManager.needsWaterPlacementToken(false, true));
        assertFalse(TrailerManager.needsWaterPlacementToken(true, false));
        assertFalse(TrailerManager.needsWaterPlacementToken(false, false));
    }

    @Test
    void playerCannotClaimASecondDistinctTrailer() {
        UUID held = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(TrailerManager.canPlayerClaimTrailer(null, second));
        assertTrue(TrailerManager.canPlayerClaimTrailer(held, held));
        assertFalse(TrailerManager.canPlayerClaimTrailer(held, second));
    }

    @Test
    void onlyTheMapOwnedTrailerCanResolveAPlayerPullLink() {
        UUID tracked = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();

        assertTrue(TrailerManager.ownsPlayerPullLink(tracked, tracked));
        assertFalse(TrailerManager.ownsPlayerPullLink(tracked, orphan));
        assertFalse(TrailerManager.ownsPlayerPullLink(null, orphan));
    }
}
