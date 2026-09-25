package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-only proof for legacy plant presentation inputs.
 *
 * <p>The analyzer deliberately accepts only a straight-line constructor chain whose active source
 * constructor (or source superclass constructor) assigns exactly one constant
 * {@code setBlockTextureName(String)} value. Source presentation overrides and constructor bounds
 * mutations are surfaced independently so the materializer can fail closed instead of guessing a
 * modern cross/stage model.</p>
 */
public final class LegacyPlantPresentationAnalyzer {
    private static final Set<String> TEXTURE_SETTERS = Set.of("setBlockTextureName", "func_149658_d");
    private static final Set<String> PRESENTATION_MUTATORS = Set.of(
            "setBlockBounds", "func_149676_a",
            "setBlockBoundsForItemRender", "func_149683_g");

    public record Proof(
            String registryName,
            String legacyNamespace,
            String sourceClass,
            LegacyPlantBlockAnalyzer.Family family,
            String constructorDescriptor,
            boolean constructorPathStraightLine,
            String textureName,
            boolean textureNameProofComplete,
            List<String> sourcePresentationHooks,
            List<String> constructorPresentationMutations
    ) {
        public Proof {
            sourcePresentationHooks = List.copyOf(sourcePresentationHooks);
            constructorPresentationMutations = List.copyOf(constructorPresentationMutations);
        }

        public boolean sourcePresentationProofComplete() {
            return constructorPathStraightLine
                    && textureNameProofComplete
                    && sourcePresentationHooks.isEmpty()
                    && constructorPresentationMutations.isEmpty();
        }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis {
            proofs = List.copyOf(proofs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record MethodKey(String owner, String descriptor) { }
    private record Trace(boolean straightLine, boolean complete, Set<String> textureNames,
                         List<String> presentationMutations) { }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        load(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        LegacyPlantBlockAnalyzer.Analysis plants = new LegacyPlantBlockAnalyzer().analyze(jarPath);
        LegacyPlantLifecycleAnalyzer.Analysis lifecycle = new LegacyPlantLifecycleAnalyzer().analyze(jarPath);

        Map<String, LegacyRegistryAnalyzer.Registration> registrations = new LinkedHashMap<>();
        for (var block : registry.blocks()) registrations.put(key(block.registryName(), block.implementationClass()), block);
        Map<String, LegacyPlantLifecycleAnalyzer.Proof> lifecycleProofs = new LinkedHashMap<>();
        for (var proof : lifecycle.proofs()) lifecycleProofs.put(key(proof.registryName(), proof.sourceClass()), proof);

        List<Proof> proofs = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        diagnostics.addAll(registry.diagnostics());
        diagnostics.addAll(plants.diagnostics());
        diagnostics.addAll(lifecycle.diagnostics());

        for (var plant : plants.rules()) {
            String k = key(plant.registryName(), plant.sourceClass());
            var registration = registrations.get(k);
            var lifecycleProof = lifecycleProofs.get(k);
            String constructorDescriptor = registration == null ? null : registration.constructorDescriptor();
            Trace trace = constructorDescriptor == null
                    ? new Trace(false, false, Set.of(), List.of())
                    : traceConstructor(plant.sourceClass(), constructorDescriptor, new HashSet<>());
            String textureName = trace.textureNames().size() == 1 ? trace.textureNames().iterator().next() : null;
            boolean textureComplete = trace.complete() && textureName != null;
            List<String> presentationHooks = lifecycleProof == null ? List.of() : lifecycleProof.presentationHooks();
            proofs.add(new Proof(
                    plant.registryName(), plant.legacyNamespace(), plant.sourceClass(), plant.family(),
                    constructorDescriptor, trace.straightLine(), textureName, textureComplete,
                    presentationHooks, trace.presentationMutations()));
        }
        return new Analysis(proofs, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private Trace traceConstructor(String owner, String descriptor, Set<MethodKey> seen) {
        MethodKey key = new MethodKey(owner, descriptor);
        if (!seen.add(key)) return new Trace(false, false, Set.of(), List.of());
        ClassNode node = classes.get(owner);
        if (node == null) return new Trace(false, false, Set.of(), List.of());
        MethodNode constructor = node.methods.stream()
                .filter(method -> "<init>".equals(method.name) && descriptor.equals(method.desc))
                .findFirst().orElse(null);
        if (constructor == null) return new Trace(false, false, Set.of(), List.of());

        boolean straightLine = constructor.tryCatchBlocks == null || constructor.tryCatchBlocks.isEmpty();
        LinkedHashSet<String> textureNames = new LinkedHashSet<>();
        List<String> mutations = new ArrayList<>();
        boolean complete = true;

        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction instanceof JumpInsnNode
                    || instruction instanceof LookupSwitchInsnNode
                    || instruction instanceof TableSwitchInsnNode) {
                straightLine = false;
                complete = false;
            }
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (TEXTURE_SETTERS.contains(call.name)
                    && "(Ljava/lang/String;)Lnet/minecraft/block/Block;".equals(call.desc)) {
                AbstractInsnNode argument = previousReal(instruction.getPrevious());
                if (argument instanceof LdcInsnNode ldc && ldc.cst instanceof String value && !value.isBlank()) {
                    textureNames.add(value);
                } else {
                    complete = false;
                }
            }
            if (PRESENTATION_MUTATORS.contains(call.name)) {
                mutations.add(owner + "#<init>" + descriptor + " -> " + call.owner + "." + call.name + call.desc);
            }
            if (call.getOpcode() == Opcodes.INVOKESPECIAL && "<init>".equals(call.name)
                    && classes.containsKey(call.owner) && isSelfOrSourceAncestor(owner, call.owner)) {
                Trace nested = traceConstructor(call.owner, call.desc, seen);
                straightLine &= nested.straightLine();
                complete &= nested.complete();
                textureNames.addAll(nested.textureNames());
                mutations.addAll(nested.presentationMutations());
            }
        }
        if (!straightLine || textureNames.size() != 1) complete = false;
        return new Trace(straightLine, complete, Set.copyOf(textureNames), List.copyOf(mutations));
    }

    private boolean isSelfOrSourceAncestor(String owner, String candidate) {
        String current = owner;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (candidate.equals(current)) return true;
            ClassNode node = classes.get(current);
            if (node == null) return false;
            current = node.superName;
        }
        return false;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction;
        while (current != null && current.getOpcode() < 0) current = current.getPrevious();
        return current;
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + "\u0000" + sourceClass;
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                }
            }
        }
    }
}
