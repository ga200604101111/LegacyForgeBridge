package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces one already-proven processor IGuiHandler server/client case pair with null returns while
 * preserving every other GUI id in the shared handler.
 */
public final class LegacySingleInputProcessorGuiHandlerBranchStripper {
    public record Target(
            int guiId,
            String serverMethod,
            String serverDescriptor,
            String clientMethod,
            String clientDescriptor,
            String sourceTileClass,
            String sourceContainerClass,
            String sourceGuiClass) { }

    public record Result(
            byte[] bytes,
            int strippedBranches,
            boolean serverBranchStripped,
            boolean clientBranchStripped,
            List<String> blockers) {
        public Result {
            blockers = List.copyOf(blockers);
        }
    }

    private record SwitchCase(
            LabelNode target,
            LabelNode defaultLabel,
            Set<LabelNode> boundaries,
            boolean uniqueTarget) { }

    private record Branch(
            MethodNode method,
            SwitchCase shape,
            AbstractInsnNode firstMeaningful,
            AbstractInsnNode returnInstruction,
            List<AbstractInsnNode> meaningful) { }

    public Result strip(byte[] sourceClass, Target target) {
        List<String> blockers = new ArrayList<>();
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(sourceClass).accept(node, 0);

        MethodNode server = ownMethod(
                node, target.serverMethod(), target.serverDescriptor());
        MethodNode client = ownMethod(
                node, target.clientMethod(), target.clientDescriptor());
        if (server == null) blockers.add("processor-gui-handler-server-method-missing");
        if (client == null) blockers.add("processor-gui-handler-client-method-missing");
        if (!blockers.isEmpty()) {
            return new Result(sourceClass, 0, false, false, blockers);
        }

        Branch serverBranch = locateBranch(
                server, target.guiId(), "server", blockers);
        Branch clientBranch = locateBranch(
                client, target.guiId(), "client", blockers);
        if (serverBranch != null) {
            if (!references(serverBranch.meaningful(), target.sourceContainerClass())) {
                blockers.add("processor-gui-handler-server-container-reference-drift");
            }
            if (!references(serverBranch.meaningful(), target.sourceTileClass())) {
                blockers.add("processor-gui-handler-server-tile-reference-drift");
            }
        }
        if (clientBranch != null) {
            if (!references(clientBranch.meaningful(), target.sourceGuiClass())) {
                blockers.add("processor-gui-handler-client-gui-reference-drift");
            }
            if (!references(clientBranch.meaningful(), target.sourceTileClass())) {
                blockers.add("processor-gui-handler-client-tile-reference-drift");
            }
        }
        if (!blockers.isEmpty() || serverBranch == null || clientBranch == null) {
            return new Result(sourceClass, 0, false, false, blockers);
        }

        rewrite(serverBranch);
        rewrite(clientBranch);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        byte[] rewritten = writer.toByteArray();

        List<String> postBlockers = verifyPostRewrite(rewritten, target);
        if (!postBlockers.isEmpty()) {
            return new Result(sourceClass, 0, false, false, postBlockers);
        }
        return new Result(rewritten, 2, true, true, List.of());
    }

