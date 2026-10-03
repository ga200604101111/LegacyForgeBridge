package dev.yinghuang.legacyforgebridge.behavior;
/** TEST DOUBLE ONLY. ABI mirrors the members used by the packaged diagnostics. */
public final class LegacyBehaviorApi {
    public interface EventProgram { void run(Event event); }
    public static class Entity {
        public World field_70170_p = new World();
        public boolean field_70133_I;
        public double field_70165_t,field_70163_u,field_70161_v,field_70159_w,field_70181_x,field_70179_y;
        public Object handle;
    }
    public static class Living extends Entity { }
    public static class Event {
        public Living entityLiving = new Living();
        public float distance;
        public boolean canceled;
        public RuntimeException diagnosticFailure;
        public void setCanceled(boolean v){canceled=v;}
        public boolean isCanceled(){if(diagnosticFailure!=null)throw diagnosticFailure;return canceled;}
    }
    public static class World { public final java.util.List<Object> particles=new java.util.ArrayList<>(); }
}
