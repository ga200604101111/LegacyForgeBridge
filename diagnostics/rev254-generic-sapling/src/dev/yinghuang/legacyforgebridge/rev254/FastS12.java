package dev.yinghuang.legacyforgebridge.rev254;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.slf4j.LoggerFactory;
/** Additional game-thread drain point. No raw-packet bypass, reordered S12, or duplicate apply. */
public final class FastS12 {
 private static class_634 connection;
 private record Target(Object listener,int id){}
 private static volatile Target target;
 private static boolean eligible;
 private static int retry;
 private static Thread drainingThread;
 private static long earlyDrainCalls, velocityHandlerReturns, totalDrainNanos, maxDrainNanos;
 public static void beforeTick(class_310 mc) {
  class_634 h=mc.method_1562();
  if(h!=connection){report();connection=h;target=null;eligible=false;retry=0;earlyDrainCalls=velocityHandlerReturns=totalDrainNanos=maxDrainNanos=0;}
  if(!Rev254Config.FAST_S12 || h==null || mc.field_1724==null || mc.field_1687==null || mc.method_1542())return;
  class_11980 batch=mc.method_74186();
  if(!batch.method_74447() || drainingThread!=null || !(batch instanceof FastS12Access access))return;
  if(!eligible && retry--<=0){
   retry=20;
   try {
    var backend=ViaFabricPlusBackend.INSTANCE;
    var u=backend.initialized()?backend.userConnection(h.method_48296()):null;
    var info=u==null?null:u.getProtocolInfo();
    var v=info==null?null:info.serverProtocolVersion();
    eligible=v!=null && v.getVersion()==5;
    if(eligible)LoggerFactory.getLogger("LFB-rev254").info("Fast S12 enabled for actual remote protocol 5; ordered vanilla queue, no velocity cancellation");
   }catch(RuntimeException|LinkageError e){eligible=false;retry=200;LoggerFactory.getLogger("LFB-rev254").warn("Fast S12 protocol gate unavailable; retaining normal packet processing",e);}
  }
  if(eligible){int id=mc.field_1724.method_5628();Target t=target;if(t==null||t.listener()!=h||t.id()!=id)target=new Target(h,id);}
  boolean pending=access.rev254$takeVelocitySignal();
  if(!eligible || !pending)return;
  long start=System.nanoTime();earlyDrainCalls++;drainingThread=Thread.currentThread();
  try {batch.method_74449();}
  finally {long ns=System.nanoTime()-start;totalDrainNanos+=ns;maxDrainNanos=Math.max(maxDrainNanos,ns);drainingThread=null;}
 }
 public static boolean shouldSignal(Object listener,int id){Target t=target;return t!=null&&t.listener()==listener&&t.id()==id;}
 public static void velocityHandled(){if(drainingThread==Thread.currentThread())velocityHandlerReturns++;}
 public static void report(){
  if(connection!=null)LoggerFactory.getLogger("LFB-rev254").info("Fast S12 summary: earlyDrainCalls="+earlyDrainCalls+", velocityHandlerReturnsDuringEarlyDrain="+velocityHandlerReturns+", drainTotalUs="+(totalDrainNanos/1000)+", maxDrainUs="+(maxDrainNanos/1000)+"; empty drains possible; not network RTT or proof of jump correction");
 }
 private FastS12(){}
}