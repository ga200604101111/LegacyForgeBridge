package dev.yinghuang.legacyforgebridge.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yinghuang.legacyforgebridge.config.LegacyBlockingPoseConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ShieldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third-person companion to the first-person visual-only BLOCK-pose correction. */
@Mixin(ItemInHandLayer.class)
public abstract class LegacyThirdPersonBlockingPoseMixin {
    @Inject(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void legacyforgebridge$adjustBlockingPose(
            ArmedEntityRenderState state,
            ItemStackRenderState itemState,
            ItemStack stack,
            HumanoidArm arm,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!(state instanceof HumanoidRenderState humanoid) || !activeWeaponBlock(humanoid, state, arm, stack)) return;
        LegacyBlockingPoseConfig.applyThirdPerson(matrices, arm == HumanoidArm.LEFT);
    }

    private static boolean activeWeaponBlock(
            HumanoidRenderState humanoid,
            ArmedEntityRenderState state,
            HumanoidArm renderedArm,
            ItemStack stack
    ) {
        if (!LegacyBlockingPoseConfig.enabled()
                || !humanoid.isUsingItem
                || stack.isEmpty()
                || stack.getItem() instanceof ShieldItem
                || stack.getUseAnimation() != ItemUseAnimation.BLOCK) return false;
        HumanoidArm activeArm = humanoid.useItemHand == InteractionHand.MAIN_HAND
                ? state.mainArm : state.mainArm.getOpposite();
        return renderedArm == activeArm;
    }
}
