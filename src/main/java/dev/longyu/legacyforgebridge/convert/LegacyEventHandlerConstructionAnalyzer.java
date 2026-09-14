package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves whether a source event listener can be instantiated without replaying
 * legacy FML/Forge bus registration on the modern client.
 *
 * <p>This analyzer never loads source classes. It recognizes only exact,
 * source-independent bytecode shapes. A normal constructor is left to the
 * behavior compiler's existing API validation. A constructor whose sole extra
 * effect is self-registration may be replaced by a synthesized constructor
 * that invokes only the proven parent constructor. Constructors that initialize
 * fields or perform any other work in addition to self-registration are not
 * stripped here; those require a later statement-level rewrite or an existing
 * generated instance binding.</p>
 */
public final class LegacyEventHandlerConstructionAnalyzer {
    private static final String EVENT_BUS = "cpw/mods/fml/common/eventhandler/EventBus";
    private static final String MINECRAFT_FORGE = "net/minecraftforge/common/MinecraftForge";
    private static final String FML_COMMON_HANDLER = "cpw/mods/fml/common/FMLCommonHandler";

    public enum Strategy {
        /** No self-registration was found; existing source-constructor validation applies. */
        SOURCE_CONSTRUCTOR,
        /** Exact parent-constructor + self-register + return shape; registration may be removed. */
        SYNTHESIZE_PARENT_ONLY,
        /** Registration is mixed with state initialization or other effects; fail closed. */
        UNSUPPORTED_SELF_REGISTER
    }

    public record Plan(
            String handlerClass,
            String parentClass,
            String constructorDescriptor,
            Strategy strategy,
            LegacyEventAnalyzer.Bus bus,
            String diagnostic
    ) { }

    public Plan analyze(Path sourceJar, String handlerClass, String constructorDescriptor) throws IOException {
        try (JarFile jar = new JarFile(sourceJar.toFile())) {
            JarEntry entry = jar.getJarEntry(handlerClass + ".class");
            if (entry == null) {
                return new Plan(handlerClass, null, constructorDescriptor, Strategy.UNSUPPORTED_SELF_REGISTER,
                        LegacyEventAnalyzer.Bus.UNKNOWN, "Handler class is absent from source JAR");
            }
            try (InputStream input = jar.getInputStream(entry)) {
                ClassNode node = new ClassNode(Opcodes.ASM9);
                new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return analyze(node, constructorDescriptor);
            }
        }
    }

    Plan analyze(ClassNode node, String constructorDescriptor) {
        MethodNode constructor = null;
        for (MethodNode method : node.methods) {
            if (method.name.equals("<init>") && method.desc.equals(constructorDescriptor)) {
                constructor = method;
                break;
            }
        }
        if (constructor == null) {
            return new Plan(node.name, node.superName, constructorDescriptor, Strategy.UNSUPPORTED_SELF_REGISTER,
                    LegacyEventAnalyzer.Bus.UNKNOWN, "Constructor not found");
        }

        List<AbstractInsnNode> instructions = opcodes(constructor.instructions);
        List<Integer> registrations = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            if (isRegister(instructions.get(index))) registrations.add(index);
        }
        if (registrations.isEmpty()) {
            return new Plan(node.name, node.superName, constructorDescriptor, Strategy.SOURCE_CONSTRUCTOR,
                    LegacyEventAnalyzer.Bus.UNKNOWN, "");
        }
        if (registrations.size() != 1 || !constructorDescriptor.equals("()V")) {
            return unsupported(node, constructorDescriptor, "Self-registration is not a single no-arg constructor site");
        }

        int registerIndex = registrations.getFirst();
        LegacyEventAnalyzer.Bus bus = registrationBus(instructions, registerIndex);
        if (bus == LegacyEventAnalyzer.Bus.UNKNOWN) {
            return unsupported(node, constructorDescriptor, "Self-registration bus provenance is not proven");
        }
        if (!registersThis(instructions, registerIndex)) {
            return unsupported(node, constructorDescriptor, "EventBus.register argument is not the constructor receiver");
        }

        int parentCall = findParentConstructorCall(instructions, node.superName);
        if (parentCall < 0) {
            return unsupported(node, constructorDescriptor, "Parent constructor invocation is not proven");
        }

        Set<Integer> allowed = new HashSet<>();
        // aload_0; invokespecial super.<init>()V
        if (parentCall == 0 || !isAload0(instructions.get(parentCall - 1))) {
            return unsupported(node, constructorDescriptor, "Parent constructor has unsupported arguments or receiver flow");
        }
        MethodInsnNode parent = (MethodInsnNode) instructions.get(parentCall);
        if (!parent.desc.equals("()V")) {
            return unsupported(node, constructorDescriptor, "Parent constructor is not no-arg");
        }
        allowed.add(parentCall - 1);
        allowed.add(parentCall);

