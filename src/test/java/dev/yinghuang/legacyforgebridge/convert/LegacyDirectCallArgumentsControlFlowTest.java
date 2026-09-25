package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.LinkedHashMap;
import java.util.Map;

class LegacyDirectCallArgumentsControlFlowTest {
    private static final String DESC = "(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V";
    private record Fixture(ClassNode owner, MethodNode method, MethodInsnNode call) { }

    @Test
    void canonicalShapeDoesNotNeedMaxStackAndMetadataLabelsAreHarmless() {
        Fixture f = fixture(false);
        f.method().maxStack = 0; f.method().maxLocals = 0;
        var result = LegacyDirectCallArguments.classStringNew(f.owner(), f.method(), f.call());
        check(result != null && result.classInternalName().equals("foreign/Tile")
                && result.stringValue().equals("tile") && result.newTypeInternalName().equals("foreign/Renderer"), "canonical metadata-only labels");
    }

    @Test
    void branchIntoArgumentSequenceCannotBorrowTheFallthroughClassLiteral() {
        Fixture f = fixture(true);
        check(LegacyDirectCallArguments.classStringNew(f.owner(), f.method(), f.call()) == null, "merged class literal must remain unresolved");
    }

    @Test
    void unrelatedDescriptorDoesNotMatchThreeArgumentProof() {
        Fixture f = fixture(false); f.call().desc = "(Ljava/lang/Object;)V";
        check(LegacyDirectCallArguments.classStringNew(f.owner(), f.method(), f.call()) == null, "wrong descriptor");
    }

    @Test
    void invocationMustBelongToTheSuppliedMethod() {
        Fixture f = fixture(false);
        MethodNode unrelated = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "other", "()V", null, null);
        check(LegacyDirectCallArguments.classStringNew(f.owner(), unrelated, f.call()) == null, "detached invocation");
    }

    @Test
    void consecutiveRegistrationsKeepTheirOwnArguments() {
        Fixture f = fixture(false);
        var first = LegacyDirectCallArguments.classStringNew(f.owner(), f.method(), f.call());
        f.method().instructions.remove(f.method().instructions.getLast());
        f.method().instructions.add(new LdcInsnNode(Type.getObjectType("foreign/OtherTile")));
        f.method().instructions.add(new LdcInsnNode("other"));
        f.method().instructions.add(new TypeInsnNode(Opcodes.NEW, "foreign/OtherRenderer"));
        f.method().instructions.add(new InsnNode(Opcodes.DUP));
        f.method().instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "foreign/OtherRenderer", "<init>", "()V", false));
        MethodInsnNode second = call(); f.method().instructions.add(second); f.method().instructions.add(new InsnNode(Opcodes.RETURN));
        var result = LegacyDirectCallArguments.classStringNew(f.owner(), f.method(), second);
        check(first != null && result != null && result.classInternalName().equals("foreign/OtherTile")
                && result.stringValue().equals("other") && result.newTypeInternalName().equals("foreign/OtherRenderer"), "cross-call contamination");
    }

    @Test
    void unresolvedRegistrationCannotBeIgnoredWhenCheckingUniqueness() throws Exception {
        Fixture f = fixture(false);
        f.method().instructions.remove(f.method().instructions.getLast());
        f.method().instructions.add(new LdcInsnNode(Type.getObjectType("foreign/Tile")));
        f.method().instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "foreign/Ids", "dynamic", "Ljava/lang/String;"));
        f.method().instructions.add(new TypeInsnNode(Opcodes.NEW, "foreign/Renderer"));
        f.method().instructions.add(new InsnNode(Opcodes.DUP));
        f.method().instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "foreign/Renderer", "<init>", "()V", false));
        f.method().instructions.add(call()); f.method().instructions.add(new InsnNode(Opcodes.RETURN));
        ClassNode renderer = new ClassNode(Opcodes.ASM9); renderer.name = "foreign/Renderer";
        renderer.superName = "net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
        Map<String, ClassNode> classes = new LinkedHashMap<>(); classes.put(f.owner().name, f.owner()); classes.put(renderer.name, renderer);
        var method = LegacyOscillatingModelPresentationAnalyzer.class.getDeclaredMethod("registration", Map.class, String.class);
        method.setAccessible(true);
        check(method.invoke(null, classes, "foreign/Tile") == null, "unknown later registration might replace the renderer");
        var processor = LegacySingleInputProcessorPresentationAnalyzer.class.getDeclaredMethod("findRendererRegistration", Map.class, String.class, String.class);
        processor.setAccessible(true);
        check(processor.invoke(null, classes, "foreign/Tile", "tile") == null, "processor must also reject unknown registration");
    }

    private static Fixture fixture(boolean branch) {
        ClassNode owner = new ClassNode(Opcodes.ASM9); owner.name = "foreign/Client";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bind", "(Z)V", null, null);
        owner.methods.add(method); method.maxStack = 5; method.maxLocals = 1;
        LabelNode normal = new LabelNode(), joined = new LabelNode();
        if (branch) {
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
            method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, normal));
            method.instructions.add(new LdcInsnNode(Type.getObjectType("foreign/OtherTile")));
            method.instructions.add(new JumpInsnNode(Opcodes.GOTO, joined)); method.instructions.add(normal);
        }
        method.instructions.add(new LdcInsnNode(Type.getObjectType("foreign/Tile")));
        method.instructions.add(joined);
        method.instructions.add(new LdcInsnNode("tile"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "foreign/Renderer"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "foreign/Renderer", "<init>", "()V", false));
        MethodInsnNode call = call(); method.instructions.add(call); method.instructions.add(new InsnNode(Opcodes.RETURN));
        return new Fixture(owner, method, call);
    }

    private static MethodInsnNode call() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, "cpw/mods/fml/client/registry/ClientRegistry", "registerTileEntity", DESC, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
