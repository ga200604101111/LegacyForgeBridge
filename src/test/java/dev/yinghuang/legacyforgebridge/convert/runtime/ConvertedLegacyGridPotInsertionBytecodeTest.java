package dev.yinghuang.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyGridPotInsertionBytecodeTest {
    @Test
    void sourceProvenInsertionGateIsWiredThroughReplacementDropStoreAndConsumption() throws Exception {
        String resource="/"+ConvertedLegacyGridPotBlock.class.getName().replace('.','/')+".class";
        try(InputStream input=ConvertedLegacyGridPotBlock.class.getResourceAsStream(resource)){
            assertNotNull(input);
            boolean[] eligible={false},remove={false},store={false},shrink={false},drop={false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if(!"useItemOn".equals(name))return null;
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                            if(owner.endsWith("/LegacyGridPotBlockRegistry$Rule")&&"insertionEligible".equals(method))eligible[0]=true;
                            if(owner.endsWith("/ConvertedLegacyGridPotBlockEntity")&&"removeItem".equals(method))remove[0]=true;
                            if(owner.endsWith("/ConvertedLegacyGridPotBlockEntity")&&"setItem".equals(method))store[0]=true;
                            if(owner.equals("net/minecraft/world/item/ItemStack")&&"shrink".equals(method))shrink[0]=true;
                            if(owner.equals("net/minecraft/world/Containers")&&"dropItemStack".equals(method))drop[0]=true;
                        }
                    };}
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(eligible[0]);assertTrue(remove[0]);assertTrue(store[0]);assertTrue(shrink[0]);assertTrue(drop[0]);
        }
    }
}
