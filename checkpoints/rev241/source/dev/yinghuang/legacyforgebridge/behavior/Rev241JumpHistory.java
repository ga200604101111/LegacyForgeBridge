package dev.yinghuang.legacyforgebridge.behavior;

/**
 * Bounded source-jump history. This is a conservative compatibility heuristic,
 * NOT a protocol acknowledgement: S12 contains no jump ID or server timestamp.
 * All velocities in the window must follow observed dry-ground jump physics.
 * No source/mod/item name or observed entity ID participates in admission.
 */
public final class Rev241JumpHistory {
    public static final int MAX_TICKS = 40;
    private static final double DRAG = (double) 0.98F;
    private static final double EPS = 1.0E-8;
    private final double[] samples = new double[MAX_TICKS + 2];
    private int count;
    private long startTick, lastStepTick;
    private boolean active;
    private String reason = "unarmed";

    public record Decision(boolean reconcile, double replacementY, int matchedPhase,
                           int currentPhase, String reason) {}

    public boolean arm(long tick, double beforeY, double afterY, boolean eligible) {
        clear("source-not-eligible");
        if (!eligible || !finite(beforeY) || !finite(afterY) || beforeY <= 0
                || afterY <= beforeY + EPS || afterY >= 3.9) return false;
        startTick = tick;
        lastStepTick = Long.MIN_VALUE;
        count = 1;
        samples[0] = afterY;
        active = true;
        reason = "source-history";
        return true;
    }

    public void clear(String why) { active = false; count = 0; reason = why; }
    public boolean active() { return active; }
    public String reason() { return reason; }
    public int phase() { return Math.max(0, count - 1); }
    public double expectedY() { return count == 0 ? Double.NaN : samples[count - 1]; }

    private boolean context(long tick, double currentY, boolean eligible) {
        if (!active) return false;
        if (!eligible || tick < startTick || tick - startTick > MAX_TICKS
                || !finite(currentY) || Math.abs(currentY - expectedY()) > EPS) {
            clear("context-or-trajectory-changed");
            return false;
        }
        return true;
    }

    /** Called before a native velocity write; packet writes are excluded by the adapter. */
    public void observeVelocity(long tick, double currentY, double requestedY, boolean eligible) {
        if (!context(tick, currentY, eligible)) return;
        if (!finite(requestedY)) { clear("non-finite-velocity"); return; }
        if (Math.abs(requestedY - currentY) <= EPS) return; // horizontal-only write
        double next = gravity(currentY);
        if (Math.abs(requestedY - next) > EPS || lastStepTick == tick || count == samples.length) {
            clear("non-gravity-velocity-write");
            return;
        }
        samples[count++] = requestedY;
        lastStepTick = tick;
    }

    public void observePosition(double currentYVelocity, double deltaY) {
        if (active && (!finite(deltaY) || !finite(currentYVelocity)
                || (Math.abs(deltaY) > 1.0E-9 && Math.abs(deltaY - currentYVelocity) > 1.0E-6)))
            clear("clipped-step-or-position-change");
    }

    public void tick(long tick, double currentY, boolean eligible) {
        context(tick, currentY, eligible);
    }

    /**
     * Only positive incoming Y matching an ALREADY OBSERVED ASCENT phase can be
     * reconciled. Unmatched, downward, zero, oversized and ambiguous inputs pass.
     * Incoming X/Z are used for codec scale only and are never changed.
     */
    public Decision decide(long tick, double currentY, double x, double y, double z, boolean eligible) {
        if (!context(tick, currentY, eligible)) return pass(currentY, reason);
        if (!finite(x) || !finite(y) || !finite(z) || Math.max(Math.abs(x), Math.abs(z)) >= 3.9
                || y <= 0 || y >= 3.9 || y <= currentY + 1.0E-5 || count < 2) {
            clear("authoritative-non-echo-packet");
            return pass(currentY, reason);
        }
        double scale = Math.ceil(Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))));
        int match = -1;
        for (int i = 0; i < count - 1; i++) {
            if (samples[i] <= 0) continue;
            // Exact legacy short truncation followed by the modern packed-vector codec.
            if (Math.abs(encodedY(samples[i], scale) - y) <= 1.0E-10) {
                if (match >= 0) { clear("ambiguous-history-match"); return pass(currentY, reason); }
                match = i;
            }
        }
        if (match < 0) { clear("no-source-history-match"); return pass(currentY, reason); }
        return new Decision(true, currentY, match, count - 1, "matched-observed-ascent");
    }

    private Decision pass(double y, String why) { return new Decision(false, y, -1, phase(), why); }
    private static boolean finite(double n) { return Double.isFinite(n); }
    public static double gravity(double y) { return (y - 0.08D) * DRAG; }
    public static double encodedY(double y, double scale) {
        double legacy = (int) (Math.max(-3.9D, Math.min(3.9D, y)) * 8000.0D) / 8000.0D;
        return (Math.round((legacy / scale * 0.5D + 0.5D) * 32766.0D) * 2.0D / 32766.0D - 1.0D) * scale;
    }
}
