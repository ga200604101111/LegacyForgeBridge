package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBlockActivationEffectCompilerTest {
    static final String OWNER = "foreign/rotation/Items";
    static final LegacyBlockActivationEffectCompiler.FieldKey FIELD =
            new LegacyBlockActivationEffectCompiler.FieldKey(OWNER, "tool", "Lnet/minecraft/item/Item;");
    static final Map<LegacyBlockActivationEffectCompiler.FieldKey, String> BINDINGS = Map.of(FIELD, "foreign:tool");

    @Test void unrelatedNamespacePreservesAll1152Outcomes() {
        var plan = compile(fixture(false, true));
        assertEquals(1152, plan.outcomes().size());
        for (int side = 0; side < 6; side++) for (int meta = 0; meta < 16; meta++)
            for (int flags = 0; flags < 4; flags++) for (int held = 0; held < 3; held++) {
                var decision = plan.evaluate(side, meta, (flags & 1) != 0, (flags & 2) != 0, held);
                assertEquals(held == 2, decision.handled());
                assertEquals(held == 2 ? ((side / 2) << 2) | ((meta + 1) & 3) : -1, decision.metadata());
            }
    }

    @Test void serverGuardSuppressesClientPredictionWithoutChangingReturn() {
        var plan = compile(fixture(true, true));
        assertEquals(new LegacyBlockActivationEffectPlan.Decision(true, -1), plan.evaluate(2, 3, true, false, 2));
        assertEquals(new LegacyBlockActivationEffectPlan.Decision(true, 4), plan.evaluate(2, 3, false, false, 2));
    }

    @Test void falseReturnDoesNotEraseAnExistingSourceEffect() {
        var plan = compile(fixture(false, false));
        var decision = plan.evaluate(4, 3, false, false, 2);
        assertFalse(decision.handled());
        assertEquals(8, decision.metadata());
    }

    @Test void missingHeldIdentityFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> new LegacyBlockActivationEffectCompiler().compile(fixture(false, true), Map.of()));
    }

    @Test void unknownCallInUnreachableTailStillRejectsWholeCallback() {
        var method = fixture(false, true);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "foreign/Effects", "hidden", "()V", false);
        method.visitInsn(Opcodes.ICONST_0); method.visitInsn(Opcodes.IRETURN);
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void setterReturnValueCannotBeGuessed() {
        var method = fixture(false, true);
        method.instructions.remove(setter(method).getNext());
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void notificationFlagsOtherThanTwoAreRejected() {
        for (int flag : new int[]{0, 1, 3, 4}) {
            var method = fixture(false, true);
            method.instructions.set(setter(method).getPrevious(), new InsnNode(Opcodes.ICONST_0 + flag));
            assertThrows(IllegalArgumentException.class, () -> compile(method));
        }
    }

    @Test void changedReadCoordinatesAreRejectedRatherThanMappedToCurrentBlock() {
        var method = fixture(false, true);
        for (AbstractInsnNode node : method.instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("getBlockMetadata")) {
                AbstractInsnNode x = call.getPrevious().getPrevious().getPrevious();
                InsnList offset = new InsnList(); offset.add(new InsnNode(Opcodes.ICONST_1)); offset.add(new InsnNode(Opcodes.IADD));
                method.instructions.insert(x, offset); break;
            }
        }
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void readAfterWriteRequiresAnExplicitTransactionalAdapter() {
        var method = fixture(false, true);
        var after = setter(method).getNext();
        var read = new InsnList();
        read.add(new VarInsnNode(Opcodes.ALOAD, 1));
        for (int i = 2; i <= 4; i++) read.add(new VarInsnNode(Opcodes.ILOAD, i));
        read.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "getBlockMetadata", "(III)I", false));
        read.add(new InsnNode(Opcodes.POP));
        method.instructions.insert(after, read);
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void unguardedEmptyStackDereferenceIsRejectedForTheEmptyInput() {
        var method = fixture(false, true);
        for (AbstractInsnNode node : method.instructions) if (node.getOpcode() == Opcodes.IFNULL) {
            method.instructions.set(node, new InsnNode(Opcodes.POP)); break;
        }
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void outOfRangeMetadataIsRejectedNotClamped() {
        var method = fixture(false, true);
        for (AbstractInsnNode node : method.instructions) if (node.getOpcode() == Opcodes.IOR) {
            InsnList extra = new InsnList(); extra.add(new IntInsnNode(Opcodes.BIPUSH, 16)); extra.add(new InsnNode(Opcodes.IADD));
            method.instructions.insert(node, extra); break;
        }
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void multipleSetterInstructionsAreRejectedEvenWhenOneIsUnreachable() {
        var method = fixture(false, true);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "setBlockMetadataWithNotify", "(IIIII)Z", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void sourceAccessDescriptorAndExceptionHandlersAreChecked() {
        for (int access : new int[]{Opcodes.ACC_STATIC, Opcodes.ACC_SYNCHRONIZED, Opcodes.ACC_NATIVE, Opcodes.ACC_ABSTRACT}) {
            var method = fixture(false, true); method.access |= access;
            assertThrows(IllegalArgumentException.class, () -> compile(method));
        }
        var method = fixture(false, true); method.desc = "()Z";
        assertThrows(IllegalArgumentException.class, () -> compile(method));
        var caught = fixture(false, true);
        caught.tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(), new LabelNode(), new LabelNode(), null));
        assertThrows(IllegalArgumentException.class, () -> compile(caught));
    }

    @Test void instructionBudgetRejectsOversizedMethod() {
        var method = fixture(false, true);
        for (int i = 0; i < 97; i++) method.instructions.add(new InsnNode(Opcodes.NOP));
        assertThrows(IllegalArgumentException.class, () -> compile(method));
    }

    @Test void sidecarTableRequiresCompleteGuardedValidOutcomes() {
        var plan = compile(fixture(false, true));
        assertThrows(IllegalArgumentException.class, () -> new LegacyBlockActivationEffectPlan("foreign:tool", List.of(3)));
        var invalid = new ArrayList<>(plan.outcomes()); invalid.set(2, 34);
        assertThrows(IllegalArgumentException.class, () -> new LegacyBlockActivationEffectPlan("foreign:tool", invalid));
        var unguarded = new ArrayList<>(plan.outcomes()); unguarded.set(0, 3);
        assertThrows(IllegalArgumentException.class, () -> new LegacyBlockActivationEffectPlan("foreign:tool", unguarded));
        assertThrows(IllegalArgumentException.class, () -> new LegacyBlockActivationEffectPlan("foreign:tool", Collections.nCopies(1152, 0)));
        assertThrows(IllegalArgumentException.class, () -> plan.evaluate(6, 0, false, false, 2));
    }

    static LegacyBlockActivationEffectPlan compile(MethodNode method) {
        return new LegacyBlockActivationEffectCompiler().compile(method, BINDINGS);
    }
    private static MethodInsnNode setter(MethodNode method) {
        for (AbstractInsnNode node : method.instructions) if (node instanceof MethodInsnNode call && call.name.equals("setBlockMetadataWithNotify")) return call;
        throw new AssertionError();
    }

    /** Builds unrelated source bytecode, not a production name/registry lookup shortcut. */
    static MethodNode fixture(boolean serverGuard, boolean handled) {
        var m = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "onBlockActivated", LegacyBlockActivationEffectCompiler.DESCRIPTOR, null, null);
        m.maxLocals = 13; m.maxStack = 8;
        Label pass = new Label(), wrap = new Label(), join = new Label(), done = new Label();
        m.visitVarInsn(Opcodes.ALOAD, 5);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "getCurrentEquippedItem", "()Lnet/minecraft/item/ItemStack;", false);
        m.visitVarInsn(Opcodes.ASTORE, 10); m.visitVarInsn(Opcodes.ALOAD, 10); m.visitJumpInsn(Opcodes.IFNULL, pass);
        m.visitVarInsn(Opcodes.ALOAD, 10);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "getItem", "()Lnet/minecraft/item/Item;", false);
        m.visitFieldInsn(Opcodes.GETSTATIC, OWNER, "tool", "Lnet/minecraft/item/Item;");
        m.visitJumpInsn(Opcodes.IF_ACMPNE, pass);
        if (serverGuard) {
            m.visitVarInsn(Opcodes.ALOAD, 1);
            m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/World", "isRemote", "Z");
            m.visitJumpInsn(Opcodes.IFNE, done);
        }
        m.visitVarInsn(Opcodes.ALOAD, 1);
        for (int i = 2; i <= 4; i++) m.visitVarInsn(Opcodes.ILOAD, i);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "getBlockMetadata", "(III)I", false);
        m.visitInsn(Opcodes.ICONST_3); m.visitInsn(Opcodes.IAND); m.visitVarInsn(Opcodes.ISTORE, 11);
        m.visitVarInsn(Opcodes.ILOAD, 6); m.visitInsn(Opcodes.ICONST_2); m.visitInsn(Opcodes.IDIV);
        m.visitInsn(Opcodes.ICONST_2); m.visitInsn(Opcodes.ISHL); m.visitVarInsn(Opcodes.ISTORE, 12);
        m.visitVarInsn(Opcodes.ALOAD, 1);
        for (int i = 2; i <= 4; i++) m.visitVarInsn(Opcodes.ILOAD, i);
        m.visitVarInsn(Opcodes.ILOAD, 12); m.visitVarInsn(Opcodes.ILOAD, 11);
        m.visitInsn(Opcodes.ICONST_3); m.visitInsn(Opcodes.IAND); m.visitInsn(Opcodes.ICONST_3);
        m.visitJumpInsn(Opcodes.IF_ICMPGE, wrap);
        m.visitVarInsn(Opcodes.ILOAD, 11); m.visitInsn(Opcodes.ICONST_1); m.visitInsn(Opcodes.IADD); m.visitJumpInsn(Opcodes.GOTO, join);
        m.visitLabel(wrap); m.visitVarInsn(Opcodes.ILOAD, 11); m.visitInsn(Opcodes.ICONST_3); m.visitInsn(Opcodes.ISUB);
        m.visitLabel(join); m.visitInsn(Opcodes.IOR); m.visitInsn(Opcodes.ICONST_2);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "setBlockMetadataWithNotify", "(IIIII)Z", false);
        m.visitInsn(Opcodes.POP); m.visitLabel(done);
        m.visitInsn(handled ? Opcodes.ICONST_1 : Opcodes.ICONST_0); m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(pass); m.visitInsn(Opcodes.ICONST_0); m.visitInsn(Opcodes.IRETURN);
        return m;
    }
}
