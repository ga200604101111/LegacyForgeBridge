package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

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
 * Proves the raw Minecraft 1.7.10 Material field passed into a source-owned Block constructor.
 *
 * <p>This is deliberately provenance-only. A proven Material reference does not imply any harvest
 * rule by itself: whether the material requires a tool, how Forge tool classes are matched, and how
 * a modern block should model that decision remain separate platform proofs. The analyzer records
 * the exact legacy field owner/name/descriptor without translating or interpreting the field name.</p>
 *
 * <p>The admitted topology is conservative: the registered implementation may have source-owned
 * superclasses, but the first external superclass must be exactly {@code Block}. The source class
 * that directly extends Block must have at least one constructor that directly invokes a Block
 * constructor, and every such invocation must derive its first Material argument from the same
 * static field. Vanilla {@code Material.field_*} references are admitted directly. A source-owned
 * Material singleton is admitted only when its field type is the owning direct Material subclass and
 * every source write is the one canonical {@code <clinit>: new/dup/<init>/putstatic} assignment.
 * Constructor parameters, computed values, mutable source Material fields, mixed fields, malformed
 * bytecode and specialized external Block subclasses remain unresolved.</p>
 */
public final class LegacyBlockMaterialProvenanceAnalyzer {
    private static final String VANILLA_BLOCK = "net/minecraft/block/Block";
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MATERIAL_DESCRIPTOR = "L" + MATERIAL + ";";

    public record MaterialRef(String owner, String fieldName, String descriptor) { }

    public record Proof(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            String directBlockSourceClass,
            boolean complete,
            MaterialRef material,
            List<String> reasons
    ) {
        public Proof { reasons = List.copyOf(reasons); }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis {
            proofs = List.copyOf(proofs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = loadClasses(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Proof> proofs = new ArrayList<>();

        for (LegacyRegistryAnalyzer.Registration block : registry.blocks()) {
            LinkedHashSet<String> reasons = new LinkedHashSet<>();
            ClassNode direct = directBlockSourceClass(classes, block.implementationClass(), reasons);
            MaterialRef material = null;
            int directSuperCalls = 0;

            if (direct != null) {
                LinkedHashSet<MaterialRef> materials = new LinkedHashSet<>();
                for (MethodNode method : direct.methods) {
                    if (!"<init>".equals(method.name)) continue;
                    ConstructorResult result = inspectConstructor(classes, direct, method);
                    directSuperCalls += result.directSuperCalls();
                    materials.addAll(result.materials());
                    reasons.addAll(result.reasons());
                }

                if (directSuperCalls == 0) {
                    reasons.add("no source constructor directly invoking Block.<init> was proven");
                } else if (materials.size() > 1) {
                    reasons.add("direct Block constructors use multiple Material fields: " + materials);
                } else if (materials.size() == 1 && reasons.isEmpty()) {
                    material = materials.iterator().next();
                } else if (materials.isEmpty() && reasons.isEmpty()) {
                    reasons.add("Block Material argument did not resolve to one stable static Material field");
                }
            }

            proofs.add(new Proof(
                    block.registryName(),
                    block.legacyNamespace(),
                    block.implementationClass(),
                    direct == null ? null : direct.name,
                    direct != null && directSuperCalls > 0 && material != null && reasons.isEmpty(),
                    material,
                    List.copyOf(reasons)
            ));
        }

        return new Analysis(proofs, registry.diagnostics());
    }

    private static ClassNode directBlockSourceClass(
            Map<String, ClassNode> classes,
            String implementationClass,
            Set<String> reasons
    ) {
        if (implementationClass == null || implementationClass.isBlank()) {
            reasons.add("registered block has no proven implementation class");
            return null;
        }
        String current = implementationClass;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) {
                reasons.add("source hierarchy disappeared at " + current);
                return null;
            }
            if (VANILLA_BLOCK.equals(node.superName)) return node;
            if (!classes.containsKey(node.superName)) {
                reasons.add("external superclass " + node.superName
                        + " owns or may alter Block material construction");
                return null;
            }
            current = node.superName;
        }
        reasons.add("source hierarchy did not reach Block");
        return null;
    }

    private static ConstructorResult inspectConstructor(
            Map<String, ClassNode> classes,
            ClassNode owner,
            MethodNode method
    ) {
        List<MaterialRef> materials = new ArrayList<>();
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        Frame<SourceValue>[] frames;
        try {
            frames = new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
        } catch (AnalyzerException | RuntimeException error) {
            reasons.add("could not analyze constructor " + owner.name + method.desc + ": "
                    + error.getClass().getSimpleName());
            return new ConstructorResult(0, materials, List.copyOf(reasons));
        }

        int directSuperCalls = 0;
        for (int index = 0; index < method.instructions.size(); index++) {
            AbstractInsnNode instruction = method.instructions.get(index);
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != Opcodes.INVOKESPECIAL
                    || !VANILLA_BLOCK.equals(call.owner)
                    || !"<init>".equals(call.name)) {
                continue;
            }

            Type[] arguments = Type.getArgumentTypes(call.desc);
            if (arguments.length == 0 || !MATERIAL_DESCRIPTOR.equals(arguments[0].getDescriptor())) {
                continue;
            }
            directSuperCalls++;

            Frame<SourceValue> frame = frames[index];
            if (frame == null || frame.getStackSize() < arguments.length + 1) {
                reasons.add("missing data-flow frame for Block.<init> in " + owner.name + method.desc);
                continue;
            }
            int materialStackIndex = frame.getStackSize() - arguments.length;
            SourceValue value = frame.getStack(materialStackIndex);
            MaterialRef material = staticMaterial(classes, value);
            if (material == null) {
                reasons.add("Block Material argument in " + owner.name + method.desc
                        + " is not one stable direct static Material field");
                continue;
            }
            materials.add(material);
        }
        return new ConstructorResult(directSuperCalls, materials, List.copyOf(reasons));
    }

