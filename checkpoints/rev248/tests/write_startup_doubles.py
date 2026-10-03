"""Explicit API-signature doubles, not ViaFabricPlus or Minecraft; never packaged."""
from pathlib import Path
SOURCES={
'com/viaversion/viaversion/api/protocol/version/ProtocolVersion.java':'''package com.viaversion.viaversion.api.protocol.version;
public final class ProtocolVersion {
 public static final ProtocolVersion v1_7_6=new ProtocolVersion(5,"1.7.6-1.7.10"),NATIVE=new ProtocolVersion(774,"1.21.11"),OTHER=new ProtocolVersion(47,"1.8");
 private final int id;private final String name;
 public ProtocolVersion(int id,String name){this.id=id;this.name=name;}
 public int getVersion(){return id;}public String getName(){return name;}
}''',
'com/viaversion/viaversion/api/connection/UserConnection.java':'''package com.viaversion.viaversion.api.connection;public interface UserConnection {}''',
'com/viaversion/viafabricplus/api/events/ChangeProtocolVersionCallback.java':'''package com.viaversion.viafabricplus.api.events;import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;public interface ChangeProtocolVersionCallback{void onChangeProtocolVersion(ProtocolVersion a,ProtocolVersion b);}''',
'com/viaversion/viafabricplus/api/events/LoadingCycleCallback.java':'''package com.viaversion.viafabricplus.api.events;public interface LoadingCycleCallback {
 enum LoadingCycle{PRE_SETTINGS_LOAD,POST_SETTINGS_LOAD,PRE_FILES_LOAD,POST_FILES_LOAD,PRE_VIAVERSION_LOAD,POST_VIAVERSION_LOAD,FINAL_LOAD,POST_GAME_LOAD}
 void onLoadCycle(LoadingCycle cycle);
}''',
'com/viaversion/viafabricplus/api/entrypoint/ViaFabricPlusLoadEntrypoint.java':'''package com.viaversion.viafabricplus.api.entrypoint;import com.viaversion.viafabricplus.api.ViaFabricPlusBase;public interface ViaFabricPlusLoadEntrypoint{void onPlatformLoad(ViaFabricPlusBase platform);}''',
'com/viaversion/viafabricplus/api/ViaFabricPlusBase.java':'''package com.viaversion.viafabricplus.api;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;import com.viaversion.viaversion.api.connection.UserConnection;import com.viaversion.viafabricplus.api.events.*;
public interface ViaFabricPlusBase {
 String getVersion();int apiVersion();ProtocolVersion getTargetVersion();void setTargetVersion(ProtocolVersion version,boolean revert);
 void registerLoadingCycleCallback(LoadingCycleCallback c);void registerOnChangeProtocolVersionCallback(ChangeProtocolVersionCallback c);
 UserConnection getPlayNetworkUserConnection();
 default UserConnection getUserConnection(net.minecraft.class_2535 c){return null;}
 default boolean itemExists(net.minecraft.class_1792 i,ProtocolVersion p){return true;}
 default boolean itemExistsInConnection(net.minecraft.class_1799 s){return true;}
}''',
'net/minecraft/class_310.java':'''package net.minecraft;import java.util.concurrent.*;
public final class class_310 implements Executor {
 static {System.setProperty("rev248.test.client.loaded","yes");}
 public static final class_310 INSTANCE=new class_310();public static boolean absent;
 public Object field_1687,field_1724,field_1755,network,server;
 public Thread thread=Thread.currentThread();public ConcurrentLinkedQueue<Runnable> queued=new ConcurrentLinkedQueue<>();
 public static class_310 method_1551(){return absent?null:INSTANCE;}public boolean method_18854(){return Thread.currentThread()==thread;}
 public Object method_1562(){return network;}public Object method_1576(){return server;}
 public void execute(Runnable r){queued.add(r);}public void drain(){Runnable r;while((r=queued.poll())!=null)r.run();}
}''',
'net/minecraft/class_412.java':'package net.minecraft;public class class_412 {}',
'net/minecraft/class_2535.java':'package net.minecraft;public class class_2535 {}',
'net/minecraft/class_1792.java':'package net.minecraft;public class class_1792 {}',
'net/minecraft/class_1799.java':'package net.minecraft;public class class_1799 {}',
'dev/yinghuang/legacyforgebridge/LegacyFileLogger.java':'''package dev.yinghuang.legacyforgebridge;public final class LegacyFileLogger {
 public static final java.util.List<String> lines=new java.util.concurrent.CopyOnWriteArrayList<>();
 public void info(String s,Object...v){for(Object o:v){int i=s.indexOf("{}");if(i>=0)s=s.substring(0,i)+o+s.substring(i+2);}lines.add(s);}
}''',
'dev/yinghuang/legacyforgebridge/LegacyForgeBridge.java':'''package dev.yinghuang.legacyforgebridge;public final class LegacyForgeBridge{public static final LegacyFileLogger LOGGER=new LegacyFileLogger();}''',
'dev/yinghuang/legacyforgebridge/session/LegacySessionController.java':'''package dev.yinghuang.legacyforgebridge.session;public final class LegacySessionController{public static int id;public static void onTargetProtocolChanged(int i,String n){id=i;}}''',
}
def write(out):
 out=Path(out);paths=[]
 for name,source in SOURCES.items():
  p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source+'\n',encoding='utf-8');paths.append(p)
 return paths
