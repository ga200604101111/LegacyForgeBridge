package dev.longyu.legacyforgebridge.network;

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
        assertEquals(FmlWireCodec.MOD_LIST, FmlWireCodec.discriminator(outbound.get(1)));
        assertEquals("7.10.99.99", FmlWireCodec.parseModList(outbound.get(1)).get("FML"));

        client.handle(FmlWireCodec.encodeModList(Map.of("FML", "7.10.99.99", "Forge", "10.13.4.1614")), outbound::add, trace);
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
    void convertedLegacyModIdentityIsAdvertisedToForgeServer() {
        FmlHandshakeClient client = new FmlHandshakeClient(() -> Map.of("rpgtool1", "1.0"));
        List<byte[]> outbound = new ArrayList<>();

        client.handle(
                new byte[]{0, 2, 0, 0, 0, 0},
                outbound::add,
                FmlConnectionTrace.INSTANCE
        );

        Map<String, String> advertised = FmlWireCodec.parseModList(outbound.get(1));
        assertEquals("7.10.99.99", advertised.get("FML"));
        assertEquals("1.0", advertised.get("rpgtool1"));
    }
}
