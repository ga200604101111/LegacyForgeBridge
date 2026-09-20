package dev.yinghuang.legacyforgebridge.compat;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Bounded neighbor walking independent of world/render APIs. -1 is terminal; -2 is unresolved. */
public final class LegacyMimicResolver {
    private LegacyMimicResolver() { }

    /** Compatibility overload for callers with an unrestricted, already safe data source. */
    public static <P> P resolve(P start, Function<P, Integer> direction,
                                BiFunction<P, Integer, P> step, int limit) {
        return resolve(start, direction, step, limit, position -> true);
    }

    /** The read guard runs BEFORE the direction callback, including for the terminal block. */
    public static <P> P resolve(P start, Function<P, Integer> direction,
                                BiFunction<P, Integer, P> step, int limit, Predicate<P> readable) {
        if (limit < 1 || limit > 256) throw new IllegalArgumentException("Mimic traversal budget outside 1..256");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(readable, "readable");
        Set<P> seen = new HashSet<>();
        P current = start;
        for (int depth = 0; depth < limit; depth++) {
            if (current == null || !seen.add(current) || !readable.test(current)) return null;
            Integer face = direction.apply(current);
            if (face != null && face == -1) return current;
            if (face == null || face < 0 || face > 5) return null;
            current = step.apply(current, face);
        }
        return null;
    }
}
