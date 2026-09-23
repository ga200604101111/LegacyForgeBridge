package dev.yinghuang.legacyforgebridge.render;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConvertedLegacyVanillaArrowRendererBytecodeTest {
    private static final String RENDERER =
            "dev/yinghuang/legacyforgebridge/render/ConvertedLegacyOrientedProjectileRenderer";
    private static final String VANILLA = "net/minecraft/client/renderer/entity/ArrowRenderer";
    private static final String STATE = "net/minecraft/client/renderer/entity/state/ArrowRenderState";

    @Test
    void submissionDelegatesToVanillaAndNeverRendersAnItemOrReimplementsTheMesh() throws Exception {
        ClassNode wrapper = read(RENDERER);
        ClassNode delegate = read(RENDERER + "$VanillaArrowDelegate");
        assertEquals(VANILLA, delegate.superName);
        assertTrue(delegate.methods.stream().noneMatch(m -> m.name.equals("submit")),
                "The delegate must inherit the real vanilla submit implementation");
        var calls = calls(wrapper);
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("submit")
                && (c.owner.equals(VANILLA) || c.owner.equals(RENDERER + "$VanillaArrowDelegate"))));
        assertTrue(calls.stream().noneMatch(c -> c.owner.contains("ItemModelResolver")
                || c.owner.contains("ItemStackRenderState") || c.owner.equals("com/mojang/math/Axis")
                || c.name.equals("getDeltaMovement") || c.name.equals("atan2")
                || c.owner.contains("/ArrowModel")),
                "No inventory model, stale velocity override, or copied vanilla geometry/axis implementation");
        assertEquals(STATE, read(RENDERER + "$State").superName);
    }

    @Test
    void exactTargetVanillaRendererOwnsTheArrowModelAndSubmissionDescriptor() throws Exception {
        ClassNode vanilla = read(VANILLA);
        assertTrue(vanilla.fields.stream().anyMatch(f -> f.desc.equals("Lnet/minecraft/client/model/object/projectile/ArrowModel;")));
        assertTrue(vanilla.methods.stream().anyMatch(m -> m.name.equals("submit")
                && m.desc.equals("(L" + STATE + ";Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                + "Lnet/minecraft/client/renderer/state/CameraRenderState;)V")),
                "Pin the descriptor against the Minecraft version on the real test classpath");
    }

    @Test
    void throwableBillboardsRemainSeparateFromArrowRendering() throws Exception {
        ClassNode runtime = read("dev/yinghuang/legacyforgebridge/render/ConvertedProjectilePresentationRuntime");
        List<String> factories = new ArrayList<>();
        for (MethodNode method : runtime.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof InvokeDynamicInsnNode dynamic) for (Object argument : dynamic.bsmArgs) {
                if (argument instanceof org.objectweb.asm.Handle handle) factories.add(handle.getOwner());
            }
        }
        assertTrue(factories.contains(RENDERER));
        assertTrue(factories.contains("net/minecraft/client/renderer/entity/ThrownItemRenderer"));
    }

    @Test
    void spawnInitializesPreviousAnglesBeforeClientInsertion() throws Exception {
        ClassNode client = read("dev/yinghuang/legacyforgebridge/network/FmlRuntimeClient");
        MethodNode spawn = client.methods.stream().filter(m -> m.name.equals("applyRemoteProjectileSpawn"))
                .findFirst().orElseThrow();
        boolean yaw = false, pitch = false, inserted = false;
        for (AbstractInsnNode instruction : spawn.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                if (field.name.equals("yRotO") && field.desc.equals("F")) yaw = true;
                if (field.name.equals("xRotO") && field.desc.equals("F")) pitch = true;
            }
            if (instruction instanceof MethodInsnNode call && call.name.equals("addEntity")) {
                assertTrue(yaw && pitch, "Both initial angles must be set before the first frame");
                inserted = true;
            }
        }
        assertTrue(inserted);
    }

    @Test
    void remoteCarrierDoesNotRunVanillaArrowGameplay() throws Exception {
        ClassNode carrier = read("dev/yinghuang/legacyforgebridge/convert/runtime/ConvertedLegacyRemoteProjectile");
        assertEquals("net/minecraft/world/entity/Entity", carrier.superName);
        assertTrue(carrier.methods.stream().noneMatch(m -> m.name.equals("onHit")
                || m.name.equals("onHitEntity") || m.name.equals("onHitBlock")
                || m.name.equals("playerTouch")));
        for (ClassNode node : List.of(carrier, read(RENDERER), read(RENDERER + "$VanillaArrowDelegate"))) {
            for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                    assertFalse(type.desc.startsWith("net/minecraft/world/entity/projectile/"),
                            "Do not allocate a second gameplay projectile just to render an arrow");
                }
            }
        }
    }

    private static List<MethodInsnNode> calls(ClassNode node) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) result.add(call);
        }
        return result;
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = ConvertedLegacyVanillaArrowRendererBytecodeTest.class
                .getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
