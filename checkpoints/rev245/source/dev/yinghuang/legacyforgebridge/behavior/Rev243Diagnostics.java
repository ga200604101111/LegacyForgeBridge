package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only client snapshots. All lookups are exact-signature intermediary lookups.
 * Optional hook/read failures are reported, not substituted with fabricated values.
 * The only game-facing non-read call is a LOCAL chat notice for /lfbtrace controls.
 */
public final class Rev243Diagnostics {
    private static final Map<String,Boolean> HOOKS=new ConcurrentHashMap<>();
    private static final Set<String> WARNED=ConcurrentHashMap.newKeySet();
    private static final AtomicLong CAMERAS=new AtomicLong(), MOVES=new AtomicLong(), STARTS=new AtomicLong();
    private static final ThreadLocal<ArrayDeque<Move>> MOVE=ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile boolean installed;
    private static volatile ReadApi readApi;
    private static long tickStartNs, previousFrameNs, captureEndTicks, sampleCostNs, mark;
    private static Object tickPlayer;
    private static boolean previousGround;
    private static String lastInput="",lastEffects="";
    private record Move(long id,long ns,Object entity,double x,double y,double z,String requested,String before) {}
    private Rev243Diagnostics() {}

    public static synchronized void install() {
        if(installed)return;
        installed=true;
        register("tickStart","net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents",
                "START_CLIENT_TICK","net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$StartTick",
                (proxy,method,args)->{tickStart(args[0]);return null;});
        register("localCommands","net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents",
                "ALLOW_COMMAND","net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents$AllowCommand",
                (proxy,method,args)->allowCommand((String)args[0]));
        LegacyMotionTraceLog.notice("rev245 DEEP CORRELATION DIAGNOSTICS; velocity heuristics disabled; motion capture auto-starts on legacy join; /lfbtrace stop|start|mark|status");
    }
    private static void register(String name,String holder,String field,String listener,InvocationHandler h) {
        try {
            Class<?> type=Class.forName(listener);
            Object proxy=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,args)-> {
                if(m.getDeclaringClass()==Object.class)return switch(m.getName()) {
                    case "toString"->"LFB-rev245-"+name;
                    case "hashCode"->System.identityHashCode(p);
                    case "equals"->p==args[0];
                    default->null;
                };
                return h.invoke(p,m,args);
            });
            Object event=Class.forName(holder).getField(field).get(null);
            Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register",Object.class).invoke(event,proxy);
            HOOKS.put(name,true);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { HOOKS.put(name,false);failure(name,e); }
    }
    static void captureStarted() {
        // Observations belong to this capture, not the lifetime of the JVM.
        CAMERAS.set(0);MOVES.set(0);STARTS.set(0);
        WARNED.clear();MOVE.remove();
        captureEndTicks=0;sampleCostNs=0;tickStartNs=0;previousFrameNs=0;lastInput="";lastEffects="";tickPlayer=null;
        LegacyMotionTraceLog.event("HOOKS",HOOKS.toString()+" cameraAndMove=await_first_invocation");
        LegacyMotionTraceLog.event("DIAGNOSTIC_CONFIG","reconciliation=hard_disabled diagnosticSchema=rev245-deep.1 coverageCounters=capture_local sourceFlag=velocityChanged:field_70133_I correlation=wire_exact+packet_object_exact+vector_time_candidate traceAutoStart=true partMiB=16 captureMiB=128 writerThread=LFB-motion-log-writer flushMilliseconds=500");
        LegacyMotionTraceLog.event("KNOWN_LIMITATIONS","server_tick_and_causal_id=unavailable via_to_packet_identity=vector_time_candidate_not_causal camera=Camera.update_return_not_final_bob_or_shader_matrix sourceParticles=queue_not_GPU allPacketTypes=false");
    }
    public static boolean isLocal(Object entity) {
        if(entity==null)return false;
        try {
            var a=Rev242ClientAccess.get();Object c=a.client();
            return a.onThread(c)&&a.legacyMultiplayer(c)&&a.player.get(c)==entity&&LegacyMotionTraceLog.local(a.id(entity));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { return false; }
    }
    private static Object local(Object c) throws ReflectiveOperationException {
        var a=Rev242ClientAccess.get();
        if(!a.onThread(c)||!a.legacyMultiplayer(c))return null;
        Object p=a.player.get(c);return LegacyMotionTraceLog.local(a.id(p))?p:null;
    }
    private static ReadApi api() throws ReflectiveOperationException {
        ReadApi r=readApi;
        if(r==null)synchronized(Rev243Diagnostics.class) { r=readApi;if(r==null)readApi=r=new ReadApi(); }
        return r;
    }
    public static void tickStart(Object client) {
        if(!LegacyMotionTraceLog.active())return;
        long begin=System.nanoTime();
        try {
            Object p=local(client);if(p==null)return;
            STARTS.incrementAndGet();
            tickStartNs=begin;
            if(!MOVE.get().isEmpty()) { LegacyMotionTraceLog.event("MOVE_TAIL_MISSING","count="+MOVE.get().size());MOVE.get().clear(); }
            LegacyMotionTraceLog.event("TICK_START",state(p));
            inputs(client,p,"start");
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { failure("tick-start",e); }
        finally { sampleCostNs+=System.nanoTime()-begin; }
    }
    public static void tickEnd(Object client) {
        if(!LegacyMotionTraceLog.active())return;
        long begin=System.nanoTime();
        try {
            Object p=local(client);if(p==null)return;
            var a=Rev242ClientAccess.get();boolean ground=a.ground(p);
            if(p!=tickPlayer) { tickPlayer=p;previousGround=ground; }
            LegacyMotionTraceLog.event("TICK_SNAPSHOT_END",state(p)+" observedTickNs="+(tickStartNs==0?-1:begin-tickStartNs));
            if(previousGround!=ground)LegacyMotionTraceLog.event(ground?"GROUND_CONTACT":"LEAVE_GROUND",state(p));
            previousGround=ground;inputs(client,p,"end");
            String effects=api().optionalEffects(p);
            if(!effects.equals(lastEffects)) { lastEffects=effects;LegacyMotionTraceLog.event("EFFECTS_CHANGED","effects="+effects); }
            if(++captureEndTicks%100==0) {
                LegacyMotionTraceLog.event("COVERAGE_HEALTH","startTickCalls="+STARTS.get()+" cameraCalls="+CAMERAS.get()
                        +" moveCalls="+MOVES.get()+" observerSampleNs="+sampleCostNs+" "+LegacyMotionTraceLog.stats());
                if(STARTS.get()==0)LegacyMotionTraceLog.event("HOOK_NOT_OBSERVED","hook=tickStart reason=not_observed_or_registration_missing");
                if(CAMERAS.get()==0)LegacyMotionTraceLog.event("HOOK_NOT_OBSERVED","hook=camera reason=not_rendered_or_injection_missing");
                if(MOVES.get()==0)LegacyMotionTraceLog.event("HOOK_NOT_OBSERVED","hook=move reason=no_move_or_injection_missing");
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { failure("tick-end",e); }
        finally { sampleCostNs+=System.nanoTime()-begin; }
    }
    private static void inputs(Object client,Object p,String phase) throws ReflectiveOperationException {
        String value=api().input(client,p);
        LegacyMotionTraceLog.event("INPUT_SNAPSHOT","phase="+phase+" "+value);
        if(!value.equals(lastInput)) { lastInput=value;LegacyMotionTraceLog.event("INPUT_CHANGED","phase="+phase+" "+value); }
    }
    private static String state(Object p) throws ReflectiveOperationException {
        var a=Rev242ClientAccess.get();var r=api();Object v=a.velocity.invoke(p);
        return "tick="+a.age(p)+" pos="+LegacyMotionTraceLog.vector(r.number(r.x,p),a.y(p),r.number(r.z,p))
                +" vel="+vector(v)+" lastY="+r.lastY.get(p)+" eyeY="+r.number(r.eyeY,p)
                +" box="+r.box.invoke(p)+" ground="+a.ground(p)+" horizontalCollision="+r.horizontal.get(p)
                +" verticalCollision="+a.verticalCollision.get(p)+" fallDistance="+r.fallDistance.get(p)
                +" hurtTime="+a.hurt.get(p)+" water="+a.isWater.invoke(p)+" lava="+a.isLava.invoke(p)
                +" climbing="+a.isClimbing.invoke(p)+" vehicle="+a.hasVehicle.invoke(p)+" flying="+a.flying.get(a.abilities.invoke(p));
    }
    private static String vector(Object v) throws ReflectiveOperationException {
        var a=Rev242ClientAccess.get();return LegacyMotionTraceLog.vector(a.vectorX.getDouble(v),a.vectorY.getDouble(v),a.vectorZ.getDouble(v));
    }
    public static void moveHead(Object entity,Object type,Object requested) {
        if(!LegacyMotionTraceLog.active()||!isLocal(entity))return;
        try {
            var r=api();var a=Rev242ClientAccess.get();long id=LegacyMotionTraceLog.next();
            var frame=new Move(id,System.nanoTime(),entity,r.number(r.x,entity),a.y(entity),r.number(r.z,entity),vector(requested),state(entity));
            MOVE.get().push(frame);MOVES.incrementAndGet();
            LegacyMotionTraceLog.event("MOVE_HEAD","move="+id+" movementType="+type+" requested="+frame.requested+" "+frame.before);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { failure("move-head",e); }
    }
    public static void moveTail(Object entity,Object type,Object requested) {
        if(!LegacyMotionTraceLog.active()||!isLocal(entity))return;
        try {
            ArrayDeque<Move> s=MOVE.get();if(s.isEmpty()){LegacyMotionTraceLog.event("MOVE_HEAD_MISSING",state(entity));return;}
            Move m=s.pop();var r=api();var a=Rev242ClientAccess.get();
            if(m.entity!=entity){LegacyMotionTraceLog.event("MOVE_PAIR_MISMATCH","move="+m.id);return;}
            LegacyMotionTraceLog.event("MOVE_TAIL","move="+m.id+" durationNs="+(System.nanoTime()-m.ns)+" requested="+m.requested
                    +" actual="+LegacyMotionTraceLog.vector(r.number(r.x,entity)-m.x,a.y(entity)-m.y,r.number(r.z,entity)-m.z)+" "+state(entity));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { failure("move-tail",e); }
    }
    /** Called from Camera.update RETURN. No camera transforms are written. */
    public static void camera(Object camera) {
        if(!LegacyMotionTraceLog.active())return;
        long now=System.nanoTime();
        try {
            var r=api();Object focus=r.cameraFocus.invoke(camera);if(!isLocal(focus))return;
            var a=Rev242ClientAccess.get();CAMERAS.incrementAndGet();
            LegacyMotionTraceLog.event("CAMERA_FRAME","tick="+a.age(focus)+" frame="+CAMERAS.get()
                    +" frameIntervalNs="+(previousFrameNs==0?-1:now-previousFrameNs)+" cameraPos="+vector(r.cameraPos.invoke(camera))
                    +" tickProgress="+r.cameraProgress.invoke(camera)+" playerY="+a.y(focus)+" lastY="+r.lastY.get(focus)
                    +" eyeY="+r.eyeY.invoke(focus)+" eyeOffset="+r.cameraY.get(camera)+" lastEyeOffset="+r.lastCameraY.get(camera)
                    +" thirdPerson="+r.cameraThird.invoke(camera)+" yaw="+r.cameraYaw.invoke(camera)+" pitch="+r.cameraPitch.invoke(camera));
            previousFrameNs=now;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { failure("camera",e); }
    }
    /** Local control only. Every unrelated command returns true without logging its text. */
    public static boolean allowCommand(String command) {
        if(command==null||!(command.equals("lfbtrace")||command.startsWith("lfbtrace ")))return true;
        String operation=command.length()==8?"status":command.substring(9).trim();
        switch(operation) {
            case "start" -> tell(LegacyMotionTraceLog.start("MANUAL_START")?"移動日誌已開始記錄。":"請先連線至 1.7.10 伺服器。");
            case "stop" -> { LegacyMotionTraceLog.stop("MANUAL_STOP");tell("移動日誌已停止；檔案在 logs/lfb-motion。"); }
            case "mark" -> { if(LegacyMotionTraceLog.active()){LegacyMotionTraceLog.event("USER_MARK","mark="+(++mark));tell("已標記異常時間點 #"+mark+"。");}else tell("日誌已停止；請先用 /lfbtrace start 開始記錄。"); }
            case "status" -> tell("移動日誌："+(LegacyMotionTraceLog.active()?"記錄中":"已停止")+"；深度關聯診斷中，速度補償已停用。"+LegacyMotionTraceLog.stats());
            default -> tell("用法：/lfbtrace start、stop、mark、status");
        }
        return false;
    }
    private static void tell(String text) {
        LegacyMotionTraceLog.notice("rev245 "+text);
        try {
            var a=Rev242ClientAccess.get();Object c=a.client(),p=a.player.get(c);if(p==null||!a.onThread(c))return;
            Class<?> t=Class.forName("net.minecraft.class_2561");Object literal=t.getMethod("method_43470",String.class).invoke(null,"[LFB] "+text);
            Class.forName("net.minecraft.class_1657").getMethod("method_7353",t,boolean.class).invoke(p,literal,false);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { failure("local-notice",e); }
    }
    private static void failure(String part,Throwable e) {
        Rev242ClientAccess.fatal(e);
        if(WARNED.add(part)) {
            String text="component="+part+" exception="+e.getClass().getSimpleName()+" message="+e.getMessage();
            LegacyMotionTraceLog.event("DIAGNOSTIC_COMPONENT_FAILED",text);
            LegacyMotionTraceLog.notice("rev245 diagnostic component unavailable: "+text);
        }
    }
    private static final class ReadApi {
        final Method x,z,eyeY,box,cameraPos,cameraFocus,cameraProgress,cameraThird,cameraYaw,cameraPitch,keyPressed;
        final Field lastY,horizontal,fallDistance,input,playerInput,options,jumpKey,sneakKey,cameraY,lastCameraY;
        final Method effects;
        ReadApi() throws ReflectiveOperationException {
            Class<?> e=Class.forName("net.minecraft.class_1297"),c=Class.forName("net.minecraft.class_4184");
            x=e.getMethod("method_23317");z=e.getMethod("method_23321");eyeY=e.getMethod("method_23320");box=e.getMethod("method_5829");
            lastY=e.getField("field_6036");horizontal=e.getField("field_5976");fallDistance=e.getField("field_6017");
            cameraPos=c.getMethod("method_71156");cameraFocus=c.getMethod("method_19331");cameraProgress=c.getMethod("method_55437");
            cameraThird=c.getMethod("method_19333");cameraYaw=c.getMethod("method_19330");cameraPitch=c.getMethod("method_19329");
            cameraY=c.getDeclaredField("field_18721");lastCameraY=c.getDeclaredField("field_18722");
            cameraY.setAccessible(true);lastCameraY.setAccessible(true);
            input=Class.forName("net.minecraft.class_746").getField("field_3913");playerInput=Class.forName("net.minecraft.class_744").getField("field_54155");
            options=Class.forName("net.minecraft.class_310").getField("field_1690");
            Class<?> o=Class.forName("net.minecraft.class_315");jumpKey=o.getField("field_1903");sneakKey=o.getField("field_1832");
            keyPressed=Class.forName("net.minecraft.class_304").getMethod("method_1434");
            Method temp;try{temp=Class.forName("net.minecraft.class_1309").getMethod("method_6026");}catch(NoSuchMethodException ex){temp=null;}
            effects=temp;
        }
        double number(Method m,Object o) throws ReflectiveOperationException{return ((Number)m.invoke(o)).doubleValue();}
        String input(Object client,Object p) throws ReflectiveOperationException {
            Object i=input.get(p),o=options.get(client);
            return "keyJump="+keyPressed.invoke(jumpKey.get(o))+" keySneak="+keyPressed.invoke(sneakKey.get(o))
                    +" consumedPlayerInput="+(i==null?"null":playerInput.get(i));
        }
        String optionalEffects(Object p) throws ReflectiveOperationException{return effects==null?"unavailable":String.valueOf(effects.invoke(p));}
    }
}
