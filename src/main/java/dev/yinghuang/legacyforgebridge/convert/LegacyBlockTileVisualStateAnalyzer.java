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
 * Read-only 1.7.10 Block/TileEntity/TESR visual-state dependency inventory.
 *
 * <p>Source GETFIELD/PUTFIELD, GL and NBT opcodes are <em>observations</em>, not an operand-
 * causal proof that a server tick field is delivered to the client. Even a present description
 * packet handler is not a networking bridge. No legacy class is loaded or invoked.</p>
 *
 * <p>Only rev284 source-registered Block/TileEntity/TESR candidates may be supplied. All
 * results are diagnostic; this analyzer never grants runtime readiness or animation support.</p>
 */
public final class LegacyBlockTileVisualStateAnalyzer {
    private static final String TILE = "net/minecraft/tileentity/TileEntity";
    private static final String MODEL_PART = "net/minecraft/client/model/ModelRenderer";
    private static final String GL = "org/lwjgl/opengl/GL11";
    private static final String DRAW = "(L" + TILE + ";DDDF)V";
    private static final Set<String> TICKS = Set.of("updateEntity", "func_145845_h");
    private static final Set<String> RENDERS = Set.of("renderTileEntityAt", "func_147500_a");
    private static final Set<String> PIVOTS = Set.of("rotationPointX", "rotationPointY", "rotationPointZ",
            "field_78800_c", "field_78797_d", "field_78798_e");
    private static final int MAX_CLOSURE = 24;
    private static final int MAX_CANDIDATES = 512;

    public enum TileSyncAssessment {
        NO_SOURCE_RENDER_STATE_READS,
        RENDER_STATE_SYNC_UNPROVEN,
        SOURCE_PACKET_HOOK_PRESENT_BUT_PAYLOAD_UNPROVEN
    }

    public record Evidence(
            String sourceBlockClass, String tileClass, String rendererClass, String modelClass,
            List<String> rendererTileFieldsRead, List<String> modelAnimationTileFieldsRead,
            List<String> tileTickFieldsWritten, List<String> tickDrivenRenderFields,
            List<String> modelPivotFieldsWritten,
            boolean modelAnimationCallObserved, boolean tileMetadataLookupObserved,
            int sourceGlRotateCalls, boolean negativeScaleObserved,
            boolean sourceNbtHooksPresent, boolean sourceTilePacketHookPresent,
            TileSyncAssessment tileSyncAssessment,
            boolean tileStateSyncProven, boolean rotationMapProven,
            boolean animationRuntimeWired) {
        public Evidence {
            Objects.requireNonNull(sourceBlockClass);
            Objects.requireNonNull(tileClass);
            Objects.requireNonNull(rendererClass);
            Objects.requireNonNull(modelClass);
            rendererTileFieldsRead = immutableSorted(rendererTileFieldsRead);
            modelAnimationTileFieldsRead = immutableSorted(modelAnimationTileFieldsRead);
            tileTickFieldsWritten = immutableSorted(tileTickFieldsWritten);
            tickDrivenRenderFields = immutableSorted(tickDrivenRenderFields);
            modelPivotFieldsWritten = immutableSorted(modelPivotFieldsWritten);
            Objects.requireNonNull(tileSyncAssessment);
            if (tileStateSyncProven || rotationMapProven || animationRuntimeWired)
                throw new IllegalArgumentException("Source-only proof cannot grant runtime readiness");
        }
    }

    public record Analysis(Map<String, Evidence> byBlockSourceClass, List<String> diagnostics) {
        public Analysis {
            byBlockSourceClass = Map.copyOf(byBlockSourceClass);
            diagnostics = List.copyOf(diagnostics);
        }
    }
    private record MethodRef(String owner, MethodNode method) { }
    private record RenderUse(Set<String> tileFields, Set<String> animatedTileFields,
                             Set<String> modelPivotWrites, boolean modelAnimationCalled,
                             boolean tileMetadataLookup, int glRotateCalls, boolean negativeScale) { }
    private record Closure(List<MethodRef> reachable, boolean complete) { }

    public Analysis analyze(Path jar, List<LegacyBlockTileModelPreflight.Candidate> sourceCandidates)
            throws IOException {
        Objects.requireNonNull(jar);
        Objects.requireNonNull(sourceCandidates);
        if (sourceCandidates.size() > MAX_CANDIDATES)
            throw new IllegalArgumentException("Unbounded Block/Tile source candidate count");
        Map<String, ClassNode> classes = sourceClasses(jar);
        Map<String, Evidence> results = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        for (var candidate : sourceCandidates) {
            if (candidate == null) { diagnostics.add("Null source candidate skipped"); continue; }
            String key = candidate.sourceBlockClass();
            if (key == null || key.isBlank() || results.containsKey(key)) {
                diagnostics.add("Missing/duplicate source block identity in visual-state scan");
                continue;
            }
            Evidence state = inspect(classes, candidate);
            if (state == null) {
                diagnostics.add("Visual state not source-provable for registered block " + key);
                continue;
            }
            results.put(key, state);
        }
        return new Analysis(results, diagnostics);
    }

