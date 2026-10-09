package dev.yinghuang.legacyforgebridge;
/** Rev308 client behavior and presentation bridge; converter cache identity stays at rev306. */
public final class BuildInfo {
    public static final String VERSION = text("0.2.0-alpha.27-corpus4-local.61-rev308-twilight-sword-arrow-equipment.1");
    public static final int CONVERSION_SCHEMA = number(2);
    public static final String CONVERTER_REVISION = text("2026-10-09.306-source-bow-presentation-proof-alpha");
    public static final String CACHE_COMPATIBILITY_VERSION =
            text("0.2.0-alpha.27-corpus4-local.17-rev233-cache.1");
    private BuildInfo() {}
    private static String text(String s) {return s;}
    private static int number(int n) {return n;}
}