    private static MaterialRef staticMaterial(Map<String, ClassNode> classes, SourceValue value) {
        if (value == null || value.insns == null || value.insns.size() != 1) return null;
        AbstractInsnNode source = value.insns.iterator().next();
        if (!(source instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC) return null;

        if (MATERIAL.equals(field.owner) && MATERIAL_DESCRIPTOR.equals(field.desc)) {
            return new MaterialRef(field.owner, field.name, field.desc);
        }
        if (!stableSourceMaterialSingleton(classes, field)) return null;
        return new MaterialRef(field.owner, field.name, field.desc);
    }

    private static boolean stableSourceMaterialSingleton(Map<String, ClassNode> classes, FieldInsnNode reference) {
        Type fieldType;
        try {
            fieldType = Type.getType(reference.desc);
        } catch (RuntimeException ignored) {
            return false;
        }
        if (fieldType.getSort() != Type.OBJECT) return false;
        String materialClass = fieldType.getInternalName();
        if (!reference.owner.equals(materialClass)) return false;

        ClassNode owner = classes.get(materialClass);
        if (owner == null || !MATERIAL.equals(owner.superName)) return false;
        boolean declaredStatic = false;
        for (FieldNode field : owner.fields) {
            if (field.name.equals(reference.name) && field.desc.equals(reference.desc)
                    && (field.access & Opcodes.ACC_STATIC) != 0) {
                declaredStatic = true;
                break;
            }
        }
        if (!declaredStatic) return false;

        int writes = 0;
        for (ClassNode candidate : classes.values()) {
            for (MethodNode method : candidate.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof FieldInsnNode put)
                            || put.getOpcode() != Opcodes.PUTSTATIC
                            || !put.owner.equals(reference.owner)
                            || !put.name.equals(reference.name)
                            || !put.desc.equals(reference.desc)) {
                        continue;
                    }
                    writes++;
                    if (!candidate.name.equals(reference.owner)
                            || !"<clinit>".equals(method.name)
                            || !canonicalNewSingleton(put, materialClass)) {
                        return false;
                    }
                }
            }
        }
        return writes == 1;
    }

    private static boolean canonicalNewSingleton(FieldInsnNode put, String materialClass) {
        AbstractInsnNode initInsn = previousReal(put);
        if (!(initInsn instanceof MethodInsnNode init)
                || init.getOpcode() != Opcodes.INVOKESPECIAL
                || !materialClass.equals(init.owner)
                || !"<init>".equals(init.name)
                || !"()V".equals(init.desc)) {
            return false;
        }
        AbstractInsnNode dup = previousReal(initInsn);
        if (dup == null || dup.getOpcode() != Opcodes.DUP) return false;
        AbstractInsnNode newInsn = previousReal(dup);
        return newInsn instanceof TypeInsnNode created
                && created.getOpcode() == Opcodes.NEW
                && materialClass.equals(created.desc);
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode node) {
        for (AbstractInsnNode current = node == null ? null : node.getPrevious(); current != null;
             current = current.getPrevious()) {
            if (current.getOpcode() >= 0) return current;
        }
        return null;
    }

    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) {
                    // Missing source bytecode remains fail-closed in the hierarchy/data-flow proof.
                }
            }
        }
        return classes;
    }

    private record ConstructorResult(int directSuperCalls, List<MaterialRef> materials, List<String> reasons) {
        private ConstructorResult {
            materials = List.copyOf(materials);
            reasons = List.copyOf(reasons);
        }
    }
}