    private static Evidence inspect(Map<String, ClassNode> classes,
                                    LegacyBlockTileModelPreflight.Candidate source) {
        String tile = source.tileClass(), renderer = source.rendererClass(), model = source.modelClass();
        if (!classes.containsKey(source.sourceBlockClass()) || !classes.containsKey(tile)
                || !classes.containsKey(renderer) || !classes.containsKey(model)
                || !inherits(classes, tile, TILE)) return null;
        MethodRef draw = method(classes, renderer, RENDERS, DRAW);
        if (draw == null || !draw.owner().equals(renderer)) return null;
        Closure renderClosure = closure(classes, renderer, draw);
        if (!renderClosure.complete()) return null;

        SortedSet<String> directReads = new TreeSet<>(), animationReads = new TreeSet<>(), modelPivots = new TreeSet<>();
        boolean animationCalled = false, metadata = false, negativeScale = false;
        int glRotations = 0;
        String animDescriptor = "(L" + tile + ";F)V";
        for (MethodRef ref : renderClosure.reachable()) {
            for (AbstractInsnNode instruction : ref.method().instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                        && sourceTileField(classes, tile, field)) directReads.add(field.name);
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if ("getBlockMetadata".equals(call.name) || "func_145832_p".equals(call.name))
                    metadata |= sourceTileAccessor(classes, tile, call.owner);
                if (GL.equals(call.owner) && call.name.equals("glRotatef") && call.desc.equals("(FFFF)V"))
                    glRotations++;
                if (GL.equals(call.owner) && (call.name.equals("glScalef") || call.name.equals("glScaled")))
                    negativeScale |= negativeScaleArguments(call);
                if (call.owner.equals(model) && call.desc.equals(animDescriptor)
                        && !"<init>".equals(call.name)) {
                    ClassNode modelNode = classes.get(model);
                    MethodNode animation = singleMethod(modelNode, Set.of(call.name), animDescriptor);
                    if (animation == null || animation.tryCatchBlocks.size() > 0) return null;
                    animationCalled = true;
                    for (AbstractInsnNode partInsn : animation.instructions) {
                        if (partInsn instanceof FieldInsnNode get && get.getOpcode() == Opcodes.GETFIELD
                                && sourceTileField(classes, tile, get)) animationReads.add(get.name);
                        if (partInsn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTFIELD
                                && MODEL_PART.equals(put.owner) && PIVOTS.contains(put.name)
                                && put.desc.equals("F")) modelPivots.add(put.name);
                    }
                }
            }
        }

