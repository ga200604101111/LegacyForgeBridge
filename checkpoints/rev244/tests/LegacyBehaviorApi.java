package dev.yinghuang.legacyforgebridge.behavior;

/** TEST DOUBLE ONLY: no original mod or converted handler is loaded by this suite. */
public final class LegacyBehaviorApi {
    public interface EventProgram { void run(Event event); }
    public static final class Event {
        public Living entityLiving = new Living();
        public double distance;
        public boolean canceled;
        public RuntimeException diagnosticFailure;
        public boolean isCanceled() {
            if (diagnosticFailure != null) throw diagnosticFailure;
            return canceled;
        }
    }
    public static final class Living {
        public Object handle;
        public double field_70165_t,field_70163_u,field_70161_v;
        public double field_70159_w,field_70181_x,field_70179_y;
        public boolean field_70133_I;
        public World field_70170_p = new World();
    }
    public static final class World {
        public final java.util.List<Object> particles = new java.util.ArrayList<>();
    }
}
