package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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

class LegacyBlockDropPlanCompilerTest {
    @TempDir Path tempDir;

    @Test void onlyCompleteDirectBlockSemanticsBecomeRuntimeSafePlans() throws Exception {
        Path jar = tempDir.resolve("ForeignDropPlans.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/dropplan/DefaultBlock.class", defaultBlock());
            put(out, "foreign/dropplan/SafeNoDrop.class", safeNoDrop());
            put(out, "foreign/dropplan/FortuneOverride.class", fortuneOverride());
            put(out, "foreign/dropplan/RandomCount.class", randomCount());
            put(out, "foreign/dropplan/ExternalDerived.class", externalDerived());
            put(out, "foreign/dropplan/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyBlockDropPlanCompiler().compile(jar);
        Map<String, LegacyBlockDropPlanCompiler.Plan> plans = analysis.plans().stream()
                .collect(Collectors.toMap(LegacyBlockDropPlanCompiler.Plan::registryName, value -> value));
        Map<String, LegacyBlockDropPlanCompiler.Incomplete> incomplete = analysis.incomplete().stream()
                .collect(Collectors.toMap(LegacyBlockDropPlanCompiler.Incomplete::registryName, value -> value));

        assertEquals(2, plans.size());
        assertEquals(3, incomplete.size());

        var defaults = plans.get("default_block");
        assertEquals(LegacyBlockDropPlanCompiler.ItemKind.SELF_BLOCK_ITEM, defaults.item().kind());
        assertEquals("default_block", defaults.item().registryName());
        assertEquals(1, defaults.quantity());
        assertEquals(16, defaults.itemDamageByBlockMeta().size());
        assertTrue(defaults.itemDamageByBlockMeta().stream().allMatch(value -> value == 0));
        assertTrue(defaults.platformDefaults().containsAll(java.util.Set.of("item", "quantity", "damage")));

        var safeNoDrop = plans.get("safe_no_drop");
        assertEquals(LegacyBlockDropPlanCompiler.ItemKind.NONE, safeNoDrop.item().kind());
        assertEquals(0, safeNoDrop.quantity());
        assertEquals(7, safeNoDrop.itemDamageByBlockMeta().get(15));
        assertTrue(safeNoDrop.platformDefaults().isEmpty());

        assertTrue(incomplete.get("fortune_override").reasons().stream()
                .anyMatch(value -> value.contains("quantityDroppedWithBonus")));
        assertTrue(incomplete.get("random_count").reasons().stream()
                .anyMatch(value -> value.contains("quantityDropped override")));
        assertTrue(incomplete.get("external_derived").reasons().stream()
                .anyMatch(value -> value.contains("BlockContainer")));
        assertFalse(plans.containsKey("fortune_override"));
        assertFalse(plans.containsKey("random_count"));
        assertFalse(plans.containsKey("external_derived"));
    }

    private static byte[] defaultBlock() {
        return bareBlock("foreign/dropplan/DefaultBlock", "net/minecraft/block/Block");
    }

    private static byte[] safeNoDrop() {
        String owner = "foreign/dropplan/SafeNoDrop";
        ClassWriter writer = blockWriter(owner, "net/minecraft/block/Block");

        MethodVisitor item = writer.visitMethod(Opcodes.ACC_PUBLIC, "getItemDropped",
                "(ILjava/util/Random;I)Lnet/minecraft/item/Item;", null, null);
        item.visitCode();
        item.visitInsn(Opcodes.ACONST_NULL);
        item.visitInsn(Opcodes.ARETURN);
        end(item);

        MethodVisitor quantity = writer.visitMethod(Opcodes.ACC_PUBLIC, "quantityDropped", "(Ljava/util/Random;)I", null, null);
        quantity.visitCode();
        quantity.visitInsn(Opcodes.ICONST_0);
        quantity.visitInsn(Opcodes.IRETURN);
        end(quantity);

        MethodVisitor damage = writer.visitMethod(Opcodes.ACC_PUBLIC, "damageDropped", "(I)I", null, null);
        damage.visitCode();
        damage.visitVarInsn(Opcodes.ILOAD, 1);
        damage.visitIntInsn(Opcodes.BIPUSH, 7);
        damage.visitInsn(Opcodes.IAND);
        damage.visitInsn(Opcodes.IRETURN);
        end(damage);

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] fortuneOverride() {
        String owner = "foreign/dropplan/FortuneOverride";
        ClassWriter writer = blockWriter(owner, "net/minecraft/block/Block");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "quantityDroppedWithBonus",
                "(ILjava/util/Random;)I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.IRETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] randomCount() {
        String owner = "foreign/dropplan/RandomCount";
        ClassWriter writer = blockWriter(owner, "net/minecraft/block/Block");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "quantityDropped", "(Ljava/util/Random;)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitIntInsn(Opcodes.BIPUSH, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Random", "nextInt", "(I)I", false);
        method.visitInsn(Opcodes.IRETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] externalDerived() {
        return bareBlock("foreign/dropplan/ExternalDerived", "net/minecraft/block/BlockContainer");
    }

    private static byte[] bareBlock(String owner, String parent) {
        ClassWriter writer = blockWriter(owner, parent);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter blockWriter(String owner, String parent) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, parent, null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        if ("net/minecraft/block/Block".equals(parent)) {
            init.visitInsn(Opcodes.ACONST_NULL);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>",
                    "(Lnet/minecraft/block/material/Material;)V", false);
        } else {
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        }
        init.visitInsn(Opcodes.RETURN);
        end(init);
        return writer;
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/dropplan/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/dropplan/DefaultBlock", "default_block");
        register(method, "foreign/dropplan/SafeNoDrop", "safe_no_drop");
        register(method, "foreign/dropplan/FortuneOverride", "fortune_override");
        register(method, "foreign/dropplan/RandomCount", "random_count");
        register(method, "foreign/dropplan/ExternalDerived", "external_derived");
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
}
