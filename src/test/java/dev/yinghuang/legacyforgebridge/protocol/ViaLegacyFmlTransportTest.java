package dev.yinghuang.legacyforgebridge.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViaLegacyFmlTransportTest {
    @Test
    void clientRegistrationUsesExactForge1710ChannelNames() {
        assertEquals(
                "FML|HS\0FML\0FORGE",
                new String(LegacyFmlWireConstants.clientChannelRegistration(), StandardCharsets.UTF_8)
        );
        assertEquals(0x17, LegacyFmlWireConstants.CUSTOM_PAYLOAD_PACKET_ID);
        assertEquals("FML|HS", LegacyFmlWireConstants.HANDSHAKE_CHANNEL);
        assertEquals("REGISTER", LegacyFmlWireConstants.REGISTER_CHANNEL);
    }
}
