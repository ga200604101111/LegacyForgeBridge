#!/usr/bin/env python3
"""Offline lifecycle regression harness. Minecraft API classes here are TEST DOUBLES.
The Rev233 helper and baseline Rev239 helper run from the user's real main JAR.
No test-double classes are copied into the delivered main JAR.
"""
import json
import subprocess
import sys
from pathlib import Path

SOURCES = {
"net/minecraft/class_3610.java": '''package net.minecraft;
public final class class_3610 {
 public static final class_3610 EMPTY = new class_3610(true,0,false);
 public final boolean empty, falling; public final int amount;
 public class_3610(boolean e,int a,boolean f){empty=e;amount=a;falling=f;}
}''',
"net/minecraft/class_3609.java": '''package net.minecraft;
public class class_3609 {
 private final class_3610 source=new class_3610(false,8,false);
 private final class_3610 falling=new class_3610(false,8,true);
 private final class_3610[] flows=new class_3610[9];
 public class_3609(){for(int i=1;i<=8;i++)flows[i]=new class_3610(false,i,false);}
 public class_3610 method_15729(boolean f){return f?falling:source;}
 public class_3610 method_15728(int n,boolean f){return f?falling:flows[n];}
}''',
"net/minecraft/class_3612.java": '''package net.minecraft;
public final class class_3612 {public static final class_3609 field_15910=new class_3609();}''',
"net/minecraft/class_2464.java": '''package net.minecraft;
public enum class_2464 {field_11455,field_11458}''',
"net/minecraft/class_4970.java": '''package net.minecraft;
public class class_4970 {
 public static class class_4971 {
  public final class_2248 block; public final int meta;
  private class_3610 field_40339=class_3610.EMPTY;
  public boolean freezeCache=false,throwOnInit=false;
  public int cacheInitializations=0;
  public class_4971(class_2248 b,int m){block=b;meta=m;}
  public void method_26200(){cacheInitializations++;if(throwOnInit)throw new IllegalStateException("INJECTED init failure");
   if(!freezeCache)field_40339=block.dispatchFluid((class_2680)this);}
  public class_3610 method_26227(){return field_40339;}
  public void clearCacheForTest(){field_40339=class_3610.EMPTY;}
 }
}''',
"net/minecraft/class_2680.java": '''package net.minecraft;
public final class class_2680 extends class_4970.class_4971 {
 public class_2680(class_2248 b,int m){super(b,m);}
}''',
"net/minecraft/class_2689.java": '''package net.minecraft;
public final class class_2689 {
 private final java.util.List<class_2680> states;
 public class_2689(java.util.List<class_2680> s){states=s;}
 public java.util.List<class_2680> method_11662(){return states;}
}''',
"net/minecraft/class_2248.java": '''package net.minecraft;
public class class_2248 {
 private final class_2689 manager;
 public class_2248(){var list=new java.util.ArrayList<class_2680>();
  for(int m=0;m<16;m++)list.add(new class_2680(this,m));manager=new class_2689(list);
  for(var state:list)state.method_26200();}
 public class_2689 method_9595(){return manager;}
 protected class_3610 method_9545(class_2680 state){return class_3610.EMPTY;}
 protected class_2464 method_9604(class_2680 state){return class_2464.field_11458;}
 public class_3610 dispatchFluid(class_2680 state){return method_9545(state);}
 public class_2464 dispatchRender(class_2680 state){return method_9604(state);}
}''',
"dev/yinghuang/legacyforgebridge/convert/runtime/ConvertedLegacyBlock.java": '''package dev.yinghuang.legacyforgebridge.convert.runtime;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.behavior.Rev239VanillaLiquidBridge;
public class ConvertedLegacyBlock extends class_2248 {
 private final Object convertedId;
 public ConvertedLegacyBlock(String id){super();convertedId=id;}
 public static int legacyMeta(class_2680 state){return state.meta;}
 protected class_3610 method_9545(class_2680 state){return (class_3610)Rev239VanillaLiquidBridge.fluidStateOrSuper(this,state);}
 protected class_2464 method_9604(class_2680 state){return (class_2464)Rev239VanillaLiquidBridge.renderTypeOrSuper(this,state);}
}''',
"LifecycleTest.java": '''import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.behavior.Rev233LiquidCompat;
import dev.yinghuang.legacyforgebridge.behavior.Rev239VanillaLiquidBridge;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
public final class LifecycleTest {
 static int assertions=0;
 public static final class Value {private final String value; public Value(String v){value=v;} public String getAsString(){return value;}}
 public static final class Rule {private final String kind;public Rule(String k){kind=k;} public Value get(String k){return "kind".equals(k)&&kind!=null?new Value(kind):null;}}
 static void check(boolean ok,String msg){assertions++;if(!ok)throw new AssertionError(msg);}
 static void register(ConvertedLegacyBlock b,String id,Object rule)throws Exception{
  Rev239VanillaLiquidBridge.class.getMethod("registerLiquid",Object.class,Object.class,Object.class).invoke(null,b,id,rule);
 }
 static void visibleEmpty(ConvertedLegacyBlock b){for(var s:b.method_9595().method_11662()){
  check(s.method_26227().empty,"expected empty cached fluid meta="+s.meta);
  check(b.dispatchRender(s)==class_2464.field_11458,"model must remain visible meta="+s.meta);
 }}
 public static void main(String[] args)throws Exception {
  boolean baseline=args[0].equals("baseline");
  var b=new ConvertedLegacyBlock("fixture:source_water");visibleEmpty(b);
  if(baseline){
   Rev233LiquidCompat.registerLiquid(b,"fixture:source_water",new Rule("WATER"));
   for(var s:b.method_9595().method_11662()){
    check(!b.dispatchFluid(s).empty,"base block override returns real water");
    check(s.method_26227().empty,"base renderer still reads stale EMPTY");
    check(b.dispatchRender(s)==class_2464.field_11455,"base hides model despite EMPTY");
   }
   System.out.println("RESULT baseline_regression_reproduced assertions="+assertions);return;
  }
  register(b,"fixture:source_water",new Rule("WATER"));
  for(var s:b.method_9595().method_11662()){
   var fluid=s.method_26227();check(!fluid.empty,"cache refreshed meta="+s.meta);
   check(fluid==b.dispatchFluid(s),"cache matches block fluid meta="+s.meta);
   check(fluid.amount==(s.meta==0||s.meta>=8?8:8-s.meta),"amount meta="+s.meta);
   check(fluid.falling==(s.meta>=8),"falling meta="+s.meta);
   check(b.dispatchRender(s)==class_2464.field_11455,"real water replaces model");
   check(s.cacheInitializations==2,"startup cache plus explicit refresh");
  }
  check(Rev233LiquidCompat.isLiquidBlock(b),"rev233 admission preserved");
  var state=b.method_9595().method_11662().get(0);var identity=state;
  state.clearCacheForTest();check(b.dispatchRender(state)==class_2464.field_11458,"stale cache is visible fallback");
  state.method_26200();check(b.dispatchRender(state)==class_2464.field_11455,"cache recovery resumes water");
  register(b,"fixture:source_water",new Rule("WATER"));
  check(b.method_9595().method_11662().get(0)==identity,"state identity preserved on repeated registration");
  for(String kind:new String[]{"LAVA","OTHER",null}){
   String id="fixture:nonwater_"+kind;var dry=new ConvertedLegacyBlock(id);
   register(dry,id,new Rule(kind));visibleEmpty(dry);
   for(var s:dry.method_9595().method_11662())check(b.dispatchFluid(s).empty,"cross-block mapping rejected");
  }
  var bad=new ConvertedLegacyBlock("fixture:bad_rule");register(bad,"fixture:bad_rule",new Object());visibleEmpty(bad);
  var partial=new ConvertedLegacyBlock("fixture:partial");
  partial.method_9595().method_11662().get(5).throwOnInit=true;
  partial.method_9595().method_11662().get(6).freezeCache=true;
  register(partial,"fixture:partial",new Rule("WATER"));
  for(var s:partial.method_9595().method_11662()){
   boolean failed=s.meta==5||s.meta==6;
   check(s.method_26227().empty==failed,"partial failure fluid status meta="+s.meta);
   check(partial.dispatchRender(s)==(failed?class_2464.field_11458:class_2464.field_11455),"partial failure never invisible+EMPTY");
  }
  partial.method_9595().method_11662().get(5).throwOnInit=false;
  partial.method_9595().method_11662().get(6).freezeCache=false;
  register(partial,"fixture:partial",new Rule("WATER"));
  for(var s:partial.method_9595().method_11662())check(!s.method_26227().empty,"failed states can recover");
  for(int m=0;m<16;m++){
   check(Rev239VanillaLiquidBridge.vanillaStateIndexForTest(m)==Math.min(m,8),"index");
   check(Rev239VanillaLiquidBridge.flowingAmountForTest(m)==(m==0||m>=8?8:8-m),"diagnostic amount");
   check(Rev239VanillaLiquidBridge.fallingForTest(m)==(m>=8),"diagnostic falling");
  }
  for(int m:new int[]{-1,16}){boolean rejected=false;try{Rev239VanillaLiquidBridge.vanillaStateIndexForTest(m);}catch(IllegalArgumentException ok){rejected=true;}check(rejected,"reject bad metadata");}
  register(null,null,null);
  System.out.println("RESULT fixed_lifecycle_pass assertions="+assertions);
 }
}'''
}

def run(base: Path, replacement: Path, work: Path) -> dict:
    source = work / 'test-source'
    classes = work / 'test-classes'
    paths = []
    for name, text in SOURCES.items():
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text + '\n', encoding='utf-8')
        paths.append(str(path))
    subprocess.run(['javac', '--release', '21', '-cp', str(base), '-d', str(classes), *paths], check=True)
    results = {'uses_minecraft_test_doubles': True, 'live_minecraft_launch': False}
    import os
    for mode, cp in [('baseline', [classes, base]), ('fixed', [classes, replacement, base])]:
        result = subprocess.run(['java', '-Xverify:all', '-cp', os.pathsep.join(map(str,cp)), 'LifecycleTest', mode],
                                check=True, text=True, capture_output=True)
        (work / f'{mode}-test.log').write_text(result.stdout + result.stderr, encoding='utf-8')
        marker = next(line for line in result.stdout.splitlines() if line.startswith('RESULT '))
        print(marker)
        results[mode] = marker
    return results

if __name__ == '__main__':
    print(json.dumps(run(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), Path(sys.argv[3]).resolve()), indent=2))
