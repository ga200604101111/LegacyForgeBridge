package dev.yinghuang.legacyforgebridge.convert;

import java.util.Properties;

/** Exact two-release alias backed by the checkpoint's binary comparison; never bypasses artifact hashes. */
public final class Rev247CacheCompatibility {
    public static final String CANONICAL_REVISION="2026-10-03.245-deep-motion-correlation-diagnostics";
    private static final String UI_REVISION="2026-10-03.246-desktop-status-support-window";
    private static final String PREFIX="0.2.0-alpha.27-corpus4-local.17-rev233-cache.1:schema=2:revision=";
    private Rev247CacheCompatibility() {}
    public static String normalizeFingerprint(String value) {
        if(value==null)return null;
        String alias=PREFIX+UI_REVISION+":source=";
        if(!value.startsWith(alias))return value;
        String hash=value.substring(alias.length());
        if(!hash.matches("[0-9a-f]{64}"))return value;
        return PREFIX+CANONICAL_REVISION+":source="+hash;
    }
    public static Properties normalizeCache(Properties values) {
        // This is an in-memory read adapter, not a persistent cache rewrite.
        Properties copy=new Properties();copy.putAll(values);
        for(String key:values.stringPropertyNames())copy.setProperty(key,normalizeFingerprint(values.getProperty(key)));
        return copy;
    }
    public static boolean equivalentRevision(String expected,String actual) {
        if(expected==null||actual==null)return false;
        return expected.equals(actual) || (CANONICAL_REVISION.equals(expected)&&UI_REVISION.equals(actual));
    }
}
