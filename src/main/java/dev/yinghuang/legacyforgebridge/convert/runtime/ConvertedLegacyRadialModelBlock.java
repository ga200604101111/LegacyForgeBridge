package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyRadialModelBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Modern invisible block host for a source-proven radial TESR base. */
public final class ConvertedLegacyRadialModelBlock extends ConvertedLegacyBlock implements EntityBlock {
    private final LegacyRadialModelBlockRegistry.Rule rule;
    public ConvertedLegacyRadialModelBlock(Identifier id,BlockBehaviour.Properties properties){
        super(id,configure(id,properties));this.rule=LegacyRadialModelBlockRegistry.requireRule(id);
    }
    private static BlockBehaviour.Properties configure(Identifier id,BlockBehaviour.Properties properties){
        var rule=LegacyRadialModelBlockRegistry.requireRule(id);
        return properties.noOcclusion().lightLevel(state->rule.lightEmission());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ConvertedLegacyRadialModelBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
}
