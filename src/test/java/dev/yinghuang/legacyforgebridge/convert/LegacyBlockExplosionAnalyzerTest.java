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

class LegacyBlockExplosionAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void separatesExplosionDropEligibilityFromDestructionCallbacksAndWalksSourceHierarchy() throws Exception {
        Path jar = tempDir.resolve("ExplosionProof.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/explosion/Plain.class", directBlock("foreign/explosion/Plain", Kind.PLAIN));
            put(out, "foreign/explosion/NoDropBase.class", directBlock("foreign/explosion/NoDropBase", Kind.NO_DROP));
            put(out, "foreign/explosion/InheritedNoDrop.class",
                    child("foreign/explosion/InheritedNoDrop", "foreign/explosion/NoDropBase"));
            put(out, "foreign/explosion/CustomExploded.class",
                    directBlock("foreign/explosion/CustomExploded", Kind.ON_EXPLODED));
            put(out, "foreign/explosion/CustomDestroyed.class",
                    directBlock("foreign/explosion/CustomDestroyed", Kind.DESTROYED_BY_EXPLOSION));
            put(out, "foreign/explosion/Specialized.class",
                    child("foreign/explosion/Specialized", "net/minecraft/block/BlockTNT"));
            put(out, "foreign/explosion/Bootstrap.class", bootstrap());
        }

        LegacyBlockExplosionAnalyzer.Analysis analysis = new LegacyBlockExplosionAnalyzer().analyze(jar);
        Map<String, LegacyBlockExplosionAnalyzer.Proof> proofs = analysis.proofs().stream()
                .collect(Collectors.toMap(LegacyBlockExplosionAnalyzer.Proof::registryName, value -> value));

        assertEquals(5, proofs.size(), String.join("\n", analysis.diagnostics()));

        var plain = proofs.get("plain");
        assertTrue(plain.dropEligibilityProofComplete());
        assertTrue(plain.sourceDestructionOverrideFree());
        assertTrue(plain.dropEligibilityReasons().isEmpty());
        assertTrue(plain.destructionReasons().isEmpty());

        var noDrop = proofs.get("inherited_no_drop");
        assertFalse(noDrop.dropEligibilityProofComplete());
        assertTrue(noDrop.sourceDestructionOverrideFree());
        assertTrue(noDrop.dropEligibilityReasons().stream()
                .anyMatch(reason -> reason.contains("canDropFromExplosion")));

        var exploded = proofs.get("custom_exploded");
        assertTrue(exploded.dropEligibilityProofComplete());
        assertFalse(exploded.sourceDestructionOverrideFree());
        assertTrue(exploded.destructionReasons().stream()
                .anyMatch(reason -> reason.contains("onBlockExploded")));

        var destroyed = proofs.get("custom_destroyed");
        assertTrue(destroyed.dropEligibilityProofComplete());
        assertFalse(destroyed.sourceDestructionOverrideFree());
        assertTrue(destroyed.destructionReasons().stream()
                .anyMatch(reason -> reason.contains("onBlockDestroyedByExplosion")));

        var specialized = proofs.get("specialized");
        assertFalse(specialized.dropEligibilityProofComplete());
        assertFalse(specialized.sourceDestructionOverrideFree());
        assertTrue(specialized.dropEligibilityReasons().stream()
                .anyMatch(reason -> reason.contains("BlockTNT")));
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
        init.visitInsn(Opcodes.RETURN);
        end(init);

        switch (kind) {
            case PLAIN -> { }
            case NO_DROP -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149659_a",
                        "(Lnet/minecraft/world/Explosion;)Z", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.ICONST_0);
                method.visitInsn(Opcodes.IRETURN);
                end(method);
            }
            case ON_EXPLODED -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "onBlockExploded",
                        "(Lnet/minecraft/world/World;IIILnet/minecraft/world/Explosion;)V", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.RETURN);
                end(method);
            }
            case DESTROYED_BY_EXPLOSION -> {
                MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149723_a",
                        "(Lnet/minecraft/world/World;IIILnet/minecraft/world/Explosion;)V", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.RETURN);
                end(method);
            }
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] child(String owner, String parent) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, parent, null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/explosion/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/explosion/Plain", "plain");
        register(method, "foreign/explosion/InheritedNoDrop", "inherited_no_drop");
        register(method, "foreign/explosion/CustomExploded", "custom_exploded");
        register(method, "foreign/explosion/CustomDestroyed", "custom_destroyed");
        register(method, "foreign/explosion/Specialized", "specialized");
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String name) {
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(name);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
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

    private enum Kind { PLAIN, NO_DROP, ON_EXPLODED, DESTROYED_BY_EXPLOSION }
}
