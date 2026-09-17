package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityRendererRegistrationStripperTest {
    @Test void removesExactCanonicalRendererRegistrationExpression() {
        byte[] source=fixture(false);
        var target=new LegacyEntityRendererRegistrationStripper.Target("register","()V","third/entity/Orb","third/client/RenderEmpty");
        var result=new LegacyEntityRendererRegistrationStripper().strip(source,target);
        assertEquals(1,result.strippedSites());assertTrue(result.blockers().isEmpty());
        ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(result.bytes()).accept(node,0);
        assertFalse(node.methods.stream().anyMatch(method->{for(var instruction:method.instructions){
            if(instruction instanceof MethodInsnNode call&&call.owner.equals("cpw/mods/fml/client/registry/RenderingRegistry")&&call.name.equals("registerEntityRenderingHandler"))return true;
            if(instruction instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&type.desc.equals("third/client/RenderEmpty"))return true;
        }return false;}));
    }

    @Test void constructorArgumentsOrExtraExpressionOpcodesAreNotStripped() {
        byte[] source=fixture(true);
        var target=new LegacyEntityRendererRegistrationStripper.Target("register","()V","third/entity/Orb","third/client/RenderEmpty");
        var result=new LegacyEntityRendererRegistrationStripper().strip(source,target);
        assertEquals(0,result.strippedSites());assertTrue(result.blockers().contains("no-exact-pure-renderer-registration-callsite"));assertArrayEquals(source,result.bytes());
    }

    private static byte[] fixture(boolean extraOpcode){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"third/client/ClientRegistrar",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","()V",null,null);m.visitCode();
        m.visitLdcInsn(Type.getObjectType("third/entity/Orb"));
        if(extraOpcode){m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.POP);}
        m.visitTypeInsn(Opcodes.NEW,"third/client/RenderEmpty");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"third/client/RenderEmpty","<init>","()V",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry","registerEntityRenderingHandler","(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
