package dev.yinghuang.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedModSupportSeatBedBytecodeTest {
    @Test void generatedRegistrationSelectsSpecializedSeatBedBlockAndPlacementItem() throws Exception {
        String resource = "/" + GeneratedModSupport.class.getName().replace('.', '/') + ".class";
        try (InputStream input = GeneratedModSupport.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            boolean[] block = {false};boolean[] item = {false};boolean[] load = {false};
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitTypeInsn(int opcode,String type) {
                            if(opcode!=Opcodes.NEW)return;
                            if(type.endsWith("/ConvertedLegacySeatBedBlock"))block[0]=true;
                            if(type.endsWith("/ConvertedLegacySeatBedItem"))item[0]=true;
                        }
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf) {
                            if(owner.endsWith("/LegacySeatBedRegistry")&&method.equals("loadMod"))load[0]=true;
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(load[0]);assertTrue(block[0]);assertTrue(item[0]);
        }
    }
}
