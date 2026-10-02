package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local legacy FALL lifecycle fallback and narrowly scoped legacy positional origin.
 * Executes the EXISTING source-compiled fall event, not a hardcoded wing effect.
 * No live player position/velocity, outgoing packet, or server state is changed here.
 */
public final class Rev242LandingBridge {
    private static final AtomicLong BARRIER=new AtomicLong();
    private static final ThreadLocal<Object> FALL_ACTOR=new ThreadLocal<>();
    private static volatile Method originalFall;
    private static volatile boolean failed;
    private static Object owner,world,connection,deliveredOwner;
    private static long generation,lastTick=Long.MIN_VALUE,deliveredTick=Long.MIN_VALUE;
    private static double lastY,descended;
    private static boolean lastGround,initialized,dispatching,deliveredCanceled;
    private Rev242LandingBridge() {}
    public static void barrier() { BARRIER.incrementAndGet(); }

    /** Called only at Snapshot.fill's legacy posY assignment. All other events unchanged. */
    public static double sourceY(Object nativeEntity,double feetY) {
        return FALL_ACTOR.get()==nativeEntity ? feetY+(double)1.62F : feetY;
    }

    /** Wrapper around the pre-existing runtime method, also used by native fall hooks. */
    public static boolean fall(Object entity,double distance,float multiplier,Object damage) {
        boolean local=false;
        long tick=Long.MIN_VALUE;
        try {
            var a=Rev242ClientAccess.get(); Object c=a.client();
            local=a.onThread(c)&&a.legacyMultiplayer(c)&&a.player.get(c)==entity;
            if(local) tick=a.age(entity);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
        if(local && deliveredOwner==entity && deliveredTick==tick) return deliveredCanceled;
        if(local && dispatching) return false;
        Object previous=FALL_ACTOR.get();
        if(local) { FALL_ACTOR.set(entity); dispatching=true; deliveredOwner=entity; deliveredTick=tick; deliveredCanceled=false; }
        try {
            boolean canceled=(Boolean)original().invoke(null,entity,distance,multiplier,damage);
            if(local) {
                deliveredCanceled=canceled;
                LegacyMotionTraceLog.event("REV242_FALL_DISPATCH","tick="+tick+" distance="+distance
                        +" canceled="+canceled+" sourceOrigin=legacy-local-posY");
            }
            return canceled;
        } catch(InvocationTargetException e) {
            Rev242ClientAccess.fatal(e);
            Throwable cause=e.getCause();
            if(cause instanceof RuntimeException r) throw r;
            if(cause instanceof Error r) throw r;
            throw new IllegalStateException("Source fall dispatch failed",cause);
        } catch(ReflectiveOperationException e) {
            fail(e); return false;
        } finally {
            if(local) { if(previous==null) FALL_ACTOR.remove(); else FALL_ACTOR.set(previous); dispatching=false; }
        }
    }
    private static Method original() throws ReflectiveOperationException {
        Method m=originalFall;
        if(m==null) {
            m=Class.forName("dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRuntime")
                    .getMethod("lfb$fallBeforeRev242",Class.forName("net.minecraft.class_1309"),double.class,float.class,
                            Class.forName("net.minecraft.class_1282"));
            originalFall=m;
        }
        return m;
    }
    /** Logs source requests, not a claim that a particular renderer displayed them. */
    public static void sourceParticles(Object snapshot) {
        if(FALL_ACTOR.get()==null) return;
        try {
            Field f=snapshot.getClass().getDeclaredField("world"); f.setAccessible(true);
            LegacyBehaviorApi.World sw=(LegacyBehaviorApi.World)f.get(snapshot);
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(LegacyBehaviorApi.Particle p:sw.particles) { min=Math.min(min,p.y());max=Math.max(max,p.y()); }
            LegacyMotionTraceLog.event("REV242_FALL_PARTICLES","queued="+sw.particles.size()+" minY="+min+" maxY="+max);
        } catch(ReflectiveOperationException|RuntimeException e) { Rev242ClientAccess.fatal(e); }
    }
    public static void tick(Object client) {
        if(failed) return;
        try {
            var a=Rev242ClientAccess.get();
            if(!a.onThread(client)) return;
            if(!a.legacyMultiplayer(client)) { reset(); return; }
            Object p=a.player.get(client),w=a.world.get(client),c=a.connection.invoke(client);
            long t=a.age(p); double y=a.y(p); boolean ground=a.ground(p);
            if(p!=owner||w!=world||c!=connection||generation!=BARRIER.get()||t<lastTick) {
                reset(); owner=p;world=w;connection=c; generation=BARRIER.get();
            }
            if(!Double.isFinite(y)||!a.dry(p)) { initialized=false;descended=0;lastTick=t;return; }
            if(lastTick==t) return;
            if(initialized) {
                double dy=y-lastY;
                if(t-lastTick!=1||!Double.isFinite(dy)||Math.abs(dy)>64) descended=0;
                else {
                    if(dy<0) descended-=dy;
                    if(ground) {
                        if(!lastGround&&descended>1E-5) {
                            // Ignore cancellation here: this is presentation fallback, not server damage simulation.
                            fall(p,descended,1.0F,null);
                        }
                        descended=0;
                    }
                }
            }
            lastTick=t;lastY=y;lastGround=ground;initialized=true;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
    }
    private static void reset() {
        owner=world=connection=null; initialized=false; descended=0;lastTick=Long.MIN_VALUE;
        deliveredOwner=null;deliveredTick=Long.MIN_VALUE;deliveredCanceled=false;
    }
    private static void fail(Throwable e) {
        Rev242ClientAccess.fatal(e); failed=true; reset();
        System.getLogger("LegacyForgeBridge/fall").log(System.Logger.Level.WARNING,
                "rev242 landing fallback disabled after an API failure",e);
    }
}
