package dev.yinghuang.legacyforgebridge.compat;

/** Implemented only by converter-generated plain Entity subclasses that can accept proven legacy watcher values. */
public interface LegacyPlainEntityWatcherBridge {
    boolean legacyforgebridge$applyWatcher(int legacyIndex, int legacyType, Object value);
}
