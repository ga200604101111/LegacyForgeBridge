package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production Fabric-intermediary adapter for the SHA-pinned rev240 main JAR.
 * Hooks are added to existing lifecycle/trace methods; no new injection target.
 * World/position/outbound packets are never rewritten. All packet X/Z survive.
 * Reflection failures fail open to the complete authoritative server velocity.
 */
public final class Rev241JumpMotionBridge {
    private static final Rev241JumpHistory HISTORY = new Rev241JumpHistory();
    private static final AtomicLong BARRIER = new AtomicLong();
    private static final boolean ENABLED = !"off".equalsIgnoreCase(
            System.getProperty("legacyforgebridge.jumpReconcile", "source-history"));
    private static volatile Api api;
    private static volatile boolean failed;
    private static Object owner, world, connection;
    private static long generation;
    private static Source source;
    private static Pending pending;
    private static boolean restoring;
    private static long reconciled;
    private record Source(Object player, long tick, double y, double vy, boolean eligible) {}
    private record Pending(Object packet, Object player, long tick, double y,
                           double x, double vy, double z, long generation,
                           Rev241JumpHistory.Decision decision) {}

    private Rev241JumpMotionBridge() {}

    public static void initialize() {
        log("REV241_READY", "mode=" + (ENABLED ? "SOURCE_HISTORY_RECONCILIATION" : "OFF")
                + " old diagnostic jumpEcho property ignored; unmatched/negative motion passes; XZ preserved");
    }

