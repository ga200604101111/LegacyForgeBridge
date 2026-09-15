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

class LegacyPlantBlockAnalyzerTest {
    @TempDir Path tempDir;

    @Test void unrelatedPlantFamiliesAndSourceOverridesAreClassifiedWithoutEnablingRuntime() throws Exception {
        Path jar = tempDir.resolve("ForeignPlants.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "other/plant/PureCrop.class", subclass("other/plant/PureCrop", "net/minecraft/block/BlockCrops", false));
            put(out, "other/plant/CustomCrop.class", subclass("other/plant/CustomCrop", "net/minecraft/block/BlockCrops", true));
            put(out, "other/plant/PureReed.class", subclass("other/plant/PureReed", "net/minecraft/block/BlockReed", false));
            put(out, "other/plant/PureBush.class", subclass("other/plant/PureBush", "net/minecraft/block/BlockBush", false));
            put(out, "other/plant/Bootstrap.class", bootstrap());
        }
        var analysis = new LegacyPlantBlockAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(4, analysis.rules().size());
        Map<String,LegacyPlantBlockAnalyzer.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyPlantBlockAnalyzer.Rule::registryName, Function.identity()));
        assertEquals(LegacyPlantBlockAnalyzer.Family.CROPS, rules.get("pure_crop").family());
        assertTrue(rules.get("pure_crop").inheritedVanillaLifecycleOnly());
        assertEquals(LegacyPlantBlockAnalyzer.Family.CROPS, rules.get("custom_crop").family());
        assertFalse(rules.get("custom_crop").inheritedVanillaLifecycleOnly());
        assertEquals(1, rules.get("custom_crop").sourceOverrides().size());
        assertTrue(rules.get("custom_crop").sourceOverrides().getFirst().contains("updateTick"));
        assertEquals(LegacyPlantBlockAnalyzer.Family.REED, rules.get("pure_reed").family());
        assertEquals(LegacyPlantBlockAnalyzer.Family.BUSH, rules.get("pure_bush").family());
    }

    private static byte[] subclass(String name, String superName, boolean custom) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, superName, null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();
        if (custom) {
            MethodVisitor tick = w.visitMethod(Opcodes.ACC_PUBLIC, "updateTick",
                    "(Lnet/minecraft/world/World;IIILjava/util/Random;)V", null, null);
            tick.visitCode(); tick.visitInsn(Opcodes.RETURN); tick.visitMaxs(0, 0); tick.visitEnd();
        }
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "other/plant/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        register(m, "other/plant/PureCrop", "pure_crop");
        register(m, "other/plant/CustomCrop", "custom_crop");
        register(m, "other/plant/PureReed", "pure_reed");
        register(m, "other/plant/PureBush", "pure_bush");
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static void register(MethodVisitor m, String type, String id) {
        m.visitTypeInsn(Opcodes.NEW, type); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false);
        m.visitLdcInsn(id);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
