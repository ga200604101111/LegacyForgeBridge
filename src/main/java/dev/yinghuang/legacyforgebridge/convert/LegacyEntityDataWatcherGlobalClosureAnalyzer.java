package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-wide closure check for runtime DataWatcher calls. Every direct DataWatcher invocation in
 * the source JAR, except entityInit definition calls handled by {@link LegacyEntityDataWatcherAnalyzer},
 * must be fully accounted for by at least one admitted bounded entity access proof.
 */
public final class LegacyEntityDataWatcherGlobalClosureAnalyzer {
    private static final String DATA_WATCHER = "net/minecraft/entity/DataWatcher";
    private static final Set<String> ENTITY_INIT_NAMES = Set.of("entityInit", "func_70088_a");
    private static final Set<String> ADD_NAMES = Set.of("addObject", "func_75682_a", "addObjectByDataType", "func_82709_a");
    private static final Set<String> UPDATE_NAMES = Set.of("updateObject", "func_75692_b");
    private static final Set<String> SUPPORTED_GETTERS = Set.of(
            "getWatchableObjectByte(I)B", "func_75683_a(I)B",
            "getWatchableObjectShort(I)S", "func_75693_b(I)S",
            "getWatchableObjectInt(I)I", "func_75679_c(I)I",
            "getWatchableObjectFloat(I)F", "func_111145_d(I)F",
            "getWatchableObjectString(I)Ljava/lang/String;", "func_75681_e(I)Ljava/lang/String;"
    );

    public record MethodSurface(String sourceOwner, String sourceMethod, String sourceDescriptor,
                                int runtimeCallCount, int provenAccessCount, List<String> unsupportedCalls) {
        public MethodSurface { unsupportedCalls = List.copyOf(unsupportedCalls); }
    }
    public record Analysis(boolean sourceWideClosureComplete, int runtimeCallCount, int provenRuntimeCallCount,
                           List<MethodSurface> accounted, List<MethodSurface> unresolved, List<String> diagnostics) {
        public Analysis {
            accounted = List.copyOf(accounted);
            unresolved = List.copyOf(unresolved);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record MethodKey(String owner, String name, String descriptor) { }
    private record MethodContext(MethodNode method,Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final Map<MethodKey,MethodContext> contexts = new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        contexts.clear();
        diagnostics.clear();
        load(jarPath);

        LegacyEntityDataWatcherAccessAnalyzer.Analysis accessAnalysis =
                new LegacyEntityDataWatcherAccessAnalyzer().analyze(jarPath);
        diagnostics.addAll(accessAnalysis.diagnostics());

        Map<MethodKey,Integer> maxProvenByMethod = new HashMap<>();
        for (LegacyEntityDataWatcherAccessAnalyzer.Rule rule : accessAnalysis.rules()) {
            Map<MethodKey,Integer> perRule = new HashMap<>();
            for (LegacyEntityDataWatcherAccessAnalyzer.Access access : rule.accesses()) {
                MethodKey key = new MethodKey(access.sourceOwner(), access.sourceMethod(), access.sourceDescriptor());
                perRule.merge(key, 1, Integer::sum);
            }
            perRule.forEach((key, count) -> maxProvenByMethod.merge(key, count, Math::max));
        }

        List<MethodSurface> accounted = new ArrayList<>();
        List<MethodSurface> unresolved = new ArrayList<>();
        int totalRuntimeCalls = 0;
        int totalProvenCalls = 0;

        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                int runtimeCalls = 0;
                int platformProven = 0;
                List<String> unsupported = new ArrayList<>();
                MethodContext context = null;
                for (int i = 0; i < method.instructions.size(); i++) {
                    AbstractInsnNode instruction = method.instructions.get(i);
                    if (!(instruction instanceof MethodInsnNode call) || !DATA_WATCHER.equals(call.owner)) continue;
                    if (definitionCall(method, call)) continue;
                    runtimeCalls++;
                    if (!supportedAccessCall(call)) {
                        unsupported.add(call.name + call.desc + "@" + i);
                        continue;
                    }
                    if (context == null) {
                        try {
                            context = context(owner, method);
                        } catch (AnalyzerException error) {
                            diagnostics.add("Platform DataWatcher closure dataflow unavailable for "
                                    + owner.name + "." + method.name + method.desc + ": " + error.getMessage());
                        }
                    }
                    if (context != null && platformGetter(context, i, call)) platformProven++;
                }
                if (runtimeCalls == 0) continue;

                MethodKey key = new MethodKey(owner.name, method.name, method.desc);
                int entityProven = maxProvenByMethod.getOrDefault(key, 0);
                // Aggregate call counts do not identify individual instructions. Never sum an
                // entity-lineage count and a platform count in the same method: doing so could
                // double-count one call and accidentally mask a different unresolved call.
                // Platform-only utility/event methods may use the pinned vanilla shortcut; mixed
                // methods remain fail-closed until callsite identity is represented explicitly.
                int proven = entityProven == 0
                        ? Math.min(runtimeCalls, platformProven)
                        : Math.min(runtimeCalls, entityProven);
                totalRuntimeCalls += runtimeCalls;
                totalProvenCalls += proven;
                if (unsupported.isEmpty() && proven == runtimeCalls) {
                    accounted.add(new MethodSurface(owner.name, method.name, method.desc,
                            runtimeCalls, proven, List.of()));
                } else {
                    unresolved.add(new MethodSurface(owner.name, method.name, method.desc,
                            runtimeCalls, proven, unsupported));
                }
            }
        }

        return new Analysis(unresolved.isEmpty(), totalRuntimeCalls, totalProvenCalls,
                accounted, unresolved, diagnostics);
    }

