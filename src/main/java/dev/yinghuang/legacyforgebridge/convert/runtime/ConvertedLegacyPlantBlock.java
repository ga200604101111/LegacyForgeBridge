package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyPlantRuntimeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

import java.util.ArrayList;
import java.util.List;

/**
 * Dormant until GeneratedModSupport explicitly selects it for a fully proven plant rule.
 *
 * <p>The block carries legacy raw metadata through {@link ConvertedLegacyBlock#LEGACY_META} and
 * implements only the survival/random-tick/support-loss/bonemeal semantics already locked by
 * {@link LegacyPlantRuntimeRegistry}. Item placement stays outside this class until its independent
 * proof/runtime gate is complete.</p>
 */
public final class ConvertedLegacyPlantBlock extends ConvertedLegacyBlock implements BonemealableBlock {
    private static final Identifier WATER = Identifier.parse("minecraft:water");
    private final Identifier convertedId;

    public ConvertedLegacyPlantBlock(Identifier convertedId, BlockBehaviour.Properties properties) {
        super(convertedId, properties.randomTicks().noCollision());
        this.convertedId = convertedId;
        if (!LegacyPlantRuntimeRegistry.hasRule(convertedId)) {
            throw new IllegalStateException("Missing proven plant runtime rule for " + convertedId);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        LegacyPlantRuntimeRegistry.Rule rule = requiredRule();
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        Identifier belowId = BuiltInRegistries.BLOCK.getKey(below.getBlock());
        boolean belowSamePlant = below.getBlock() == this;
        boolean adjacentWater = rule.adjacentWaterRequired() && hasAdjacentLegacyWater(level, belowPos);
        return LegacyPlantRuntimeRegistry.canSurvive(rule, belowId, adjacentWater, belowSamePlant);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide() || canSurvive(state, level, pos)) return;
        dropAndRemove(state, level, pos, level.getRandom());
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        LegacyPlantRuntimeRegistry.Rule rule = requiredRule();
        if (!canSurvive(state, level, pos)) {
            dropAndRemove(state, level, pos, random);
            return;
        }
        switch (rule.family()) {
            case CROPS -> cropRandomTick(rule, state, level, pos, random);
            case REED -> reedRandomTick(state, level, pos);
            case BUSH -> { }
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        LegacyPlantRuntimeRegistry.Rule rule = requiredRule();
        return rule.family() == LegacyPlantRuntimeRegistry.Family.CROPS
                && LegacyPlantRuntimeRegistry.isCropBonemealTarget(rule, legacyMeta(state));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return requiredRule().family() == LegacyPlantRuntimeRegistry.Family.CROPS;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        LegacyPlantRuntimeRegistry.Rule rule = requiredRule();
        if (rule.family() != LegacyPlantRuntimeRegistry.Family.CROPS) return;
        int metadata = LegacyPlantRuntimeRegistry.cropBonemealMetadata(rule, legacyMeta(state), random::nextInt);
        level.setBlock(pos, withLegacyMeta(state, metadata), 2);
    }

    private void cropRandomTick(LegacyPlantRuntimeRegistry.Rule rule, BlockState state, ServerLevel level,
                                BlockPos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) < 9) return;
        int metadata = legacyMeta(state);
        if (metadata >= 7) return;
        float growthRate = LegacyPlantRuntimeRegistry.cropGrowthRate(rule, cropSoils(level, pos),
                samePlantOnXAxis(level, pos), samePlantOnZAxis(level, pos), samePlantDiagonal(level, pos));
        int denominator = (int) (25.0F / growthRate) + 1;
        if (random.nextInt(denominator) == 0) {
            level.setBlock(pos, withLegacyMeta(state, metadata + 1), 2);
        }
    }

    private void reedRandomTick(BlockState state, ServerLevel level, BlockPos pos) {
        boolean aboveAir = level.isEmptyBlock(pos.above());
        int height = 1;
        while (height < 3 && level.getBlockState(pos.below(height)).getBlock() == this) height++;
        LegacyPlantRuntimeRegistry.ReedTick tick = LegacyPlantRuntimeRegistry.reedRandomTick(
                legacyMeta(state), height, aboveAir);
        if (tick.growAbove()) {
            level.setBlock(pos.above(), defaultBlockState(), 3);
            level.setBlock(pos, withLegacyMeta(state, tick.metadata()), 4);
        } else if (tick.metadata() != legacyMeta(state)) {
            level.setBlock(pos, withLegacyMeta(state, tick.metadata()), 4);
        }
    }

    private void dropAndRemove(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.isClientSide()) return;
        LegacyPlantRuntimeRegistry.Rule rule = requiredRule();
        List<LegacyPlantRuntimeRegistry.DropStack> drops = LegacyPlantRuntimeRegistry.unsupportedRemovalDrops(
                rule, legacyMeta(state), random::nextInt);
        for (LegacyPlantRuntimeRegistry.DropStack drop : drops) {
            if (!BuiltInRegistries.ITEM.containsKey(drop.itemId())) {
                throw new IllegalStateException("Missing proven plant drop item at runtime: " + drop.itemId());
            }
            popResource(level, pos, new ItemStack(BuiltInRegistries.ITEM.getValue(drop.itemId()), drop.count()));
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private List<LegacyPlantRuntimeRegistry.SoilSample> cropSoils(LevelReader level, BlockPos cropPos) {
        List<LegacyPlantRuntimeRegistry.SoilSample> samples = new ArrayList<>(9);
        BlockPos below = cropPos.below();
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                BlockState soil = level.getBlockState(below.offset(x, 0, z));
                Identifier id = BuiltInRegistries.BLOCK.getKey(soil.getBlock());
                int moisture = soil.hasProperty(FarmBlock.MOISTURE) ? soil.getValue(FarmBlock.MOISTURE) : 0;
                samples.add(new LegacyPlantRuntimeRegistry.SoilSample(id, moisture));
            }
        }
        return List.copyOf(samples);
    }

    private boolean samePlantOnXAxis(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.west()).getBlock() == this || level.getBlockState(pos.east()).getBlock() == this;
    }

    private boolean samePlantOnZAxis(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.north()).getBlock() == this || level.getBlockState(pos.south()).getBlock() == this;
    }

    private boolean samePlantDiagonal(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.north().west()).getBlock() == this
                || level.getBlockState(pos.north().east()).getBlock() == this
                || level.getBlockState(pos.south().west()).getBlock() == this
                || level.getBlockState(pos.south().east()).getBlock() == this;
    }

    private static boolean hasAdjacentLegacyWater(LevelReader level, BlockPos soilPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState adjacent = level.getBlockState(soilPos.relative(direction));
            if (WATER.equals(BuiltInRegistries.BLOCK.getKey(adjacent.getBlock()))) return true;
        }
        return false;
    }

    private LegacyPlantRuntimeRegistry.Rule requiredRule() {
        LegacyPlantRuntimeRegistry.Rule rule = LegacyPlantRuntimeRegistry.rule(convertedId);
        if (rule == null) throw new IllegalStateException("Missing proven plant runtime rule for " + convertedId);
        return rule;
    }
}
