package dev.yinghuang.legacyforgebridge.behavior;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.session.LegacySessionController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Client-only experimental reconciliation. The source callback and the server remain unchanged. */
public final class LegacyClientJumpMotion {
    private enum Mode { OFF, OBSERVE, RECONCILE }
    // Until a live packet trace and game test are available, matching does not change motion by default.
    private static final Mode MODE = mode(System.getProperty("legacyforgebridge.jumpEcho", "observe"));
    private static final boolean TRACE = Boolean.getBoolean("legacyforgebridge.jumpTrace");
    private static final LegacyJumpEchoWindow WINDOW = new LegacyJumpEchoWindow();
    private static Object level, connection;
    private static LocalPlayer player;
    private static boolean initialized;
    private static int traceLines;
    private static long lastJumpTick = Long.MIN_VALUE;
    private static double launchY;
    private static final ThreadLocal<PacketFrame> PACKET = new ThreadLocal<>();
    private record PacketFrame(ClientboundSetEntityMotionPacket packet, LocalPlayer player,
                               Object level, Object connection, Vec3 velocity, double y, int tick) { }
    private LegacyClientJumpMotion() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(LegacyClientJumpMotion::tick);
        LegacyForgeBridge.LOGGER.info("LFB client jump echo mode={}; this is an experimental matcher, not proven packet causality", MODE);
    }
    private static Mode mode(String text) {
        return switch (text.toLowerCase(java.util.Locale.ROOT)) {
            case "off" -> Mode.OFF;
            case "reconcile" -> Mode.RECONCILE;
            default -> Mode.OBSERVE;
        };
    }
    private static LegacyJumpEchoWindow.Velocity vector(Vec3 v) {
        return new LegacyJumpEchoWindow.Velocity(v.x, v.y, v.z);
    }
    private static boolean context(Minecraft mc) {
        if (MODE == Mode.OFF || !mc.isSameThread() || !LegacySessionController.isLegacy1710()
                || mc.player == null || mc.level == null || mc.getConnection() == null
                || mc.getSingleplayerServer() != null || mc.player.level() != mc.level) {
            reset(); return false;
        }
        if (player != mc.player || level != mc.level || connection != mc.getConnection()) {
            reset(); player = mc.player; level = mc.level; connection = mc.getConnection(); traceLines = 0;
        }
        return true;
    }
    private static boolean unsafe(LocalPlayer p) {
        return p.isRemoved() || !p.isAlive() || p.isPassenger() || p.isSpectator()
                || p.getAbilities().flying || p.isFallFlying() || p.isInWater() || p.isInLava()
                || p.onClimbable() || p.hurtTime > 0 || p.verticalCollision && !p.onGround()
                || p.hasEffect(MobEffects.LEVITATION) || p.hasEffect(MobEffects.SLOW_FALLING);
    }
    private static void reset() {
        WINDOW.clear(); PACKET.remove(); player = null; level = null; connection = null;
        lastJumpTick = Long.MIN_VALUE;
    }

    /** Replaces just the existing mixin's callback expression, never the vanilla jump method. */
    public static void sourceJump(LivingEntity entity) {
        if (!entity.level().isClientSide()) { LegacyBehaviorRuntime.jump(entity); return; }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || entity != mc.player) { LegacyBehaviorRuntime.jump(entity); return; }
        boolean eligible = context(mc) && !unsafe(mc.player);
        Vec3 before = entity.getDeltaMovement();
        LegacyBehaviorRuntime.jump(entity); // Exactly once; do not replace or lower its source boost.
        if (!eligible) { WINDOW.clear(); return; }
        Vec3 after = entity.getDeltaMovement();
        boolean repeated = lastJumpTick == mc.player.tickCount;
        lastJumpTick = mc.player.tickCount; launchY = mc.player.getY();
        // Repeated calls are diagnostic evidence, not grounds to suppress a legitimate source event.
        boolean armed = !repeated && WINDOW.begin(mc.player.tickCount, mc.player.getY(), vector(before), vector(after));
        if (repeated) WINDOW.clear();
        trace("jump", "tick=" + lastJumpTick + " beforeY=" + before.y + " afterY=" + after.y
                + " sameTickRepeat=" + repeated + " armed=" + armed);
    }
    private static void tick(Minecraft mc) {
        if (!mc.isSameThread() || !context(mc)) return;
        // Release an incomplete HEAD frame if another handler aborted before TAIL.
        PACKET.remove();
        boolean tracked = WINDOW.active();
        WINDOW.sample(mc.player.tickCount, mc.player.getY(), vector(mc.player.getDeltaMovement()),
                mc.player.onGround(), unsafe(mc.player));
        if (tracked) trace("sample", "tick=" + mc.player.tickCount + " rise=" + (mc.player.getY() - launchY)
                + " velocityY=" + mc.player.getDeltaMovement().y + " active=" + WINDOW.active());
    }
    public static void velocityHead(ClientboundSetEntityMotionPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        // Vanilla schedules the same packet again on the render thread. Never capture on Netty.
        if (!mc.isSameThread()) return;
        PACKET.remove();
        if (!context(mc) || packet.getId() != mc.player.getId()) return;
        PACKET.set(new PacketFrame(packet, mc.player, mc.level, mc.getConnection(),
                mc.player.getDeltaMovement(), mc.player.getY(), mc.player.tickCount));
    }
    public static void velocityTail(ClientboundSetEntityMotionPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;
        PacketFrame frame = PACKET.get(); PACKET.remove();
        if (frame == null || frame.packet() != packet) return;
        if (!context(mc) || frame.player() != mc.player || frame.level() != mc.level
                || frame.connection() != mc.getConnection() || frame.tick() != mc.player.tickCount) {
            WINDOW.clear(); return;
        }
        Vec3 incoming = packet.getMovement(), applied = mc.player.getDeltaMovement();
        // Another handler or Mixin may own this update; do not overwrite its result.
        if (Double.compare(incoming.x, applied.x) != 0 || Double.compare(incoming.y, applied.y) != 0
                || Double.compare(incoming.z, applied.z) != 0) { WINDOW.clear(); return; }
        var decision = WINDOW.receive(frame.tick(), frame.y(), vector(frame.velocity()), vector(incoming),
                mc.player.onGround(), unsafe(mc.player));
        if (decision.candidate() && MODE == Mode.RECONCILE) {
            var v = decision.velocity();
            mc.player.setDeltaMovement(v.x(), v.y(), v.z());
        }
        trace("motion", "tick=" + frame.tick() + " age=" + decision.ageTicks()
                + " beforeY=" + frame.velocity().y + " packetY=" + incoming.y
                + " candidate=" + decision.candidate() + " mode=" + MODE + " reason=" + decision.reason());
    }
    /** Any correction, damage/explosion context or respawn discards the possible echo. No packet is cancelled. */
    public static void barrier() { barrier("context"); }
    public static void barrier(String reason) {
        if (Minecraft.getInstance().isSameThread()) {
            if (WINDOW.active() || PACKET.get() != null) trace("barrier", reason);
            WINDOW.clear(); PACKET.remove();
        }
    }
    private static void trace(String kind, String message) {
        if (TRACE && traceLines++ < 4096)
            LegacyForgeBridge.LOGGER.info("LFB jump-trace {} {}", kind, message);
    }
}
