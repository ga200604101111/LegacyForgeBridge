package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.yinghuang.legacyforgebridge.convert.LegacyGridPotAsm.*;
import static dev.yinghuang.legacyforgebridge.convert.LegacyGridPotProofs.*;

/**
 * Fail-closed proof for a legacy BlockContainer whose state is a fixed square grid of individually
 * enabled pot-like cells with one optional ItemStack per cell. The content-insertion render predicate
 * is inventoried independently and remains runtime-closed until its legacy render identity is portable.
 */
public final class LegacyGridPotBlockAnalyzer {
    private static final String BLOCK_CONTAINER = "net/minecraft/block/BlockContainer";
    private static final String TILE_ENTITY = "net/minecraft/tileentity/TileEntity";
    private static final String ITEM_BLOCK = "net/minecraft/item/ItemBlock";

    public record Rule(String registryName,String legacyNamespace,String sourceBlockClass,String sourceItemBlockClass,
                       String sourceTileClass,String legacyTileId,int cells,int gridWidth,float baseHeight,float cellHeight,
                       boolean placementCreatesCell,boolean emptyHandRemovalProven,boolean selfItemAddsCellProven,
                       boolean breakDropsEveryEnabledCell,boolean normalBlockDropDisabled,boolean persistenceProven,
                       boolean dynamicCellShapeProven,boolean nonOpaqueProven,boolean contentInsertionPredicateProven) { }
    public record Skipped(String registryName,String sourceBlockClass,String reason) { }
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules); skipped=List.copyOf(skipped); diagnostics=List.copyOf(diagnostics); }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String,ClassNode> classes=loadClasses(jarPath);
        LegacyRegistryAnalyzer.Analysis registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        LegacyLifecycleAnalyzer.Analysis lifecycle=new LegacyLifecycleAnalyzer().analyze(jarPath);
        Map<String,String> tileIds=registeredTiles(lifecycle);
        List<Rule> rules=new ArrayList<>(); List<Skipped> skipped=new ArrayList<>();
        LinkedHashSet<String> diagnostics=new LinkedHashSet<>();
        diagnostics.addAll(registry.diagnostics()); diagnostics.addAll(lifecycle.diagnostics());

        for(LegacyRegistryAnalyzer.Registration registration:registry.blocks()){
            String blockClass=registration.implementationClass(), itemBlockClass=registration.itemBlockClass();
            if(blockClass==null||itemBlockClass==null||ITEM_BLOCK.equals(itemBlockClass))continue;
            if(!inherits(classes,blockClass,BLOCK_CONTAINER)||!inherits(classes,itemBlockClass,ITEM_BLOCK))continue;

            MethodNode create=method(classes,blockClass,Set.of("createNewTileEntity","func_149915_a"),
                    "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
            String tileClass=uniqueCreatedType(create);
            if(tileClass==null||!tileIds.containsKey(tileClass)||!inherits(classes,tileClass,TILE_ENTITY)){
                skipped.add(skip(registration,blockClass,"created TileEntity is not uniquely source-proven and lifecycle-registered")); continue;
            }

            TileShape shape=tileShape(classes.get(tileClass));
            if(shape==null){skipped.add(skip(registration,blockClass,"TileEntity is not the admitted nine-cell enabled/item grid family"));continue;}
            if(!canonicalPersistence(classes.get(tileClass),shape)){
                skipped.add(skip(registration,blockClass,"grid persistence is not the admitted per-cell boolean plus optional ItemStack NBT shape"));continue;
            }
            if(!canonicalPlacement(classes,itemBlockClass,tileClass,shape)){
                skipped.add(skip(registration,blockClass,"custom ItemBlock placement does not prove one hit-selected cell is enabled after successful placement"));continue;
            }

            MethodNode activation=method(classes,blockClass,Set.of("onBlockActivated","func_149727_a"),
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z");
            boolean emptyHand=canonicalEmptyHandAndSelfItemActivation(activation,tileClass,shape);
            boolean insertionPredicate=canonicalContentInsertionPredicate(activation,tileClass,shape);
            if(!emptyHand){skipped.add(skip(registration,blockClass,"activation does not prove empty-hand removal plus same-BlockItem cell addition"));continue;}

            MethodNode breakBlock=method(classes,blockClass,Set.of("breakBlock","func_149749_a"),
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;I)V");
            if(!calls(breakBlock,tileClass,shape.dropAll().name,shape.dropAll().desc)){
                skipped.add(skip(registration,blockClass,"breakBlock does not source-prove grid dropAll invocation"));continue;
            }
            MethodNode dropped=method(classes,blockClass,Set.of("getItemDropped","func_149650_a"),
                    "(ILjava/util/Random;I)Lnet/minecraft/item/Item;");
            if(!returnsNull(dropped)){skipped.add(skip(registration,blockClass,"normal Block drop is not explicitly disabled"));continue;}

            Float baseHeight=constructorFloatBeforeCall(classes.get(blockClass),"func_149676_a","(FFFFFF)V",0.01F);
            MethodNode bounds=method(classes,blockClass,Set.of("setBlockBoundsForItemRender","func_149683_g"),"()V");
            Float cellHeight=containsFloat(bounds,0.375F)?0.375F:null;
            boolean dynamicShape=canonicalDynamicCellShape(classes,blockClass,tileClass,shape,baseHeight,cellHeight);
            boolean nonOpaque=constantFalse(method(classes,blockClass,Set.of("isOpaqueCube","func_149662_c"),"()Z"))
                    &&constantFalse(method(classes,blockClass,Set.of("renderAsNormalBlock","func_149686_d"),"()Z"));
            if(baseHeight==null||cellHeight==null||!dynamicShape||!nonOpaque){
                skipped.add(skip(registration,blockClass,"grid collision/selection geometry or non-opaque presentation boundary is not source-proven"));continue;
            }
            rules.add(new Rule(registration.registryName(),registration.legacyNamespace(),blockClass,itemBlockClass,
                    tileClass,tileIds.get(tileClass),9,3,baseHeight,cellHeight,true,true,true,true,true,true,true,true,insertionPredicate));
        }
        return new Analysis(rules,skipped,List.copyOf(diagnostics));
    }

    private static Map<String,String> registeredTiles(LegacyLifecycleAnalyzer.Analysis lifecycle){
        java.util.LinkedHashMap<String,String> result=new java.util.LinkedHashMap<>();
        for(LegacyLifecycleAnalyzer.Registration registration:lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY)){
            if(registration.arguments().size()<2)continue;
            if(registration.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type
                    && registration.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text){
                result.put(type.internalName(),text.value());
            }
        }
        return result;
    }
    private static Skipped skip(LegacyRegistryAnalyzer.Registration r,String owner,String reason){return new Skipped(r.registryName(),owner,reason);}
}
