package dev.yinghuang.legacyforgebridge.behavior;

import net.minecraft.class_243;
import net.minecraft.class_2743;
import net.minecraft.class_310;
import net.minecraft.class_1297;
import net.minecraft.class_746;

public final class LegacyJumpEchoReconciler {
    private LegacyJumpEchoReconciler() { }
    private static Before before;
    private static Prediction prediction;
    private static final double MIN_CLIENT_BOOST = 0.02D;
    private static final double MATCH_EPS = 0.004D;
    private static final double REBOOST_EPS = 0.02D;
    private static final int MAX_AGE_TICKS = 4;
    private record Before(int entityId, int tick, class_243 velocity) { }
    private record Prediction(int entityId, int tick, class_243 velocity, double boost) { }

    public static synchronized void beforeLocalJumpCallback() {
        class_310 mc=class_310.method_1551();
        if(mc==null || !mc.method_18854() || mc.field_1724==null){ before=null; prediction=null; return; }
        class_746 p=mc.field_1724;
        before=new Before(p.method_5628(),p.field_6012,p.method_18798());
        prediction=null;
    }

    public static synchronized void afterLocalJumpCallback() {
        class_310 mc=class_310.method_1551();
        class_746 p=mc==null?null:mc.field_1724;
        Before b=before; before=null;
        if(b==null || p==null || !mc.method_18854() || b.entityId()!=p.method_5628() || b.tick()!=p.field_6012){ prediction=null; return; }
        class_243 after=p.method_18798();
        double boost=after.field_1351-b.velocity().field_1351;
        if(!(boost>MIN_CLIENT_BOOST) || !Double.isFinite(boost)){ prediction=null; return; }
        prediction=new Prediction(b.entityId(),b.tick(),after,boost);
        LegacyMotionTraceLog.event("JUMP_PREDICTION_ARMED","tick="+b.tick()+" predictedY="+after.field_1351+" boost="+boost);
    }

    public static synchronized boolean consumeIfPredictedEcho(class_2743 packet) {
        Prediction p=prediction;
        if(p==null || packet==null)return false;
        class_310 mc=class_310.method_1551();
        if(mc==null || !mc.method_18854() || mc.field_1724==null)return false;
        class_746 player=mc.field_1724;
        if(packet.method_11818()!=p.entityId() || player.method_5628()!=p.entityId())return false;
        int age=player.field_6012-p.tick();
        if(age<1 || age>MAX_AGE_TICKS){ if(age>MAX_AGE_TICKS || age<0)prediction=null; return false; }
        if(player.method_24828()){ prediction=null; return false; }
        class_243 incoming=packet.method_73085();
        class_243 current=player.method_18798();
        if(incoming==null || current==null || !finite(incoming) || !(incoming.field_1351>current.field_1351+REBOOST_EPS))return false;
        double v0=p.velocity().field_1351;
        double v1=(v0-0.08D)*0.98D;
        if(Math.abs(incoming.field_1351-v0)>MATCH_EPS && Math.abs(incoming.field_1351-v1)>MATCH_EPS)return false;
        ((class_1297)player).method_18800(incoming.field_1352,current.field_1351,incoming.field_1350);
        prediction=null;
        LegacyMotionTraceLog.event("SERVER_JUMP_ECHO_DEDUPED","ageTicks="+age+" incomingY="+incoming.field_1351+" currentY="+current.field_1351+" predictedY="+v0+" boost="+p.boost());
        return true;
    }

    public static synchronized void clear(String reason) {
        before=null;
        if(prediction!=null)LegacyMotionTraceLog.event("JUMP_PREDICTION_CLEARED","reason="+reason);
        prediction=null;
    }
    private static boolean finite(class_243 v){return Double.isFinite(v.field_1352)&&Double.isFinite(v.field_1351)&&Double.isFinite(v.field_1350);}
}
