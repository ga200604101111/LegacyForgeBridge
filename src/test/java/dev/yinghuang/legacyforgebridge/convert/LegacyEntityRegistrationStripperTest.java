package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityRegistrationStripperTest {
    @Test void removesOneExactPureRegisterModEntityStackSlice() {
        byte[] source=fixture(false);
        var target=new LegacyEntityRegistrationStripper.Target("preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V","foreign/Orb","orb",17,80,2,true);
        var result=new LegacyEntityRegistrationStripper().strip(source,target);
        assertEquals(1,result.strippedSites());assertTrue(result.blockers().isEmpty());
        ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(result.bytes()).accept(node,0);
        assertFalse(node.methods.stream().flatMap(method->method.instructions.iterator().hasNext()?java.util.stream.Stream.of(method):java.util.stream.Stream.empty())
                .anyMatch(method->{for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals("cpw/mods/fml/common/registry/EntityRegistry")&&call.name.equals("registerModEntity"))return true;return false;}));
        assertFalse(node.methods.stream().anyMatch(method->{for(var instruction:method.instructions)if(instruction instanceof LdcInsnNode ldc&&ldc.cst instanceof Type type&&"foreign/Orb".equals(type.getInternalName()))return true;return false;}));
    }

    @Test void computedTrackingRangeIsLeftUntouched() {
        byte[] source=fixture(true);
        var target=new LegacyEntityRegistrationStripper.Target("preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V","foreign/Orb","orb",17,80,2,true);
        var result=new LegacyEntityRegistrationStripper().strip(source,target);
        assertEquals(0,result.strippedSites());
        assertTrue(result.blockers().contains("no-exact-pure-registerModEntity-callsite"));
        assertArrayEquals(source,result.bytes());
    }

    private static byte[] fixture(boolean computedTracking){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/Orb"));m.visitLdcInsn("orb");m.visitIntInsn(Opcodes.BIPUSH,17);m.visitVarInsn(Opcodes.ALOAD,0);
        if(computedTracking){m.visitIntInsn(Opcodes.BIPUSH,40);m.visitIntInsn(Opcodes.BIPUSH,40);m.visitInsn(Opcodes.IADD);}else m.visitIntInsn(Opcodes.BIPUSH,80);
        m.visitInsn(Opcodes.ICONST_2);m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity","(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
