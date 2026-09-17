package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class FmlRuntimeClientPlainSpawnBytecodeTest {
    @Test void plainEntitySpawnPathUsesRemoteRuleFactoryTypedWatcherBridgeAndClientLevelInsertion() throws Exception {
        String resource="/"+FmlRuntimeClient.class.getName().replace('.','/')+".class";
        try(InputStream input=FmlRuntimeClient.class.getResourceAsStream(resource)){
            assertNotNull(input);boolean[] lookup={false},strict={false},create={false},watcher={false},add={false},baseGate={false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if(!name.equals("handleEntitySpawn")&&!name.equals("applyRemotePlainSpawn")&&!name.equals("isDefaultLegacyEntityBaseWatcher"))return null;
                    if(name.equals("isDefaultLegacyEntityBaseWatcher"))baseGate[0]=true;
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                            if(owner.endsWith("/LegacyPlainEntityRegistry")&&method.equals("remoteSpawnRule"))lookup[0]=true;
                            if(owner.endsWith("/FmlRuntimeCodec")&&method.equals("parseSimpleEntitySpawn"))strict[0]=true;
                            if(owner.endsWith("/LegacyPlainEntityRegistry")&&method.equals("create"))create[0]=true;
                            if(owner.endsWith("/LegacyPlainEntityWatcherBridge")&&method.equals("legacyforgebridge$applyWatcher"))watcher[0]=true;
                            if(owner.equals("net/minecraft/client/multiplayer/ClientLevel")&&method.equals("addEntity"))add[0]=true;
                        }
                    };}
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(lookup[0]);assertTrue(strict[0]);assertTrue(create[0]);assertTrue(watcher[0]);assertTrue(add[0]);assertTrue(baseGate[0]);
        }
    }
}
