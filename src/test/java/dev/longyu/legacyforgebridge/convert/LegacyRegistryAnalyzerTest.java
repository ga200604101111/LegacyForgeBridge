package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRegistryAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void helperWrappedRegistrationsAreRecoveredAcrossAnUnrelatedNamespace() throws Exception {
        Path jar=tempDir.resolve("ForeignContent.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"other/sample/CrimsonBlade.class",simpleSubclass("other/sample/CrimsonBlade","net/minecraft/item/ItemSword"));
            put(out,"other/sample/StoneLamp.class",simpleSubclass("other/sample/StoneLamp","net/minecraft/block/Block"));
            put(out,"other/sample/Bootstrap.class",bootstrap());
        }

        LegacyRegistryAnalyzer analyzer=new LegacyRegistryAnalyzer();
        LegacyRegistryAnalyzer.Analysis analysis=analyzer.analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n",analysis.diagnostics()));
        assertEquals(2,analysis.registrations().size());
        var item=analysis.items().getFirst();
        assertEquals("foreign_blade",item.registryName());
        assertEquals("other/sample/CrimsonBlade",item.implementationClass());
        assertEquals("sword",analyzer.classifyItem(item.implementationClass()));
        var block=analysis.blocks().getFirst();
        assertEquals("stone_lamp",block.registryName());
        assertEquals("other/sample/StoneLamp",block.implementationClass());
        // This fixture does not store helper return values into static fields, so no field binding is expected.
        assertTrue(analysis.fieldBindings().isEmpty());
    }

    private static byte[] simpleSubclass(String name,String parent){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,parent,null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(1,1);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/sample/Bootstrap",null,"java/lang/Object",null);
        // helper(Item,String)
        MethodVisitor h=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"item","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",null,null);h.visitCode();h.visitVarInsn(Opcodes.ALOAD,0);h.visitVarInsn(Opcodes.ALOAD,1);h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);h.visitInsn(Opcodes.RETURN);h.visitMaxs(2,2);h.visitEnd();
        // helper(Block,String)
        h=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"block","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",null,null);h.visitCode();h.visitVarInsn(Opcodes.ALOAD,0);h.visitVarInsn(Opcodes.ALOAD,1);h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);h.visitInsn(Opcodes.RETURN);h.visitMaxs(2,2);h.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor av=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);av.visitEnd();
        m.visitCode();m.visitTypeInsn(Opcodes.NEW,"other/sample/CrimsonBlade");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/sample/CrimsonBlade","<init>","()V",false);m.visitLdcInsn("foreign_blade");m.visitMethodInsn(Opcodes.INVOKESTATIC,"other/sample/Bootstrap","item","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitTypeInsn(Opcodes.NEW,"other/sample/StoneLamp");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/sample/StoneLamp","<init>","()V",false);m.visitLdcInsn("stone_lamp");m.visitMethodInsn(Opcodes.INVOKESTATIC,"other/sample/Bootstrap","block","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(3,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
