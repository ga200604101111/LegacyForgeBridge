import java.nio.file.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Narrow ABI-preserving edits to the SHA-pinned cumulative rev239 main JAR. */
public final class PatchRev240 {
    private static final String ROOT = "dev/yinghuang/legacyforgebridge/";
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("base.jar output-class-directory");
        Path output = Path.of(args[1]);
        try (ZipFile zip = new ZipFile(args[0])) {
            String runtime = ROOT + "render/ConvertedLiquidPresentationRuntime.class";
            ClassNode node = read(zip, runtime);
            int hooks = 0;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals(ROOT + "behavior/Rev233LiquidCompat")
                        && call.name.equals("registerLiquid")
                        && call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V")) {
                    call.owner = ROOT + "behavior/Rev239VanillaLiquidBridge";
                    hooks++;
                }
            }
            if (hooks != 1) throw new IllegalStateException("Expected exactly one liquid hook, got " + hooks);
            write(output.resolve(runtime), node);
            String info = ROOT + "BuildInfo.class";
            node = read(zip, info);
            int versions = 0, revisions = 0;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions) {
                if (!(insn instanceof LdcInsnNode ldc)) continue;
                if ("0.2.0-alpha.27-corpus4-local.23-rev239-local-test.1".equals(ldc.cst)) {
                    ldc.cst = "0.2.0-alpha.27-corpus4-local.24-rev240-local-test.1";
                    versions++;
                } else if ("2026-10-02.239-vanilla-liquid-fluidstate".equals(ldc.cst)) {
                    ldc.cst = "2026-10-02.240-liquid-cache-refresh-visible-fallback";
                    revisions++;
                }
            }
            if (versions != 1 || revisions != 1) throw new IllegalStateException("Unexpected BuildInfo baseline");
            write(output.resolve(info), node);
            System.out.println("PASS: one runtime hook; one version; one converter revision; cache key untouched");
        }
    }
    private static ClassNode read(ZipFile zip, String path) throws Exception {
        ClassNode node = new ClassNode();
        try (var in = zip.getInputStream(zip.getEntry(path))) { new ClassReader(in.readAllBytes()).accept(node, 0); }
        return node;
    }
    private static void write(Path path, ClassNode node) throws Exception {
        ClassWriter writer = new ClassWriter(0); // Same descriptors/stack shapes: preserve existing frames.
        node.accept(writer);
        Files.createDirectories(path.getParent());
        Files.write(path, writer.toByteArray());
    }
}
