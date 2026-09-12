package dev.longyu.legacyforgebridge.network;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FmlWireCodecTest {
    @Test
    void parsesProtocol2ServerHelloWithDimension() {
        byte[] payload = new byte[]{0, 2, 0, 0, 0, (byte) 0xFF};
        FmlWireCodec.ServerHello hello = FmlWireCodec.parseServerHello(payload);

        assertEquals(2, hello.protocolVersion());
        assertEquals(255, hello.overrideDimension());
        assertEquals(0, hello.trailingBytes());
    }

    @Test
    void encodesClientHelloExactlyLikeFmlHandshakeCodec() {
        assertArrayEquals(new byte[]{1, 2}, FmlWireCodec.encodeClientHello());
    }

    @Test
    void modListRoundTripsLegacyVarIntStrings() {
        Map<String, String> mods = new LinkedHashMap<>();
        mods.put("FML", "7.10.99.99");
        mods.put("Forge", "10.13.4.1614");

        byte[] encoded = FmlWireCodec.encodeModList(mods);
        assertEquals(FmlWireCodec.MOD_LIST, FmlWireCodec.discriminator(encoded));
        assertEquals(mods, FmlWireCodec.parseModList(encoded));
    }

    @Test
    void ackUsesMinusOneDiscriminatorOnWire() {
        byte[] encoded = FmlWireCodec.encodeAck(3);
        assertArrayEquals(new byte[]{(byte) 0xFF, 3}, encoded);
        assertEquals(3, FmlWireCodec.parseAck(encoded));
    }

    @Test
    void parsesRegistryDataAndSubstitutions() {
        byte[] payload = new byte[]{
                3,
                1,
                4, 'F', 'M', 'L', ':',
                42,
                1,
                5, 'b', 'l', 'o', 'c', 'k',
                1,
                4, 'i', 't', 'e', 'm'
        };

        FmlWireCodec.ModIdData data = FmlWireCodec.parseModIdData(payload);
        assertEquals(Map.of("FML:" , 42), data.ids());
        assertEquals(1, data.blockSubstitutions().size());
        assertTrue(data.blockSubstitutions().contains("block"));
        assertEquals(1, data.itemSubstitutions().size());
        assertTrue(data.itemSubstitutions().contains("item"));
        assertEquals(0, data.trailingBytes());
    }
}
