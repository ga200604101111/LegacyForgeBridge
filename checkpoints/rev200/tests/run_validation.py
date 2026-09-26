#!/usr/bin/env python3
"""Pure-Java core + explicit recording-double adapter tests. NOT a Minecraft/Mixin/API build."""
import argparse, json, pathlib, subprocess, sys

DOUBLES = {
'net/minecraft/world/phys/Vec3.java': '''package net.minecraft.world.phys;
public final class Vec3 { public final double x,y,z; public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;} }''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;
public class Level { final boolean client; public Level(boolean c){client=c;} public boolean isClientSide(){return client;} }''',
'net/minecraft/world/entity/LivingEntity.java': '''package net.minecraft.world.entity;
import net.minecraft.world.level.Level; import net.minecraft.world.phys.Vec3;
public class LivingEntity { protected Level level; public boolean verticalCollision; public int hurtTime,tickCount; public double y;
private Vec3 velocity=new Vec3(0,0,0); public LivingEntity(Level l){level=l;} public Level level(){return level;}
public Vec3 getDeltaMovement(){return velocity;} public void setDeltaMovement(double x,double y,double z){velocity=new Vec3(x,y,z);}
public double getY(){return y;} }''',
'net/minecraft/client/player/LocalPlayer.java': '''package net.minecraft.client.player;
import net.minecraft.world.entity.LivingEntity; import net.minecraft.world.level.Level;
public class LocalPlayer extends LivingEntity { public boolean ground,water,lava,passenger,spectator,fallFlying,ladder,removed,dead;
public static class Abilities {public boolean flying;} public Abilities abilities=new Abilities();
public LocalPlayer(Level l){super(l);} public int getId(){return 17;} public boolean onGround(){return ground;}
public boolean isRemoved(){return removed;} public boolean isAlive(){return !dead;} public boolean isPassenger(){return passenger;}
public boolean isSpectator(){return spectator;} public Abilities getAbilities(){return abilities;}
public boolean isFallFlying(){return fallFlying;} public boolean isInWater(){return water;} public boolean isInLava(){return lava;}
public boolean onClimbable(){return ladder;} public boolean hasEffect(Object effect){return false;} }''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
import net.minecraft.client.player.LocalPlayer; import net.minecraft.world.level.Level;
public class Minecraft { private static final Minecraft INSTANCE=new Minecraft(); public LocalPlayer player; public Level level;
public Object connection,singleplayer; public boolean mainThread=true;
public static Minecraft getInstance(){return INSTANCE;} public boolean isSameThread(){return mainThread;}
public Object getConnection(){return connection;} public Object getSingleplayerServer(){return singleplayer;} }''',
'net/minecraft/world/effect/MobEffects.java': '''package net.minecraft.world.effect;
public class MobEffects { public static final Object LEVITATION=new Object(), SLOW_FALLING=new Object(); }''',
'net/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket.java': '''package net.minecraft.network.protocol.game;
import net.minecraft.world.phys.Vec3;
public class ClientboundSetEntityMotionPacket { private final int id; private final Vec3 movement;
public ClientboundSetEntityMotionPacket(int id,Vec3 movement){this.id=id;this.movement=movement;}
public int getId(){return id;} public Vec3 getMovement(){return movement;} }''',
'net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java': '''package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import java.util.*; import net.minecraft.client.Minecraft;
public class ClientTickEvents { public interface Tick {void tick(Minecraft mc);} public static class Event {
public final List<Tick> listeners=new ArrayList<>(); public void register(Tick t){listeners.add(t);}
public void fire(Minecraft mc){for(Tick t:listeners)t.tick(mc);} } public static final Event END_CLIENT_TICK=new Event(); }''',
'dev/yinghuang/legacyforgebridge/LegacyForgeBridge.java': '''package dev.yinghuang.legacyforgebridge;
public class LegacyForgeBridge { public static class Log { public void info(String text,Object... values){} }
public static final Log LOGGER=new Log(); }''',
'dev/yinghuang/legacyforgebridge/session/LegacySessionController.java': '''package dev.yinghuang.legacyforgebridge.session;
public class LegacySessionController { public static boolean legacy=true; public static boolean isLegacy1710(){return legacy;} }''',
'dev/yinghuang/legacyforgebridge/behavior/LegacyBehaviorRuntime.java': '''package dev.yinghuang.legacyforgebridge.behavior;
import net.minecraft.world.entity.LivingEntity;
public class LegacyBehaviorRuntime {public static int calls; public static void jump(LivingEntity e){calls++; var v=e.getDeltaMovement();e.setDeltaMovement(v.x,v.y+.15,v.z);} }''',
'net/minecraft/client/multiplayer/ClientPacketListener.java': '''package net.minecraft.client.multiplayer;
public class ClientPacketListener {}''',
'org/spongepowered/asm/mixin/Mixin.java': '''package org.spongepowered.asm.mixin;
public @interface Mixin {Class<?>[] value();}''',
'org/spongepowered/asm/mixin/injection/At.java': '''package org.spongepowered.asm.mixin.injection;
public @interface At {String value();}''',
'org/spongepowered/asm/mixin/injection/Inject.java': '''package org.spongepowered.asm.mixin.injection;
public @interface Inject {String[] method(); At at();}''',
'org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java': '''package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfo {}''',
}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=pathlib.Path,required=True);args=p.parse_args()
    root=pathlib.Path(__file__).resolve().parents[1];work=args.work.resolve()
    if work.exists():p.error('--work must be a NEW directory')
    work.mkdir(parents=True);out=work/'classes';out.mkdir();stubs=work/'recording-doubles'
    for name,text in DOUBLES.items():
        f=stubs/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text+'\n',encoding='utf-8')
    def run(command):
        r=subprocess.run(command,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=90)
        if r.returncode:raise RuntimeError('Command failed: '+' '.join(command)+'\n'+r.stdout)
        return r.stdout
    java_files=[*sorted((root/'src/main/java').rglob('*.java')),*sorted(stubs.rglob('*.java')),
                root/'tests/JumpEchoWindowTest.java',root/'tests/ClientJumpAdapterTest.java']
    compile_log=run(['javac','--release','21','-encoding','UTF-8','-d',str(out),*[str(f) for f in java_files]])
    (work/'compile.log').write_text(compile_log)
    logs=[run(['java','-Xverify:all','-cp',str(out),'JumpEchoWindowTest'])]
    for mode in ['observe','reconcile','off']:
        logs.append(run(['java','-Xverify:all','-Dlegacyforgebridge.jumpEcho='+mode,'-cp',str(out),'ClientJumpAdapterTest',mode]))
    text=''.join(logs);(work/'tests.log').write_text(text);print(text,end='')
    (work/'verification.json').write_text(json.dumps({'core':'PURE_JAVA','adapter':'EXPLICIT_RECORDING_DOUBLES',
        'java_version':run(['java','-version']).strip(),'logs':logs,'production_api_compile':False,
        'mixin_application_test':False,'minecraft_launch':False,'server_test':False},indent=2)+'\n')
if __name__=='__main__': main()
