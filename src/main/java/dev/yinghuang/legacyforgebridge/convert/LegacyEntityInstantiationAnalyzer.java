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

/**
 * Non-executing inventory of direct source construction and World.spawnEntityInWorld use for
 * lifecycle-registered legacy Entity classes. Unknown spawn argument provenance is retained rather
 * than attributed to a registered entity by guesswork.
 */
public final class LegacyEntityInstantiationAnalyzer {
    private static final String WORLD = "net/minecraft/world/World";
    private static final String ENTITY_DESC = "Lnet/minecraft/entity/Entity;";
    private static final Set<String> SPAWN_NAMES = Set.of("spawnEntityInWorld", "func_72838_d");
    private static final String SPAWN_DESC = "(" + ENTITY_DESC + ")Z";

    public record Registration(String registryName, String sourceClass) { }
    public record Construction(String sourceClass, String constructorDescriptor,
                               String sourceOwner, String sourceMethod, String sourceDescriptor,
                               int instructionIndex) { }
    public record WorldSpawn(String sourceClass, boolean sourceClassProven,
                             String sourceOwner, String sourceMethod, String sourceDescriptor,
                             int instructionIndex) { }
    public record Analysis(List<Registration> registrations, List<Construction> constructions,
                           List<WorldSpawn> worldSpawns, List<String> diagnostics) {
        public Analysis {
            registrations = List.copyOf(registrations);
            constructions = List.copyOf(constructions);
            worldSpawns = List.copyOf(worldSpawns);
            diagnostics = List.copyOf(diagnostics);
        }
        public long unresolvedWorldSpawnCount() {
            return worldSpawns.stream().filter(value -> !value.sourceClassProven()).count();
        }
    }

    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private Set<String> registeredClasses = Set.of();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        diagnostics.clear();
        load(jarPath);

        LegacyLifecycleAnalyzer.Analysis lifecycle = new LegacyLifecycleAnalyzer().analyze(jarPath);
        LinkedHashMap<String,String> registrations = new LinkedHashMap<>();
        for (LegacyLifecycleAnalyzer.Registration registration : lifecycle.of(LegacyLifecycleAnalyzer.Kind.ENTITY)) {
            if (registration.arguments().isEmpty()
                    || !(registration.arguments().getFirst() instanceof LegacyLifecycleAnalyzer.TypeValue type)) continue;
            String name = registration.arguments().size() > 1
                    && registration.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text
                    ? text.value() : null;
            registrations.putIfAbsent(type.internalName(), name);
        }
        registeredClasses = Collections.unmodifiableSet(new LinkedHashSet<>(registrations.keySet()));

        List<Construction> constructions = new ArrayList<>();
        List<WorldSpawn> spawns = new ArrayList<>();
        for (ClassNode owner : classes.values()) for (MethodNode method : owner.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            MethodContext context;
            try {
                context = context(owner, method);
            } catch (AnalyzerException | RuntimeException error) {
                diagnostics.add("Entity instantiation dataflow unavailable for " + owner.name + "."
                        + method.name + method.desc + ": " + String.valueOf(error.getMessage()));
                continue;
            }
            for (int i = 0; i < method.instructions.size(); i++) {
                AbstractInsnNode instruction = method.instructions.get(i);
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (call.getOpcode() == Opcodes.INVOKESPECIAL && "<init>".equals(call.name)
                        && registeredClasses.contains(call.owner)) {
                    constructions.add(new Construction(call.owner, call.desc, owner.name,
                            method.name, method.desc, i));
                }
                if (WORLD.equals(call.owner) && SPAWN_NAMES.contains(call.name) && SPAWN_DESC.equals(call.desc)) {
                    Frame<SourceValue> frame = context.frames()[i];
                    String sourceClass = null;
                    if (frame != null && frame.getStackSize() >= 2) {
                        SourceValue argument = frame.getStack(frame.getStackSize() - 1);
                        sourceClass = registeredEntityType(context, argument, 0, new HashSet<>());
                    }
                    spawns.add(new WorldSpawn(sourceClass, sourceClass != null, owner.name,
                            method.name, method.desc, i));
                }
            }
        }

