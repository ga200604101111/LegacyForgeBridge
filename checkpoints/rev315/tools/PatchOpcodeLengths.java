import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Rev315: preserve strict standalone scanner; correctly count CHECKCAST/INSTANCEOF operands. */
public final class PatchOpcodeLengths {
    private static final String TARGET = "dev/yinghuang/legacyforgebridge/convert/LegacyGeneratedBytecodeLinkageAnalyzer";
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PatchOpcodeLengths original.class patched.class");
        byte[] orig = Files.readAllBytes(Path.of(args[0]));
        ClassNode node = new ClassNode();
        new ClassReader(orig).accept(node, 0);
        if (!TARGET.equals(node.name)) throw new IllegalStateException("Unexpected class: " + node.name);
        MethodNode method = null;
        for (MethodNode m : node.methods) if ("validateInstructions".equals(m.name) && "([B)V".equals(m.desc)) method=m;
        if (method == null) throw new IllegalStateException("Missing validateInstructions([B)V");

        JumpInsnNode zeroCaseBranch = null;
        VarInsnNode opcodeStored = null;
        IntInsnNode erroneousBound = null;
        for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof VarInsnNode v && v.getOpcode()==Opcodes.ISTORE && v.var==3 && opcodeStored == null)
                opcodeStored=v;
            if (i instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IF_ICMPLE && zeroCaseBranch==null)
                zeroCaseBranch=j;
            if (i instanceof IntInsnNode n && n.getOpcode()==Opcodes.SIPUSH && n.operand==195
                && i.getNext() != null && i.getNext().getOpcode()==Opcodes.IF_ICMPGT && erroneousBound==null)
                erroneousBound=n;
        }
        if (opcodeStored == null || zeroCaseBranch == null || erroneousBound==null)
            throw new IllegalStateException("Unexpected scanner shape; refusing unsafe patch");
        // Zero-operand range must stop at 191 (ARRAYLENGTH, ATHROW).
        // CHECKCAST(192) and INSTANCEOF(193) are already covered by 2-byte-operand case.
        erroneousBound.operand=191;
        // MONITORENTER(194) and MONITOREXIT(195) remain zero operand.
        InsnList additionalZeroCases = new InsnList();
        for (int opcode : new int[]{194,195}) {
            additionalZeroCases.add(new VarInsnNode(Opcodes.ILOAD,3));
            additionalZeroCases.add(new IntInsnNode(Opcodes.SIPUSH,opcode));
            additionalZeroCases.add(new JumpInsnNode(Opcodes.IF_ICMPEQ,zeroCaseBranch.label));
        }
        method.instructions.insert(opcodeStored,additionalZeroCases);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        byte[] patched=writer.toByteArray();
        ClassNode checked=new ClassNode();new ClassReader(patched).accept(checked,0);
        Files.createDirectories(Path.of(args[1]).getParent());
        Files.write(Path.of(args[1]),patched);
        System.out.printf("Patched %s: %d -> %d bytes%n",TARGET,orig.length,patched.length);
    }
}
