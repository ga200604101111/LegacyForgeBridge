package dev.yinghuang.legacyforgebridge.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.config.LegacyBlockingPoseConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a user-configured visual correction after Via/vanilla has established the block pose. */
@Mixin(ItemInHandRenderer.class)
public abstract class LegacyFirstPersonBlockingPoseMixin {
    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void legacyforgebridge$adjustConvertedBlockingPose(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!activeConvertedBlock(player, hand, stack)) return;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        LegacyBlockingPoseConfig.applyFirstPerson(matrices, arm == HumanoidArm.LEFT);
    }

    private static boolean activeConvertedBlock(AbstractClientPlayer player, InteractionHand hand, ItemStack stack) {
        return LegacyBlockingPoseConfig.enabled()
                && player.isUsingItem()
                && player.getUsedItemHand() == hand
                && !stack.isEmpty()
                && stack.get(LegacyStackComponents.legacyMeta()) != null
                && stack.get(DataComponents.BLOCKS_ATTACKS) != null;
    }
}