    public static void sourceBefore(Object entity) {
        if (!ENABLED || failed) return;
        try {
            Api a = api();
            Object client = a.client();
            if (!a.onThread(client)) return;
            source = null;
            if (!bind(a, client) || entity != owner) return;
            HISTORY.clear("new-source-jump");
            pending = null;
            source = new Source(entity, a.age(entity), a.y(entity), a.vy(entity),
                    a.ground(entity) && a.safe(entity));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { fail(e); }
    }

    public static void sourceAfter(Object entity) {
        if (!ENABLED || failed) return;
        try {
            Api a = api();
            Object client = a.client();
            if (!a.onThread(client)) return;
            Source s = source;
            source = null;
            if (s == null || s.player() != entity || !bind(a, client)) return;
            boolean eligible = s.eligible() && a.safe(entity) && a.ground(entity)
                    && a.age(entity) == s.tick() && Math.abs(a.y(entity) - s.y()) < 1.0E-9;
            if (HISTORY.arm(s.tick(), s.vy(), a.vy(entity), eligible)) {
                generation = BARRIER.get();
                log("REV241_ARM", "tick=" + s.tick() + " beforeY=" + s.vy()
                        + " sourceY=" + a.vy(entity) + " maxTicks=" + Rev241JumpHistory.MAX_TICKS);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { fail(e); }
    }

    public static void nativeVelocity(Object entity, Object requested) {
        if (!ENABLED || failed || !HISTORY.active() || source != null || restoring || pending != null) return;
        try {
            Api a = api();
            Object client = a.client();
            if (!a.onThread(client) || !bind(a, client) || entity != owner) return;
            if (!epoch()) return;
            HISTORY.observeVelocity(a.age(entity), a.vy(entity), a.vectorY.getDouble(requested), a.safe(entity));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { fail(e); }
    }

    public static void nativePosition(Object entity, double x, double y, double z) {
        if (!ENABLED || failed || !HISTORY.active() || source != null || pending != null) return;
        try {
            Api a = api();
            Object client = a.client();
            if (!a.onThread(client) || !bind(a, client) || entity != owner || !epoch()) return;
            HISTORY.observePosition(a.vy(entity), y - a.y(entity));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { fail(e); }
    }

    public static void tick(Object client) {
        if (!ENABLED || failed || (owner == null && !HISTORY.active())) return;
        try {
            Api a = api();
            if (!a.onThread(client) || !bind(a, client)) return;
            if (pending != null) { clear("missing-packet-tail"); return; }
            if (!epoch()) return;
            HISTORY.tick(a.age(owner), a.vy(owner), a.safe(owner) && !a.ground(owner));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { fail(e); }
    }

    public static void packetHead(Object packet) {
        if (!ENABLED || failed) return;
        try {
            Api a = api();
            Object client = a.client();
            if (!a.onThread(client)) return; // Netty dispatch is NOT an apply.
            if (!bind(a, client) || ((Number)a.packetId.invoke(packet)).intValue() != a.id(owner)) return;
            if (pending != null) { clear("nested-or-missing-packet-tail"); return; }
            epoch();
            Object v = a.packetVelocity.invoke(packet);
            double x = a.vectorX.getDouble(v), y = a.vectorY.getDouble(v), z = a.vectorZ.getDouble(v);
            double before = a.vy(owner);
            boolean wasActive = HISTORY.active();
            Rev241JumpHistory.Decision decision = HISTORY.decide(a.age(owner), before, x, y, z,
                    a.safe(owner) && !a.ground(owner) && !a.verticalCollision.getBoolean(owner));
            pending = new Pending(packet, owner, a.age(owner), a.y(owner), x, y, z, BARRIER.get(), decision);
            if (wasActive && !decision.reconcile()) log("REV241_PASS", "incomingY=" + y + " reason=" + decision.reason());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { fail(e); }
    }

    public static void packetTail(Object packet) {
        if (!ENABLED || failed) return;
        try {
            Api a = api();
            Object client = a.client();
            if (!a.onThread(client)) return;
            Pending p = pending;
            if (p == null || p.packet() != packet) return;
            if (!p.decision().reconcile()) { pending = null; return; }
            if (!bind(a, client) || p.player() != owner || p.generation() != BARRIER.get()
                    || a.age(owner) != p.tick() || a.y(owner) != p.y() || !a.safe(owner) || a.ground(owner)) {
                clear("changed-during-packet"); return;
            }
            Object v = a.velocity.invoke(owner);
            double x = a.vectorX.getDouble(v), y = a.vectorY.getDouble(v), z = a.vectorZ.getDouble(v);
            if (x != p.x() || y != p.vy() || z != p.z()) {
                clear("other-packet-handler-modified-velocity"); return;
            }
            restoring = true;
            try { a.setVelocity.invoke(owner, x, p.decision().replacementY(), z); }
            finally { restoring = false; pending = null; }
            reconciled++;
            log("REV241_RECONCILED", "tick=" + p.tick() + " incomingY=" + y
                    + " retainedY=" + p.decision().replacementY() + " matchedPhase=" + p.decision().matchedPhase()
                    + " currentPhase=" + p.decision().currentPhase() + " xzPreserved=true count=" + reconciled);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { fail(e); }
    }

    /** Conservative barrier even when the packet is first seen on Netty. */
    public static void barrier(String reason) { BARRIER.incrementAndGet(); }

    private static boolean epoch() {
        if (generation != BARRIER.get()) { clear("damage-status-explosion-or-position-barrier"); return false; }
        return true;
    }
    private static void clear(String reason) { HISTORY.clear(reason); pending = null; }

    private static boolean bind(Api a, Object client) throws ReflectiveOperationException {
        Object p = a.player.get(client), w = a.world.get(client), c = a.connection.invoke(client);
        if (!(Boolean) a.legacy.invoke(null) || p == null || w == null || c == null || a.server.invoke(client) != null) {
            clear("not-legacy-multiplayer"); owner = world = connection = null; source = null; return false;
        }
        if (p != owner || w != world || c != connection) {
            clear("new-session-player-or-world"); source = null;
            owner = p; world = w; connection = c; generation = BARRIER.get();
        }
        return true;
    }
    private static Api api() throws ReflectiveOperationException {
        Api a = api;
        if (a == null) synchronized (Rev241JumpMotionBridge.class) {
            a = api; if (a == null) api = a = new Api();
        }
        return a;
    }
    private static void fail(Throwable e) {
        failed = true; clear("reflection-or-linkage-failure"); source = null; restoring = false;
        System.getLogger("LegacyForgeBridge/jump").log(System.Logger.Level.WARNING,
                "rev241 reconciliation disabled; complete server velocity retained", e);
    }
    private static void log(String stage, String text) { LegacyMotionTraceLog.event(stage, text); }

    private static final class Api {
        final Method getClient, onThread, legacy, connection, server, velocity, setVelocity, getY,
                isGround, isWater, isLava, isClimbing, hasVehicle, abilities, getId, packetId, packetVelocity;
        final Field player, world, age, hurt, flying, verticalCollision, vectorX, vectorY, vectorZ;
        Api() throws ReflectiveOperationException {
            Class<?> mc = Class.forName("net.minecraft.class_310"), entity = Class.forName("net.minecraft.class_1297"),
                    living = Class.forName("net.minecraft.class_1309"), human = Class.forName("net.minecraft.class_1657"),
                    vec = Class.forName("net.minecraft.class_243"), packet = Class.forName("net.minecraft.class_2743");
            getClient = mc.getMethod("method_1551"); onThread = mc.getMethod("method_18854");
            player = mc.getField("field_1724"); world = mc.getField("field_1687");
            connection = mc.getMethod("method_1562"); server = mc.getMethod("method_1576");
            legacy = Class.forName("dev.yinghuang.legacyforgebridge.session.LegacySessionController").getMethod("isLegacy1710");
            age = entity.getField("field_6012"); hurt = living.getField("field_6235");
            verticalCollision = entity.getField("field_5992");
            velocity = entity.getMethod("method_18798"); setVelocity = entity.getMethod("method_18800", double.class, double.class, double.class);
            getY = entity.getMethod("method_23318"); isGround = entity.getMethod("method_24828");
            isWater = entity.getMethod("method_5799"); isLava = entity.getMethod("method_5771");
            isClimbing = living.getMethod("method_6101"); hasVehicle = entity.getMethod("method_5765");
            abilities = human.getMethod("method_31549"); flying = Class.forName("net.minecraft.class_1656").getField("field_7479");
            getId = entity.getMethod("method_5628");
            vectorX = vec.getField("field_1352"); vectorY = vec.getField("field_1351"); vectorZ = vec.getField("field_1350");
            packetId = packet.getMethod("method_11818"); packetVelocity = packet.getMethod("method_73085");
        }
        Object client() throws ReflectiveOperationException { return getClient.invoke(null); }
        boolean onThread(Object c) throws ReflectiveOperationException { return (Boolean) onThread.invoke(c); }
        long age(Object p) throws ReflectiveOperationException { return age.getInt(p); }
        int id(Object p) throws ReflectiveOperationException { return ((Number) getId.invoke(p)).intValue(); }
        double y(Object p) throws ReflectiveOperationException { return ((Number) getY.invoke(p)).doubleValue(); }
        double vy(Object p) throws ReflectiveOperationException { return vectorY.getDouble(velocity.invoke(p)); }
        boolean ground(Object p) throws ReflectiveOperationException { return (Boolean) isGround.invoke(p); }
        boolean safe(Object p) throws ReflectiveOperationException {
            return hurt.getInt(p) == 0 && !(Boolean) isWater.invoke(p) && !(Boolean) isLava.invoke(p)
                    && !(Boolean) isClimbing.invoke(p) && !(Boolean) hasVehicle.invoke(p)
                    && !flying.getBoolean(abilities.invoke(p));
        }
    }
}
