package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FmlRuntimeClientSpawnPoseBytecodeTest {
    private static final Set<String> REMOTE_SPAWNS=Set.of(
            "applyRemoteSeatSpawn",
            "applyRemoteVisibleSpawn",
            "applyRemoteProjectileSpawn",
            "applyRemoteRotatingSpawn",
            "applyRemotePlainSpawn"
    );

    @Test
    void everyConvertedFmlSpawnUsesSnapInitializedRemotePose() throws Exception {
        String resource="/"+FmlRuntimeClient.class.getName().replace('.','/')+".class";
        try(InputStream input=FmlRuntimeClient.class.getResourceAsStream(resource)){
            assertNotNull(input);
            int[] spawnHelperCalls={0};
            boolean[] helperSeen={false},snap={false},packetBase={false},legacySplitPose={false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    boolean spawn=REMOTE_SPAWNS.contains(name);
                    boolean helper=name.equals("initializeRemoteSpawnPose");
                    if(!spawn&&!helper)return null;
                    if(helper)helperSeen[0]=true;
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                            if(spawn&&owner.endsWith("/FmlRuntimeClient")&&method.equals("initializeRemoteSpawnPose"))
                                spawnHelperCalls[0]++;
                            if(helper&&owner.equals("net/minecraft/world/entity/Entity")&&method.equals("snapTo")
                                    &&desc.equals("(DDDFF)V"))snap[0]=true;
                            if(helper&&owner.equals("net/minecraft/world/entity/Entity")&&method.equals("syncPacketPositionCodec")
                                    &&desc.equals("(DDD)V"))packetBase[0]=true;
                            if(helper&&owner.equals("net/minecraft/world/entity/Entity")
                                    &&Set.of("setPos","setYRot","setXRot").contains(method))legacySplitPose[0]=true;
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);

            assertEquals(REMOTE_SPAWNS.size(),spawnHelperCalls[0],
                    "seat/visible/projectile/rotating/plain spawn paths must all share one pose initializer");
            assertTrue(helperSeen[0]);
            assertTrue(snap[0],"modern snapTo must initialize current and previous render pose together");
            assertTrue(packetBase[0],"legacy fixed-point packet baseline still has to be synchronized");
            assertFalse(legacySplitPose[0],"split setPos/setYRot/setXRot reintroduces first-frame interpolation drift");
        }
    }
}
