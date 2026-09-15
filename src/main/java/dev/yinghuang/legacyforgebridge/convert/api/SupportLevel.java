package dev.yinghuang.legacyforgebridge.convert.api;

/**
 * Describes how a conversion rule is satisfied.
 */
public enum SupportLevel {
    AUTO,
    ADAPTED,
    RUNTIME_BRIDGE,
    MANUAL_REQUIRED,
    UNSUPPORTED
}
