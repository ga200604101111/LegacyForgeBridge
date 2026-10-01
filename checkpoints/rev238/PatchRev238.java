import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public final class PatchRev238 {
    static ClassNode read(Path p) throws Exception {
        ClassNode n = new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(n, 0);
        return n;
    }
    static void write(Path p, ClassNode n) throws Exception {
        ClassWriter w = new ClassWriter(0);
        n.accept(w);
        Files.createDirectories(p.getParent());
        Files.write(p, w.toByteArray());
    }

    public static void main(String[] args) throws Exception {
        patchDesktopHelper(Paths.get(args[0]));
        if (args.length > 1) patchBuildInfo(Paths.get(args[1]));
    }

    static void patchDesktopHelper(Path p) throws Exception {
        ClassNode n = read(p);
        boolean initialDetails = false, openDiag = false, cancel = false, lambda = false;
        for (MethodNode m : n.methods) {
            if (m.name.equals("createWindow")) {
                int buttonIndex = 0;
                for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext()) {
                    if (!(x instanceof TypeInsnNode type) || x.getOpcode() != Opcodes.NEW) continue;
                    if ("javax/swing/JToggleButton".equals(type.desc)) {
                        LdcInsnNode text = nextStringLdc(x);
                        if (text != null) { text.cst = "詳細資訊"; initialDetails = true; }
                    } else if ("javax/swing/JButton".equals(type.desc)) {
                        LdcInsnNode text = nextStringLdc(x);
                        if (text != null) {
                            if (buttonIndex++ == 0) { text.cst = "轉換診斷"; openDiag = true; }
                            else if (buttonIndex == 2) { text.cst = "取消自動退出/重啟"; cancel = true; }
                        }
                    }
                }
            } else if (m.name.equals("lambda$createWindow$0")) {
                int count = 0;
                for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext()) {
                    if (x instanceof LdcInsnNode l && l.cst instanceof String) {
                        l.cst = "詳細資訊";
                        count++;
                    }
                }
                lambda = count >= 2;
            }
        }
        if (!initialDetails || !openDiag || !cancel || !lambda) {
            throw new IllegalStateException("DesktopHelper anchors missing");
        }
        write(p, n);
    }

    static LdcInsnNode nextStringLdc(AbstractInsnNode from) {
        for (AbstractInsnNode x = from.getNext(); x != null; x = x.getNext()) {
            if (x instanceof LdcInsnNode l && l.cst instanceof String) return l;
            if (x instanceof MethodInsnNode m && m.name.equals("<init>")) return null;
        }
        return null;
    }

    static void patchBuildInfo(Path p) throws Exception {
        ClassNode n = read(p);
        boolean version=false, revision=false;
        for (MethodNode m : n.methods) if (m.name.equals("<clinit>")) {
            for (AbstractInsnNode x=m.instructions.getFirst(); x!=null; x=x.getNext()) {
                if (x instanceof LdcInsnNode l && l.cst instanceof String s) {
                    if (s.equals("0.2.0-alpha.27-corpus4-local.21-rev237-local-test.1")) {
                        l.cst="0.2.0-alpha.27-corpus4-local.22-rev238-local-test.1"; version=true;
                    } else if (s.equals("2026-10-01.237-liquid-alpha-desktop-progress")) {
                        l.cst="2026-10-01.238-compact-progress-window-layout"; revision=true;
                    }
                }
            }
        }
        if (!version || !revision) throw new IllegalStateException("BuildInfo anchors missing");
        write(p,n);
    }
}
