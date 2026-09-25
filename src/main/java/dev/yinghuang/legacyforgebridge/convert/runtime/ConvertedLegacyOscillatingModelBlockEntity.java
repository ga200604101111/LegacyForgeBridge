package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyOscillatingModelBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/** Ephemeral client-only source animation state; deliberately has no custom NBT persistence. */
public final class ConvertedLegacyOscillatingModelBlockEntity extends BlockEntity {
    private static final Random RANDOM=new Random();
    private final LegacyOscillatingModelBlockRegistry.Rule rule;
    private float angleDegrees;
    private boolean positiveDirection;

    public ConvertedLegacyOscillatingModelBlockEntity(BlockPos pos,BlockState state){
        super(LegacyOscillatingModelBootstrap.requireType(state.getBlock()),pos,state);
        Identifier id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        this.rule=LegacyOscillatingModelBlockRegistry.requireRule(id);
        this.angleDegrees=RANDOM.nextInt(rule.animation().randomInitialBound());
        this.positiveDirection=RANDOM.nextBoolean();
    }

    public static void clientTick(Level level,BlockPos pos,BlockState state,ConvertedLegacyOscillatingModelBlockEntity blockEntity){
        if(!level.isClientSide())return;
        blockEntity.tickAnimation();
    }

    private void tickAnimation(){
        var animation=rule.animation();
        if(positiveDirection){
            angleDegrees=(float)((double)angleDegrees+(double)animation.stepDegrees());
            if(angleDegrees>=animation.upperBoundDegrees())positiveDirection=!positiveDirection;
        }else{
            angleDegrees=(float)((double)angleDegrees-(double)animation.stepDegrees());
            if(angleDegrees<=animation.lowerBoundDegrees())positiveDirection=!positiveDirection;
        }
    }

    static Step sourceStep(float angle,boolean positive,float step,float lower,float upper){
        float next;
        boolean direction=positive;
        if(direction){
            next=(float)((double)angle+(double)step);
            if(next>=upper)direction=!direction;
        }else{
            next=(float)((double)angle-(double)step);
            if(next<=lower)direction=!direction;
        }
        return new Step(next,direction);
    }

    record Step(float angle,boolean positiveDirection) { }

    public float angleDegrees(){return angleDegrees;}
}
