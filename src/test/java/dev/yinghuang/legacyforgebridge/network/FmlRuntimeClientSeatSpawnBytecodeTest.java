package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class FmlRuntimeClientSeatSpawnBytecodeTest {
    @Test void entitySpawnPathUsesProofGatedRemoteSeatMappingAndClientLevelInsertion() throws Exception {
        String resource="/"+FmlRuntimeClient.class.getName().replace('.','/')+".class";
        try(InputStream input=FmlRuntimeClient.class.getResourceAsStream(resource)){
            assertNotNull(input);boolean[] lookup={false},strict={false},add={false},seat={false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if(!name.equals("handleEntitySpawn")&&!name.equals("applyRemoteSeatSpawn"))return null;
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitTypeInsn(int opcode,String type){if(opcode==Opcodes.NEW&&type.endsWith("/ConvertedLegacySeatEntity"))seat[0]=true;}
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                            if(owner.endsWith("/LegacySeatBedRegistry")&&method.equals("remoteSpawnRule"))lookup[0]=true;
                            if(owner.endsWith("/FmlRuntimeCodec")&&method.equals("parseSimpleEntitySpawn"))strict[0]=true;
                            if(owner.equals("net/minecraft/client/multiplayer/ClientLevel")&&method.equals("addEntity"))add[0]=true;
                        }
                    };}
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(lookup[0]);assertTrue(strict[0]);assertTrue(seat[0]);assertTrue(add[0]);
        }
    }
}
