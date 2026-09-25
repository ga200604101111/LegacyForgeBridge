package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes one exact side-effect-free legacy RenderingRegistry entity-renderer registration.
 *
 * <p>The caller must already have proven the renderer callback is a no-op and its source no-arg
 * constructor chain is side-effect-free. This transformer then accepts only the canonical five-opcode
 * expression: class literal, NEW renderer, DUP, renderer.<init>()V, registration call.</p>
 */
public final class LegacyEntityRendererRegistrationStripper {
    private static final String RENDERING_REGISTRY = "cpw/mods/fml/client/registry/RenderingRegistry";
    private static final String REGISTER = "registerEntityRenderingHandler";
    private static final String REGISTER_DESC = "(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V";

    public record Target(String sourceMethod, String sourceDescriptor,
                         String entityClass, String rendererClass) { }
    public record Result(byte[] bytes, int strippedSites, List<String> blockers) {
        public Result { blockers = List.copyOf(blockers); }
    }

    public Result strip(byte[] sourceClass, Target target) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(sourceClass).accept(node, 0);
        List<MethodInsnNode> matches = new ArrayList<>();
        List<AbstractInsnNode[]> slices = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (!method.name.equals(target.sourceMethod()) || !method.desc.equals(target.sourceDescriptor())) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !RENDERING_REGISTRY.equals(call.owner)
                        || !REGISTER.equals(call.name)
                        || !REGISTER_DESC.equals(call.desc)) continue;
                AbstractInsnNode[] slice = exactSlice(call, target);
                if (slice != null) { matches.add(call); slices.add(slice); }
            }
        }
        if (matches.isEmpty()) return new Result(sourceClass, 0,
                List.of("no-exact-pure-renderer-registration-callsite"));
        if (matches.size() != 1) return new Result(sourceClass, 0,
                List.of("ambiguous-exact-renderer-registration-callsites:" + matches.size()));

        MethodInsnNode call = matches.getFirst();
        MethodNode ownerMethod = null;
        for (MethodNode method : node.methods) if (method.instructions.indexOf(call) >= 0) { ownerMethod = method; break; }
        if (ownerMethod == null) return new Result(sourceClass, 0, List.of("matched-callsite-owner-method-missing"));
        for (AbstractInsnNode instruction : slices.getFirst()) ownerMethod.instructions.remove(instruction);
        ownerMethod.instructions.remove(call);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return new Result(writer.toByteArray(), 1, List.of());
    }

    private static AbstractInsnNode[] exactSlice(MethodInsnNode call, Target target) {
        AbstractInsnNode ctor = previousOpcode(call);
        AbstractInsnNode dup = previousOpcode(ctor);
        AbstractInsnNode create = previousOpcode(dup);
        AbstractInsnNode entity = previousOpcode(create);
        if (ctor == null || dup == null || create == null || entity == null) return null;
        if (crossesBoundary(entity, create) || crossesBoundary(create, dup)
                || crossesBoundary(dup, ctor) || crossesBoundary(ctor, call)) return null;
        if (!(entity instanceof LdcInsnNode literal) || !(literal.cst instanceof Type type)
                || type.getSort() != Type.OBJECT || !target.entityClass().equals(type.getInternalName())) return null;
        if (!(create instanceof TypeInsnNode creation) || creation.getOpcode() != Opcodes.NEW
                || !target.rendererClass().equals(creation.desc)) return null;
        if (dup.getOpcode() != Opcodes.DUP) return null;
        if (!(ctor instanceof MethodInsnNode init) || init.getOpcode() != Opcodes.INVOKESPECIAL
                || !target.rendererClass().equals(init.owner) || !"<init>".equals(init.name)
                || !"()V".equals(init.desc)) return null;
        return new AbstractInsnNode[]{entity, create, dup, ctor};
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode from) {
        if (from == null) return null;
        AbstractInsnNode cursor = from.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) cursor = cursor.getPrevious();
        return cursor;
    }

    private static boolean crossesBoundary(AbstractInsnNode earlier, AbstractInsnNode later) {
        for (AbstractInsnNode cursor = earlier.getNext(); cursor != null && cursor != later; cursor = cursor.getNext())
            if (cursor instanceof LabelNode || cursor instanceof FrameNode) return true;
        return false;
    }
}