    private static List<String> verifyPostRewrite(byte[] rewritten, Target target) {
        List<String> blockers = new ArrayList<>();
        ClassNode node = new ClassNode(Opcodes.ASM9);
        try {
            new ClassReader(rewritten).accept(node, 0);
        } catch (RuntimeException malformed) {
            return List.of("processor-gui-handler-post-strip-reparse-failed:"
                    + malformed.getClass().getSimpleName());
        }

        MethodNode server = ownMethod(
                node, target.serverMethod(), target.serverDescriptor());
        MethodNode client = ownMethod(
                node, target.clientMethod(), target.clientDescriptor());
        if (server == null) blockers.add("processor-gui-handler-post-strip-server-method-missing");
        if (client == null) blockers.add("processor-gui-handler-post-strip-client-method-missing");
        if (!blockers.isEmpty()) return blockers;

        Branch serverBranch = locateBranch(
                server, target.guiId(), "post-server", blockers);
        Branch clientBranch = locateBranch(
                client, target.guiId(), "post-client", blockers);
        if (serverBranch == null || clientBranch == null) return blockers;

        if (!isNullReturn(serverBranch.meaningful())) {
            blockers.add("processor-gui-handler-post-strip-server-not-null-return");
        }
        if (!isNullReturn(clientBranch.meaningful())) {
            blockers.add("processor-gui-handler-post-strip-client-not-null-return");
        }

        Set<String> retired = Set.of(
                target.sourceTileClass(),
                target.sourceContainerClass(),
                target.sourceGuiClass());
        for (String sourceClass : retired) {
            if (references(serverBranch.meaningful(), sourceClass)) {
                blockers.add("processor-gui-handler-post-strip-server-reference-remains:"
                        + sourceClass);
            }
            if (references(clientBranch.meaningful(), sourceClass)) {
                blockers.add("processor-gui-handler-post-strip-client-reference-remains:"
                        + sourceClass);
            }
        }
        return blockers;
    }

