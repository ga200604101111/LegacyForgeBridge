package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
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
 * Compiles source-owned Minecraft 1.7.x {@code Block#damageDropped(int)} callbacks without
 * defining or executing legacy classes.
 *
 * <p>The source method is admitted only through {@link LegacyPureIntFunctionCompiler}. The
 * resulting pure program is immediately evaluated over the complete legacy block metadata domain
 * (0..15) and collapsed to a fixed table. This deliberately keeps arbitrary source bytecode out of
 * the modern runtime while retaining the exact item-damage result for every possible block state.
 * Calls through {@code super}, source fields, helper methods, loops, allocations and other object
 * behavior remain unsupported and fail closed.</p>
 *
 * <p>This compiler proves only the dropped stack metadata. It does not by itself authorize a
 * modern drop override: item identity and quantity must be proven independently before a later
 * materializer may change gameplay drops.</p>
 */
public final class LegacyBlockDropMetadataCompiler {
    private static final int MIN_BLOCK_META = 0;
    private static final int MAX_BLOCK_META = 15;
    private static final int MAX_LEGACY_ITEM_DAMAGE = Short.MAX_VALUE;

    public record Rule(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            String sourceOwner,
            String sourceMethod,
            String sourceDescriptor,
            List<Integer> itemDamageByBlockMeta
    ) {
        public Rule {
            itemDamageByBlockMeta = List.copyOf(itemDamageByBlockMeta);
            if (itemDamageByBlockMeta.size() != 16) {
                throw new IllegalArgumentException("Expected one drop-damage value for each legacy block metadata state");
            }
        }

        public int itemDamage(int blockMeta) {
            if (blockMeta < MIN_BLOCK_META || blockMeta > MAX_BLOCK_META) {
                throw new IllegalArgumentException("Legacy block metadata outside 0..15: " + blockMeta);
            }
            return itemDamageByBlockMeta.get(blockMeta);
        }
    }

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
        LegacyPureIntFunctionCompiler compiler = new LegacyPureIntFunctionCompiler();

        for (LegacyBlockBehaviorAnalyzer.BlockBehavior block : behavior.blocks()) {
            LegacyBlockBehaviorAnalyzer.Callback callback = block.callbacks().stream()
                    .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.DAMAGE_DROPPED)
                    .findFirst()
                    .orElse(null);
            if (callback == null) continue;

            MethodNode method = findMethod(classes.get(callback.owner()), callback);
            if (method == null) {
                diagnostics.add("Drop metadata callback disappeared from source class " + callback.owner() + ".");
                continue;
            }

            LegacyPureIntFunctionCompiler.Result compiled = compiler.compile(method, 1);
            if (!compiled.supported()) {
                diagnostics.add("Unsupported pure drop metadata callback " + callback.owner() + "."
                        + callback.method() + callback.descriptor() + ": " + compiled.error());
                continue;
            }

            List<Integer> table = new ArrayList<>(16);
            String failure = null;
            for (int meta = MIN_BLOCK_META; meta <= MAX_BLOCK_META; meta++) {
                final int value;
                try {
                    value = compiled.program().evaluate(meta);
                } catch (RuntimeException exception) {
                    failure = "evaluation failed for metadata " + meta + ": " + exception.getMessage();
                    break;
                }
                if (value < 0 || value > MAX_LEGACY_ITEM_DAMAGE) {
                    failure = "returned item damage outside 0.." + MAX_LEGACY_ITEM_DAMAGE
                            + " for metadata " + meta + ": " + value;
                    break;
                }
                table.add(value);
            }
            if (failure != null) {
                diagnostics.add("Unsupported pure drop metadata callback " + callback.owner() + "."
                        + callback.method() + callback.descriptor() + ": " + failure);
                continue;
            }

            rules.add(new Rule(block.registryName(), block.legacyNamespace(), block.implementationClass(),
                    callback.owner(), callback.method(), callback.descriptor(), table));
        }

        return new Analysis(rules, List.copyOf(new LinkedHashSet<>(diagnostics)));
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
                    // Registry/block callback analysis owns malformed-source diagnostics. A class
                    // that cannot be read here simply cannot yield a trusted drop metadata rule.
                }
            }
        }
        return classes;
    }
}
