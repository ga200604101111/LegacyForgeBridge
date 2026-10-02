#!/usr/bin/env python3
"""Minecraft/Fabric and source-event TEST DOUBLES; never included in the main JAR."""
from pathlib import Path
import sys
SOURCES={
'net/minecraft/class_243.java': '''package net.minecraft;
public final class class_243 { public final double field_1352,field_1351,field_1350;
 public class_243(double x,double y,double z){field_1352=x;field_1351=y;field_1350=z;} }''',
'net/minecraft/class_1297.java': '''package net.minecraft;
import dev.yinghuang.legacyforgebridge.behavior.LegacyClientJumpMotion;
public class class_1297 {
 public int field_6012=100;public boolean field_5992=true;
 public int id=1;public boolean ground=true,water,lava,vehicle;
 public double x,y,z;private class_243 velocity=new class_243(0,0,0);
 public class_243 method_18798(){return velocity;}
 public void method_18800(double x,double y,double z){method_18799(new class_243(x,y,z));}
 public void method_18799(class_243 v){LegacyClientJumpMotion.nativeVelocity(this,v);velocity=v;}
 public void method_5814(double x,double y,double z){LegacyClientJumpMotion.nativePosition(this,x,y,z);this.x=x;this.y=y;this.z=z;}
 public void setRaw(double vy){velocity=new class_243(0,vy,0);}
 public double method_23317(){return x;}public double method_23318(){return y;}public double method_23321(){return z;}
 public boolean method_24828(){return ground;}public boolean method_5799(){return water;}
 public boolean method_5771(){return lava;}public boolean method_5765(){return vehicle;}public int method_5628(){return id;}
}''',
'net/minecraft/class_1309.java': '''package net.minecraft;
public class class_1309 extends class_1297 {public int field_6235;public boolean climbing;
 public boolean method_6101(){return climbing;} }''',
'net/minecraft/class_1656.java': 'package net.minecraft;public class class_1656 {public boolean field_7479;}',
'net/minecraft/class_1657.java': '''package net.minecraft;
public class class_1657 extends class_1309 {public class_1656 abilities=new class_1656();public class_1656 method_31549(){return abilities;}}''',
'net/minecraft/class_746.java': 'package net.minecraft;public class class_746 extends class_1657 {}',
'net/minecraft/class_638.java': 'package net.minecraft;public class class_638 {}',
'net/minecraft/class_634.java': 'package net.minecraft;public class class_634 {}',
'net/minecraft/class_1132.java': 'package net.minecraft;public class class_1132 {}',
'net/minecraft/class_310.java': '''package net.minecraft;
public class class_310 {private static final class_310 INSTANCE=new class_310();private final Thread main=Thread.currentThread();
 public class_746 field_1724=new class_746();public class_638 field_1687=new class_638();
 public class_634 connection=new class_634();public class_1132 server;
 public static class_310 method_1551(){return INSTANCE;}public boolean method_18854(){return Thread.currentThread()==main;}
 public class_634 method_1562(){return connection;}public class_1132 method_1576(){return server;}}
''',
'net/minecraft/class_2743.java': '''package net.minecraft;
public class class_2743 {private final int id;private final class_243 v;
 public class_2743(int id,double x,double y,double z){this.id=id;v=new class_243(x,y,z);}
 public int method_11818(){return id;}public class_243 method_73085(){return v;}}''',
'net/fabricmc/fabric/api/event/Event.java': '''package net.fabricmc.fabric.api.event;
public class Event<T> { public T listener;public void register(T listener){this.listener=listener;}}''',
'net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java': '''package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;import net.minecraft.class_310;
public final class ClientTickEvents {public interface EndTick {void onEndTick(class_310 client);}
 public static final Event<EndTick> END_CLIENT_TICK=new Event<>();}''',
'dev/yinghuang/legacyforgebridge/session/LegacySessionController.java': '''package dev.yinghuang.legacyforgebridge.session;
public final class LegacySessionController {public static boolean legacy=true;public static boolean isLegacy1710(){return legacy;}}''',
'dev/yinghuang/legacyforgebridge/LegacyForgeBridge.java': '''package dev.yinghuang.legacyforgebridge;
public final class LegacyForgeBridge {public static final LegacyFileLogger LOGGER=new LegacyFileLogger();}''',
'dev/yinghuang/legacyforgebridge/LegacyFileLogger.java': '''package dev.yinghuang.legacyforgebridge;
public final class LegacyFileLogger {public static long lines,reconciled,ready;public void info(String s,Object... args){
 lines++;if(s.contains("READY rev241"))ready++;for(Object arg:args)if(String.valueOf(arg).contains("stage=REV241_RECONCILED"))reconciled++;}}
''',
'dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorRegistry.java': '''package dev.yinghuang.legacyforgebridge.behavior;
public final class LegacyBehaviorRegistry {public static java.util.List<?> events(String name){return java.util.List.of("source-event-test-double");}}''',
'dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorRuntime.java': '''package dev.yinghuang.legacyforgebridge.behavior;
import net.minecraft.class_1309;
public final class LegacyBehaviorRuntime {public static int calls;public static double delta=0.15;
 public static void jump(class_1309 entity){calls++;var v=entity.method_18798();entity.method_18800(v.field_1352,v.field_1351+delta,v.field_1350);}}'''
}

def write(root):
 root=Path(root)
 for name,text in SOURCES.items():
  p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text+'\n')
if __name__=='__main__':write(sys.argv[1])
