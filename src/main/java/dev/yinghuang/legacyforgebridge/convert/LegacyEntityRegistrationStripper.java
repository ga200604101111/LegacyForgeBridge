package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes one exact, side-effect-free Forge 1.7.10 {@code registerModEntity} callsite.
 *
 * <p>The accepted shape is deliberately tiny: seven category-1 pure argument producers immediately
 * followed by the standard static registration call, with no label/frame boundary inside the
 * sequence. The caller supplies source method identity and all proven registration constants.
 * Anything more dynamic is left untouched.</p>
 */
public final class LegacyEntityRegistrationStripper {
    private static final String ENTITY_REGISTRY = "cpw/mods/fml/common/registry/EntityRegistry";
    private static final String REGISTER = "registerModEntity";
    private static final String REGISTER_DESC = "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V";

    public record Target(String sourceMethod, String sourceDescriptor, String entityClass,
                         String registryName, int numericId, int trackingRange,
                         int updateFrequency, boolean velocityUpdates) { }
    public record Result(byte[] bytes, int strippedSites, List<String> blockers) {
        public Result { blockers = List.copyOf(blockers); }
    }

    public Result strip(byte[] sourceClass, Target target) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(sourceClass).accept(node, 0);
        List<MethodInsnNode> matches = new ArrayList<>();
        List<AbstractInsnNode[]> slices = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (!method.name.equals(target.sourceMethod()) || !method.desc.equals(target.sourceDescriptor())) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !ENTITY_REGISTRY.equals(call.owner)
                        || !REGISTER.equals(call.name)
                        || !REGISTER_DESC.equals(call.desc)) continue;
                AbstractInsnNode[] slice = exactPureSlice(node.name, call, target);
                if (slice != null) { matches.add(call); slices.add(slice); }
            }
        }
        if (matches.isEmpty()) return new Result(sourceClass, 0,
                List.of("no-exact-pure-registerModEntity-callsite"));
        if (matches.size() != 1) return new Result(sourceClass, 0,
                List.of("ambiguous-exact-registerModEntity-callsites:" + matches.size()));

        MethodInsnNode call = matches.getFirst();
        AbstractInsnNode[] slice = slices.getFirst();
        MethodNode ownerMethod = null;
        for (MethodNode method : node.methods) if (method.instructions.indexOf(call) >= 0) { ownerMethod = method; break; }
        if (ownerMethod == null) return new Result(sourceClass, 0, List.of("matched-callsite-owner-method-missing"));
        for (AbstractInsnNode instruction : slice) ownerMethod.instructions.remove(instruction);
        ownerMethod.instructions.remove(call);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return new Result(writer.toByteArray(), 1, List.of());
    }

    private static AbstractInsnNode[] exactPureSlice(String sourceOwner, MethodInsnNode call, Target target) {
        AbstractInsnNode[] values = previousSevenPureOpcodes(call);
        if (values == null) return null;
        if (!classLiteral(values[0], target.entityClass())) return null;
        if (!(values[1] instanceof LdcInsnNode name) || !(name.cst instanceof String text)
                || !target.registryName().equals(text)) return null;
        if (intConstant(values[2]) != target.numericId()) return null;
        if (!modOwnerProducer(sourceOwner, values[3])) return null;
        if (intConstant(values[4]) != target.trackingRange()) return null;
        if (intConstant(values[5]) != target.updateFrequency()) return null;
        int velocity = intConstant(values[6]);
        if (velocity == Integer.MIN_VALUE || (velocity != 0) != target.velocityUpdates()) return null;
        return values;
    }

    private static AbstractInsnNode[] previousSevenPureOpcodes(AbstractInsnNode call) {
        AbstractInsnNode[] result = new AbstractInsnNode[7];
        AbstractInsnNode cursor = call.getPrevious();
        for (int index = 6; index >= 0; index--) {
            while (cursor != null && cursor.getOpcode() < 0) {
                if (cursor instanceof LabelNode || cursor instanceof FrameNode) return null;
                cursor = cursor.getPrevious();
            }
            if (cursor == null || !pureProducer(cursor)) return null;
            result[index] = cursor;
            cursor = cursor.getPrevious();
        }
        return result;
    }

    private static boolean pureProducer(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (instruction instanceof LdcInsnNode) return true;
        if (instruction instanceof IntInsnNode integer)
            return integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH;
        if (instruction instanceof VarInsnNode variable) return variable.getOpcode() == Opcodes.ALOAD;
        if (instruction instanceof FieldInsnNode field) return field.getOpcode() == Opcodes.GETSTATIC;
        return instruction instanceof InsnNode && (opcode == Opcodes.ACONST_NULL
                || (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5));
    }

    private static boolean classLiteral(AbstractInsnNode instruction, String entityClass) {
        return instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Type type
                && type.getSort() == Type.OBJECT && entityClass.equals(type.getInternalName());
    }

    private static boolean modOwnerProducer(String sourceOwner, AbstractInsnNode instruction) {
        if (instruction instanceof VarInsnNode variable) return variable.getOpcode() == Opcodes.ALOAD;
        if (instruction instanceof InsnNode insn) return insn.getOpcode() == Opcodes.ACONST_NULL;
        if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
            if (!sourceOwner.equals(field.owner)) return false;
            try { return Type.getType(field.desc).getSort() == Type.OBJECT; }
            catch (IllegalArgumentException ignored) { return false; }
        }
        return false;
    }

    private static int intConstant(AbstractInsnNode instruction) {
        if (instruction instanceof InsnNode insn) {
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) return opcode - Opcodes.ICONST_0;
        }
        if (instruction instanceof IntInsnNode integer
                && (integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH)) return integer.operand;
        if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value) return value;
        return Integer.MIN_VALUE;
    }
}
