package dev.yinghuang.legacyforgebridge.compat;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Conservative inverse of the supported read window. A changed source section can affect
 * any of its 27 neighboring owner sections, including a model being compiled for the first
 * time. This intentionally avoids a worker-populated dependency cache and its first-build
 * race. Requests are coalesced; capacity pressure promotes to a full refresh, never eviction.
 */
public final class LegacyMimicRefreshQueue {
    public record Section(int x, int y, int z) {
        public static Section ofBlock(int x, int y, int z) {
            return new Section(x >> 4, y >> 4, z >> 4);
        }
    }

    public record Batch(boolean fullRefresh, Set<Section> sections) {
        public Batch { sections = Set.copyOf(sections); }
    }

    private final int capacity;
    private final Set<Section> pending = new LinkedHashSet<>();
    private final Set<Section> changedSources = new LinkedHashSet<>();
    private boolean fullRefresh;

    public LegacyMimicRefreshQueue(int capacity) {
        if (capacity < 27 || capacity > 65536) throw new IllegalArgumentException("Refresh capacity outside 27..65536");
        this.capacity = capacity;
    }

    public synchronized void blockChanged(int x, int y, int z) {
        Section source = Section.ofBlock(x, y, z);
        around(source.x(), source.y(), source.z());
    }

    /** Also used for unloads and replacements: a terminal becoming air still changes a mimic. */
    public synchronized void columnChanged(int x, int z, int minSectionY, int sectionCount) {
        if (sectionCount < 0) throw new IllegalArgumentException("Negative section count");
        if (sectionCount == 0 || fullRefresh) return;
        if ((long) sectionCount + 2 > capacity / 9L
                || (long) minSectionY + sectionCount > Integer.MAX_VALUE) {
            requestFullRefresh();
            return;
        }
        for (int i = 0; i < sectionCount && !fullRefresh; i++) around(x, minSectionY + i, z);
    }

    private void around(int x, int y, int z) {
        if (fullRefresh || !changedSources.add(new Section(x, y, z))) return;
        // Real block sections are much narrower than int; do not wrap invalid caller input.
        if (x == Integer.MIN_VALUE || x == Integer.MAX_VALUE || y == Integer.MIN_VALUE
                || y == Integer.MAX_VALUE || z == Integer.MIN_VALUE || z == Integer.MAX_VALUE) {
            requestFullRefresh();
            return;
        }
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            Section owner = new Section(x + dx, y + dy, z + dz);
            if (!pending.contains(owner) && pending.size() == capacity) {
                requestFullRefresh();
                return;
            }
            pending.add(owner);
        }
    }

    public synchronized void requestFullRefresh() {
        pending.clear();
        changedSources.clear();
        fullRefresh = true;
    }

    public synchronized Batch drain() {
        Batch batch = new Batch(fullRefresh, pending);
        clear();
        return batch;
    }

    public synchronized void clear() {
        pending.clear();
        changedSources.clear();
        fullRefresh = false;
    }
}
