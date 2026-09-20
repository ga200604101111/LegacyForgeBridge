package dev.yinghuang.legacyforgebridge.mixin.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the exact 1.21.11 render boundaries while keeping Via classes outside both mixins. */
class LegacyBlockingPoseMixinWiringTest {
    private static ClassNode read(String name) throws Exception {
        try (InputStream input=LegacyBlockingPoseMixinWiringTest.class.getClassLoader().getResourceAsStream(name+".class")) {
            assertNotNull(input,"Missing runtime class "+name);
            ClassNode node=new ClassNode();new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return node;
        }
    }
    private static void method(String owner,String name,String descriptor) throws Exception {
        assertTrue(read(owner).methods.stream().anyMatch(method->method.name.equals(name)&&method.desc.equals(descriptor)),
                owner+" lacks "+name+descriptor);
    }
    private static byte[] bytes(String name) throws Exception {
        try(InputStream input=LegacyBlockingPoseMixinWiringTest.class.getClassLoader().getResourceAsStream(name+".class")){
            assertNotNull(input);return input.readAllBytes();
        }
    }

    @Test void exactRenderSubmissionBoundariesExist() throws Exception {
        method("net/minecraft/client/renderer/ItemInHandRenderer","renderArmWithItem",
                "(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V");
        method("net/minecraft/client/renderer/ItemInHandRenderer","renderItem",
                "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V");
        method("net/minecraft/client/renderer/entity/layers/ItemInHandLayer","submitArmWithItem",
                "(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V");
        method("net/minecraft/client/renderer/item/ItemStackRenderState","submit",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V");
    }

    @Test void mixinsAreVisualOnlyAndInstalledOnce() throws Exception {
        for(String name:new String[]{"LegacyFirstPersonBlockingPoseMixin","LegacyThirdPersonBlockingPoseMixin"}){
            byte[] bytes=bytes("dev/yinghuang/legacyforgebridge/mixin/client/"+name);
            String pool=new String(bytes,StandardCharsets.ISO_8859_1);
            assertTrue(pool.contains("LegacyBlockingPoseConfig"));
            assertTrue(pool.contains("BLOCKS_ATTACKS"));
            assertFalse(pool.contains("com/viaversion"),"Visual correction must not patch Via internals");
            assertFalse(pool.contains("startUsingItem"));
            assertFalse(pool.contains("stopUsingItem"));
        }
        try(InputStream input=getClass().getClassLoader().getResourceAsStream("legacyforgebridge.client.mixins.json")){
            assertNotNull(input);
            var root=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for(String name:new String[]{"LegacyFirstPersonBlockingPoseMixin","LegacyThirdPersonBlockingPoseMixin"}){
                long count=root.getAsJsonArray("client").asList().stream().filter(v->v.getAsString().equals(name)).count();
                assertEquals(1,count);
            }
        }
    }
}
