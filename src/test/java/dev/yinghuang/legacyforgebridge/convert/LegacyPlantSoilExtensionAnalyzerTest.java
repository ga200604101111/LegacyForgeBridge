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

class LegacyPlantSoilExtensionAnalyzerTest {
    @TempDir Path tempDir;

    @Test void exactForgeSoilHooksAreInventoriedAcrossRegisteredSourceLineage() throws Exception {
        Path jar = tempDir.resolve("Soils.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "s/Plain.class", soil("s/Plain", Hook.NONE));
            put(out, "s/Sustain.class", soil("s/Sustain", Hook.SUSTAIN));
            put(out, "s/Fertile.class", soil("s/Fertile", Hook.FERTILE));
            put(out, "s/Both.class", soil("s/Both", Hook.BOTH));
            put(out, "s/Bootstrap.class", bootstrap());
        }
        var analysis = new LegacyPlantSoilExtensionAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        Map<String,LegacyPlantSoilExtensionAnalyzer.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyPlantSoilExtensionAnalyzer.Rule::registryName, Function.identity()));
        assertEquals(4, rules.size());
        assertTrue(rules.get("plain").inheritsForgeDefaultSustain());
        assertTrue(rules.get("plain").inheritsForgeDefaultFertility());
        assertFalse(rules.get("sustain").inheritsForgeDefaultSustain());
        assertTrue(rules.get("sustain").inheritsForgeDefaultFertility());
        assertEquals(1, rules.get("sustain").canSustainPlantHooks().size());
        assertTrue(rules.get("fertile").inheritsForgeDefaultSustain());
        assertFalse(rules.get("fertile").inheritsForgeDefaultFertility());
        assertEquals(1, rules.get("fertile").fertilityHooks().size());
        assertFalse(rules.get("both").inheritsForgeDefaultSustain());
        assertFalse(rules.get("both").inheritsForgeDefaultFertility());
    }

    private enum Hook { NONE, SUSTAIN, FERTILE, BOTH }

    private static byte[] soil(String name, Hook hook) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();
        if (hook == Hook.SUSTAIN || hook == Hook.BOTH) {
            MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "canSustainPlant",
                    "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/common/util/ForgeDirection;Lnet/minecraftforge/common/IPlantable;)Z",
                    null, null);
            m.visitCode(); m.visitInsn(Opcodes.ICONST_1); m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0, 0); m.visitEnd();
        }
        if (hook == Hook.FERTILE || hook == Hook.BOTH) {
            MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "isFertile", "(Lnet/minecraft/world/World;III)Z", null, null);
            m.visitCode(); m.visitInsn(Opcodes.ICONST_1); m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0, 0); m.visitEnd();
        }
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "s/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        register(m, "s/Plain", "plain");
        register(m, "s/Sustain", "sustain");
        register(m, "s/Fertile", "fertile");
        register(m, "s/Both", "both");
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
