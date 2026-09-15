package dev.longyu.legacyforgebridge.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySessionControllerTest {
    @AfterEach
    void reset() {
        LegacySessionController.reset();
    }

    @Test
    void protocolFiveActivatesLegacy1710State() {
        LegacySessionController.onTargetProtocolChanged(5, "1.7.6-1.7.10");

        assertTrue(LegacySessionController.isLegacy1710());
        assertEquals(LegacySessionController.State.LEGACY_1_7_10, LegacySessionController.state());
        assertEquals(5, LegacySessionController.targetProtocolId());
    }

    @Test
    void modernProtocolRestoresModernState() {
        LegacySessionController.onTargetProtocolChanged(5, "1.7.6-1.7.10");
        LegacySessionController.onTargetProtocolChanged(47, "1.8.x");

        assertFalse(LegacySessionController.isLegacy1710());
        assertEquals(LegacySessionController.State.MODERN, LegacySessionController.state());
        assertEquals(47, LegacySessionController.targetProtocolId());
    }
}