        SortedSet<String> tickWrites = new TreeSet<>();
        MethodRef tick = method(classes, tile, TICKS, "()V");
        if (tick != null) {
            Closure tickClosure = closure(classes, tick.owner(), tick);
            if (!tickClosure.complete()) return null;
            for (MethodRef ref : tickClosure.reachable())
                for (AbstractInsnNode insn : ref.method().instructions)
                    if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                            && sourceTileField(classes, tile, field)) tickWrites.add(field.name);
        }

        SortedSet<String> usedByRender = new TreeSet<>(directReads);
        usedByRender.addAll(animationReads);
        SortedSet<String> tickDriven = new TreeSet<>(usedByRender);
        tickDriven.retainAll(tickWrites);
        boolean nbt = method(classes, tile, Set.of("writeToNBT", "func_145841_b"),
                    "(Lnet/minecraft/nbt/NBTTagCompound;)V") != null
                || method(classes, tile, Set.of("readFromNBT", "func_145839_a"),
                    "(Lnet/minecraft/nbt/NBTTagCompound;)V") != null;
        boolean packet = methodDeclaredInSourceHierarchy(classes, tile,
                Set.of("getDescriptionPacket", "func_145844_m"),
                "()Lnet/minecraft/network/Packet;")
                || methodDeclaredInSourceHierarchy(classes, tile,
                Set.of("onDataPacket", "func_145836_u"),
                "(Lnet/minecraft/network/NetworkManager;Lnet/minecraft/network/play/server/S35PacketUpdateTileEntity;)V");
        TileSyncAssessment assessment = usedByRender.isEmpty()
                ? TileSyncAssessment.NO_SOURCE_RENDER_STATE_READS
                : packet ? TileSyncAssessment.SOURCE_PACKET_HOOK_PRESENT_BUT_PAYLOAD_UNPROVEN
                        : TileSyncAssessment.RENDER_STATE_SYNC_UNPROVEN;
        return new Evidence(source.sourceBlockClass(), tile, renderer, model,
                List.copyOf(directReads), List.copyOf(animationReads), List.copyOf(tickWrites),
                List.copyOf(tickDriven), List.copyOf(modelPivots), animationCalled,
                metadata, glRotations, negativeScale, nbt, packet, assessment,
                false, false, false);
    }

    private static List<String> immutableSorted(List<String> fields) {
        Objects.requireNonNull(fields);
        if (fields.size() > 256 || fields.stream().anyMatch(x -> x == null || x.isBlank()))
            throw new IllegalArgumentException("Unbounded/invalid source visual-state fields");
        return List.copyOf(new TreeSet<>(fields));
    }
    private static boolean negativeScaleArguments(MethodInsnNode call) {
        // This is a local constant-shape observation, not proof of the complete GL transform.
        AbstractInsnNode step = call.getPrevious();
        double[] v = new double[3];
        for (int index = 2; index >= 0; index--) {
            while (step != null && step.getOpcode() < 0) step = step.getPrevious();
            if (step == null) return false;
            Double value = literalFloat(step);
            if (value == null || !Double.isFinite(value)) return false;
            v[index] = value;
            step = step.getPrevious();
        }
        return v[0] < 0 || v[1] < 0 || v[2] < 0;
    }
    private static Double literalFloat(AbstractInsnNode n) {
        if (n instanceof LdcInsnNode ldc && ldc.cst instanceof Number value)
            return value.doubleValue();
        return switch (n.getOpcode()) {
            case Opcodes.FCONST_0, Opcodes.DCONST_0 -> 0.0;
            case Opcodes.FCONST_1, Opcodes.DCONST_1 -> 1.0;
            case Opcodes.FCONST_2 -> 2.0;
            default -> null;
        };
    }
    private static boolean sourceTileAccessor(Map<String, ClassNode> classes, String tile, String owner) {
        return owner.equals(tile) || owner.equals(TILE)
                || (classes.containsKey(owner) && inherits(classes, tile, owner)
                && inherits(classes, owner, TILE));
    }
    private static boolean sourceTileField(Map<String, ClassNode> classes, String tile, FieldInsnNode field) {
        if (!sourceTileAccessor(classes, tile, field.owner)) return false;
        ClassNode owner = classes.get(field.owner);
        if (owner == null) return false;
        return owner.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)
                && (f.access & Opcodes.ACC_STATIC) == 0);
    }
    private static MethodNode singleMethod(ClassNode owner, Set<String> names, String desc) {
        if (owner == null) return null;
        MethodNode found = null;
        for (MethodNode m : owner.methods) if (names.contains(m.name) && m.desc.equals(desc)) {
            if (found != null || (m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
                return null;
            found = m;
        }
        return found;
    }
    private static MethodRef method(Map<String, ClassNode> classes, String owner,
                                    Set<String> names, String desc) {
        Set<String> seen = new HashSet<>();
        for (String current = owner; current != null && seen.add(current);) {
            ClassNode node = classes.get(current);
            if (node == null) return null;
            MethodNode found = singleMethod(node, names, desc);
            if (found != null) return new MethodRef(current, found);
            current = node.superName;
        }
        return null;
    }
    private static boolean methodDeclaredInSourceHierarchy(Map<String, ClassNode> classes, String owner,
                                                            Set<String> names, String desc) {
        MethodRef found = method(classes, owner, names, desc);
        return found != null && classes.containsKey(found.owner())
                && inherits(classes, found.owner(), TILE);
    }
    private static Closure closure(Map<String, ClassNode> classes, String owner, MethodRef root) {
        List<MethodRef> methods = new ArrayList<>();
        ArrayDeque<MethodRef> queue = new ArrayDeque<>();
        Set<MethodNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(root);
        while (!queue.isEmpty()) {
            if (methods.size() >= MAX_CLOSURE) return new Closure(List.of(), false);
            MethodRef at = queue.removeFirst();
            if (!seen.add(at.method())) continue;
            if (!at.method().tryCatchBlocks.isEmpty()) return new Closure(List.of(), false);
            methods.add(at);
            for (AbstractInsnNode insn : at.method().instructions) {
                if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(owner)) continue;
                ClassNode node = classes.get(owner);
                if (node == null) return new Closure(List.of(), false);
                // Intraclass helpers only; do not guess invokedynamic, virtual dispatch or unknown supercode.
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKESPECIAL) {
                    MethodNode next = singleMethod(node, Set.of(call.name), call.desc);
                    if (next == null) return new Closure(List.of(), false);
                    queue.addLast(new MethodRef(owner, next));
                }
            }
        }
        return new Closure(methods, true);
    }
    private static boolean inherits(Map<String, ClassNode> classes, String child, String base) {
        Set<String> seen = new HashSet<>();
        for (String current = child; current != null && seen.add(current);) {
            if (current.equals(base)) return true;
            ClassNode node = classes.get(current);
            current = node == null ? null : node.superName;
        }
        return false;
    }
    private static Map<String, ClassNode> sourceClasses(Path sourceJar) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(sourceJar.toFile(), false)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) continue;
                try (InputStream bytes = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (classes.putIfAbsent(node.name, node) != null)
                        throw new IOException("Duplicate source class identity: " + node.name);
                } catch (RuntimeException damaged) {
                    // Parser failures cannot grant state proof; missing class leads to explicit diagnostics.
                }
            }
        }
        return classes;
    }
}
