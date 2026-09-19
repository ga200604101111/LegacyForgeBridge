package dev.yinghuang.legacyforgebridge.compat;

/** Registration-time policy: a vanilla identity map is not an identity map for Forge carriers. */
public final class LegacyTerminalBlockRewritePolicy {
    public static final String PROTOCOL = "com.viaversion.viaversion.protocols.v1_21_9to1_21_11.Protocol1_21_9To1_21_11";
    private LegacyTerminalBlockRewritePolicy() { }

    public static boolean maySkip(String protocolClass, boolean blockStateMap, boolean vanillaIdentity) {
        // Registration happens before a legacy session exists. Do not gate registration on the
        // selected server; MappingDataBase's actual per-packet hook remains legacy-session gated.
        return vanillaIdentity && !(blockStateMap && PROTOCOL.equals(protocolClass));
    }
}
