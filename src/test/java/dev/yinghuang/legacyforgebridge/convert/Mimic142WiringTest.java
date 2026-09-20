package dev.yinghuang.legacyforgebridge.convert;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Resolve exact Minecraft/API boundaries from the CI classpath without initializing Minecraft. */
class Mimic142WiringTest {
    private static final String ROOT = "dev/yinghuang/legacyforgebridge/";
    private static final String POS = "Lnet/minecraft/core/BlockPos;";
    private static final String STATE = "Lnet/minecraft/world/level/block/state/BlockState;";
    private static final String NOTIFY = "(" + POS + STATE + STATE + "I)V";
    private static final String DIRTY = "(" + POS + STATE + STATE + ")V";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = Mimic142WiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, "Missing exact runtime class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    private static MethodNode method(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new AssertionError(type.name + " lacks " + name + desc));
    }
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }
    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }

    @Test void exactMinecraftTargetsAndRefreshMethodsExist() throws Exception {
        ClassNode level = read("net/minecraft/client/multiplayer/ClientLevel");
        assertEquals(0, method(level, "sendBlockUpdated", NOTIFY).access & Opcodes.ACC_STATIC);
        assertEquals(0, method(level, "setBlocksDirty", DIRTY).access & Opcodes.ACC_STATIC);
        ClassNode renderer = read("net/minecraft/client/renderer/LevelRenderer");
        assertNotEquals(0, method(renderer, "setSectionDirty", "(III)V").access & Opcodes.ACC_PUBLIC);
        assertNotEquals(0, method(renderer, "allChanged", "()V").access & Opcodes.ACC_PUBLIC);
        method(read("net/minecraft/client/renderer/chunk/RenderChunkRegion"), "getBlockState", "(" + POS + ")" + STATE);
    }

    @Test void bothMixinCallbacksAreNonCancellingTailObservers() throws Exception {
        ClassNode mixin = read(ROOT + "mixin/client/LegacyMimicBlockUpdateMixin");
        int injections = 0;
        for (MethodNode callback : mixin.methods) for (AnnotationNode annotation : annotations(callback)) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
            injections++;
            assertNotEquals(Boolean.TRUE, value(annotation, "cancellable"));
            @SuppressWarnings("unchecked") List<String> targets = (List<String>) value(annotation, "method");
            boolean notified = callback.name.endsWith("mimicNotified");
            assertEquals(List.of((notified ? "sendBlockUpdated" : "setBlocksDirty") + (notified ? NOTIFY : DIRTY)), targets);
            assertEquals("(" + POS + STATE + STATE + (notified ? "I" : "") + CALLBACK + ")V", callback.desc);
            @SuppressWarnings("unchecked") List<AnnotationNode> at = (List<AnnotationNode>) value(annotation, "at");
            assertEquals(1, at.size()); assertEquals("TAIL", value(at.getFirst(), "value"));
            int calls = 0;
            for (var instruction : callback.instructions) if (instruction instanceof MethodInsnNode call) {
                calls++;
                assertEquals(ROOT + "render/LegacyMimicInvalidationClient", call.owner);
                assertEquals("blockChanged", call.name);
            }
            assertEquals(1, calls, "Observer must not send packets, cancel, or mutate the world");
        }
        assertEquals(2, injections);
    }

    @Test void renderingUsesGuardedTraversalWithoutLiveLevelFallback() throws Exception {
        ClassNode renderer = read(ROOT + "render/ConvertedGeometryClient");
        boolean guarded = false, snapshotWindow = false, localWindow = false;
        for (MethodNode method : renderer.methods) {
            if (!method.name.equals("referenced") && !method.name.startsWith("lambda$referenced$")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(ROOT + "compat/LegacyMimicResolver") && call.name.equals("resolve")) {
                        assertTrue(call.desc.contains("Ljava/util/function/Predicate;")); guarded = true;
                    }
                    if (call.owner.equals(ROOT + "compat/LegacyMimicReadWindow")) {
                        snapshotWindow |= call.name.equals("sectionSnapshot");
                        localWindow |= call.name.equals("immediateNeighbors");
                    }
                }
                if (instruction instanceof FieldInsnNode field) {
                    assertFalse(field.owner.equals("net/minecraft/client/Minecraft") && field.name.equals("level"),
                            "Do not escape the render snapshot through the live client world");
                }
            }
        }
        assertTrue(guarded && snapshotWindow && localWindow);
    }

    @Test void mixinIsInstalledAndVanillaRequirePolicyRemains() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("legacyforgebridge.client.mixins.json")) {
            assertNotNull(input);
            var root = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(root.get("required").getAsBoolean());
            assertEquals(1, root.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            long count = root.getAsJsonArray("client").asList().stream()
                    .filter(item -> item.getAsString().equals("LegacyMimicBlockUpdateMixin")).count();
            assertEquals(1, count);
        }
    }
}
