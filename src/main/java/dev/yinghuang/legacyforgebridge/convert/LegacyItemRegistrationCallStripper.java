package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Neutralizes one exact Forge 1.7.x GameRegistry.registerItem call while preserving evaluation of
 * every argument producer and source constructor side effect.
 */
public final class LegacyItemRegistrationCallStripper {
    private static final String GAME_REGISTRY =
            "cpw/mods/fml/common/registry/GameRegistry";

    public record Target(String sourceMethod, String sourceDescriptor) { }

    public record Result(byte[] bytes, int strippedSites, List<String> blockers) {
        public Result {
            blockers = List.copyOf(blockers);
        }
    }

    public Result strip(byte[] sourceClass, Target target) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(sourceClass).accept(node, 0);

        MethodNode owner = null;
        for (MethodNode method : node.methods) {
            if (method.name.equals(target.sourceMethod())
                    && method.desc.equals(target.sourceDescriptor())) {
                owner = method;
                break;
            }
        }
        if (owner == null) {
            return new Result(sourceClass, 0, List.of("registration-source-method-missing"));
        }

        List<MethodInsnNode> matches = new ArrayList<>();
        for (AbstractInsnNode instruction : owner.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !GAME_REGISTRY.equals(call.owner)
                    || !"registerItem".equals(call.name)
                    || !supportedDescriptor(call.desc)) continue;
            matches.add(call);
        }

        if (matches.isEmpty()) {
            return new Result(sourceClass, 0,
                    List.of("no-supported-registerItem-callsite"));
        }
        if (matches.size() != 1) {
            return new Result(sourceClass, 0,
                    List.of("ambiguous-registerItem-callsites:" + matches.size()));
        }

        MethodInsnNode call = matches.getFirst();
        Type[] args = Type.getArgumentTypes(call.desc);
        for (int index = args.length - 1; index >= 0; index--) {
            owner.instructions.insertBefore(
                    call,
                    new InsnNode(args[index].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
        }
        owner.instructions.remove(call);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return new Result(writer.toByteArray(), 1, List.of());
    }

    static boolean supportedDescriptor(String descriptor) {
        Type method = Type.getMethodType(descriptor);
        if (method.getReturnType().getSort() != Type.VOID) return false;
        Type[] args = method.getArgumentTypes();
        if (args.length < 2 || args.length > 3) return false;
        if (args[0].getSort() != Type.OBJECT
                || !"net/minecraft/item/Item".equals(args[0].getInternalName())) return false;
        for (int index = 1; index < args.length; index++) {
            if (args[index].getSort() != Type.OBJECT
                    || !"java/lang/String".equals(args[index].getInternalName())) return false;
        }
        return true;
    }
}
