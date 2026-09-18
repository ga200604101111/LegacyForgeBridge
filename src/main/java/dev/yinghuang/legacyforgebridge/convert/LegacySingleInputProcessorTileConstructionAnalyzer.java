package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

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
 * Conservative constructor proof for runtime-complete single-input processor TileEntities.
 *
 * <p>Only source effects that the modern BlockEntity constructor necessarily reproduces are
 * admitted: an exact source constructor chain to vanilla TileEntity, one fixed-size ItemStack[]
 * inventory allocation matching the proven slot count, and optional explicit writes of JVM default
 * values to source-owned instance fields. Any branch, extra method call, static/global write,
 * non-default scalar/reference initialization or unsupported allocation fails closed.</p>
 */
public final class LegacySingleInputProcessorTileConstructionAnalyzer {
    private static final String TILE_ENTITY = "net/minecraft/tileentity/TileEntity";
    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";

    public record FieldInitialization(
            String owner,
            String name,
            String descriptor,
            String kind,
            Integer arrayLength) { }

    public record Proof(
            String registryName,
            String sourceTileClass,
            int expectedSlots,
            boolean constructorPresent,
            boolean constructorChainComplete,
            boolean constructorControlFlowSimple,
            boolean inventoryArrayInitializationProven,
            boolean defaultFieldWritesOnly,
            boolean noAdditionalMethodCalls,
            boolean replacementProofComplete,
            List<String> constructorChain,
            List<FieldInitialization> fieldInitializations,
            List<String> blockers) {
        public Proof {
            constructorChain = List.copyOf(constructorChain);
            fieldInitializations = List.copyOf(fieldInitializations);
            blockers = List.copyOf(blockers);
        }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis {
            proofs = List.copyOf(proofs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record Chain(
            List<String> constructors,
            List<FieldInitialization> initializations,
            boolean complete,
            boolean simple,
            int inventoryArrays,
            boolean defaultOnly,
            boolean noCalls,
            List<String> blockers) { }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path sourceJar) throws IOException {
        classes.clear();
        diagnostics.clear();
        load(sourceJar);

        LegacySingleInputProcessorAnalyzer.Analysis processors =
                new LegacySingleInputProcessorAnalyzer().analyze(sourceJar);
        diagnostics.addAll(processors.diagnostics());

        List<Proof> proofs = new ArrayList<>();
        for (LegacySingleInputProcessorAnalyzer.Rule rule : processors.rules()) {
            proofs.add(prove(rule));
        }
        return new Analysis(proofs, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    public Proof prove(
            Path sourceJar,
            LegacySingleInputProcessorAnalyzer.Rule rule) throws IOException {
        classes.clear();
        diagnostics.clear();
        load(sourceJar);
        return prove(rule);
    }

    private Proof prove(LegacySingleInputProcessorAnalyzer.Rule rule) {
        ClassNode tile = classes.get(rule.sourceTileClass());
        MethodNode constructor = findConstructor(tile, "()V");
        if (tile == null || constructor == null) {
            return new Proof(
                    rule.registryName(), rule.sourceTileClass(), rule.slots(),
                    false, false, false, false, false, false, false,
                    List.of(), List.of(),
                    List.of("source-tile-noarg-constructor-missing"));
        }

        Chain chain = collect(
                rule.sourceTileClass(), rule.slots(), new LinkedHashSet<>());

        boolean inventory = chain.inventoryArrays() == 1;
        List<String> blockers = new ArrayList<>(chain.blockers());
        if (!chain.complete()) blockers.add("source-tile-constructor-chain-incomplete");
        if (!chain.simple()) blockers.add("source-tile-constructor-control-flow-not-simple");
        if (!inventory) blockers.add(
                "source-tile-exact-inventory-array-initialization-not-proven:"
                        + chain.inventoryArrays());
        if (!chain.defaultOnly()) blockers.add("source-tile-nondefault-field-initialization");
        if (!chain.noCalls()) blockers.add("source-tile-extra-constructor-method-call");

        boolean complete = chain.complete()
                && chain.simple()
                && inventory
                && chain.defaultOnly()
                && chain.noCalls()
                && blockers.isEmpty();

        return new Proof(
                rule.registryName(),
                rule.sourceTileClass(),
                rule.slots(),
                true,
                chain.complete(),
                chain.simple(),
                inventory,
                chain.defaultOnly(),
                chain.noCalls(),
                complete,
                chain.constructors(),
                chain.initializations(),
                List.copyOf(new LinkedHashSet<>(blockers)));
    }

    private Chain collect(
            String ownerName,
            int expectedSlots,
            Set<String> visiting) {
        if (!visiting.add(ownerName)) {
            return failed(
                    "recursive-source-tile-constructor-chain:" + ownerName);
        }

        ClassNode owner = classes.get(ownerName);
        MethodNode constructor = findConstructor(owner, "()V");
        if (owner == null || constructor == null) {
            visiting.remove(ownerName);
            return failed("source-tile-constructor-missing:" + ownerName);
        }

        boolean simple = simpleControlFlow(constructor);
        List<AbstractInsnNode> code = real(constructor);
        List<FieldInitialization> localInitializations = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> constructors = new ArrayList<>();
        constructors.add(ownerName + "()V");

        int inventoryArrays = 0;
        boolean defaultOnly = true;
        boolean noCalls = true;
        boolean delegated = false;
        boolean complete = false;
        String sourceParent = null;

        for (int index = 0; index < code.size();) {
            AbstractInsnNode instruction = code.get(index);

            if (instruction.getOpcode() == Opcodes.NOP) {
                index++;
                continue;
            }
            if (instruction.getOpcode() == Opcodes.RETURN) {
                index++;
                continue;
            }

            if (index + 1 < code.size()
                    && aload0(code.get(index))
                    && code.get(index + 1) instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && "<init>".equals(call.name)
                    && "()V".equals(call.desc)) {
                if (delegated) {
                    blockers.add("multiple-source-tile-constructor-delegations:"
                            + ownerName);
                    noCalls = false;
                } else if (classes.containsKey(call.owner)) {
                    if (!call.owner.equals(owner.superName)) {
                        blockers.add("unexpected-source-tile-constructor-owner:"
                                + call.owner);
                        noCalls = false;
                    } else {
                        sourceParent = call.owner;
                        delegated = true;
                    }
                } else if (call.owner.equals(owner.superName)
                        && TILE_ENTITY.equals(call.owner)) {
                    delegated = true;
                    complete = true;
                } else {
                    blockers.add("unproven-external-tile-constructor:"
                            + call.owner + call.desc);
                    noCalls = false;
                }
                index += 2;
                continue;
            }

            if (index + 3 < code.size()
                    && aload0(code.get(index))
                    && intConstant(code.get(index + 1)) != null
                    && code.get(index + 2) instanceof TypeInsnNode array
                    && array.getOpcode() == Opcodes.ANEWARRAY
                    && ITEM_STACK.equals(array.desc)
                    && code.get(index + 3) instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD
                    && ("[L" + ITEM_STACK + ";").equals(field.desc)) {
                int size = intConstant(code.get(index + 1));
                localInitializations.add(new FieldInitialization(
                        field.owner, field.name, field.desc,
                        "item-stack-array", size));
                if (size == expectedSlots) {
                    inventoryArrays++;
                } else {
                    blockers.add("source-tile-inventory-array-size:"
                            + size + " expected:" + expectedSlots);
                    defaultOnly = false;
                }
                index += 4;
                continue;
            }

            if (index + 2 < code.size()
                    && aload0(code.get(index))
                    && code.get(index + 2) instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD) {
                if (isDefaultValue(code.get(index + 1), field.desc)) {
                    localInitializations.add(new FieldInitialization(
                            field.owner, field.name, field.desc,
                            "jvm-default-write", null));
                } else {
                    blockers.add("source-tile-nondefault-field-write:"
                            + field.owner + "." + field.name + field.desc);
                    defaultOnly = false;
                }
                index += 3;
                continue;
            }

            if (instruction instanceof MethodInsnNode call
                    && !"<init>".equals(call.name)) {
                blockers.add("source-tile-constructor-method-call:"
                        + call.owner + "." + call.name + call.desc);
                noCalls = false;
                index++;
                continue;
            }

            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTSTATIC) {
                blockers.add("source-tile-constructor-static-write:"
                        + field.owner + "." + field.name + field.desc);
                defaultOnly = false;
                index++;
                continue;
            }

            blockers.add("unsupported-source-tile-constructor-opcode:"
                    + ownerName + ":" + instruction.getOpcode());
            defaultOnly = false;
            index++;
        }

        List<FieldInitialization> initializations =
                new ArrayList<>(localInitializations);
        if (sourceParent != null) {
            Chain parent = collect(sourceParent, expectedSlots, visiting);
            constructors.addAll(parent.constructors());
            initializations.addAll(parent.initializations());
            complete = parent.complete();
            simple &= parent.simple();
            inventoryArrays += parent.inventoryArrays();
            defaultOnly &= parent.defaultOnly();
            noCalls &= parent.noCalls();
            blockers.addAll(parent.blockers());
        }

        visiting.remove(ownerName);
        return new Chain(
                List.copyOf(constructors),
                List.copyOf(initializations),
                complete,
                simple,
                inventoryArrays,
                defaultOnly,
                noCalls,
                List.copyOf(blockers));
    }

    private static Chain failed(String blocker) {
        return new Chain(
                List.of(), List.of(), false, false, 0,
                false, false, List.of(blocker));
    }

    private static boolean simpleControlFlow(MethodNode method) {
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            return false;
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode
                    || instruction instanceof LookupSwitchInsnNode
                    || instruction instanceof TableSwitchInsnNode) {
                return false;
            }
        }
        return true;
    }

    private static MethodNode findConstructor(ClassNode owner, String descriptor) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods) {
            if ("<init>".equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> output = new ArrayList<>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof LabelNode
                    || instruction instanceof LineNumberNode
                    || instruction instanceof org.objectweb.asm.tree.FrameNode) {
                continue;
            }
            output.add(instruction);
        }
        return output;
    }

    private static boolean aload0(AbstractInsnNode instruction) {
        return instruction instanceof org.objectweb.asm.tree.VarInsnNode variable
                && variable.getOpcode() == Opcodes.ALOAD
                && variable.var == 0;
    }

    private static Integer intConstant(AbstractInsnNode instruction) {
        if (instruction instanceof InsnNode insn) {
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                return opcode - Opcodes.ICONST_0;
            }
        }
        if (instruction instanceof org.objectweb.asm.tree.IntInsnNode value
                && (value.getOpcode() == Opcodes.BIPUSH
                || value.getOpcode() == Opcodes.SIPUSH)) {
            return value.operand;
        }
        if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                && ldc.cst instanceof Integer value) {
            return value;
        }
        return null;
    }

    private static boolean isDefaultValue(
            AbstractInsnNode instruction, String descriptor) {
        Type type;
        try {
            type = Type.getType(descriptor);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                    instruction.getOpcode() == Opcodes.ICONST_0;
            case Type.FLOAT -> instruction.getOpcode() == Opcodes.FCONST_0;
            case Type.LONG -> instruction.getOpcode() == Opcodes.LCONST_0;
            case Type.DOUBLE -> instruction.getOpcode() == Opcodes.DCONST_0;
            case Type.ARRAY, Type.OBJECT ->
                    instruction.getOpcode() == Opcodes.ACONST_NULL;
            default -> false;
        };
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()
                        || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(
                            node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add(
                            "Unreadable processor tile-construction class "
                                    + entry.getName() + ": "
                                    + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