    private static boolean definitionCall(MethodNode method, MethodInsnNode call) {
        return ENTITY_INIT_NAMES.contains(method.name) && ADD_NAMES.contains(call.name);
    }

    private static boolean supportedAccessCall(MethodInsnNode call) {
        if (SUPPORTED_GETTERS.contains(call.name + call.desc)) return true;
        return UPDATE_NAMES.contains(call.name) && "(ILjava/lang/Object;)V".equals(call.desc);
    }

    private boolean platformGetter(MethodContext context,int instructionIndex,MethodInsnNode call) {
        String valueKind = switch (call.name + call.desc) {
            case "getWatchableObjectByte(I)B", "func_75683_a(I)B" -> "byte";
            case "getWatchableObjectShort(I)S", "func_75693_b(I)S" -> "short";
            case "getWatchableObjectInt(I)I", "func_75679_c(I)I" -> "int";
            case "getWatchableObjectFloat(I)F", "func_111145_d(I)F" -> "float";
            case "getWatchableObjectString(I)Ljava/lang/String;",
                    "func_75681_e(I)Ljava/lang/String;" -> "string";
            default -> null;
        };
        if (valueKind == null) return false;
        Frame<SourceValue> frame = context.frames()[instructionIndex];
        if (frame == null || frame.getStackSize() < 2) return false;
        Integer index = scalarInt(context, frame.getStack(frame.getStackSize() - 1));
        if (index == null) return false;
        String owner = platformWatcherOwner(context, frame.getStack(frame.getStackSize() - 2));
        return owner != null && LegacyVanillaEntityDataWatcher1710.matchesPlatformAccess(owner, index, valueKind);
    }

    private String platformWatcherOwner(MethodContext context,SourceValue value) {
        if (value == null || value.insns == null || value.insns.size() != 1) return null;
        AbstractInsnNode producer = value.insns.iterator().next();
        if (!(producer instanceof MethodInsnNode call)
                || call.getOpcode() == Opcodes.INVOKESTATIC
                || !Set.of("getDataWatcher","func_70096_w").contains(call.name)
                || !"()Lnet/minecraft/entity/DataWatcher;".equals(call.desc)
                || !call.owner.startsWith("net/minecraft/entity/")) return null;
        return call.owner;
    }

    private static Integer scalarInt(MethodContext context,SourceValue value) {
        if (value == null || value.insns == null || value.insns.size() != 1) return null;
        AbstractInsnNode producer = value.insns.iterator().next();
        if (producer instanceof org.objectweb.asm.tree.IntInsnNode integer
                && (integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH))
            return integer.operand;
        if (producer instanceof org.objectweb.asm.tree.LdcInsnNode ldc && ldc.cst instanceof Integer integer)
            return integer;
        int opcode = producer.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) return opcode - Opcodes.ICONST_0;
        return null;
    }

    private MethodContext context(ClassNode owner,MethodNode method) throws AnalyzerException {
        MethodKey key = new MethodKey(owner.name, method.name, method.desc);
        MethodContext cached = contexts.get(key);
        if (cached != null) return cached;
        Frame<SourceValue>[] frames = new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
        Map<AbstractInsnNode,Integer> indices = new java.util.IdentityHashMap<>();
        for (int i = 0; i < method.instructions.size(); i++) indices.put(method.instructions.get(i), i);
        MethodContext result = new MethodContext(method, frames, indices);
        contexts.put(key, result);
        return result;
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
                    diagnostics.add("Unreadable source-wide DataWatcher closure class " + entry.getName()
                            + ": " + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
