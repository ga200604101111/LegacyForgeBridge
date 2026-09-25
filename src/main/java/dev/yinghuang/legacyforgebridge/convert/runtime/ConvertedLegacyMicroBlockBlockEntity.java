package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyMicroBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Client state carrier for a source-proven N^3 legacy micro-block container. */
public final class ConvertedLegacyMicroBlockBlockEntity extends BlockEntity {
    private final LegacyMicroBlockRegistry.Rule rule;
    private LegacyMicroBlockRegistry.Snapshot snapshot;

    public ConvertedLegacyMicroBlockBlockEntity(BlockPos pos, BlockState state) {
        super(LegacyMicroBlockRegistry.requireType(state.getBlock()), pos, state);
        this.rule=LegacyMicroBlockRegistry.requireRule(state.getBlock());
        this.snapshot=LegacyMicroBlockRegistry.Snapshot.empty(rule.fallbackFieldSize());
    }

    public LegacyMicroBlockRegistry.Rule rule(){return rule;}
    public int fieldSize(){return snapshot.fieldSize();}
    public boolean isEmpty(){return snapshot.occupiedCount()==0;}

    public LegacyMicroBlockRegistry.LegacyCell cell(int x,int y,int z){
        int n=fieldSize();
        if(x<0||y<0||z<0||x>=n||y>=n||z>=n)return LegacyMicroBlockRegistry.LegacyCell.EMPTY;
        return snapshot.cells().get((x*n+y)*n+z);
    }

    public BlockState cellState(int x,int y,int z){
        LegacyMicroBlockRegistry.LegacyCell cell=cell(x,y,z);
        return cell.empty()?null:LegacyMicroBlockRegistry.resolveBlockState(cell.legacyItemId(),cell.metadata());
    }

    public List<dev.yinghuang.legacyforgebridge.compat.LegacyGeometry.Box> occupiedBoxes(){
        if(isEmpty())return List.of();
        int n=fieldSize();double step=1D/n;
        java.util.ArrayList<dev.yinghuang.legacyforgebridge.compat.LegacyGeometry.Box> boxes=new java.util.ArrayList<>(snapshot.occupiedCount());
        for(int x=0;x<n;x++)for(int y=0;y<n;y++)for(int z=0;z<n;z++)if(!cell(x,y,z).empty()){
            boxes.add(new dev.yinghuang.legacyforgebridge.compat.LegacyGeometry.Box(
                    x*step,y*step,z*step,(x+1)*step,(y+1)*step,(z+1)*step));
        }
        return List.copyOf(boxes);
    }

    public void applySnapshot(LegacyMicroBlockRegistry.Snapshot next){
        if(next==null)throw new IllegalArgumentException("snapshot");
        int normalized=rule.normalizeSize(next.fieldSize());
        if(normalized!=next.fieldSize()||next.cells().size()!=normalized*normalized*normalized)
            throw new IllegalArgumentException("Micro-block snapshot shape mismatch for "+rule.id());
        this.snapshot=next;
        setChanged();
    }
}
