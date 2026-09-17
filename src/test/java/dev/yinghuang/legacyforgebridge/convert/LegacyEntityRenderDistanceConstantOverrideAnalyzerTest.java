package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityRenderDistanceConstantOverrideAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesConstantDoubleArgumentBooleanCallbackButRejectsArgumentDependentBody() throws Exception {
        Path jar=tempDir.resolve("render-distance.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){put(out,"foreign/Orb.class",entity());}
        LegacyEntityConstantOverrideAnalyzer analyzer=new LegacyEntityConstantOverrideAnalyzer();
        var constant=analyzer.proveBoolean(jar,"foreign/Orb","isInRangeToRenderDist","(D)Z");
        assertTrue(constant.proven(),constant.reason());assertEquals(Boolean.FALSE,constant.value());
        var dynamic=analyzer.proveBoolean(jar,"foreign/Orb","dynamicRenderDist","(D)Z");
        assertFalse(dynamic.proven());assertNull(dynamic.value());assertEquals("boolean-callback-not-exact-constant-return",dynamic.reason());
    }

    private static byte[] entity(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Orb",null,"java/lang/Object",null);
        MethodVisitor constant=w.visitMethod(Opcodes.ACC_PUBLIC,"isInRangeToRenderDist","(D)Z",null,null);constant.visitCode();constant.visitInsn(Opcodes.ICONST_0);constant.visitInsn(Opcodes.IRETURN);constant.visitMaxs(0,0);constant.visitEnd();
        MethodVisitor dynamic=w.visitMethod(Opcodes.ACC_PUBLIC,"dynamicRenderDist","(D)Z",null,null);dynamic.visitCode();dynamic.visitVarInsn(Opcodes.DLOAD,1);dynamic.visitInsn(Opcodes.DCONST_0);dynamic.visitInsn(Opcodes.DCMPL);org.objectweb.asm.Label falseLabel=new org.objectweb.asm.Label();dynamic.visitJumpInsn(Opcodes.IFLE,falseLabel);dynamic.visitInsn(Opcodes.ICONST_1);dynamic.visitInsn(Opcodes.IRETURN);dynamic.visitLabel(falseLabel);dynamic.visitInsn(Opcodes.ICONST_0);dynamic.visitInsn(Opcodes.IRETURN);dynamic.visitMaxs(0,0);dynamic.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
