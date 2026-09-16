package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock.LEGACY_META;

/** Source-proven two-part bed topology retaining the original 1.7 raw metadata in parallel. */
public final class ConvertedLegacySeatBedBlock extends BedBlock {
    private final Identifier convertedId;
    private final LegacySeatBedRegistry.Rule rule;

    public ConvertedLegacySeatBedBlock(Identifier convertedId, BlockBehaviour.Properties properties) {
        // Color is not used by this specialized BlockEntity/runtime; source TESR presentation remains separately gated.
        super(DyeColor.WHITE, properties);
        this.convertedId = convertedId;
        this.rule = LegacySeatBedRegistry.blockRule(convertedId);
        if (rule == null) throw new IllegalArgumentException("Missing seat-bed rule for " + convertedId);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.SOUTH)
                .setValue(PART, BedPart.FOOT)
                .setValue(OCCUPIED, false)
                .setValue(LEGACY_META, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEGACY_META);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos foot = context.getClickedPos();
        BlockPos head = foot.relative(facing);
        if (!context.getLevel().getBlockState(head).canBeReplaced(context)
                || !context.getLevel().getWorldBorder().isWithinBounds(head)) return null;
        return stateForLegacyHalf(facing, false, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        // Dedicated converted placement items place both halves themselves. This keeps the otherwise-generated
        // same-id BlockItem from ever producing a malformed single half if it is obtained manually.
        if (level.isClientSide()) return;
        BlockState foot = semanticState(state).setValue(PART, BedPart.FOOT)
                .setValue(LEGACY_META, legacyDirectionForFacing((Direction) state.getValue(FACING)));
        if (!level.getBlockState(pos).equals(foot)) level.setBlock(pos, foot, 3);
        Direction facing = foot.getValue(FACING);
        BlockPos headPos = pos.relative(facing);
        if (level.getBlockState(headPos).canBeReplaced()) {
            level.setBlock(headPos, stateForLegacyHalf(facing, true, false), 3);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isHeadMeta(legacyMeta(state)) ? null : new ConvertedLegacySeatBedBlockEntity(pos, semanticState(state));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS_SERVER;
        int meta = legacyMeta(state);
        Direction facing = facingForLegacyMeta(meta);
        BlockPos headPos = isHeadMeta(meta) ? pos : pos.relative(facing);
        BlockPos footPos = isHeadMeta(meta) ? pos.relative(facing.getOpposite()) : pos;
        BlockState head = level.getBlockState(headPos);
        if (!head.is(this)) return InteractionResult.CONSUME;

        syncSemantic(level, footPos);
        syncSemantic(level, headPos);
        head = level.getBlockState(headPos);

        BedRule bedRule = (BedRule) level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, headPos);
        // Forge/Minecraft 1.7 EnumStatus.NOT_POSSIBLE_NOW is the ordinary bed's daytime failure.
        // Modern BedRule exposes the same boundary as spawn-valid but currently-not-sleepable and non-explosive.
        if (!bedRule.explodes() && bedRule.canSetSpawn(level) && !bedRule.canSleep(level)) {
            if (level instanceof ServerLevel serverLevel) LegacySeatEntityRuntime.mount(serverLevel, footPos, player);
            return InteractionResult.SUCCESS_SERVER;
        }

        return super.useWithoutItem(level.getBlockState(pos), level, pos, player, hitResult);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        int meta = legacyMeta(state);
        Direction facing = facingForLegacyMeta(meta);
        boolean head = isHeadMeta(meta);
        Direction pairDirection = head ? facing.getOpposite() : facing;
        if (directionToNeighbour == pairDirection) {
            if (!neighbourState.is(this)) return Blocks.AIR.defaultBlockState();
            int neighbourMeta = legacyMeta(neighbourState);
            if (isHeadMeta(neighbourMeta) == head || facingForLegacyMeta(neighbourMeta) != facing) {
                return Blocks.AIR.defaultBlockState();
            }
            return semanticState(state).setValue(OCCUPIED, neighbourState.getValue(OCCUPIED));
        }
        return semanticState(state);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        int meta = legacyMeta(state);
        if (isHeadMeta(meta)) {
            Direction facing = facingForLegacyMeta(meta);
            BlockPos footPos = pos.relative(facing.getOpposite());
            BlockState foot = level.getBlockState(footPos);
            if (foot.is(this) && !isHeadMeta(legacyMeta(foot)) && facingForLegacyMeta(legacyMeta(foot)) == facing) {
                var item = BuiltInRegistries.ITEM.getValue(rule.placementItemId());
                if (item != null) Block.popResource(level, footPos, new ItemStack(item));
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (isHeadMeta(legacyMeta(state))) return List.of();
        var item = BuiltInRegistries.ITEM.getValue(rule.placementItemId());
        return item == null ? List.of() : List.of(new ItemStack(item));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        var item = BuiltInRegistries.ITEM.getValue(rule.placementItemId());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(0D, 0D, 0D, 16D, rule.blockHeight() * 16D, 16D);
    }

    public BlockState stateForLegacyHalf(Direction facing, boolean head, boolean occupied) {
        int meta = legacyDirectionForFacing(facing) | (head ? 8 : 0);
        return defaultBlockState().setValue(FACING, facing).setValue(PART, head ? BedPart.HEAD : BedPart.FOOT)
                .setValue(OCCUPIED, occupied).setValue(LEGACY_META, meta);
    }

    private BlockState semanticState(BlockState state) {
        int meta = legacyMeta(state);
        return state.setValue(FACING, facingForLegacyMeta(meta))
                .setValue(PART, isHeadMeta(meta) ? BedPart.HEAD : BedPart.FOOT);
    }

    private void syncSemantic(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return;
        BlockState semantic = semanticState(state);
        if (semantic != state) level.setBlock(pos, semantic, 2);
    }

    public static int legacyMeta(BlockState state) {
        if (state == null || !state.hasProperty(LEGACY_META)) throw new IllegalArgumentException("Seat-bed state lacks legacy metadata");
        return state.getValue(LEGACY_META);
    }

    public static boolean isHeadMeta(int meta) { return (meta & 8) != 0; }
    public static Direction facingForLegacyMeta(int meta) { return facingForLegacyDirection(meta & 3); }

    public static Direction facingForLegacyDirection(int direction) {
        return switch (direction & 3) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    public static int legacyDirectionForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> throw new IllegalArgumentException("Expected horizontal facing, got " + facing);
        };
    }
}
