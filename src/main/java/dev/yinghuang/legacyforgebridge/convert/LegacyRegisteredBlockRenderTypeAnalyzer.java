package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-only inventory of exact legacy Block#getRenderType() identities for lifecycle/registry
 * registered mod blocks. Only direct constant or symbolic static-field returns are admitted; any
 * dataflow, arithmetic, branch-dependent mixed identity, or external inherited renderer stays
 * unresolved.
 */
public final class LegacyRegisteredBlockRenderTypeAnalyzer {
    private static final Set<String> RENDER_NAMES = Set.of("getRenderType", "func_149645_b");
    private static final String RENDER_DESC = "()I";

    public record RenderIdentity(Integer constant, String fieldOwner, String fieldName) {
        public RenderIdentity {
            boolean constantIdentity = constant != null;
            boolean fieldIdentity = fieldOwner != null && !fieldOwner.isBlank() && fieldName != null && !fieldName.isBlank();
            if (constantIdentity == fieldIdentity) throw new IllegalArgumentException("Render identity must be exactly one of constant or static field");
        }
        public static RenderIdentity constant(int value) { return new RenderIdentity(value, null, null); }
        public static RenderIdentity field(String owner, String name) { return new RenderIdentity(null, owner, name); }
        public boolean isConstant(int value) { return constant != null && constant == value; }
    }

    public record Rule(String registryName, String legacyNamespace, String sourceBlockClass,
                       RenderIdentity renderIdentity) { }
    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis { rules = List.copyOf(rules); diagnostics = List.copyOf(diagnostics); }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = loadClasses(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        LinkedHashSet<String> diagnostics = new LinkedHashSet<>();

        for (LegacyRegistryAnalyzer.Registration registration : registry.blocks()) {
            String sourceClass = registration.implementationClass();
            if (sourceClass == null) continue;
            MethodNode method = effectiveSourceMethod(classes, sourceClass);
            if (method == null) continue;
            RenderIdentity identity = directRenderIdentity(method);
            if (identity == null) {
                diagnostics.add("Registered block render type is not an exact direct constant/static-field return: "
                        + sourceClass + "." + method.name + method.desc);
                continue;
            }
            rules.add(new Rule(registration.registryName(), registration.legacyNamespace(), sourceClass, identity));
        }
        return new Analysis(rules, List.copyOf(diagnostics));
    }

    static RenderIdentity directRenderIdentity(MethodNode method) {
        if (method == null || !RENDER_NAMES.contains(method.name) || !RENDER_DESC.equals(method.desc)) return null;
        RenderIdentity result = null;
        boolean sawReturn = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.IRETURN) continue;
            sawReturn = true;
            AbstractInsnNode producer = previousReal(instruction);
            RenderIdentity candidate = identity(producer);
            if (candidate == null) return null;
            if (result == null) result = candidate;
            else if (!result.equals(candidate)) return null;
        }
        return sawReturn ? result : null;
    }

    private static RenderIdentity identity(AbstractInsnNode instruction) {
        Integer constant = intConstant(instruction);
        if (constant != null) return RenderIdentity.constant(constant);
        if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && "I".equals(field.desc))
            return RenderIdentity.field(field.owner, field.name);
        return null;
    }

    private static Integer intConstant(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        return switch (instruction.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.ICONST_0 -> 0;
            case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3;
            case Opcodes.ICONST_4 -> 4;
            case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) instruction).operand;
            case Opcodes.LDC -> instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value ? value : null;
            default -> null;
        };
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction == null ? null : instruction.getPrevious(); current != null; current = current.getPrevious())
            if (current.getOpcode() >= 0) return current;
        return null;
    }

    private static MethodNode effectiveSourceMethod(Map<String, ClassNode> classes, String sourceClass) {
        Set<String> visited = new LinkedHashSet<>();
        for (String current = sourceClass; current != null && visited.add(current); ) {
            ClassNode node = classes.get(current);
            if (node == null) return null;
            for (MethodNode method : node.methods)
                if ((method.access & Opcodes.ACC_STATIC) == 0 && RENDER_NAMES.contains(method.name) && RENDER_DESC.equals(method.desc)) return method;
            current = node.superName;
        }
        return null;
    }

    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) {
                    // The positive table is fail-closed: unreadable classes simply cannot become eligible.
                }
            }
        }
        return classes;
    }
}
