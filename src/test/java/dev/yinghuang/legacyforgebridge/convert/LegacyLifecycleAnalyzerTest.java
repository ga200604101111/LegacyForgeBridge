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

    @Test void reachableStaticEntityIdsAreFoldedButDynamicAssignmentsStayFailClosed() throws Exception {
        Path jar=tempDir.resolve("StaticEntityIds.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"alt/staticid/Bootstrap.class",staticEntityIds());
        }
        var analysis=new LegacyLifecycleAnalyzer().analyze(jar);
        var entities=analysis.of(LegacyLifecycleAnalyzer.Kind.ENTITY);
        assertEquals(2,entities.size());

        var proven=entities.stream()
                .filter(value->value.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text&&text.value().equals("fixed"))
                .findFirst().orElseThrow();
        assertEquals(177,assertInstanceOf(LegacyLifecycleAnalyzer.NumberValue.class,proven.arguments().get(2)).value().intValue());

        var dynamic=entities.stream()
                .filter(value->value.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text&&text.value().equals("dynamic"))
                .findFirst().orElseThrow();
        var unresolved=assertInstanceOf(LegacyLifecycleAnalyzer.FieldValue.class,dynamic.arguments().get(2));
        assertEquals("DYNAMIC_ID",unresolved.name());
    }

    private static byte[] staticEntityIds(){
        String owner="alt/staticid/Bootstrap";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"FIXED_ID","I",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"DYNAMIC_ID","I",null,null).visitEnd();

        MethodVisitor assign=w.visitMethod(Opcodes.ACC_PRIVATE,"assign","(Lnet/minecraftforge/common/config/Configuration;)V",null,null);
        assign.visitCode();
        assign.visitIntInsn(Opcodes.SIPUSH,177);assign.visitFieldInsn(Opcodes.PUTSTATIC,owner,"FIXED_ID","I");
        assign.visitVarInsn(Opcodes.ALOAD,1);assign.visitLdcInsn("entity");assign.visitLdcInsn("dynamic");assign.visitIntInsn(Opcodes.SIPUSH,188);
        assign.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraftforge/common/config/Configuration","get",
                "(Ljava/lang/String;Ljava/lang/String;I)Lnet/minecraftforge/common/config/Property;",false);
        assign.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraftforge/common/config/Property","getInt","()I",false);
        assign.visitFieldInsn(Opcodes.PUTSTATIC,owner,"DYNAMIC_ID","I");
        assign.visitInsn(Opcodes.RETURN);assign.visitMaxs(4,2);assign.visitEnd();

        MethodVisitor helper=w.visitMethod(Opcodes.ACC_PRIVATE,"entity","(Ljava/lang/Class;Ljava/lang/String;I)V",null,null);
        helper.visitCode();helper.visitVarInsn(Opcodes.ALOAD,1);helper.visitVarInsn(Opcodes.ALOAD,2);helper.visitVarInsn(Opcodes.ILOAD,3);
        helper.visitVarInsn(Opcodes.ALOAD,0);helper.visitIntInsn(Opcodes.BIPUSH,80);helper.visitInsn(Opcodes.ICONST_3);helper.visitInsn(Opcodes.ICONST_1);
        helper.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);
        helper.visitInsn(Opcodes.RETURN);helper.visitMaxs(7,4);helper.visitEnd();

        MethodVisitor pre=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        pre.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true).visitEnd();pre.visitCode();
        pre.visitVarInsn(Opcodes.ALOAD,0);pre.visitInsn(Opcodes.ACONST_NULL);
        pre.visitMethodInsn(Opcodes.INVOKESPECIAL,owner,"assign","(Lnet/minecraftforge/common/config/Configuration;)V",false);
        pre.visitVarInsn(Opcodes.ALOAD,0);pre.visitLdcInsn(Type.getObjectType("alt/staticid/FixedEntity"));pre.visitLdcInsn("fixed");
        pre.visitFieldInsn(Opcodes.GETSTATIC,owner,"FIXED_ID","I");
        pre.visitMethodInsn(Opcodes.INVOKESPECIAL,owner,"entity","(Ljava/lang/Class;Ljava/lang/String;I)V",false);
        pre.visitVarInsn(Opcodes.ALOAD,0);pre.visitLdcInsn(Type.getObjectType("alt/staticid/DynamicEntity"));pre.visitLdcInsn("dynamic");
        pre.visitFieldInsn(Opcodes.GETSTATIC,owner,"DYNAMIC_ID","I");
        pre.visitMethodInsn(Opcodes.INVOKESPECIAL,owner,"entity","(Ljava/lang/Class;Ljava/lang/String;I)V",false);
        pre.visitInsn(Opcodes.RETURN);pre.visitMaxs(4,2);pre.visitEnd();

        w.visitEnd();return w.toByteArray();
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
