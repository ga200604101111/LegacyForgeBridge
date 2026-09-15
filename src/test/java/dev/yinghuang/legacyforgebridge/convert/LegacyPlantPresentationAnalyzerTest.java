package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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

class LegacyPlantPresentationAnalyzerTest {
    @TempDir Path tempDir;

    @Test void constantTextureAndPresentationHooksAreProvenIndependently() throws Exception {
        Path jar = tempDir.resolve("PlantPresentation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "z/PureCrop.class", block("z/PureCrop", "net/minecraft/block/BlockCrops", "plants:herb", false, false));
            put(out, "z/HookCrop.class", block("z/HookCrop", "net/minecraft/block/BlockCrops", "plants:hook", true, false));
            put(out, "z/BranchCrop.class", block("z/BranchCrop", "net/minecraft/block/BlockCrops", "plants:branch", false, true));
            put(out, "z/Reed.class", block("z/Reed", "net/minecraft/block/BlockReed", "plants:reed", false, false));
            put(out, "z/Bootstrap.class", bootstrap());
        }
        var analysis = new LegacyPlantPresentationAnalyzer().analyze(jar);
        Map<String,LegacyPlantPresentationAnalyzer.Proof> proofs = analysis.proofs().stream()
                .collect(Collectors.toMap(LegacyPlantPresentationAnalyzer.Proof::registryName, Function.identity()));
        assertEquals(4, proofs.size());

        var pure = proofs.get("pure_crop");
        assertTrue(pure.constructorPathStraightLine());
        assertEquals("plants:herb", pure.textureName());
        assertTrue(pure.textureNameProofComplete());
        assertTrue(pure.sourcePresentationProofComplete());

        var hook = proofs.get("hook_crop");
        assertTrue(hook.textureNameProofComplete());
        assertEquals(1, hook.sourcePresentationHooks().size());
        assertFalse(hook.sourcePresentationProofComplete());

        var branch = proofs.get("branch_crop");
        assertFalse(branch.constructorPathStraightLine());
        assertFalse(branch.textureNameProofComplete());
        assertFalse(branch.sourcePresentationProofComplete());

        var reed = proofs.get("reed");
        assertEquals(LegacyPlantBlockAnalyzer.Family.REED, reed.family());
        assertEquals("plants:reed", reed.textureName());
        assertTrue(reed.sourcePresentationProofComplete());
    }

    private static byte[] block(String name, String superName, String texture, boolean hook, boolean branch) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, superName, null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        if (branch) {
            Label target = new Label();
            init.visitInsn(Opcodes.ICONST_0);
            init.visitJumpInsn(Opcodes.IFEQ, target);
            init.visitLabel(target);
        }
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(texture);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/block/Block", "setBlockTextureName",
                "(Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        init.visitInsn(Opcodes.POP);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();
        if (hook) {
            MethodVisitor icon = w.visitMethod(Opcodes.ACC_PUBLIC, "getIcon", "(II)Lnet/minecraft/util/IIcon;", null, null);
            icon.visitCode(); icon.visitInsn(Opcodes.ACONST_NULL); icon.visitInsn(Opcodes.ARETURN); icon.visitMaxs(0, 0); icon.visitEnd();
        }
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "z/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        register(m, "z/PureCrop", "pure_crop");
        register(m, "z/HookCrop", "hook_crop");
        register(m, "z/BranchCrop", "branch_crop");
        register(m, "z/Reed", "reed");
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
