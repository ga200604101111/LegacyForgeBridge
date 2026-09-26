package dev.yinghuang.legacyforgebridge.behavior;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, one-shot matching of an incoming velocity against this client's observed jump history.
 * Matching is evidence of a possible delayed echo, NOT proof of a server packet's cause. The old
 * protocol carries no jump sequence/cause; a different force with the same vector is ambiguous.
 * No mod identity, fixed boost, height cap, gravity extrapolation or packet cancellation is used.
 */
public final class LegacyJumpEchoWindow {
    public static final int MAX_AGE_TICKS = 8;
    public static final double COMPONENT_EPSILON = 1.0 / 8000.0 + 1.0e-7;
    private static final double NUMERIC_EPSILON = 1.0e-8;
    public record Velocity(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public record Decision(boolean candidate, Velocity velocity, String reason, int ageTicks) { }
    private record Sample(long tick, double y, Velocity velocity) { }
    private final List<Sample> history = new ArrayList<>();
    private long launchTick, lastTick;
    private double lastY, lastVertical;
    private boolean active, airborne;

    public boolean active() { return active; }
    public int sampleCount() { return history.size(); }
    public void clear() { active = false; airborne = false; history.clear(); }

    /** Called AFTER the original source jump callback; its motion change is not modified. */
    public boolean begin(long tick, double y, Velocity before, Velocity after) {
        clear();
        if (tick < 0 || !Double.isFinite(y) || before == null || after == null
                || !before.finite() || !after.finite() || before.y() <= 0 || after.y() <= before.y()
                || Double.compare(before.x(), after.x()) != 0 || Double.compare(before.z(), after.z()) != 0)
            return false;
        launchTick = lastTick = tick; lastY = y; lastVertical = after.y(); active = true;
        history.add(new Sample(tick, y, after));
        return true;
    }

    /** Only observed, uninterrupted ascending motion is retained; unknown motion fails open. */
    public void sample(long tick, double y, Velocity current, boolean onGround, boolean unsafe) {
        if (!validate(tick, y, current, onGround, unsafe)) return;
        // Do not store an unbounded number of observations within one tick.
        long samplesThisTick = history.stream().filter(s -> s.tick() == tick).count();
        if (samplesThisTick < 2 && !history.getLast().velocity().equals(current))
            history.add(new Sample(tick, y, current));
        lastTick = tick; lastY = y; lastVertical = current.y();
    }

    private boolean validate(long tick, double y, Velocity current, boolean onGround, boolean unsafe) {
        if (!active) return false;
        if (unsafe || current == null || !current.finite() || !Double.isFinite(y)
                || tick < lastTick || tick - launchTick > MAX_AGE_TICKS
                || current.y() <= 0 || current.y() > lastVertical + NUMERIC_EPSILON
                || y < lastY - NUMERIC_EPSILON || onGround && (airborne || tick > launchTick)) {
            clear(); return false;
        }
        if (!onGround) airborne = true;
        return true;
    }

    /**
     * Every first local motion packet consumes the window, including mismatches. A matching old
     * vector may retain only the PRE-packet client Y; incoming X/Z are always kept. Callers must
     * separately verify connection/entity identity and that vanilla applied the packet unchanged.
     */
    public Decision receive(long tick, double y, Velocity beforePacket, Velocity incoming,
                            boolean onGround, boolean unsafe) {
        int age = active && tick >= launchTick && tick - launchTick <= Integer.MAX_VALUE
                ? (int) (tick - launchTick) : -1;
        if (!validate(tick, y, beforePacket, onGround, unsafe))
            return new Decision(false, incoming, "NO_VALID_ASCENT", age);
        boolean match = incoming != null && incoming.finite() && airborne && age >= 0
                && incoming.y() > beforePacket.y() + COMPONENT_EPSILON
                && history.stream().anyMatch(s -> s.tick() <= tick && y > s.y() + NUMERIC_EPSILON && close(s.velocity(), incoming));
        clear();
        return match
                ? new Decision(true, new Velocity(incoming.x(), beforePacket.y(), incoming.z()), "MATCHED_OLD_VECTOR", age)
                : new Decision(false, incoming, "UNMATCHED_OR_NOT_A_REWIND", age);
    }
    public static boolean close(Velocity a, Velocity b) {
        return a != null && b != null && a.finite() && b.finite()
                && Math.abs(a.x() - b.x()) <= COMPONENT_EPSILON
                && Math.abs(a.y() - b.y()) <= COMPONENT_EPSILON
                && Math.abs(a.z() - b.z()) <= COMPONENT_EPSILON;
    }
}
