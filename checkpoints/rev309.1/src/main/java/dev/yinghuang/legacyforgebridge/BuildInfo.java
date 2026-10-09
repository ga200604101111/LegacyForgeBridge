package dev.yinghuang.legacyforgebridge;
/** Rev309.1: model resource identifier correction; triggers a cache rebuild. */
public final class BuildInfo {
    public static final String VERSION = text("0.2.0-alpha.27-corpus4-local.63-rev309.1-critter-identifier-fix.1");
    public static final int CONVERSION_SCHEMA = number(2);
    public static final String CONVERTER_REVISION = text("2026-10-09.309.1-critter-texture-identifier-fix");
    public static final String CACHE_COMPATIBILITY_VERSION =
            text("0.2.0-alpha.27-corpus4-local.17-rev233-cache.1");
    private BuildInfo() {}
    private static String text(String s) {return s;}
    private static int number(int n) {return n;}
}
