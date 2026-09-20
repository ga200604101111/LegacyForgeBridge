package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyBlockActivationEffectsRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockActivationRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockDropRuntimeRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockPlacementRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockGeometryRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Neutral modern Block carrier for Minecraft 1.7.x raw metadata and proven source behavior.
 *
 * <p>The metadata property deliberately remains an opaque 0..15 value. Direction, growth stage,
 * powered state and other meanings are projected onto semantic modern properties only when a
 * source behavior compiler can prove those meanings. Keeping the raw value first prevents
 * unrelated old metadata layouts from being conflated.</p>
 */
public class ConvertedLegacyBlock extends Block {
    public static final IntegerProperty LEGACY_META = IntegerProperty.create("legacy_meta", 0, 15);
    private final Identifier convertedId;

    public ConvertedLegacyBlock(Identifier convertedId, BlockBehaviour.Properties properties) {
        super(LegacyBlockGeometryRegistry.properties(convertedId, properties));
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

    /**
     * Proof-gated normal drop runtime. Admitted rules are count=1 self BlockItems whose exact
     * legacy item damage is either the metadata-independent zero value or a source-proven 16-entry
     * block-metadata lookup table. Metadata-dependent rules are admitted only when legacy silk
     * touch is proven disabled.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        LegacyBlockDropRuntimeRegistry.Rule rule = LegacyBlockDropRuntimeRegistry.rule(convertedId);
        if (rule == null) {
            return super.getDrops(state, params);
        }
        return List.of(legacyDropStack(state, rule));
    }

    /**
     * Replays the proven Forge 1.7.10 per-affected-block explosion path without inheriting modern
     * DESTROY versus DESTROY_WITH_DECAY loot semantics. KEEP/TRIGGER_BLOCK remain modern because
     * they are not destructive counterparts of the admitted legacy path.
     */
    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                  BiConsumer<ItemStack, BlockPos> dropConsumer) {
        LegacyBlockDropRuntimeRegistry.Rule rule = LegacyBlockDropRuntimeRegistry.rule(convertedId);
        if (rule == null
                || (explosion.getBlockInteraction() != Explosion.BlockInteraction.DESTROY
                && explosion.getBlockInteraction() != Explosion.BlockInteraction.DESTROY_WITH_DECAY)) {
            super.onExplosionHit(state, level, pos, explosion, dropConsumer);
            return;
        }

        if (LegacyBlockDropRuntimeRegistry.shouldDropFromExplosion(
                convertedId, level.getRandom().nextFloat(), explosion.radius())) {
            dropConsumer.accept(legacyDropStack(state, rule), pos);
        }

        // Forge/Minecraft 1.7.10 Block#onBlockExploded removes the block with update flags 3.
        // Source destruction callbacks are proven absent before a rule can reach this runtime.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private ItemStack legacyDropStack(BlockState state, LegacyBlockDropRuntimeRegistry.Rule rule) {
        ItemStack stack = new ItemStack(this);
        int legacyDamage = rule.legacyDamage(legacyMeta(state));
        if (legacyDamage != 0) {
            LegacyStackComponents.set(stack, legacyDamage);
        }
        return stack;
    }

    /**
     * Minecraft 1.21.11 routes the default item-on-block path to this hook for the main hand. That
     * matches the single-hand Minecraft 1.7 activation model without intercepting modern item use
     * or inventing an off-hand callback that never existed in the source game.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        InteractionResult effect = LegacyBlockActivationEffectsRegistry.activate(
                convertedId, state, level, pos, player, hitResult);
        if (effect != null) return effect;
        Boolean handled = LegacyBlockActivationRegistry.handled(
                convertedId, legacyMeta(state), level.isClientSide(), player.isShiftKeyDown(), hitResult);
        if (handled == null) return super.useWithoutItem(state, level, pos, player, hitResult);
        return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Client mixins may attach source-proven random-display presentation without affecting server behavior. */
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random){super.animateTick(state,level,pos,random);}

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        VoxelShape shape = LegacyBlockGeometryRegistry.shape(convertedId, state, world, pos, false);
        return shape == null ? super.getShape(state, world, pos, context) : shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        VoxelShape shape = LegacyBlockGeometryRegistry.shape(convertedId, state, world, pos, true);
        return shape == null ? super.getCollisionShape(state, world, pos, context) : shape;
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