        List<Registration> registrationValues = registrations.entrySet().stream()
                .map(entry -> new Registration(entry.getValue(), entry.getKey())).toList();
        List<String> combinedDiagnostics = new ArrayList<>(lifecycle.diagnostics());
        combinedDiagnostics.addAll(diagnostics);
        return new Analysis(registrationValues, constructions, spawns,
                List.copyOf(new LinkedHashSet<>(combinedDiagnostics)));
    }

    private MethodContext context(ClassNode owner, MethodNode method) throws AnalyzerException {
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
        Map<AbstractInsnNode,Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < method.instructions.size(); i++) indices.put(method.instructions.get(i), i);
        return new MethodContext(owner, method, frames, indices);
    }

    private String registeredEntityType(MethodContext context, SourceValue value, int depth,
                                        Set<AbstractInsnNode> guard) {
        if (value == null || depth > 32 || value.insns == null || value.insns.isEmpty()) return null;
        String result = null;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return null;
            String candidate = registeredEntityProducer(context, producer, depth + 1, guard);
            guard.remove(producer);
            if (candidate == null) return null;
            if (result == null) result = candidate;
            else if (!result.equals(candidate)) return null;
        }
        return result;
    }

    private String registeredEntityProducer(MethodContext context, AbstractInsnNode producer, int depth,
                                            Set<AbstractInsnNode> guard) {
        if (producer instanceof TypeInsnNode type) {
            if (type.getOpcode() == Opcodes.NEW)
                return registeredClasses.contains(type.desc) ? type.desc : null;
            if (type.getOpcode() == Opcodes.CHECKCAST) {
                if (registeredClasses.contains(type.desc)) return type.desc;
                Integer index = context.indices().get(producer);
                Frame<SourceValue> frame = index == null ? null : context.frames()[index];
                return frame != null && frame.getStackSize() > 0
                        ? registeredEntityType(context, frame.getStack(frame.getStackSize() - 1), depth, guard) : null;
            }
        }
        // SourceInterpreter intentionally makes copy instructions (notably DUP and ALOAD) their
        // own source producers. Follow only exact stack/local copies so NEW provenance survives the
        // ordinary NEW -> DUP -> <init> -> ASTORE -> ALOAD entity construction shape.
        if (producer instanceof InsnNode insn && insn.getOpcode() == Opcodes.DUP) {
            Integer index = context.indices().get(producer);
            Frame<SourceValue> frame = index == null ? null : context.frames()[index];
            return frame != null && frame.getStackSize() > 0
                    ? registeredEntityType(context, frame.getStack(frame.getStackSize() - 1), depth, guard) : null;
        }
        if (producer instanceof VarInsnNode variable) {
            Integer index = context.indices().get(producer);
            Frame<SourceValue> frame = index == null ? null : context.frames()[index];
            if (variable.getOpcode() == Opcodes.ALOAD) {
                return frame != null && variable.var < frame.getLocals()
                        ? registeredEntityType(context, frame.getLocal(variable.var), depth, guard) : null;
            }
            if (variable.getOpcode() == Opcodes.ASTORE) {
                return frame != null && frame.getStackSize() > 0
                        ? registeredEntityType(context, frame.getStack(frame.getStackSize() - 1), depth, guard) : null;
            }
        }
        if (producer instanceof FieldInsnNode field
                && (field.getOpcode() == Opcodes.GETFIELD || field.getOpcode() == Opcodes.GETSTATIC)) {
            Type type = Type.getType(field.desc);
            return type.getSort() == Type.OBJECT && registeredClasses.contains(type.getInternalName())
                    ? type.getInternalName() : null;
        }
        if (producer instanceof MethodInsnNode call) {
            Type type = Type.getReturnType(call.desc);
            return type.getSort() == Type.OBJECT && registeredClasses.contains(type.getInternalName())
                    ? type.getInternalName() : null;
        }
        return null;
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable entity-instantiation class " + entry.getName()
                            + ": " + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
