package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Conservative, non-executing inventory of legacy source-class dependencies. */
public final class LegacyClassDependencyAnalyzer {
    public enum Reachability { POTENTIALLY_REACHABLE, UNRESOLVED_NOT_PROVEN_UNREACHABLE }
    public enum CandidateState { NOT_INSPECTED, ORIGINAL_BYTES_RETAINED, REWRITTEN_UNPROVEN, ABSENT_REPLACEMENT_UNPROVEN }

    public record ClassDependency(
            String sourceClass, String sourceSha256, List<String> roles, String declaredSide,
            Reachability reachability, CandidateState candidateState, List<String> rootEvidence,
            List<String> sourceReferences, List<String> incomingSourceReferences,
            List<String> generatedReferences, List<String> resourceReferences,
            List<String> externalReferences, List<String> capabilities,
            List<String> dynamicEvidence, String modernReplacement, String action) { }

    public record CapabilitySummary(String name, List<String> directSourceClasses,
                                    List<String> potentiallyReachableDependents,
                                    List<String> unresolvedDependents) { }

    public record Analysis(List<ClassDependency> classes, List<CapabilitySummary> capabilities,
                           List<String> diagnostics, List<String> limitations) {
        public long potentiallyReachableCount() {
            return classes.stream().filter(value -> value.reachability() == Reachability.POTENTIALLY_REACHABLE).count();
        }
    }

    private record Scanned(String name, byte[] bytes, ClassNode node, Set<String> refs,
                           Set<String> strings, Set<String> dynamic) { }

    public Analysis analyze(Path sourceJar) throws IOException { return analyze(sourceJar, null); }

    public Analysis analyze(Path sourceJar, Path stagingDir) throws IOException {
        List<String> diagnostics = new ArrayList<>();
        Map<String, Scanned> source = scanJar(sourceJar, diagnostics);
        Set<String> names = source.keySet();
        Map<String, Set<String>> edges = new TreeMap<>(), incoming = new TreeMap<>(), roots = new TreeMap<>();
        Map<String, Set<String>> generated = new TreeMap<>();

        for (Scanned value : source.values()) {
            Set<String> local = new TreeSet<>();
            for (String ref : value.refs()) if (names.contains(ref) && !ref.equals(value.name())) local.add(ref);
            for (String literal : value.strings()) {
                String ref = literal.replace('.', '/');
                if (names.contains(ref) && !ref.equals(value.name())) local.add(ref);
            }
            edges.put(value.name(), local);
            for (String ref : local) add(incoming, ref, value.name());
            collectFmlRoots(value.node(), names, roots);
        }
        collectManifestAndServices(sourceJar, names, roots, diagnostics);
        Map<String, Set<String>> resourceReferences = collectResourceReferences(sourceJar, names, diagnostics);
        resourceReferences.forEach((sourceClass, evidence) -> {
            for (String resource : evidence)
                add(roots, sourceClass, "source resource symbolic reference: " + resource);
        });

        Map<String, Scanned> staged = stagingDir == null ? Map.of() : scanDirectory(stagingDir, diagnostics);
        if (stagingDir != null) {
            for (Scanned value : staged.values()) {
                if (names.contains(value.name())) continue;
                for (String ref : value.refs()) if (names.contains(ref)) add(generated, ref, value.name());
                for (String literal : value.strings()) {
                    String ref = literal.replace('.', '/');
                    if (names.contains(ref)) add(generated, ref, value.name() + " (string)");
                }
            }
            generated.forEach((sourceClass, evidence) -> {
                for (String generatedClass : evidence) add(roots, sourceClass, "generated class symbolic reference: " + generatedClass);
            });
        }

        Set<String> reachable = closure(roots.keySet(), edges);
        Map<String, Set<String>> capabilityOwners = new TreeMap<>();
        List<ClassDependency> classes = new ArrayList<>();
        for (Scanned value : source.values()) {
            Set<String> external = new TreeSet<>(value.refs());
            external.removeAll(names);
            Set<String> capabilities = new TreeSet<>();
            for (String ref : external) capability(ref).ifPresent(capabilities::add);
            for (String cap : capabilities) add(capabilityOwners, cap, value.name());

            Reachability reachability = reachable.contains(value.name())
                    ? Reachability.POTENTIALLY_REACHABLE : Reachability.UNRESOLVED_NOT_PROVEN_UNREACHABLE;
            CandidateState state = candidateState(value, staged, stagingDir != null);
            String action = !value.dynamic().isEmpty() || reachability == Reachability.UNRESOLVED_NOT_PROVEN_UNREACHABLE
                    ? "unresolved" : capabilities.isEmpty() ? "convert" : "adapter_or_convert";
            classes.add(new ClassDependency(
                    value.name(), sha256(value.bytes()), roles(value.name(), source), declaredSide(value.node()),
                    reachability, state, values(roots, value.name()), values(edges, value.name()),
                    values(incoming, value.name()), values(generated, value.name()), values(resourceReferences, value.name()),
                    sorted(external), sorted(capabilities), sorted(value.dynamic()),
                    "not_proven_by_reference_analysis", action));
        }
        classes.sort(Comparator.comparing(ClassDependency::sourceClass));

        List<CapabilitySummary> graph = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : capabilityOwners.entrySet()) {
            Set<String> dependents = closure(entry.getValue(), incoming);
            Set<String> potential = new TreeSet<>(dependents); potential.retainAll(reachable);
            Set<String> unresolved = new TreeSet<>(dependents); unresolved.removeAll(reachable);
            graph.add(new CapabilitySummary(entry.getKey(), sorted(entry.getValue()), sorted(potential), sorted(unresolved)));
        }
        graph.sort(Comparator.<CapabilitySummary>comparingInt(v -> v.potentiallyReachableDependents().size())
                .reversed().thenComparing(CapabilitySummary::name));

