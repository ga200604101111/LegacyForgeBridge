package dev.yinghuang.legacyforgebridge.behavior;

import java.util.concurrent.atomic.AtomicLong;

/** rev242 replacement, retaining every rev241 lifecycle hook and binary signature. */
public final class Rev241JumpMotionBridge {
    private static final Rev241JumpHistory HISTORY=new Rev241JumpHistory();
    private static final AtomicLong BARRIER=new AtomicLong();
    private static final boolean ENABLED=!"off".equalsIgnoreCase(System.getProperty("legacyforgebridge.jumpReconcile","source-history"));
    private static volatile boolean failed;
    private static Object owner,world,connection;
    private static long generation,reconciled;
    private static Source source;
    private static Pending pending;
    private static boolean restoring;
    private record Source(Object player,long tick,double y,double vy,boolean eligible) {}
    private record Pending(Object packet,Object player,long tick,double y,double x,double vy,double z,
                           long generation,Rev241JumpHistory.Decision decision) {}
    private Rev241JumpMotionBridge() {}
    public static void initialize() {
        log("REV242_READY","mode="+(ENABLED?"SOURCE_HISTORY":"OFF")
                +"; unchanged source jump boost; bounded previous stationary descent guard; packet XZ preserved");
    }
    public static void sourceBefore(Object entity) {
        if(!ENABLED||failed) return;
        try {
            var a=Rev242ClientAccess.get(); Object c=a.client();
            if(!a.onThread(c)) return;
            source=null;
            if(!bind(a,c)||entity!=owner) return;
            epoch(); HISTORY.newJump(); pending=null;
            source=new Source(entity,a.age(entity),a.y(entity),a.vy(entity),a.ground(entity)&&a.safe(entity));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
    }
    public static void sourceAfter(Object entity) {
        if(!ENABLED||failed) return;
        try {
            var a=Rev242ClientAccess.get(); Object c=a.client();
            if(!a.onThread(c)) return;
            Source s=source; source=null;
            if(s==null||s.player()!=entity||!bind(a,c)) return;
            boolean eligible=s.eligible()&&a.safe(entity)&&a.ground(entity)&&a.age(entity)==s.tick()
                    &&Math.abs(a.y(entity)-s.y())<1E-9;
            if(HISTORY.arm(s.tick(),s.vy(),a.vy(entity),eligible)) {
                HISTORY.horizontal(a.stationary(entity)); generation=BARRIER.get();
                log("REV242_ARM","tick="+s.tick()+" beforeY="+s.vy()+" sourceY="+a.vy(entity));
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
    }
    public static void nativeVelocity(Object entity,Object requested) {
        if(!ENABLED||failed||!HISTORY.active()||source!=null||restoring||pending!=null) return;
        try {
            var a=Rev242ClientAccess.get(); Object c=a.client();
            if(!a.onThread(c)||!bind(a,c)||entity!=owner||!epoch()) return;
            boolean still=a.stationary(entity)&&Math.abs(a.vectorX.getDouble(requested))<=1E-8
                    &&Math.abs(a.vectorZ.getDouble(requested))<=1E-8;
            HISTORY.horizontal(still);
            // Capture only genuine descending ground contact, before zeroing Y.
            if(a.ground(entity)&&a.vy(entity)<0) {
                HISTORY.land(a.age(entity),a.vy(entity),a.safe(entity)); return;
            }
            HISTORY.observeVelocity(a.age(entity),a.vy(entity),a.vectorY.getDouble(requested),a.safe(entity));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
    }
    public static void nativePosition(Object entity,double x,double y,double z) {
        if(!ENABLED||failed||!HISTORY.active()||source!=null||pending!=null) return;
        try {
            var a=Rev242ClientAccess.get(); Object c=a.client();
            if(!a.onThread(c)||!bind(a,c)||entity!=owner||!epoch()) return;
            HISTORY.horizontal(a.stationary(entity));
            HISTORY.observePosition(a.vy(entity),y-a.y(entity));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
    }
    public static void tick(Object client) {
        // Landing effects must work even when motion reconciliation is switched off.
        Rev242LandingBridge.tick(client);
        if(!ENABLED||failed||(owner==null&&!HISTORY.active())) return;
        try {
            var a=Rev242ClientAccess.get();
            if(!a.onThread(client)||!bind(a,client)) return;
            if(pending!=null) { clear("missing-packet-tail"); return; }
            if(!epoch()) return;
            if(HISTORY.active()&&a.ground(owner)) HISTORY.land(a.age(owner),a.vy(owner),a.safe(owner));
            else HISTORY.tick(a.age(owner),a.vy(owner),a.safe(owner)&&!a.ground(owner));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
    }
    public static void packetHead(Object packet) {
        if(!ENABLED||failed) return;
        try {
            var a=Rev242ClientAccess.get(); Object c=a.client();
            if(!a.onThread(c)||!bind(a,c)||((Number)a.packetId.invoke(packet)).intValue()!=a.id(owner)) return;
            if(pending!=null) { clear("nested-or-missing-packet-tail"); return; }
            epoch(); Object v=a.packetVelocity.invoke(packet);
            double x=a.vectorX.getDouble(v),y=a.vectorY.getDouble(v),z=a.vectorZ.getDouble(v);
            HISTORY.horizontal(a.stationary(owner));
            boolean wasActive=HISTORY.active();
            var d=HISTORY.decide(a.age(owner),a.vy(owner),x,y,z,a.safe(owner)&&!a.ground(owner)
                    &&!a.verticalCollision.getBoolean(owner));
            pending=new Pending(packet,owner,a.age(owner),a.y(owner),x,y,z,BARRIER.get(),d);
            if(wasActive&&!d.reconcile()) log("REV242_PASS","incomingY="+y+" reason="+d.reason());
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
    }
    public static void packetTail(Object packet) {
        if(!ENABLED||failed) return;
        try {
            var a=Rev242ClientAccess.get(); Object c=a.client();
            if(!a.onThread(c)) return;
            Pending p=pending;
            if(p==null||p.packet()!=packet) return;
            if(!p.decision().reconcile()) { pending=null; return; }
            if(!bind(a,c)||p.player()!=owner||p.generation()!=BARRIER.get()||a.age(owner)!=p.tick()
                    ||a.y(owner)!=p.y()||!a.safe(owner)||a.ground(owner)) { clear("changed-during-packet"); return; }
            Object v=a.velocity.invoke(owner);
            double x=a.vectorX.getDouble(v),y=a.vectorY.getDouble(v),z=a.vectorZ.getDouble(v);
            if(x!=p.x()||y!=p.vy()||z!=p.z()) { clear("other-packet-handler-modified-velocity"); return; }
            restoring=true;
            try { a.setVelocity.invoke(owner,x,p.decision().replacementY(),z); }
            finally { restoring=false; pending=null; }
            log("REV242_RECONCILED","tick="+p.tick()+" incomingY="+y+" retainedY="+p.decision().replacementY()
                    +" matchedPhase="+p.decision().matchedPhase()+" currentPhase="+p.decision().currentPhase()
                    +" reason="+p.decision().reason()+" xzPreserved=true count="+(++reconciled));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { fail(e); }
    }
    public static void barrier(String reason) { BARRIER.incrementAndGet(); Rev242LandingBridge.barrier(); }
    private static boolean epoch() {
        if(generation!=BARRIER.get()) { clear("damage-explosion-position-barrier"); generation=BARRIER.get(); return false; }
        return true;
    }
    private static void clear(String why) { HISTORY.clear(why); pending=null; }
    private static boolean bind(Rev242ClientAccess a,Object c) throws ReflectiveOperationException {
        Object p=a.player.get(c),w=a.world.get(c),n=a.connection.invoke(c);
        if(!a.legacyMultiplayer(c)) { clear("not-legacy-multiplayer"); owner=world=connection=null; source=null; return false; }
        if(p!=owner||w!=world||n!=connection) {
            clear("new-session-player-or-world"); source=null; owner=p; world=w; connection=n; generation=BARRIER.get();
        }
        return true;
    }
    private static void fail(Throwable e) {
        Rev242ClientAccess.fatal(e); failed=true; clear("reflection-or-linkage-failure"); source=null; restoring=false;
        System.getLogger("LegacyForgeBridge/jump").log(System.Logger.Level.WARNING,
                "rev242 motion adapter disabled; complete server velocity retained",e);
    }
    private static void log(String stage,String text) { LegacyMotionTraceLog.event(stage,text); }
}
