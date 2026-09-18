package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Retires only a freshly verified processor GUI-id case pair. No legacy class is loaded. Unknown
 * effects, shared entry points and verification failures return the original bytes atomically.
 */
public final class LegacySingleInputProcessorGuiHandlerBranchStripper {
    private static final String HANDLER_DESC =
            "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;";
    private static final String CONSTRUCTOR_DESC =
            "(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/tileentity/TileEntity;)V";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String WORLD = "net/minecraft/world/World";

    public record Target(
            int guiId, String serverMethod, String serverDescriptor,
            String clientMethod, String clientDescriptor, String sourceTileClass,
            String sourceContainerClass, String sourceGuiClass) { }

    public record Result(byte[] bytes, int strippedBranches, boolean serverBranchStripped,
                         boolean clientBranchStripped, List<String> blockers) {
        public Result { blockers = List.copyOf(blockers); }
    }

    private record Branch(MethodNode method, AbstractInsnNode dispatch, LabelNode label,
                          List<AbstractInsnNode> instructions) { }

    public Result strip(byte[] sourceClass, Target target) {
        if (!validTarget(target)) return rejected(sourceClass, "target-invalid");
        try {
            ClassNode node = read(sourceClass);
            List<String> blockers = new ArrayList<>();
            Branch server = locate(node, target.serverMethod(), target.serverDescriptor(),
                    target.guiId(), "server", blockers);
            Branch client = locate(node, target.clientMethod(), target.clientDescriptor(),
                    target.guiId(), "client", blockers);
            if (server != null && !exactConstruction(server.instructions(),
                    target.sourceContainerClass(), target.sourceTileClass())) {
                blockers.add("processor-gui-handler-server-construction-or-effects-not-proven");
            }
            if (client != null && !exactConstruction(client.instructions(),
                    target.sourceGuiClass(), target.sourceTileClass())) {
                blockers.add("processor-gui-handler-client-construction-or-effects-not-proven");
            }
            if (!blockers.isEmpty() || server == null || client == null) {
                return new Result(sourceClass, 0, false, false, blockers);
            }

            rewrite(server);
            rewrite(client);
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            byte[] rewritten = writer.toByteArray();
            ClassNode checked = read(rewritten);
            Branch postServer = locate(checked, target.serverMethod(), target.serverDescriptor(),
                    target.guiId(), "post-server", blockers);
            Branch postClient = locate(checked, target.clientMethod(), target.clientDescriptor(),
                    target.guiId(), "post-client", blockers);
            if (postServer == null || !nullReturn(postServer.instructions())
                    || postClient == null || !nullReturn(postClient.instructions())) {
                blockers.add("processor-gui-handler-post-strip-null-return-not-proven");
            }
            if (!blockers.isEmpty()) {
                return new Result(sourceClass, 0, false, false, blockers);
            }
            return new Result(rewritten, 2, true, true, List.of());
        } catch (RuntimeException failure) {
            return rejected(sourceClass, "verification-failed:" + failure.getClass().getSimpleName());
        }
    }

    private static boolean validTarget(Target target) {
        return target != null
                && "getServerGuiElement".equals(target.serverMethod())
                && "getClientGuiElement".equals(target.clientMethod())
                && HANDLER_DESC.equals(target.serverDescriptor())
                && HANDLER_DESC.equals(target.clientDescriptor())
                && validName(target.sourceTileClass())
                && validName(target.sourceContainerClass())
                && validName(target.sourceGuiClass())
                && !target.sourceTileClass().equals(target.sourceContainerClass())
                && !target.sourceTileClass().equals(target.sourceGuiClass())
                && !target.sourceContainerClass().equals(target.sourceGuiClass());
    }

    private static boolean validName(String value) {
        return value != null && !value.isBlank() && !value.startsWith("/")
                && !value.endsWith("/") && !value.contains("//") && !value.contains(".")
                && !value.contains("\\") && !value.contains(";") && !value.contains("[");
    }

