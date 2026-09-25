package dev.yinghuang.legacyforgebridge.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyNameTagBehaviorMixinBytecodeTest {
    @Test void exact12111ExtractRenderStateTargetExistsAndMixinClearsOnlyRenderStateNameTag() throws Exception {
        assertNotNull(EntityRenderer.class.getDeclaredMethod("extractRenderState",Entity.class,EntityRenderState.class,float.class));
        String resource="/"+LegacyNameTagBehaviorMixin.class.getName().replace('.','/')+".class";
        try(InputStream input=LegacyNameTagBehaviorMixin.class.getResourceAsStream(resource)){
            assertNotNull(input);byte[] bytes=input.readAllBytes();boolean[] runtimeCall={false},nameTagWrite={false};
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){if(owner.endsWith("/LegacyBehaviorRuntime")&&name.equals("renderSpecialsPre"))runtimeCall[0]=true;}@Override public void visitFieldInsn(int opcode,String owner,String name,String descriptor){if(opcode==Opcodes.PUTFIELD&&owner.equals("net/minecraft/client/renderer/entity/state/EntityRenderState")&&name.equals("nameTag"))nameTagWrite[0]=true;}};
            }},ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);assertTrue(runtimeCall[0]);assertTrue(nameTagWrite[0]);
        }
    }
}
