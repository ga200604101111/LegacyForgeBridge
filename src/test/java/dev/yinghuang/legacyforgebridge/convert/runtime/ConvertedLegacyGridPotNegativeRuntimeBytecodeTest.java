package dev.yinghuang.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ConvertedLegacyGridPotNegativeRuntimeBytecodeTest {
    @Test
    void runtimeUsesExplicitInsertionRouteAndSharedRemovalPath() throws Exception {
        String resource="/"+ConvertedLegacyGridPotBlock.class.getName().replace('.','/')+".class";
        try(InputStream input=ConvertedLegacyGridPotBlock.class.getResourceAsStream(resource)){
            assertNotNull(input);boolean[] route={false},sharedFromItem={false},sharedFromEmpty={false};
            boolean[] removeItem={false},removeCell={false},empty={false},removeBlock={false},drop={false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                            if(name.equals("useItemOn")&&owner.endsWith("/LegacyGridPotBlockRegistry$Rule")&&method.equals("insertionRoute"))route[0]=true;
                            if(owner.endsWith("/ConvertedLegacyGridPotBlock")&&method.equals("removeStoredOrCell")){
                                if(name.equals("useItemOn"))sharedFromItem[0]=true;if(name.equals("useWithoutItem"))sharedFromEmpty[0]=true;
                            }
                            if(name.equals("removeStoredOrCell")&&owner.endsWith("/ConvertedLegacyGridPotBlockEntity")&&method.equals("removeItem"))removeItem[0]=true;
                            if(name.equals("removeStoredOrCell")&&owner.endsWith("/ConvertedLegacyGridPotBlockEntity")&&method.equals("removeCell"))removeCell[0]=true;
                            if(name.equals("removeStoredOrCell")&&owner.endsWith("/ConvertedLegacyGridPotBlockEntity")&&method.equals("isGridEmpty"))empty[0]=true;
                            if(name.equals("removeStoredOrCell")&&method.equals("removeBlock"))removeBlock[0]=true;
                            if(name.equals("removeStoredOrCell")&&owner.equals("net/minecraft/world/Containers")&&method.equals("dropItemStack"))drop[0]=true;
                        }
                    };}
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(route[0]);assertTrue(sharedFromItem[0]);assertTrue(sharedFromEmpty[0]);
            assertTrue(removeItem[0]);assertTrue(removeCell[0]);assertTrue(empty[0]);assertTrue(removeBlock[0]);assertTrue(drop[0]);
        }
    }
}
