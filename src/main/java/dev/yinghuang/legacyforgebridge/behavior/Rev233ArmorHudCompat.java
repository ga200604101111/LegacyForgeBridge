package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.*;
import java.util.function.BiConsumer;

/** Client-only 1.7.10 armor HUD projection. Server combat remains authoritative. */
public final class Rev233ArmorHudCompat {
    private static final String[] ARMOR_SLOTS={"head","chest","legs","feet"};
    private Rev233ArmorHudCompat(){}

    public static int correctedArmor(Object entity,int original){
        try{
            if(entity==null||!isLocal1710(entity))return original;
            Object armorHolder=Class.forName("net.minecraft.class_5134").getField("field_23724").get(null);
            double base=0D,add=0D,mulBase=0D,mulTotalFactor=1D;
            boolean found=false;
            for(String slotName:ARMOR_SLOTS){
                Object slot=slot(slotName);if(slot==null)continue;
                Object stack=invokeCompatible(entity,"method_6118",slot);if(stack==null)continue;
                String itemId=itemId(stack);
                Object rule=itemId==null?null:sourceRule(itemId);
                boolean sourceBase=false;
                if(rule!=null){
                    Object points=invokeNoArgs(rule,"armorPoints");
                    Object armorSlot=invokeNoArgs(rule,"armorSlot");
                    if(points instanceof Number n&&armorSlot instanceof Number&&n.doubleValue()>0D){base+=n.doubleValue();sourceBase=true;found=true;}
                }
                final boolean skipBridgeBase=sourceBase;
                final double[] sums={0D,0D,1D};
                BiConsumer<Object,Object> consumer=(attribute,modifier)->{
                    try{
                        if(attribute==null||modifier==null||!(attribute==armorHolder||attribute.equals(armorHolder)))return;
                        String id=modifierId(modifier);
                        if(skipBridgeBase&&isBridgeArmorCarrier(id))return;
                        double amount=modifierAmount(modifier);int op=modifierOperation(modifier);
                        if(op==0)sums[0]+=amount;else if(op==1)sums[1]+=amount;else if(op==2)sums[2]*=(1D+amount);
                    }catch(Throwable ignored){}
                };
                Method apply=findCompatibleMethod(stack.getClass(),"method_57354",slot,consumer);
                if(apply!=null){apply.setAccessible(true);apply.invoke(stack,slot,consumer);}
                if(sums[0]!=0D||sums[1]!=0D||sums[2]!=1D)found=true;
                add+=sums[0];mulBase+=sums[1];mulTotalFactor*=sums[2];
            }
            if(!found)return original;
            double value=(base+add+base*mulBase)*mulTotalFactor;
            int armor=(int)Math.floor(value+1.0E-7D);
            return Math.max(0,Math.min(30,armor));
        }catch(Throwable ignored){return original;}
    }

    private static boolean isLocal1710(Object entity)throws Exception{
        Class<?> backend=Class.forName("dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend");
        Object instance=backend.getField("INSTANCE").get(null);
        Object target=invokeNoArgs(instance,"isMinecraft1710Target");
        if(!Boolean.TRUE.equals(target))return false;
        Class<?> mc=Class.forName("net.minecraft.class_310");
        Method get=mc.getMethod("method_1551");Object client=get.invoke(null);
        Field player=findField(mc,"field_1724");if(player==null)return false;player.setAccessible(true);
        return player.get(client)==entity;
    }
    private static Object slot(String name)throws Exception{
        Class<?> type=Class.forName("net.minecraft.class_1304");Method m=type.getMethod("method_5924",String.class);return m.invoke(null,name);
    }
    private static String itemId(Object stack)throws Exception{
        Object item=invokeNoArgs(stack,"method_7909");if(item==null)return null;
        Class<?> registries=Class.forName("net.minecraft.class_7923");Object registry=registries.getField("field_41178").get(null);
        Object id=invokeCompatible(registry,"method_10221",item);return id==null?null:String.valueOf(id);
    }
    private static Object sourceRule(String id)throws Exception{
        Class<?> rt=Class.forName("dev.yinghuang.legacyforgebridge.compat.LegacySourceItemRuntime");
        Method m=rt.getMethod("rule",String.class);return m.invoke(null,id);
    }
    private static boolean isBridgeArmorCarrier(String id){
        if(id==null)return false;
        return id.startsWith("legacyforgebridge:source_armor/")||id.startsWith("legacyforgebridge:compat_armor/")
                ||(id.contains(":converted/")&&id.endsWith("_armor"));
    }
    private static String modifierId(Object modifier)throws Exception{
        for(Field f:modifier.getClass().getDeclaredFields())if(f.getType().getName().equals("net.minecraft.class_2960")){f.setAccessible(true);Object v=f.get(modifier);return v==null?null:String.valueOf(v);}
        for(Method m:modifier.getClass().getDeclaredMethods())if(m.getParameterCount()==0&&m.getReturnType().getName().equals("net.minecraft.class_2960")){m.setAccessible(true);Object v=m.invoke(modifier);return v==null?null:String.valueOf(v);}
        return null;
    }
    private static double modifierAmount(Object modifier)throws Exception{
        try{Method m=modifier.getClass().getDeclaredMethod("comp_2449");m.setAccessible(true);return ((Number)m.invoke(modifier)).doubleValue();}catch(NoSuchMethodException ignored){}
        for(Field f:modifier.getClass().getDeclaredFields())if(f.getType()==double.class){f.setAccessible(true);return f.getDouble(modifier);}return 0D;
    }
    private static int modifierOperation(Object modifier)throws Exception{
        Object op=null;
        for(Field f:modifier.getClass().getDeclaredFields())if(f.getType().getName().equals("net.minecraft.class_1322$class_1323")){f.setAccessible(true);op=f.get(modifier);break;}
        if(op==null)for(Method m:modifier.getClass().getDeclaredMethods())if(m.getParameterCount()==0&&m.getReturnType().getName().equals("net.minecraft.class_1322$class_1323")){m.setAccessible(true);op=m.invoke(modifier);break;}
        if(op==null)return 0;Method id=findMethod(op.getClass(),"method_56082",0);if(id==null)return 0;id.setAccessible(true);Object v=id.invoke(op);return v instanceof Number n?n.intValue():0;
    }
    private static Object invokeNoArgs(Object target,String name)throws Exception{Method m=findMethod(target.getClass(),name,0);if(m==null)throw new NoSuchMethodException(name);m.setAccessible(true);return m.invoke(target);}
    private static Object invokeCompatible(Object target,String name,Object...args)throws Exception{Method m=findCompatibleMethod(target.getClass(),name,args);if(m==null)throw new NoSuchMethodException(name);m.setAccessible(true);return m.invoke(target,args);}
    private static Method findMethod(Class<?> type,String name,int count){for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==count)return m;return null;}
    private static Method findCompatibleMethod(Class<?> type,String name,Object...args){for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()){if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue;Class<?>[]p=m.getParameterTypes();boolean ok=true;for(int i=0;i<p.length;i++)if(args[i]!=null&&!p[i].isAssignableFrom(args[i].getClass())){ok=false;break;}if(ok)return m;}return null;}
    private static Field findField(Class<?> type,String name){for(Class<?> c=type;c!=null;c=c.getSuperclass())try{return c.getDeclaredField(name);}catch(NoSuchFieldException ignored){}return null;}
}
