package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockAllocationRetirementSafetyTest implements Opcodes {
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String CONTAINER = "net/minecraft/block/BlockContainer";
    private static final String MACHINE = "fixture/machine/PressBlock";
    private static final String OWNER = "fixture/machine/Bootstrap";
    private static final String EVENT =
            "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V";
    private static final String HARDNESS = "(F)Lnet/minecraft/block/Block;";

    @TempDir Path tempDir;

    @Test
    void admitsInheritedSetterThroughBaseTypedReceiver() throws Exception {
        var proof = sourceProof("base");
        assertTrue(proof.allocationProofComplete(), proof.blockers().toString());
        assertEquals(1, proof.effects().size());
    }

    @Test
    void rejectsVirtualOverrideHiddenByBaseCallOwner() throws Exception {
        var proof = sourceProof("override");
        assertFalse(proof.allocationProofComplete());
        assertTrue(proof.blockers().stream().anyMatch(value ->
                value.startsWith("source-block-allocation-setter-owner-not-safe:")),
                proof.blockers().toString());
    }

    @Test
    void rejectsNonFloatConstantInsteadOfCoercingIt() throws Exception {
        var proof = sourceProof("integer");
        assertFalse(proof.allocationProofComplete());
        assertTrue(proof.blockers().contains("source-block-allocation-hardness-not-supported"),
                proof.blockers().toString());
    }

    @Test
    void rejectsAllocationConsumedThroughDuplicateBeforeRegistration() throws Exception {
        var proof = sourceProof("alias");
        assertFalse(proof.allocationProofComplete());
        assertTrue(proof.blockers().contains("source-block-allocation-exclusive-use-not-proven"),
                proof.blockers().toString());
    }

    @Test
    void rejectsCastBetweenAllocationAndRegistration() throws Exception {
        var proof = sourceProof("cast");
        assertFalse(proof.allocationProofComplete());
        assertTrue(proof.blockers().contains("source-block-allocation-exclusive-use-not-proven"),
                proof.blockers().toString());
    }

    @Test
    void stripsSimpleTwoAndFourArgumentResidues() throws Exception {
        for (String mode : List.of("plain", "four")) {
            var result = strip(mode);
            assertEquals(1, result.strippedSites(), result.blockers().toString());
            assertTrue(result.blockers().isEmpty());
            load(result.bytes()).getMethod("boot").invoke(null);
        }
    }

    @Test
    void stripsProvenFluentSetterResidue() throws Exception {
        var result = strip("float");
        assertEquals(1, result.strippedSites(), result.blockers().toString());
        load(result.bytes()).getMethod("boot").invoke(null);
    }

    @Test
    void preservesNameEvaluationSideEffects() throws Exception {
        var result = strip("name");
        assertEquals(1, result.strippedSites(), result.blockers().toString());
        Class<?> output = load(result.bytes());
        output.getMethod("boot").invoke(null);
        assertEquals(1, output.getField("seen").getInt(null));
    }

    @Test
    void preservesStackMapFramesOfUnchangedMethods() throws Exception {
        byte[] source = staged("frames");
        assertTrue(frameCount(source) > 0);
        var result = new LegacyBlockAllocationResidueStripper().strip(source, target(false));
        assertEquals(1, result.strippedSites(), result.blockers().toString());
        assertEquals(frameCount(source), frameCount(result.bytes()));
        Class<?> output = load(result.bytes());
        assertEquals(1, output.getMethod("choose", boolean.class).invoke(null, true));
        assertEquals(2, output.getMethod("choose", boolean.class).invoke(null, false));
        output.getMethod("boot").invoke(null);
    }

    @Test
    void rejectsEscapedDuplicateEvenWhenAnotherStackValueMasksUnderflow() {
        assertUnchanged("alias");
    }

    @Test
    void rejectsWrongStagedConstantTypeWithoutMutatingBytes() {
        assertUnchanged("integer");
    }

    @Test
    void rejectsControlFlowInsideLiveAllocationInterval() {
        assertUnchanged("branch");
    }

    @Test
    void rejectsExceptionProtectedAllocationInterval() {
        assertUnchanged("catch");
    }

    @Test
    void rejectsLocalAliasWithoutMutatingBytes() {
        assertUnchanged("local");
    }

    @Test
    void rejectsAmbiguousMatchingAllocations() {
        assertUnchanged("ambiguous");
    }

    @Test
    void rollsBackWhenSourceClassReferenceRemains() {
        assertUnchanged("reference");
    }

    private LegacySingleInputProcessorBlockAllocationAnalyzer.Proof sourceProof(String mode)
            throws Exception {
        Path jar = tempDir.resolve(mode + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, MACHINE, machine(mode.equals("override")));
            ClassWriter writer = owner();
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC,
                    "boot", EVENT, null, null);
            method.visitCode();
            if (mode.equals("alias")) method.visitInsn(ACONST_NULL);
            allocation(method);
            if (mode.equals("alias")) {
                method.visitInsn(DUP);
                method.visitMethodInsn(INVOKESTATIC, OWNER, "observe",
                        "(Ljava/lang/Object;)V", false);
            } else if (mode.equals("cast")) {
                method.visitTypeInsn(CHECKCAST, BLOCK);
            } else {
                if (mode.equals("integer")) method.visitLdcInsn(3);
                else method.visitLdcInsn(3.0F);
                method.visitMethodInsn(INVOKEVIRTUAL, BLOCK, "func_149711_c", HARDNESS, false);
            }
            method.visitLdcInsn("press");
            method.visitMethodInsn(INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry",
                    "registerBlock", "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
            if (mode.equals("alias")) method.visitInsn(POP);
            method.visitInsn(RETURN);
            finish(method);
            observer(writer);
            writer.visitEnd();
            put(out, OWNER, writer.toByteArray());
        }
        var rule = new LegacySingleInputProcessorAnalyzer.Rule(
                "press", "fixture", MACHINE, "fixture/machine/PressTile",
                "fixture.machine.PressTile", 3, 64, 0, List.of(1, 2),
                List.of(0), List.of(1, 2), List.of(0), 200, 64.0D, 7,
                "fixture/machine/Recipes", "lookup",
                "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;",
                true, true, false);
        return new LegacySingleInputProcessorBlockAllocationAnalyzer().prove(jar, rule);
    }

    private static byte[] machine(boolean override) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_7, ACC_PUBLIC, MACHINE, null, CONTAINER, null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitInsn(ACONST_NULL);
        method.visitMethodInsn(INVOKESPECIAL, CONTAINER, "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        method.visitInsn(RETURN);
        finish(method);
        if (override) {
            method = writer.visitMethod(ACC_PUBLIC, "func_149711_c", HARDNESS, null, null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 0);
            method.visitMethodInsn(INVOKESTATIC, OWNER, "observe", "(Ljava/lang/Object;)V", false);
            method.visitVarInsn(ALOAD, 0);
            method.visitInsn(ARETURN);
            finish(method);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static LegacyBlockAllocationResidueStripper.Target target(boolean effects) {
        return new LegacyBlockAllocationResidueStripper.Target("boot", "()V", MACHINE, "()V",
                effects ? List.of(new LegacySingleInputProcessorBlockAllocationAnalyzer.Effect(
                        LegacySingleInputProcessorBlockAllocationAnalyzer.EffectKind.HARDNESS,
                        MACHINE, "func_149711_c", HARDNESS, 3.0F, null)) : List.of());
    }

    private static LegacyBlockAllocationResidueStripper.Result strip(String mode) {
        return new LegacyBlockAllocationResidueStripper().strip(staged(mode),
                target(mode.equals("float") || mode.equals("integer")));
    }

    private static void assertUnchanged(String mode) {
        byte[] source = staged(mode);
        var result = new LegacyBlockAllocationResidueStripper().strip(source,
                target(mode.equals("integer")));
        assertEquals(0, result.strippedSites(), result.blockers().toString());
        assertFalse(result.blockers().isEmpty());
        assertArrayEquals(source, result.bytes());
    }

    private static byte[] staged(String mode) {
        ClassWriter writer = owner();
        writer.visitField(ACC_PUBLIC | ACC_STATIC, "seen", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "boot", "()V", null, null);
        method.visitCode();
        Label start = new Label(), end = new Label(), handler = new Label(), done = new Label();
        if (mode.equals("catch")) method.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
        method.visitLabel(start);
        if (mode.equals("alias")) method.visitInsn(ACONST_NULL);
        allocation(method);
        if (mode.equals("float") || mode.equals("integer")) {
            if (mode.equals("integer")) method.visitLdcInsn(3);
            else method.visitLdcInsn(3.0F);
            method.visitMethodInsn(INVOKEVIRTUAL, MACHINE, "func_149711_c", HARDNESS, false);
        }
        if (mode.equals("alias")) {
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESTATIC, OWNER, "observe", "(Ljava/lang/Object;)V", false);
        }
        if (mode.equals("local")) {
            method.visitVarInsn(ASTORE, 0);
            method.visitVarInsn(ALOAD, 0);
        }
        if (mode.equals("branch")) {
            Label join = new Label();
            method.visitJumpInsn(GOTO, join);
            method.visitLabel(join);
        }
        if (mode.equals("four")) method.visitLdcInsn(Type.getType("Ljava/lang/Object;"));
        if (mode.equals("name")) {
            method.visitMethodInsn(INVOKESTATIC, OWNER, "nextName", "()Ljava/lang/String;", false);
        } else method.visitLdcInsn("press");
        if (mode.equals("four")) {
            method.visitInsn(ICONST_0);
            method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            method.visitInsn(POP);
            method.visitInsn(POP);
        }
        method.visitInsn(POP);
        method.visitInsn(POP);
        if (mode.equals("alias")) method.visitInsn(POP);
        if (mode.equals("reference")) {
            method.visitLdcInsn(Type.getObjectType(MACHINE));
            method.visitInsn(POP);
        }
        if (mode.equals("ambiguous")) {
            allocation(method);
            method.visitLdcInsn("other");
            method.visitInsn(POP);
            method.visitInsn(POP);
        }
        method.visitLabel(end);
        if (mode.equals("catch")) {
            method.visitJumpInsn(GOTO, done);
            method.visitLabel(handler);
            method.visitVarInsn(ASTORE, 0);
            method.visitInsn(RETURN);
            method.visitLabel(done);
        }
        method.visitInsn(RETURN);
        finish(method);
        observer(writer);
        method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "nextName", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitFieldInsn(GETSTATIC, OWNER, "seen", "I");
        method.visitInsn(ICONST_1);
        method.visitInsn(IADD);
        method.visitFieldInsn(PUTSTATIC, OWNER, "seen", "I");
        method.visitLdcInsn("press");
        method.visitInsn(ARETURN);
        finish(method);
        if (mode.equals("frames")) {
            method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "choose", "(Z)I", null, null);
            method.visitCode();
            Label otherwise = new Label();
            method.visitVarInsn(ILOAD, 0);
            method.visitJumpInsn(IFEQ, otherwise);
            method.visitInsn(ICONST_1);
            method.visitInsn(IRETURN);
            method.visitLabel(otherwise);
            method.visitInsn(ICONST_2);
            method.visitInsn(IRETURN);
            finish(method);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter owner() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_7, ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        return writer;
    }

    private static void allocation(MethodVisitor method) {
        method.visitTypeInsn(NEW, MACHINE);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, MACHINE, "<init>", "()V", false);
    }

    private static void observer(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "observe",
                "(Ljava/lang/Object;)V", null, null);
        method.visitCode();
        method.visitInsn(RETURN);
        finish(method);
    }

    private static void finish(MethodVisitor method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name + ".class"));
        out.write(bytes);
        out.closeEntry();
    }

    private static int frameCount(byte[] bytes) {
        ClassNode node = new ClassNode(ASM9);
        new ClassReader(bytes).accept(node, 0);
        int count = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof FrameNode) count++;
            }
        }
        return count;
    }

    private static Class<?> load(byte[] bytes) {
        return new ClassLoader(null) {
            Class<?> define() { return defineClass(OWNER.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
    }
}
