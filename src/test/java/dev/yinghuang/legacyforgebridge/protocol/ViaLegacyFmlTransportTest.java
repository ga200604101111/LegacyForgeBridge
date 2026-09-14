package dev.yinghuang.legacyforgebridge.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViaLegacyFmlTransportTest {
    @Test
    void clientRegistrationUsesModernAliasesThatViaVersionCanReverseMap() {
        assertEquals(
                "legacyforgebridge:fml_hs\0legacyforgebridge:fml\0legacyforgebridge:forge",
                new String(ViaLegacyFmlTransport.clientChannelRegistrationForTest(), StandardCharsets.UTF_8)
        );
    }
}
