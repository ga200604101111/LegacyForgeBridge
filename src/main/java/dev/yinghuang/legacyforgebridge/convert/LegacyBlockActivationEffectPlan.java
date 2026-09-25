package dev.yinghuang.legacyforgebridge.convert;

import java.util.List;
import java.util.Objects;

/** Immutable, source-derived finite decision table. Contains no legacy executable bytecode. */
public record LegacyBlockActivationEffectPlan(String heldItemId, List<Integer> outcomes) {
    public static final int EMPTY_HAND = 0;
    public static final int OTHER_ITEM = 1;
    public static final int MATCHING_ITEM = 2;
    public static final int INPUT_COUNT = 6 * 16 * 2 * 2 * 3;
    public static final int LEGACY_NOTIFY_FLAGS = 2;

    public LegacyBlockActivationEffectPlan {
        if (heldItemId == null || !heldItemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid held item identity");
        }
        outcomes = List.copyOf(outcomes);
        if (outcomes.size() != INPUT_COUNT) throw new IllegalArgumentException("Incomplete activation effect table");
        boolean writes = false;
        for (int i = 0; i < outcomes.size(); i++) {
            int code = Objects.requireNonNull(outcomes.get(i));
            if (code < 0 || code > 33) throw new IllegalArgumentException("Invalid activation outcome " + code);
            if (code >= 2) {
                writes = true;
                if (i % 3 != MATCHING_ITEM) throw new IllegalArgumentException("Mutation lacks matching held-item guard");
            }
        }
        if (!writes) throw new IllegalArgumentException("Effect plan has no reachable metadata write");
    }

    public record Decision(boolean handled, int metadata) {
        public Decision {
            if (metadata < -1 || metadata > 15) throw new IllegalArgumentException("Invalid legacy metadata");
        }
        public boolean writesMetadata() { return metadata >= 0; }
        public int encode() { return ((metadata + 1) << 1) | (handled ? 1 : 0); }
    }

    public Decision evaluate(int side, int metadata, boolean clientSide, boolean sneaking, int heldKind) {
        if (side < 0 || side > 5 || metadata < 0 || metadata > 15 || heldKind < 0 || heldKind > 2) {
            throw new IllegalArgumentException("Invalid activation input");
        }
        int index = (((side * 16 + metadata) * 2 + (clientSide ? 1 : 0)) * 2 + (sneaking ? 1 : 0)) * 3 + heldKind;
        int code = outcomes.get(index);
        return new Decision((code & 1) != 0, (code >> 1) - 1);
    }
}
