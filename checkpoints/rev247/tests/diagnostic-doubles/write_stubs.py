"""Minimal API test doubles. These are not Minecraft and are NEVER packaged."""
from pathlib import Path
SOURCES={
'net/minecraft/class_243.java':'''package net.minecraft; public final class class_243 {
public final double field_1352,field_1351,field_1350;
public class_243(double x,double y,double z){field_1352=x;field_1351=y;field_1350=z;}
}''',
'net/minecraft/class_1297.java':'''package net.minecraft; public class class_1297 {
public int field_6012=10,id=42; public double x=2,y=4,z=6,field_6036=4,field_6017=0;
public boolean field_5976=false,field_5992=false,ground=false,water=false,lava=false,vehicle=false;
public class_243 velocity=new class_243(.1,.3,.2);
public double method_23317(){return x;} public double method_23318(){return y;} public double method_23321(){return z;}
public double method_23320(){return y+1.62;} public Object method_5829(){return "Box[feet="+y+"]";}
public class_243 method_18798(){return velocity;} public void method_18800(double x,double y,double z){velocity=new class_243(x,y,z);}
public boolean method_24828(){return ground;} public boolean method_5799(){return water;}public boolean method_5771(){return lava;}
public boolean method_5765(){return vehicle;}public int method_5628(){return id;}
}''',
'net/minecraft/class_1309.java':'''package net.minecraft; public class class_1309 extends class_1297 {
public int field_6235;public boolean climbing;public boolean method_6101(){return climbing;}
public java.util.List<String> method_6026(){return java.util.List.of("test_effect");}
}''',
'net/minecraft/class_1656.java':'''package net.minecraft; public class class_1656 {public boolean field_7479;}''',
'net/minecraft/class_2561.java':'''package net.minecraft;public record class_2561(String value){public static class_2561 method_43470(String s){return new class_2561(s);}}''',
'net/minecraft/class_1657.java':'''package net.minecraft;public class class_1657 extends class_1309 {
public final class_1656 abilities=new class_1656(); public class_1656 method_31549(){return abilities;}
public final java.util.List<String> notices=new java.util.ArrayList<>(); public void method_7353(class_2561 t,boolean b){notices.add(t.value());}
}''',
'net/minecraft/class_744.java':'''package net.minecraft;public class class_744{public Object field_54155="PlayerInput[jump=true,forward=false]";}''',
'net/minecraft/class_746.java':'''package net.minecraft;public class class_746 extends class_1657 {public class_744 field_3913=new class_744();}''',
'net/minecraft/class_304.java':'''package net.minecraft;public class class_304{public boolean pressed;public int consumed;public boolean method_1434(){return pressed;}}''',
'net/minecraft/class_315.java':'''package net.minecraft;public class class_315{public class_304 field_1903=new class_304(),field_1832=new class_304();}''',
'net/minecraft/class_310.java':'''package net.minecraft;public class class_310 {
private static final class_310 INSTANCE=new class_310();public class_746 field_1724=new class_746();public Object field_1687=new Object();
public class_315 field_1690=new class_315();public Object network=new Object(),server=null; public Thread main=Thread.currentThread();
public static class_310 method_1551(){return INSTANCE;}public boolean method_18854(){return Thread.currentThread()==main;}
public Object method_1562(){return network;}public Object method_1576(){return server;}
}''',
'net/minecraft/class_2743.java':'''package net.minecraft;public record class_2743(int id,class_243 v){public int method_11818(){return id;}public class_243 method_73085(){return v;}}''',
'net/minecraft/class_4184.java':'''package net.minecraft;public class class_4184 {
private float field_18721=1.62f,field_18722=1.62f;public class_1297 focus;public class_243 pos=new class_243(2,6,6);
public class_243 method_71156(){return pos;}public class_1297 method_19331(){return focus;}public float method_55437(){return .5f;}
public boolean method_19333(){return false;}public float method_19330(){return 90;}public float method_19329(){return 10;}
}''',
'dev/yinghuang/legacyforgebridge/session/LegacySessionController.java':'''package dev.yinghuang.legacyforgebridge.session;public final class LegacySessionController{public static boolean legacy=true;public static boolean isLegacy1710(){return legacy;}}''',
'net/fabricmc/loader/api/FabricLoader.java':'''package net.fabricmc.loader.api; public final class FabricLoader {
public static FabricLoader getInstance(){return new FabricLoader();}public java.nio.file.Path getGameDir(){return java.nio.file.Path.of(System.getProperty("lfb.test.dir"));}
}''',
'net/fabricmc/fabric/api/event/Event.java':'''package net.fabricmc.fabric.api.event;public class Event<T>{public final java.util.List<T> listeners=new java.util.ArrayList<>();public void register(T t){listeners.add(t);}}''',
'net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java':'''package net.fabricmc.fabric.api.client.event.lifecycle.v1;import net.fabricmc.fabric.api.event.Event;
public final class ClientTickEvents {public interface StartTick{void onStartTick(net.minecraft.class_310 c);}public static final Event<StartTick> START_CLIENT_TICK=new Event<>();}
''',
'net/fabricmc/fabric/api/client/message/v1/ClientSendMessageEvents.java':'''package net.fabricmc.fabric.api.client.message.v1;import net.fabricmc.fabric.api.event.Event;
public final class ClientSendMessageEvents {public interface AllowCommand{boolean allowSendCommand(String text);}public static final Event<AllowCommand> ALLOW_COMMAND=new Event<>();}
''',
}
def write(root):
    paths=[]
    for name,text in SOURCES.items():
        p=Path(root)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text+'\n',encoding='utf-8');paths.append(str(p))
    return paths
