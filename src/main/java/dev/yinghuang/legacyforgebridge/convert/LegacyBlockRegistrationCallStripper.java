package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Neutralizes one exact Forge 1.7.x GameRegistry.registerBlock call while preserving evaluation of
 * every argument producer and source constructor side effect.
 */
public final class LegacyBlockRegistrationCallStripper {
    private static final String GAME_REGISTRY =
            "cpw/mods/fml/common/registry/GameRegistry";
    private static final String BLOCK = "net/minecraft/block/Block";

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
            return new Result(sourceClass, 0,
                    List.of("registration-source-method-missing"));
        }

        List<MethodInsnNode> matches = new ArrayList<>();
        for (AbstractInsnNode instruction : owner.instructions) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != Opcodes.INVOKESTATIC
                    || !GAME_REGISTRY.equals(call.owner)
                    || !"registerBlock".equals(call.name)
                    || !supportedDescriptor(call.desc)) {
                continue;
            }
            matches.add(call);
        }

        if (matches.isEmpty()) {
            return new Result(sourceClass, 0,
                    List.of("no-supported-registerBlock-callsite"));
        }
        if (matches.size() != 1) {
            return new Result(sourceClass, 0,
                    List.of("ambiguous-registerBlock-callsites:" + matches.size()));
        }

        MethodInsnNode call = matches.getFirst();
        Type methodType = Type.getMethodType(call.desc);
        Type[] args = methodType.getArgumentTypes();
        AbstractInsnNode discardedReturn = null;

        if (methodType.getReturnType().getSort() == Type.OBJECT) {
            // Do not assume Forge returns its first Block argument. Only neutralize a non-void
            // overload when the bytecode itself proves that the return value is immediately
            // discarded and therefore semantically irrelevant to the caller.
            AbstractInsnNode next = call.getNext();
            if (!(next instanceof InsnNode insn) || insn.getOpcode() != Opcodes.POP) {
                return new Result(sourceClass, 0,
                        List.of("registerBlock-return-value-is-used-or-control-boundary"));
            }
            discardedReturn = next;
        }

        for (int index = args.length - 1; index >= 0; index--) {
            owner.instructions.insertBefore(
                    call,
                    new InsnNode(args[index].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
        }
        owner.instructions.remove(call);
        if (discardedReturn != null) owner.instructions.remove(discardedReturn);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        byte[] rewritten = writer.toByteArray();
        if (containsSupportedRegistration(rewritten, target)) {
            return new Result(sourceClass, 0,
                    List.of("post-strip-residual-registerBlock-call"));
        }
        return new Result(rewritten, 1, List.of());
    }

    static boolean supportedDescriptor(String descriptor) {
        Type method;
        try {
            method = Type.getMethodType(descriptor);
        } catch (IllegalArgumentException invalid) {
            return false;
        }

        Type returnType = method.getReturnType();
        boolean supportedReturn = returnType.getSort() == Type.VOID
                || (returnType.getSort() == Type.OBJECT
                && BLOCK.equals(returnType.getInternalName()));
        if (!supportedReturn) return false;

        Type[] args = method.getArgumentTypes();
        if (args.length == 2) {
            return object(args[0], BLOCK)
                    && object(args[1], "java/lang/String");
        }
        if (args.length == 4) {
            return object(args[0], BLOCK)
                    && object(args[1], "java/lang/Class")
                    && object(args[2], "java/lang/String")
                    && args[3].getSort() == Type.ARRAY
                    && args[3].getElementType().getSort() == Type.OBJECT
                    && "java/lang/Object".equals(
                    args[3].getElementType().getInternalName());
        }
        return false;
    }

    private static boolean object(Type type, String internalName) {
        return type.getSort() == Type.OBJECT
                && internalName.equals(type.getInternalName());
    }

    private static boolean containsSupportedRegistration(
            byte[] bytes, Target target) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(
                node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        for (MethodNode method : node.methods) {
            if (!method.name.equals(target.sourceMethod())
                    || !method.desc.equals(target.sourceDescriptor())) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESTATIC
                        && GAME_REGISTRY.equals(call.owner)
                        && "registerBlock".equals(call.name)
                        && supportedDescriptor(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }
}
