package dev.yinghuang.legacyforgebridge.protocol;

import java.nio.charset.StandardCharsets;

/** Dependency-free Forge 1.7.10 wire constants shared by transport and unit tests. */
final class LegacyFmlWireConstants {
    static final int CUSTOM_PAYLOAD_PACKET_ID = 0x17;
    static final String HANDSHAKE_CHANNEL = "FML|HS";
    static final String REGISTER_CHANNEL = "REGISTER";

    private static final byte[] CLIENT_CHANNEL_REGISTRATION = String.join(
            "\0", "FML|HS", "FML", "FORGE"
    ).getBytes(StandardCharsets.UTF_8);

    private LegacyFmlWireConstants() { }

    static byte[] clientChannelRegistration() {
        return CLIENT_CHANNEL_REGISTRATION.clone();
    }
}
