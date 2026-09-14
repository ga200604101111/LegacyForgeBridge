package dev.yinghuang.legacyforgebridge.runtime;

import dev.yinghuang.legacyforgebridge.network.ForgeRuntimeCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRuntimeStateTest {
    @Test void resetClearsIdentitiesAndInvalidatesQueuedGeneration() {
        var state = new LegacyRuntimeState(); state.begin();
        long generation = state.snapshot().generation();
        state.apply(new ForgeRuntimeCodec.DimensionRegister(7, -1));
        state.apply(new ForgeRuntimeCodec.FluidIdMap(Map.of("water", 42), List.of("minecraft:water"), true));
        var old = state.snapshot(); state.reset();
        assertFalse(state.accepts(generation)); assertFalse(state.snapshot().active());
        assertTrue(state.snapshot().dimensions().isEmpty()); assertTrue(state.snapshot().fluids().isEmpty());
        assertFalse(state.snapshot().hasFluidDefaults()); assertEquals(42, old.fluids().get("water"));
        state.begin(); assertFalse(state.accepts(generation));
        assertThrows(IllegalStateException.class, () -> state.apply(generation, new ForgeRuntimeCodec.DimensionRegister(9, 3)));
    }
    @Test void repeatedDimensionIsIdempotentButConflictDoesNotMutateState() {
        var state = new LegacyRuntimeState(); state.begin();
        state.apply(new ForgeRuntimeCodec.DimensionRegister(7, -1));
        state.apply(new ForgeRuntimeCodec.DimensionRegister(7, -1));
        assertThrows(IllegalArgumentException.class, () -> state.apply(new ForgeRuntimeCodec.DimensionRegister(7, 3)));
        assertEquals(Map.of(7, -1), state.snapshot().dimensions());
    }
    @Test void fluidSnapshotsReplaceRatherThanLeakPreviousServerMap() {
        var state = new LegacyRuntimeState(); state.begin();
        state.apply(new ForgeRuntimeCodec.FluidIdMap(Map.of("water", 42), List.of("minecraft:water"), true));
        state.apply(new ForgeRuntimeCodec.FluidIdMap(Map.of("lava", 8), List.of(), false));
        assertEquals(Map.of("lava", 8), state.snapshot().fluids()); assertFalse(state.snapshot().hasFluidDefaults());
        assertThrows(UnsupportedOperationException.class, () -> state.snapshot().fluids().put("other", 9));
    }
    @Test void inactiveSessionRejectsUpdates() {
        var state = new LegacyRuntimeState();
        assertThrows(IllegalStateException.class, () -> state.apply(new ForgeRuntimeCodec.DimensionRegister(0, 0)));
    }
    @Test void dimensionCountIsBoundedAndSnapshotsAreCachedUntilUpdate() {
        var state = new LegacyRuntimeState(); state.begin();
        assertSame(state.snapshot(), state.snapshot());
        for (int i = 0; i < 4096; i++) state.apply(new ForgeRuntimeCodec.DimensionRegister(i, i));
        assertThrows(IllegalArgumentException.class, () -> state.apply(new ForgeRuntimeCodec.DimensionRegister(4096, 0)));
        assertEquals(4096, state.snapshot().dimensions().size());
    }
}
