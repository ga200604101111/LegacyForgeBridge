package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacySingleInputProcessorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Modern block host for an admitted generic three-slot legacy processor. */
public final class ConvertedLegacyProcessorBlock extends ConvertedLegacyBlock implements EntityBlock {
    private final Identifier convertedId;
    private final LegacySingleInputProcessorRegistry.Rule processorRule;

    public ConvertedLegacyProcessorBlock(Identifier convertedId,BlockBehaviour.Properties properties){
        super(convertedId,properties);
        this.convertedId=convertedId;
        processorRule=LegacySingleInputProcessorRegistry.rule(convertedId);
        if(processorRule==null)throw new IllegalArgumentException("Missing processor rule for "+convertedId);
    }

    @Override
    public RenderShape getRenderShape(BlockState state){
        return LegacySingleInputProcessorRegistry.hasWorldPresentation(convertedId)
                ?RenderShape.INVISIBLE:RenderShape.MODEL;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return new ConvertedLegacyProcessorBlockEntity(pos,state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hitResult){
        if(level.isClientSide())return InteractionResult.SUCCESS;
        BlockEntity blockEntity=level.getBlockEntity(pos);
        if(blockEntity instanceof ConvertedLegacyProcessorBlockEntity processor&&player instanceof ServerPlayer serverPlayer){
            serverPlayer.openMenu(processor);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        BlockEntityType<ConvertedLegacyProcessorBlockEntity> expected=LegacySingleInputProcessorRegistry.requireType(this);
        if(type!=expected)return null;
        return (tickLevel,pos,tickState,blockEntity)->{
            if(!(blockEntity instanceof ConvertedLegacyProcessorBlockEntity processor))return;
            if(tickLevel.isClientSide()){
                if(processorRule.metadataDrivesRoll())ConvertedLegacyProcessorBlockEntity.clientTick(tickLevel,pos,tickState,processor);
            }else if(tickLevel instanceof ServerLevel serverLevel){
                ConvertedLegacyProcessorBlockEntity.serverTick(serverLevel,pos,tickState,processor);
            }
        };
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean movedByPiston){
        if(!movedByPiston&&processorRule.dropContents()){
            BlockEntity blockEntity=level.getBlockEntity(pos);
            if(blockEntity instanceof ConvertedLegacyProcessorBlockEntity processor){
                Containers.dropContents(level,pos,processor);
            }
        }
        super.affectNeighborsAfterRemoval(state,level,pos,movedByPiston);
        if(processorRule.comparator())Containers.updateNeighboursAfterDestroy(state,level,pos);
    }

    @Override protected boolean hasAnalogOutputSignal(BlockState state){return processorRule.comparator();}

    @Override
    protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction direction){
        if(!processorRule.comparator())return 0;
        BlockEntity blockEntity=level.getBlockEntity(pos);
        return blockEntity instanceof ConvertedLegacyProcessorBlockEntity processor
                ?AbstractContainerMenu.getRedstoneSignalFromContainer(processor):0;
    }
}
