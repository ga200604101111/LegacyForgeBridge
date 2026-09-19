package dev.yinghuang.legacyforgebridge.compat;

/** Unsigned legacy data selection, independent of Minecraft bootstrap and Via serializers. */
public final class LegacyStackMetadataPolicy {
    private LegacyStackMetadataPolicy() { }
    public static int select(Integer nativeMeta, Integer damage, Integer carriedMeta, boolean damageable) {
        int value = damageable && damage != null ? damage
                : nativeMeta != null ? nativeMeta : carriedMeta != null ? carriedMeta
                : damage != null ? damage : 0;
        return require(value);
    }
    public static int require(int value) {
        if (value < 0 || value > 65535) throw new IllegalArgumentException("Legacy metadata outside unsigned-short range: " + value);
        return value;
    }
}
