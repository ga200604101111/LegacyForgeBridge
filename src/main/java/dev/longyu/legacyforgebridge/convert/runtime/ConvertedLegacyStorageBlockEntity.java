package dev.longyu.legacyforgebridge.convert.runtime;

import dev.longyu.legacyforgebridge.compat.LegacyStorageBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Modern six-row storage carrier for a source-proven 1.7 BlockContainer/IInventory rule. */
public final class ConvertedLegacyStorageBlockEntity extends BaseContainerBlockEntity {
    private final LegacyStorageBlockRegistry.Rule rule;
    private NonNullList<ItemStack> items;

    public ConvertedLegacyStorageBlockEntity(BlockPos pos, BlockState state) {
        super(LegacyStorageBlockRegistry.requireType(state.getBlock()), pos, state);
        this.rule = LegacyStorageBlockRegistry.requireRule(state.getBlock());
        this.items = NonNullList.withSize(rule.slots(), ItemStack.EMPTY);
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal(rule.title());
    }

    @Override
    public int getContainerSize() {
        return rule.slots();
    }

    @Override
    public int getMaxStackSize() {
        return rule.stackLimit();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        NonNullList<ItemStack> normalized = NonNullList.withSize(rule.slots(), ItemStack.EMPTY);
        int count = Math.min(items.size(), normalized.size());
        for (int slot = 0; slot < count; slot++) normalized.set(slot, items.get(slot));
        this.items = normalized;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5
        ) <= rule.interactionDistanceSq();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(rule.slots(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
        return ChestMenu.sixRows(syncId, playerInventory, this);
    }
}
