package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.*;
import java.nio.file.Files;
import java.util.Map;

/** Retarget only a proven LDC -> StatCollector.translateToLocal(String) call, never arbitrary strings. */
public final class LegacyTranslationReferencePass implements ConversionPass {
    @Override public String id() { return "legacy-translation-direct-references"; }

    @Override public void apply(ConversionContext context) throws Exception {
        Map<String, String> aliases = context.registryIdentities().getOrDefault("translations", Map.of());
        if (aliases.isEmpty()) return;
        int rewritten = 0;
        try (var files = Files.walk(context.stagingDir())) {
            for (var path : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                try {
                    Rewrite result = rewrite(Files.readAllBytes(path), aliases);
                    if (result.calls() > 0) Files.write(path, result.bytecode());
                    rewritten += result.calls();
                } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                    context.diagnostics().warning("LFB-CONVERT-TRANSLATION-0002", SupportLevel.MANUAL_REQUIRED,
                            "Could not inspect translation calls in " + context.stagingDir().relativize(path));
                }
            }
        }
        context.diagnostics().info("LFB-CONVERT-TRANSLATION-0001", SupportLevel.ADAPTED,
                "Retargeted " + rewritten + " proven direct StatCollector translation calls to collision-free aliases. "
                        + "Dynamic/formatted keys, API migration and rendering still require their own validation.");
    }

    public record Rewrite(byte[] bytecode, int calls) { }

    public static Rewrite rewrite(byte[] original, Map<String, String> aliases) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        int[] changed = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new DirectCallVisitor(super.visitMethod(access, name, descriptor, signature, exceptions), aliases, changed);
            }
        }, 0);
        return new Rewrite(changed[0] == 0 ? original : writer.toByteArray(), changed[0]);
    }

    private static final class DirectCallVisitor extends MethodVisitor {
        private final Map<String, String> aliases;
        private final int[] changed;
        private String pending;
        DirectCallVisitor(MethodVisitor target, Map<String, String> aliases, int[] changed) {
            super(Opcodes.ASM9, target); this.aliases = aliases; this.changed = changed;
        }
        private void flush() { if (pending != null) { super.visitLdcInsn(pending); pending = null; } }
        @Override public void visitLdcInsn(Object value) {
            flush();
            if (value instanceof String text) pending = text;
            else super.visitLdcInsn(value);
        }
        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (pending != null && opcode == Opcodes.INVOKESTATIC && !isInterface
                    && owner.equals("net/minecraft/util/StatCollector")
                    && (name.equals("translateToLocal") || name.equals("func_74838_a"))
                    && descriptor.equals("(Ljava/lang/String;)Ljava/lang/String;") && aliases.containsKey(pending)) {
                pending = aliases.get(pending);
                changed[0]++;
            }
            flush(); super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
        // Every other instruction/control-flow boundary ends the proof of direct literal use.
        @Override public void visitInsn(int opcode) { flush(); super.visitInsn(opcode); }
        @Override public void visitIntInsn(int opcode, int operand) { flush(); super.visitIntInsn(opcode, operand); }
        @Override public void visitVarInsn(int opcode, int index) { flush(); super.visitVarInsn(opcode, index); }
        @Override public void visitTypeInsn(int opcode, String type) { flush(); super.visitTypeInsn(opcode, type); }
        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) { flush(); super.visitFieldInsn(opcode, owner, name, descriptor); }
        @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) { flush(); super.visitInvokeDynamicInsn(name, descriptor, bootstrap, arguments); }
        @Override public void visitJumpInsn(int opcode, Label target) { flush(); super.visitJumpInsn(opcode, target); }
        @Override public void visitLabel(Label label) { flush(); super.visitLabel(label); }
        @Override public void visitIincInsn(int index, int increment) { flush(); super.visitIincInsn(index, increment); }
        @Override public void visitTableSwitchInsn(int min, int max, Label fallback, Label... labels) { flush(); super.visitTableSwitchInsn(min, max, fallback, labels); }
        @Override public void visitLookupSwitchInsn(Label fallback, int[] keys, Label[] labels) { flush(); super.visitLookupSwitchInsn(fallback, keys, labels); }
        @Override public void visitMultiANewArrayInsn(String descriptor, int dimensions) { flush(); super.visitMultiANewArrayInsn(descriptor, dimensions); }
        @Override public void visitFrame(int type, int locals, Object[] local, int stackCount, Object[] stack) { flush(); super.visitFrame(type, locals, local, stackCount, stack); }
        @Override public void visitLineNumber(int line, Label start) { flush(); super.visitLineNumber(line, start); }
        @Override public AnnotationVisitor visitInsnAnnotation(int ref, TypePath path, String descriptor, boolean visible) { flush(); return super.visitInsnAnnotation(ref, path, descriptor, visible); }
        @Override public void visitMaxs(int stack, int locals) { flush(); super.visitMaxs(stack, locals); }
        @Override public void visitEnd() { flush(); super.visitEnd(); }
    }
}
