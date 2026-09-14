package dev.yinghuang.legacyforgebridge.runtime;

import dev.yinghuang.legacyforgebridge.network.ForgeRuntimeCodec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Connection-local legacy identities. No mutation of Minecraft's process-wide registries. */
public final class LegacyRuntimeState {
    private long generation;
    private boolean active;
    private final Map<Integer, Integer> dimensions = new LinkedHashMap<>();
    private Map<String, Integer> fluids = Map.of();
    private List<String> defaults = List.of();
    private boolean hasDefaults;
    private Snapshot cachedSnapshot;

    public record Snapshot(long generation, boolean active, Map<Integer, Integer> dimensions,
                           Map<String, Integer> fluids, List<String> defaultFluids, boolean hasFluidDefaults) {
        public Snapshot {
            dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
            fluids = Collections.unmodifiableMap(new LinkedHashMap<>(fluids));
            defaultFluids = List.copyOf(defaultFluids);
        }
    }

    public synchronized void begin() { clear(); active = true; }
    public synchronized void reset() { clear(); }
    private void clear() {
        generation++;
        cachedSnapshot = null;
        active = false;
        dimensions.clear();
        fluids = Map.of();
        defaults = List.of();
        hasDefaults = false;
    }
    public synchronized boolean accepts(long expectedGeneration) {
        return active && generation == expectedGeneration;
    }
    public synchronized Snapshot snapshot() {
        if (cachedSnapshot == null) cachedSnapshot = new Snapshot(generation, active, dimensions, fluids, defaults, hasDefaults);
        return cachedSnapshot;
    }
    public synchronized void apply(ForgeRuntimeCodec.Message message) { apply(generation, message); }
    public synchronized void apply(long expectedGeneration, ForgeRuntimeCodec.Message message) {
        if (!accepts(expectedGeneration)) throw new IllegalStateException("No active legacy runtime session");
        if (message instanceof ForgeRuntimeCodec.DimensionRegister dimension) {
            Integer previous = dimensions.get(dimension.dimensionId());
            if (previous != null && previous != dimension.providerId()) {
                throw new IllegalArgumentException("Conflicting legacy provider for dimension " + dimension.dimensionId());
            }
            if (previous == null && dimensions.size() >= 4_096) {
                throw new IllegalArgumentException("Too many legacy dimension identities");
            }
            dimensions.put(dimension.dimensionId(), dimension.providerId());
        } else if (message instanceof ForgeRuntimeCodec.FluidIdMap map) {
            fluids = map.ids();
            defaults = map.defaults();
            hasDefaults = map.hasDefaults();
        } else {
            throw new IllegalArgumentException("Missing FORGE message");
        }
        cachedSnapshot = null;
    }
}
