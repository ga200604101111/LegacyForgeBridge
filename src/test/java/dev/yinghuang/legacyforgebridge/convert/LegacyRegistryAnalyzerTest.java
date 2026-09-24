package dev.yinghuang.legacyforgebridge.convert;

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
            put(out,"other/sample/BeamBlock.class",floatConstructorBlock());
            put(out,"other/sample/Bootstrap.class",bootstrap());
        }

        LegacyRegistryAnalyzer analyzer=new LegacyRegistryAnalyzer();
        LegacyRegistryAnalyzer.Analysis analysis=analyzer.analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n",analysis.diagnostics()));
        assertEquals(3,analysis.registrations().size());
        var item=analysis.items().getFirst();
        assertEquals("foreign_blade",item.registryName());
        assertEquals("other/sample/CrimsonBlade",item.implementationClass());
        assertEquals("sword",analyzer.classifyItem(item.implementationClass()));
        var block=analysis.blocks().stream().filter(value->value.registryName().equals("stone_lamp")).findFirst().orElseThrow();
        assertEquals("stone_lamp",block.registryName());
        assertEquals("other/sample/StoneLamp",block.implementationClass());
        var beam=analysis.blocks().stream().filter(value->value.registryName().equals("beam")).findFirst().orElseThrow();
        assertEquals("(FFFF)V",beam.constructorDescriptor());
        assertEquals(0.15F,((Number)beam.constructorArguments().get(0).value()).floatValue(),1e-6F);
        assertEquals(0.85F,((Number)beam.constructorArguments().get(1).value()).floatValue(),1e-6F);
        assertEquals(0.15F,((Number)beam.constructorArguments().get(2).value()).floatValue(),1e-6F);
        assertEquals(0.85F,((Number)beam.constructorArguments().get(3).value()).floatValue(),1e-6F);
        // This fixture does not store helper return values into static fields, so no field binding is expected.
        assertTrue(analysis.fieldBindings().isEmpty());
    }

    @Test
    void iterableDerivedNameRegistrationExpandsConcreteSelfEnrolledItems() throws Exception {
        Path jar=tempDir.resolve("IterableContent.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"other/iterable/BaseItem.class",iterableBaseItem());
            put(out,"other/iterable/ChildItem.class",iterableChildItem());
            put(out,"other/iterable/Content.class",iterableContent());
            put(out,"other/iterable/Bootstrap.class",iterableBootstrap());
        }

        var analysis=new LegacyRegistryAnalyzer().analyze(jar);
        assertEquals(2,analysis.items().size(),String.join("\n",analysis.diagnostics()));
        assertEquals(java.util.Set.of("alpha","beta"),
                analysis.items().stream().map(LegacyRegistryAnalyzer.Registration::registryName).collect(java.util.stream.Collectors.toSet()));
        assertTrue(analysis.items().stream().anyMatch(item->item.registryName().equals("alpha")
                &&item.implementationClass().equals("other/iterable/BaseItem")));
        assertTrue(analysis.items().stream().anyMatch(item->item.registryName().equals("beta")
                &&item.implementationClass().equals("other/iterable/ChildItem")));
        assertEquals(2,analysis.fieldBindings().stream().filter(binding->binding.owner().equals("other/iterable/Content")).count());
        assertTrue(analysis.diagnostics().stream().anyMatch(value->value.contains("iterable derived-name registration")));
    }

    @Test
    void registrationNameDerivedFromStaticItemsUnlocalizedNameIsRecovered() throws Exception {
        Path jar=tempDir.resolve("DerivedNameContent.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"other/derived/Blade.class",namedSwordSubclass());
            put(out,"other/derived/Content.class",derivedContent());
            put(out,"other/derived/Bootstrap.class",derivedBootstrap());
        }

        var analysis=new LegacyRegistryAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(),String.join("\n",analysis.diagnostics()));
        assertEquals(1,analysis.items().size());
        var item=analysis.items().getFirst();
        assertEquals("derived_blade",item.registryName());
        assertEquals("other/derived/Blade",item.implementationClass());
        assertEquals("(Ljava/lang/String;)V",item.constructorDescriptor());
        assertTrue(analysis.fieldBindings().stream().anyMatch(binding->
                binding.owner().equals("other/derived/Content")&&binding.name().equals("BLADE")
                        &&binding.registryName().equals("derived_blade")&&binding.kind()==LegacyRegistryAnalyzer.Kind.ITEM));
    }

    private static byte[] iterableBaseItem(){
        String name="other/iterable/BaseItem";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/item/Item",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"setUnlocalizedName",
                "(Ljava/lang/String;)Lnet/minecraft/item/Item;",false);m.visitInsn(Opcodes.POP);
        m.visitFieldInsn(Opcodes.GETSTATIC,"other/iterable/Content","ALL","Ljava/util/List;");
        m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","add","(Ljava/lang/Object;)Z",true);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] iterableChildItem(){
        String name="other/iterable/ChildItem";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"other/iterable/BaseItem",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/iterable/BaseItem","<init>","(Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] iterableContent(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/iterable/Content",null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"ALL","Ljava/util/List;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"FIRST","Lnet/minecraft/item/Item;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"SECOND","Lnet/minecraft/item/Item;",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,"java/util/ArrayList");m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/ArrayList","<init>","()V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/iterable/Content","ALL","Ljava/util/List;");
        m.visitTypeInsn(Opcodes.NEW,"other/iterable/BaseItem");m.visitInsn(Opcodes.DUP);m.visitLdcInsn("alpha");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/iterable/BaseItem","<init>","(Ljava/lang/String;)V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/iterable/Content","FIRST","Lnet/minecraft/item/Item;");
        m.visitTypeInsn(Opcodes.NEW,"other/iterable/ChildItem");m.visitInsn(Opcodes.DUP);m.visitLdcInsn("beta");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/iterable/ChildItem","<init>","(Ljava/lang/String;)V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/iterable/Content","SECOND","Lnet/minecraft/item/Item;");
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] iterableBootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/iterable/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor h=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"registerAll","()V",null,null);h.visitCode();
        h.visitFieldInsn(Opcodes.GETSTATIC,"other/iterable/Content","ALL","Ljava/util/List;");
        h.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","iterator","()Ljava/util/Iterator;",true);
        h.visitVarInsn(Opcodes.ASTORE,0);
        org.objectweb.asm.Label loop=new org.objectweb.asm.Label(),end=new org.objectweb.asm.Label();
        h.visitLabel(loop);h.visitVarInsn(Opcodes.ALOAD,0);
        h.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Iterator","hasNext","()Z",true);h.visitJumpInsn(Opcodes.IFEQ,end);
        h.visitVarInsn(Opcodes.ALOAD,0);h.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Iterator","next","()Ljava/lang/Object;",true);
        h.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/item/Item");h.visitVarInsn(Opcodes.ASTORE,1);
        h.visitVarInsn(Opcodes.ALOAD,1);h.visitVarInsn(Opcodes.ALOAD,1);
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/Item","getUnlocalizedName","()Ljava/lang/String;",false);
        h.visitInsn(Opcodes.ICONST_5);
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","substring","(I)Ljava/lang/String;",false);
        h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        h.visitJumpInsn(Opcodes.GOTO,loop);h.visitLabel(end);h.visitInsn(Opcodes.RETURN);h.visitMaxs(0,0);h.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor av=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);av.visitEnd();m.visitCode();
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"other/iterable/Bootstrap","registerAll","()V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] derivedContent(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/derived/Content",null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"BLADE","Lnet/minecraft/item/Item;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);c.visitCode();
        c.visitTypeInsn(Opcodes.NEW,"other/derived/Blade");c.visitInsn(Opcodes.DUP);c.visitLdcInsn("derived_blade");
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/derived/Blade","<init>","(Ljava/lang/String;)V",false);
        c.visitFieldInsn(Opcodes.PUTSTATIC,"other/derived/Content","BLADE","Lnet/minecraft/item/Item;");
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] derivedBootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/derived/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor h=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"register",
                "(Lnet/minecraft/item/Item;)V",null,null);h.visitCode();
        h.visitVarInsn(Opcodes.ALOAD,0);h.visitVarInsn(Opcodes.ALOAD,0);
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/Item","getUnlocalizedName",
                "()Ljava/lang/String;",false);
        h.visitInsn(Opcodes.ICONST_5);
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","substring",
                "(I)Ljava/lang/String;",false);
        h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        h.visitInsn(Opcodes.RETURN);h.visitMaxs(0,1);h.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor av=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);av.visitEnd();
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC,"other/derived/Content","BLADE","Lnet/minecraft/item/Item;");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"other/derived/Bootstrap","register",
                "(Lnet/minecraft/item/Item;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] namedSwordSubclass(){
        String name="other/derived/Blade";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/item/ItemSword",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitInsn(Opcodes.ACONST_NULL);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSword","<init>",
                "(Lnet/minecraft/item/Item$ToolMaterial;)V",false);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"setUnlocalizedName",
                "(Ljava/lang/String;)Lnet/minecraft/item/Item;",false);
        m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] simpleSubclass(String name,String parent){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,parent,null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(1,1);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] floatConstructorBlock(){
        String name="other/sample/BeamBlock";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/block/Block",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(FFFF)V",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/Block","<init>","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
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
        m.visitTypeInsn(Opcodes.NEW,"other/sample/StoneLamp");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/sample/StoneLamp","<init>","()V",false);m.visitLdcInsn("stone_lamp");m.visitMethodInsn(Opcodes.INVOKESTATIC,"other/sample/Bootstrap","block","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
        m.visitLdcInsn(0.85F);m.visitVarInsn(Opcodes.FSTORE,2);
        m.visitInsn(Opcodes.FCONST_1);m.visitVarInsn(Opcodes.FLOAD,2);m.visitInsn(Opcodes.FSUB);m.visitVarInsn(Opcodes.FSTORE,3);
        m.visitLdcInsn(0.85F);m.visitVarInsn(Opcodes.FSTORE,4);
        m.visitInsn(Opcodes.FCONST_1);m.visitVarInsn(Opcodes.FLOAD,4);m.visitInsn(Opcodes.FSUB);m.visitVarInsn(Opcodes.FSTORE,5);
        m.visitTypeInsn(Opcodes.NEW,"other/sample/BeamBlock");m.visitInsn(Opcodes.DUP);
        m.visitVarInsn(Opcodes.FLOAD,3);m.visitVarInsn(Opcodes.FLOAD,2);m.visitVarInsn(Opcodes.FLOAD,5);m.visitVarInsn(Opcodes.FLOAD,4);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/sample/BeamBlock","<init>","(FFFF)V",false);m.visitLdcInsn("beam");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"other/sample/Bootstrap","block","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,6);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
