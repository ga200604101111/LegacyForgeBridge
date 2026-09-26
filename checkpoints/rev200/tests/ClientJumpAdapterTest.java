import dev.yinghuang.legacyforgebridge.behavior.*;
import dev.yinghuang.legacyforgebridge.session.LegacySessionController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Calls actual new adapter bodies against EXPLICIT RECORDING DOUBLES, never a Minecraft launch. */
public final class ClientJumpAdapterTest {
    static int checks;
    static String mode;
    static Minecraft mc = Minecraft.getInstance();
    static void check(boolean b, String text) { checks++; if (!b) throw new AssertionError(text); }
    static void close(double actual, double expected, String text) { check(Math.abs(actual-expected)<1e-9, text+" "+actual+" != "+expected); }
    static LocalPlayer fixture() {
        mc.level = new Level(true); mc.player = new LocalPlayer(mc.level); mc.connection = new Object();
        mc.singleplayer = null; mc.mainThread = true; LegacySessionController.legacy = true;
        mc.player.tickCount = 100; mc.player.y = 64; mc.player.ground = true;
        mc.player.setDeltaMovement(0, .42, 0); return mc.player;
    }
    static Vec3 jump(LocalPlayer p) {
        int beforeCalls = LegacyBehaviorRuntime.calls;
        LegacyClientJumpMotion.sourceJump(p);
        check(LegacyBehaviorRuntime.calls == beforeCalls+1, "source called once");
        close(p.getDeltaMovement().y, .57, "original source boost retained");
        Vec3 launch = p.getDeltaMovement();
        p.tickCount = 101; p.ground = false; p.y = 64.57; p.setDeltaMovement(0, .4802, 0);
        return launch;
    }
    static void packet(LocalPlayer p, Vec3 v) {
        var pkt = new ClientboundSetEntityMotionPacket(p.getId(), v);
        LegacyClientJumpMotion.velocityHead(pkt);
        p.setDeltaMovement(v.x, v.y, v.z); // Recorded vanilla boundary; not the real packet implementation.
        LegacyClientJumpMotion.velocityTail(pkt);
    }
    public static void main(String[] args) {
        mode = args[0]; LegacyClientJumpMotion.initialize(); LegacyClientJumpMotion.initialize();
        check(ClientTickEvents.END_CLIENT_TICK.listeners.size()==1, "single tick registration");
        var p = fixture(); var launch = jump(p); packet(p, launch);
        close(p.getDeltaMovement().y, mode.equals("reconcile") ? .4802 : .57, "configured mode");
        packet(p, launch); close(p.getDeltaMovement().y, .57, "second velocity not suppressed");
        p = fixture(); jump(p); packet(p, new Vec3(.4,.7,-.2));
        close(p.getDeltaMovement().x,.4,"knockback X"); close(p.getDeltaMovement().y,.7,"different impulse Y");
        close(p.getDeltaMovement().z,-.2,"knockback Z");
        p = fixture(); launch = jump(p); LegacyClientJumpMotion.barrier(); packet(p, launch);
        close(p.getDeltaMovement().y,.57,"barrier preserves packet");
        p = fixture(); launch = jump(p); p.hurtTime=1; packet(p, launch);
        close(p.getDeltaMovement().y,.57,"hurt preserves packet");
        p = fixture(); launch = jump(p); p.water=true; packet(p, launch);
        close(p.getDeltaMovement().y,.57,"water preserves packet");
        p = fixture(); launch = jump(p); mc.connection=new Object(); packet(p, launch);
        close(p.getDeltaMovement().y,.57,"changed connection preserves packet");
        p = fixture(); launch = jump(p); LegacySessionController.legacy=false; packet(p, launch);
        close(p.getDeltaMovement().y,.57,"modern target preserves packet");
        p = fixture(); launch = jump(p); mc.singleplayer=new Object(); packet(p, launch);
        close(p.getDeltaMovement().y,.57,"integrated server preserves packet");
        p = fixture(); launch = jump(p); p.tickCount=109; packet(p, launch);
        close(p.getDeltaMovement().y,.57,"old record expires");
        p = fixture(); launch = jump(p);
        var pkt = new ClientboundSetEntityMotionPacket(p.getId(),launch);
        LegacyClientJumpMotion.velocityHead(pkt); p.setDeltaMovement(.2,.8,.1);
        LegacyClientJumpMotion.velocityTail(pkt); close(p.getDeltaMovement().y,.8,"other handler result retained");
        p = fixture(); launch = jump(p); pkt = new ClientboundSetEntityMotionPacket(p.getId(),launch);
        mc.mainThread=false; LegacyClientJumpMotion.velocityHead(pkt); LegacyClientJumpMotion.velocityTail(pkt);
        close(p.getDeltaMovement().y,.4802,"Netty callback not applied");
        mc.mainThread=true; packet(p,launch);
        close(p.getDeltaMovement().y,mode.equals("reconcile")?.4802:.57,"main-thread scheduling path");
        p = fixture(); launch = jump(p); pkt = new ClientboundSetEntityMotionPacket(p.getId()+1,launch);
        LegacyClientJumpMotion.velocityHead(pkt); LegacyClientJumpMotion.velocityTail(pkt);
        close(p.getDeltaMovement().y,.4802,"other entity not modified");
        p = fixture(); launch=jump(p); mc.level=null; mc.player=null;
        ClientTickEvents.END_CLIENT_TICK.fire(mc); mc.level=p.level(); mc.player=p;
        packet(p,launch); close(p.getDeltaMovement().y,.57,"disconnect clears pending");
        p=fixture(); LegacyClientJumpMotion.sourceJump(p); LegacyClientJumpMotion.sourceJump(p);
        close(p.getDeltaMovement().y,.72,"duplicate source call is observed, not silently removed");
        p.ground=false; p.y=64.72; p.tickCount=101; p.setDeltaMovement(0,.60,0);
        packet(p,new Vec3(0,.72,0)); close(p.getDeltaMovement().y,.72,"ambiguous duplicate callback not reconciled");
        var serverEntity = new LivingEntity(new Level(false)); serverEntity.setDeltaMovement(0,.42,0);
        int calls=LegacyBehaviorRuntime.calls; LegacyClientJumpMotion.sourceJump(serverEntity);
        check(LegacyBehaviorRuntime.calls==calls+1,"integrated original handler executes once");
        close(serverEntity.getDeltaMovement().y,.57,"server delegate unchanged");
        System.out.println("PASS recording-double adapter mode="+mode+" assertions="+checks);
    }
}
