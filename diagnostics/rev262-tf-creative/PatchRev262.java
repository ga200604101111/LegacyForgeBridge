import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchRev262 {
    static final String STATE = "dev/yinghuang/legacyforgebridge/convert/pass/Corpus3GenericCompletionPass$State.class";
    static final String BUILD = "dev/yinghuang/legacyforgebridge/BuildInfo.class";

    static byte[] patchState(byte[] b) {
        ClassNode c = new ClassNode();
        new ClassReader(b).accept(c, 0);
        int n = 0;
        for (MethodNode m : c.methods) {
            if (!m.name.equals("scanDirectCreativeMembership") || !m.desc.equals("(Ljava/util/Map;)V")) continue;
            for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext()) {
                if (x.getOpcode() != Opcodes.RETURN) continue;
                InsnList q = new InsnList();
                q.add(new VarInsnNode(Opcodes.ALOAD, 0));
                q.add(new FieldInsnNode(Opcodes.GETFIELD,
                        "dev/yinghuang/legacyforgebridge/convert/pass/Corpus3GenericCompletionPass$State",
                        "sourceJar", "Ljava/nio/file/Path;"));
                q.add(new VarInsnNode(Opcodes.ALOAD, 0));
                q.add(new FieldInsnNode(Opcodes.GETFIELD,
                        "dev/yinghuang/legacyforgebridge/convert/pass/Corpus3GenericCompletionPass$State",
                        "registry", "Ldev/yinghuang/legacyforgebridge/convert/LegacyRegistryAnalyzer$Analysis;"));
                q.add(new VarInsnNode(Opcodes.ALOAD, 1));
                q.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "dev/yinghuang/legacyforgebridge/convert/pass/GenericBlockCreativeMembership",
                        "complete",
                        "(Ljava/nio/file/Path;Ldev/yinghuang/legacyforgebridge/convert/LegacyRegistryAnalyzer$Analysis;Ljava/util/Map;)V",
                        false));
                m.instructions.insertBefore(x, q);
                n++;
            }
        }
        if (n != 1) throw new IllegalStateException("expected one State return patch got " + n);
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(w);
        return w.toByteArray();
    }

    static byte[] patchBuild(byte[] b) {
        ClassNode c = new ClassNode();
        new ClassReader(b).accept(c, 0);
        int n = 0;
        for (MethodNode m : c.methods) {
            if (!m.name.equals("<clinit>")) continue;
            for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext()) {
                if (!(x instanceof LdcInsnNode l) || !(l.cst instanceof String s)) continue;
                if (s.equals("0.2.0-alpha.27-corpus4-local.43-rev260-handoff.1")) {
                    l.cst = "0.2.0-alpha.27-corpus4-local.45-rev262-tf-creative.1";
                    n++;
                } else if (s.equals("2026-10-06.257-sapling-source-evidence-collision-occlusion")) {
                    l.cst = "2026-10-07.262-block-creative-allocation-proof";
                    n++;
                }
            }
        }
        if (n != 2) throw new IllegalStateException("expected build replacements 2 got " + n);
        ClassWriter w = new ClassWriter(0);
        c.accept(w);
        return w.toByteArray();
    }

    public static void main(String[] a) throws Exception {
        Path in = Path.of(a[0]), out = Path.of(a[1]), classes = Path.of(a[2]);
        Manifest mf;
        try (JarFile jf = new JarFile(in.toFile())) { mf = jf.getManifest(); }
        try (JarFile jf = new JarFile(in.toFile());
             JarOutputStream jos = mf == null ? new JarOutputStream(Files.newOutputStream(out)) : new JarOutputStream(Files.newOutputStream(out), mf)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (e.getName().equalsIgnoreCase("META-INF/MANIFEST.MF")) continue;
                byte[] data;
                try (InputStream is = jf.getInputStream(e)) { data = is.readAllBytes(); }
                if (e.getName().equals(STATE)) data = patchState(data);
                else if (e.getName().equals(BUILD)) data = patchBuild(data);
                JarEntry ne = new JarEntry(e.getName());
                ne.setTime(e.getTime());
                jos.putNextEntry(ne);
                jos.write(data);
                jos.closeEntry();
            }
            Path root = classes.resolve("dev/yinghuang/legacyforgebridge/convert/pass");
            try (var stream = Files.walk(root)) {
                for (Path p : stream.filter(Files::isRegularFile).toList()) {
                    String fn = p.getFileName().toString();
                    if (!fn.startsWith("GenericBlockCreativeMembership") || fn.contains("Local")) continue;
                    String name = "dev/yinghuang/legacyforgebridge/convert/pass/" + fn;
                    JarEntry ne = new JarEntry(name);
                    jos.putNextEntry(ne);
                    jos.write(Files.readAllBytes(p));
                    jos.closeEntry();
                }
            }
        }
    }
}
