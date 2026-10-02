package dev.yinghuang.legacyforgebridge.behavior;

/** Diagnostic rev243: binary-compatible NO-OP for every rev241/rev242 motion correction hook.
 * No property can re-enable the previous velocity heuristics in this artifact.
 * The independent source landing/particle adapter is intentionally retained.
 */
public final class Rev241JumpMotionBridge {
    private Rev241JumpMotionBridge() {}
    public static void initialize() { LegacyMotionTraceLog.event("REV243_READY","heuristicReconciliation=DISABLED"); }
    public static void sourceBefore(Object entity) {}
    public static void sourceAfter(Object entity) {}
    public static void nativeVelocity(Object entity,Object requested) {}
    public static void nativePosition(Object entity,double x,double y,double z) {}
    public static void packetHead(Object packet) {}
    public static void packetTail(Object packet) {}
    public static void tick(Object client) {
        Rev243Diagnostics.tickEnd(client);
        Rev242LandingBridge.tick(client);
    }
    public static void barrier(String reason) { Rev242LandingBridge.barrier(); }
}
