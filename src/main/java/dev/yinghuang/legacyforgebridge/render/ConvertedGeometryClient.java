package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.*;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Fabric native model extension: no alternate raw block IDs, no extra resource mod. */
public final class ConvertedGeometryClient implements ClientModInitializer {
    private record Material(TextureAtlasSprite sprite,int tint) { }
    private static final Direction[] DIRECTIONS={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private static final Map<BlockStateModel,Material[]> MATERIALS=new ConcurrentHashMap<>();
    private static final Map<List<LegacyGeometry.Box>,List<LegacyGeometry.Face>> SURFACES=new ConcurrentHashMap<>();
    @Override public void onInitializeClient() {
        LegacyMimicInvalidationClient.initialize();
        ModelLoadingPlugin.register(plugin->{
            MATERIALS.clear();
            plugin.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{
                BlockState state=context.state();var rule=LegacyBlockGeometryRegistry.rule(state);
                if(rule==null||!state.hasProperty(ConvertedLegacyBlock.LEGACY_META)||rule.variant(ConvertedLegacyBlock.legacyMeta(state))==null)return model;
                return new GeometryModel(model,rule);
            });
        });
        LegacyForgeBridge.LOGGER.info("Native geometric model projection enabled: stairs, slabs, connected panes, connected cuboids and directional neighbour materials; rules={}",LegacyBlockGeometryRegistry.all().size());
    }
    private static final class GeometryModel extends WrapperBlockStateModel {
        private final LegacyGeometrySpec.Rule rule;
        private final Material[] materials;
        private GeometryModel(BlockStateModel wrapped,LegacyGeometrySpec.Rule rule){super(wrapped);this.rule=rule;this.materials=materials(wrapped);}
        @Override public Object createGeometryKey(BlockAndTintGetter world,BlockPos pos,BlockState state,RandomSource random){
            // Null is required for neighbor-dependent geometry/materials; never use a stale
            // state-only key for connected panels, stair corners or copied adjacent material.
            return rule.family().equals("box")?this:null;
        }
        @Override public void emitQuads(QuadEmitter emitter,BlockAndTintGetter world,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cullTest){
            int meta=ConvertedLegacyBlock.legacyMeta(state);var variant=rule.variant(meta);
            if(variant==null){super.emitQuads(emitter,world,pos,state,random,cullTest);return;}
            List<LegacyGeometry.Face> faces;
            if(rule.pane())faces=LegacyGeometry.paneFaces(LegacyBlockGeometryRegistry.paneMask(world,pos,state),variant.edges());
            else faces=surfaces(LegacyBlockGeometryRegistry.renderBoxes(rule,meta,world,pos,state));
            Material[] chosen=materials;BlockState tintState=state;BlockPos tintPos=pos;
            ChunkSectionLayer layer=ChunkSectionLayer.CUTOUT;
            if(rule.mimic()&&variant.copyFace()>=0){
                Target target=referenced(world,pos,state);
                if(target!=null){
                    BlockStateModel targetModel=Minecraft.getInstance().getBlockRenderer().getBlockModel(target.state());
                    chosen=targetModel instanceof GeometryModel g?g.materials:materials(targetModel);
                    tintState=target.state();tintPos=target.pos();
                    if(ItemBlockRenderTypes.getChunkRenderType(target.state())==ChunkSectionLayer.TRANSLUCENT)layer=ChunkSectionLayer.TRANSLUCENT;
                }
            }
            for(var face:faces){
                Direction direction=DIRECTIONS[face.side()];Direction cull=boundary(face)?direction:null;
                if(cull!=null&&cullTest.test(cull))continue;
                Material material=chosen[face.side()];int color=material.tint()<0?0xFFFFFF:Minecraft.getInstance().getBlockColors().getColor(tintState,world,tintPos,material.tint());
                if(color==-1)color=0xFFFFFF;
                emitter.cullFace(cull).nominalFace(direction).renderLayer(layer).tintIndex(-1).ambientOcclusion(TriState.DEFAULT);
                for(int vertex=0;vertex<4;vertex++){
                    var p=face.positions();emitter.pos(vertex,p.get(vertex*3).floatValue(),p.get(vertex*3+1).floatValue(),p.get(vertex*3+2).floatValue());
                    emitter.uv(vertex,face.uv().get(vertex*2).floatValue(),face.uv().get(vertex*2+1).floatValue());emitter.color(vertex,0xFF000000|color);
                }
                emitter.spriteBake(material.sprite(),QuadEmitter.BAKE_NORMALIZED);emitter.emit();
            }
        }
    }
    private record Target(BlockPos pos,BlockState state) { }
    /** Resolve the source directional neighbor chain with explicit cycle/budget protection.
     * Complex neighbor multi-layer model equivalence and source interaction forwarding are not claimed.
     */
    private static Target referenced(BlockAndTintGetter world,BlockPos position,BlockState state){
        // A traversal budget alone does not bound snapshot array indices. Check all axes
        // BEFORE reading, including the terminal. Unknown views get only a local allowance.
        var window=world instanceof RenderSectionRegion
                ?LegacyMimicReadWindow.sectionSnapshot(position.getX(),position.getY(),position.getZ())
                :LegacyMimicReadWindow.immediateNeighbors(position.getX(),position.getY(),position.getZ());
        BlockPos target=LegacyMimicResolver.resolve(position.immutable(),pos->{
            BlockState current=pos.equals(position)?state:world.getBlockState(pos);
            var rule=LegacyBlockGeometryRegistry.rule(current);
            if(rule==null||!rule.mimic())return -1;
            if(!current.hasProperty(ConvertedLegacyBlock.LEGACY_META))return -2;
            var variant=rule.variant(ConvertedLegacyBlock.legacyMeta(current));
            return variant==null?-2:variant.copyFace();
        },(pos,face)->pos.relative(DIRECTIONS[face]),256,
                pos->window.contains(pos.getX(),pos.getY(),pos.getZ()));
        if(target==null)return null;BlockState result=world.getBlockState(target);
        // A waterlogged solid is still a material source. Checking its fluid state would
        // incorrectly reject waterlogged stairs/slabs/panes as though the block were water.
        return result.isAir()||result.is(Blocks.WATER)?null:new Target(target,result);
    }

    private static Material[] materials(BlockStateModel model){
        Material[] cached=MATERIALS.get(model);if(cached!=null)return cached;
        Material[] result=new Material[6];
        for(var part:model.collectParts(RandomSource.create(0))){
            for(int group=-1;group<6;group++)for(var quad:part.getQuads(group<0?null:DIRECTIONS[group])){
                int side=quad.direction().ordinal();if(side>=0&&side<6&&result[side]==null)result[side]=new Material(quad.sprite(),quad.tintIndex());
            }
        }
        for(int side=0;side<6;side++)if(result[side]==null)result[side]=new Material(model.particleIcon(),-1);
        if(MATERIALS.size()<4096)MATERIALS.putIfAbsent(model,result);return result;
    }
    private static List<LegacyGeometry.Face> surfaces(List<LegacyGeometry.Box> boxes){
        var cached=SURFACES.get(boxes);if(cached!=null)return cached;var result=LegacyGeometry.surfaces(boxes);
        if(SURFACES.size()<4096)SURFACES.putIfAbsent(boxes,result);return result;
    }
    private static boolean boundary(LegacyGeometry.Face face){
        int axis=face.side()<2?1:face.side()<4?2:0;double expected=(face.side()&1)==0?0:1;
        for(int vertex=0;vertex<4;vertex++)if(face.positions().get(vertex*3+axis)!=expected)return false;return true;
    }
}
