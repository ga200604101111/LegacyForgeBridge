package dev.longyu.legacyforgebridge.convert.runtime;

import dev.longyu.legacyforgebridge.compat.LegacyBlockPlacementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Neutral modern Block carrier for Minecraft 1.7.x raw metadata.
 *
 * <p>The property deliberately remains an opaque 0..15 value. Direction, growth stage, powered
 * state and other meanings are projected onto semantic modern properties only when a later source
 * behavior compiler can prove those meanings. Keeping the raw value first prevents unrelated old
 * metadata layouts from being conflated.</p>
 */
public final class ConvertedLegacyBlock extends Block {
    public static final IntegerProperty LEGACY_META = IntegerProperty.create("legacy_meta", 0, 15);
    private final Identifier convertedId;

    public ConvertedLegacyBlock(Identifier convertedId, BlockBehaviour.Properties properties) {
        super(properties);
        this.convertedId = convertedId;
        registerDefaultState(stateDefinition.any().setValue(LEGACY_META, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEGACY_META);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState base = super.getStateForPlacement(context);
        if (base == null) return null;
        Integer legacyMeta = LegacyBlockPlacementRegistry.placementMeta(convertedId, context);
        return legacyMeta == null ? base : withLegacyMeta(base, legacyMeta);
    }

    public static int legacyMeta(BlockState state) {
        if (state == null || !state.hasProperty(LEGACY_META)) {
            throw new IllegalArgumentException("BlockState does not carry legacy metadata");
        }
        return state.getValue(LEGACY_META);
    }

    public static BlockState withLegacyMeta(BlockState state, int meta) {
        if (state == null || !state.hasProperty(LEGACY_META)) {
            throw new IllegalArgumentException("BlockState does not carry legacy metadata");
        }
        return state.setValue(LEGACY_META, validateLegacyMeta(meta));
    }

    /** Pure 1.7 metadata-domain validation kept independent from the frozen modern block registry. */
    static int validateLegacyMeta(int meta) {
        if (meta < 0 || meta > 15) {
            throw new IllegalArgumentException("Legacy block metadata outside 1.7 range: " + meta);
        }
        return meta;
    }
}
