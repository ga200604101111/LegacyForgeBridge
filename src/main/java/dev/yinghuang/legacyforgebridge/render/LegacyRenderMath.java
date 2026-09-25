package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;

/** Legacy MathHelper's float trig table, used by compiled animation expressions. */
public final class LegacyRenderMath {
    private static final float[] SIN = new float[65536];
    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float)Math.sin(i * Math.PI * 2.0 / SIN.length);
    }
    private LegacyRenderMath() { }
    public static float sin(float radians) { return SIN[(int)(radians * 10430.378F) & 65535]; }
    public static float cos(float radians) { return SIN[(int)(radians * 10430.378F + 16384.0F) & 65535]; }

    /**
     * Minecraft 1.21 item transforms finish by shifting model space by -0.5 on every axis because
     * ordinary block/item models live in 0..1 coordinates. Legacy Forge ModelRenderer inventory
     * handlers instead render around their own source origin. Cancel only that modern convention
     * before replaying source-proven legacy translate/rotate/scale operations.
     */
    public static void restoreLegacyModelRendererItemOrigin(PoseStack matrices) {
        matrices.translate(0.5D,0.5D,0.5D);
    }
}
