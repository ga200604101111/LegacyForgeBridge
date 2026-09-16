package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyGridPotProofsTest {
    private static final String TILE = "foreign/grid/Tile";
    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";
    private static final String DIRECTION = "net/minecraftforge/common/util/ForgeDirection";

    @Test
    void unrelatedNineCellTileAndLegacyRenderPredicateAreProvenStructurally() {
        ClassNode tile = tile(9);
        var shape = LegacyGridPotProofs.tileShape(tile);
        assertNotNull(shape);
        assertNotNull(shape.slotMapper());
        assertNotNull(shape.enableCell());
        assertNotNull(shape.removeCell());
        assertNotNull(shape.removeItem());
        assertNotNull(shape.dropAll());
        assertTrue(LegacyGridPotProofs.canonicalContentInsertionPredicate(insertionPredicate()));
    }

    @Test
    void changedCellCountFailsClosed() {
        assertNull(LegacyGridPotProofs.tileShape(tile(8)));
    }

    private static ClassNode tile(int size) {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.name = TILE;
        owner.superName = "net/minecraft/tileentity/TileEntity";
        owner.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "enabled", "[Z", null, null));
        owner.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "items", "[L" + ITEM_STACK + ";", null, null));

        MethodNode ctor = method(owner, Opcodes.ACC_PUBLIC, "<init>", "()V");
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner.superName, "<init>", "()V", false));
        allocate(ctor, "enabled", "[Z", size, Opcodes.T_BOOLEAN);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        push(ctor, size);
        ctor.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, ITEM_STACK));
        ctor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TILE, "items", "[L" + ITEM_STACK + ";"));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));

        MethodNode canUpdate = method(owner, Opcodes.ACC_PUBLIC, "canUpdate", "()Z");
        canUpdate.instructions.add(new InsnNode(Opcodes.ICONST_0));
        canUpdate.instructions.add(new InsnNode(Opcodes.IRETURN));

        MethodNode slot = method(owner, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "slot", "(FFL" + DIRECTION + ";)I");
        slot.instructions.add(new VarInsnNode(Opcodes.FLOAD, 0));
        slot.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        slot.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, DIRECTION, "offsetX", "I"));
        slot.instructions.add(new InsnNode(Opcodes.I2F));
        slot.instructions.add(new InsnNode(Opcodes.FMUL));
        slot.instructions.add(new InsnNode(Opcodes.F2I));
        slot.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
        slot.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        slot.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, DIRECTION, "offsetZ", "I"));
        slot.instructions.add(new InsnNode(Opcodes.I2F));
        slot.instructions.add(new InsnNode(Opcodes.FMUL));
        slot.instructions.add(new InsnNode(Opcodes.F2I));
        push(slot, 3); slot.instructions.add(new InsnNode(Opcodes.IMUL));
        slot.instructions.add(new InsnNode(Opcodes.IADD));
        push(slot, 9); slot.instructions.add(new InsnNode(Opcodes.POP));
        push(slot, 8); slot.instructions.add(new InsnNode(Opcodes.POP));
        slot.instructions.add(new InsnNode(Opcodes.IRETURN));

        MethodNode enable = method(owner, Opcodes.ACC_PUBLIC, "enable", "(I)V");
        array(enable, "enabled", Opcodes.BASTORE, 1);
        enable.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        enable.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TILE, "func_70296_d", "()V", false));
        enable.instructions.add(new InsnNode(Opcodes.RETURN));

        MethodNode removeItem = method(owner, Opcodes.ACC_PUBLIC, "removeItem", "(I)L" + ITEM_STACK + ";");
        removeItem.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        removeItem.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TILE, "items", "[L" + ITEM_STACK + ";"));
        removeItem.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        removeItem.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        removeItem.instructions.add(new InsnNode(Opcodes.AASTORE));
        removeItem.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        removeItem.instructions.add(new InsnNode(Opcodes.ARETURN));

        MethodNode removeCell = method(owner, Opcodes.ACC_PUBLIC, "removeCell", "(I)L" + ITEM_STACK + ";");
        array(removeCell, "enabled", Opcodes.BASTORE, 0);
        removeCell.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        removeCell.instructions.add(new InsnNode(Opcodes.ARETURN));

        MethodNode item = method(owner, Opcodes.ACC_PUBLIC, "item", "(I)Lnet/minecraft/item/Item;");
        item.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        item.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ITEM_STACK, "func_77973_b", "()Lnet/minecraft/item/Item;", false));
        item.instructions.add(new InsnNode(Opcodes.ARETURN));

        MethodNode meta = method(owner, Opcodes.ACC_PUBLIC, "meta", "(I)I");
        meta.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        meta.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ITEM_STACK, "func_77960_j", "()I", false));
        meta.instructions.add(new InsnNode(Opcodes.IRETURN));

        MethodNode enabled = method(owner, Opcodes.ACC_PUBLIC, "enabled", "(I)Z");
        read(enabled, "enabled", Opcodes.BALOAD);
        enabled.instructions.add(new InsnNode(Opcodes.IRETURN));

        MethodNode dropAll = method(owner, Opcodes.ACC_PUBLIC, "dropAll", "(Lnet/minecraft/world/World;)V");
        read(dropAll, "enabled", Opcodes.BALOAD); dropAll.instructions.add(new InsnNode(Opcodes.POP));
        read(dropAll, "items", Opcodes.AALOAD); dropAll.instructions.add(new InsnNode(Opcodes.POP));
        push(dropAll, 9); dropAll.instructions.add(new InsnNode(Opcodes.POP));
        dropAll.instructions.add(new TypeInsnNode(Opcodes.NEW, ITEM_STACK)); dropAll.instructions.add(new InsnNode(Opcodes.POP));
        dropAll.instructions.add(new InsnNode(Opcodes.RETURN));

        MethodNode empty = method(owner, Opcodes.ACC_PUBLIC, "empty", "()Z");
        read(empty, "enabled", Opcodes.BALOAD); empty.instructions.add(new InsnNode(Opcodes.POP));
        push(empty, 9); empty.instructions.add(new InsnNode(Opcodes.POP));
        empty.instructions.add(new InsnNode(Opcodes.ICONST_0)); empty.instructions.add(new InsnNode(Opcodes.IRETURN));
        empty.instructions.add(new InsnNode(Opcodes.ICONST_1)); empty.instructions.add(new InsnNode(Opcodes.IRETURN));
        return owner;
    }

    private static MethodNode insertionPredicate() {
        ClassNode owner = new ClassNode(Opcodes.ASM9); owner.name = "foreign/grid/Block";
        MethodNode method = method(owner, Opcodes.ACC_PUBLIC, "use", "()V");
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/block/Block", "func_149634_a",
                "(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;", false));
        for (int value : new int[]{1, 13, 40, 40}) {
            method.instructions.add(new InsnNode(Opcodes.DUP));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/block/Block", "func_149645_b", "()I", false));
            push(method, value); method.instructions.add(new InsnNode(Opcodes.POP2));
        }
        method.instructions.add(new InsnNode(Opcodes.POP)); method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static MethodNode method(ClassNode owner, int access, String name, String desc) {
        MethodNode method = new MethodNode(Opcodes.ASM9, access, name, desc, null, null); owner.methods.add(method); return method;
    }
    private static void allocate(MethodNode method, String field, String desc, int size, int type) {
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); push(method, size);
        method.instructions.add(new IntInsnNode(Opcodes.NEWARRAY, type)); method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TILE, field, desc));
    }
    private static void array(MethodNode method, String field, int opcode, int value) {
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TILE, field, "[Z"));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1)); push(method, value); method.instructions.add(new InsnNode(opcode));
    }
    private static void read(MethodNode method, String field, int opcode) {
        String desc = field.equals("items") ? "[L" + ITEM_STACK + ";" : "[Z";
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TILE, field, desc));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0)); method.instructions.add(new InsnNode(opcode));
    }
    private static void push(MethodNode method, int value) {
        if (value >= -1 && value <= 5) method.instructions.add(new InsnNode(Opcodes.ICONST_0 + value));
        else method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, value));
    }
}
