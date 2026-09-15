package dev.longyu.legacyforgebridge.convert.runtime;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.compat.LegacySingleInputProcessorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** Modern base runtime for source-proven three-slot single-input legacy processors. */
public final class ConvertedLegacyProcessorBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private static final String AIR_ID="minecraft:air";
    private final LegacySingleInputProcessorRegistry.Rule rule;
    private NonNullList<ItemStack> items;
    private int grindTime;
    private int grindMotion;
    private int innerEnergy;
    private int activeRecipeIndex=-1;
    private String activeLegacyName=AIR_ID;
    private int activeLegacyMeta;

    private final ContainerData data=new ContainerData(){
        @Override public int get(int index){return switch(index){case 0->grindMotion;case 1->progressStage();case 2->grindTime>0?1:0;default->0;};}
        @Override public void set(int index,int value){
            if(index==0)grindMotion=value;
            else if(index==1&&grindTime<=0&&value>0)grindTime=Math.min(rule.processTicks(),Math.round(value/3F*rule.processTicks()));
        }
        @Override public int getCount(){return 3;}
    };

    public ConvertedLegacyProcessorBlockEntity(BlockPos pos,BlockState state){
        super(LegacySingleInputProcessorRegistry.requireType(state.getBlock()),pos,state);
        this.rule=LegacySingleInputProcessorRegistry.requireRule(state.getBlock());
        this.items=NonNullList.withSize(rule.slots(),ItemStack.EMPTY);
    }

    @Override protected Component getDefaultName(){return Component.translatable("block."+rule.id().getNamespace()+"."+rule.id().getPath());}
    @Override public int getContainerSize(){return rule.slots();}
    @Override public int getMaxStackSize(){return rule.stackLimit();}
    @Override protected NonNullList<ItemStack> getItems(){return items;}
    @Override protected void setItems(NonNullList<ItemStack> values){
        NonNullList<ItemStack> normalized=NonNullList.withSize(rule.slots(),ItemStack.EMPTY);
        int count=Math.min(values.size(),normalized.size());
        for(int slot=0;slot<count;slot++)normalized.set(slot,values.get(slot));
        this.items=normalized;
    }

    // Source isItemValidForSlot accepts every item in slot 0 and rejects both output slots.
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==rule.inputSlot();}
    public boolean canGrindInput(ItemStack stack){return level!=null&&LegacySingleInputProcessorRegistry.findInput(rule,stack,level.registryAccess())!=null;}

    @Override public int[] getSlotsForFace(Direction side){
        return switch(side){case DOWN->rule.bottomSlots();case UP->rule.topSlots();default->rule.sideSlots();};
    }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction direction){return canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction direction){return direction!=Direction.DOWN||slot!=rule.inputSlot();}

    @Override public boolean stillValid(Player player){
        if(level==null||level.getBlockEntity(worldPosition)!=this)return false;
        return player.distanceToSqr(worldPosition.getX()+0.5,worldPosition.getY()+0.5,worldPosition.getZ()+0.5)<=rule.interactionDistanceSq();
    }

    @Override protected void loadAdditional(ValueInput input){
        super.loadAdditional(input);
        items=NonNullList.withSize(rule.slots(),ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input,items);
        grindTime=input.getIntOr("grindTime",0);
        activeLegacyMeta=input.getIntOr("grindItemDmg",0);
        activeLegacyName=input.getStringOr("grindItemName",AIR_ID);
        activeRecipeIndex=input.getIntOr("lfbActiveRecipe",-1);
        grindMotion=grindTime>0?Math.floorMod(grindTime,40)/10:0;
        if(rule.legacyEnergyApiPresent())innerEnergy=input.getIntOr(rule.energyNbtKey(),0);
    }

    @Override protected void saveAdditional(ValueOutput output){
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output,items);
        output.putInt("grindTime",grindTime);
        output.putString("grindItemName",activeLegacyName==null?AIR_ID:activeLegacyName);
        output.putInt("grindItemDmg",activeLegacyMeta);
        output.putInt("lfbActiveRecipe",activeRecipeIndex);
        if(rule.legacyEnergyApiPresent())output.putInt(rule.energyNbtKey(),innerEnergy);
    }

    @Override protected AbstractContainerMenu createMenu(int containerId,Inventory inventory){
        return new ConvertedLegacyProcessorMenu(containerId,inventory,this,data);
    }

    public static void serverTick(ServerLevel level,BlockPos pos,BlockState state,ConvertedLegacyProcessorBlockEntity machine){
        machine.tickServer();
    }

    private void tickServer(){
        if(level==null||level.isClientSide())return;
        int step=1;
        if(grindTime!=0&&rule.legacyEnergyApiPresent()){
            step=sourceProgressStep(grindTime,innerEnergy,rule.minUseEnergy());
            innerEnergy=sourceEnergyAfterStep(grindTime,innerEnergy,rule.minUseEnergy());
        }
        boolean changed=false;
        if(grindTime==0){
            LegacySingleInputProcessorRegistry.ResolvedRecipe recipe=
                    LegacySingleInputProcessorRegistry.findInput(rule,items.get(rule.inputSlot()),level.registryAccess());
            if(recipe!=null&&canStart(recipe)){
                start(recipe);
                grindTime+=step;
                changed=true;
            }
        }else{
            grindTime+=step;
            changed=true;
            if(grindTime>rule.processTicks()){
                LegacySingleInputProcessorRegistry.ResolvedRecipe recipe=
                        LegacySingleInputProcessorRegistry.resolveActive(
                                rule,activeRecipeIndex,activeLegacyName,activeLegacyMeta,level.registryAccess());
                grindTime=0;
                if(recipe!=null)finish(recipe);
                else LegacyForgeBridge.LOGGER.error(
                        "Converted processor {} could not resolve active legacy recipe {}:{}; output was not fabricated.",
                        rule.id(),activeLegacyName,activeLegacyMeta);
            }
        }
        grindMotion=grindTime>0?Math.floorMod(grindTime,40)/10:0;
        if(changed){
            setChanged();
            level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
        }
    }

    private boolean canStart(LegacySingleInputProcessorRegistry.ResolvedRecipe recipe){
        ItemStack main=recipe.output(),bonus=recipe.bonus(),slotMain=items.get(1),slotBonus=items.get(2);
        if(main.isEmpty())return false;
        // Exact 1.7 source returns early when both outputs are empty, before capacity checks.
        if(slotMain.isEmpty()&&slotBonus.isEmpty())return true;
        if(!slotBonus.isEmpty()&&bonus.isEmpty())return false;
        if(!slotMain.isEmpty()&&!ItemStack.isSameItemSameComponents(slotMain,main))return false;
        if(!slotBonus.isEmpty()&&!bonus.isEmpty()&&!ItemStack.isSameItemSameComponents(slotBonus,bonus))return false;
        int mainCount=(slotMain.isEmpty()?0:slotMain.getCount())+main.getCount();
        boolean ok=mainCount<=getMaxStackSize()&&mainCount<=main.getMaxStackSize();
        if(ok&&!bonus.isEmpty()){
            int bonusCount=(slotBonus.isEmpty()?0:slotBonus.getCount())+bonus.getCount();
            ok=bonusCount<=getMaxStackSize()&&bonusCount<=bonus.getMaxStackSize();
        }
        return ok;
    }

    private void start(LegacySingleInputProcessorRegistry.ResolvedRecipe recipe){
        activeRecipeIndex=recipe.index();
        activeLegacyName=recipe.legacyInput().registryId();
        activeLegacyMeta=recipe.legacyInput().meta();
        ItemStack input=items.get(rule.inputSlot());
        input.shrink(recipe.requiredInputCount());
        if(input.isEmpty())items.set(rule.inputSlot(),ItemStack.EMPTY);
    }

    private void finish(LegacySingleInputProcessorRegistry.ResolvedRecipe recipe){
        mergeSourceStyle(1,recipe.output());
        if(!recipe.bonus().isEmpty()&&level.random.nextFloat()<=recipe.bonusChance())mergeSourceStyle(2,recipe.bonus());
        activeRecipeIndex=-1;
        activeLegacyName=AIR_ID;
        activeLegacyMeta=0;
    }

    /** Source completion silently does nothing if the occupied output slot no longer matches. */
    private void mergeSourceStyle(int slot,ItemStack produced){
        if(produced.isEmpty())return;
        ItemStack current=items.get(slot);
        if(current.isEmpty())items.set(slot,produced.copy());
        else if(ItemStack.isSameItemSameComponents(current,produced))current.grow(produced.getCount());
    }

    private int progressStage(){return Math.round((float)grindTime/(float)rule.processTicks()*3F);}

    /** Exact source rule: acceleration is only considered once progress is non-zero and energy > minimum. */
    private static int sourceProgressStep(int progress,int energy,int minUseEnergy){
        if(progress==0||energy<=minUseEnergy)return 1;
        return (byte)(energy/minUseEnergy+1);
    }

    /** Exact source behavior intentionally allows energy to overshoot below zero (e.g. 500 -> -100). */
    private static int sourceEnergyAfterStep(int progress,int energy,int minUseEnergy){
        int step=sourceProgressStep(progress,energy,minUseEnergy);
        return progress==0||energy<=minUseEnergy?energy:energy-minUseEnergy*step;
    }

    int grindTimeForTests(){return grindTime;}
    int innerEnergyForTests(){return innerEnergy;}
    int activeRecipeForTests(){return activeRecipeIndex;}
}