        if (bus == LegacyEventAnalyzer.Bus.FORGE) {
            // GETSTATIC MinecraftForge.EVENT_BUS; ALOAD 0; register
            if (registerIndex < 2 || !isAload0(instructions.get(registerIndex - 1))) {
                return unsupported(node, constructorDescriptor, "Forge self-registration argument flow is not exact");
            }
            AbstractInsnNode receiver = instructions.get(registerIndex - 2);
            if (!(receiver instanceof FieldInsnNode field)
                    || receiver.getOpcode() != Opcodes.GETSTATIC
                    || !field.owner.equals(MINECRAFT_FORGE)
                    || !field.name.equals("EVENT_BUS")
                    || !field.desc.equals("L" + EVENT_BUS + ";")) {
                return unsupported(node, constructorDescriptor, "Forge event bus receiver is not exact");
            }
            allowed.add(registerIndex - 2);
            allowed.add(registerIndex - 1);
            allowed.add(registerIndex);
        } else {
            // FMLCommonHandler.instance(); bus(); ALOAD 0; register
            if (registerIndex < 3 || !isAload0(instructions.get(registerIndex - 1))) {
                return unsupported(node, constructorDescriptor, "FML self-registration argument flow is not exact");
            }
            if (!(instructions.get(registerIndex - 2) instanceof MethodInsnNode busCall)
                    || !busCall.owner.equals(FML_COMMON_HANDLER)
                    || !busCall.name.equals("bus")
                    || !busCall.desc.equals("()L" + EVENT_BUS + ";")) {
                return unsupported(node, constructorDescriptor, "FML event bus receiver is not exact");
            }
            if (!(instructions.get(registerIndex - 3) instanceof MethodInsnNode instance)
                    || instance.getOpcode() != Opcodes.INVOKESTATIC
                    || !instance.owner.equals(FML_COMMON_HANDLER)
                    || !instance.name.equals("instance")) {
                return unsupported(node, constructorDescriptor, "FML common handler source is not exact");
            }
            allowed.add(registerIndex - 3);
            allowed.add(registerIndex - 2);
            allowed.add(registerIndex - 1);
            allowed.add(registerIndex);
        }

        int returnCount = 0;
        for (int index = 0; index < instructions.size(); index++) {
            AbstractInsnNode instruction = instructions.get(index);
            if (instruction.getOpcode() == Opcodes.RETURN) {
                allowed.add(index);
                returnCount++;
            }
        }
        if (returnCount != 1 || allowed.size() != instructions.size()) {
            return new Plan(node.name, node.superName, constructorDescriptor, Strategy.UNSUPPORTED_SELF_REGISTER,
                    bus, "Constructor mixes self-registration with additional initialization or effects");
        }

        return new Plan(node.name, node.superName, constructorDescriptor, Strategy.SYNTHESIZE_PARENT_ONLY,
                bus, "Exact self-registration-only constructor; legacy bus call can be omitted");
    }

    private static Plan unsupported(ClassNode node, String descriptor, String diagnostic) {
        return new Plan(node.name, node.superName, descriptor, Strategy.UNSUPPORTED_SELF_REGISTER,
                LegacyEventAnalyzer.Bus.UNKNOWN, diagnostic);
    }

    private static List<AbstractInsnNode> opcodes(InsnList instructions) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : instructions) {
            if (instruction.getOpcode() >= 0) result.add(instruction);
        }
        return result;
    }

    private static boolean isRegister(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode method
                && method.owner.equals(EVENT_BUS)
                && method.name.equals("register")
                && method.desc.equals("(Ljava/lang/Object;)V");
    }

    private static boolean registersThis(List<AbstractInsnNode> instructions, int registerIndex) {
        return registerIndex > 0 && isAload0(instructions.get(registerIndex - 1));
    }

    private static LegacyEventAnalyzer.Bus registrationBus(List<AbstractInsnNode> instructions, int registerIndex) {
        if (registerIndex >= 2 && instructions.get(registerIndex - 2) instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && field.owner.equals(MINECRAFT_FORGE)
                && field.name.equals("EVENT_BUS")) {
            return LegacyEventAnalyzer.Bus.FORGE;
        }
        if (registerIndex >= 2 && instructions.get(registerIndex - 2) instanceof MethodInsnNode method
                && method.owner.equals(FML_COMMON_HANDLER)
                && method.name.equals("bus")) {
            return LegacyEventAnalyzer.Bus.FML;
        }
        return LegacyEventAnalyzer.Bus.UNKNOWN;
    }

    private static int findParentConstructorCall(List<AbstractInsnNode> instructions, String parentClass) {
        for (int index = 0; index < instructions.size(); index++) {
            AbstractInsnNode instruction = instructions.get(index);
            if (instruction instanceof MethodInsnNode method
                    && method.getOpcode() == Opcodes.INVOKESPECIAL
                    && method.owner.equals(parentClass)
                    && method.name.equals("<init>")) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isAload0(AbstractInsnNode instruction) {
        return instruction instanceof VarInsnNode variable
                && variable.getOpcode() == Opcodes.ALOAD
                && variable.var == 0;
    }
}
