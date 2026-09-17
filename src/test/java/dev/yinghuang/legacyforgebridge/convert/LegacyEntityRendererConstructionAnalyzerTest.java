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

class LegacyEntityRendererConstructionAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesOnlyTrivialSourceConstructorChainEndingAtVanillaRender() throws Exception {
        Path jar=tempDir.resolve("renderers.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"third/client/BaseEmpty.class",renderer("third/client/BaseEmpty","net/minecraft/client/renderer/entity/Render",false));
            put(out,"third/client/RenderEmpty.class",renderer("third/client/RenderEmpty","third/client/BaseEmpty",false));
            put(out,"third/client/RenderSideEffect.class",renderer("third/client/RenderSideEffect","net/minecraft/client/renderer/entity/Render",true));
        }
        LegacyEntityRendererConstructionAnalyzer analyzer=new LegacyEntityRendererConstructionAnalyzer();
        var proven=analyzer.prove(jar,"third/client/RenderEmpty");
        assertTrue(proven.proven(),proven.reason());
        assertEquals(java.util.List.of("third/client/RenderEmpty","third/client/BaseEmpty"),proven.sourceConstructorChain());
        assertEquals("net/minecraft/client/renderer/entity/Render",proven.terminalBaseClass());
        var blocked=analyzer.prove(jar,"third/client/RenderSideEffect");
        assertFalse(blocked.proven());assertEquals("noarg-constructor-has-side-effects",blocked.reason());
    }

    private static byte[] renderer(String name,String superName,boolean sideEffect){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,superName,null);
        MethodVisitor ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,superName,"<init>","()V",false);
        if(sideEffect){ctor.visitInsn(Opcodes.ICONST_1);ctor.visitFieldInsn(Opcodes.PUTSTATIC,name,"TOUCHED","Z");}
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        if(sideEffect)w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"TOUCHED","Z",null,null).visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
