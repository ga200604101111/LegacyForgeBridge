package dev.longyu.legacyforgebridge.convert.api;

public interface ConversionPass {
    String id();

    void apply(ConversionContext context) throws Exception;
}
