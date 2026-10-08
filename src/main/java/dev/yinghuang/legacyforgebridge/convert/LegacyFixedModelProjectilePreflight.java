package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Non-executable evidence only: joins source-proven entity registrations, renderer registrations,
 * vanilla projectile ancestry and a closed, fixed-cuboid rendering proof. It deliberately does NOT
 * prove player item launch, legacy FML spawn semantics, lighting, or modern renderer readiness.
 */
public final class LegacyFixedModelProjectilePreflight {
    private static final String THROWABLE = "net/minecraft/entity/projectile/EntityThrowable";
    private static final String ADDITIONAL_SPAWN = "cpw/mods/fml/common/registry/IEntityAdditionalSpawnData";

    public record EntityRegistration(String registryName, String sourceClass) { }
    public record RendererRegistration(String entityClass, String rendererClass) { }
    public record Candidate(String registryName, String entityClass, String rendererClass,
                            LegacyFixedModelProjectileAnalyzer.Proof geometry,
                            java.util.Optional<LegacyProjectileLauncherAnalyzer.Proof> launcherProof,
                            java.util.Optional<LegacyProjectileFullbright1710Analyzer.Proof> fullbrightProof) {
        public Candidate {
            Objects.requireNonNull(geometry);
            launcherProof = Objects.requireNonNull(launcherProof);
            fullbrightProof = Objects.requireNonNull(fullbrightProof);
        }
        /** Historical geometry+launcher test fixture compatibility; lighting is not inferred. */
        public Candidate(String registryName, String entityClass, String rendererClass,
                         LegacyFixedModelProjectileAnalyzer.Proof geometry,
                         java.util.Optional<LegacyProjectileLauncherAnalyzer.Proof> launcherProof) {
            this(registryName, entityClass, rendererClass, geometry, launcherProof, java.util.Optional.empty());
        }
        /** Geometry-only synthetic fixture compatibility; no launcher or light may be inferred. */
        public Candidate(String registryName, String entityClass, String rendererClass,
                         LegacyFixedModelProjectileAnalyzer.Proof geometry) {
            this(registryName, entityClass, rendererClass, geometry,
                    java.util.Optional.empty(), java.util.Optional.empty());
        }
    }
    public record Skipped(String registryName, String entityClass, String reason) { }
    public record Analysis(List<Candidate> candidates, List<Skipped> skipped) {
        public Analysis { candidates = List.copyOf(candidates); skipped = List.copyOf(skipped); }
    }

    /** Production caller: consumes only provenance returned by the two source-structural analyzers. */
    public Analysis analyze(Path jar) throws IOException {
        var entities = new LegacyEntityDataWatcherAnalyzer().analyze(jar);
        var renderers = new LegacyEntityPresentationAnalyzer().analyze(jar);
        List<EntityRegistration> registered = new ArrayList<>();
        for (var entity : entities.rules())
            registered.add(new EntityRegistration(entity.registryName(), entity.sourceClass()));
        List<RendererRegistration> bindings = new ArrayList<>();
        for (var binding : renderers.registrations())
            bindings.add(new RendererRegistration(binding.entityClass(), binding.rendererClass()));
        List<LegacyProjectileLauncherAnalyzer.ItemRegistration> items = new ArrayList<>();
        for (var item : new LegacyRegistryAnalyzer().analyze(jar).items())
            items.add(new LegacyProjectileLauncherAnalyzer.ItemRegistration(
                    item.registryName(), item.implementationClass()));
        return inspect(jar, registered, bindings, items);
    }

    /**
     * Package-independent preflight seam for synthetic renamed fixtures. Production callers must
     * provide source-proven registration rows, not arbitrary user-supplied runtime identifiers.
     */
    public Analysis inspect(Path jar, List<EntityRegistration> registrations,
                            List<RendererRegistration> rendererRegistrations) throws IOException {
        return inspect(jar, registrations, rendererRegistrations, List.of());
    }

