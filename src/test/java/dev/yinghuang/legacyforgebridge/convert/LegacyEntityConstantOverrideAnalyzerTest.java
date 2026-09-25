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

class LegacyEntityConstantOverrideAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesOnlyExactBooleanConstantReturnBodies() throws Exception {
        Path jar=tempDir.resolve("entity.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){put(out,"foreign/Orb.class",entity());}
        LegacyEntityConstantOverrideAnalyzer analyzer=new LegacyEntityConstantOverrideAnalyzer();
        var falseProof=analyzer.proveBoolean(jar,"foreign/Orb","canBePushed","()Z");
        assertTrue(falseProof.proven());assertEquals(Boolean.FALSE,falseProof.value());assertEquals("exact-iconst-boolean-return",falseProof.reason());
        var trueProof=analyzer.proveBoolean(jar,"foreign/Orb","alwaysPush","()Z");
        assertTrue(trueProof.proven());assertEquals(Boolean.TRUE,trueProof.value());
        var dynamic=analyzer.proveBoolean(jar,"foreign/Orb","dynamicPush","()Z");
        assertFalse(dynamic.proven());assertNull(dynamic.value());assertEquals("boolean-callback-not-exact-constant-return",dynamic.reason());
    }

    private static byte[] entity(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/Orb",null,"java/lang/Object",null);
        booleanMethod(w,"canBePushed",false);booleanMethod(w,"alwaysPush",true);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"PUSH","Z",null,null).visitEnd();
        MethodVisitor dynamic=w.visitMethod(Opcodes.ACC_PUBLIC,"dynamicPush","()Z",null,null);dynamic.visitCode();dynamic.visitFieldInsn(Opcodes.GETSTATIC,"foreign/Orb","PUSH","Z");dynamic.visitInsn(Opcodes.IRETURN);dynamic.visitMaxs(0,0);dynamic.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void booleanMethod(ClassWriter w,String name,boolean value){MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,name,"()Z",null,null);m.visitCode();m.visitInsn(value?Opcodes.ICONST_1:Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
