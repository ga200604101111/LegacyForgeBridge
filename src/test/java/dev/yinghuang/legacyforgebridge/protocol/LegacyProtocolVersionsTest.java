package dev.longyu.legacyforgebridge.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyProtocolVersionsTest {
    @Test
    void protocolFiveIsMinecraft1710Family() {
        assertTrue(LegacyProtocolVersions.isMinecraft1710(5));
        assertFalse(LegacyProtocolVersions.isMinecraft1710(4));
        assertFalse(LegacyProtocolVersions.isMinecraft1710(47));
        assertFalse(LegacyProtocolVersions.isMinecraft1710(-1));
    }
}
