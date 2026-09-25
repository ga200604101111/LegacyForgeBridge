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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPlantLifecycleAnalyzerTest {
    @TempDir Path tempDir;

    @Test void lifecycleGatesSeparatePresentationPlantableSurvivalGrowthBonemealAndDrops() throws Exception {
        Path jar = tempDir.resolve("PlantLifecycle.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "p/PureCrop.class", block("p/PureCrop", "net/minecraft/block/BlockCrops", Hook.NONE));
            put(out, "p/TextureCrop.class", block("p/TextureCrop", "net/minecraft/block/BlockCrops", Hook.PRESENTATION));
            put(out, "p/PlantTypeCrop.class", block("p/PlantTypeCrop", "net/minecraft/block/BlockCrops", Hook.PLANTABLE));
            put(out, "p/SoilCrop.class", block("p/SoilCrop", "net/minecraft/block/BlockCrops", Hook.SURVIVAL));
            put(out, "p/GrowthCrop.class", block("p/GrowthCrop", "net/minecraft/block/BlockCrops", Hook.GROWTH));
            put(out, "p/DropCrop.class", block("p/DropCrop", "net/minecraft/block/BlockCrops", Hook.DROPS));
            put(out, "p/BonemealCrop.class", block("p/BonemealCrop", "net/minecraft/block/BlockCrops", Hook.BONEMEAL));
            put(out, "p/TickMutatedCrop.class", block("p/TickMutatedCrop", "net/minecraft/block/BlockCrops", Hook.TICK_MUTATION));
            put(out, "p/PureReed.class", block("p/PureReed", "net/minecraft/block/BlockReed", Hook.NONE));
            put(out, "p/PureBush.class", block("p/PureBush", "net/minecraft/block/BlockBush", Hook.NONE));
            put(out, "p/Bootstrap.class", bootstrap());
        }
        var analysis = new LegacyPlantLifecycleAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        Map<String,LegacyPlantLifecycleAnalyzer.Proof> proofs = analysis.proofs().stream()
                .collect(Collectors.toMap(LegacyPlantLifecycleAnalyzer.Proof::registryName, Function.identity()));
        assertEquals(10, proofs.size());

        var pure = proofs.get("pure_crop");
        assertTrue(pure.plantableHooks().isEmpty());
        assertTrue(pure.survivalInheritedVanilla());
        assertTrue(pure.growthInheritedVanilla());
        assertTrue(pure.bonemealInheritedVanilla());
        assertTrue(pure.dropsInheritedVanilla());
        assertEquals(LegacyPlantLifecycleAnalyzer.AgeModel.LEGACY_META_0_7, pure.ageModel());
        assertEquals(LegacyPlantLifecycleAnalyzer.SurvivalModel.FORGE_CROPS_PLAINS, pure.survivalModel());

        var texture = proofs.get("texture_crop");
        assertTrue(texture.survivalInheritedVanilla());
        assertTrue(texture.growthInheritedVanilla());
        assertEquals(1, texture.presentationHooks().size());

        var plantType = proofs.get("plant_type_crop");
        assertEquals(1, plantType.plantableHooks().size());
        assertTrue(plantType.plantableHooks().getFirst().contains("getPlantType"));
        assertFalse(plantType.survivalInheritedVanilla());
        assertFalse(plantType.growthInheritedVanilla());
        assertEquals(LegacyPlantLifecycleAnalyzer.AgeModel.UNKNOWN, plantType.ageModel());
        assertEquals(LegacyPlantLifecycleAnalyzer.SurvivalModel.CUSTOM_SOURCE, plantType.survivalModel());

        assertFalse(proofs.get("soil_crop").survivalInheritedVanilla());
        assertTrue(proofs.get("soil_crop").growthInheritedVanilla());
        assertFalse(proofs.get("growth_crop").growthInheritedVanilla());
        assertFalse(proofs.get("growth_crop").survivalInheritedVanilla());
        assertEquals(LegacyPlantLifecycleAnalyzer.AgeModel.UNKNOWN, proofs.get("growth_crop").ageModel());
        assertFalse(proofs.get("drop_crop").dropsInheritedVanilla());
        assertFalse(proofs.get("bonemeal_crop").bonemealInheritedVanilla());
        assertEquals(LegacyPlantLifecycleAnalyzer.AgeModel.UNKNOWN, proofs.get("bonemeal_crop").ageModel());
        assertFalse(proofs.get("tick_mutated_crop").growthInheritedVanilla());
        assertFalse(proofs.get("tick_mutated_crop").survivalInheritedVanilla());
        assertEquals(1, proofs.get("tick_mutated_crop").constructorLifecycleMutations().size());

        assertEquals(LegacyPlantLifecycleAnalyzer.AgeModel.LEGACY_META_TIMER_0_15, proofs.get("pure_reed").ageModel());
        assertEquals(LegacyPlantLifecycleAnalyzer.SurvivalModel.FORGE_REED_BEACH, proofs.get("pure_reed").survivalModel());
        assertEquals(LegacyPlantLifecycleAnalyzer.AgeModel.NONE, proofs.get("pure_bush").ageModel());
        assertEquals(LegacyPlantLifecycleAnalyzer.SurvivalModel.FORGE_BUSH_PLAINS, proofs.get("pure_bush").survivalModel());
    }

    private enum Hook { NONE, PRESENTATION, PLANTABLE, SURVIVAL, GROWTH, DROPS, BONEMEAL, TICK_MUTATION }

    private static byte[] block(String name, String superName, Hook hook) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, superName, null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        if (hook == Hook.TICK_MUTATION) {
            init.visitVarInsn(Opcodes.ALOAD,0); init.visitInsn(Opcodes.ICONST_0);
            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "setTickRandomly", "(Z)Lnet/minecraft/block/Block;", false);
            init.visitInsn(Opcodes.POP);
        }
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0,0); init.visitEnd();
        switch (hook) {
            case PRESENTATION -> objectReturn(w, "getIcon", "(II)Lnet/minecraft/util/IIcon;");
            case PLANTABLE -> objectReturn(w, "getPlantType", "(Lnet/minecraft/world/IBlockAccess;III)Lnet/minecraftforge/common/EnumPlantType;");
            case SURVIVAL -> boolReturn(w, "canPlaceBlockOn", "(Lnet/minecraft/block/Block;)Z");
            case GROWTH -> voidReturn(w, "updateTick", "(Lnet/minecraft/world/World;IIILjava/util/Random;)V");
            case DROPS -> objectReturn(w, "func_149866_i", "()Lnet/minecraft/item/Item;");
            case BONEMEAL -> voidReturn(w, "func_149853_b", "(Lnet/minecraft/world/World;Ljava/util/Random;III)V");
            default -> { }
        }
        w.visitEnd(); return w.toByteArray();
    }

    private static void objectReturn(ClassWriter w, String name, String desc) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        m.visitCode(); m.visitInsn(Opcodes.ACONST_NULL); m.visitInsn(Opcodes.ARETURN); m.visitMaxs(0,0); m.visitEnd();
    }
    private static void boolReturn(ClassWriter w, String name, String desc) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PROTECTED, name, desc, null, null);
        m.visitCode(); m.visitInsn(Opcodes.ICONST_1); m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0,0); m.visitEnd();
    }
    private static void voidReturn(ClassWriter w, String name, String desc) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        m.visitCode(); m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "p/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        register(m,"p/PureCrop","pure_crop"); register(m,"p/TextureCrop","texture_crop");
        register(m,"p/PlantTypeCrop","plant_type_crop"); register(m,"p/SoilCrop","soil_crop");
        register(m,"p/GrowthCrop","growth_crop"); register(m,"p/DropCrop","drop_crop");
        register(m,"p/BonemealCrop","bonemeal_crop"); register(m,"p/TickMutatedCrop","tick_mutated_crop");
        register(m,"p/PureReed","pure_reed"); register(m,"p/PureBush","pure_bush");
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }
    private static void register(MethodVisitor m,String type,String id){
        m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);m.visitLdcInsn(id);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
