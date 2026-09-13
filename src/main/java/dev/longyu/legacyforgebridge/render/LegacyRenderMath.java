package dev.longyu.legacyforgebridge.render;

/** Legacy MathHelper's float trig table, used by compiled animation expressions. */
public final class LegacyRenderMath {
    private static final float[] SIN = new float[65536];
    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float)Math.sin(i * Math.PI * 2.0 / SIN.length);
    }
    private LegacyRenderMath() { }
    public static float sin(float radians) { return SIN[(int)(radians * 10430.378F) & 65535]; }
    public static float cos(float radians) { return SIN[(int)(radians * 10430.378F + 16384.0F) & 65535]; }
}
