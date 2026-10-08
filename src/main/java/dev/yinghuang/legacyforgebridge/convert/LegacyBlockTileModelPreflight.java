package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-only join for registered legacy Block -> TileEntity -> TileEntitySpecialRenderer -> ModelBase.
 *
 * <p>Unlike the existing narrow BlockContainer family, this recognizes a source Block that
 * inherits a proven hasTileEntity(int)=true and returns exactly one constructed TileEntity
 * from createTileEntity(World,int). The identities come from Forge lifecycle registration and
 * exact client renderer registration operands, never from mod/class/item names in a selector.</p>
 *
 * <p>Candidate admission is not runtime admission: block bounds, placement, tick state,
 * animation, texture/material, server ownership and legacy network sync must still be proven.
 * No old source class is loaded, linked, instantiated or executed.</p>
 */
public final class LegacyBlockTileModelPreflight {
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String TILE = "net/minecraft/tileentity/TileEntity";
    private static final String TESR = "net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String MODEL = "net/minecraft/client/model/ModelBase";
    private static final String CLIENT_REGISTRY = "cpw/mods/fml/client/registry/ClientRegistry";
    private static final String BIND_DESC = "(Ljava/lang/Class;L" + TESR + ";)V";
    private static final String REGISTER_DESC = "(Ljava/lang/Class;Ljava/lang/String;L" + TESR + ";)V";
    private static final String FACTORY_DESC = "(L" + WORLD + ";I)L" + TILE + ";";
    private static final String DRAW_DESC = "(L" + TILE + ";DDDF)V";
    private static final String BOUNDS_DESC = "(Lnet/minecraft/world/IBlockAccess;III)V";

    public record BlockIdentity(String registryName, String blockClass) { }
    public record TileIdentity(String tileClass, String legacyTileId) { }
    public record RendererIdentity(String tileClass, String rendererClass, String sourceOwner) { }
    public record Candidate(String registryName, String sourceBlockClass, String tileClass,
                            String tileRegistryId, String rendererClass, String modelClass,
                            String hasTileEntityOwner, String createTileEntityOwner,
                            boolean sourceTileTickPresent, boolean rendererCallsTileModelMethod,
                            boolean rendererReadsTileFields, boolean metadataDependentBounds,
                            Integer sourceConstantLight, boolean sourceZeroDrop) { }
    public record Skipped(String registryName, String sourceBlockClass, String reason) { }
    public record Analysis(List<Candidate> candidates, List<Skipped> skipped,
                           List<String> diagnostics) {
        public Analysis {
            candidates = List.copyOf(candidates);
            skipped = List.copyOf(skipped);
            diagnostics = List.copyOf(diagnostics);
        }
    }
    private record OwnerMethod(String owner, MethodNode method) { }
    private record RendererInventory(List<RendererIdentity> registrations, boolean unresolvedCall) { }
    private record RenderEvidence(boolean modelMethodCall, boolean tileFieldReads) { }

