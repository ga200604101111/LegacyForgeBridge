package dev.yinghuang.legacyforgebridge;

public final class BuildInfo {
    /*
     * Keep these as runtime-initialized final fields rather than Java compile-time constants.
     * Converter hotfixes may replace BuildInfo independently; compile-time constants would be
     * copied into every caller's bytecode and could leave cache fingerprints on an older revision.
     * A clean build therefore always observes the active converter fingerprint through the field.
     */
    public static final String VERSION = text("0.2.0-alpha.27");
    public static final int CONVERSION_SCHEMA = number(2);
    public static final String CONVERTER_REVISION = text("2026-09-24.188-resource-case-fingerprint");

    private BuildInfo() { }

    private static String text(String value) { return value; }
    private static int number(int value) { return value; }
}
