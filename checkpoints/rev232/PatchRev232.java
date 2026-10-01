import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Exact rev232 runtime-class repair.
 *
 * Input must be the rev230 LegacySourceItemRuntime.class. Existing StackMap frames are discarded
 * and regenerated from the unchanged executable bytecode.
 */
public final class PatchRev232 {
    private static final class SafeWriter extends ClassWriter {
        SafeWriter(int flags) { super(flags); }
        @Override protected String getCommonSuperClass(String a, String b) {
            if (a.equals(b)) return a;
            if ("java/lang/Object".equals(a) || "java/lang/Object".equals(b)) return "java/lang/Object";
            return "java/lang/Object";
        }
    }

    public static void main(String[] args) throws Exception {
        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        ClassReader reader = new ClassReader(Files.readAllBytes(input));
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);

        SafeWriter writer = new SafeWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);

        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }
}
