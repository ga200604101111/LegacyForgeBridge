package dev.yinghuang.legacyforgebridge.runtime;

/** Wire/container invariants shared by runtime dispatch and pure-Java regression tests. */
public final class LegacyRuntimeGuards {
    private LegacyRuntimeGuards() { }
    public static void requireWindow(int windowId) {
        if (windowId <= 0 || windowId > 255) throw new IllegalArgumentException("Invalid legacy container window ID: " + windowId);
    }
    public static void requireMatchingWindow(int requested, int actual) {
        requireWindow(requested);
        if (actual != requested) throw new IllegalArgumentException("GUI adapter returned wrong window ID");
    }
}
