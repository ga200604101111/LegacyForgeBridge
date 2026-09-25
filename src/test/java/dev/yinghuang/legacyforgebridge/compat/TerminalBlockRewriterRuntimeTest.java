package dev.yinghuang.legacyforgebridge.compat;

import com.viaversion.viaversion.api.data.*;
import com.viaversion.viaversion.api.minecraft.BlockChangeRecord;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.*;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.type.Types;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Execute upstream handlers with a synthetic registry/packet transport, not a Minecraft connection. */
public class TerminalBlockRewriterRuntimeTest {
    private static final String CLASS="com.viaversion.viaversion.rewriter.BlockRewriter";
    public static boolean terminalIdentity(Mappings mapping) {
        return LegacyTerminalBlockRewritePolicy.maySkip(LegacyTerminalBlockRewritePolicy.PROTOCOL,true,Mappings.isIntIdIdentity(mapping));
    }
    @SuppressWarnings("unchecked")
    private static <T>T proxy(Class<T> type,java.lang.reflect.InvocationHandler calls){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},calls);
    }
    private static Class<?> upstream(boolean corrected)throws Exception {
        ClassNode node=Terminal140Checks.read(CLASS.replace('.','/'));
        if(corrected)for(var m:node.methods)for(var n:m.instructions)
            if(n instanceof MethodInsnNode call && call.owner.equals("com/viaversion/viaversion/api/data/Mappings")&&call.name.equals("isIntIdIdentity")){
                call.owner=TerminalBlockRewriterRuntimeTest.class.getName().replace('.','/');call.name="terminalIdentity";call.itf=false;
            }
        ClassWriter writer=new ClassWriter(0);node.accept(writer);byte[] bytes=writer.toByteArray();
        return new ClassLoader(TerminalBlockRewriterRuntimeTest.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.equals(CLASS))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)) {Class<?> c=findLoadedClass(name);if(c==null)c=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(c);return c;}
            }
        }.loadClass(CLASS);
    }
    private static List<PacketHandler> handlers(boolean corrected)throws Exception {
        Mappings identity=new IdentityMappings(29671,29671);
        MappingData mapping=proxy(MappingData.class,(p,m,a)->switch(m.getName()){
            case "getBlockStateMappings"->identity;
            case "getNewBlockStateId"->mapped((Integer)a[0]);
            default->throw new AssertionError("Unexpected mapping method "+m);
        });
        List<PacketHandler> handlers=new ArrayList<>();
        Protocol<?,?,?,?> protocol=proxy(Protocol.class,(p,m,a)->switch(m.getName()){
            case "getMappingData"->mapping;
            case "registerClientbound"->{handlers.add((PacketHandler)a[a.length-1]);yield null;}
            default->throw new AssertionError("Unexpected protocol method "+m);
        });
        Class<?> type=upstream(corrected);
        Object rewriter=type.getConstructors()[0].newInstance(protocol,null,null,null,null);
        type.getMethod("registerBlockUpdate",ClientboundPacketType.class).invoke(rewriter,new Object[]{null});
        type.getMethod("registerSectionBlocksUpdate1_20",ClientboundPacketType.class).invoke(rewriter,new Object[]{null});
        return handlers;
    }
    private static int mapped(int state){return state>=29671&&state<30679?32000-(state-29671):state;}
    @Test void identityOptimizationReallyRemovesHandlersUntilPolicyIsApplied()throws Exception {
        assertTrue(handlers(false).isEmpty());assertEquals(2,handlers(true).size());
    }
    @Test void actualSingleUpdateHandlerRestoresAll1008CarriersAndLeavesVanillaAlone()throws Exception {
        PacketHandler handler=handlers(true).getFirst();
        for(int value=29671;value<30679;value++)assertEquals(mapped(value),single(handler,value));
        for(int value:List.of(0,1,16,160,29670))assertEquals(value,single(handler,value));
    }
    private static int single(PacketHandler handler,int state)throws Exception {
        AtomicInteger output=new AtomicInteger(-1);
        PacketWrapper packet=proxy(PacketWrapper.class,(p,m,a)->switch(m.getName()){
            case "passthrough"->null;
            case "read"->state;
            case "write"->{output.set((Integer)a[1]);yield null;}
            default->throw new AssertionError("Unexpected packet operation "+m);
        });handler.handle(packet);return output.get();
    }
    @Test void actualSectionHandlerDoesNotConfuseMetadataOrEntryOrder()throws Exception {
        PacketHandler handler=handlers(true).get(1);
        int[] values={29671,29686,30678,16,29703};int[] before=values.clone();BlockChangeRecord[] records=new BlockChangeRecord[values.length];
        for(int i=0;i<records.length;i++){final int index=i;records[i]=proxy(BlockChangeRecord.class,(p,m,a)->switch(m.getName()){
            case "getBlockId"->values[index];case "setBlockId"->{values[index]=(Integer)a[0];yield null;}
            default->throw new AssertionError("Unexpected record operation "+m);
        });}
        PacketWrapper packet=proxy(PacketWrapper.class,(p,m,a)->{
            if(m.getName().equals("passthrough"))return a[0]==Types.LONG?0L:records;
            throw new AssertionError("Unexpected packet operation "+m);
        });handler.handle(packet);
        for(int i=0;i<values.length;i++)assertEquals(mapped(before[i]),values[i]);
    }
}
