package dev.yinghuang.legacyforgebridge.behavior;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** rev245 deep-correlation trace ABI; trace data is never a movement decision. */
public final class LegacyMotionTraceLog {
    static final String VERSION="0.2.0-alpha.27-corpus4-local.28-rev245-deepdiag.1";
    private static final AtomicLong SEQUENCE=new AtomicLong(), CAPTURES=new AtomicLong();
    private static volatile Consumer<String> sink=s->{};
    private static volatile int localEntity=Integer.MIN_VALUE;
    private static volatile long session,jump,capture,captureStartNs;
    private static volatile Rev243TraceWriter writer;
    private static final java.util.concurrent.ConcurrentLinkedQueue<Rev243TraceWriter> WRITERS=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static volatile boolean initialized;
    private static Path directory;
    private LegacyMotionTraceLog() {}
    public static synchronized void initialize(Consumer<String> consumer) {
        sink=Objects.requireNonNull(consumer);
        if(initialized)return;
        initialized=true;
        try {
            Class<?> loader=Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance=loader.getMethod("getInstance").invoke(null);
            directory=((Path)loader.getMethod("getGameDir").invoke(instance)).resolve("logs/lfb-motion");
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) {
            directory=Path.of("logs","lfb-motion");
            notice("rev245 game directory unavailable; using working-directory logs/lfb-motion");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(()-> {
            long deadline=System.nanoTime()+2_000_000_000L;
            for(var w:WRITERS)w.requestClose("JVM_SHUTDOWN");
            for(var w:WRITERS)try { w.awaitClosed(Math.max(1,(deadline-System.nanoTime())/1_000_000)); }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        },"LFB-motion-log-close"));
        Rev243Diagnostics.install();
    }
    public static synchronized void session(int entity) {
        stop("SESSION_CHANGE");localEntity=entity;session++;jump=0;
        start("AUTO_SESSION_START");
    }
    public static synchronized void close() { stop("DISCONNECT");localEntity=Integer.MIN_VALUE;jump=0; }
    public static void arm(long id) { jump=id; }
    public static boolean active() { Rev243TraceWriter w=writer;return localEntity!=Integer.MIN_VALUE&&w!=null&&w.accepting(); }
    public static boolean local(int entity) { return active()&&entity==localEntity; }
    public static long next() { return SEQUENCE.incrementAndGet(); }
    public static long jump() { return jump; }
    public static void event(String stage,String detail) {
        Rev243TraceWriter w=writer;
        if(w==null||!w.accepting()||localEntity==Integer.MIN_VALUE)return;
        long ns=System.nanoTime(),seq=next();
        String annotation;
        try { annotation=Rev245VelocityCorrelation.decorate(stage,detail,ns,jump); }
        catch(RuntimeException|LinkageError ignored) { annotation=" corrDiagnosticError=true"; }
        w.offer("seq="+seq+" ns="+ns+" captureAgeNs="+(captureStartNs==0?-1:ns-captureStartNs)
                +" utc="+Instant.now()+" session="+session+" capture="+capture+" localJump="+jump
                +" entity="+localEntity+" threadId="+Thread.currentThread().threadId()
                +" thread="+oneLine(Thread.currentThread().getName())
                +" stage="+oneLine(stage)+" "+oneLine(detail)+annotation);
    }
    /** ABI retained: high-volume disk work no longer runs on the tick thread. */
    public static void drain() {}
    public static String vector(double x,double y,double z) { return "["+x+","+y+","+z+"]"; }
    public static String stack() {
        return StackWalker.getInstance().walk(s->s.filter(f->!f.getClassName().contains("LegacyMotionTraceLog")
                &&!f.getClassName().contains("LegacyClientJumpMotion")).limit(8)
                .map(f->f.getClassName()+"."+f.getMethodName()+":"+f.getLineNumber()).collect(Collectors.joining(" <- ")));
    }
    public static LegacyBehaviorApi.EventProgram wrap(String mod,String event,String item,LegacyBehaviorApi.EventProgram program) {
        Objects.requireNonNull(program);
        return e-> {
            boolean trace=active()&&e!=null&&e.entityLiving!=null&&Rev243Diagnostics.isLocal(e.entityLiving.handle);
            if(trace)safeHandler("SOURCE_HANDLER_HEAD",mod,event,item,program,e);
            boolean success=false;
            try { program.run(e);success=true; }
            finally { if(trace)safeHandler(success?"SOURCE_HANDLER_TAIL":"SOURCE_HANDLER_THROW",mod,event,item,program,e); }
        };
    }
    private static void safeHandler(String stage,String mod,String kind,String item,Object p,LegacyBehaviorApi.Event e) {
        try {
            var ent=e.entityLiving;
            event(stage,"kind="+kind+" mod="+mod+" targetItem="+item+" program="+p.getClass().getName()
                    +" sourcePos="+vector(ent.field_70165_t,ent.field_70163_u,ent.field_70161_v)
                    +" sourceVel="+vector(ent.field_70159_w,ent.field_70181_x,ent.field_70179_y)
                    +" velocityChanged="+ent.field_70133_I+" canceled="+e.isCanceled()+" distance="+e.distance
                    +" queuedParticles="+(ent.field_70170_p==null?0:ent.field_70170_p.particles.size()));
        } catch(RuntimeException|LinkageError ignored) { /* Diagnostics must not skip or mask a source program. */ }
    }
    /** Called after the source World queue accepted a particle, not after GPU rendering. */
    public static void sourceParticle(String type,double x,double y,double z,double dx,double dy,double dz) {
        if(active())event("SOURCE_PARTICLE_QUEUED","type="+oneLine(type)+" pos="+vector(x,y,z)+" velocity="+vector(dx,dy,dz)
                +" scope=converted-source-world rendererVisibility=unknown");
    }
    public static synchronized boolean start(String reason) {
        if(localEntity==Integer.MIN_VALUE)return false;
        if(writer!=null&&writer.accepting())return true;
        if(directory==null)directory=Path.of("logs","lfb-motion");
        capture=CAPTURES.incrementAndGet();captureStartNs=System.nanoTime();
        String prefix="motion-"+Instant.now().toString().replace(':','-')+"-"+Long.toUnsignedString(captureStartNs,36)+"-"+capture;
        writer=new Rev243TraceWriter(directory,prefix,"version="+VERSION+" session="+session+" capture="+capture+" entity="+localEntity,sink);
        Rev245VelocityCorrelation.captureStarted(capture,captureStartNs);
        WRITERS.removeIf(Rev243TraceWriter::isClosed);WRITERS.add(writer);
        event("CAPTURE_START","reason="+reason+" heuristicReconciliation=DISABLED sourceJumpPreserved=true correlation=rev245-deep.1");
        event("COVERAGE","scope=local-client motionWrites=existing-hooks wire=existing-via-hooks correlation=wire-exact+packetObject-exact+vector-time-candidate tick=start+end camera=optional-return-hook move=optional-head+return-hook particle=source-queue-only serverCause=unavailable");
        Rev243Diagnostics.captureStarted();
        return true;
    }
    public static synchronized void stop(String why) {
        Rev243TraceWriter w=writer;
        if(w!=null) { if(w.accepting())event("CAPTURE_STOP","reason="+why+" "+Rev245VelocityCorrelation.summary());w.requestClose(why); }
    }
    public static String stats() { var w=writer;return w==null?"no_capture":w.stats()+" "+Rev245VelocityCorrelation.status(); }
    static void notice(String s) { try { sink.accept(s); }catch(RuntimeException ignored){} }
    static String oneLine(String text) { return text==null?"null":text.replace('\n',' ').replace('\r',' ').replace('\t',' '); }
    static Rev243TraceWriter writerForTest() { return writer; }
}
