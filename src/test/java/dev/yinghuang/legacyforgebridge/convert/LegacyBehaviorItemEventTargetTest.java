package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorItemEventTargetTest {
    @TempDir Path tempDir;

    @Test void selfRegisteredItemKeepsConstructorStateAndIsNotConstructedAgainForItsEvent() throws Exception {
        Path jar = tempDir.resolve("unrelated-item-event.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("foreign/item/SelfItem.class"));
            out.write(sourceItem());
            out.closeEntry();
        }

        var allocation = new LegacyItemRenderAnalyzer.ItemAllocation(
                "self", "foreign/item/SelfItem", "()V", List.of(), false, false, false);
        var result = new LegacyBehaviorCompiler().compile(
                jar,
                "fixture",
                "generated/ItemTargetBootstrap",
                Map.of("self", "fixture:self"),
                List.of(allocation));

        assertEquals(1, result.items().size(), String.join("\n", result.diagnostics()));
        assertEquals(1, result.events().size(), String.join("\n", result.diagnostics()));
        assertEquals("jump", result.events().getFirst().kind());
        assertEquals("fixture:self", result.events().getFirst().targetItemId());

        String generatedItemPath = "generated/ItemTargetBootstrapSource/foreign/item/SelfItem.class";
        byte[] generatedItem = result.classes().get(generatedItemPath);
        assertNotNull(generatedItem, result.classes().keySet().toString());
        String itemPool = new String(generatedItem, StandardCharsets.ISO_8859_1);
        assertFalse(itemPool.contains("MinecraftForge"), "generated item retained legacy Forge owner");
        assertFalse(itemPool.contains("EventBus"), "generated item retained legacy event bus call");
        assertTrue(itemPool.contains("func_77625_d"), "constructor initialization was dropped with self-registration");

        byte[] bootstrap = result.classes().get("generated/ItemTargetBootstrap.class");
        assertNotNull(bootstrap);
        String mappedItem = "generated/ItemTargetBootstrapSource/foreign/item/SelfItem";
        int[] itemAllocations = {0};
        int[] pendingLookups = {0};
        new ClassReader(bootstrap).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                if (!name.equals("initialize")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW && type.equals(mappedItem)) itemAllocations[0]++;
                    }

                    @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                          String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && owner.equals(LegacyBehaviorCompiler.REG)
                                && name.equals("bootstrapItem")
                                && descriptor.equals("(Ljava/lang/String;)L" + LegacyBehaviorCompiler.REG
                                + "$ItemDefinition;")) {
                            pendingLookups[0]++;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(1, itemAllocations[0], "event target constructed a second source item instance");
        assertEquals(1, pendingLookups[0], "event adapter did not bind through the pending item transaction");
    }

    private static byte[] sourceItem() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "foreign/item/SelfItem", null, "net/minecraft/item/Item", null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/Item", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitIntInsn(Opcodes.BIPUSH, 1);
        constructor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "foreign/item/SelfItem", "func_77625_d",
                "(I)Lnet/minecraft/item/Item;", false);
        constructor.visitInsn(Opcodes.POP);
        constructor.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lcpw/mods/fml/common/eventhandler/EventBus;");
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus", "register",
                "(Ljava/lang/Object;)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(2, 1);
        constructor.visitEnd();

        MethodVisitor jump = writer.visitMethod(Opcodes.ACC_PUBLIC, "onJump",
                "(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingJumpEvent;)V", null, null);
        AnnotationVisitor subscribe = jump.visitAnnotation(
                "Lcpw/mods/fml/common/eventhandler/SubscribeEvent;", true);
        subscribe.visitEnd();
        jump.visitCode();
        jump.visitInsn(Opcodes.RETURN);
        jump.visitMaxs(0, 2);
        jump.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
