package dev.longyu.legacyforgebridge.compat;

/** Pure decision logic for the local-player authenticated skin fallback. */
public final class LegacyLocalPlayerSkinPolicy {
    private LegacyLocalPlayerSkinPolicy() {
    }

    public static boolean shouldUseAuthenticatedProfile(
            boolean profileIdMatches,
            int sessionTextureCount,
            int authenticatedTextureCount
    ) {
        return authenticatedTextureCount > 0 && (!profileIdMatches || sessionTextureCount == 0);
    }
}
