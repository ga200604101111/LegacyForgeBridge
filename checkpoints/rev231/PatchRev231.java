import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public final class PatchRev231 {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        patchLegacySourceItemRuntime(root.resolve("dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime.class"));
        patchBuildInfo(root.resolve("dev/yinghuang/legacyforgebridge/BuildInfo.class"));
    }

    static ClassNode read(Path p) throws Exception {
        ClassNode n = new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(n, 0);
        return n;
    }

    static void write(Path p, ClassNode n) throws Exception {
        ClassWriter w = new ClassWriter(0);
        n.accept(w);
        Files.write(p, w.toByteArray());
    }

    static void patchLegacySourceItemRuntime(Path p) throws Exception {
        ClassNode n = read(p);
        MethodNode target = null;
        for (MethodNode m : n.methods) {
            if (m.name.equals("configure") && m.desc.equals("(Ljava/lang/String;Lnet/minecraft/class_1792$class_1793;)V")) {
                target = m;
                break;
            }
        }
        if (target == null) throw new IllegalStateException("configure missing");

        LabelNode movementJoin = null;
        for (AbstractInsnNode x = target.instructions.getFirst(); x != null; x = x.getNext()) {
            if (x instanceof LdcInsnNode ldc && "generic.movementSpeed".equals(ldc.cst)) {
                for (AbstractInsnNode y = x.getNext(); y != null; y = y.getNext()) {
                    if (y instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFEQ) {
                        movementJoin = j.label;
                        break;
                    }
                    if (y instanceof VarInsnNode v && v.getOpcode() == Opcodes.ASTORE && v.var == 8) break;
                }
                break;
            }
        }
        if (movementJoin == null) throw new IllegalStateException("movement join label missing");

        AbstractInsnNode after = movementJoin.getNext();
        while (after != null && after.getType() == AbstractInsnNode.LINE) after = after.getNext();
        if (!(after instanceof FrameNode)) {
            Object[] locals = {
                "java/lang/String",
                "net/minecraft/class_1792$class_1793",
                "dev/yinghuang/legacyforgebridge/compat/LegacySourceItemContract$Rule",
                "net/minecraft/class_1304",
                "net/minecraft/class_9285$class_9286",
                Opcodes.INTEGER,
                "java/util/Iterator",
                "dev/yinghuang/legacyforgebridge/compat/LegacySourceItemContract$Modifier"
            };
            target.instructions.insert(movementJoin,
                    new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, new Object[0]));
        }
        write(p, n);
    }

    static void patchBuildInfo(Path p) throws Exception {
        ClassNode n = read(p);
        boolean v = false, r = false;
        for (MethodNode m : n.methods) if (m.name.equals("<clinit>")) {
            for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext()) {
                if (x instanceof LdcInsnNode l && l.cst instanceof String s) {
                    if (s.equals("0.2.0-alpha.27-corpus4-local.14-rev230-local-test.1")) {
                        l.cst = "0.2.0-alpha.27-corpus4-local.15-rev231-local-test.1";
                        v = true;
                    } else if (s.equals("2026-10-01.230-legacy-nbt-armor-merge")) {
                        l.cst = "2026-10-01.231-stackmap-verifier-fix";
                        r = true;
                    }
                }
            }
        }
        if (!v || !r) throw new IllegalStateException("BuildInfo anchors " + v + "/" + r);
        write(p, n);
    }
}
