package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Strict client-side source proof that exactly one registered legacy item actually supplies the
 * projectile argument of World.spawnEntityInWorld in a supported 1.7.10 use/release callback.
 * Nothing here executes projectile logic or authorizes a modern runtime registration.
 *
 * <p>Unlike a method-wide NEW + spawn census, the proof traces the exact spawn operand through
 * verifier frames and bytecode producers. A factory, another entity, ambiguous branch allocation,
 * changed World receiver, unsupported callback, or duplicate launcher registration fails closed.</p>
 */
public final class LegacyProjectileLauncherAnalyzer {
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String STACK = "net/minecraft/item/ItemStack";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String SPAWN_DESC = "(L" + ENTITY + ";)Z";
    private static final int MAX_DEPTH = 32;

    public enum Callback { RIGHT_CLICK, RELEASE_USE }
    public record ItemRegistration(String registryName, String sourceItemClass) { }
    public record Proof(String registryName, String sourceItemClass, String declaringOwner,
                        String callbackName, String callbackDescriptor, Callback callback,
                        String projectileClass) { }
    public record Analysis(Optional<Proof> proof, List<String> diagnostics) {
        public Analysis {
            proof = Objects.requireNonNull(proof);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record CallbackSignature(Callback callback, Set<String> names, String descriptor) { }
    private static final List<CallbackSignature> CALLBACKS = List.of(
            new CallbackSignature(Callback.RELEASE_USE, Set.of("onPlayerStoppedUsing", "func_77615_a"),
                    "(L" + STACK + ";L" + WORLD + ";L" + PLAYER + ";I)V"),
            new CallbackSignature(Callback.RIGHT_CLICK, Set.of("onItemRightClick", "func_77659_a"),
                    "(L" + STACK + ";L" + WORLD + ";L" + PLAYER + ";)L" + STACK + ";"));

    private record Context(Frame<SourceValue>[] frames, Map<AbstractInsnNode, Integer> positions) { }

    /** The production path obtains registered item classes from the source registry analyzer. */
    public Analysis analyze(Path jar, String projectileClass) throws IOException {
        var registry = new LegacyRegistryAnalyzer().analyze(jar);
        List<ItemRegistration> items = new ArrayList<>();
        for (var item : registry.items())
            items.add(new ItemRegistration(item.registryName(), item.implementationClass()));
        return inspect(jar, projectileClass, items);
    }

    /** Allows renamed synthetic registration fixtures without mod/class/name dispatch tables. */
    public Analysis inspect(Path jar, String projectileClass, List<ItemRegistration> items) throws IOException {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(items, "items");
        if (projectileClass == null || projectileClass.isBlank())
            return failed("Projectile source identity is missing");
        Map<String, ClassNode> classes = load(jar);
        if (!classes.containsKey(projectileClass))
            return failed("Projectile class must be source-owned");

        List<Proof> matches = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (ItemRegistration item : items) {
            if (item == null || item.registryName() == null || item.registryName().isBlank()
                    || item.sourceItemClass() == null || item.sourceItemClass().isBlank()
                    || !inherits(classes, item.sourceItemClass(), ITEM)) continue;
            for (CallbackSignature callback : CALLBACKS) {
                MethodOwner effective = effective(classes, item.sourceItemClass(), callback);
                if (effective == null) continue;
                MethodNode method = effective.method();
                List<MethodInsnNode> spawnCalls = new ArrayList<>();
                for (AbstractInsnNode insn : method.instructions)
                    if (insn instanceof MethodInsnNode call && isSpawnCall(call)) spawnCalls.add(call);
                if (spawnCalls.isEmpty()) continue;
                if (spawnCalls.size() != 1) {
                    reasons.add("Callback has multiple direct World.spawnEntityInWorld calls: " + effective.owner());
                    continue;
                }
                if (!isProvenLaunch(classes, effective.owner(), method, spawnCalls.getFirst(), projectileClass)) {
                    reasons.add("Projectile spawn operand/constructor/World receiver not proven: "
                            + effective.owner() + "." + method.name + method.desc);
                    continue;
                }
                matches.add(new Proof(item.registryName(), item.sourceItemClass(), effective.owner(),
                        method.name, method.desc, callback.callback(), projectileClass));
            }
        }
        if (matches.size() != 1) {
            if (matches.size() > 1) reasons.add("More than one registered item/callback launches projectile");
            if (reasons.isEmpty()) reasons.add("No unique source-proven registered item launcher");
            return new Analysis(Optional.empty(), List.copyOf(new LinkedHashSet<>(reasons)));
        }
        Proof winner = matches.getFirst();
        long duplicateName = items.stream().filter(i -> i != null && winner.registryName().equals(i.registryName())).count();
        long duplicateClass = items.stream().filter(i -> i != null && winner.sourceItemClass().equals(i.sourceItemClass())).count();
        if (duplicateName != 1 || duplicateClass != 1)
            return failed("Item registration name/class identity is reused or ambiguous");
        // A second candidate callback can carry an unproved spawn path; do not endorse the item in
        // that case just because one call site happened to pass the strict proof.
        if (!reasons.isEmpty()) return new Analysis(Optional.empty(), List.copyOf(new LinkedHashSet<>(reasons)));
        return new Analysis(Optional.of(winner), List.of());
    }

    private record MethodOwner(String owner, MethodNode method) { }
    private static MethodOwner effective(Map<String, ClassNode> classes, String itemClass, CallbackSignature callback) {
        Set<String> visited = new HashSet<>();
        for (String current = itemClass; current != null && visited.add(current);) {
            ClassNode owner = classes.get(current);
            if (owner == null) break;
            for (MethodNode method : owner.methods) if (callback.names().contains(method.name)
                    && callback.descriptor().equals(method.desc) && (method.access & Opcodes.ACC_STATIC) == 0)
                return new MethodOwner(owner.name, method);
            current = owner.superName;
        }
        return null;
    }

    private static boolean isSpawnCall(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && WORLD.equals(call.owner)
                && SPAWN_DESC.equals(call.desc)
                && (call.name.equals("spawnEntityInWorld") || call.name.equals("func_72838_d"));
    }

    private static boolean isProvenLaunch(Map<String, ClassNode> classes, String owner, MethodNode method,
                                          MethodInsnNode spawn, String projectileClass) {
        Context ctx;
        try {
            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
            Frame<SourceValue>[] frames = analyzer.analyze(owner, method);
            Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
            for (int i = 0; i < method.instructions.size(); i++) indices.put(method.instructions.get(i), i);
            ctx = new Context(frames, indices);
        } catch (AnalyzerException | RuntimeException unreadable) { return false; }
        Integer index = ctx.positions().get(spawn);
        if (index == null || ctx.frames()[index] == null) return false;
        Frame<SourceValue> frame = ctx.frames()[index];
        if (frame.getStackSize() < 2) return false;
        // The explicit Entity argument must originate from one, and only one, NEW allocation.
        TypeInsnNode allocation = allocation(ctx, frame.getStack(frame.getStackSize() - 1), 0,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        if (allocation == null || !projectileClass.equals(allocation.desc)) return false;
        if (!worldParameter(ctx, frame.getStack(frame.getStackSize() - 2), 0,
                Collections.newSetFromMap(new IdentityHashMap<>()))) return false;

        int correspondingConstructors = 0;
        for (int i = 0; i < index; i++) {
            AbstractInsnNode insn = method.instructions.get(i);
            if (!(insn instanceof MethodInsnNode ctor) || ctor.getOpcode() != Opcodes.INVOKESPECIAL
                    || !ctor.owner.equals(projectileClass) || !ctor.name.equals("<init>")) continue;
            // The actual source class must implement the exact invoked descriptor. A textual NEW
            // and <init> pair that would link to a nonexistent constructor cannot be a proof.
            ClassNode target = classes.get(projectileClass);
            if (target == null || target.methods.stream().noneMatch(defined ->
                    defined.name.equals("<init>") && defined.desc.equals(ctor.desc))) return false;
            Frame<SourceValue> ctorFrame = ctx.frames()[i];
            if (ctorFrame == null) continue;
            int receiver = ctorFrame.getStackSize() - Type.getArgumentTypes(ctor.desc).length - 1;
            if (receiver < 0) continue;
            TypeInsnNode ctorAllocation = allocation(ctx, ctorFrame.getStack(receiver), 0,
                    Collections.newSetFromMap(new IdentityHashMap<>()));
            if (ctorAllocation == allocation) correspondingConstructors++;
        }
        return correspondingConstructors == 1;
    }

    private static TypeInsnNode allocation(Context ctx, SourceValue value, int depth,
                                            Set<AbstractInsnNode> path) {
        if (value == null || value.insns == null || value.insns.isEmpty() || depth > MAX_DEPTH) return null;
        TypeInsnNode resolved = null;
        for (AbstractInsnNode producer : value.insns) {
            if (!path.add(producer)) return null;
            TypeInsnNode candidate;
            if (producer instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) candidate = type;
            else if (producer instanceof VarInsnNode var && var.getOpcode() == Opcodes.ALOAD) {
                Frame<SourceValue> frame = before(ctx, producer);
                candidate = frame == null || var.var >= frame.getLocals() ? null
                        : allocation(ctx, frame.getLocal(var.var), depth + 1, path);
            } else if (producer instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE) {
                Frame<SourceValue> frame = before(ctx, producer);
                candidate = frame == null || frame.getStackSize() < 1 ? null
                        : allocation(ctx, frame.getStack(frame.getStackSize() - 1), depth + 1, path);
            } else if (producer.getOpcode() == Opcodes.DUP || producer.getOpcode() == Opcodes.CHECKCAST) {
                Frame<SourceValue> frame = before(ctx, producer);
                candidate = frame == null || frame.getStackSize() < 1 ? null
                        : allocation(ctx, frame.getStack(frame.getStackSize() - 1), depth + 1, path);
            } else candidate = null;
            path.remove(producer);
            if (candidate == null || (resolved != null && resolved != candidate)) return null;
            resolved = candidate;
        }
        return resolved;
    }

    /** Proves spawn is invoked on the source callback's World parameter, never another World. */
    private static boolean worldParameter(Context ctx, SourceValue value, int depth,
                                          Set<AbstractInsnNode> path) {
        if (value == null || value.insns == null || value.insns.isEmpty() || depth > MAX_DEPTH) return false;
        for (AbstractInsnNode producer : value.insns) {
            if (!path.add(producer)) return false;
            Frame<SourceValue> frame = before(ctx, producer);
            if (frame == null) return false;
            if (producer instanceof VarInsnNode var && var.getOpcode() == Opcodes.ALOAD) {
                if (var.var >= frame.getLocals()) return false;
                SourceValue original = frame.getLocal(var.var);
                if (!(var.var == 2 && original != null && original.insns != null && original.insns.isEmpty())
                        && !worldParameter(ctx, original, depth + 1, path)) return false;
            } else if (producer instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE) {
                if (frame.getStackSize() < 1 || !worldParameter(ctx,
                        frame.getStack(frame.getStackSize() - 1), depth + 1, path)) return false;
            } else return false;
            path.remove(producer);
        }
        return true;
    }

    private static Frame<SourceValue> before(Context ctx, AbstractInsnNode insn) {
        Integer index = ctx.positions().get(insn);
        return index == null ? null : ctx.frames()[index];
    }
    private static boolean inherits(Map<String, ClassNode> classes, String child, String root) {
        Set<String> seen = new HashSet<>();
        for (String current = child; current != null && seen.add(current);) {
            if (root.equals(current)) return true;
            ClassNode node = classes.get(current);
            current = node == null ? null : node.superName;
        }
        return false;
    }
    private static Analysis failed(String message) {
        return new Analysis(Optional.empty(), List.of(message));
    }
    private static Map<String, ClassNode> load(Path sourceJar) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile source = new JarFile(sourceJar.toFile(), false)) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = source.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (classes.putIfAbsent(node.name, node) != null)
                        throw new IOException("Duplicate source class " + node.name);
                } catch (RuntimeException malformed) {
                    // A damaged class cannot substantiate a launch; unknown owners stay fail-closed.
                }
            }
        }
        return classes;
    }
}
