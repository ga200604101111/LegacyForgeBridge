package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyItemBlockBindingAnalyzerTest {
    @TempDir Path tempDir;

    @Test void unrelatedSeedFoodAndReedConstructorsPreserveBlockFieldProvenance() throws Exception {
        Path jar = tempDir.resolve("ForeignPlanting.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "other/plant/CropBlock.class", block("other/plant/CropBlock"));
            put(out, "other/plant/SoilBlock.class", block("other/plant/SoilBlock"));
            put(out, "other/plant/ReedBlock.class", block("other/plant/ReedBlock"));
            put(out, "other/plant/SeedItem.class", seed());
            put(out, "other/plant/SeedFoodItem.class", seedFood());
            put(out, "other/plant/ReedItem.class", reed());
            put(out, "other/plant/ExternalSoilSeed.class", externalSoilSeed());
            put(out, "other/plant/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyItemBlockBindingAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(4, analysis.rules().size(), analysis.skipped().toString());
        assertTrue(analysis.skipped().isEmpty(), analysis.skipped().toString());
        Map<String,LegacyItemBlockBindingAnalyzer.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyItemBlockBindingAnalyzer.Rule::registryName, Function.identity()));

        var seed = rules.get("seed");
        assertEquals(LegacyItemBlockBindingAnalyzer.Family.SEEDS, seed.family());
        assertEquals("crop", seed.targetBlock().registryName());
        assertEquals("soil", seed.soilBlock().registryName());
        assertTrue(seed.targetBlock().registered());
        assertTrue(seed.soilBlock().registered());

        var food = rules.get("seed_food");
        assertEquals(LegacyItemBlockBindingAnalyzer.Family.SEED_FOOD, food.family());
        assertEquals(3, food.nutrition());
        assertEquals(0.4F, food.saturationModifier());
        assertEquals("crop", food.targetBlock().registryName());

        var reed = rules.get("reed_item");
        assertEquals(LegacyItemBlockBindingAnalyzer.Family.REED, reed.family());
        assertEquals("reed_block", reed.targetBlock().registryName());
        assertNull(reed.soilBlock());

        var external = rules.get("external_seed");
        assertEquals("crop", external.targetBlock().registryName());
        assertFalse(external.soilBlock().registered());
        assertEquals("net/minecraft/init/Blocks", external.soilBlock().sourceFieldOwner());
        assertEquals("field_150458_ak", external.soilBlock().sourceFieldName());
    }

    private static byte[] block(String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD, 0); c.visitInsn(Opcodes.ACONST_NULL);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>", "(Lnet/minecraft/block/material/Material;)V", false);
        c.visitInsn(Opcodes.RETURN); c.visitMaxs(0, 0); c.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] seed() {
        return boundItem("other/plant/SeedItem", "net/minecraft/item/ItemSeeds",
                "(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V", false, false);
    }
    private static byte[] seedFood() {
        return boundItem("other/plant/SeedFoodItem", "net/minecraft/item/ItemSeedFood",
                "(IFLnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V", true, false);
    }
    private static byte[] reed() {
        return boundItem("other/plant/ReedItem", "net/minecraft/item/ItemReed",
                "(Lnet/minecraft/block/Block;)V", false, true);
    }
    private static byte[] externalSoilSeed() {
        String name = "other/plant/ExternalSoilSeed";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/item/ItemSeeds", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD,0);
        c.visitFieldInsn(Opcodes.GETSTATIC,"other/plant/Bootstrap","CROP","Lnet/minecraft/block/Block;");
        c.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/init/Blocks","field_150458_ak","Lnet/minecraft/block/Block;");
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSeeds","<init>","(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V",false);
        c.visitInsn(Opcodes.RETURN); c.visitMaxs(0,0); c.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] boundItem(String name, String superName, String superDescriptor, boolean food, boolean reed) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, superName, null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD,0);
        if (food) { c.visitInsn(Opcodes.ICONST_3); c.visitLdcInsn(0.4F); }
        c.visitFieldInsn(Opcodes.GETSTATIC,"other/plant/Bootstrap",reed?"REED":"CROP","Lnet/minecraft/block/Block;");
        if (!reed) c.visitFieldInsn(Opcodes.GETSTATIC,"other/plant/Bootstrap","SOIL","Lnet/minecraft/block/Block;");
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,superName,"<init>",superDescriptor,false);
        c.visitInsn(Opcodes.RETURN); c.visitMaxs(0,0); c.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] bootstrap() {
        String owner="other/plant/Bootstrap"; ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"CROP","Lnet/minecraft/block/Block;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"SOIL","Lnet/minecraft/block/Block;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"REED","Lnet/minecraft/block/Block;",null,null).visitEnd();
        MethodVisitor bind=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"bindBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;",null,null);
        bind.visitCode(); bind.visitVarInsn(Opcodes.ALOAD,0); bind.visitVarInsn(Opcodes.ALOAD,1);
        bind.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
        bind.visitVarInsn(Opcodes.ALOAD,0); bind.visitInsn(Opcodes.ARETURN); bind.visitMaxs(0,0); bind.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        bindBlock(m,"other/plant/CropBlock","crop","CROP"); bindBlock(m,"other/plant/SoilBlock","soil","SOIL"); bindBlock(m,"other/plant/ReedBlock","reed_block","REED");
        registerItem(m,"other/plant/SeedItem","seed"); registerItem(m,"other/plant/SeedFoodItem","seed_food"); registerItem(m,"other/plant/ReedItem","reed_item"); registerItem(m,"other/plant/ExternalSoilSeed","external_seed");
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void bindBlock(MethodVisitor m,String type,String id,String field){m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);m.visitLdcInsn(id);m.visitMethodInsn(Opcodes.INVOKESTATIC,"other/plant/Bootstrap","bindBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;",false);m.visitFieldInsn(Opcodes.PUTSTATIC,"other/plant/Bootstrap",field,"Lnet/minecraft/block/Block;");}
    private static void registerItem(MethodVisitor m,String type,String id){m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);m.visitLdcInsn(id);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
