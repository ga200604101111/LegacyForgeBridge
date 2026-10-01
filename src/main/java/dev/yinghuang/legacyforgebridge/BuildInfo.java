package dev.yinghuang.legacyforgebridge;

public final class BuildInfo {
    public static final String VERSION = text("0.2.0-alpha.27-corpus4-local.22-rev238-local-test.1");
    public static final int CONVERSION_SCHEMA = number(2);
    public static final String CONVERTER_REVISION =
            text("2026-10-01.238-compact-progress-window-layout");
    public static final String CACHE_COMPATIBILITY_VERSION =
            text("0.2.0-alpha.27-corpus4-local.17-rev233-cache.1");

    private BuildInfo() { }
    private static String text(String value) { return value; }
    private static int number(int value) { return value; }
}
