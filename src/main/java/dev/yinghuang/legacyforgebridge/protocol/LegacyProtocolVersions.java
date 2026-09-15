package dev.yinghuang.legacyforgebridge.protocol;

/** Protocol constants that do not depend on a specific protocol translator implementation. */
public final class LegacyProtocolVersions {
    /** Minecraft 1.7.6 through 1.7.10 share protocol id 5. */
    public static final int MINECRAFT_1_7_10_PROTOCOL_ID = 5;

    private LegacyProtocolVersions() {
    }

    public static boolean isMinecraft1710(int protocolId) {
        return protocolId == MINECRAFT_1_7_10_PROTOCOL_ID;
    }
}
