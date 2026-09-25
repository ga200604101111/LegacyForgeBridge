package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
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
 * Extracts the source-independent constant subset of Minecraft 1.7.x
 * {@code Block#quantityDropped(Random)}.
 *
 * <p>This stage intentionally admits only a literal non-negative integer followed directly by
 * {@code IRETURN}. Random access, helper calls, fields, branches and arithmetic are rejected rather
 * than approximated. The result is conversion evidence only; a modern drop override still requires
 * independently proven item identity and item damage.</p>
 */
public final class LegacyBlockDropQuantityCompiler {
    public record Rule(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            String sourceOwner,
            String sourceMethod,
            String sourceDescriptor,
            int quantity
    ) { }

    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis compile(Path jarPath) throws IOException {
        LegacyBlockBehaviorAnalyzer.Analysis behavior = new LegacyBlockBehaviorAnalyzer().analyze(jarPath);
        Map<String, ClassNode> classes = loadClasses(jarPath);
        List<Rule> rules = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>(behavior.diagnostics());

        for (LegacyBlockBehaviorAnalyzer.BlockBehavior block : behavior.blocks()) {
            LegacyBlockBehaviorAnalyzer.Callback callback = block.callbacks().stream()
                    .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.QUANTITY_DROPPED)
                    .findFirst()
                    .orElse(null);
            if (callback == null) continue;

            MethodNode method = findMethod(classes.get(callback.owner()), callback);
            if (method == null) {
                diagnostics.add("Drop quantity callback disappeared from source class " + callback.owner() + ".");
                continue;
            }

            Integer quantity = constantReturn(method);
            if (quantity == null) {
                diagnostics.add("Unsupported constant drop quantity callback " + callback.owner() + "."
                        + callback.method() + callback.descriptor()
                        + ": expected a non-negative literal followed directly by IRETURN");
                continue;
            }
            rules.add(new Rule(block.registryName(), block.legacyNamespace(), block.implementationClass(),
                    callback.owner(), callback.method(), callback.descriptor(), quantity));
        }

        return new Analysis(rules, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static Integer constantReturn(MethodNode method) {
        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) instructions.add(instruction);
        }
        if (instructions.size() != 2 || instructions.get(1).getOpcode() != Opcodes.IRETURN) return null;

        Integer value = switch (instructions.get(0)) {
            case InsnNode instruction -> switch (instruction.getOpcode()) {
                case Opcodes.ICONST_0 -> 0;
                case Opcodes.ICONST_1 -> 1;
                case Opcodes.ICONST_2 -> 2;
                case Opcodes.ICONST_3 -> 3;
                case Opcodes.ICONST_4 -> 4;
                case Opcodes.ICONST_5 -> 5;
                default -> null;
            };
            case IntInsnNode instruction when instruction.getOpcode() == Opcodes.BIPUSH
                    || instruction.getOpcode() == Opcodes.SIPUSH -> instruction.operand;
            case LdcInsnNode instruction when instruction.cst instanceof Integer integer -> integer;
            default -> null;
        };
        return value != null && value >= 0 ? value : null;
    }

    private static MethodNode findMethod(ClassNode owner, LegacyBlockBehaviorAnalyzer.Callback callback) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods) {
            if (method.name.equals(callback.method()) && method.desc.equals(callback.descriptor())) return method;
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
                    // Shared analyzers own malformed-source diagnostics. An unreadable class simply
                    // cannot contribute a trusted constant quantity rule.
                }
            }
        }
        return classes;
    }
}
