package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes one exact residual source item allocation after registerItem has already been neutralized.
 *
 * <p>The admitted shape is deliberately narrow:
 * NEW sourceItem; DUP; invokespecial sourceItem.<init>()V; LDC registryName; POP; POP.
 * The constructor must already have an independently proven generated replacement. This helper
 * does not make that decision; callers supply only proof-complete targets.</p>
 */
public final class LegacyItemAllocationResidueStripper {
    public record Target(String sourceMethod, String sourceDescriptor,
                         String sourceItemClass, String constructorDescriptor,
                         String registryName) { }
    public record Result(byte[] bytes, int strippedSites, List<String> blockers) {
        public Result { blockers = List.copyOf(blockers); }
    }

    public Result strip(byte[] sourceClass, Target target) {
        if (!"()V".equals(target.constructorDescriptor())) {
            return new Result(sourceClass, 0,
                    List.of("source-allocation-constructor-args-not-supported"));
        }

        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(sourceClass).accept(
                node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        List<Match> matches = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (!method.name.equals(target.sourceMethod())
                    || !method.desc.equals(target.sourceDescriptor())) continue;

            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode constructor)
                        || constructor.getOpcode() != Opcodes.INVOKESPECIAL
                        || !"<init>".equals(constructor.name)
                        || !target.sourceItemClass().equals(constructor.owner)
                        || !"()V".equals(constructor.desc)) continue;

                AbstractInsnNode dup = previousMeaningful(constructor);
                AbstractInsnNode allocation = previousMeaningful(dup);
                AbstractInsnNode name = nextMeaningful(constructor);
                AbstractInsnNode popName = nextMeaningful(name);
                AbstractInsnNode popItem = nextMeaningful(popName);

                if (!(allocation instanceof TypeInsnNode type)
                        || type.getOpcode() != Opcodes.NEW
                        || !target.sourceItemClass().equals(type.desc)) continue;
                if (!(dup instanceof InsnNode) || dup.getOpcode() != Opcodes.DUP) continue;
                if (!(name instanceof LdcInsnNode ldc)
                        || !(ldc.cst instanceof String text)
                        || !target.registryName().equals(text)) continue;
                if (!(popName instanceof InsnNode) || popName.getOpcode() != Opcodes.POP) continue;
                if (!(popItem instanceof InsnNode) || popItem.getOpcode() != Opcodes.POP) continue;
                if (!contiguousMeaningful(allocation, dup, constructor, name, popName, popItem)) continue;

                matches.add(new Match(method, allocation, popItem));
            }
        }

        if (matches.isEmpty()) {
            return new Result(sourceClass, 0,
                    List.of("no-exact-neutralized-inline-item-allocation-residue"));
        }
        if (matches.size() != 1) {
            return new Result(sourceClass, 0,
                    List.of("ambiguous-neutralized-inline-item-allocation-residues:"
                            + matches.size()));
        }

        Match match = matches.getFirst();
        AbstractInsnNode after = match.last().getNext();
        AbstractInsnNode cursor = match.first();
        while (cursor != after) {
            if (cursor == null) {
                return new Result(sourceClass, 0,
                        List.of("matched-item-allocation-residue-not-contiguous"));
            }
            AbstractInsnNode next = cursor.getNext();
            match.method().instructions.remove(cursor);
            cursor = next;
        }

        if (methodReferences(match.method(), target.sourceItemClass())) {
            return new Result(sourceClass, 0,
                    List.of("source-item-reference-remains-in-allocation-owner-method"));
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return new Result(writer.toByteArray(), 1, List.of());
    }

    private record Match(MethodNode method, AbstractInsnNode first, AbstractInsnNode last) { }

    private static boolean contiguousMeaningful(AbstractInsnNode... expected) {
        AbstractInsnNode cursor = expected[0];
        for (int index = 1; index < expected.length; index++) {
            cursor = nextMeaningful(cursor);
            if (cursor != expected[index]) return false;
        }
        return true;
    }

    private static boolean methodReferences(MethodNode method, String internalName) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && internalName.equals(type.desc)) return true;
            if (instruction instanceof MethodInsnNode call
                    && internalName.equals(call.owner)) return true;
            if (instruction instanceof LdcInsnNode ldc
                    && ldc.cst instanceof org.objectweb.asm.Type type
                    && type.getSort() == org.objectweb.asm.Type.OBJECT
                    && internalName.equals(type.getInternalName())) return true;
        }
        return false;
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode node) {
        if (node == null) return null;
        AbstractInsnNode cursor = node.getPrevious();
        while (cursor instanceof LabelNode
                || cursor instanceof LineNumberNode
                || cursor instanceof FrameNode) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node) {
        if (node == null) return null;
        AbstractInsnNode cursor = node.getNext();
        while (cursor instanceof LabelNode
                || cursor instanceof LineNumberNode
                || cursor instanceof FrameNode) {
            cursor = cursor.getNext();
        }
        return cursor;
    }
}
