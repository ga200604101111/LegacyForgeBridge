package dev.yinghuang.legacyforgebridge.behavior;

/** TEST DOUBLE ONLY. In-memory record sink; does NOT test disk I/O, rotation or drops. */
final class Rev243TraceWriter {
    final java.util.List<String> records = new java.util.ArrayList<>();
    private boolean accepting = true;
    Rev243TraceWriter(java.nio.file.Path directory, String prefix, String metadata,
                      java.util.function.Consumer<String> notices) {}
    boolean accepting() { return accepting; }
    boolean isClosed() { return !accepting; }
    void requestClose(String reason) { accepting = false; }
    boolean awaitClosed(long millis) throws InterruptedException {return true;}
    void offer(String record) { if (accepting) records.add(record); }
    String stats() { return "testDoubleWriter=true records="+records.size(); }
}
