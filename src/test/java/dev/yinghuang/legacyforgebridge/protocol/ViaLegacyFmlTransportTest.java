package dev.yinghuang.legacyforgebridge.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViaLegacyFmlTransportTest {
    @Test
    void clientRegistrationUsesExactForge1710ChannelNames() {
        assertEquals(
                "FML|HS\0FML\0FORGE",
                new String(ViaLegacyFmlTransport.legacyClientChannelRegistrationForTest(), StandardCharsets.UTF_8)
        );
        assertEquals(0x17, ViaLegacyFmlTransport.legacyCustomPayloadPacketIdForTest());
    }
}
