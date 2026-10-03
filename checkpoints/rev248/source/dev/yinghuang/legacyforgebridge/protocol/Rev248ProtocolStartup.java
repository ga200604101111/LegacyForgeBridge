package dev.yinghuang.legacyforgebridge.protocol;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.behavior.LegacyMotionTraceLog;
import java.lang.reflect.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** One startup default, AFTER VFP API-6 SettingsSave.postInit. Never a per-tick force. */
public final class Rev248ProtocolStartup {
    private static final AtomicBoolean REQUESTED=new AtomicBoolean();
    private static volatile Api api;
    private static volatile String status="NOT_REGISTERED", runtime="unknown", before="unknown", after="unknown";
    private Rev248ProtocolStartup() {}

    /** Called by the retained LFB viafabricplus entrypoint after backend.initialize(platform). */
    public static synchronized void install(Object platform) {
        if(api!=null)return;
        try {
            Api a=new Api(platform);
            runtime=String.valueOf(a.version.invoke(platform));
            int version=((Number)a.apiVersion.invoke(platform)).intValue();
            if(version!=6)throw new IllegalStateException("Unsupported VFP API "+version+"; expected API 6");
            api=a;
            Object callback=Proxy.newProxyInstance(a.callback.getClassLoader(),new Class<?>[]{a.callback},(p,m,args)->{
                if(m.getDeclaringClass()==Object.class)return switch(m.getName()) {
                    case "toString"->"LFB-rev248-startup-default";
                    case "hashCode"->System.identityHashCode(p);
                    case "equals"->p==args[0];
                    default->null;
                };
                if(m.getName().equals("onLoadCycle")&&args!=null&&args.length==1
                        &&args[0] instanceof Enum<?> cycle&&cycle.name().equals("POST_FILES_LOAD"))request(a);
                return null;
            });
            report("ARMED","cycle=POST_FILES_LOAD mode=once_per_process revertOnDisconnect=false");
            a.register.invoke(platform,callback);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e){failure("register",e);}
    }

