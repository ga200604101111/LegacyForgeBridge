package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source proof for the legacy 3x3 GridPot world presentation. This slice deliberately proves only
 * that enabled cells and their stored Item/meta values are consumed by the custom renderer with the
 * admitted 3x3 transforms and render-type branches. Modern rendering remains a separate pass.
 */
public final class LegacyGridPotPresentationAnalyzer {
    private static final String RENDER_BLOCKS = "net/minecraft/client/renderer/RenderBlocks";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String TESSELLATOR = "net/minecraft/client/renderer/Tessellator";

    public record Proof(String registryName, String sourceBlockClass, String sourceTileClass,
                        String sourceRendererClass, List<Float> gridOffsets,
                        float contentTranslateY, float crossedScale, float cactusHalfWidth) {
        public Proof {
            gridOffsets = List.copyOf(gridOffsets);
            if (sourceRendererClass == null || sourceRendererClass.isBlank() || gridOffsets.size() != 3)
                throw new IllegalArgumentException("Invalid GridPot presentation proof");
        }
    }
    public record Skipped(String registryName, String sourceBlockClass, String reason) { }
    public record Analysis(List<Proof> proofs, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis { proofs = List.copyOf(proofs); skipped = List.copyOf(skipped); diagnostics = List.copyOf(diagnostics); }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String,ClassNode> classes = loadClasses(jarPath);
        LegacyGridPotBlockAnalyzer.Analysis grids = new LegacyGridPotBlockAnalyzer().analyze(jarPath);
        LegacyRegisteredBlockRenderTypeAnalyzer.Analysis renderTypes = new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(jarPath);
        Map<String,LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity> blockRender = new LinkedHashMap<>();
        for (var rule : renderTypes.rules()) blockRender.putIfAbsent(rule.sourceBlockClass(), rule.renderIdentity());

        List<Proof> proofs = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        LinkedHashSet<String> diagnostics = new LinkedHashSet<>();
        diagnostics.addAll(grids.diagnostics());
        diagnostics.addAll(renderTypes.diagnostics());

        for (LegacyGridPotBlockAnalyzer.Rule rule : grids.rules()) {
            LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity identity = blockRender.get(rule.sourceBlockClass());
            if (identity == null || identity.fieldOwner() == null) {
                skipped.add(new Skipped(rule.registryName(), rule.sourceBlockClass(),
                        "GridPot custom world render identity is not a proven static field."));
                continue;
            }
            String renderer = findRendererForIdentity(classes, identity.fieldOwner(), identity.fieldName());
            if (renderer == null) {
                skipped.add(new Skipped(rule.registryName(), rule.sourceBlockClass(),
                        "GridPot custom renderer map binding is not uniquely source-proven."));
                continue;
            }
            ClassNode tile = classes.get(rule.sourceTileClass());
            LegacyGridPotProofs.TileShape shape = LegacyGridPotProofs.tileShape(tile);
            if (shape == null) {
                skipped.add(new Skipped(rule.registryName(), rule.sourceBlockClass(),
                        "GridPot tile presentation accessors are not source-proven."));
                continue;
            }
            ClassNode rendererNode = classes.get(renderer);
            if (!rendererPresentationShape(rendererNode, rule.sourceTileClass(), shape.enabledGetter().name,
                    shape.itemGetter().name, shape.itemMetaGetter().name,
                    Set.copyOf(rule.contentInsertionSymbolicRenderFields()))) {
                skipped.add(new Skipped(rule.registryName(), rule.sourceBlockClass(),
                        "GridPot renderer does not match the bounded stored-content presentation shape."));
                continue;
            }
            proofs.add(new Proof(rule.registryName(), rule.sourceBlockClass(), rule.sourceTileClass(), renderer,
                    List.of(-0.333F, 0.0F, 0.333F), 0.25F, 0.75F, 0.125F));
        }
        return new Analysis(proofs, skipped, List.copyOf(diagnostics));
    }

