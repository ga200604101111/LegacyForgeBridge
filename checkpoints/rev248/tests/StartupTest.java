package dev.yinghuang.legacyforgebridge.protocol;
import com.viaversion.viafabricplus.api.*;
import com.viaversion.viafabricplus.api.events.*;
import com.viaversion.viafabricplus.api.events.LoadingCycleCallback.LoadingCycle;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.connection.UserConnection;
import dev.yinghuang.legacyforgebridge.LegacyFileLogger;
import java.util.*;
import net.minecraft.class_310;

/** Real packaged LFB entrypoint/backend/helper; explicit VFP lifecycle and Minecraft API doubles. */
public final class StartupTest {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s+" logs="+LegacyFileLogger.lines);}
 static boolean log(String s){return LegacyFileLogger.lines.stream().anyMatch(x->x.contains(s));}
 public static final class Platform implements ViaFabricPlusBase {
  ProtocolVersion target=ProtocolVersion.NATIVE,previous;int calls,version=6;boolean throwSetter,ignoreSetter,throwRegister,noChangeCallbacks;UserConnection connection;
  final List<LoadingCycleCallback> load=new ArrayList<>();final List<ChangeProtocolVersionCallback> changes=new ArrayList<>();
  public String getVersion(){return "4.4.15-test-double";}public int apiVersion(){return version;}
  public ProtocolVersion getTargetVersion(){return target;}
  public void setTargetVersion(ProtocolVersion v,boolean revert){calls++;if(throwSetter)throw new IllegalStateException("setter fixture");if(ignoreSetter)return;
   ProtocolVersion old=target;target=v;if(old!=v){if(revert)previous=old;if(!noChangeCallbacks)for(var c:changes)c.onChangeProtocolVersion(old,v);}}
  public void registerLoadingCycleCallback(LoadingCycleCallback c){if(throwRegister)throw new IllegalStateException("register fixture");load.add(c);}
  public void registerOnChangeProtocolVersionCallback(ChangeProtocolVersionCallback c){changes.add(c);}
  public UserConnection getPlayNetworkUserConnection(){return connection;}
  void fire(LoadingCycle c){for(var f:List.copyOf(load))f.onLoadCycle(c);}
  void settings(ProtocolVersion v){setTargetVersion(v,false);}
  void disconnect(){if(previous!=null){setTargetVersion(previous,false);previous=null;}}
 }
 public static void main(String[] args)throws Exception{
  String test=args[0];Platform p=new Platform();var entry=new ViaFabricPlusEntrypoint();
  if(test.equals("unsupported-api"))p.version=7;
  if(test.equals("register-error"))p.throwRegister=true;
  entry.onPlatformLoad(p);
  if(test.startsWith("baseline-")){
   ok(p.calls==1&&p.target==ProtocolVersion.v1_7_6,"old entry selected too early");
   if(test.equals("baseline-settings")){p.settings(ProtocolVersion.OTHER);p.fire(LoadingCycle.POST_FILES_LOAD);ok(p.target==ProtocolVersion.OTHER,"old setting overrides startup");}
   else{p.disconnect();ok(p.target==ProtocolVersion.NATIVE,"old revert-on-disconnect returns native");}
   System.out.println("REPRODUCED "+test+" checks="+checks);return;
  }
  ok(p.calls==0,"no early setter");ok(System.getProperty("rev248.test.client.loaded")==null,"no early Minecraft class initialization");
  if(test.equals("unsupported-api")||test.equals("register-error")){
   ok(log("status=FAILED"),"API/registration failure disclosed");ok(p.load.isEmpty(),"no fake registration");
   System.out.println("PASS "+test+" checks="+checks);return;
  }
  ok(p.load.size()==1,"one load callback");ok(p.changes.size()==1,"original backend callback retained");
  class_310 c=class_310.INSTANCE;
  switch(test){
   case "saved-other", "save-disabled", "already-selected", "manual-after", "disconnect", "duplicate-cycle", "callback-object", "off-thread", "queued-active", "null-client", "setter-error", "readback-error", "backend-mismatch", "world-active", "player-active", "network-active", "server-active", "vfp-active", "connecting", "non-connecting-screen" -> {}
   default->throw new IllegalArgumentException(test);
  }
  for(LoadingCycle phase:LoadingCycle.values())if(phase!=LoadingCycle.POST_FILES_LOAD)p.fire(phase);
  ok(p.calls==0,"other loading cycles cannot select");
  p.settings(test.equals("already-selected")?ProtocolVersion.v1_7_6:test.equals("save-disabled")?ProtocolVersion.NATIVE:ProtocolVersion.OTHER);
  int prior=p.calls;
  if(test.equals("world-active"))c.field_1687=new Object();
  if(test.equals("player-active"))c.field_1724=new Object();
  if(test.equals("network-active"))c.network=new Object();
  if(test.equals("server-active"))c.server=new Object();
  if(test.equals("vfp-active"))p.connection=new UserConnection(){};
  if(test.equals("connecting"))c.field_1755=new net.minecraft.class_412();
  if(test.equals("non-connecting-screen"))c.field_1755=new Object();
  if(test.equals("null-client"))class_310.absent=true;
  if(test.equals("setter-error"))p.throwSetter=true;
  if(test.equals("readback-error"))p.ignoreSetter=true;
  if(test.equals("backend-mismatch"))p.noChangeCallbacks=true;
  if(test.equals("off-thread")||test.equals("queued-active")){
   Thread t=new Thread(()->p.fire(LoadingCycle.POST_FILES_LOAD));t.start();t.join();
   ok(p.calls==prior,"no network-thread setter");ok(c.queued.size()==1,"queued once on client");
   if(test.equals("queued-active"))c.field_1687=new Object();c.drain();
  }else p.fire(LoadingCycle.POST_FILES_LOAD);
  if(test.endsWith("-active")||test.equals("connecting")||test.equals("null-client")){
   ok(p.calls==prior,"active/absent client does not change protocol");ok(log("status=SKIPPED"),"skip disclosed");
  }else if(test.equals("setter-error")){
   ok(log("status=FAILED")&&p.calls==prior+1,"setter error visible, one attempt");
  }else if(test.equals("readback-error")||test.equals("backend-mismatch")){
   ok(log("status=VERIFY_FAILED"),"failed readback/backend not reported successful");
  }else{
   ok(p.target==ProtocolVersion.v1_7_6,"final target family correct");
   ok(ViaFabricPlusBackend.INSTANCE.currentProtocolId()==5,"retained backend agrees");
   ok(p.calls==prior+(test.equals("already-selected")?0:1),"bounded setter count");
   ok(p.previous==null,"startup did not request disconnect restore");
   ok(log(test.equals("already-selected")?"status=ALREADY_SELECTED":"status=SELECTED"),"verified success log");
  }
  int selectedCalls=p.calls;
  if(test.equals("manual-after")){p.setTargetVersion(ProtocolVersion.NATIVE,false);selectedCalls++;p.fire(LoadingCycle.POST_FILES_LOAD);ok(p.target==ProtocolVersion.NATIVE,"later manual choice retained");}
  if(test.equals("disconnect")){p.disconnect();ok(p.target==ProtocolVersion.v1_7_6,"disconnect keeps startup default");}
  if(test.equals("callback-object")){Object cb=p.load.getFirst();ok(cb.equals(cb)&&!cb.equals(p),"proxy equality");ok(cb.toString().contains("rev248"),"proxy toString");ok(cb.hashCode()==System.identityHashCode(cb),"proxy hashCode");}
  entry.onPlatformLoad(p);p.fire(LoadingCycle.POST_FILES_LOAD);c.drain();
  ok(p.calls==selectedCalls,"duplicate entry/cycle never re-forces");ok(p.load.size()==1&&p.changes.size()==1,"no duplicate listeners");
  ok(LegacyFileLogger.lines.stream().filter(x->x.contains("LFB_PROTOCOL_STARTUP")).count()<=3,"sparse logging");
  System.out.println("PASS "+test+" checks="+checks);
 }
}