    private static void request(Api a) {
        if(!REQUESTED.compareAndSet(false,true))return;
        try {
            ClientRead read=new ClientRead();
            Object client=read.client.invoke(null);
            if(client==null){report("SKIPPED","reason=client_unavailable");return;}
            if(Boolean.TRUE.equals(read.onThread.invoke(client)))apply(a,read,client);
            else {
                if(!(client instanceof Executor executor))throw new IllegalStateException("Client is not an Executor");
                report("QUEUED","reason=dispatch_to_client_thread");
                executor.execute(()->apply(a,read,client));
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e){failure("schedule",e);}
    }

    private static void apply(Api a,ClientRead read,Object client) {
        try {
            if(!Boolean.TRUE.equals(read.onThread.invoke(client)))throw new IllegalStateException("Not on client thread");
            Object screen=read.screen.get(client);
            if(read.world.get(client)!=null||read.player.get(client)!=null||read.connection.invoke(client)!=null
                    ||read.server.invoke(client)!=null||a.playConnection.invoke(a.platform)!=null
                    ||(screen!=null&&read.connectScreen.isInstance(screen))) {
                report("SKIPPED","reason=world_or_connection_already_active");return;
            }
            Object previous=a.getTarget.invoke(a.platform);
            before=a.describe(previous);
            boolean same=a.id(previous)==5;
            // A startup default must NOT capture the native version as a disconnect fallback.
            if(!same)a.setTarget.invoke(a.platform,a.target,false);
            Object selected=a.getTarget.invoke(a.platform);
            after=a.describe(selected);
            if(a.id(selected)!=5) {report("VERIFY_FAILED","reason=target_readback_not_5");return;}
            if(ViaFabricPlusBackend.INSTANCE.currentProtocolId()!=5) {
                report("VERIFY_FAILED","reason=backend_target_mismatch backendId="+ViaFabricPlusBackend.INSTANCE.currentProtocolId());return;
            }
            report(same?"ALREADY_SELECTED":"SELECTED","cycle=POST_FILES_LOAD targetId=5 revertOnDisconnect=false");
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e){failure("apply",e);}
    }

    /** One small context event per new capture; selected protocol is NOT a connection causal id. */
    public static void captureContext() {
        try {
            String now="unavailable";
            Api a=api;
            if(a!=null)now=a.describe(a.getTarget.invoke(a.platform));
            LegacyMotionTraceLog.event("PROTOCOL_STARTUP_CONTEXT","startupStatus="+status+" vfpRuntime="+safe(runtime)
                    +" startupBefore="+safe(before)+" startupAfter="+safe(after)+" configuredNow="+safe(now)
                    +" backendTargetId="+ViaFabricPlusBackend.INSTANCE.currentProtocolId()
                    +" scope=global_selected_protocol_not_per_connection serverCause=unavailable");
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e){
            fatal(e);
            LegacyMotionTraceLog.event("PROTOCOL_STARTUP_CONTEXT","startupStatus="+status+" contextRead=unavailable");
        }
    }
    private static void report(String value,String detail) {
        status=value;
        try {LegacyForgeBridge.LOGGER.info("LFB_PROTOCOL_STARTUP status={} vfpRuntime={} before={} after={} {}",
                value,safe(runtime),safe(before),safe(after),detail);}
        catch(RuntimeException|LinkageError e){fatal(e);}
    }
    private static void failure(String part,Throwable t) {
        fatal(t);
        while(t instanceof InvocationTargetException&&t.getCause()!=null)t=t.getCause();
        report("FAILED","component="+part+" exception="+t.getClass().getSimpleName()+" message="+safe(t.getMessage()));
    }
    private static void fatal(Throwable t) {
        while(t instanceof InvocationTargetException&&t.getCause()!=null)t=t.getCause();
        if(t instanceof VirtualMachineError e)throw e;
        if(t instanceof ThreadDeath e)throw e;
    }
    private static String safe(String s){return s==null?"null":s.replace('\n','_').replace('\r','_').replace('\t','_').replace(' ','_');}
    private static final class Api {
        final Object platform,target;
        final Class<?> callback;
        final Method register,getTarget,setTarget,version,apiVersion,protocolId,protocolName,playConnection;
        Api(Object platform)throws ReflectiveOperationException {
            this.platform=platform;
            Class<?> base=Class.forName("com.viaversion.viafabricplus.api.ViaFabricPlusBase");
            if(!base.isInstance(platform))throw new IllegalArgumentException("Not a ViaFabricPlusBase");
            Class<?> protocol=Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            callback=Class.forName("com.viaversion.viafabricplus.api.events.LoadingCycleCallback");
            register=base.getMethod("registerLoadingCycleCallback",callback);
            getTarget=base.getMethod("getTargetVersion");setTarget=base.getMethod("setTargetVersion",protocol,boolean.class);
            version=base.getMethod("getVersion");apiVersion=base.getMethod("apiVersion");
            playConnection=base.getMethod("getPlayNetworkUserConnection");
            protocolId=protocol.getMethod("getVersion");protocolName=protocol.getMethod("getName");
            target=protocol.getField("v1_7_6").get(null);
            if(id(target)!=5)throw new IllegalStateException("Unexpected v1_7_6 protocol id");
        }
        int id(Object v)throws ReflectiveOperationException{return v==null?-1:((Number)protocolId.invoke(v)).intValue();}
        String describe(Object v)throws ReflectiveOperationException{return v==null?"null":protocolName.invoke(v)+"(id="+id(v)+")";}
    }
    /** Resolve Minecraft classes only in POST_FILES_LOAD, never from the early addon entrypoint. */
    private static final class ClientRead {
        final Method client,onThread,connection,server;
        final Field world,player,screen;
        final Class<?> connectScreen;
        ClientRead()throws ReflectiveOperationException {
            Class<?> mc=Class.forName("net.minecraft.class_310");
            client=mc.getMethod("method_1551");onThread=mc.getMethod("method_18854");
            connection=mc.getMethod("method_1562");server=mc.getMethod("method_1576");
            world=mc.getField("field_1687");player=mc.getField("field_1724");screen=mc.getField("field_1755");
            connectScreen=Class.forName("net.minecraft.class_412");
        }
    }

}
