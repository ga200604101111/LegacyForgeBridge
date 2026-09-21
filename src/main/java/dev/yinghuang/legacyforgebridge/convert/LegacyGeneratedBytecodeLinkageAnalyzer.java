package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Final linkage proof for LFB-generated wrapper classes.
 *
 * <p>Source stripping proves that no generated class points back to a retired source class. This
 * analyzer closes the other loader-safety hole: a generated wrapper must also not link directly to
 * Forge/FML/LaunchWrapper APIs that are absent from the modern Fabric client.</p>
 */
public final class LegacyGeneratedBytecodeLinkageAnalyzer {
    public static final String GENERATED_PREFIX = "dev/yinghuang/legacyforgebridge/generated/";
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "cpw/mods/fml/",
            "net/minecraftforge/",
            "net/minecraft/launchwrapper/"
    );

    public record Finding(String generatedClass, List<String> legacyReferences) {
        public Finding { legacyReferences = List.copyOf(legacyReferences); }
    }

    public record Analysis(List<Finding> findings, List<String> diagnostics) {
        public Analysis {
            findings = List.copyOf(findings);
            diagnostics = List.copyOf(diagnostics);
        }
        public boolean complete() { return diagnostics.isEmpty(); }
        public boolean clean() { return complete() && findings.isEmpty(); }
    }

    public Analysis analyze(Path stagingDir) throws IOException {
        List<Finding> findings = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(stagingDir)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".class"))
                    .sorted().toList()) {
                String relative = stagingDir.relativize(path).toString().replace('\\', '/');
                if (!relative.startsWith(GENERATED_PREFIX)) continue;
                try {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(Files.readAllBytes(path)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    Set<String> legacy = new TreeSet<>();
                    for (String reference : references(node)) if (forbidden(reference)) legacy.add(reference);
                    if (!legacy.isEmpty()) findings.add(new Finding(
                            node.name == null ? relative.substring(0, relative.length() - 6) : node.name,
                            List.copyOf(legacy)));
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable generated wrapper while proving legacy API linkage closure: "
                            + relative + " (" + malformed.getClass().getSimpleName() + ")");
                }
            }
        }
        findings.sort(Comparator.comparing(Finding::generatedClass));
        return new Analysis(findings, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static boolean forbidden(String reference) {
        if (reference == null) return false;
        for (String prefix : FORBIDDEN_PREFIXES) if (reference.startsWith(prefix)) return true;
        return false;
    }

    private static Set<String> references(ClassNode node) {
        Set<String> refs = new TreeSet<>();
        add(refs, node.superName);
        if (node.interfaces != null) node.interfaces.forEach(value -> add(refs, value));
        add(refs, node.outerClass);
        methodDescriptor(refs, node.outerMethodDesc);
        add(refs, node.nestHostClass);
        if (node.nestMembers != null) node.nestMembers.forEach(value -> add(refs, value));
        if (node.permittedSubclasses != null) node.permittedSubclasses.forEach(value -> add(refs, value));
        LegacySignatureReferenceCollector.classOrMethod(refs, node.signature);
        annotations(node.visibleAnnotations, refs);
        annotations(node.invisibleAnnotations, refs);
        annotations(node.visibleTypeAnnotations, refs);
        annotations(node.invisibleTypeAnnotations, refs);

        if (node.innerClasses != null) for (InnerClassNode inner : node.innerClasses) {
            add(refs, inner.name); add(refs, inner.outerName);
        }
        if (node.module != null) {
            add(refs, node.module.mainClass);
            if (node.module.uses != null) node.module.uses.forEach(value -> add(refs, value));
            if (node.module.provides != null) for (ModuleProvideNode value : node.module.provides) {
                add(refs, value.service);
                if (value.providers != null) value.providers.forEach(provider -> add(refs, provider));
            }
        }
        if (node.recordComponents != null) for (RecordComponentNode component : node.recordComponents) {
            descriptor(refs, component.descriptor);
            LegacySignatureReferenceCollector.field(refs, component.signature);
            annotations(component.visibleAnnotations, refs);
            annotations(component.invisibleAnnotations, refs);
            annotations(component.visibleTypeAnnotations, refs);
            annotations(component.invisibleTypeAnnotations, refs);
        }

        for (FieldNode field : node.fields) {
            descriptor(refs, field.desc);
            LegacySignatureReferenceCollector.field(refs, field.signature);
            annotations(field.visibleAnnotations, refs);
            annotations(field.invisibleAnnotations, refs);
            annotations(field.visibleTypeAnnotations, refs);
            annotations(field.invisibleTypeAnnotations, refs);
        }

        for (MethodNode method : node.methods) {
            methodDescriptor(refs, method.desc);
            LegacySignatureReferenceCollector.classOrMethod(refs, method.signature);
            if (method.exceptions != null) method.exceptions.forEach(value -> add(refs, value));
            annotations(method.visibleAnnotations, refs);
            annotations(method.invisibleAnnotations, refs);
            annotations(method.visibleTypeAnnotations, refs);
            annotations(method.invisibleTypeAnnotations, refs);
            parameterAnnotations(method.visibleParameterAnnotations, refs);
            parameterAnnotations(method.invisibleParameterAnnotations, refs);
            annotationValue(refs, method.annotationDefault);
            annotations(method.visibleLocalVariableAnnotations, refs);
            annotations(method.invisibleLocalVariableAnnotations, refs);

            for (TryCatchBlockNode block : method.tryCatchBlocks) {
                add(refs, block.type);
                annotations(block.visibleTypeAnnotations, refs);
                annotations(block.invisibleTypeAnnotations, refs);
            }
            for (AbstractInsnNode instruction : method.instructions) {
                annotations(instruction.visibleTypeAnnotations, refs);
                annotations(instruction.invisibleTypeAnnotations, refs);
                if (instruction instanceof TypeInsnNode value) typeInsn(refs, value.desc);
                else if (instruction instanceof FieldInsnNode value) {
                    add(refs, value.owner); descriptor(refs, value.desc);
                } else if (instruction instanceof MethodInsnNode value) {
                    add(refs, value.owner); methodDescriptor(refs, value.desc);
                } else if (instruction instanceof MultiANewArrayInsnNode value) descriptor(refs, value.desc);
                else if (instruction instanceof LdcInsnNode value) constant(refs, value.cst);
                else if (instruction instanceof InvokeDynamicInsnNode value) {
                    methodDescriptor(refs, value.desc);
                    handle(refs, value.bsm);
                    for (Object argument : value.bsmArgs) constant(refs, argument);
                }
            }
        }
        refs.remove(node.name);
        return refs;
    }

    private static void parameterAnnotations(List<AnnotationNode>[] groups, Set<String> refs) {
        if (groups == null) return;
        for (List<AnnotationNode> group : groups) annotations(group, refs);
    }

    private static void annotations(List<? extends AnnotationNode> values, Set<String> refs) {
        if (values == null) return;
        for (AnnotationNode annotation : values) {
            descriptor(refs, annotation.desc);
            if (annotation.values == null) continue;
            for (int index = 1; index < annotation.values.size(); index += 2)
                annotationValue(refs, annotation.values.get(index));
        }
    }

    private static void annotationValue(Set<String> refs, Object value) {
        if (value instanceof Type type) type(refs, type);
        else if (value instanceof String[] enumValue && enumValue.length > 0) descriptor(refs, enumValue[0]);
        else if (value instanceof AnnotationNode annotation) annotations(List.of(annotation), refs);
        else if (value instanceof List<?> list) for (Object item : list) annotationValue(refs, item);
    }

    private static void constant(Set<String> refs, Object value) {
        if (value instanceof Type type) type(refs, type);
        else if (value instanceof Handle handle) handle(refs, handle);
        else if (value instanceof ConstantDynamic dynamic) {
            descriptor(refs, dynamic.getDescriptor());
            handle(refs, dynamic.getBootstrapMethod());
            for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++)
                constant(refs, dynamic.getBootstrapMethodArgument(i));
        }
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
        type(refs, Type.getType(descriptor));
    }

    private static void methodDescriptor(Set<String> refs, String descriptor) {
        if (descriptor == null) return;
        type(refs, Type.getReturnType(descriptor));
        for (Type argument : Type.getArgumentTypes(descriptor)) type(refs, argument);
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

    private static void add(Set<String> refs, String value) {
        if (value != null && !value.isBlank()) refs.add(value);
    }
}
