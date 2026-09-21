package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class FmlRuntimeClientVisibleSpawnBytecodeTest {
    @Test void visibleEntitySpawnPathUsesRemoteRuleFactoryTypedWatcherApplicationAndClientInsertion()throws Exception{
        String resource="/"+FmlRuntimeClient.class.getName().replace('.','/')+".class";
        try(InputStream input=FmlRuntimeClient.class.getResourceAsStream(resource)){
            assertNotNull(input);boolean[] lookup={false},strict={false},create={false},watcher={false},add={false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if(!name.equals("handleEntitySpawn")&&!name.equals("applyRemoteVisibleSpawn"))return null;
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                            if(owner.endsWith("/LegacyVisibleEntityRegistry")&&method.equals("remoteSpawnRule"))lookup[0]=true;
                            if(owner.endsWith("/FmlRuntimeCodec")&&method.equals("parseSimpleEntitySpawn"))strict[0]=true;
                            if(owner.endsWith("/LegacyVisibleEntityRegistry")&&method.equals("create"))create[0]=true;
                            if(owner.endsWith("/ConvertedLegacyVisualEntity")&&method.equals("applyLegacyWatcher"))watcher[0]=true;
                            if(owner.equals("net/minecraft/client/multiplayer/ClientLevel")&&method.equals("addEntity"))add[0]=true;
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(lookup[0]);assertTrue(strict[0]);assertTrue(create[0]);assertTrue(watcher[0]);assertTrue(add[0]);
        }
    }
}
