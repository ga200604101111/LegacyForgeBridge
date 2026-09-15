package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static dev.yinghuang.legacyforgebridge.convert.LegacyBlockActivationCompiler.Op;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBlockActivationSafetyTest {
    private static final String DESC = "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z";
    @TempDir Path tempDir;

    @Test void booleanReturnUsesJvmLowBitNotNonzero() {
        for (int value : new int[]{Integer.MIN_VALUE, -3, -2, -1, 0, 1, 2, 3, Integer.MAX_VALUE}) {
            MethodNode method = method();
            method.visitLdcInsn(value);
            method.visitInsn(Opcodes.IRETURN);
            assertEquals((value & 1) != 0, compile(method).evaluate(0), "source return=" + value);
        }
    }

    @Test void composedSourceInputsAgreeForAll384Tuples() {
        MethodNode method = method();
        clientSide(method);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "func_70093_af", "()Z", false);
        method.visitInsn(Opcodes.IXOR);
        metadata(method);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IAND);
        method.visitInsn(Opcodes.IXOR);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IAND);
        method.visitInsn(Opcodes.IXOR);
        method.visitInsn(Opcodes.IRETURN);
        var program = compile(method);
        int checked = 0;
        for (int side = 0; side < 6; side++) {
            for (int meta = 0; meta < 16; meta++) {
                for (int flags = 0; flags < 4; flags++) {
                    boolean client = (flags & 1) != 0;
                    boolean sneak = (flags & 2) != 0;
                    boolean expected = client ^ sneak ^ ((meta & 1) != 0) ^ ((side & 1) != 0);
                    assertEquals(expected, program.evaluate(side, meta, client, sneak));
                    checked++;
                }
            }
        }
        assertEquals(384, checked);
    }

    @Test void guardedDivisionIsAccepted() {
        MethodNode method = method();
        Label zero = new Label();
        metadata(method);
        method.visitJumpInsn(Opcodes.IFEQ, zero);
        method.visitInsn(Opcodes.ICONST_1);
        metadata(method);
        method.visitInsn(Opcodes.IDIV);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(zero);
        boolReturn(method, false);
        var program = compile(method);
        assertFalse(program.evaluate(1, 0));
        assertTrue(program.evaluate(1, 1));
        assertFalse(program.evaluate(1, 2));
    }

    @Test void unguardedDivisionIsRejectedBeforeProgramInstallation() {
        var error = assertThrows(IllegalArgumentException.class, () -> compile(division()));
        assertTrue(error.getMessage().contains("activation input proof failed"));
        assertTrue(error.getMessage().contains("metadata=0"));
    }

    @Test void failureOnlyAtLastOf384TuplesIsNotMissed() {
        MethodNode method = method();
        Label safe = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitInsn(Opcodes.ICONST_5);
        method.visitJumpInsn(Opcodes.IF_ICMPNE, safe);
        metadata(method);
        method.visitIntInsn(Opcodes.BIPUSH, 15);
        method.visitJumpInsn(Opcodes.IF_ICMPNE, safe);
        clientSide(method);
        method.visitJumpInsn(Opcodes.IFEQ, safe);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/Entity", "isSneaking", "()Z", false);
        method.visitJumpInsn(Opcodes.IFEQ, safe);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IDIV);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(safe);
        boolReturn(method, false);
        var error = assertThrows(IllegalArgumentException.class, () -> compile(method));
        assertTrue(error.getMessage().contains("side=5, metadata=15, clientSide=true, sneaking=true"), error.getMessage());
    }

    @Test void tableSwitchAtIntegerMaxValueTerminates() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            MethodNode method = method();
            Label hit = new Label();
            Label fallback = new Label();
            method.visitLdcInsn(Integer.MAX_VALUE);
            method.visitTableSwitchInsn(Integer.MAX_VALUE, Integer.MAX_VALUE, fallback, hit);
            method.visitLabel(hit);
            boolReturn(method, true);
            method.visitLabel(fallback);
            boolReturn(method, false);
            assertTrue(compile(method).evaluate(1));
        });
    }

    @Test void largeIntegerMaskIsAdmittedWithoutConstantTruncation() {
        MethodNode method = method();
        metadata(method);
        method.visitLdcInsn(0x10001);
        method.visitInsn(Opcodes.IAND);
        method.visitInsn(Opcodes.IRETURN);
        var program = compile(method);
        assertFalse(program.evaluate(0, 2));
        assertTrue(program.evaluate(0, 3));
        assertTrue(program.instructions().stream().anyMatch(value -> value.op() == Op.CONST_INT && value.operand() == 0x10001));
    }

    @Test void nonIntegerLdcIsRejected() {
        MethodNode method = method();
        method.visitLdcInsn("not an integer");
        method.visitInsn(Opcodes.POP);
        boolReturn(method, true);
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void staticSynchronizedAbstractNativeAndNonPublicMethodsAreRejected() {
        for (int flag : new int[]{Opcodes.ACC_STATIC, Opcodes.ACC_SYNCHRONIZED, Opcodes.ACC_ABSTRACT, Opcodes.ACC_NATIVE}) {
            MethodNode method = method();
            method.access |= flag;
            boolReturn(method, true);
            assertThrows(IllegalArgumentException.class, () -> compile(method));
        }
        MethodNode hidden = method();
        hidden.access = Opcodes.ACC_PRIVATE;
        boolReturn(hidden, true);
        assertThrows(IllegalArgumentException.class, () -> compile(hidden));
    }

    @Test void exceptionHandlersAreNeverSilentlyDiscarded() {
        MethodNode method = method();
        Label start = new Label(), end = new Label(), handler = new Label();
        method.visitTryCatchBlock(start, end, handler, "java/lang/RuntimeException");
        method.visitLabel(start);
        boolReturn(method, true);
        method.visitLabel(end);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.POP);
        boolReturn(method, false);
        var error = assertThrows(IllegalArgumentException.class, () -> compile(method));
        assertTrue(error.getMessage().contains("exception handlers"));
    }

    @Test void branchCannotEnterMiddleOfTypedInputSequence() {
        MethodNode method = method();
        Label fieldRead = new Label();
        // Both paths hold a World reference; a stack-type check alone is insufficient.
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitJumpInsn(Opcodes.IFLT, fieldRead);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitLabel(fieldRead);
        method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/World", "isRemote", "Z");
        method.visitInsn(Opcodes.IRETURN);
        var error = assertThrows(IllegalArgumentException.class, () -> compile(method));
        assertTrue(error.getMessage().contains("branch enters typed-input sequence"), error.getMessage());
    }

    @Test void sourceStackUnderflowAndUninitializedLocalsAreRejected() {
        MethodNode underflow = method();
        underflow.visitInsn(Opcodes.IADD);
        underflow.visitInsn(Opcodes.IRETURN);
        assertThrows(IllegalArgumentException.class, () -> compile(underflow));
        MethodNode uninitialized = method();
        uninitialized.visitVarInsn(Opcodes.ILOAD, 10);
        uninitialized.visitInsn(Opcodes.IRETURN);
        assertThrows(IllegalArgumentException.class, () -> compile(uninitialized));
    }

    @Test void sourceDescriptorAndFrameBudgetAreChecked() {
        MethodNode method = method();
        boolReturn(method, true);
        method.desc = "(I)Z";
        assertThrows(IllegalArgumentException.class, () -> compile(method));
        method.desc = DESC;
        method.maxLocals = 65535;
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void malformedAndOverBudgetSourceSwitchesAreRejected() {
        for (int high : new int[]{-1, 0, 1000}) {
            MethodNode method = method();
            Label target = new Label();
            method.visitInsn(Opcodes.ICONST_0);
            method.visitTableSwitchInsn(0, high, target, target, target);
            method.visitLabel(target);
            boolReturn(method, false);
            assertThrows(IllegalArgumentException.class, () -> compile(method));
        }
    }

    @Test void sourceNeighborMetadataReadRemainsUnsupported() {
        MethodNode method = method();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "getBlockMetadata", "(III)I", false);
        method.visitInsn(Opcodes.IRETURN);
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void readOnlyPrefixCannotHideWorldMutation() {
        MethodNode method = method();
        clientSide(method);
        Label server = new Label();
        method.visitJumpInsn(Opcodes.IFEQ, server);
        boolReturn(method, true);
        method.visitLabel(server);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        for (int local = 2; local <= 4; local++) method.visitVarInsn(Opcodes.ILOAD, local);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "setBlockMetadataWithNotify", "(IIIII)Z", false);
        method.visitInsn(Opcodes.POP);
        boolReturn(method, true);
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void heldItemGatedMetadataRotationRemainsFailClosed() {
        // Source-shape cross-check from the pinned upstream file in the session document.
        // This is an unrelated-namespace fixture, NOT bytecode from the exact corpus JAR.
        MethodNode method = method();
        method.maxLocals = 13;
        Label pass = new Label(), wrap = new Label(), join = new Label();
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "getCurrentEquippedItem", "()Lnet/minecraft/item/ItemStack;", false);
        method.visitVarInsn(Opcodes.ASTORE, 10);
        method.visitVarInsn(Opcodes.ALOAD, 10);
        method.visitJumpInsn(Opcodes.IFNULL, pass);
        method.visitVarInsn(Opcodes.ALOAD, 10);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "getItem", "()Lnet/minecraft/item/Item;", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "foreign/proof/Items", "selector", "Lnet/minecraft/item/Item;");
        method.visitJumpInsn(Opcodes.IF_ACMPNE, pass);
        metadata(method);
        method.visitInsn(Opcodes.ICONST_3);
        method.visitInsn(Opcodes.IAND);
        method.visitVarInsn(Opcodes.ISTORE, 11);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.IDIV);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.ISHL);
        method.visitVarInsn(Opcodes.ISTORE, 12);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        for (int local = 2; local <= 4; local++) method.visitVarInsn(Opcodes.ILOAD, local);
        method.visitVarInsn(Opcodes.ILOAD, 12);
        method.visitVarInsn(Opcodes.ILOAD, 11);
        method.visitInsn(Opcodes.ICONST_3);
        method.visitInsn(Opcodes.IAND);
        method.visitInsn(Opcodes.ICONST_3);
        method.visitJumpInsn(Opcodes.IF_ICMPGE, wrap);
        method.visitVarInsn(Opcodes.ILOAD, 11);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD);
        method.visitJumpInsn(Opcodes.GOTO, join);
        method.visitLabel(wrap);
        method.visitVarInsn(Opcodes.ILOAD, 11);
        method.visitInsn(Opcodes.ICONST_3);
        method.visitInsn(Opcodes.ISUB);
        method.visitLabel(join);
        method.visitInsn(Opcodes.IOR);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "setBlockMetadataWithNotify", "(IIIII)Z", false);
        method.visitInsn(Opcodes.POP);
        boolReturn(method, true);
        method.visitLabel(pass);
        boolReturn(method, false);
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void emptyLookupSwitchMayUseItsProvenDefault() {
        MethodNode method = method();
        Label fallback = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitLookupSwitchInsn(fallback, new int[0], new Label[0]);
        method.visitLabel(fallback);
        boolReturn(method, true);
        assertTrue(compile(method).evaluate(5));
    }

    @Test void sidecarProgramsAreValidatedEvenWithoutSourceCompiler() {
        assertThrows(IllegalArgumentException.class, () -> program(List.of()));
        assertThrows(IllegalArgumentException.class, () -> program(List.of(ins(Op.IRETURN, 0))));
        assertThrows(IllegalArgumentException.class, () -> program(List.of(ins(Op.LOAD_INT, 10), ins(Op.IRETURN, 0))));
        assertThrows(IllegalArgumentException.class, () -> program(List.of(ins(Op.CONST_INT, 1))));
        var tooLong = new ArrayList<LegacyBlockActivationCompiler.Instruction>();
        for (int i = 0; i < 97; i++) tooLong.add(ins(Op.NOP, 0));
        assertThrows(IllegalArgumentException.class, () -> program(tooLong));
    }

    @Test void sidecarInvalidBranchAndSwitchShapesAreRejected() {
        var one = ins(Op.CONST_INT, 1);
        var ret = ins(Op.IRETURN, 0);
        assertThrows(IllegalArgumentException.class, () -> program(List.of(new LegacyBlockActivationCompiler.Instruction(Op.GOTO, 0, 0, null, null))));
        assertThrows(IllegalArgumentException.class, () -> program(List.of(one, new LegacyBlockActivationCompiler.Instruction(Op.IFEQ, 0, 99, null, null), one, ret)));
        for (List<Integer> keys : List.of(List.of(1), List.of(1, 1), List.of(2, 1))) {
            assertThrows(IllegalArgumentException.class, () -> program(List.of(one,
                    new LegacyBlockActivationCompiler.Instruction(Op.LOOKUP_SWITCH, 0, 2, keys, List.of(2, 2)), one, ret)));
        }
        assertThrows(IllegalArgumentException.class, () -> program(List.of(one,
                new LegacyBlockActivationCompiler.Instruction(Op.TABLE_SWITCH, 0, 2, List.of(1, 3), List.of(2, 2)), one, ret)));
    }

    @Test void sidecarCannotOverwriteInputsOrReadForbiddenLocals() {
        for (int local : new int[]{-1, 0, 5, 7, 9, 256}) {
            assertThrows(IllegalArgumentException.class, () -> program(List.of(ins(Op.LOAD_INT, local), ins(Op.IRETURN, 0))));
        }
        assertThrows(IllegalArgumentException.class, () -> program(List.of(ins(Op.CONST_INT, 1), ins(Op.STORE_INT, 6), ins(Op.CONST_INT, 1), ins(Op.IRETURN, 0))));
    }

    @Test void oneUnsafeRegisteredCallbackDoesNotDiscardSafeSibling() throws Exception {
        Path jar = tempDir.resolve("proof.jar");
        MethodNode safe = method();
        boolReturn(safe, true);
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/proof/Good.class", block("foreign/proof/Good", safe));
            put(out, "foreign/proof/Bad.class", block("foreign/proof/Bad", division()));
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/proof/Bootstrap", null, "java/lang/Object", null);
            MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit", "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
            init.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true).visitEnd();
            init.visitCode();
            for (String name : List.of("Good", "Bad")) {
                String owner = "foreign/proof/" + name;
                init.visitTypeInsn(Opcodes.NEW, owner);
                init.visitInsn(Opcodes.DUP);
                init.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
                init.visitLdcInsn(name.toLowerCase(java.util.Locale.ROOT));
                init.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock", "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
            }
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();
            writer.visitEnd();
            put(out, "foreign/proof/Bootstrap.class", writer.toByteArray());
        }
        var analysis = new LegacyBlockActivationCompiler().compile(jar);
        assertEquals(2, analysis.activationCallbacks());
        assertEquals(1, analysis.programs().size());
        assertEquals("good", analysis.programs().getFirst().registryName());
        assertTrue(analysis.diagnostics().stream().anyMatch(value -> value.contains("foreign/proof/Bad") && value.contains("activation input proof failed")));
    }

    private static MethodNode method() {
        var method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "onBlockActivated", DESC, null, null);
        method.maxLocals = 12;
        method.maxStack = 8;
        return method;
    }

    private static MethodNode division() {
        MethodNode method = method();
        method.visitInsn(Opcodes.ICONST_1);
        metadata(method);
        method.visitInsn(Opcodes.IDIV);
        method.visitInsn(Opcodes.IRETURN);
        return method;
    }

    private static void metadata(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        for (int local = 2; local <= 4; local++) method.visitVarInsn(Opcodes.ILOAD, local);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72805_g", "(III)I", false);
    }

    private static void clientSide(MethodVisitor method) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/World", "field_72995_K", "Z");
    }

    private static void boolReturn(MethodVisitor method, boolean value) {
        method.visitInsn(value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
    }

    private static LegacyBlockActivationCompiler.Program compile(MethodNode method) {
        return program(LegacyBlockActivationCompiler.compileMethod(method));
    }

    private static LegacyBlockActivationCompiler.Program program(List<LegacyBlockActivationCompiler.Instruction> code) {
        return new LegacyBlockActivationCompiler.Program("gate", "foreign", "foreign/proof/Gate", "foreign/proof/Gate", "onBlockActivated", DESC, code);
    }

    private static LegacyBlockActivationCompiler.Instruction ins(Op op, int operand) {
        return new LegacyBlockActivationCompiler.Instruction(op, operand, -1, null, null);
    }

    private static byte[] block(String owner, MethodNode callback) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>", "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        callback.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
