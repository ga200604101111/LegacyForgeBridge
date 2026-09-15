package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Removes only source-proven {@code EventBus.register(this)} side effects from
 * selected constructors while preserving every other source instruction.
 *
 * <p>The caller supplies constructor descriptors that came from
 * {@link LegacyEventAnalyzer} registration provenance. This transformer does
 * not discover listeners by class name and never strips arbitrary EventBus
 * calls. Both accepted instruction shapes are exact legacy Forge/FML bytecode
 * sequences and are stack-neutral once removed.</p>
 */
public final class LegacySelfRegistrationStripper {
    private static final String EVENT_BUS = "cpw/mods/fml/common/eventhandler/EventBus";
    private static final String MINECRAFT_FORGE = "net/minecraftforge/common/MinecraftForge";
    private static final String FML_COMMON_HANDLER = "cpw/mods/fml/common/FMLCommonHandler";

    public record Result(byte[] bytes, int strippedSites) { }

    public Result strip(byte[] sourceClass, Set<String> constructorDescriptors) {
        if (constructorDescriptors.isEmpty()) return new Result(sourceClass, 0);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(sourceClass).accept(node, 0);
        int stripped = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>") || !constructorDescriptors.contains(method.desc)) continue;
            stripped += stripConstructor(method);
        }
        if (stripped == 0) return new Result(sourceClass, 0);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return new Result(writer.toByteArray(), stripped);
    }

    private static int stripConstructor(MethodNode method) {
        int stripped = 0;
        for (AbstractInsnNode cursor = firstOpcode(method.instructions.getFirst()); cursor != null;) {
            List<AbstractInsnNode> forge = forgePattern(cursor);
            if (forge != null) {
                AbstractInsnNode after = nextOpcode(forge.getLast());
                forge.forEach(method.instructions::remove);
                stripped++;
                cursor = after;
                continue;
            }
            List<AbstractInsnNode> fml = fmlPattern(cursor);
            if (fml != null) {
                AbstractInsnNode after = nextOpcode(fml.getLast());
                fml.forEach(method.instructions::remove);
                stripped++;
                cursor = after;
                continue;
            }
            cursor = nextOpcode(cursor);
        }
        return stripped;
    }

    private static List<AbstractInsnNode> forgePattern(AbstractInsnNode first) {
        if (!(first instanceof FieldInsnNode field)
                || first.getOpcode() != Opcodes.GETSTATIC
                || !field.owner.equals(MINECRAFT_FORGE)
                || !field.name.equals("EVENT_BUS")
                || !field.desc.equals("L" + EVENT_BUS + ";")) return null;
        AbstractInsnNode loadThis = nextOpcode(first);
        AbstractInsnNode register = nextOpcode(loadThis);
        if (!isAload0(loadThis) || !isRegister(register)) return null;
        return List.of(first, loadThis, register);
    }

    private static List<AbstractInsnNode> fmlPattern(AbstractInsnNode first) {
        if (!(first instanceof MethodInsnNode instance)
                || first.getOpcode() != Opcodes.INVOKESTATIC
                || !instance.owner.equals(FML_COMMON_HANDLER)
                || !instance.name.equals("instance")
                || !instance.desc.equals("()L" + FML_COMMON_HANDLER + ";")) return null;
        AbstractInsnNode busNode = nextOpcode(first);
        AbstractInsnNode loadThis = nextOpcode(busNode);
        AbstractInsnNode register = nextOpcode(loadThis);
        if (!(busNode instanceof MethodInsnNode bus)
                || bus.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !bus.owner.equals(FML_COMMON_HANDLER)
                || !bus.name.equals("bus")
                || !bus.desc.equals("()L" + EVENT_BUS + ";")
                || !isAload0(loadThis)
                || !isRegister(register)) return null;
        return List.of(first, busNode, loadThis, register);
    }

    private static boolean isRegister(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode method
                && method.getOpcode() == Opcodes.INVOKEVIRTUAL
                && method.owner.equals(EVENT_BUS)
                && method.name.equals("register")
                && method.desc.equals("(Ljava/lang/Object;)V");
    }

    private static boolean isAload0(AbstractInsnNode instruction) {
        return instruction instanceof VarInsnNode variable
                && variable.getOpcode() == Opcodes.ALOAD
                && variable.var == 0;
    }

    private static AbstractInsnNode firstOpcode(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        return firstOpcode(instruction.getNext());
    }
}
