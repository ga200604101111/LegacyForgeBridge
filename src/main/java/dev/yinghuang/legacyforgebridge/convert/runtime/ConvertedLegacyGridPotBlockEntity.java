package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyGridPotBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Persistent nine-cell state carrier for the proof-complete core of a legacy grid-pot block. */
public final class ConvertedLegacyGridPotBlockEntity extends BlockEntity {
    private static final String ENABLED_MASK = "lfb_grid_enabled";
    private final LegacyGridPotBlockRegistry.Rule rule;
    private NonNullList<ItemStack> items;
    private int enabledMask;

    public ConvertedLegacyGridPotBlockEntity(BlockPos pos, BlockState state) {
        super(LegacyGridPotBlockRegistry.requireType(state.getBlock()), pos, state);
        this.rule = LegacyGridPotBlockRegistry.requireRule(state.getBlock());
        this.items = NonNullList.withSize(rule.cells(), ItemStack.EMPTY);
    }

    public int cells() {
        return rule.cells();
    }

    public boolean isEnabled(int slot) {
        validateSlot(slot);
        return (enabledMask & (1 << slot)) != 0;
    }

    public boolean isGridEmpty() {
        return enabledMask == 0;
    }

    public ItemStack item(int slot) {
        validateSlot(slot);
        return items.get(slot);
    }

    public void enable(int slot) {
        validateSlot(slot);
        if (isEnabled(slot)) return;
        enabledMask |= 1 << slot;
        changedAndNotify();
    }

    /** Removes only the stored item, retaining the pot cell itself. */
    public ItemStack removeItem(int slot) {
        validateSlot(slot);
        if (!isEnabled(slot)) return ItemStack.EMPTY;
        ItemStack removed = items.get(slot);
        if (removed.isEmpty()) return ItemStack.EMPTY;
        items.set(slot, ItemStack.EMPTY);
        changedAndNotify();
        return removed;
    }

    /** Removes a whole cell and any item it currently contains. */
    public ItemStack removeCell(int slot) {
        validateSlot(slot);
        if (!isEnabled(slot)) return ItemStack.EMPTY;
        ItemStack removed = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        enabledMask &= ~(1 << slot);
        changedAndNotify();
        return removed;
    }

    /** Reserved for the later source-render-type insertion gate. */
    public void setItem(int slot, ItemStack stack) {
        validateSlot(slot);
        if (!isEnabled(slot)) throw new IllegalStateException("Cannot populate a disabled grid-pot cell");
        items.set(slot, stack == null ? ItemStack.EMPTY : stack.copyWithCount(Math.min(1, stack.getCount())));
        changedAndNotify();
    }

    public void dropAll(Level level, BlockPos pos, ItemStack cellItem) {
        for (int slot = 0; slot < rule.cells(); slot++) {
            if (!isEnabled(slot)) continue;
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), cellItem.copy());
            ItemStack content = items.get(slot);
            if (!content.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), content);
            }
        }
        enabledMask = 0;
        setChanged();
    }


    @Override
    public Packet getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        enabledMask = input.getIntOr(ENABLED_MASK, 0) & ((1 << rule.cells()) - 1);
        items = NonNullList.withSize(rule.cells(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int slot = 0; slot < rule.cells(); slot++) {
            if (!isEnabled(slot)) items.set(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(ENABLED_MASK, enabledMask);
        ContainerHelper.saveAllItems(output, items);
    }

    private void changedAndNotify() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    private void validateSlot(int slot) {
        if (slot < 0 || slot >= rule.cells()) throw new IndexOutOfBoundsException("grid-pot slot " + slot);
    }
}
