package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Source-wide inventory of legacy FML entity renderer registrations and provably empty renderers. */
public final class LegacyEntityPresentationAnalyzer {
    private static final String RENDERING_REGISTRY = "cpw/mods/fml/client/registry/RenderingRegistry";
    private static final String REGISTER = "registerEntityRenderingHandler";
    private static final String REGISTER_DESC = "(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V";
    private static final Set<String> RENDER_NAMES = Set.of("doRender", "func_76986_a");

    public record Registration(String entityClass, String rendererClass,
                               String sourceOwner, String sourceMethod, String sourceDescriptor,
                               boolean rendererClassPresent, boolean noOpRenderProven,
                               String renderOwner, String renderMethod, String renderDescriptor) { }
    public record Analysis(List<Registration> registrations, List<String> diagnostics) {
        public Analysis {
            registrations = List.copyOf(registrations);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices) { }
    private record RenderProof(boolean noOp, String owner, String method, String descriptor) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear(); diagnostics.clear(); load(jarPath);
        List<Registration> output = new ArrayList<>();
        for (ClassNode owner : classes.values()) for (MethodNode method : owner.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            MethodContext context;
            try { context = context(owner, method); }
            catch (AnalyzerException | RuntimeException error) {
                diagnostics.add("Entity renderer registration dataflow unavailable for " + owner.name + "."
                        + method.name + method.desc + ": " + String.valueOf(error.getMessage()));
                continue;
            }
            for (int i = 0; i < method.instructions.size(); i++) {
                AbstractInsnNode instruction = method.instructions.get(i);
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !RENDERING_REGISTRY.equals(call.owner)
                        || !REGISTER.equals(call.name)) continue;
                if (!REGISTER_DESC.equals(call.desc)) {
                    diagnostics.add("Unsupported legacy entity renderer registration descriptor " + call.desc
                            + " in " + owner.name + "." + method.name + method.desc + ".");
                    continue;
                }
                Frame<SourceValue> frame = context.frames()[i];
                if (frame == null || frame.getStackSize() < 2) {
                    diagnostics.add("Missing frame for legacy entity renderer registration in "
                            + owner.name + "." + method.name + method.desc + ".");
                    continue;
                }
                int start = frame.getStackSize() - 2;
                String entityClass = classLiteral(context, frame.getStack(start));
                String rendererClass = newObjectType(context, frame.getStack(start + 1));
                if (entityClass == null || rendererClass == null) {
                    diagnostics.add("Unable to prove entity/renderer class identity for RenderingRegistry call in "
                            + owner.name + "." + method.name + method.desc + ".");
                    continue;
                }
                RenderProof proof = renderProof(rendererClass);
                output.add(new Registration(entityClass, rendererClass, owner.name, method.name, method.desc,
                        classes.containsKey(rendererClass), proof.noOp(), proof.owner(), proof.method(), proof.descriptor()));
            }
        }
        return new Analysis(output, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private RenderProof renderProof(String rendererClass) {
        String current = rendererClass;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) break;
            for (MethodNode method : node.methods) {
                if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE
                        | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
                if (!RENDER_NAMES.contains(method.name) || !renderDescriptor(method.desc)) continue;
                return new RenderProof(trivialNoOp(method), node.name, method.name, method.desc);
            }
            current = node.superName;
        }
        return new RenderProof(false, null, null, null);
    }

    private static boolean renderDescriptor(String descriptor) {
        Type method = Type.getMethodType(descriptor);
        if (method.getReturnType().getSort() != Type.VOID) return false;
        Type[] args = method.getArgumentTypes();
        return args.length == 6
                && args[0].getSort() == Type.OBJECT
                && args[1].getSort() == Type.DOUBLE
                && args[2].getSort() == Type.DOUBLE
                && args[3].getSort() == Type.DOUBLE
                && args[4].getSort() == Type.FLOAT
                && args[5].getSort() == Type.FLOAT;
    }

    private static boolean trivialNoOp(MethodNode method) {
        int executable = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            int opcode = instruction.getOpcode();
            if (opcode < 0) continue;
            executable++;
            if (opcode != Opcodes.RETURN) return false;
        }
        return executable == 1;
    }

    private static String classLiteral(MethodContext context, SourceValue value) {
        return uniqueProducerType(context, value, true);
    }

    private static String newObjectType(MethodContext context, SourceValue value) {
        return uniqueProducerType(context, value, false);
    }

    private static String uniqueProducerType(MethodContext context, SourceValue value, boolean classLiteral) {
        if (value == null || value.insns == null || value.insns.isEmpty()) return null;
        String result = null;
        for (AbstractInsnNode producer : value.insns) {
            String candidate = null;
            if (classLiteral && producer instanceof LdcInsnNode ldc && ldc.cst instanceof Type type
                    && type.getSort() == Type.OBJECT) candidate = type.getInternalName();
            else if (!classLiteral && producer instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW)
                candidate = type.desc;
            if (candidate == null) return null;
            if (result == null) result = candidate;
            else if (!result.equals(candidate)) return null;
        }
        return result;
    }

    private static MethodContext context(ClassNode owner, MethodNode method) throws AnalyzerException {
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
        Map<AbstractInsnNode,Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < method.instructions.size(); i++) indices.put(method.instructions.get(i), i);
        return new MethodContext(owner, method, frames, indices);
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable entity-presentation class " + entry.getName() + ": "
                            + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
