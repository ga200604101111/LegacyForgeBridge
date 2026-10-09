package dev.yinghuang.legacyforgebridge;

/** Rev307 interface-only increment: converter semantics/cache identity are unchanged from rev306. */
public final class BuildInfo {
    public static final String VERSION = text("0.2.0-alpha.27-corpus4-local.59-rev307-legacy-config-menu.1");
    public static final int CONVERSION_SCHEMA = number(2);
    public static final String CONVERTER_REVISION = text("2026-10-09.306-source-bow-presentation-proof-alpha");
    public static final String CACHE_COMPATIBILITY_VERSION =
            text("0.2.0-alpha.27-corpus4-local.17-rev233-cache.1");
    private BuildInfo() { }
    private static String text(String s) {return s;}
    private static int number(int n) {return n;}
}
