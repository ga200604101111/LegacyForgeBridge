package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Generic source-proven legacy liquid client presentation helpers. */
public final class Rev233LiquidCompat {
    private static final Set<String> LIQUID_IDS=ConcurrentHashMap.newKeySet();
    private static final Set<String> TINT_REGISTERED=ConcurrentHashMap.newKeySet();
    private Rev233LiquidCompat(){}

    public static Object tintModel(Object model){
        if(model==null)return null;
        try{
            Object elements=invokeCompatible(model,"getAsJsonArray","elements");if(!(elements instanceof Iterable<?> iterable))return model;
            for(Object el:iterable){Object obj=invokeNoArgs(el,"getAsJsonObject");Object faces=invokeCompatible(obj,"getAsJsonObject","faces");if(faces==null)continue;
                Object entries=invokeNoArgs(faces,"entrySet");if(!(entries instanceof Iterable<?> faceEntries))continue;
                for(Object entry:faceEntries){Object value=invokeNoArgs(entry,"getValue");Object face=invokeNoArgs(value,"getAsJsonObject");invokeCompatible(face,"addProperty","tintindex",Integer.valueOf(0));}
            }
        }catch(Throwable ignored){}
        return model;
    }

    public static void registerLiquid(Object block,Object id,Object jsonRule){
        if(block==null||id==null)return;String text=String.valueOf(id);LIQUID_IDS.add(text);
        try{Object kindElement=invokeCompatible(jsonRule,"get","kind");String kind=kindElement==null?null:String.valueOf(invokeNoArgs(kindElement,"getAsString"));if("WATER".equals(kind))registerWaterTint(block,text);}catch(Throwable ignored){}
    }

    public static boolean isLiquidBlock(Object block){
        if(block==null)return false;
        try{Field f=findField(block.getClass(),"convertedId");if(f==null)return false;f.setAccessible(true);Object id=f.get(block);return id!=null&&LIQUID_IDS.contains(String.valueOf(id));}catch(Throwable ignored){return false;}
    }

    private static void registerWaterTint(Object block,String id)throws Exception{
        if(!TINT_REGISTERED.add(id))return;
        Class<?> providerType=Class.forName("net.minecraft.class_322");
        Object provider=Proxy.newProxyInstance(providerType.getClassLoader(),new Class[]{providerType},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "toString"->"LFBWaterTint";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->null;};
            if(args==null||args.length<4||args[1]==null||args[2]==null)return -1;
            Class<?> biome=Class.forName("net.minecraft.class_1163");
            Method water=biome.getMethod("method_4961",Class.forName("net.minecraft.class_1920"),Class.forName("net.minecraft.class_2338"));
            return Rev237LiquidAlpha.apply(water.invoke(null,args[1],args[2]));
        });
        Class<?> registryClass=Class.forName("net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry");Object registry=registryClass.getField("BLOCK").get(null);
        Class<?> blockClass=Class.forName("net.minecraft.class_2248");Object array=Array.newInstance(blockClass,1);Array.set(array,0,block);
        Method register=findCompatibleMethod(registry.getClass(),"register",provider,array);if(register==null)throw new NoSuchMethodException("ColorProviderRegistry.BLOCK.register");register.setAccessible(true);register.invoke(registry,provider,array);
    }

    private static Object invokeNoArgs(Object target,String name)throws Exception{Method m=findMethod(target.getClass(),name,0);if(m==null)throw new NoSuchMethodException(name);m.setAccessible(true);return m.invoke(target);}
    private static Object invokeCompatible(Object target,String name,Object...args)throws Exception{Method m=findCompatibleMethod(target.getClass(),name,args);if(m==null)throw new NoSuchMethodException(name);m.setAccessible(true);return m.invoke(target,args);}
    private static Method findMethod(Class<?> type,String name,int count){for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==count)return m;for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==count)return m;return null;}
    private static Method findCompatibleMethod(Class<?> type,String name,Object...args){for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()){if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue;Class<?>[]p=m.getParameterTypes();boolean ok=true;for(int i=0;i<p.length;i++)if(args[i]!=null&&!p[i].isAssignableFrom(args[i].getClass())){ok=false;break;}if(ok)return m;}for(Method m:type.getMethods()){if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue;Class<?>[]p=m.getParameterTypes();boolean ok=true;for(int i=0;i<p.length;i++)if(args[i]!=null&&!p[i].isAssignableFrom(args[i].getClass())){ok=false;break;}if(ok)return m;}return null;}
    private static Field findField(Class<?> type,String name){for(Class<?> c=type;c!=null;c=c.getSuperclass())try{return c.getDeclaredField(name);}catch(NoSuchFieldException ignored){}return null;}
}
