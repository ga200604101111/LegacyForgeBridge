package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Targeted, non-executing reference scan over the current conversion staging tree.
 *
 * <p>Unlike {@link LegacyClassDependencyAnalyzer}, this scan intentionally reads the candidate
 * bytes currently staged after semantic/codegen passes. It is used only as retirement-readiness
 * evidence for a caller-supplied set of source classes; it never deletes a class.</p>
 */
public final class LegacyCandidateReferenceAnalyzer {
    private static final long MAX_RESOURCE_SCAN_BYTES = 32L * 1024L * 1024L;

    public record Evidence(String targetClass, List<String> incomingClassReferences,
                           List<String> resourceReferences) { }

    public record Analysis(List<Evidence> evidence, boolean classReferenceClosureComplete,
                           boolean resourceReferenceClosureComplete, List<String> diagnostics) {
        public Evidence forTarget(String targetClass) {
            return evidence.stream().filter(value -> value.targetClass().equals(targetClass))
                    .findFirst().orElse(new Evidence(targetClass, List.of(), List.of()));
        }
        public boolean complete() {
            return classReferenceClosureComplete && resourceReferenceClosureComplete;
        }
    }

    public Analysis analyze(Path stagingDir, Set<String> targetClasses) throws IOException {
        TreeSet<String> targets = new TreeSet<>();
        for (String target : targetClasses) if (target != null && !target.isBlank()) targets.add(target);
        if (targets.isEmpty()) return new Analysis(List.of(), true, true, List.of());

        Map<String,Set<String>> incoming = new TreeMap<>(), resources = new TreeMap<>();
        List<String> diagnostics = new ArrayList<>();
        boolean classComplete = true, resourceComplete = true;

        try (Stream<Path> stream = Files.walk(stagingDir)) {
            for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                String relative = stagingDir.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".class")) {
                    try {
                        scanClass(Files.readAllBytes(path), relative, targets, incoming);
                    } catch (RuntimeException malformed) {
                        classComplete = false;
                        diagnostics.add("Unreadable candidate class while proving retirement references: "
                                + relative + " (" + malformed.getClass().getSimpleName() + ")");
                    }
                    continue;
                }
                if (relative.startsWith("legacyforgebridge/")) continue;
                long size = Files.size(path);
                if (size > MAX_RESOURCE_SCAN_BYTES) {
                    resourceComplete = false;
                    diagnostics.add("Candidate resource exceeds retirement reference scan bound: "
                            + relative + " (" + size + " bytes)");
                    continue;
                }
                byte[] bytes = Files.readAllBytes(path);
                for (String target : targets) {
                    if (contains(bytes, target.getBytes(StandardCharsets.UTF_8))
                            || contains(bytes, target.replace('/', '.').getBytes(StandardCharsets.UTF_8)))
                        add(resources, target, relative);
                }
            }
        }

        List<Evidence> evidence = new ArrayList<>();
        for (String target : targets) evidence.add(new Evidence(target,
                values(incoming, target), values(resources, target)));
        return new Analysis(List.copyOf(evidence), classComplete, resourceComplete,
                List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static void scanClass(byte[] bytes, String relative, Set<String> targets,
                                  Map<String,Set<String>> incoming) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Set<String> references = new TreeSet<>(), strings = new TreeSet<>();
        add(references, node.superName);
        if (node.interfaces != null) node.interfaces.forEach(value -> add(references, value));
        LegacySignatureReferenceCollector.classOrMethod(references, node.signature);
        annotations(node.visibleAnnotations, references, strings);
        annotations(node.invisibleAnnotations, references, strings);
        for (FieldNode field : node.fields) {
            descriptor(references, field.desc);
            LegacySignatureReferenceCollector.field(references, field.signature);
            annotations(field.visibleAnnotations, references, strings);
            annotations(field.invisibleAnnotations, references, strings);
        }
        for (MethodNode method : node.methods) {
            methodDescriptor(references, method.desc);
            LegacySignatureReferenceCollector.classOrMethod(references, method.signature);
            if (method.exceptions != null) method.exceptions.forEach(value -> add(references, value));
            annotations(method.visibleAnnotations, references, strings);
            annotations(method.invisibleAnnotations, references, strings);
            for (TryCatchBlockNode block : method.tryCatchBlocks) add(references, block.type);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode value) typeInsn(references, value.desc);
                else if (instruction instanceof FieldInsnNode value) {
                    add(references, value.owner); descriptor(references, value.desc);
                } else if (instruction instanceof MethodInsnNode value) {
                    add(references, value.owner); methodDescriptor(references, value.desc);
                } else if (instruction instanceof MultiANewArrayInsnNode value) descriptor(references, value.desc);
                else if (instruction instanceof LdcInsnNode value) constant(references, strings, value.cst);
                else if (instruction instanceof InvokeDynamicInsnNode value) {
                    methodDescriptor(references, value.desc); handle(references, value.bsm);
                    for (Object argument : value.bsmArgs) constant(references, strings, argument);
                }
            }
        }
        String owner = node.name == null ? relative.replaceAll("\\.class$", "") : node.name;
        for (String target : targets) {
            if (target.equals(owner)) continue;
            if (references.contains(target) || strings.contains(target) || strings.contains(target.replace('/', '.')))
                add(incoming, target, owner);
        }
    }

    private static void annotations(List<AnnotationNode> annotations, Set<String> refs, Set<String> strings) {
        if (annotations == null) return;
        for (AnnotationNode annotation : annotations) {
            descriptor(refs, annotation.desc);
            if (annotation.values == null) continue;
            for (int index = 1; index < annotation.values.size(); index += 2)
                annotationValue(refs, strings, annotation.values.get(index));
        }
    }

    private static void annotationValue(Set<String> refs, Set<String> strings, Object value) {
        if (value instanceof Type type) type(refs, type);
        else if (value instanceof String text) strings.add(text);
        else if (value instanceof String[] enumValue && enumValue.length > 0) descriptor(refs, enumValue[0]);
        else if (value instanceof AnnotationNode annotation) annotations(List.of(annotation), refs, strings);
        else if (value instanceof List<?> list) for (Object item : list) annotationValue(refs, strings, item);
    }

    private static void constant(Set<String> refs, Set<String> strings, Object value) {
        if (value instanceof Type type) type(refs, type);
        else if (value instanceof Handle handle) handle(refs, handle);
        else if (value instanceof String text) strings.add(text);
    }

    private static void handle(Set<String> refs, Handle handle) {
        if (handle == null) return;
        add(refs, handle.getOwner());
        if (handle.getDesc().startsWith("(")) methodDescriptor(refs, handle.getDesc());
        else descriptor(refs, handle.getDesc());
    }

    private static void typeInsn(Set<String> refs, String value) {
        if (value == null) return;
        if (value.startsWith("[")) descriptor(refs, value); else add(refs, value);
    }

    private static void descriptor(Set<String> refs, String descriptor) {
        if (descriptor == null) return;
        try { type(refs, Type.getType(descriptor)); } catch (IllegalArgumentException ignored) { }
    }

    private static void methodDescriptor(Set<String> refs, String descriptor) {
        if (descriptor == null) return;
        try {
            type(refs, Type.getReturnType(descriptor));
            for (Type argument : Type.getArgumentTypes(descriptor)) type(refs, argument);
        } catch (IllegalArgumentException ignored) { }
    }

    private static void type(Set<String> refs, Type type) {
        if (type == null) return;
        if (type.getSort() == Type.OBJECT) add(refs, type.getInternalName());
        else if (type.getSort() == Type.ARRAY) type(refs, type.getElementType());
        else if (type.getSort() == Type.METHOD) {
            type(refs, type.getReturnType());
            for (Type argument : type.getArgumentTypes()) type(refs, argument);
        }
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) return false;
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    private static void add(Set<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }
    private static void add(Map<String,Set<String>> values, String key, String value) {
        if (key == null || value == null) return;
        values.computeIfAbsent(key, ignored -> new TreeSet<>()).add(value);
    }
    private static List<String> values(Map<String,Set<String>> values, String key) {
        Set<String> found = values.get(key);
        if (found == null) return List.of();
        return found.stream().sorted(Comparator.naturalOrder()).toList();
    }
}
