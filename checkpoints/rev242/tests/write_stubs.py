from pathlib import Path

def write(root):
    # Minecraft and logger doubles; NEVER included in the delivered main JAR.
    src={
'net/minecraft/class_243.java': '''package net.minecraft; public class class_243 {
public final double field_1352,field_1351,field_1350;
public class_243(double x,double y,double z){field_1352=x;field_1351=y;field_1350=z;}}''',
'net/minecraft/class_1297.java': '''package net.minecraft; public class class_1297 {
public int field_6012,id=42; public boolean field_5992,ground=true,water,lava,vehicle;
public double x,y=4,z; public class_243 v=new class_243(0,0,0);
public class_243 method_18798(){return v;} public void method_18800(double x,double y,double z){var next=new class_243(x,y,z);dev.yinghuang.legacyforgebridge.behavior.Rev241JumpMotionBridge.nativeVelocity(this,next);v=next;}
public double method_23318(){return y;} public boolean method_24828(){return ground;}
public boolean method_5799(){return water;}public boolean method_5771(){return lava;}
public boolean method_5765(){return vehicle;} public int method_5628(){return id;}}''',
'net/minecraft/class_1309.java': '''package net.minecraft; public class class_1309 extends class_1297 {
public int field_6235;public boolean climbing,wing=true;public boolean method_6101(){return climbing;}}''',
'net/minecraft/class_1656.java': 'package net.minecraft; public class class_1656 {public boolean field_7479;}',
'net/minecraft/class_1657.java': '''package net.minecraft; public class class_1657 extends class_1309 {
public final class_1656 abilities=new class_1656();public class_1656 method_31549(){return abilities;}}''',
'net/minecraft/class_310.java': '''package net.minecraft; public class class_310 {
public static final class_310 INSTANCE=new class_310();public class_1657 field_1724;
public Object field_1687=new Object(),connection=new Object(),server;
public boolean mainThread=true;public static class_310 method_1551(){return INSTANCE;}
public boolean method_18854(){return mainThread;}public Object method_1562(){return connection;}
public Object method_1576(){return server;}}''',
'net/minecraft/class_2743.java': '''package net.minecraft; public class class_2743 {
final int id;final class_243 v;public class_2743(int i,double x,double y,double z){id=i;var next=new class_243(x,y,z);dev.yinghuang.legacyforgebridge.behavior.Rev241JumpMotionBridge.nativeVelocity(this,next);v=next;}
public int method_11818(){return id;}public class_243 method_73085(){return v;}}''',
'net/minecraft/class_1282.java': 'package net.minecraft;public class class_1282 {}',
'dev/yinghuang/legacyforgebridge/session/LegacySessionController.java': '''package dev.yinghuang.legacyforgebridge.session;
public class LegacySessionController {public static boolean legacy=true;public static boolean isLegacy1710(){return legacy;}}''',
'dev/yinghuang/legacyforgebridge/behavior/LegacyMotionTraceLog.java': '''package dev.yinghuang.legacyforgebridge.behavior;
public class LegacyMotionTraceLog {public static final java.util.List<String> events=new java.util.ArrayList<>();
public static void event(String s,String t){events.add(s+" "+t);}}''',
'com/google/common/collect/Multimap.java': 'package com.google.common.collect;public interface Multimap<K,V> {}',
'com/google/common/collect/ArrayListMultimap.java': '''package com.google.common.collect;public class ArrayListMultimap<K,V> implements Multimap<K,V> {
public static <K,V> ArrayListMultimap<K,V> create(){return new ArrayListMultimap<>();}}''',
'fixture/WingItem.java': '''package fixture;
public class WingItem extends dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorApi.Item {}''',
'dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorRuntime.java': '''package dev.yinghuang.legacyforgebridge.behavior;
import java.util.*;import net.minecraft.*;
/** Test-only stand-in for the game's snapshot capture/commit surrounding the REAL source event bodies. */
public class LegacyBehaviorRuntime {
public static final List<LegacyBehaviorApi.Particle> particles=new ArrayList<>();
public static int falls;public static boolean throwNext;public static double lastSourceY;
public static LegacyBehaviorApi.Player snapshot(class_1309 n,boolean scoped) {
 var p=new LegacyBehaviorApi.Player();p.handle=n;p.field_70165_t=n.x;p.field_70161_v=n.z;
 p.field_70163_u=scoped?Rev242LandingBridge.sourceY(n,n.y):n.y;
 p.field_70181_x=n.v.field_1351;p.field_70130_N=.6F;p.field_70131_O=1.8F;
 p.field_70170_p=new LegacyBehaviorApi.World();p.field_70170_p.field_72995_K=true;
 if(n.wing)p.equipment[3]=new LegacyBehaviorApi.Stack(new fixture.WingItem());return p;
}
public static boolean lfb$fallBeforeRev242(class_1309 n,double distance,float multiplier,class_1282 damage) {
 falls++;if(throwNext){throwNext=false;throw new IllegalStateException("injected source failure");}
 var p=snapshot(n,true);lastSourceY=p.field_70163_u;
 var event=new LegacyBehaviorApi.Event();event.entityLiving=p;event.distance=(float)distance;event.damageMultiplier=multiplier;
 new fixture.RpgEvent().onFallDown(event);particles.addAll(p.field_70170_p.particles);return event.isCanceled();
}
public static double sourceJump(class_1309 n){var p=snapshot(n,false);var e=new LegacyBehaviorApi.Event();e.entityLiving=p;
 new fixture.RpgEvent().onJump(e);return p.field_70181_x;}
}'''
}
    files=[]
    for name,text in src.items():
        p=Path(root)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text+'\n');files.append(str(p))
    return files
