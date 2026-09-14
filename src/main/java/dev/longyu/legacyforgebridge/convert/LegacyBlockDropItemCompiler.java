package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves the narrow source-independent subset of Minecraft 1.7.x
 * {@code Block#getItemDropped(int, Random, int)}.
 *
 * <p>Only four exact return shapes are admitted:</p>
 * <ul>
 *     <li>a static Item field already bound to a proven legacy registry registration;</li>
 *     <li>{@code Item.getItemFromBlock(staticBlockField)} for a proven block registration;</li>
 *     <li>{@code null};</li>
 *     <li>{@code Item.getItemById(0)}, the legacy air/no-item sentinel.</li>
 * </ul>
 *
 * <p>No source class is defined or executed. Conditional returns, instance state, helper calls,
 * world access and unproven static fields fail closed. Quantity and item damage are intentionally
 * separate evidence and must be proven before a later materializer can alter modern drops.</p>
 */
public final class LegacyBlockDropItemCompiler {
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String ITEM_FROM_BLOCK = "(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;";
    private static final String ITEM_FROM_ID = "(I)Lnet/minecraft/item/Item;";

    public enum TargetKind { ITEM, BLOCK_ITEM, NONE }

    public record Target(
            TargetKind kind,
            String registryName,
            String legacyNamespace,
            String sourceFieldOwner,
            String sourceFieldName,
            String sourceFieldDescriptor
    ) {
        public Target {
            if (kind == TargetKind.NONE) {
                registryName = null;
                legacyNamespace = null;
                sourceFieldOwner = null;
                sourceFieldName = null;
                sourceFieldDescriptor = null;
            } else if (registryName == null || registryName.isBlank()) {
                throw new IllegalArgumentException("Registry-backed drop target requires a registry name");
            }
        }

        public static Target none() {
            return new Target(TargetKind.NONE, null, null, null, null, null);
        }
    }

    public record Rule(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            String sourceOwner,
            String sourceMethod,
            String sourceDescriptor,
            Target target
    ) { }

    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis compile(Path jarPath) throws IOException {
        LegacyBlockBehaviorAnalyzer.Analysis behavior = new LegacyBlockBehaviorAnalyzer().analyze(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        Map<String, LegacyRegistryAnalyzer.FieldBinding> fields = new LinkedHashMap<>();
        for (LegacyRegistryAnalyzer.FieldBinding field : registry.fieldBindings()) {
            fields.put(fieldKey(field.owner(), field.name(), field.descriptor()), field);
        }
        Map<String, ClassNode> classes = loadClasses(jarPath);
        List<Rule> rules = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>(behavior.diagnostics());
        diagnostics.addAll(registry.diagnostics());

        for (LegacyBlockBehaviorAnalyzer.BlockBehavior block : behavior.blocks()) {
            LegacyBlockBehaviorAnalyzer.Callback callback = block.callbacks().stream()
                    .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.ITEM_DROPPED)
                    .findFirst()
                    .orElse(null);
            if (callback == null) continue;

            MethodNode method = findMethod(classes.get(callback.owner()), callback);
            if (method == null) {
                diagnostics.add("Drop item callback disappeared from source class " + callback.owner() + ".");
                continue;
            }
            CompileResult result = compileMethod(method, fields);
            if (result.target() == null) {
                diagnostics.add("Unsupported direct drop item callback " + callback.owner() + "."
                        + callback.method() + callback.descriptor() + ": " + result.error());
                continue;
            }
            rules.add(new Rule(block.registryName(), block.legacyNamespace(), block.implementationClass(),
                    callback.owner(), callback.method(), callback.descriptor(), result.target()));
        }

        return new Analysis(rules, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static CompileResult compileMethod(MethodNode method,
                                               Map<String, LegacyRegistryAnalyzer.FieldBinding> fields) {
        List<AbstractInsnNode> instructions = realInstructions(method);
        if (instructions.size() == 2
                && instructions.get(0) instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && instructions.get(1).getOpcode() == Opcodes.ARETURN) {
            LegacyRegistryAnalyzer.FieldBinding binding = fields.get(fieldKey(field.owner, field.name, field.desc));
            if (binding == null) return CompileResult.error("static return field has no proven registry binding");
            if (binding.kind() != LegacyRegistryAnalyzer.Kind.ITEM) {
                return CompileResult.error("static return field is not a proven item registration");
            }
            return CompileResult.target(target(TargetKind.ITEM, binding, field));
        }

        if (instructions.size() == 3
                && instructions.get(0) instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && instructions.get(1) instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(ITEM)
                && call.desc.equals(ITEM_FROM_BLOCK)
                && instructions.get(2).getOpcode() == Opcodes.ARETURN) {
            LegacyRegistryAnalyzer.FieldBinding binding = fields.get(fieldKey(field.owner, field.name, field.desc));
            if (binding == null) return CompileResult.error("block-to-item field has no proven registry binding");
            if (binding.kind() != LegacyRegistryAnalyzer.Kind.BLOCK) {
                return CompileResult.error("Item.getItemFromBlock input is not a proven block registration");
            }
            return CompileResult.target(target(TargetKind.BLOCK_ITEM, binding, field));
        }

        if (instructions.size() == 2
                && instructions.get(0) instanceof InsnNode first
                && first.getOpcode() == Opcodes.ACONST_NULL
                && instructions.get(1).getOpcode() == Opcodes.ARETURN) {
            return CompileResult.target(Target.none());
        }

        if (instructions.size() == 3
                && instructions.get(0) instanceof InsnNode first
                && first.getOpcode() == Opcodes.ICONST_0
                && instructions.get(1) instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(ITEM)
                && call.desc.equals(ITEM_FROM_ID)
                && instructions.get(2).getOpcode() == Opcodes.ARETURN) {
            return CompileResult.target(Target.none());
        }

        return CompileResult.error("callback is not an admitted direct item/block-item/no-drop return shape");
    }

    private static Target target(TargetKind kind, LegacyRegistryAnalyzer.FieldBinding binding, FieldInsnNode field) {
        return new Target(kind, binding.registryName(), binding.legacyNamespace(), field.owner, field.name, field.desc);
    }

    private static List<AbstractInsnNode> realInstructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) result.add(instruction);
        }
        return result;
    }

    private static MethodNode findMethod(ClassNode owner, LegacyBlockBehaviorAnalyzer.Callback callback) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods) {
            if (method.name.equals(callback.method()) && method.desc.equals(callback.descriptor())) return method;
        }
        return null;
    }

    private static String fieldKey(String owner, String name, String descriptor) {
        return owner + "\u0000" + name + "\u0000" + descriptor;
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
                    // The shared analyzers own malformed-class diagnostics. Missing bytecode here
                    // simply prevents a direct drop rule from being trusted.
                }
            }
        }
        return classes;
    }

    private record CompileResult(Target target, String error) {
        static CompileResult target(Target target) { return new CompileResult(target, null); }
        static CompileResult error(String error) { return new CompileResult(null, error); }
    }
}