    static String findRendererForIdentity(Map<String,ClassNode> classes, String fieldOwner, String fieldName) {
        if (classes == null || fieldOwner == null || fieldName == null) return null;
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (ClassNode owner : classes.values()) for (MethodNode method : owner.methods) {
            List<AbstractInsnNode> code = real(method);
            for (int i = 0; i < code.size(); i++) {
                AbstractInsnNode instruction = code.get(i);
                if (!(instruction instanceof MethodInsnNode call)
                        || !("java/util/HashMap".equals(call.owner) || "java/util/Map".equals(call.owner))
                        || !"put".equals(call.name)
                        || !"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(call.desc)) continue;
                boolean key = false;
                String renderer = null;
                int start = Math.max(0, i - 14);
                for (int j = start; j < i; j++) {
                    AbstractInsnNode candidate = code.get(j);
                    if (candidate instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                            && "I".equals(field.desc) && fieldOwner.equals(field.owner) && fieldName.equals(field.name)) key = true;
                    if (candidate instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && classes.containsKey(type.desc)) {
                        if (hasConstructorBefore(code, j, i, type.desc)) renderer = type.desc;
                    }
                }
                if (key && renderer != null) candidates.add(renderer);
            }
        }
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    static boolean rendererPresentationShape(ClassNode renderer, String tileClass, String enabledGetter,
                                             String itemGetter, String metaGetter, Set<String> symbolicRenderFields) {
        if (renderer == null || tileClass == null) return false;
        MethodNode constructor = ownMethod(renderer, "<init>", "()V");
        if (!gridOffsetConstructor(constructor)) return false;

        boolean enabledTraversal = false;
        boolean contentDraw = false;
        Set<String> symbols = symbolicRenderFields == null ? Set.of() : Set.copyOf(symbolicRenderFields);
        for (MethodNode method : renderer.methods) {
            if (calls(method, tileClass, enabledGetter, "(I)Z") && callsOwnNonConstructor(renderer, method)) enabledTraversal = true;
            if (storedContentDraw(method, tileClass, itemGetter, metaGetter, symbols)) contentDraw = true;
        }
        return enabledTraversal && contentDraw;
    }

    private static boolean gridOffsetConstructor(MethodNode constructor) {
        if (constructor == null || countOpcode(constructor, Opcodes.FASTORE) < 3) return false;
        return countFloat(constructor, -0.333F) >= 1 && countFloat(constructor, 0.0F) >= 1
                && countFloat(constructor, 0.333F) >= 1 && containsInt(constructor, 3);
    }

    private static boolean storedContentDraw(MethodNode method, String tileClass, String itemGetter,
                                             String metaGetter, Set<String> symbolicRenderFields) {
        if (!calls(method, tileClass, itemGetter, "(I)Lnet/minecraft/item/Item;")
                || !calls(method, tileClass, metaGetter, "(I)I")
                || !calls(method, BLOCK, "func_149634_a", "(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;")
                || !calls(method, BLOCK, "func_149645_b", "()I")) return false;
        if (!containsInt(method, 1) || !containsInt(method, 13) || !containsInt(method, 40)) return false;
        if (symbolicRenderFields.isEmpty() || !containsAnyStaticIntField(method, symbolicRenderFields)) return false;
        if (countFloat(method, 0.375F) < 1 || countFloat(method, 4.0F) < 1 || countFloat(method, 16.0F) < 1
                || countFloat(method, 0.75F) < 1 || countFloat(method, 0.125F) < 1) return false;
        if (countOwnerDescriptor(method, RENDER_BLOCKS, "(DDDDDD)V") < 4) return false;
        if (countOwnerDescriptor(method, RENDER_BLOCKS, "(Lnet/minecraft/block/Block;III)Z") < 4) return false;
        if (countOwnerDescriptor(method, RENDER_BLOCKS, "(Lnet/minecraft/util/IIcon;DDDF)V") < 1) return false;
        return countOwnerDescriptor(method, TESSELLATOR, "(FFF)V") >= 2;
    }

    private static boolean containsAnyStaticIntField(MethodNode method, Set<String> keys) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && "I".equals(field.desc)
                    && keys.contains(field.owner + "#" + field.name)) return true;
        }
        return false;
    }

    private static boolean callsOwnNonConstructor(ClassNode owner, MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && owner.name.equals(call.owner) && !"<init>".equals(call.name)) return true;
        }
        return false;
    }

    private static boolean hasConstructorBefore(List<AbstractInsnNode> code, int newIndex, int end, String owner) {
        for (int i = newIndex + 1; i < end; i++) {
            if (code.get(i) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && owner.equals(call.owner) && "<init>".equals(call.name) && "()V".equals(call.desc)) return true;
        }
        return false;
    }

    private static MethodNode ownMethod(ClassNode owner, String name, String descriptor) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods) if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        return null;
    }
    private static boolean calls(MethodNode method, String owner, String name, String desc) {
        if (method == null) return false;
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) return true;
        return false;
    }
    private static int countOwnerDescriptor(MethodNode method, String owner, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && owner.equals(call.owner) && descriptor.equals(call.desc)) count++;
        return count;
    }
    private static int countOpcode(MethodNode method, int opcode) {
        int count = 0;
        if (method != null) for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() == opcode) count++;
        return count;
    }
    private static boolean containsInt(MethodNode method, int target) {
        if (method == null) return false;
        for (AbstractInsnNode instruction : method.instructions) {
            Integer value = intConstant(instruction);
            if (value != null && value == target) return true;
        }
        return false;
    }
    private static int countFloat(MethodNode method, float target) {
        int count = 0;
        if (method != null) for (AbstractInsnNode instruction : method.instructions) {
            Float value = floatConstant(instruction);
            if (value != null && Math.abs(value - target) < 0.0001F) count++;
        }
        return count;
    }
    private static Integer intConstant(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        return switch (instruction.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1; case Opcodes.ICONST_0 -> 0; case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2; case Opcodes.ICONST_3 -> 3; case Opcodes.ICONST_4 -> 4; case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) instruction).operand;
            case Opcodes.LDC -> instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value ? value : null;
            default -> null;
        };
    }
    private static Float floatConstant(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        return switch (instruction.getOpcode()) {
            case Opcodes.FCONST_0 -> 0.0F; case Opcodes.FCONST_1 -> 1.0F; case Opcodes.FCONST_2 -> 2.0F;
            case Opcodes.LDC -> instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Float value ? value : null;
            default -> null;
        };
    }
    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        if (method != null) for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
    private static Map<String,ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String,ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
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
