package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyLifecycleAnalyzerTest {
    @TempDir Path tempDir;

    @Test void helperWrappedEntityAndDirectTileRegistrationsAreRecoveredAcrossAnotherNamespace() throws Exception {
        Path jar=tempDir.resolve("ForeignLifecycle.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){put(out,"alt/mod/Bootstrap.class",bootstrap());}
        var analysis=new LegacyLifecycleAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(),String.join("\n",analysis.diagnostics()));
        var entities=analysis.of(LegacyLifecycleAnalyzer.Kind.ENTITY);assertEquals(1,entities.size());
        assertInstanceOf(LegacyLifecycleAnalyzer.TypeValue.class,entities.getFirst().arguments().getFirst());
        assertEquals("alt/mod/SparkEntity",((LegacyLifecycleAnalyzer.TypeValue)entities.getFirst().arguments().getFirst()).internalName());
        assertEquals("spark",((LegacyLifecycleAnalyzer.TextValue)entities.getFirst().arguments().get(1)).value());
        assertEquals(37,((LegacyLifecycleAnalyzer.NumberValue)entities.getFirst().arguments().get(2)).value().intValue());
        assertEquals(96,((LegacyLifecycleAnalyzer.NumberValue)entities.getFirst().arguments().get(4)).value().intValue());
        var tiles=analysis.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY);assertEquals(1,tiles.size());
        assertEquals("alt/mod/LampTile",((LegacyLifecycleAnalyzer.TypeValue)tiles.getFirst().arguments().getFirst()).internalName());
        assertEquals("lamp_tile",((LegacyLifecycleAnalyzer.TextValue)tiles.getFirst().arguments().get(1)).value());
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"alt/mod/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor h=w.visitMethod(Opcodes.ACC_PRIVATE,"entity","(Ljava/lang/Class;Ljava/lang/String;IIIZ)V",null,null);h.visitCode();
        h.visitVarInsn(Opcodes.ALOAD,1);h.visitVarInsn(Opcodes.ALOAD,2);h.visitVarInsn(Opcodes.ILOAD,3);h.visitVarInsn(Opcodes.ALOAD,0);h.visitVarInsn(Opcodes.ILOAD,4);h.visitVarInsn(Opcodes.ILOAD,5);h.visitVarInsn(Opcodes.ILOAD,6);
        h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity","(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);h.visitInsn(Opcodes.RETURN);h.visitMaxs(7,7);h.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);AnnotationVisitor av=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);av.visitEnd();m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitLdcInsn(Type.getObjectType("alt/mod/SparkEntity"));m.visitLdcInsn("spark");m.visitIntInsn(Opcodes.BIPUSH,37);m.visitIntInsn(Opcodes.BIPUSH,96);m.visitIntInsn(Opcodes.BIPUSH,3);m.visitInsn(Opcodes.ICONST_1);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"alt/mod/Bootstrap","entity","(Ljava/lang/Class;Ljava/lang/String;IIIZ)V",false);
        m.visitLdcInsn(Type.getObjectType("alt/mod/LampTile"));m.visitLdcInsn("lamp_tile");m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerTileEntity","(Ljava/lang/Class;Ljava/lang/String;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(7,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
