package dev.longyu.legacyforgebridge;

public final class BuildInfo {
    public static final String VERSION = "0.2.0-alpha.27";

    /** Bump whenever the conversion output contract changes, even before public versioning moves. */
    public static final int CONVERSION_SCHEMA = 2;

    /** Cache identity for analyzer/compiler/materializer behavior that can change candidate bytes. */
    public static final String CONVERTER_REVISION = "2026-09-15.5";

    private BuildInfo() { }
}
