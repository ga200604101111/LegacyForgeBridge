package dev.yinghuang.legacyforgebridge.rev254;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import dev.yinghuang.legacyforgebridge.rev255.S12Trace;
import org.slf4j.LoggerFactory;
/** Keeps rev254's ordered queue drain; eligibility is also resolved while Fast S12 is OFF. */
public final class FastS12 {
 private static class_634 connection;
 private record Target(Object listener,int id){}
 private static volatile Target target;
 private static Object via;
 private static boolean eligible;
 private static int retry;
 private static Thread drainingThread;
 private static long earlyDrainCalls,velocityHandlerReturns,totalDrainNanos,maxDrainNanos;
 public static void beforeTick(class_310 mc){
  S12Trace.tick();class_634 h=mc.method_1562();
  if(h!=connection){report();connection=h;target=null;via=null;eligible=false;retry=0;earlyDrainCalls=velocityHandlerReturns=totalDrainNanos=maxDrainNanos=0;}
  if(h==null||mc.field_1724==null||mc.field_1687==null||mc.method_1542()){target=null;S12Trace.context(mc,null,false);return;}
  class_11980 batch=mc.method_74186();if(!batch.method_74447()||drainingThread!=null)return;
  if(!eligible&&retry--<=0){
   retry=20;
   try{
    var backend=ViaFabricPlusBackend.INSTANCE;
    var u=backend.initialized()?backend.userConnection(h.method_48296()):null;
    var info=u==null?null:u.getProtocolInfo();var v=info==null?null:info.serverProtocolVersion();
    eligible=v!=null&&v.getVersion()==5;via=eligible?u:null;
    if(eligible)LoggerFactory.getLogger("LFB-rev255").info("Fast S12/lfbtrace ready for actual remote protocol 5; normal velocity preserved; metrics available with Fast ON or OFF");
   }catch(RuntimeException|LinkageError e){eligible=false;via=null;retry=200;LoggerFactory.getLogger("LFB-rev255").warn("Protocol gate unavailable; retaining normal packet processing",e);}
  }
  if(eligible){int id=mc.field_1724.method_5628();Target t=target;if(t==null||t.listener()!=h||t.id()!=id)target=new Target(h,id);}
  S12Trace.context(mc,via,eligible);
  if(!(batch instanceof FastS12Access access))return;
  boolean pending=access.rev254$takeVelocitySignal();
  if(!eligible||!pending||!S12Trace.fastEnabled())return;
  long start=System.nanoTime();earlyDrainCalls++;drainingThread=Thread.currentThread();
  try{batch.method_74449();}
  finally{long ns=System.nanoTime()-start;totalDrainNanos+=ns;maxDrainNanos=Math.max(maxDrainNanos,ns);drainingThread=null;}
 }
 public static boolean shouldSignal(Object listener,int id){Target t=target;return t!=null&&t.listener()==listener&&t.id()==id;}
 public static boolean isDraining(){return drainingThread==Thread.currentThread();}
 public static void velocityHandled(){if(isDraining())velocityHandlerReturns++;}
 public static void report(){if(connection!=null)LoggerFactory.getLogger("LFB-rev255").info("Fast S12 summary: earlyDrainCalls="+earlyDrainCalls+", velocityHandlerReturnsDuringEarlyDrain="+velocityHandlerReturns+", drainTotalUs="+(totalDrainNanos/1000)+", maxDrainUs="+(maxDrainNanos/1000)+"; not RTT or saved latency; /lfbtrace status has measured local-player waits");}
 private FastS12(){}
}
