package dev.yinghuang.legacyforgebridge.rev255;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;
/** Export-only I/O on one bounded worker, never on the packet/physics hot path. */
public final class TraceExport {
 private static final AtomicBoolean BUSY=new AtomicBoolean();
 private static final ExecutorService WRITER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"LFB-S12-export");t.setDaemon(true);return t;});
 private static volatile String status="尚未匯出";
 public static String status(){return status;}
 public static boolean save(TraceStore.Snapshot snapshot,List<String> summary){
  if(!BUSY.compareAndSet(false,true))return false;
  status="匯出中";
  Path root=FabricLoader.getInstance().getGameDir().resolve("logs/lfb-s12");
  WRITER.execute(()->{
   try{
    String name="s12-"+DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC).format(Instant.now())+"-"+UUID.randomUUID().toString().substring(0,8);
    Path dir=root.resolve(name);Files.createDirectories(dir);
    writeSnapshot(dir,snapshot,summary);
    status=dir.toString();LoggerFactory.getLogger("LFB-rev255").info("S12 trace exported: "+dir);
   }catch(Exception e){status="匯出失敗："+e.getClass().getSimpleName();LoggerFactory.getLogger("LFB-rev255").warn(status,e);}
   finally{BUSY.set(false);}
  });return true;
 }
 public static void writeSnapshot(Path dir,TraceStore.Snapshot s,List<String> summary)throws java.io.IOException{
  StringBuilder csv=new StringBuilder("seq,local_monotonic_ns,fast_enqueue,fast_apply,mixed_mode,early,success,enqueue_to_apply_ms,handler_ms,tick_delta,fps,previous_frame_ms,feet_y,vy_before,vy_after,vy_requested,on_ground_before\n");
  for(var p:s.packets())csv.append(String.format(Locale.ROOT,"%d,%d,%b,%b,%b,%b,%b,%s,%s,%d,%d,%s,%.8f,%.8f,%.8f,%.8f,%b%n",p.sequence(),p.atNs(),p.fastAtEnqueue(),p.fastAtApply(),p.mixedMode(),p.early(),p.success(),TraceStore.ms(p.queueNs()),TraceStore.ms(p.handlerNs()),p.tickDelta(),p.fps(),TraceStore.ms(p.frameNs()),p.y(),p.beforeYVelocity(),p.afterYVelocity(),p.requestedYVelocity(),p.groundBefore()));
  Files.writeString(dir.resolve("s12-packets.csv"),csv,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
  StringBuilder via=new StringBuilder("local_monotonic_ns,fast_mode,normal_return,transform_ms\n");
  for(var v:s.via())via.append(v.atNs()).append(',').append(v.fast()).append(',').append(v.success()).append(',').append(TraceStore.ms(v.durationNs())).append('\n');
  Files.writeString(dir.resolve("via-s12.csv"),via,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
  Files.writeString(dir.resolve("summary.txt"),String.join("\n",summary)+"\n\nVia rows and packet rows are independent samples. Do not join by row number. Times are local, not network RTT. FPS is Minecraft's reported rolling counter, not an instantaneous per-frame FPS. Failed handlers and mode-transition packets are excluded from A/B wait distributions.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
 }
 private TraceExport(){}
}
