package dev.yinghuang.lfb.jumpprobe;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Read-only field/getter snapshots. Never changes packets, player state, or a game return value. */
public final class JumpProbe {
    public static final String VERSION="1.0.0";
    private static volatile boolean configured, enabled=true, stopping, capped;
    private static volatile File root=new File(".");
    private static volatile String target="LongYu420";
    private static volatile long captureStartNs;
    private static volatile int seconds=600, maxLines=200000;
    private static final AtomicLong sequence=new AtomicLong(), packetSequence=new AtomicLong();
    private static final AtomicLong errors=new AtomicLong(), dropped=new AtomicLong(), lines=new AtomicLong(), transformFailures=new AtomicLong();
    private static final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<String>(8192);
    private static final java.lang.ref.ReferenceQueue<Object> packetRefQueue=new java.lang.ref.ReferenceQueue<Object>();
    private static final Map<IdentityRef,Long> packetIds=new HashMap<IdentityRef,Long>();
    private static final class IdentityRef extends java.lang.ref.WeakReference<Object> {
        final int hash;
        IdentityRef(Object o,java.lang.ref.ReferenceQueue<Object> q){super(o,q);hash=System.identityHashCode(o);}
        public int hashCode(){return hash;}
        public boolean equals(Object o){return this==o || (o instanceof IdentityRef && get()!=null && get()==((IdentityRef)o).get());}
    }
    private static final ConcurrentMap<Integer,Boolean> playerIds=new ConcurrentHashMap<Integer,Boolean>();
    private static final Map<Object,Boolean> connections=Collections.synchronizedMap(new WeakHashMap<Object,Boolean>());
    private static volatile Object localPlayer;
    private static volatile long clientTick,serverTick;
    private static volatile String clientPhase="OUTSIDE_TICK",serverPhase="OUTSIDE_TICK";
    private static volatile Thread writerThread;
    private static final Object MISSING=new Object();
    private static final ClassValue<ConcurrentMap<String,Object>> fields=new ClassValue<ConcurrentMap<String,Object>>() {
        protected ConcurrentMap<String,Object> computeValue(Class<?> c){return new ConcurrentHashMap<String,Object>();}
    };
    private static final ClassValue<ConcurrentMap<String,Object>> methods=new ClassValue<ConcurrentMap<String,Object>>() {
        protected ConcurrentMap<String,Object> computeValue(Class<?> c){return new ConcurrentHashMap<String,Object>();}
    };
    private JumpProbe(){}

