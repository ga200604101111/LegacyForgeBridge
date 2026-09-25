package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes one exact, side-effect-free Forge 1.7.x GameRegistry.registerTileEntity callsite.
 *
 * <p>The accepted shape is deliberately narrow: a concrete tile Class literal and concrete
 * registration String immediately feed the standard static call. Any helper/computed shape is
 * left untouched.</p>
 */
public final class LegacyTileEntityRegistrationStripper {
    private static final String GAME_REGISTRY =
            "cpw/mods/fml/common/registry/GameRegistry";
    private static final String REGISTER = "registerTileEntity";
    private static final String REGISTER_DESC =
            "(Ljava/lang/Class;Ljava/lang/String;)V";

    public record Target(
            String sourceMethod,
            String sourceDescriptor,
            String tileClass,
            String legacyTileId) { }

    public record Result(byte[] bytes, int strippedSites, List<String> blockers) {
        public Result {
            blockers = List.copyOf(blockers);
        }
    }

    public Result strip(byte[] sourceClass, Target target) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(sourceClass).accept(node, 0);

        List<MethodInsnNode> matches = new ArrayList<>();
        List<AbstractInsnNode[]> slices = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (!method.name.equals(target.sourceMethod())
                    || !method.desc.equals(target.sourceDescriptor())) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !GAME_REGISTRY.equals(call.owner)
                        || !REGISTER.equals(call.name)
                        || !REGISTER_DESC.equals(call.desc)) continue;
                AbstractInsnNode[] slice = exactPureSlice(call, target);
                if (slice != null) {
                    matches.add(call);
                    slices.add(slice);
                }
            }
        }

        if (matches.isEmpty()) {
            return new Result(sourceClass, 0,
                    List.of("no-exact-pure-registerTileEntity-callsite"));
        }
        if (matches.size() != 1) {
            return new Result(sourceClass, 0,
                    List.of("ambiguous-exact-registerTileEntity-callsites:" + matches.size()));
        }

        MethodInsnNode call = matches.getFirst();
        AbstractInsnNode[] slice = slices.getFirst();
        MethodNode ownerMethod = null;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction == call) {
                    ownerMethod = method;
                    break;
                }
            }
            if (ownerMethod != null) break;
        }
        if (ownerMethod == null) {
            return new Result(sourceClass, 0,
                    List.of("matched-registerTileEntity-owner-method-missing"));
        }

        AbstractInsnNode after = call.getNext();
        AbstractInsnNode cursor = slice[0];
        boolean reachedCall = false;
        while (cursor != after) {
            if (cursor == null) {
                return new Result(sourceClass, 0,
                        List.of("matched-registerTileEntity-slice-not-contiguous"));
            }
            AbstractInsnNode next = cursor.getNext();
            if (cursor == call) reachedCall = true;
            ownerMethod.instructions.remove(cursor);
            cursor = next;
        }
        if (!reachedCall) {
            return new Result(sourceClass, 0,
                    List.of("matched-registerTileEntity-call-not-in-removal-range"));
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        byte[] rewritten = writer.toByteArray();
        String residual = residualReference(rewritten, target);
        if (residual != null) {
            return new Result(sourceClass, 0,
                    List.of("post-strip-residual-tile-reference:" + residual));
        }
        return new Result(rewritten, 1, List.of());
    }

    private static AbstractInsnNode[] exactPureSlice(
            MethodInsnNode call, Target target) {
        AbstractInsnNode[] values = previousTwoPureOpcodes(call);
        if (values == null) return null;
        if (!(values[0] instanceof LdcInsnNode typeConstant)
                || !(typeConstant.cst instanceof Type type)
                || type.getSort() != Type.OBJECT
                || !target.tileClass().equals(type.getInternalName())) {
            return null;
        }
        if (!(values[1] instanceof LdcInsnNode idConstant)
                || !(idConstant.cst instanceof String id)
                || !target.legacyTileId().equals(id)) {
            return null;
        }
        return values;
    }

    private static AbstractInsnNode[] previousTwoPureOpcodes(AbstractInsnNode call) {
        AbstractInsnNode[] result = new AbstractInsnNode[2];
        AbstractInsnNode cursor = call.getPrevious();
        for (int index = 1; index >= 0; index--) {
            while (cursor != null && cursor.getOpcode() < 0) {
                if (cursor instanceof LabelNode || cursor instanceof FrameNode) return null;
                cursor = cursor.getPrevious();
            }
            if (!(cursor instanceof LdcInsnNode)) return null;
            result[index] = cursor;
            cursor = cursor.getPrevious();
        }
        return result;
    }

    private static String residualReference(byte[] bytes, Target target) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(
                node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        for (MethodNode method : node.methods) {
            if (!method.name.equals(target.sourceMethod())
                    || !method.desc.equals(target.sourceDescriptor())) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode ldc
                        && ldc.cst instanceof Type type
                        && type.getSort() == Type.OBJECT
                        && target.tileClass().equals(type.getInternalName())) {
                    return "ldc-class-literal";
                }
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESTATIC
                        && GAME_REGISTRY.equals(call.owner)
                        && REGISTER.equals(call.name)
                        && REGISTER_DESC.equals(call.desc)) {
                    return "registerTileEntity-call";
                }
            }
        }
        return null;
    }
}
