package dev.yinghuang.legacyforgebridge.behavior;

/** Restores visible translucency for model-rendered source-proven WATER liquids. */
public final class Rev237LiquidAlpha {
    private static final int LEGACY_WATER_ALPHA = 0xA0;

    private Rev237LiquidAlpha() { }

    public static Object apply(Object color) {
        if (!(color instanceof Number number)) return color;
        int rgb = number.intValue();
        if (rgb == -1) return color;
        return Integer.valueOf((LEGACY_WATER_ALPHA << 24) | (rgb & 0x00FFFFFF));
    }
}