        return new Analysis(List.copyOf(classes), List.copyOf(graph), List.copyOf(new LinkedHashSet<>(diagnostics)), List.of(
                "Symbolic references over-approximate execution and may include dormant code.",
                "Reflection, native code, binary or oversized resources and external integrations can hide runtime edges.",
                "Not statically reachable is not proof that a source class is safe to remove.",
                "Generated references are dependency evidence; they do not prove complete modern semantic replacement.",
                "This analysis never authorizes source-class exclusion or changes loader-safety status."));
    }

    private static Map<String, Scanned> scanJar(Path jarPath, List<String> diagnostics) throws IOException {
        Map<String, Scanned> result = new TreeMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    Scanned scanned = scan(input.readAllBytes(), diagnostics, entry.getName());
                    if (scanned != null && result.put(scanned.name(), scanned) != null)
                        throw new IOException("Duplicate source class: " + scanned.name());
                }
            }
        }
        return result;
    }

    private static Map<String, Scanned> scanDirectory(Path root, List<String> diagnostics) throws IOException {
        Map<String, Scanned> result = new TreeMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(v -> v.getFileName().toString().endsWith(".class")).sorted().toList()) {
                Scanned scanned = scan(Files.readAllBytes(path), diagnostics, root.relativize(path).toString());
                if (scanned != null) result.put(scanned.name(), scanned);
            }
        }
        return result;
    }

    private static Scanned scan(byte[] bytes, List<String> diagnostics, String source) {
        try {
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            Set<String> refs = new TreeSet<>(), strings = new TreeSet<>(), dynamic = new TreeSet<>();
            add(refs, node.superName); node.interfaces.forEach(v -> add(refs, v));
            annotations(node.visibleAnnotations, refs, strings); annotations(node.invisibleAnnotations, refs, strings);
            for (FieldNode field : node.fields) {
                descriptor(refs, field.desc); annotations(field.visibleAnnotations, refs, strings); annotations(field.invisibleAnnotations, refs, strings);
            }
            for (MethodNode method : node.methods) {
                methodDescriptor(refs, method.desc);
                if (method.exceptions != null) method.exceptions.forEach(v -> add(refs, v));
                annotations(method.visibleAnnotations, refs, strings); annotations(method.invisibleAnnotations, refs, strings);
                if ((method.access & Opcodes.ACC_NATIVE) != 0) dynamic.add("native method: " + method.name + method.desc);
                for (TryCatchBlockNode block : method.tryCatchBlocks) add(refs, block.type);
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof TypeInsnNode v) typeInsn(refs, v.desc);
                    else if (insn instanceof FieldInsnNode v) { add(refs, v.owner); descriptor(refs, v.desc); }
                    else if (insn instanceof MethodInsnNode v) { add(refs, v.owner); methodDescriptor(refs, v.desc); dynamic(v, dynamic); }
                    else if (insn instanceof MultiANewArrayInsnNode v) descriptor(refs, v.desc);
                    else if (insn instanceof LdcInsnNode v) constant(refs, strings, v.cst);
                    else if (insn instanceof InvokeDynamicInsnNode v) {
                        methodDescriptor(refs, v.desc); handle(refs, v.bsm); dynamic.add("invokedynamic: " + v.name + v.desc);
                        for (Object arg : v.bsmArgs) constant(refs, strings, arg);
                    }
                }
            }
            refs.remove(node.name);
            return new Scanned(node.name, bytes, node, refs, strings, dynamic);
        } catch (RuntimeException malformed) {
            diagnostics.add("Unreadable dependency-analysis class " + source + ": " + malformed.getClass().getSimpleName());
            return null;
        }
    }

    private static void collectFmlRoots(ClassNode node, Set<String> names, Map<String, Set<String>> roots) {
        for (AnnotationNode annotation : all(node.visibleAnnotations, node.invisibleAnnotations)) {
            if ("Lcpw/mods/fml/common/Mod;".equals(annotation.desc)) {
                add(roots, node.name, "FML @Mod entrypoint");
                configuredRoot(annotationValue(annotation, "guiFactory"), "FML @Mod guiFactory declared by " + node.name, names, roots);
            }
        }
        for (FieldNode field : node.fields) {
            for (AnnotationNode annotation : all(field.visibleAnnotations, field.invisibleAnnotations)) {
                if (!"Lcpw/mods/fml/common/SidedProxy;".equals(annotation.desc)) continue;
                configuredRoot(annotationValue(annotation, "clientSide"), "FML @SidedProxy clientSide declared by " + node.name, names, roots);
                configuredRoot(annotationValue(annotation, "serverSide"), "FML @SidedProxy serverSide declared by " + node.name, names, roots);
            }
        }
    }

    private static void collectManifestAndServices(Path jarPath, Set<String> names,
                                                   Map<String, Set<String>> roots,
                                                   List<String> diagnostics) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            if (jar.getManifest() != null) {
                String plugin = jar.getManifest().getMainAttributes().getValue("FMLCorePlugin");
                if (plugin != null && !plugin.isBlank()) {
                    String internal = plugin.trim().replace('.', '/');
                    if (names.contains(internal)) add(roots, internal, "manifest FMLCorePlugin activation");
                    else diagnostics.add("Activated FMLCorePlugin is outside readable source classes: " + plugin);
                }
            }
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith("META-INF/services/")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                        String provider = line.split("#", 2)[0].trim().replace('.', '/');
                        if (names.contains(provider)) add(roots, provider, "service provider: " + entry.getName());
                    }
                }
            }
        }
    }

    private static final int RESOURCE_SCAN_LIMIT = 2 * 1024 * 1024;
    private static final Set<String> TEXT_RESOURCE_SUFFIXES = Set.of(
            ".json", ".info", ".cfg", ".conf", ".properties", ".txt", ".xml", ".lang",
            ".toml", ".yml", ".yaml", ".list", ".ini", ".csv", ".tsv", ".accesswidener");

    /**
     * Conservatively records source classes named by bounded text resources. Binary assets are
     * deliberately excluded; unresolved binary/custom formats remain covered by the limitation.
     */
    private static Map<String, Set<String>> collectResourceReferences(
            Path jarPath, Set<String> names, List<String> diagnostics) throws IOException {
        Map<String, Set<String>> references = new TreeMap<>();
        List<String> orderedNames = names.stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().endsWith(".class") || !textResource(entry.getName())) continue;
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    bytes = input.readNBytes(RESOURCE_SCAN_LIMIT + 1);
                }
                if (bytes.length > RESOURCE_SCAN_LIMIT) {
                    diagnostics.add("Skipped oversized dependency-analysis resource: " + entry.getName());
                    continue;
                }
                String text = new String(bytes, StandardCharsets.UTF_8);
                for (String name : orderedNames) {
                    String dotted = name.replace('/', '.');
                    if (containsClassToken(text, name) || containsClassToken(text, dotted)
                            || text.contains("L" + name + ";"))
                        add(references, name, entry.getName());
                }
            }
        }
        return references;
    }

    private static boolean textResource(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("meta-inf/services/")) return true;
        if (lower.equals("meta-inf/manifest.mf")) return false;
        for (String suffix : TEXT_RESOURCE_SUFFIXES) if (lower.endsWith(suffix)) return true;
        return false;
    }

    private static boolean containsClassToken(String text, String token) {
        int from = 0;
        while (from <= text.length() - token.length()) {
            int index = text.indexOf(token, from);
            if (index < 0) return false;
            int before = index - 1, after = index + token.length();
            boolean left = before < 0 || !classTokenChar(text.charAt(before));
            boolean right = after >= text.length() || !classTokenChar(text.charAt(after));
            if (left && right) return true;
            from = index + 1;
        }
        return false;
    }

    private static boolean classTokenChar(char value) {
        return Character.isJavaIdentifierPart(value) || value == '.' || value == '/';
    }

    private static CandidateState candidateState(Scanned source, Map<String, Scanned> staged, boolean inspected) {
        if (!inspected) return CandidateState.NOT_INSPECTED;
        Scanned candidate = staged.get(source.name());
        if (candidate == null) return CandidateState.ABSENT_REPLACEMENT_UNPROVEN;
        return Arrays.equals(source.bytes(), candidate.bytes())
                ? CandidateState.ORIGINAL_BYTES_RETAINED : CandidateState.REWRITTEN_UNPROVEN;
    }

    private static List<String> roles(String name, Map<String, Scanned> classes) {
        Set<String> hierarchy = new TreeSet<>();
        String cursor = name;
        while (cursor != null && hierarchy.add(cursor)) {
            Scanned current = classes.get(cursor);
            if (current == null) break;
            hierarchy.addAll(current.node().interfaces);
            cursor = current.node().superName;
        }
        Scanned value = classes.get(name);
        Set<String> roles = new TreeSet<>();
        Map.ofEntries(
                Map.entry("net/minecraft/item/Item", "item"), Map.entry("net/minecraft/item/ItemBlock", "item_block"),
                Map.entry("net/minecraft/item/ItemFood", "food"), Map.entry("net/minecraft/item/ItemSeeds", "seeds"),
                Map.entry("net/minecraft/item/ItemSeedFood", "seed_food"), Map.entry("net/minecraft/item/ItemReed", "placement_item"),
                Map.entry("net/minecraft/block/Block", "block"), Map.entry("net/minecraft/tileentity/TileEntity", "block_entity"),
                Map.entry("net/minecraft/inventory/IInventory", "inventory"), Map.entry("net/minecraft/inventory/ISidedInventory", "inventory"),
                Map.entry("net/minecraft/inventory/Container", "menu"), Map.entry("net/minecraft/client/gui/inventory/GuiContainer", "gui"),
                Map.entry("net/minecraft/entity/Entity", "entity"), Map.entry("net/minecraft/entity/projectile/EntityThrowable", "projectile"),
                Map.entry("net/minecraft/entity/projectile/EntityArrow", "projectile"), Map.entry("cpw/mods/fml/common/IWorldGenerator", "world_generator"),
                Map.entry("net/minecraft/launchwrapper/IClassTransformer", "transformer")
        ).forEach((type, role) -> { if (hierarchy.contains(type)) roles.add(role); });
        if (value != null && all(value.node().visibleAnnotations, value.node().invisibleAnnotations).stream()
                .anyMatch(a -> "Lcpw/mods/fml/common/Mod;".equals(a.desc))) roles.add("mod_entrypoint");
        if (roles.isEmpty()) roles.add("helper_or_unclassified");
        return sorted(roles);
    }

    private static String declaredSide(ClassNode node) {
        for (AnnotationNode annotation : all(node.visibleAnnotations, node.invisibleAnnotations)) {
            if (!"Lcpw/mods/fml/relauncher/SideOnly;".equals(annotation.desc)) continue;
            Object value = annotationValue(annotation, "value");
            if (value instanceof String[] enumValue && enumValue.length == 2) return enumValue[1];
        }
        return "unspecified";
    }

    private static Optional<String> capability(String ref) {
        if (ref.startsWith("net/minecraft/tileentity/")) return Optional.of("block_entity");
        if (ref.startsWith("net/minecraft/inventory/")) return Optional.of("inventory_menu");
        if (ref.startsWith("net/minecraft/nbt/")) return Optional.of("nbt");
        if (ref.startsWith("net/minecraft/client/gui/")) return Optional.of("client_gui");
        if (ref.startsWith("org/lwjgl/opengl/") || ref.startsWith("net/minecraft/client/renderer/") || ref.startsWith("net/minecraftforge/client/")) return Optional.of("rendering");
        if (ref.startsWith("net/minecraft/entity/projectile/")) return Optional.of("projectile_entity");
        if (ref.equals("net/minecraft/entity/DataWatcher")) return Optional.of("entity_tracking");
        if (ref.startsWith("net/minecraft/entity/")) return Optional.of("entity_runtime");
        if (Set.of("net/minecraft/item/ItemFood", "net/minecraft/item/ItemSeeds", "net/minecraft/item/ItemSeedFood", "net/minecraft/item/ItemReed", "net/minecraft/item/ItemSnowball", "net/minecraft/item/ItemBed").contains(ref)) return Optional.of("item_family");
        if (ref.startsWith("net/minecraft/item/crafting/") || ref.startsWith("net/minecraftforge/oredict/")) return Optional.of("recipe_ore");
        if (ref.startsWith("net/minecraft/block/")) return Optional.of("block_behavior");
        if (ref.startsWith("net/minecraft/world/gen/") || ref.startsWith("net/minecraft/world/biome/") || ref.equals("net/minecraft/world/WorldProvider") || ref.equals("net/minecraftforge/common/DimensionManager") || ref.equals("cpw/mods/fml/common/IWorldGenerator")) return Optional.of("worldgen_dimension");
        if (ref.startsWith("net/minecraft/world/")) return Optional.of("world_state");
        if (ref.startsWith("net/minecraftforge/event/") || ref.startsWith("cpw/mods/fml/common/gameevent/") || ref.startsWith("cpw/mods/fml/common/eventhandler/")) return Optional.of("events");
        if (ref.startsWith("cpw/mods/fml/common/network/") || ref.startsWith("net/minecraft/network/")) return Optional.of("network");
        if (ref.startsWith("cpw/mods/fml/relauncher/") || ref.startsWith("net/minecraft/launchwrapper/")) return Optional.of("legacy_loader");
        return Optional.empty();
    }

    private static void dynamic(MethodInsnNode call, Set<String> output) {
        if (call.owner.startsWith("java/lang/reflect/")
                || call.owner.equals("java/lang/ClassLoader") && call.name.equals("loadClass")
                || call.owner.equals("java/util/ServiceLoader") && call.name.equals("load")
                || call.owner.equals("java/lang/Class") && Set.of("forName", "newInstance", "getMethod", "getDeclaredMethod", "getField", "getDeclaredField").contains(call.name)
                || call.owner.contains("ReflectionHelper")) {
            output.add(call.owner + "." + call.name + call.desc);
        }
    }

    private static void annotations(List<AnnotationNode> list, Set<String> refs, Set<String> strings) {
        if (list == null) return;
        for (AnnotationNode annotation : list) {
            descriptor(refs, annotation.desc);
            if (annotation.values == null) continue;
            for (int i = 1; i < annotation.values.size(); i += 2) annotationValueRefs(annotation.values.get(i), refs, strings);
        }
    }

    private static void annotationValueRefs(Object value, Set<String> refs, Set<String> strings) {
        if (value instanceof Type type) type(refs, type);
        else if (value instanceof AnnotationNode annotation) annotations(List.of(annotation), refs, strings);
        else if (value instanceof List<?> list) list.forEach(v -> annotationValueRefs(v, refs, strings));
        else if (value instanceof String[] enumValue && enumValue.length == 2) descriptor(refs, enumValue[0]);
        else if (value instanceof String text) strings.add(text);
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static List<AnnotationNode> all(List<AnnotationNode> first, List<AnnotationNode> second) {
        List<AnnotationNode> result = new ArrayList<>();
        if (first != null) result.addAll(first); if (second != null) result.addAll(second); return result;
    }

    private static void configuredRoot(Object value, String evidence, Set<String> names, Map<String, Set<String>> roots) {
        if (value instanceof String text) { String name = text.replace('.', '/'); if (names.contains(name)) add(roots, name, evidence); }
    }

    private static void constant(Set<String> refs, Set<String> strings, Object value) {
        if (value instanceof Type type) type(refs, type);
        else if (value instanceof Handle handle) handle(refs, handle);
        else if (value instanceof String text) strings.add(text);
    }

    private static void handle(Set<String> refs, Handle handle) {
        add(refs, handle.getOwner());
        if (handle.getDesc().startsWith("(")) methodDescriptor(refs, handle.getDesc()); else descriptor(refs, handle.getDesc());
    }

    private static void typeInsn(Set<String> refs, String value) {
        if (value != null && value.startsWith("[")) descriptor(refs, value); else add(refs, value);
    }
    private static void methodDescriptor(Set<String> refs, String desc) { Type type = Type.getMethodType(desc); type(refs, type.getReturnType()); for (Type arg : type.getArgumentTypes()) type(refs, arg); }
    private static void descriptor(Set<String> refs, String desc) { if (desc != null) type(refs, Type.getType(desc)); }
    private static void type(Set<String> refs, Type type) { if (type.getSort() == Type.ARRAY) type(refs, type.getElementType()); else if (type.getSort() == Type.OBJECT) add(refs, type.getInternalName()); else if (type.getSort() == Type.METHOD) methodDescriptor(refs, type.getDescriptor()); }
    private static void add(Set<String> set, String value) { if (value != null && !value.isBlank()) set.add(value); }
    private static void add(Map<String, Set<String>> map, String key, String value) { map.computeIfAbsent(key, ignored -> new TreeSet<>()).add(value); }
    private static List<String> values(Map<String, Set<String>> map, String key) { return sorted(map.getOrDefault(key, Set.of())); }
    private static List<String> sorted(Collection<String> values) { return values.stream().sorted().toList(); }
    private static Set<String> closure(Collection<String> roots, Map<String, Set<String>> graph) { Set<String> seen = new TreeSet<>(); ArrayDeque<String> queue = new ArrayDeque<>(roots); while (!queue.isEmpty()) { String value = queue.removeFirst(); if (seen.add(value)) queue.addAll(graph.getOrDefault(value, Set.of())); } return seen; }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
}