    private static Result rejected(byte[] original, String blocker) {
        return new Result(original, 0, false, false,
                List.of("processor-gui-handler-" + blocker));
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static Branch locate(ClassNode owner, String name, String descriptor, int guiId,
                                 String side, List<String> blockers) {
        String prefix = "processor-gui-handler-" + side + "-";
        List<MethodNode> methods = owner.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .toList();
        if (methods.size() != 1) {
            blockers.add(prefix + "method-missing-or-ambiguous");
            return null;
        }
        MethodNode method = methods.getFirst();
        if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || (method.access & Opcodes.ACC_PUBLIC) == 0) {
            blockers.add(prefix + "method-contract-invalid");
            return null;
        }
        // Exception entry points/ranges need an independent proof, not just ordinary CFG edges.
        if (!method.tryCatchBlocks.isEmpty()) {
            blockers.add(prefix + "exception-handlers-not-proven");
            return null;
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if ((instruction instanceof VarInsnNode variable
                    && variable.var >= 1 && variable.var <= 6
                    && instruction.getOpcode() >= Opcodes.ISTORE
                    && instruction.getOpcode() <= Opcodes.ASTORE)
                    || (instruction instanceof IincInsnNode increment
                    && increment.var >= 1 && increment.var <= 6)) {
                blockers.add(prefix + "handler-parameter-overwritten");
                return null;
            }
        }

        AbstractInsnNode dispatch = null;
        LabelNode label = null;
        List<LabelNode> boundaries = List.of();
        for (AbstractInsnNode instruction : method.instructions) {
            LabelNode candidate = caseLabel(instruction, guiId);
            if (candidate == null || !variable(previous(instruction), Opcodes.ILOAD, 1)) continue;
            if (dispatch != null) {
                blockers.add(prefix + "switch-case-ambiguous");
                return null;
            }
            dispatch = instruction;
            label = candidate;
            boundaries = labels(instruction);
        }
        if (dispatch == null || label == null) {
            blockers.add(prefix + "switch-case-missing");
            return null;
        }

        AbstractInsnNode entry = label;
        while (entry != null && entry.getOpcode() < 0) entry = entry.getNext();
        int entryIndex = entry == null ? -1 : method.instructions.indexOf(entry);
        int selectedEntries = 0;
        for (LabelNode destination : boundaries) {
            if (enters(method, destination, entryIndex, entryIndex)) selectedEntries++;
        }
        if (entryIndex < 0 || selectedEntries != 1) {
            blockers.add(prefix + "case-label-shared");
            return null;
        }

        List<AbstractInsnNode> body = new ArrayList<>();
        boolean returned = false;
        for (AbstractInsnNode cursor = label.getNext(); cursor != null; cursor = cursor.getNext()) {
            if (cursor instanceof LabelNode boundary && boundaries.contains(boundary)) break;
            if (cursor.getOpcode() < 0) continue;
            if (returned) {
                blockers.add(prefix + "case-has-code-after-return");
                return null;
            }
            body.add(cursor);
            if (cursor.getOpcode() == Opcodes.ARETURN) returned = true;
        }
        if (!returned || body.isEmpty()) {
            blockers.add(prefix + "case-return-not-proven");
            return null;
        }
        int first = method.instructions.indexOf(body.getFirst());
        int last = method.instructions.indexOf(body.getLast());
        // Count destinations by executable offset, not LabelNode identity: aliases are shared too.
        int switchEntries = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump
                    && enters(method, jump.label, first, last)) {
                blockers.add(prefix + "case-has-external-control-flow-target");
                return null;
            }
            for (LabelNode destination : labels(instruction)) {
                if (enters(method, destination, first, last)) {
                    if (instruction != dispatch || destination != label) {
                        blockers.add(prefix + "case-label-shared");
                        return null;
                    }
                    switchEntries++;
                }
            }
        }
        if (switchEntries != 1) {
            blockers.add(prefix + "case-label-shared");
            return null;
        }
        AbstractInsnNode before = previous(body.getFirst());
        if (before == null || !terminates(before.getOpcode())) {
            blockers.add(prefix + "case-has-fallthrough-entry");
            return null;
        }
        // Preserve frames at the case entry and all unrelated cases. An interior frame can carry
        // removed NEW offsets or changed locals and is outside this bounded rewrite.
        for (AbstractInsnNode cursor = body.getFirst(); cursor != body.getLast();
             cursor = cursor.getNext()) {
            if (cursor instanceof FrameNode) {
                blockers.add(prefix + "interior-stack-frame-not-proven");
                return null;
            }
        }
        try {
            var frames = new Analyzer<>(new BasicVerifier()).analyze(owner.name, method);
            if (frames[first] == null || frames[first].getStackSize() != 0) {
                blockers.add(prefix + "case-entry-stack-not-empty-or-unreachable");
                return null;
            }
        } catch (Exception invalid) {
            blockers.add(prefix + "bytecode-verification-failed:"
                    + invalid.getClass().getSimpleName());
            return null;
        }
        return new Branch(method, dispatch, label, List.copyOf(body));
    }

    private static LabelNode caseLabel(AbstractInsnNode instruction, int id) {
        if (instruction instanceof TableSwitchInsnNode table && id >= table.min && id <= table.max) {
            return table.labels.get(id - table.min);
        }
        if (instruction instanceof LookupSwitchInsnNode lookup) {
            int index = lookup.keys.indexOf(id);
            if (index >= 0) return lookup.labels.get(index);
        }
        return null;
    }

    private static List<LabelNode> labels(AbstractInsnNode instruction) {
        List<LabelNode> result = new ArrayList<>();
        if (instruction instanceof TableSwitchInsnNode table) {
            result.add(table.dflt);
            result.addAll(table.labels);
        } else if (instruction instanceof LookupSwitchInsnNode lookup) {
            result.add(lookup.dflt);
            result.addAll(lookup.labels);
        }
        return result;
    }

    private static boolean enters(MethodNode method, LabelNode label, int first, int last) {
        AbstractInsnNode cursor = label;
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getNext();
        int index = cursor == null ? -1 : method.instructions.indexOf(cursor);
        return index >= first && index <= last;
    }

    private static boolean terminates(int opcode) {
        return opcode == Opcodes.GOTO || opcode == Opcodes.TABLESWITCH
                || opcode == Opcodes.LOOKUPSWITCH || opcode == Opcodes.ATHROW
                || (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN);
    }

    private static boolean exactConstruction(List<AbstractInsnNode> body, String constructed,
                                             String tile) {
        // NEW/DUP, the handler player's inventory, the handler World(x,y,z), optional tile cast,
        // exactly one admitted constructor, and return of that object. No extra effects are erased.
        if (body.size() != 11 && body.size() != 12) return false;
        if (!type(body.get(0), Opcodes.NEW, constructed) || body.get(1).getOpcode() != Opcodes.DUP
                || !variable(body.get(2), Opcodes.ALOAD, 2)
                || !(body.get(3) instanceof FieldInsnNode field)
                || field.getOpcode() != Opcodes.GETFIELD || !PLAYER.equals(field.owner)
                || !Set.of("inventory", "field_71071_by").contains(field.name)
                || !"Lnet/minecraft/entity/player/InventoryPlayer;".equals(field.desc)
                || !variable(body.get(4), Opcodes.ALOAD, 3)
                || !variable(body.get(5), Opcodes.ILOAD, 4)
                || !variable(body.get(6), Opcodes.ILOAD, 5)
                || !variable(body.get(7), Opcodes.ILOAD, 6)
                || !(body.get(8) instanceof MethodInsnNode lookup)
                || lookup.getOpcode() != Opcodes.INVOKEVIRTUAL || lookup.itf
                || !WORLD.equals(lookup.owner)
                || !Set.of("getTileEntity", "func_147438_o").contains(lookup.name)
                || !"(III)Lnet/minecraft/tileentity/TileEntity;".equals(lookup.desc)) return false;
        int constructor = 9;
        if (body.size() == 12) {
            if (!type(body.get(9), Opcodes.CHECKCAST, tile)) return false;
            constructor++;
        }
        return body.get(constructor) instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESPECIAL && !call.itf
                && constructed.equals(call.owner) && "<init>".equals(call.name)
                && CONSTRUCTOR_DESC.equals(call.desc)
                && body.get(constructor + 1).getOpcode() == Opcodes.ARETURN;
    }

    private static boolean type(AbstractInsnNode node, int opcode, String type) {
        return node instanceof TypeInsnNode instruction && node.getOpcode() == opcode
                && type.equals(instruction.desc);
    }

    private static boolean variable(AbstractInsnNode node, int opcode, int slot) {
        return node instanceof VarInsnNode instruction && node.getOpcode() == opcode
                && instruction.var == slot;
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        AbstractInsnNode cursor = node.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getPrevious();
        return cursor;
    }

    private static boolean nullReturn(List<AbstractInsnNode> body) {
        return body.size() == 2 && body.getFirst().getOpcode() == Opcodes.ACONST_NULL
                && body.getLast().getOpcode() == Opcodes.ARETURN;
    }

    private static void rewrite(Branch branch) {
        InsnList replacement = new InsnList();
        replacement.add(new InsnNode(Opcodes.ACONST_NULL));
        replacement.add(new InsnNode(Opcodes.ARETURN));
        branch.method().instructions.insertBefore(branch.instructions().getFirst(), replacement);
        // Keep labels/debug scopes: deleting a range of nodes can leave dangling metadata labels.
        for (AbstractInsnNode instruction : branch.instructions()) {
            branch.method().instructions.remove(instruction);
        }
    }
}