    public static synchronized void configure(File location) {
        if(configured)return;
        configured=true;root=location;
        try {
            Properties p=new Properties(); File file=new File(root,"config/lfb-jump-probe.properties");
            if(file.isFile())try(InputStream in=new FileInputStream(file)){p.load(in);}
            target=System.getProperty("lfb.jumpprobe.player",p.getProperty("player","LongYu420")).trim();
            enabled=Boolean.parseBoolean(System.getProperty("lfb.jumpprobe.enabled",p.getProperty("enabled","true")));
            seconds=bounded(p.getProperty("captureSeconds","600"),10,3600,600);
            maxLines=bounded(p.getProperty("maxLines","200000"),100,500000,200000);
            if(!enabled)return;
            writerThread=new Thread(new Runnable(){public void run(){writeLoop();}},"LFB-JumpProbe-Writer");
            writerThread.setDaemon(true);writerThread.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){public void run(){shutdown();}},"LFB-JumpProbe-Shutdown"));
            Map<String,Object> h=base("HEADER");h.put("probeVersion",VERSION);h.put("runtime","Forge-1.7.10");h.put("targetPlayer",target);
            h.put("captureSeconds",seconds);h.put("maxLines",maxLines);h.put("java",System.getProperty("java.version"));
            h.put("readOnly",true);h.put("networkUpload",false);h.put("clockScope","this-JVM-only");
            h.put("networkEventsContainPlayerSnapshot",false);h.put("testStatus","offline-verified;live-game-not-tested");enqueue(h);
        }catch(Throwable e){enabled=false;System.err.println("[LFB JumpProbe] Initialization failed: "+e.getClass().getName());}
    }
    private static int bounded(String value,int min,int max,int fallback){try{return Math.max(min,Math.min(max,Integer.parseInt(value)));}catch(Exception e){return fallback;}}
    public static boolean enabled(){return enabled;}
    public static void status(String stage,String detail){try{if(stage.equals("TRANSFORM_FAILED"))transformFailures.incrementAndGet();ensure();if(enabled){Map<String,Object> e=base(stage);e.put("detail",detail);enqueue(e);}}catch(Throwable e){errors.incrementAndGet();}}
    private static void ensure(){if(!configured)configure(root);}

    public static void event(String stage,Object subject,Object packet) {
        try { observe(stage,subject,packet); } catch(Throwable e){errors.incrementAndGet();}
    }
    private static void observe(String stage,Object subject,Object packet) throws Exception {
        ensure();if(!enabled||stopping||capped)return;
        if(stage.equals("CLIENT_TICK_HEAD")){clientTick++;clientPhase="IN_TICK";localPlayer=field(subject,"field_71439_g","thePlayer");}
        if(stage.equals("SERVER_TICK_HEAD")){serverTick++;serverPhase="IN_TICK";}
        boolean tick=stage.startsWith("CLIENT_TICK_")||stage.startsWith("SERVER_TICK_");
        if(tick){
            if(captureStartNs!=0){Map<String,Object> e=base(stage);enqueue(e);}
            if(stage.equals("CLIENT_TICK_RETURN"))clientPhase="OUTSIDE_TICK";
            if(stage.equals("SERVER_TICK_RETURN"))serverPhase="OUTSIDE_TICK";
            return;
        }
        boolean networkOnly=stage.startsWith("NET_RECEIVE_")||stage.startsWith("NET_SEND_REQUEST_");
        String kind=packetKind(packet);
        if(packet!=null && kind==null)return;
        Object player=null;
        if(!networkOnly)player=resolvePlayer(stage,subject,packet);
        boolean matched=matches(player);
        if(player!=null && !matched)return;
        if(matched){
            Object id=call(player,new Class<?>[0],new Object[0],"func_145782_y","getEntityId");
            if(id instanceof Number)playerIds.put(((Number)id).intValue(),Boolean.TRUE);
            Object handler=field(player,"field_71174_a","sendQueue","field_71135_a","playerNetServerHandler");
            Object nm=handler==null?null:field(handler,"field_147302_e","netManager","field_147371_a");
            if(nm!=null)connections.put(nm,Boolean.TRUE);
            if(stage.startsWith("CLIENT_PLAYER_TICK")||stage.startsWith("CLIENT_WALK_SEND"))localPlayer=player;
            if(captureStartNs==0)captureStartNs=System.nanoTime();
        }
        if(captureStartNs==0)return;
        if(System.nanoTime()-captureStartNs>seconds*1000000000L){
            capped=true;status("CAPTURE_STOP","time limit reached; restart game/server for another capture");return;
        }
        // NET callbacks do not read live player state from Netty threads.
        if(networkOnly && !connections.containsKey(subject))return;
        if(player==null && !networkOnly && packet==null)return;
        if(player==null && "S12".equals(kind)){
            Object id=field(packet,"field_149417_a");
            if(!(id instanceof Number)||!playerIds.containsKey(((Number)id).intValue()))return;
        }
        Map<String,Object> e=base(stage);
        if(subject!=null)e.put("subjectClass",subject.getClass().getName());
        if(stage.startsWith("NET_"))e.put("connectionKey",Integer.toHexString(System.identityHashCode(subject)));
        if(packet!=null){e.put("packetKey",packetId(packet));e.put("packet",packetData(packet,kind));}
        if(matched){e.put("player",state(player));
            if(subject!=null && stage.startsWith("SERVER_C03")){
                Map<String,Object> handler=new LinkedHashMap<String,Object>();
                handler.put("lastPosY",field(subject,"field_147382_p","lastPosY"));
                handler.put("hasMoved",field(subject,"field_147380_r","hasMoved"));
                handler.put("networkTickCount",field(subject,"field_147368_e","networkTickCount"));
                e.put("serverHandler",handler);
            }
        }
        else e.put("playerSnapshot",networkOnly?"not-read-on-network-thread":"unresolved");
        if(stage.startsWith("S12_CREATE")||stage.startsWith("SERVER_C03_JUMP_CALL")||stage.equals("FORGE_JUMP_EVENT_HEAD"))e.put("stack",stack());
        if(stage.startsWith("SERVER_C03_JUMP_CALL"))e.put("causality","exact C03 object at the actual server jump call site");
        enqueue(e);
    }
    private static Object resolvePlayer(String stage,Object s,Object p) throws Exception {
        if(isPlayer(s))return s;
        if(stage.startsWith("SERVER_C03")||stage.startsWith("SERVER_SEND"))return field(s,"field_147369_b","playerEntity");
        if(stage.startsWith("TRACKER_TICK")){
            Object e=field(s,"field_73132_a","myEntity");return isPlayer(e)?e:null;
        }
        if(stage.startsWith("CLIENT_S12_APPLY")){
            Object world=field(s,"field_147300_g","clientWorldController");Object id=field(p,"field_149417_a");
            return id instanceof Number ? call(world,new Class<?>[]{int.class},new Object[]{((Number)id).intValue()},"func_73045_a","getEntityByID"):null;
        }
        if(stage.startsWith("CLIENT_S08_APPLY"))return localPlayer;
        if(stage.startsWith("NET_DRAIN")){
            Object h=field(s,"field_150744_m","netHandler");
            Object player=field(h,"field_147369_b","playerEntity");
            if(player!=null)return player;
            if(h!=null && h.getClass().getName().endsWith("NetHandlerPlayClient"))return localPlayer;
        }
        return null;
    }
    private static boolean isPlayer(Object o){for(Class<?> c=o==null?null:o.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("net.minecraft.entity.player.EntityPlayer"))return true;return false;}
    private static boolean matches(Object p) throws Exception {
        if(!isPlayer(p))return false;
        if(target.isEmpty()||target.equals("*"))return true;
        Object name=call(p,new Class<?>[0],new Object[0],"func_70005_c_","getCommandSenderName");
        return name!=null && target.equalsIgnoreCase(String.valueOf(name));
    }
    private static Map<String,Object> state(Object p) throws Exception {
        Map<String,Object> m=new LinkedHashMap<String,Object>();
        put(m,"entityId",call(p,new Class<?>[0],new Object[0],"func_145782_y","getEntityId"));
        put(m,"entityClass",p.getClass().getName());
        Object world=field(p,"field_70170_p","worldObj");put(m,"clientSide",field(world,"field_72995_K","isRemote"));
        put(m,"dimension",field(p,"field_71093_bK","dimension"));put(m,"ticksExisted",field(p,"field_70173_aa","ticksExisted"));
        put(m,"x",field(p,"field_70165_t","posX"));put(m,"y",field(p,"field_70163_u","posY"));put(m,"z",field(p,"field_70161_v","posZ"));
        put(m,"vx",field(p,"field_70159_w","motionX"));put(m,"vy",field(p,"field_70181_x","motionY"));put(m,"vz",field(p,"field_70179_y","motionZ"));
        put(m,"onGround",field(p,"field_70122_E","onGround"));put(m,"velocityChanged",field(p,"field_70133_I","velocityChanged"));
        put(m,"isAirBorne",field(p,"field_70160_al","isAirBorne"));put(m,"horizontalCollision",field(p,"field_70123_F","isCollidedHorizontally"));
        put(m,"verticalCollision",field(p,"field_70124_G","isCollidedVertically"));put(m,"yOffset",field(p,"field_70129_M","yOffset"));
        put(m,"ySize",field(p,"field_70139_V","ySize"));put(m,"jumpTicks",field(p,"field_70773_bE","jumpTicks"));
        Object b=field(p,"field_70121_D","boundingBox");put(m,"feetY",field(b,"field_72338_b","minY"));
        put(m,"bboxTopY",field(b,"field_72337_e","maxY"));
        Object item=call(p,new Class<?>[]{int.class},new Object[]{3},"func_71124_b","getEquipmentInSlot");
        Object type=call(item,new Class<?>[0],new Object[0],"func_77973_b","getItem");
        put(m,"chestItemClass",type==null?null:type.getClass().getName());
        boolean complete=true;for(String k:Arrays.asList("entityId","clientSide","y","vy","onGround","velocityChanged","feetY"))if(m.get(k)==null)complete=false;
        m.put("snapshotComplete",complete);if(!complete)errors.incrementAndGet();
        return m;
    }
    private static String packetKind(Object p){
        for(Class<?> c=p==null?null:p.getClass();c!=null;c=c.getSuperclass()){
            String n=c.getName();if(n.equals("net.minecraft.network.play.client.C03PacketPlayer"))return "C03";
            if(n.equals("net.minecraft.network.play.server.S12PacketEntityVelocity"))return "S12";
            if(n.equals("net.minecraft.network.play.server.S08PacketPlayerPosLook"))return "S08";
        }return null;
    }
    private static Map<String,Object> packetData(Object p,String kind) throws Exception {
        Map<String,Object> m=new LinkedHashMap<String,Object>();m.put("kind",kind);m.put("class",p.getClass().getSimpleName());
        if("C03".equals(kind)){
            Object move=field(p,"field_149480_h");Object look=field(p,"field_149481_i");
            m.put("hasPosition",move);m.put("hasRotation",look);m.put("onGround",field(p,"field_149474_g"));
            // C03/C05 absent position fields must not be interpreted as zero coordinates.
            if(Boolean.TRUE.equals(move)){m.put("x",field(p,"field_149479_a"));m.put("y",field(p,"field_149477_b"));m.put("stance",field(p,"field_149475_d"));m.put("z",field(p,"field_149478_c"));}
            if(Boolean.TRUE.equals(look)){m.put("yaw",field(p,"field_149476_e"));m.put("pitch",field(p,"field_149473_f"));}
        } else if("S12".equals(kind)){
            m.put("entityId",field(p,"field_149417_a"));Object x=field(p,"field_149415_b"),y=field(p,"field_149416_c"),z=field(p,"field_149414_d");
            m.put("rawX",x);m.put("rawY",y);m.put("rawZ",z);m.put("vx",velocity(x));m.put("vy",velocity(y));m.put("vz",velocity(z));
        } else if("S08".equals(kind)){
            m.put("x",field(p,"field_148940_a"));m.put("y",field(p,"field_148938_b"));m.put("z",field(p,"field_148939_c"));
            m.put("yaw",field(p,"field_148936_d"));m.put("pitch",field(p,"field_148937_e"));m.put("onGround",field(p,"field_148935_f"));
        }
        return m;
    }
    private static Double velocity(Object value){return value instanceof Number?((Number)value).doubleValue()/8000D:null;}
    private static void put(Map<String,Object> m,String k,Object v){m.put(k,v);}
    private static long packetId(Object p){synchronized(packetIds){
        java.lang.ref.Reference<?> dead;while((dead=packetRefQueue.poll())!=null)packetIds.remove(dead);
        IdentityRef key=new IdentityRef(p,packetRefQueue);Long id=packetIds.get(key);
        if(id==null){id=packetSequence.incrementAndGet();packetIds.put(key,id);}return id;
    }}
    private static Object field(Object o,String... aliases) throws IllegalAccessException {
        if(o==null)return null;
        String key=Arrays.toString(aliases);ConcurrentMap<String,Object> map=fields.get(o.getClass());Object a=map.get(key);
        if(a==null){a=MISSING;search:for(String name:aliases)for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);a=f;break search;}catch(NoSuchFieldException ignored){}map.putIfAbsent(key,a);}
        return a==MISSING?null:((Field)a).get(o);
    }
    private static Object call(Object o,Class<?>[] types,Object[] args,String... aliases) throws Exception {
        if(o==null)return null;
        String key=Arrays.toString(aliases)+Arrays.toString(types);ConcurrentMap<String,Object> map=methods.get(o.getClass());Object a=map.get(key);
        if(a==null){a=MISSING;search:for(String name:aliases)for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Method f=c.getDeclaredMethod(name,types);f.setAccessible(true);a=f;break search;}catch(NoSuchMethodException ignored){}map.putIfAbsent(key,a);}
        return a==MISSING?null:((Method)a).invoke(o,args);
    }
    private static List<String> stack(){List<String> result=new ArrayList<String>();for(StackTraceElement s:Thread.currentThread().getStackTrace()){if(s.getClassName().startsWith("dev.yinghuang.lfb.jumpprobe.")||s.getClassName().equals("java.lang.Thread"))continue;result.add(s.toString());if(result.size()==14)break;}return result;}
    private static Map<String,Object> base(String stage){Map<String,Object> e=new LinkedHashMap<String,Object>();e.put("seq",sequence.incrementAndGet());e.put("ns",System.nanoTime());e.put("utcMs",System.currentTimeMillis());e.put("stage",stage);e.put("thread",Thread.currentThread().getName());e.put("threadId",Thread.currentThread().getId());e.put("clientTick",clientTick);e.put("serverTick",serverTick);e.put("clientPhase",clientPhase);e.put("serverPhase",serverPhase);return e;}
    private static void enqueue(Map<String,Object> value){
        if(stopping)return;
        if(lines.incrementAndGet()>maxLines){capped=true;dropped.incrementAndGet();return;}
        if(!queue.offer(json(value)))dropped.incrementAndGet();
    }
    private static void writeLoop(){
        try {
            File dir=new File(root,"logs/lfb-jump-probe");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create log directory");
            SimpleDateFormat date=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");date.setTimeZone(TimeZone.getTimeZone("UTC"));
            File log=new File(dir,"forge1710-"+date.format(new Date())+"-"+UUID.randomUUID().toString().substring(0,8)+".jsonl");
            try(BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(log),StandardCharsets.UTF_8))){
                long written=0,bytes=0;while(!stopping||!queue.isEmpty()){
                    String s=queue.poll(200,TimeUnit.MILLISECONDS);
                    if(s!=null){if(bytes>64L*1024*1024){capped=true;dropped.incrementAndGet();continue;}out.write(s);out.newLine();written++;bytes+=s.getBytes(StandardCharsets.UTF_8).length+1;}
                    if(s==null||written%100==0)out.flush();
                }
                Map<String,Object> end=base("FOOTER");end.put("writtenRecords",written);end.put("droppedRecords",dropped.get());end.put("observerErrors",errors.get());end.put("limitReached",capped);end.put("transformFailures",transformFailures.get());end.put("matchedPlayerSeen",captureStartNs!=0);end.put("completeForAnalysis",dropped.get()==0&&errors.get()==0&&transformFailures.get()==0&&!capped&&captureStartNs!=0);out.write(json(end));out.newLine();out.flush();
            }
        }catch(Throwable e){enabled=false;System.err.println("[LFB JumpProbe] Log writer failed: "+e.getClass().getName());}
    }
    public static void shutdown(){stopping=true;Thread t=writerThread;if(t!=null&&t!=Thread.currentThread())try{t.join(2500);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static String json(Object o){
        if(o==null)return "null";
        if(o instanceof Boolean)return o.toString();
        if(o instanceof Number){double d=((Number)o).doubleValue();return Double.isNaN(d)||Double.isInfinite(d)?quote(o.toString()):o.toString();}
        if(o instanceof Map){StringBuilder s=new StringBuilder("{");boolean first=true;for(Object obj:((Map<?,?>)o).entrySet()){Map.Entry<?,?> e=(Map.Entry<?,?>)obj;if(!first)s.append(',');first=false;s.append(quote(String.valueOf(e.getKey()))).append(':').append(json(e.getValue()));}return s.append('}').toString();}
        if(o instanceof Iterable){StringBuilder s=new StringBuilder("[");boolean first=true;for(Object v:(Iterable<?>)o){if(!first)s.append(',');first=false;s.append(json(v));}return s.append(']').toString();}
        return quote(String.valueOf(o));
    }
    private static String quote(String s){StringBuilder out=new StringBuilder("\"");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'||c=='\\')out.append('\\').append(c);else if(c=='\n')out.append("\\n");else if(c=='\r')out.append("\\r");else if(c=='\t')out.append("\\t");else if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}return out.append('"').toString();}
}
