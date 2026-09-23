package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

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
 * Source-only proof of the Forge 1.7.10 BlockItem inventory 2D/3D decision.
 *
 * <p>Vanilla render types use the exact RenderBlocks.renderItemIn3d table. Custom render IDs are
 * admitted only when the source field is initialized by a helper that registers one
 * ISimpleBlockRenderingHandler and that handler (possibly through a source superclass) returns a
 * literal boolean from shouldRender3DInInventory. Unknown routes remain unclassified.</p>
 */
public final class LegacyBlockInventoryRenderModeAnalyzer {
    private static final String HANDLER = "cpw/mods/fml/client/registry/ISimpleBlockRenderingHandler";
    private static final String RENDERING_REGISTRY = "cpw/mods/fml/client/registry/RenderingRegistry";
    private static final String SHOULD_3D = "shouldRender3DInInventory";
    private static final String SHOULD_3D_DESC = "(I)Z";

    public enum Mode { FLAT_2D, THREE_D }

    public record Rule(String registryName, String sourceBlockClass, Mode mode, String proof) { }
    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            diagnostics = List.copyOf(diagnostics);
        }
    }
    private record MethodRef(String owner, String name, String desc) { }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String,ClassNode> classes = loadClasses(jarPath);
        var renderTypes = new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        LinkedHashSet<String> diagnostics = new LinkedHashSet<>();

        for (var rule : renderTypes.rules()) {
            var identity = rule.renderIdentity();
            Boolean threeD = null;
            String proof = null;
            if (identity.constant() != null) {
                var vanilla = LegacyBlockRenderType1710.renderItemIn3d(identity.constant());
                if (vanilla.isPresent()) {
                    threeD = vanilla.get();
                    proof = "Minecraft 1.7.10 RenderBlocks.renderItemIn3d(" + identity.constant() + ")";
                }
            } else {
                threeD = resolveCustomInventoryMode(classes, identity.fieldOwner(), identity.fieldName());
                if (threeD != null) {
                    proof = "Source Forge ISimpleBlockRenderingHandler.shouldRender3DInInventory for "
                            + identity.fieldOwner() + "." + identity.fieldName();
                }
            }
            if (threeD != null) {
                rules.add(new Rule(rule.registryName(), rule.sourceBlockClass(),
                        threeD ? Mode.THREE_D : Mode.FLAT_2D, proof));
            } else if (identity.constant() == null) {
                diagnostics.add("Custom block inventory render route is not source-proven: "
                        + rule.sourceBlockClass() + " -> " + identity.fieldOwner() + "." + identity.fieldName());
            }
        }
        return new Analysis(rules, List.copyOf(diagnostics));
    }

    static Boolean resolveCustomInventoryMode(Map<String,ClassNode> classes, String fieldOwner, String fieldName) {
        ClassNode owner = classes.get(fieldOwner);
        MethodNode clinit = ownMethod(owner, "<clinit>", "()V");
        if (clinit == null) return null;

        LinkedHashSet<MethodRef> helpers = new LinkedHashSet<>();
        List<AbstractInsnNode> code = real(clinit);
        for (int i = 1; i < code.size(); i++) {
            AbstractInsnNode insn = code.get(i);
            if (!(insn instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTSTATIC
                    || !field.owner.equals(fieldOwner) || !field.name.equals(fieldName) || !field.desc.equals("I")) continue;
            AbstractInsnNode previous = code.get(i - 1);
            if (previous instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                    && Type.getReturnType(call.desc).getSort() == Type.INT) {
                helpers.add(new MethodRef(call.owner, call.name, call.desc));
            }
        }
        if (helpers.size() != 1) return null;
        return registeredHandlerMode(classes, helpers.getFirst(), 0, new HashSet<>());
    }

    private static Boolean registeredHandlerMode(Map<String,ClassNode> classes, MethodRef ref,
                                                 int depth, Set<MethodRef> visiting) {
        if (depth > 4 || !visiting.add(ref)) return null;
        MethodNode method = ownMethod(classes.get(ref.owner()), ref.name(), ref.desc());
        if (method == null) return null;
        List<AbstractInsnNode> code = real(method);
        LinkedHashSet<Boolean> modes = new LinkedHashSet<>();
        boolean sawRegistration = false;

        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode insn = code.get(i);
            if (insn instanceof MethodInsnNode call
                    && call.owner.equals(RENDERING_REGISTRY)
                    && call.name.equals("registerBlockHandler")
                    && call.desc.contains("L" + HANDLER + ";")) {
                sawRegistration = true;
                for (String type : handlerCandidates(classes, code, i)) {
                    Boolean mode = literalShouldRender3d(classes, type);
                    if (mode != null) modes.add(mode);
                }
            }
        }
        if (sawRegistration) return modes.size() == 1 ? modes.getFirst() : null;

        for (AbstractInsnNode insn : code) {
            if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
                    || !classes.containsKey(call.owner)) continue;
            Boolean mode = registeredHandlerMode(classes,
                    new MethodRef(call.owner, call.name, call.desc), depth + 1, visiting);
            if (mode != null) modes.add(mode);
        }
        return modes.size() == 1 ? modes.getFirst() : null;
    }

    private static Set<String> handlerCandidates(Map<String,ClassNode> classes,
                                                 List<AbstractInsnNode> code, int callIndex) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (int i = Math.max(0, callIndex - 12); i < callIndex; i++) {
            AbstractInsnNode insn = code.get(i);
            String type = null;
            if (insn instanceof TypeInsnNode typed && typed.getOpcode() == Opcodes.NEW) {
                type = typed.desc;
            } else if (insn instanceof FieldInsnNode field
                    && (field.getOpcode() == Opcodes.GETFIELD || field.getOpcode() == Opcodes.GETSTATIC)
                    && field.desc.startsWith("L") && field.desc.endsWith(";")) {
                type = Type.getType(field.desc).getInternalName();
            }
            if (type != null && implementsHandler(classes, type, new HashSet<>())) candidates.add(type);
        }
        return candidates;
    }

    private static boolean implementsHandler(Map<String,ClassNode> classes, String type, Set<String> visiting) {
        if (type == null || !visiting.add(type)) return false;
        if (type.equals(HANDLER)) return true;
        ClassNode node = classes.get(type);
        if (node == null) return false;
        for (String iface : node.interfaces) if (implementsHandler(classes, iface, visiting)) return true;
        return implementsHandler(classes, node.superName, visiting);
    }

    private static Boolean literalShouldRender3d(Map<String,ClassNode> classes, String type) {
        Set<String> seen = new HashSet<>();
        for (String current = type; current != null && seen.add(current); ) {
            ClassNode node = classes.get(current);
            if (node == null) return null;
            MethodNode method = ownMethod(node, SHOULD_3D, SHOULD_3D_DESC);
            if (method != null) {
                List<AbstractInsnNode> code = real(method);
                if (code.size() != 2 || code.get(1).getOpcode() != Opcodes.IRETURN) return null;
                int op = code.get(0).getOpcode();
                if (op == Opcodes.ICONST_0) return false;
                if (op == Opcodes.ICONST_1) return true;
                return null;
            }
            current = node.superName;
        }
        return null;
    }

    private static MethodNode ownMethod(ClassNode owner, String name, String desc) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods)
            if (name.equals(method.name) && desc.equals(method.desc)) return method;
        return null;
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        if (method != null) for (AbstractInsnNode insn : method.instructions)
            if (insn.getOpcode() >= 0) result.add(insn);
        return result;
    }

    private static Map<String,ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String,ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) { }
            }
        }
        return classes;
    }
}
