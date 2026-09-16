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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropPathSafetyTest {
    @TempDir Path tempDir;

    @Test
    void sourceOwnedHarvestAndSilkOverridesFailClosedBeforeNormalDropPlans() throws Exception {
        Path jar = tempDir.resolve("DropPathSafety.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/droppath/Plain.class", block("foreign/droppath/Plain", Kind.PLAIN));
            put(out, "foreign/droppath/FullDrops.class", block("foreign/droppath/FullDrops", Kind.FULL_DROPS));
            put(out, "foreign/droppath/DropDirect.class", block("foreign/droppath/DropDirect", Kind.DROP_DIRECT));
            put(out, "foreign/droppath/DropChance.class", block("foreign/droppath/DropChance", Kind.DROP_CHANCE));
            put(out, "foreign/droppath/Harvest.class", block("foreign/droppath/Harvest", Kind.HARVEST));
            put(out, "foreign/droppath/Silk.class", block("foreign/droppath/Silk", Kind.SILK));
            put(out, "foreign/droppath/ContextSilk.class", block("foreign/droppath/ContextSilk", Kind.CONTEXT_SILK));
            put(out, "foreign/droppath/Stacked.class", block("foreign/droppath/Stacked", Kind.STACKED));
            put(out, "foreign/droppath/Bootstrap.class", bootstrap());
        }

        LegacyBlockDropPlanCompiler.Analysis analysis = new LegacyBlockDropPlanCompiler().compile(jar);
        Map<String, LegacyBlockDropPlanCompiler.Plan> plans = analysis.plans().stream()
                .collect(Collectors.toMap(LegacyBlockDropPlanCompiler.Plan::registryName, value -> value));
        Map<String, LegacyBlockDropPlanCompiler.Incomplete> incomplete = analysis.incomplete().stream()
                .collect(Collectors.toMap(LegacyBlockDropPlanCompiler.Incomplete::registryName, value -> value));

        assertEquals(1, plans.size(), String.join("\n", analysis.diagnostics()));
        assertEquals(7, incomplete.size());
        assertTrue(plans.containsKey("plain"));

        assertReason(incomplete, "full_drops", "getDrops(World,...)");
        assertReason(incomplete, "drop_direct", "dropBlockAsItem");
        assertReason(incomplete, "drop_chance", "dropBlockAsItemWithChance");
        assertReason(incomplete, "harvest", "harvestBlock");
        assertReason(incomplete, "silk", "canSilkHarvest");
        assertReason(incomplete, "context_silk", "canSilkHarvest(World,...)");
        assertReason(incomplete, "stacked", "createStackedBlock");
    }

    private static void assertReason(
            Map<String, LegacyBlockDropPlanCompiler.Incomplete> incomplete,
            String registryName,
            String fragment
    ) {
        assertTrue(incomplete.get(registryName).reasons().stream().anyMatch(value -> value.contains(fragment)),
                () -> registryName + ": " + incomplete.get(registryName).reasons());
    }

    private static byte[] block(String owner, Kind kind) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);

        switch (kind) {
            case PLAIN -> { }
            case FULL_DROPS -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getDrops",
                        "(Lnet/minecraft/world/World;IIIII)Ljava/util/ArrayList;", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.ACONST_NULL);
                method.visitInsn(Opcodes.ARETURN);
                end(method);
            }
            case DROP_DIRECT -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149697_b",
                        "(Lnet/minecraft/world/World;IIIII)V", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.RETURN);
                end(method);
            }
            case DROP_CHANCE -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149690_a",
                        "(Lnet/minecraft/world/World;IIIIFI)V", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.RETURN);
                end(method);
            }
            case HARVEST -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149636_a",
                        "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;IIII)V",
                        null, null);
                method.visitCode();
                method.visitInsn(Opcodes.RETURN);
                end(method);
            }
            case SILK -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PROTECTED, "func_149700_E",
                        "()Z", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.ICONST_1);
                method.visitInsn(Opcodes.IRETURN);
                end(method);
            }
            case CONTEXT_SILK -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "canSilkHarvest",
                        "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;IIII)Z",
                        null, null);
                method.visitCode();
                method.visitInsn(Opcodes.ICONST_1);
                method.visitInsn(Opcodes.IRETURN);
                end(method);
            }
            case STACKED -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PROTECTED, "func_149644_j",
                        "(I)Lnet/minecraft/item/ItemStack;", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.ACONST_NULL);
                method.visitInsn(Opcodes.ARETURN);
                end(method);
            }
        }

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/droppath/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();

        register(method, "foreign/droppath/Plain", "plain");
        register(method, "foreign/droppath/FullDrops", "full_drops");
        register(method, "foreign/droppath/DropDirect", "drop_direct");
        register(method, "foreign/droppath/DropChance", "drop_chance");
        register(method, "foreign/droppath/Harvest", "harvest");
        register(method, "foreign/droppath/Silk", "silk");
        register(method, "foreign/droppath/ContextSilk", "context_silk");
        register(method, "foreign/droppath/Stacked", "stacked");

        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String registryName) {
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(registryName);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V",
                false);
    }

    private static void end(MethodVisitor method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    private enum Kind {
        PLAIN,
        FULL_DROPS,
        DROP_DIRECT,
        DROP_CHANCE,
        HARVEST,
        SILK,
        CONTEXT_SILK,
        STACKED
    }
}
