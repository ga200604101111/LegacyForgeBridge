package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
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
 * Proves the exact shared legacy IGuiHandler branches that correspond to one runtime-complete
 * single-input processor. This analyzer is evidence only; it never mutates source bytecode.
 */
public final class LegacySingleInputProcessorGuiHandlerAnalyzer {
    private static final String HANDLER =
            "cpw/mods/fml/common/network/IGuiHandler";
    private static final String CONTAINER =
            "net/minecraft/inventory/Container";
    private static final String HANDLER_DESC =
            "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;";
    private static final String SERVER_METHOD = "getServerGuiElement";
    private static final String CLIENT_METHOD = "getClientGuiElement";

    public record BranchProof(
            String methodName,
            String methodDescriptor,
            String switchKind,
            boolean guiIdDispatchProven,
            boolean uniqueCaseLabelProven,
            boolean simpleCaseFlowProven,
            boolean tileHandoffProven,
            String constructedClass,
            boolean returnObjectProven,
            int otherCaseCount,
            boolean complete,
            List<String> blockers) {
        public BranchProof {
            blockers = List.copyOf(blockers);
        }
    }

    public record Proof(
            String registryName,
            String sourceBlockClass,
            String sourceTileClass,
            int guiId,
            String sourceGuiClass,
            String sourceContainerClass,
            String handlerClass,
            BranchProof serverBranch,
            BranchProof clientBranch,
            boolean sameHandlerProven,
            boolean branchRetirementProofComplete,
            List<String> blockers) {
        public Proof {
            blockers = List.copyOf(blockers);
        }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis {
            proofs = List.copyOf(proofs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record SwitchCase(
            AbstractInsnNode switchInsn,
            LabelNode target,
            LabelNode defaultLabel,
            Set<LabelNode> boundaries,
            int otherCaseCount,
            String kind,
            boolean uniqueTarget) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(
            Path sourceJar,
            LegacySingleInputProcessorAnalyzer.Rule machine,
            String sourceGuiClass) throws IOException {
        classes.clear();
        diagnostics.clear();
        load(sourceJar);

        List<Proof> matches = new ArrayList<>();
        for (ClassNode owner : classes.values()) {
            if (!implementsType(owner.name, HANDLER)) continue;
            BranchProof server = proveBranch(
                    owner,
                    ownMethod(owner, SERVER_METHOD, HANDLER_DESC),
                    machine.guiId(),
                    machine.sourceTileClass(),
                    null,
                    true);
            BranchProof client = proveBranch(
                    owner,
                    ownMethod(owner, CLIENT_METHOD, HANDLER_DESC),
                    machine.guiId(),
                    machine.sourceTileClass(),
                    sourceGuiClass,
                    false);
            if (!server.complete() || !client.complete()) continue;
            String container = server.constructedClass();
            if (container == null || client.constructedClass() == null) continue;
            matches.add(new Proof(
                    machine.registryName(),
                    machine.sourceBlockClass(),
                    machine.sourceTileClass(),
                    machine.guiId(),
                    sourceGuiClass,
                    container,
                    owner.name,
                    server,
                    client,
                    true,
                    true,
                    List.of()));
        }

        if (matches.size() == 1) {
            return new Analysis(List.copyOf(matches), List.copyOf(diagnostics));
        }

        List<String> blockers = new ArrayList<>();
        blockers.add(matches.isEmpty()
                ? "processor-gui-handler-server-client-case-pair-not-proven"
                : "processor-gui-handler-case-pair-ambiguous:" + matches.size());
        Proof failed = new Proof(
                machine.registryName(),
                machine.sourceBlockClass(),
                machine.sourceTileClass(),
                machine.guiId(),
                sourceGuiClass,
                null,
                null,
                emptyBranch(SERVER_METHOD, blockers.getFirst()),
                emptyBranch(CLIENT_METHOD, blockers.getFirst()),
                false,
                false,
                blockers);
        return new Analysis(List.of(failed), List.copyOf(diagnostics));
    }

    private BranchProof proveBranch(
            ClassNode owner,
            MethodNode method,
            int guiId,
            String tileClass,
            String expectedConstructedClass,
            boolean server) {
        List<String> blockers = new ArrayList<>();
        if (method == null) {
            return emptyBranch(server ? SERVER_METHOD : CLIENT_METHOD,
                    "processor-gui-handler-method-missing");
        }

        SwitchCase shape = uniqueSwitchCase(method, guiId);
        if (shape == null) {
            return emptyBranch(method.name,
                    "processor-gui-handler-id-switch-not-uniquely-proven");
        }

        List<AbstractInsnNode> branch = branchInstructions(method, shape);
        boolean simple = !branch.isEmpty();
        for (AbstractInsnNode instruction : branch) {
            if (instruction instanceof JumpInsnNode
                    || instruction instanceof TableSwitchInsnNode
                    || instruction instanceof LookupSwitchInsnNode) {
                simple = false;
                break;
            }
        }
        if (!simple) blockers.add("processor-gui-handler-case-flow-not-simple");

        boolean returnObject = !branch.isEmpty()
                && branch.getLast().getOpcode() == Opcodes.ARETURN;
        if (!returnObject) blockers.add("processor-gui-handler-case-return-not-proven");

        LinkedHashSet<String> constructed = new LinkedHashSet<>();
        boolean handoff = false;
        for (AbstractInsnNode instruction : branch) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != Opcodes.INVOKESPECIAL
                    || !"<init>".equals(call.name)
                    || !LegacyGuiTileHandoff.CONSTRUCTOR.equals(call.desc)) {
                continue;
            }
            boolean classMatch = server
                    ? inherits(call.owner, CONTAINER)
                    : call.owner.equals(expectedConstructedClass);
            if (!classMatch) continue;
            if (!LegacyGuiTileHandoff.fromWorld(owner, method, call, tileClass)) {
                continue;
            }
            constructed.add(call.owner);
            handoff = true;
        }

        if (!handoff) blockers.add("processor-gui-handler-tile-handoff-not-proven");
        if (constructed.size() != 1) {
            blockers.add("processor-gui-handler-constructed-class-not-unique:"
                    + constructed.size());
        }

        boolean complete = shape.uniqueTarget()
                && simple
                && returnObject
                && handoff
                && constructed.size() == 1
                && blockers.isEmpty();

        return new BranchProof(
                method.name,
                method.desc,
                shape.kind(),
                true,
                shape.uniqueTarget(),
                simple,
                handoff,
                constructed.size() == 1 ? constructed.getFirst() : null,
                returnObject,
                shape.otherCaseCount(),
                complete,
                blockers);
    }

    private static BranchProof emptyBranch(String method, String blocker) {
        return new BranchProof(
                method,
                HANDLER_DESC,
                null,
                false,
                false,
                false,
                false,
                null,
                false,
                0,
                false,
                List.of(blocker));
    }

    private static SwitchCase uniqueSwitchCase(MethodNode method, int guiId) {
        SwitchCase found = null;
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

            LabelNode target = null;
            LabelNode defaultLabel;
            List<LabelNode> labels = new ArrayList<>();
            int otherCases = 0;
            String kind;
            if (instruction instanceof TableSwitchInsnNode table) {
                if (guiId < table.min || guiId > table.max) continue;
                target = table.labels.get(guiId - table.min);
                defaultLabel = table.dflt;
                labels.addAll(table.labels);
                otherCases = table.labels.size() - 1;
                kind = "TABLE";
            } else {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) instruction;
                int index = lookup.keys.indexOf(guiId);
                if (index < 0) continue;
                target = lookup.labels.get(index);
                defaultLabel = lookup.dflt;
                labels.addAll(lookup.labels);
                otherCases = lookup.labels.size() - 1;
                kind = "LOOKUP";
            }

            int targetUses = 0;
            for (LabelNode label : labels) if (label == target) targetUses++;
            Set<LabelNode> boundaries =
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            boundaries.add(defaultLabel);
            boundaries.addAll(labels);
            SwitchCase candidate = new SwitchCase(
                    instruction,
                    target,
                    defaultLabel,
                    boundaries,
                    otherCases,
                    kind,
                    targetUses == 1 && target != defaultLabel);
            if (found != null) return null;
            found = candidate;
        }
        return found;
    }

    private static List<AbstractInsnNode> branchInstructions(
            MethodNode method, SwitchCase shape) {
        List<AbstractInsnNode> result = new ArrayList<>();
        AbstractInsnNode cursor = shape.target().getNext();
        while (cursor != null) {
            if (cursor instanceof LabelNode label
                    && shape.boundaries().contains(label)
                    && label != shape.target()) {
                break;
            }
            if (!(cursor instanceof LabelNode)
                    && !(cursor instanceof LineNumberNode)
                    && !(cursor instanceof FrameNode)) {
                result.add(cursor);
                if (cursor.getOpcode() == Opcodes.ARETURN) break;
            }
            cursor = cursor.getNext();
        }
        return result;
    }

    private boolean implementsType(String source, String target) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            if (target.equals(current)) return true;
            ClassNode node = classes.get(current);
            if (node == null) continue;
            if (node.superName != null) queue.add(node.superName);
            queue.addAll(node.interfaces);
        }
        return false;
    }

    private boolean inherits(String source, String target) {
        String current = source;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (target.equals(current)) return true;
            ClassNode node = classes.get(current);
            current = node == null ? null : node.superName;
        }
        return false;
    }

    private static MethodNode ownMethod(
            ClassNode owner, String name, String descriptor) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode node) {
        while (node instanceof LabelNode
                || node instanceof LineNumberNode
                || node instanceof FrameNode) {
            node = node.getPrevious();
        }
        return node;
    }

    private void load(Path sourceJar) throws IOException {
        try (JarFile jar = new JarFile(sourceJar.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(
                            node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add(
                            "Unreadable processor GUI-handler class "
                                    + entry.getName() + ": "
                                    + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
