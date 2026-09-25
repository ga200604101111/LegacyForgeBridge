package dev.yinghuang.legacyforgebridge.mixin.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class LegacyHeldItemVisibilityMixinBytecodeTest {
    @Test void mixinUsesHeldItemRuleMainHandAndClientStateRewrite() throws Exception {
        String resource="/"+LegacyHeldItemVisibilityMixin.class.getName().replace('.','/')+".class";
        try(InputStream input=LegacyHeldItemVisibilityMixin.class.getResourceAsStream(resource)){
            assertNotNull(input);boolean[] rule={false},hand={false},set={false},meta={false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
                    if(!name.contains("heldOwnBlockVisibility"))return null;
                    return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                        if(owner.endsWith("/LegacyHeldItemVisibilityRegistry")&&method.equals("rule"))rule[0]=true;
                        // Minecraft.player is typed as LocalPlayer, while getMainHandItem is inherited.
                        // javac may therefore use LocalPlayer, Player or LivingEntity as the call owner;
                        // pin the exact semantic method descriptor instead of one incidental owner.
                        if(method.equals("getMainHandItem")&&descriptor.equals("()Lnet/minecraft/world/item/ItemStack;"))hand[0]=true;
                        if(owner.equals("net/minecraft/world/level/Level")&&method.equals("setBlock"))set[0]=true;
                        if(owner.endsWith("/ConvertedLegacyBlock")&&method.equals("withLegacyMeta"))meta[0]=true;
                    }};
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(rule[0]);assertTrue(hand[0]);assertTrue(set[0]);assertTrue(meta[0]);
        }
    }
}
