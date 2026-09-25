package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacySingleInputProcessorRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Generic menu preserving the source three machine slots and 1.7 player-inventory layout. */
public final class ConvertedLegacyProcessorMenu extends AbstractContainerMenu {
    private final Container machine;
    private final ContainerData data;

    /** Client-side constructor used by the shared vanilla MenuType. */
    public ConvertedLegacyProcessorMenu(int containerId, Inventory playerInventory) {
        this(containerId,playerInventory,new SimpleContainer(3),new SimpleContainerData(4));
    }

    /** Server-side constructor used by the converted BlockEntity. */
    public ConvertedLegacyProcessorMenu(int containerId, Inventory playerInventory, Container machine, ContainerData data) {
        super(LegacyProcessorMenuSupport.type(),containerId);
        checkContainerSize(machine,3);
        checkContainerDataCount(data,4);
        this.machine=machine;
        this.data=data;
        addSlot(new Slot(machine,0,80,9));
        addSlot(new OutputSlot(machine,1,58,57));
        addSlot(new OutputSlot(machine,2,102,57));
        addStandardInventorySlots(playerInventory,8,84);
        addDataSlots(data);
        machine.startOpen(playerInventory.player);
    }

    @Override public boolean stillValid(Player player){return machine.stillValid(player);}

    @Override public ItemStack quickMoveStack(Player player,int index){
        Slot slot=index>=0&&index<slots.size()?slots.get(index):null;
        if(slot==null||!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem();
        ItemStack copy=stack.copy();
        boolean moved;
        if(index==1||index==2){
            moved=moveItemStackTo(stack,3,39,true);
        }else if(index==0){
            moved=moveItemStackTo(stack,3,39,false);
        }else if(index>=3&&index<39){
            boolean grindable=machine instanceof ConvertedLegacyProcessorBlockEntity processor
                    &&processor.canGrindInput(stack);
            if(grindable&&moveItemStackTo(stack,0,1,false))moved=true;
            else if(index<30)moved=moveItemStackTo(stack,30,39,false);
            else moved=moveItemStackTo(stack,3,30,false);
        }else return ItemStack.EMPTY;
        if(!moved)return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        if(stack.getCount()==copy.getCount())return ItemStack.EMPTY;
        slot.onTake(player,stack);
        return copy;
    }

    @Override public void removed(Player player){super.removed(player);machine.stopOpen(player);}

    public int grindMotion(){return data.get(0);}
    public int progressStage(){return data.get(1);}
    public boolean grinding(){return data.get(2)!=0;}
    public int presentationKey(){return data.get(3);}

    private static final class OutputSlot extends Slot {
        OutputSlot(Container container,int slot,int x,int y){super(container,slot,x,y);}
        @Override public boolean mayPlace(ItemStack stack){return false;}
    }
}
