package dev.yinghuang.legacyforgebridge.convert.api;

public interface LegacyModProfile {
    String id();

    boolean matches(LegacyModMetadata metadata, String sourceHash);

    default void inspect(ConversionContext context) {
    }

    default void configure(ConversionPlan.Builder plan) {
    }
}
