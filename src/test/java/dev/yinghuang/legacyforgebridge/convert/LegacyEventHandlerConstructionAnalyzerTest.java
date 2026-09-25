package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static dev.yinghuang.legacyforgebridge.convert.LegacyEventHandlerConstructionAnalyzer.Strategy.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyEventHandlerConstructionAnalyzerTest {
    private final LegacyEventHandlerConstructionAnalyzer analyzer = new LegacyEventHandlerConstructionAnalyzer();

    @Test void exactForgeSelfRegisterConstructorCanBeSynthesizedWithoutBusSideEffect() {
        ClassNode node = type("unrelated/events/Listener");
        MethodNode ctor = ctor(node);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lcpw/mods/fml/common/eventhandler/EventBus;"));
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus",
                "register", "(Ljava/lang/Object;)V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));

        var plan = analyzer.analyze(node, "()V");
        assertEquals(SYNTHESIZE_PARENT_ONLY, plan.strategy());
        assertEquals(LegacyEventAnalyzer.Bus.FORGE, plan.bus());
        assertEquals("java/lang/Object", plan.parentClass());
    }

    @Test void exactFmlSelfRegisterConstructorCanBeSynthesizedWithoutBusSideEffect() {
        ClassNode node = type("different/events/FmlListener");
        MethodNode ctor = ctor(node);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/FMLCommonHandler", "instance",
                "()Lcpw/mods/fml/common/FMLCommonHandler;", false));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/FMLCommonHandler", "bus",
                "()Lcpw/mods/fml/common/eventhandler/EventBus;", false));
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus",
                "register", "(Ljava/lang/Object;)V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));

        var plan = analyzer.analyze(node, "()V");
        assertEquals(SYNTHESIZE_PARENT_ONLY, plan.strategy());
        assertEquals(LegacyEventAnalyzer.Bus.FML, plan.bus());
    }

    @Test void fieldInitializationMixedWithSelfRegistrationIsNotSilentlyDropped() {
        ClassNode node = type("other/events/StatefulListener");
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "value", "I", null, null));
        MethodNode ctor = ctor(node);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new InsnNode(Opcodes.ICONST_1));
        ctor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "value", "I"));
        ctor.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lcpw/mods/fml/common/eventhandler/EventBus;"));
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus",
                "register", "(Ljava/lang/Object;)V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));

        var plan = analyzer.analyze(node, "()V");
        assertEquals(UNSUPPORTED_SELF_REGISTER, plan.strategy());
        assertTrue(plan.diagnostic().contains("additional initialization"), plan.diagnostic());
    }

    @Test void ordinaryConstructorRemainsSourceValidated() {
        ClassNode node = type("plain/events/Listener");
        MethodNode ctor = ctor(node);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));
        assertEquals(SOURCE_CONSTRUCTOR, analyzer.analyze(node, "()V").strategy());
    }

    private static ClassNode type(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_7;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        return node;
    }

    private static MethodNode ctor(ClassNode owner) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        owner.methods.add(method);
        return method;
    }
}
