package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockHarvestEligibilityAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void separatesMaterialFastPathSafetyFromTheGeneralToolRoute() throws Exception {
        Path jar = tempDir.resolve("HarvestEligibilityProof.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/harvestproof/Plain.class", directBlock("foreign/harvestproof/Plain", Kind.PLAIN));
            put(out, "foreign/harvestproof/CustomBase.class",
                    directBlock("foreign/harvestproof/CustomBase", Kind.CAN_HARVEST));
            put(out, "foreign/harvestproof/InheritedCustom.class",
                    child("foreign/harvestproof/InheritedCustom", "foreign/harvestproof/CustomBase"));
            put(out, "foreign/harvestproof/CustomTool.class",
                    directBlock("foreign/harvestproof/CustomTool", Kind.HARVEST_TOOL));
            put(out, "foreign/harvestproof/HarvestLevelSetter.class",
                    directBlock("foreign/harvestproof/HarvestLevelSetter", Kind.SET_HARVEST_LEVEL));
            put(out, "foreign/harvestproof/CustomMaterial.class",
                    directBlock("foreign/harvestproof/CustomMaterial", Kind.GET_MATERIAL));
            put(out, "foreign/harvestproof/Specialized.class",
                    child("foreign/harvestproof/Specialized", "net/minecraft/block/BlockOre"));
            put(out, "foreign/harvestproof/Bootstrap.class", bootstrap());
        }

        LegacyBlockHarvestEligibilityAnalyzer.Analysis analysis =
                new LegacyBlockHarvestEligibilityAnalyzer().analyze(jar);
        Map<String, LegacyBlockHarvestEligibilityAnalyzer.Proof> proofs = analysis.proofs().stream()
                .collect(Collectors.toMap(LegacyBlockHarvestEligibilityAnalyzer.Proof::registryName, value -> value));

        assertEquals(6, proofs.size(), String.join("\n", analysis.diagnostics()));

        var plain = proofs.get("plain");
        assertTrue(plain.sourceCustomizationFree());
        assertTrue(plain.reasons().isEmpty());
        assertTrue(plain.materialFastPathSourceSafe());
        assertTrue(plain.materialFastPathReasons().isEmpty());

        var inherited = proofs.get("inherited_custom");
        assertFalse(inherited.sourceCustomizationFree());
        assertFalse(inherited.materialFastPathSourceSafe());
        assertTrue(inherited.reasons().stream().anyMatch(reason -> reason.contains("canHarvestBlock")));
        assertTrue(inherited.materialFastPathReasons().stream().anyMatch(reason -> reason.contains("canHarvestBlock")));

        var tool = proofs.get("custom_tool");
        assertFalse(tool.sourceCustomizationFree());
        assertTrue(tool.materialFastPathSourceSafe());
        assertTrue(tool.reasons().stream().anyMatch(reason -> reason.contains("getHarvestTool")));
        assertTrue(tool.materialFastPathReasons().isEmpty());

        var setter = proofs.get("harvest_level_setter");
        assertFalse(setter.sourceCustomizationFree());
        assertTrue(setter.materialFastPathSourceSafe());
        assertTrue(setter.reasons().stream().anyMatch(reason -> reason.contains("setHarvestLevel")));
        assertTrue(setter.materialFastPathReasons().isEmpty());

        var material = proofs.get("custom_material");
        assertFalse(material.sourceCustomizationFree());
        assertFalse(material.materialFastPathSourceSafe());
        assertTrue(material.reasons().stream().anyMatch(reason -> reason.contains("getMaterial")));
        assertTrue(material.materialFastPathReasons().stream().anyMatch(reason -> reason.contains("getMaterial")));

        var specialized = proofs.get("specialized");
        assertFalse(specialized.sourceCustomizationFree());
        assertFalse(specialized.materialFastPathSourceSafe());
        assertTrue(specialized.reasons().stream().anyMatch(reason -> reason.contains("BlockOre")));
        assertTrue(specialized.materialFastPathReasons().stream().anyMatch(reason -> reason.contains("BlockOre")));
    }

    private static byte[] directBlock(String owner, Kind kind) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        if (kind == Kind.SET_HARVEST_LEVEL) {
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitLdcInsn("pickaxe");
            init.visitInsn(Opcodes.ICONST_2);
            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "setHarvestLevel",
                    "(Ljava/lang/String;I)V", false);
        }
        init.visitInsn(Opcodes.RETURN);
        end(init);

        if (kind == Kind.CAN_HARVEST) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "canHarvestBlock",
                    "(Lnet/minecraft/entity/player/EntityPlayer;I)Z", null, null);
            method.visitCode(); method.visitInsn(Opcodes.ICONST_1); method.visitInsn(Opcodes.IRETURN); end(method);
        } else if (kind == Kind.HARVEST_TOOL) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getHarvestTool",
                    "(I)Ljava/lang/String;", null, null);
            method.visitCode(); method.visitLdcInsn("axe"); method.visitInsn(Opcodes.ARETURN); end(method);
        } else if (kind == Kind.GET_MATERIAL) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getMaterial",
                    "()Lnet/minecraft/block/material/Material;", null, null);
            method.visitCode(); method.visitInsn(Opcodes.ACONST_NULL); method.visitInsn(Opcodes.ARETURN); end(method);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] child(String owner, String parent) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, parent, null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN); end(init); writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/harvestproof/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd(); method.visitCode();
        register(method, "foreign/harvestproof/Plain", "plain");
        register(method, "foreign/harvestproof/InheritedCustom", "inherited_custom");
        register(method, "foreign/harvestproof/CustomTool", "custom_tool");
        register(method, "foreign/harvestproof/HarvestLevelSetter", "harvest_level_setter");
        register(method, "foreign/harvestproof/CustomMaterial", "custom_material");
        register(method, "foreign/harvestproof/Specialized", "specialized");
        method.visitInsn(Opcodes.RETURN); end(method); writer.visitEnd(); return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String registryName) {
        method.visitTypeInsn(Opcodes.NEW, owner); method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(registryName);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void end(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }

    private enum Kind { PLAIN, CAN_HARVEST, HARVEST_TOOL, SET_HARVEST_LEVEL, GET_MATERIAL }
}