    /** Joins optional, independently source-proven registered launcher classes when available. */
    public Analysis inspect(Path jar, List<EntityRegistration> registrations,
                            List<RendererRegistration> rendererRegistrations,
                            List<LegacyProjectileLauncherAnalyzer.ItemRegistration> launcherItems) throws IOException {
        Objects.requireNonNull(jar); Objects.requireNonNull(registrations);
        Objects.requireNonNull(rendererRegistrations); Objects.requireNonNull(launcherItems);
        Map<String, ClassNode> classes = loadHierarchy(jar);
        Map<String, List<EntityRegistration>> entities = new LinkedHashMap<>();
        for (var row : registrations) if (row != null && row.sourceClass() != null)
            entities.computeIfAbsent(row.sourceClass(), ignored -> new ArrayList<>()).add(row);
        Map<String, Integer> registryNameCounts = new HashMap<>();
        for (var row : registrations) if (row != null && row.registryName() != null)
            registryNameCounts.merge(row.registryName(), 1, Integer::sum);
        Map<String, List<RendererRegistration>> renderers = new HashMap<>();
        for (var row : rendererRegistrations) if (row != null && row.entityClass() != null)
            renderers.computeIfAbsent(row.entityClass(), ignored -> new ArrayList<>()).add(row);

        List<Candidate> candidates = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        var geometryAnalyzer = new LegacyFixedModelProjectileAnalyzer();
        for (var rows : entities.values()) {
            String sourceClass = rows.getFirst().sourceClass();
            if (!inherits(classes, sourceClass, THROWABLE)) continue;
            String registryName = rows.getFirst().registryName();
            if (rows.size() != 1 || registryName == null || registryName.isBlank()
                    || registryNameCounts.getOrDefault(registryName, 0) != 1) {
                skipped.add(new Skipped(registryName, sourceClass, "Projectile entity registration is missing or not unique"));
                continue;
            }
            if (implementsInterface(classes, sourceClass, ADDITIONAL_SPAWN)) {
                skipped.add(new Skipped(registryName, sourceClass, "Projectile has an unconverted additional-spawn-data interface"));
                continue;
            }
            List<RendererRegistration> bound = renderers.getOrDefault(sourceClass, List.of());
            if (bound.size() != 1 || bound.getFirst().rendererClass() == null) {
                skipped.add(new Skipped(registryName, sourceClass, "Projectile renderer binding is missing or ambiguous"));
                continue;
            }
            String rendererClass = bound.getFirst().rendererClass();
            var proof = geometryAnalyzer.analyze(jar, rendererClass);
            if (proof.proof().isEmpty()) {
                skipped.add(new Skipped(registryName, sourceClass,
                        "Renderer is not the fixed cuboid family: " + String.join("; ", proof.diagnostics())));
                continue;
            }
            // A fixed cuboid source proof is not ready for modern rendering if 1.7.10
            // ModelBox faces escape the exact source texture or geometry becomes degenerate.
            // Keep this a source-only admission gate; never create a runtime entity here.
            try {
                LegacyModelBoxMesh1710.build(proof.proof().orElseThrow());
            } catch (IllegalArgumentException unsafeMesh) {
                skipped.add(new Skipped(registryName, sourceClass,
                        "ModelBox UV/mesh conversion is unproven: " + unsafeMesh.getMessage()));
                continue;
            }
            var launch = launcherItems.isEmpty() ? java.util.Optional.<LegacyProjectileLauncherAnalyzer.Proof>empty()
                    : new LegacyProjectileLauncherAnalyzer().inspect(jar, sourceClass, launcherItems).proof();
            var fullbright = new LegacyProjectileFullbright1710Analyzer().analyze(jar, sourceClass).proof();
            candidates.add(new Candidate(registryName, sourceClass, rendererClass,
                    proof.proof().orElseThrow(), launch, fullbright));
        }
        candidates.sort(Comparator.comparing(Candidate::registryName).thenComparing(Candidate::entityClass));
        skipped.sort(Comparator.comparing(Skipped::registryName, Comparator.nullsFirst(String::compareTo))
                .thenComparing(Skipped::entityClass));
        return new Analysis(candidates, skipped);
    }

    private static boolean inherits(Map<String, ClassNode> classes, String child, String base) {
        Set<String> visited = new HashSet<>();
        for (String type = child; type != null && visited.add(type);) {
            if (type.equals(base)) return true;
            ClassNode node = classes.get(type);
            type = node == null ? null : node.superName;
        }
        return false;
    }

    private static boolean implementsInterface(Map<String, ClassNode> classes, String child, String target) {
        Set<String> visited = new HashSet<>();
        List<String> pending = new ArrayList<>(List.of(child));
        while (!pending.isEmpty()) {
            String type = pending.removeLast();
            if (!visited.add(type)) continue;
            if (type.equals(target)) return true;
            ClassNode node = classes.get(type);
            if (node == null) continue;
            if (node.superName != null) pending.add(node.superName);
            pending.addAll(node.interfaces);
        }
        return false;
    }

    private static Map<String, ClassNode> loadHierarchy(Path jar) throws IOException {
        Map<String, ClassNode> result = new LinkedHashMap<>();
        try (JarFile source = new JarFile(jar.toFile(), false)) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = source.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (result.putIfAbsent(node.name, node) != null)
                        throw new IOException("Ambiguous class entries in fixed-model preflight: " + node.name);
                } catch (RuntimeException invalid) {
                    // An unreadable class cannot establish a positive ancestry or renderer proof.
                }
            }
        }
        return result;
    }
}
