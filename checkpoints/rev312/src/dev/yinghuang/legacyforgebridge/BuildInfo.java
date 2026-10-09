package dev.yinghuang.legacyforgebridge;
/** rev312 performance-only overlay; no converter output semantics or render changes. */
public final class BuildInfo {
    public static final String VERSION = text("0.2.0-alpha.27-corpus4-local.66-rev312-shared-source-registry.1");
    public static final int CONVERSION_SCHEMA = number(2);
    public static final String CONVERTER_REVISION = text("2026-10-09.310-pre-atlas-critter-source-flat-icon");
    public static final String CACHE_COMPATIBILITY_VERSION = text("0.2.0-alpha.27-corpus4-local.17-rev233-cache.1");
    private BuildInfo() { }
    private static String text(String s) { return s; }
    private static int number(int n) { return n; }
}