    /** Read exact registry/lifecycle classes via existing common analyzers. */
    public Analysis analyze(Path jar) throws IOException {
        var registry = new LegacyRegistryAnalyzer().analyze(jar);
        var lifecycle = new LegacyLifecycleAnalyzer().analyze(jar);
        List<BlockIdentity> blocks = new ArrayList<>();
        for (var item : registry.blocks())
            blocks.add(new BlockIdentity(item.registryName(), item.implementationClass()));
        List<TileIdentity> tiles = new ArrayList<>();
        for (var item : lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY)) {
            if (item.arguments().size() < 2) continue;
            if (item.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue clazz
                    && item.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue name)
                tiles.add(new TileIdentity(clazz.internalName(), name.value()));
        }
        var source = inspect(jar, blocks, tiles);
        List<String> diagnostics = new ArrayList<>();
        diagnostics.addAll(registry.diagnostics());
        diagnostics.addAll(lifecycle.diagnostics());
        diagnostics.addAll(source.diagnostics());
        return new Analysis(source.candidates(), source.skipped(),
                List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    /** Fixture seam: supplied identities are still checked against source bytecode in the JAR. */
    public Analysis inspect(Path jar, List<BlockIdentity> blocks, List<TileIdentity> tiles) throws IOException {
        Objects.requireNonNull(jar); Objects.requireNonNull(blocks); Objects.requireNonNull(tiles);
        Map<String, ClassNode> classes = load(jar);
        RendererInventory renderers = discoverRenderers(classes);
        List<String> diagnostics = new ArrayList<>();
        if (renderers.unresolvedCall())
            diagnostics.add("Unresolved legacy client TESR registration; no candidate is admitted");
        Map<String, List<RendererIdentity>> renderByTile = new LinkedHashMap<>();
        for (var r : renderers.registrations())
            renderByTile.computeIfAbsent(r.tileClass(), ignored -> new ArrayList<>()).add(r);
        Map<String, List<TileIdentity>> registeredTiles = new LinkedHashMap<>();
        Map<String, List<TileIdentity>> registeredTileIds = new LinkedHashMap<>();
        for (var tile : tiles) if (tile != null && tile.tileClass() != null && tile.legacyTileId() != null) {
            registeredTiles.computeIfAbsent(tile.tileClass(), ignored -> new ArrayList<>()).add(tile);
            registeredTileIds.computeIfAbsent(tile.legacyTileId(), ignored -> new ArrayList<>()).add(tile);
        }
        Map<String, Integer> blockNames = new HashMap<>();
        Map<String, Integer> blockClasses = new HashMap<>();
        for (var block : blocks) if (block != null && block.registryName() != null && block.blockClass() != null) {
            blockNames.merge(block.registryName(), 1, Integer::sum);
            blockClasses.merge(block.blockClass(), 1, Integer::sum);
        }
        List<Candidate> candidates = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (var block : blocks) {
            if (block == null || block.registryName() == null || block.registryName().isBlank()
                    || block.blockClass() == null || block.blockClass().isBlank()) continue;
            String sourceClass = block.blockClass();
            if (!inherits(classes, sourceClass, BLOCK)) continue;
            OwnerMethod marker = method(classes, sourceClass, Set.of("hasTileEntity"), "(I)Z");
            if (marker == null) continue; // This structural family requires an explicit source override.
            if (!returnedTrue(marker.method())) {
                skipped.add(skip(block, "hasTileEntity(int) is not a proven constant true")); continue;
            }
            if (blockNames.getOrDefault(block.registryName(), 0) != 1
                    || blockClasses.getOrDefault(sourceClass, 0) != 1) {
                skipped.add(skip(block, "Block registration identity is not unique")); continue;
            }
            OwnerMethod factory = method(classes, sourceClass, Set.of("createTileEntity"), FACTORY_DESC);
            String tileClass = factory == null ? null : exactNewTileReturn(classes, factory.method());
            if (tileClass == null || !inherits(classes, tileClass, TILE)) {
                skipped.add(skip(block, "createTileEntity does not return one proven source-owned TileEntity")); continue;
            }
            List<TileIdentity> tileMatches = registeredTiles.getOrDefault(tileClass, List.of());
            if (tileMatches.size() != 1 || tileMatches.getFirst().legacyTileId().isBlank()
                    || registeredTileIds.getOrDefault(tileMatches.getFirst().legacyTileId(), List.of()).size() != 1) {
                skipped.add(skip(block, "TileEntity lifecycle registration identity missing or ambiguous")); continue;
            }
            List<RendererIdentity> renderMatches = renderByTile.getOrDefault(tileClass, List.of());
            if (renderers.unresolvedCall() || renderMatches.size() != 1) {
                skipped.add(skip(block, "TileEntity client renderer binding missing, ambiguous or unresolved")); continue;
            }
            String renderer = renderMatches.getFirst().rendererClass();
            if (!inherits(classes, renderer, TESR)) {
                skipped.add(skip(block, "Source renderer is not a proven TileEntitySpecialRenderer")); continue;
            }
            String model = modelFromRenderer(classes, renderer);
            if (model == null) {
                skipped.add(skip(block, "One source-owned ModelBase field allocation is not proven")); continue;
            }
            RenderEvidence presentation = renderEvidence(classes, renderer, model, tileClass);
            if (presentation == null) {
                skipped.add(skip(block, "Renderer entrypoint draw closure is not source-proven")); continue;
            }
            OwnerMethod tick = method(classes, tileClass, Set.of("updateEntity", "func_145845_h"), "()V");
            OwnerMethod bounds = method(classes, sourceClass,
                    Set.of("setBlockBoundsBasedOnState", "func_149719_a"), BOUNDS_DESC);
            OwnerMethod light = method(classes, sourceClass,
                    Set.of("getLightValue"), "(Lnet/minecraft/world/IBlockAccess;III)I");
            OwnerMethod drop = method(classes, sourceClass,
                    Set.of("quantityDropped"), "(IILjava/util/Random;)I");
            boolean boundMeta = bounds != null && hasCall(bounds.method(), "getBlockMetadata")
                    && hasCall(bounds.method(), "setBlockBounds");
            Integer lightValue = light == null ? null : returnedInt(light.method());
            if (lightValue != null && (lightValue < 0 || lightValue > 15)) lightValue = null;
            boolean zeroDrop = drop != null && Integer.valueOf(0).equals(returnedInt(drop.method()));
            candidates.add(new Candidate(block.registryName(), sourceClass, tileClass,
                    tileMatches.getFirst().legacyTileId(), renderer, model,
                    marker.owner(), factory.owner(), tick != null,
                    presentation.modelMethodCall(), presentation.tileFieldReads(),
                    boundMeta, lightValue, zeroDrop));
        }
        candidates.sort(Comparator.comparing(Candidate::registryName));
        skipped.sort(Comparator.comparing(Skipped::registryName));
        return new Analysis(candidates, skipped, diagnostics);
    }

    private static Skipped skip(BlockIdentity b, String reason) {
        return new Skipped(b.registryName(), b.blockClass(), reason);
    }
    private static boolean returnedTrue(MethodNode method) {
        List<AbstractInsnNode> code = real(method);
        return method.tryCatchBlocks.isEmpty() && code.size() == 2
                && code.get(0).getOpcode() == Opcodes.ICONST_1
                && code.get(1).getOpcode() == Opcodes.IRETURN;
    }
    private static Integer returnedInt(MethodNode method) {
        if (!method.tryCatchBlocks.isEmpty()) return null;
        List<AbstractInsnNode> code = real(method);
        if (code.size() != 2 || code.get(1).getOpcode() != Opcodes.IRETURN) return null;
        AbstractInsnNode i = code.getFirst();
        if (i instanceof IntInsnNode n && (i.getOpcode() == Opcodes.BIPUSH || i.getOpcode() == Opcodes.SIPUSH)) return n.operand;
        if (i instanceof LdcInsnNode ldc && ldc.cst instanceof Integer n) return n;
        return switch (i.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1; case Opcodes.ICONST_0 -> 0;
            case Opcodes.ICONST_1 -> 1; case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3; case Opcodes.ICONST_4 -> 4;
            case Opcodes.ICONST_5 -> 5; default -> null;
        };
    }
    private static String exactNewTileReturn(Map<String, ClassNode> classes, MethodNode method) {
        if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || !method.tryCatchBlocks.isEmpty()) return null;
        List<AbstractInsnNode> code = real(method);
        if (code.size() != 4 || !(code.get(0) instanceof TypeInsnNode created)
                || created.getOpcode() != Opcodes.NEW || code.get(1).getOpcode() != Opcodes.DUP
                || !(code.get(2) instanceof MethodInsnNode init)
                || init.getOpcode() != Opcodes.INVOKESPECIAL || !init.owner.equals(created.desc)
                || !init.name.equals("<init>") || !init.desc.equals("()V")
                || code.get(3).getOpcode() != Opcodes.ARETURN) return null;
        ClassNode result = classes.get(created.desc);
        if (result == null || result.methods.stream().noneMatch(m -> m.name.equals("<init>")
                && m.desc.equals("()V"))) return null;
        return created.desc;
    }
    private static RendererInventory discoverRenderers(Map<String, ClassNode> classes) {
        List<RendererIdentity> bindings = new ArrayList<>();
        boolean unresolved = false;
        for (ClassNode owner : classes.values()) for (MethodNode method : owner.methods) {
            List<AbstractInsnNode> code = real(method);
            for (int i = 0; i < code.size(); i++) {
                if (!(code.get(i) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !CLIENT_REGISTRY.equals(call.owner)) continue;
                boolean two = call.name.equals("bindTileEntitySpecialRenderer") && BIND_DESC.equals(call.desc);
                boolean three = call.name.equals("registerTileEntity") && REGISTER_DESC.equals(call.desc);
                if (!two && !three) continue;
                int before = three ? 5 : 4;
                if (i < before || !(code.get(i-before) instanceof LdcInsnNode ldc)
                        || !(ldc.cst instanceof Type tile) || tile.getSort() != Type.OBJECT
                        || (three && !(code.get(i-4) instanceof LdcInsnNode name && name.cst instanceof String))
                        || !(code.get(i-3) instanceof TypeInsnNode newRender) || newRender.getOpcode() != Opcodes.NEW
                        || code.get(i-2).getOpcode() != Opcodes.DUP
                        || !(code.get(i-1) instanceof MethodInsnNode init)
                        || init.getOpcode() != Opcodes.INVOKESPECIAL || !init.owner.equals(newRender.desc)
                        || !init.name.equals("<init>") || !init.desc.equals("()V")) {
                    unresolved = true; continue;
                }
                bindings.add(new RendererIdentity(tile.getInternalName(), newRender.desc, owner.name));
            }
        }
        return new RendererInventory(bindings, unresolved);
    }
    private static String modelFromRenderer(Map<String, ClassNode> classes, String renderer) {
        ClassNode node = classes.get(renderer);
        if (node == null) return null;
        MethodNode ctor = null;
        for (MethodNode method : node.methods) if (method.name.equals("<init>")
                && method.desc.equals("()V")) {
            if (ctor != null) return null;
            ctor = method;
        }
        if (ctor == null || !ctor.tryCatchBlocks.isEmpty()) return null;
        List<AbstractInsnNode> code = real(ctor);
        String model = null;
        String fieldName = null;
        for (int i = 0; i + 4 < code.size(); i++) {
            if (!(code.get(i) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD
                    || self.var != 0 || !(code.get(i+1) instanceof TypeInsnNode alloc)
                    || alloc.getOpcode() != Opcodes.NEW || !inherits(classes, alloc.desc, MODEL)
                    || code.get(i+2).getOpcode() != Opcodes.DUP
                    || !(code.get(i+3) instanceof MethodInsnNode init)
                    || init.getOpcode() != Opcodes.INVOKESPECIAL
                    || !init.owner.equals(alloc.desc) || !init.name.equals("<init>")
                    || !init.desc.equals("()V")
                    || !(code.get(i+4) instanceof FieldInsnNode write)
                    || write.getOpcode() != Opcodes.PUTFIELD || !write.owner.equals(renderer)
                    || !write.desc.equals("L" + alloc.desc + ";")) continue;
            if (model != null) return null;
            model = alloc.desc; fieldName = write.name;
        }
        if (model == null || fieldName == null) return null;
        String finalModel = model, finalField = fieldName;
        long declarations = node.fields.stream().filter(f -> f.name.equals(finalField)
                && f.desc.equals("L" + finalModel + ";") && (f.access & Opcodes.ACC_STATIC) == 0).count();
        return declarations == 1 ? model : null;
    }
    private static RenderEvidence renderEvidence(Map<String, ClassNode> classes, String renderer,
                                                  String model, String tile) {
        OwnerMethod draw = method(classes, renderer,
                Set.of("renderTileEntityAt", "func_147500_a"), DRAW_DESC);
        if (draw == null) return null;
        if (!draw.owner().equals(renderer)) return null;
        ArrayDeque<MethodNode> queue = new ArrayDeque<>();
        Set<MethodNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(draw.method());
        boolean hasDraw = false, callsAnimation = false, readsTileFields = false;
        while (!queue.isEmpty()) {
            if (seen.size() >= 16) return null;
            MethodNode current = queue.removeFirst();
            if (!seen.add(current)) continue;
            for (AbstractInsnNode insn : current.instructions) {
                if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                        && (field.owner.equals(tile) || sourceSuper(classes, tile, field.owner)))
                    readsTileFields = true;
                if (!(insn instanceof MethodInsnNode call)) continue;
                if (call.owner.equals(model) && call.desc.equals("(F)V")) hasDraw = true;
                if (call.owner.equals(model) && call.desc.equals("(L" + tile + ";F)V")) {
                    boolean declared = classes.containsKey(model) && classes.get(model).methods.stream()
                            .anyMatch(m -> m.name.equals(call.name) && m.desc.equals(call.desc));
                    if (declared) callsAnimation = true;
                }
                if (call.owner.equals(renderer)) {
                    ClassNode n = classes.get(renderer);
                    if (n != null) for (MethodNode m : n.methods) if (m.name.equals(call.name)
                            && m.desc.equals(call.desc)) queue.add(m);
                }
            }
        }
        return hasDraw ? new RenderEvidence(callsAnimation, readsTileFields) : null;
    }
    private static boolean sourceSuper(Map<String, ClassNode> classes, String start, String owner) {
        Set<String> visited = new HashSet<>();
        for (String here = start; here != null && visited.add(here);) {
            if (here.equals(owner)) return true;
            ClassNode n = classes.get(here);
            here = n == null ? null : n.superName;
        }
        return false;
    }
    private static boolean hasCall(MethodNode m, String name) {
        for (AbstractInsnNode insn : m.instructions)
            if (insn instanceof MethodInsnNode call && call.name.equals(name)) return true;
        return false;
    }
    private static OwnerMethod method(Map<String, ClassNode> classes, String start,
                                      Set<String> names, String desc) {
        Set<String> seen = new HashSet<>();
        for (String at = start; at != null && seen.add(at);) {
            ClassNode c = classes.get(at);
            if (c == null) return null;
            MethodNode found = null;
            for (MethodNode m : c.methods) if (names.contains(m.name) && m.desc.equals(desc)) {
                if (found != null) return null;
                found = m;
            }
            if (found != null) return new OwnerMethod(at, found);
            at = c.superName;
        }
        return null;
    }
    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> out = new ArrayList<>();
        if (method != null) for (AbstractInsnNode insn : method.instructions)
            if (insn.getOpcode() >= 0) out.add(insn);
        return out;
    }
    private static boolean inherits(Map<String, ClassNode> classes, String child, String base) {
        Set<String> seen = new HashSet<>();
        for (String here = child; here != null && seen.add(here);) {
            if (here.equals(base)) return true;
            ClassNode node = classes.get(here);
            here = node == null ? null : node.superName;
        }
        return false;
    }
    private static Map<String, ClassNode> load(Path jar) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile input = new JarFile(jar.toFile(), false)) {
            Enumeration<JarEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) continue;
                try (InputStream data = input.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(data).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (classes.putIfAbsent(node.name, node) != null)
                        throw new IOException("Duplicate source class: " + node.name);
                } catch (RuntimeException damaged) {
                    // Unreadable classes do not prove identity; their missing links fail closed.
                }
            }
        }
        return classes;
    }
}
