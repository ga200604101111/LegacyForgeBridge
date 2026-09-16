package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class LegacyGuiTileHandoffTest {
    private static final String TILE = "foreign/handoff/Tile";
    private static final String GUI = "foreign/handoff/Gui";
    private record Fixture(ClassNode owner, MethodNode method, MethodInsnNode call) { }

    @Test
    void acceptsRawCastAndLocalAliasOfTheActualWorldResult() {
        for (int mode : new int[]{0, 1, 2}) {
            Fixture f = handler(mode);
            check(LegacyGuiTileHandoff.fromWorld(f.owner(), f.method(), f.call(), TILE), "valid handoff " + mode);
        }
    }

    @Test
    void rejectsDiscardedLookupFollowedByNullArgument() {
        Fixture f = handler(3);
        check(!LegacyGuiTileHandoff.fromWorld(f.owner(), f.method(), f.call(), TILE), "lookup result was discarded");
    }

    @Test
    void rejectsCastToDifferentTile() {
        Fixture f = handler(4);
        check(!LegacyGuiTileHandoff.fromWorld(f.owner(), f.method(), f.call(), TILE), "wrong narrowing cast");
    }

    @Test
    void rejectsDifferentCoordinates() {
        Fixture f = handler(5);
        check(!LegacyGuiTileHandoff.fromWorld(f.owner(), f.method(), f.call(), TILE), "y/z were exchanged");
    }

    @Test
    void rejectsMergedWorldResultAndNull() {
        Fixture f = handler(6);
        check(!LegacyGuiTileHandoff.fromWorld(f.owner(), f.method(), f.call(), TILE), "ambiguous tile operand");
    }

    @Test
    void requiresInvocationMembershipAndHandlerDescriptor() {
        Fixture f = handler(0);
        MethodInsnNode detached = new MethodInsnNode(Opcodes.INVOKESPECIAL, GUI, "<init>", LegacyGuiTileHandoff.CONSTRUCTOR, false);
        check(!LegacyGuiTileHandoff.fromWorld(f.owner(), f.method(), detached, TILE), "detached call");
        f.method().desc = "()V";
        check(!LegacyGuiTileHandoff.fromWorld(f.owner(), f.method(), f.call(), TILE), "wrong handler descriptor");
    }

    @Test
    void acceptsConstructorArgumentNarrowingAndLocalAlias() {
        for (int mode : new int[]{0, 1}) {
            Fixture f = constructor(mode);
            check(LegacyGuiTileHandoff.narrowsToField(f.owner(), f.method(), TILE), "valid constructor " + mode);
        }
    }

    @Test
    void rejectsDecoyCastWhenNullIsStored() {
        Fixture f = constructor(2);
        check(!LegacyGuiTileHandoff.narrowsToField(f.owner(), f.method(), TILE), "decoy cast must not prove field binding");
    }

    @Test
    void rejectsNarrowingDifferentConstructorArgument() {
        Fixture f = constructor(3);
        check(!LegacyGuiTileHandoff.narrowsToField(f.owner(), f.method(), TILE), "wrong constructor argument");
    }

    @Test
    void rejectsConditionalFieldAssignment() {
        Fixture f = constructor(4);
        check(!LegacyGuiTileHandoff.narrowsToField(f.owner(), f.method(), TILE), "conditional constructor is outside admitted subset");
    }

    @Test
    void presentationAnalyzerRejectsDecoyLookupInsteadOfOnlyMatchingItsPresence() throws Exception {
        for (int mode : new int[]{0, 3}) {
            Fixture f = handler(mode);
            f.owner().interfaces.add("cpw/mods/fml/common/network/IGuiHandler");
            LabelNode hit = new LabelNode(), miss = new LabelNode();
            InsnList dispatch = new InsnList(); dispatch.add(new VarInsnNode(Opcodes.ILOAD, 1));
            dispatch.add(new TableSwitchInsnNode(7, 7, miss, hit)); dispatch.add(hit);
            f.method().instructions.insert(dispatch);
            f.method().instructions.add(miss); f.method().instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            f.method().instructions.add(new InsnNode(Opcodes.ARETURN));
            ClassNode gui = new ClassNode(Opcodes.ASM9); gui.name = GUI;
            gui.superName = "net/minecraft/client/gui/inventory/GuiContainer";
            var select = LegacySingleInputProcessorPresentationAnalyzer.class.getDeclaredMethod(
                    "findGuiClass", java.util.Map.class, int.class, String.class);
            select.setAccessible(true);
            Object result = select.invoke(null, java.util.Map.of(f.owner().name, f.owner(), GUI, gui), 7, TILE);
            check(mode == 0 ? GUI.equals(result) : result == null, "presentation integration mode=" + mode);
        }
    }

    private static Fixture handler(int mode) {
        ClassNode owner = new ClassNode(Opcodes.ASM9); owner.name = "foreign/handoff/Handler";
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "getClientGuiElement",
                "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;", null, null);
        owner.methods.add(m); m.maxStack = 10; m.maxLocals = 8;
        m.instructions.add(new TypeInsnNode(Opcodes.NEW, GUI)); m.instructions.add(new InsnNode(Opcodes.DUP));
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/entity/player/EntityPlayer", "field_71071_by",
                "Lnet/minecraft/entity/player/InventoryPlayer;"));
        LabelNode alternate = new LabelNode(), joined = new LabelNode();
        if (mode == 6) {
            m.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
            m.instructions.add(new JumpInsnNode(Opcodes.IFEQ, alternate));
        }
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, mode == 5 ? 6 : 5));
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, mode == 5 ? 5 : 6));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;", false));
        if (mode == 1 || mode == 4) m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, mode == 4 ? "foreign/handoff/OtherTile" : TILE));
        if (mode == 2) { m.instructions.add(new VarInsnNode(Opcodes.ASTORE, 7)); m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 7)); }
        if (mode == 3) { m.instructions.add(new InsnNode(Opcodes.POP)); m.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); }
        if (mode == 6) {
            m.instructions.add(new JumpInsnNode(Opcodes.GOTO, joined)); m.instructions.add(alternate);
            m.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); m.instructions.add(joined);
        }
        MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESPECIAL, GUI, "<init>", LegacyGuiTileHandoff.CONSTRUCTOR, false);
        m.instructions.add(call); m.instructions.add(new InsnNode(Opcodes.ARETURN));
        return new Fixture(owner, m, call);
    }

    private static Fixture constructor(int mode) {
        ClassNode owner = new ClassNode(Opcodes.ASM9); owner.name = GUI;
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", LegacyGuiTileHandoff.CONSTRUCTOR, null, null);
        owner.methods.add(m); m.maxStack = 4; m.maxLocals = 4;
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        LabelNode end = new LabelNode();
        if (mode == 4) {
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2)); m.instructions.add(new JumpInsnNode(Opcodes.IFNULL, end));
        }
        if (mode == 2) {
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2)); m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, TILE));
            m.instructions.add(new InsnNode(Opcodes.POP));
        }
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (mode == 2) m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        else {
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, mode == 3 ? 1 : 2));
            m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, TILE));
            if (mode == 1) { m.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3)); m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3)); }
        }
        m.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, GUI, "tile", "L" + TILE + ";"));
        m.instructions.add(end); m.instructions.add(new InsnNode(Opcodes.RETURN));
        return new Fixture(owner, m, null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