    private static Branch locateBranch(
            MethodNode method,
            int guiId,
            String side,
            List<String> blockers) {
        List<SwitchCase> matches = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof TableSwitchInsnNode)
                    && !(instruction instanceof LookupSwitchInsnNode)) {
                continue;
            }
            AbstractInsnNode previous = previousMeaningful(instruction.getPrevious());
            if (!(previous instanceof VarInsnNode load)
                    || load.getOpcode() != Opcodes.ILOAD
                    || load.var != 1) {
                continue;
            }

            LabelNode target;
            LabelNode defaultLabel;
            List<LabelNode> labels = new ArrayList<>();
            if (instruction instanceof TableSwitchInsnNode table) {
                if (guiId < table.min || guiId > table.max) continue;
                target = table.labels.get(guiId - table.min);
                defaultLabel = table.dflt;
                labels.addAll(table.labels);
            } else {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) instruction;
                int index = lookup.keys.indexOf(guiId);
                if (index < 0) continue;
                target = lookup.labels.get(index);
                defaultLabel = lookup.dflt;
                labels.addAll(lookup.labels);
            }

            int targetUses = 0;
            for (LabelNode label : labels) {
                if (label == target) targetUses++;
            }
            Set<LabelNode> boundaries =
                    Collections.newSetFromMap(new IdentityHashMap<>());
            boundaries.add(defaultLabel);
            boundaries.addAll(labels);
            matches.add(new SwitchCase(
                    target,
                    defaultLabel,
                    boundaries,
                    targetUses == 1 && target != defaultLabel));
        }

        if (matches.isEmpty()) {
            blockers.add("processor-gui-handler-" + side + "-switch-case-missing");
            return null;
        }
        if (matches.size() != 1) {
            blockers.add("processor-gui-handler-" + side
                    + "-switch-case-ambiguous:" + matches.size());
            return null;
        }

        SwitchCase shape = matches.getFirst();
        if (!shape.uniqueTarget()) {
            blockers.add("processor-gui-handler-" + side + "-case-label-shared");
            return null;
        }

        Set<LabelNode> targetedLabels = targetedLabels(method);
        List<AbstractInsnNode> meaningful = new ArrayList<>();
        AbstractInsnNode firstMeaningful = null;
        AbstractInsnNode returnInstruction = null;
        boolean returnSeen = false;

        for (AbstractInsnNode cursor = shape.target().getNext();
             cursor != null;
             cursor = cursor.getNext()) {
            if (cursor instanceof LabelNode label
                    && shape.boundaries().contains(label)
                    && label != shape.target()) {
                break;
            }
            if (cursor instanceof LabelNode label
                    && targetedLabels.contains(label)) {
                blockers.add("processor-gui-handler-" + side
                        + "-case-has-external-control-flow-target");
                return null;
            }
            if (cursor instanceof LineNumberNode || cursor instanceof FrameNode
                    || cursor instanceof LabelNode) {
                continue;
            }

            if (firstMeaningful == null) firstMeaningful = cursor;
            if (returnSeen) {
                blockers.add("processor-gui-handler-" + side
                        + "-case-has-code-after-return");
                return null;
            }
            if (cursor instanceof JumpInsnNode
                    || cursor instanceof TableSwitchInsnNode
                    || cursor instanceof LookupSwitchInsnNode) {
                blockers.add("processor-gui-handler-" + side
                        + "-case-flow-not-simple");
                return null;
            }
            meaningful.add(cursor);
            if (cursor.getOpcode() == Opcodes.ARETURN) {
                returnInstruction = cursor;
                returnSeen = true;
            }
        }

        if (firstMeaningful == null || returnInstruction == null) {
            blockers.add("processor-gui-handler-" + side
                    + "-case-return-not-proven");
            return null;
        }
        return new Branch(
                method,
                shape,
                firstMeaningful,
                returnInstruction,
                List.copyOf(meaningful));
    }

    private static Set<LabelNode> targetedLabels(MethodNode method) {
        Set<LabelNode> result =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump) {
                result.add(jump.label);
            } else if (instruction instanceof TableSwitchInsnNode table) {
                result.add(table.dflt);
                result.addAll(table.labels);
            } else if (instruction instanceof LookupSwitchInsnNode lookup) {
                result.add(lookup.dflt);
                result.addAll(lookup.labels);
            }
        }
        return result;
    }

    private static void rewrite(Branch branch) {
        InsnList replacement = new InsnList();
        replacement.add(new InsnNode(Opcodes.ACONST_NULL));
        replacement.add(new InsnNode(Opcodes.ARETURN));
        branch.method().instructions.insertBefore(branch.firstMeaningful(), replacement);

        AbstractInsnNode cursor = branch.firstMeaningful();
        while (cursor != null) {
            AbstractInsnNode next = cursor.getNext();
            boolean done = cursor == branch.returnInstruction();
            branch.method().instructions.remove(cursor);
            if (done) break;
            cursor = next;
        }
    }

    private static boolean isNullReturn(List<AbstractInsnNode> instructions) {
        return instructions.size() == 2
                && instructions.get(0).getOpcode() == Opcodes.ACONST_NULL
                && instructions.get(1).getOpcode() == Opcodes.ARETURN;
    }

    private static boolean references(
            List<AbstractInsnNode> instructions, String internalName) {
        if (internalName == null || internalName.isBlank()) return false;
        for (AbstractInsnNode instruction : instructions) {
            if (instruction instanceof TypeInsnNode type
                    && typeReference(type.desc, internalName)) {
                return true;
            }
            if (instruction instanceof FieldInsnNode field
                    && (internalName.equals(field.owner)
                    || descriptorReferences(field.desc, internalName))) {
                return true;
            }
            if (instruction instanceof MethodInsnNode call
                    && (internalName.equals(call.owner)
                    || descriptorReferences(call.desc, internalName))) {
                return true;
            }
            if (instruction instanceof MultiANewArrayInsnNode array
                    && descriptorReferences(array.desc, internalName)) {
                return true;
            }
            if (instruction instanceof LdcInsnNode ldc
                    && ldc.cst instanceof Type type
                    && type.getSort() == Type.OBJECT
                    && internalName.equals(type.getInternalName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean typeReference(String descriptorOrName, String internalName) {
        return internalName.equals(descriptorOrName)
                || descriptorReferences(descriptorOrName, internalName);
    }

    private static boolean descriptorReferences(String descriptor, String internalName) {
        return descriptor != null
                && descriptor.contains("L" + internalName + ";");
    }

    private static MethodNode ownMethod(
            ClassNode owner, String name, String descriptor) {
        if (owner == null || name == null || descriptor == null) return null;
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode node) {
        AbstractInsnNode cursor = node;
        while (cursor instanceof LabelNode
                || cursor instanceof LineNumberNode
                || cursor instanceof FrameNode) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }
}
