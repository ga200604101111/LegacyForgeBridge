package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FmlHandshakeClientTest {
    @Test
    void cleanRemoteForgeHandshakeReachesComplete() {
        FmlHandshakeClient client = new FmlHandshakeClient(Map::of);
        List<byte[]> outbound = new ArrayList<>();
        FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;

        client.handle(new byte[]{0, 2, 0, 0, 0, 0}, outbound::add, trace);
        assertEquals(FmlHandshakeClient.State.WAITING_SERVER_MOD_LIST, client.state());
        assertEquals(2, outbound.size());
        assertArrayEquals(new byte[]{1, 2}, outbound.get(0));
        Map<String, String> advertised = FmlWireCodec.parseModList(outbound.get(1));
        assertEquals("9.05", advertised.get("mcp"));
        assertEquals("7.10.99.99", advertised.get("FML"));
        assertEquals("10.13.4.1614", advertised.get("Forge"));

        client.handle(FmlWireCodec.encodeModList(Map.of(
                "mcp", "9.05", "FML", "7.10.99.99", "Forge", "10.13.4.1614")),
                outbound::add, trace);
        assertEquals(FmlHandshakeClient.State.WAITING_REGISTRY_DATA, client.state());
        assertArrayEquals(new byte[]{(byte) 0xFF, 2}, outbound.get(2));

        client.handle(new byte[]{3, 0, 0, 0}, outbound::add, trace);
        assertEquals(FmlHandshakeClient.State.WAITING_SERVER_ACK_AFTER_REGISTRY, client.state());
        assertArrayEquals(new byte[]{(byte) 0xFF, 3}, outbound.get(3));

        client.handle(new byte[]{(byte) 0xFF, 2}, outbound::add, trace);
        assertEquals(FmlHandshakeClient.State.WAITING_FINAL_SERVER_ACK, client.state());
        assertArrayEquals(new byte[]{(byte) 0xFF, 4}, outbound.get(4));

        client.handle(new byte[]{(byte) 0xFF, 3}, outbound::add, trace);
        assertEquals(FmlHandshakeClient.State.COMPLETE, client.state());
        assertArrayEquals(new byte[]{(byte) 0xFF, 5}, outbound.get(5));
    }

    @Test
    void convertedLegacyModIdentityIsAdvertisedWithoutReplacingForgeBuiltins() {
        FmlHandshakeClient client = new FmlHandshakeClient(() -> Map.of(
                "rpgtool1", "1.0",
                "BambooMod", "Minecraft1.7.10 ver2.6.8.5",
                "Forge", "wrong"));
        List<byte[]> outbound = new ArrayList<>();

        client.handle(new byte[]{0, 2, 0, 0, 0, 0}, outbound::add, FmlConnectionTrace.INSTANCE);
        Map<String, String> advertised = FmlWireCodec.parseModList(outbound.get(1));
        assertEquals("9.05", advertised.get("mcp"));
        assertEquals("7.10.99.99", advertised.get("FML"));
        assertEquals("10.13.4.1614", advertised.get("Forge"));
        assertEquals("1.0", advertised.get("rpgtool1"));
        assertEquals("Minecraft1.7.10 ver2.6.8.5", advertised.get("BambooMod"));
    }
}
