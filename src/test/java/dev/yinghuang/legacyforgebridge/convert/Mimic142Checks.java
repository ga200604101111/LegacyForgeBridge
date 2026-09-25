package dev.yinghuang.legacyforgebridge.convert;

import dev.yinghuang.legacyforgebridge.compat.LegacyMimicReadWindow;
import dev.yinghuang.legacyforgebridge.compat.LegacyMimicRefreshQueue;
import dev.yinghuang.legacyforgebridge.compat.LegacyMimicRefreshQueue.Section;
import dev.yinghuang.legacyforgebridge.compat.LegacyMimicResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Standalone production-logic checks, also executed individually by the ordinary JUnit suite. */
public final class Mimic142Checks {
    private Mimic142Checks() { }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void everyDirection() {
        for (int direction = 0; direction < 6; direction++) {
            final int face = direction;
            Integer target = LegacyMimicResolver.resolve(0, p -> p == 0 ? face : -1,
                    (p, d) -> { check(d == face, "Direction changed"); return 1; }, 2, p -> true);
            check(Integer.valueOf(1).equals(target), "All six faces resolve");
        }
    }
    public static void terminalAndNullStart() {
        check(Integer.valueOf(5).equals(LegacyMimicResolver.resolve(5, p -> -1, (p, d) -> null, 1)), "Terminal");
        check(LegacyMimicResolver.resolve(null, p -> { throw new AssertionError("Null read"); },
                (p, d) -> p, 1) == null, "Null start");
    }
    public static void invalidDirections() {
        for (Integer direction : new Integer[]{null, -2, -3, 6, Integer.MAX_VALUE}) {
            check(LegacyMimicResolver.resolve(0, p -> direction,
                    (p, d) -> { throw new AssertionError("Invalid face stepped"); }, 256) == null, "Unresolved face");
        }
    }
    public static void cyclesDoNotReread() {
        List<Integer> read = new ArrayList<>();
        check(LegacyMimicResolver.resolve(0, p -> { read.add(p); return 0; }, (p, d) -> 1 - p, 256) == null, "Cycle");
        check(read.equals(List.of(0, 1)), "Cycle nodes read once");
    }
    public static void budgetBoundary() {
        check(Integer.valueOf(255).equals(LegacyMimicResolver.resolve(0, p -> p == 255 ? -1 : 0,
                (p, d) -> p + 1, 256)), "256th inspected node admitted");
        check(LegacyMimicResolver.resolve(0, p -> p == 256 ? -1 : 0,
                (p, d) -> p + 1, 256) == null, "257th node excluded");
        check(LegacyMimicResolver.resolve(0, p -> 0, (p, d) -> 1, 1) == null, "Budget one");
    }
    public static void invalidBudgets() {
        invalid(() -> LegacyMimicResolver.resolve(0, p -> -1, (p, d) -> p, 0));
        invalid(() -> LegacyMimicResolver.resolve(0, p -> -1, (p, d) -> p, 257));
    }
    public static void guardPrecedesEveryRead() {
        List<Integer> read = new ArrayList<>();
        check(LegacyMimicResolver.resolve(0, p -> {
            check(p < 2, "Out-of-window callback must never run"); read.add(p); return 0;
        }, (p, d) -> p + 1, 256, p -> p < 2) == null, "Window exceeded");
        check(read.equals(List.of(0, 1)), "Only safe reads");
    }
    public static void guardAlsoRejectsTerminalAndStart() {
        check(LegacyMimicResolver.resolve(0, p -> { throw new AssertionError("Rejected start read"); },
                (p, d) -> p, 1, p -> false) == null, "Unreadable start");
        check(LegacyMimicResolver.resolve(0, p -> {
            if (p == 1) throw new AssertionError("Rejected terminal read"); return 0;
        }, (p, d) -> 1, 2, p -> p != 1) == null, "Unreadable terminal");
    }
    public static void snapshotWindowEveryAxis() {
        var window = LegacyMimicReadWindow.sectionSnapshot(8, 8, 8);
        for (int x : new int[]{-16, 0, 31}) for (int y : new int[]{-16, 0, 31}) for (int z : new int[]{-16, 0, 31}) {
            check(window.contains(x, y, z), "Snapshot includes all 27 section corners");
        }
        for (int outside : new int[]{-17, 32}) {
            check(!window.contains(outside, 0, 0), "X boundary");
            check(!window.contains(0, outside, 0), "Y boundary");
            check(!window.contains(0, 0, outside), "Z boundary");
        }
    }
    public static void negativeCoordinates() {
        var window = LegacyMimicReadWindow.sectionSnapshot(-1, -17, -33);
        check(window.minX() == -32 && window.maxX() == 15, "Negative X floors");
        check(window.minY() == -48 && window.maxY() == -1, "Negative Y floors");
        check(window.minZ() == -64 && window.maxZ() == -17, "Negative Z floors");
        check(Section.ofBlock(-1, -17, -33).equals(new Section(-1, -2, -3)), "Queue and guard use same floor");
    }
    public static void unknownViewsStayLocal() {
        var window = LegacyMimicReadWindow.immediateNeighbors(15, -1, 0);
        check(window.contains(16, 0, -1), "Immediate neighbor including diagonal");
        check(!window.contains(17, -1, 0), "Unknown view cannot inherit section-sized padding");
        check(!window.contains(15, -3, 0), "Unknown vertical bound");
    }
    public static void coordinateOverflow() {
        var low = LegacyMimicReadWindow.sectionSnapshot(Integer.MIN_VALUE, 0, 0);
        var high = LegacyMimicReadWindow.sectionSnapshot(Integer.MAX_VALUE, 0, 0);
        check(low.contains(Integer.MIN_VALUE, 0, 0) && !low.contains(Integer.MAX_VALUE, 0, 0), "No low wrap");
        check(high.contains(Integer.MAX_VALUE, 0, 0) && !high.contains(Integer.MIN_VALUE, 0, 0), "No high wrap");
        check(LegacyMimicReadWindow.immediateNeighbors(Integer.MAX_VALUE, 0, 0).maxX() == 2147483648L, "Long addition");
        invalid(() -> new LegacyMimicReadWindow(1, 0, 0, 0, 0, 0));
    }
    public static void boundedChainKeepsSafeTerminal() {
        var window = LegacyMimicReadWindow.sectionSnapshot(15, 0, 0);
        Predicate<Integer> readable = x -> window.contains(x, 0, 0);
        check(Integer.valueOf(31).equals(LegacyMimicResolver.resolve(15, p -> p == 31 ? -1 : 5,
                (p, d) -> p + 1, 256, readable)), "Safe cross-section terminal");
        check(LegacyMimicResolver.resolve(15, p -> {
            check(p <= 31, "Never read outside snapshot"); return 5;
        }, (p, d) -> p + 1, 256, readable) == null, "Out-of-snapshot chain falls back");
    }
    public static void inverseWindowCoversAllOwners() {
        for (int x : new int[]{-17, -16, -1, 0, 15, 16}) for (int y : new int[]{-17, 0, 16}) {
            var queue = new LegacyMimicRefreshQueue(128);
            queue.blockChanged(x, y, 0);
            var batch = queue.drain();
            check(!batch.fullRefresh() && batch.sections().size() == 27, "Exactly 27 potential owners");
            Section source = Section.ofBlock(x, y, 0);
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                Section owner = new Section(source.x() + dx, source.y() + dy, source.z() + dz);
                boolean reads = LegacyMimicReadWindow.sectionSnapshot(owner.x() * 16, owner.y() * 16, owner.z() * 16).contains(x, y, 0);
                check(batch.sections().contains(owner) == reads, "Refresh is exact inverse of section window");
            }
        }
    }
    public static void repeatedUpdatesCoalesce() {
        var queue = new LegacyMimicRefreshQueue(27);
        for (int i = 0; i < 4096; i++) queue.blockChanged(i & 15, (i >> 4) & 15, (i >> 8) & 15);
        var batch = queue.drain();
        check(!batch.fullRefresh() && batch.sections().size() == 27, "Same section uses one neighborhood");
    }
    public static void overlappingNeighborhoodsUnion() {
        var queue = new LegacyMimicRefreshQueue(128);
        queue.blockChanged(0, 0, 0); queue.blockChanged(16, 0, 0);
        check(queue.drain().sections().size() == 36, "Overlapping 3x3x3 owners union to 4x3x3");
    }
    public static void columnLoadUnloadCoverage() {
        var queue = new LegacyMimicRefreshQueue(128);
        queue.columnChanged(-2, 3, -4, 4);
        var batch = queue.drain();
        check(!batch.fullRefresh() && batch.sections().size() == 54, "Column halo 3x6x3");
        check(batch.sections().contains(new Section(-3, -5, 2)), "Lower neighboring section");
        check(batch.sections().contains(new Section(-1, 0, 4)), "Upper neighboring section");
    }
    public static void capacityNeverDropsUpdates() {
        var queue = new LegacyMimicRefreshQueue(27);
        queue.blockChanged(0, 0, 0); queue.blockChanged(1000, 0, 0);
        var batch = queue.drain();
        check(batch.fullRefresh() && batch.sections().isEmpty(), "Capacity promotes to complete invalidation");
        queue.blockChanged(16, 0, 0);
        check(queue.drain().sections().size() == 27, "Overflow does not permanently disable queue");
    }
    public static void fullRefreshStaysBounded() {
        var queue = new LegacyMimicRefreshQueue(27);
        queue.requestFullRefresh();
        for (int i = 0; i < 10000; i++) queue.blockChanged(i * 16, 0, 0);
        var batch = queue.drain();
        check(batch.fullRefresh() && batch.sections().isEmpty(), "No pending entries after promotion");
    }
    public static void hugeColumnsAndInvalidCounts() {
        var queue = new LegacyMimicRefreshQueue(27);
        queue.columnChanged(0, 0, 0, Integer.MAX_VALUE);
        check(queue.drain().fullRefresh(), "Huge columns have bounded work");
        invalid(() -> queue.columnChanged(0, 0, 0, -1));
        queue.columnChanged(0, 0, 0, 0);
        check(queue.drain().sections().isEmpty(), "Empty column");
        queue.columnChanged(Integer.MAX_VALUE, 0, 0, 1);
        check(queue.drain().fullRefresh(), "No section-coordinate wrap");
        invalid(() -> new LegacyMimicRefreshQueue(26));
        invalid(() -> new LegacyMimicRefreshQueue(65537));
    }
    public static void drainAndClearAreIndependent() {
        var queue = new LegacyMimicRefreshQueue(128);
        queue.blockChanged(0, 0, 0);
        var batch = queue.drain();
        check(queue.drain().sections().isEmpty(), "Drains once");
        queue.blockChanged(100, 0, 0); queue.clear();
        check(queue.drain().sections().isEmpty(), "World reset discards old work");
        check(batch.sections().size() == 27, "Drained batch is independent");
        try { batch.sections().clear(); throw new AssertionError("Mutable batch"); }
        catch (UnsupportedOperationException expected) { /* immutable snapshot */ }
        queue.requestFullRefresh(); queue.clear();
        check(!queue.drain().fullRefresh(), "World reset also clears promotion");
    }
    public static void concurrentProducers() throws Exception {
        var queue = new LegacyMimicRefreshQueue(1024);
        var start = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            final int x = i * 160;
            threads[i] = new Thread(() -> {
                try { start.await(); for (int repeat = 0; repeat < 100; repeat++) queue.blockChanged(x, 0, 0); }
                catch (Throwable failure) { error.compareAndSet(null, failure); }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        check(error.get() == null, "Concurrent producer failure: " + error.get());
        check(queue.drain().sections().size() == 8 * 27, "All independent producer requests retained");
    }

    public static void main(String[] args) throws Exception {
        int count = 0;
        for (var method : Mimic142Checks.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0) {
                try { method.invoke(null); }
                catch (java.lang.reflect.InvocationTargetException failure) { throw new AssertionError(method.getName(), failure.getCause()); }
                System.out.println("PASS " + method.getName()); count++;
            }
        }
        System.out.println("PASS " + count + " production logic checks");
    }
}
