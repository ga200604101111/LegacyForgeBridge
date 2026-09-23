package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Source-gated shapes; preserves the block registry identity and the opaque legacy metadata. */
public final class LegacyBlockGeometryRegistry {
    private static volatile Map<String,LegacyGeometrySpec.Rule> rules;
    private static final Map<List<LegacyGeometry.Box>,VoxelShape> SHAPES=new ConcurrentHashMap<>();
    private static final Direction[] HORIZONTAL={Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private static final Direction[] LEGACY_DIRECTIONS={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private LegacyBlockGeometryRegistry() { }
    public static Map<String,LegacyGeometrySpec.Rule> all(){
        var current=rules;if(current==null)synchronized(LegacyBlockGeometryRegistry.class){if(rules==null)rules=load();current=rules;}return current;
    }
    public static LegacyGeometrySpec.Rule rule(Identifier id){return id==null?null:all().get(id.toString());}
    public static LegacyGeometrySpec.Rule rule(BlockState state){return state==null?null:rule(BuiltInRegistries.BLOCK.getKey(state.getBlock()));}
    public static BlockBehaviour.Properties properties(Identifier id,BlockBehaviour.Properties properties){
        var rule=rule(id);if(rule==null)return properties;
        properties=properties.dynamicShape();
        if(rule.renderOffset()==LegacyGeometrySpec.RenderOffset.XYZ)properties=properties.offsetType(BlockBehaviour.OffsetType.XYZ);
        if(!rule.opaque()||!rule.family().equals("box")||rule.variants().values().stream().anyMatch(v->!v.bounds().full()))properties=properties.noOcclusion();
        return properties;
    }
    public static VoxelShape shape(Identifier id,BlockState state,BlockGetter world,BlockPos pos,boolean collision){
        var rule=rule(id);if(rule==null||!state.hasProperty(ConvertedLegacyBlock.LEGACY_META))return null;
        int meta=ConvertedLegacyBlock.legacyMeta(state);var variant=rule.variant(meta);if(variant==null)return null;
        if(collision){
            if(variant.collision().equals("empty"))return Shapes.empty();
            if(variant.collision().equals("full"))return shape(List.of(LegacyGeometry.FULL));
            if(variant.collision().equals("unsupported"))return null;
        }
        return shape(boxes(rule,meta,world,pos,state));
    }
    public static List<LegacyGeometry.Box> boxes(LegacyGeometrySpec.Rule rule,int meta,BlockGetter world,BlockPos pos,BlockState self){
        var v=rule.variant(meta);if(v==null)return List.of();
        if(rule.stairs())return LegacyGeometry.stairs(meta,LegacyGeometry.stairMask(meta,(dx,dz)->stairMeta(world.getBlockState(pos.offset(dx,0,dz)))));
        if(rule.pane())return LegacyGeometry.panes(paneMask(world,pos,self));
        return List.of(v.bounds());
    }
    /** Presentation-only dynamic boxes. Collision/outline stay on source-proven variant bounds. */
    public static List<LegacyGeometry.Box> renderBoxes(LegacyGeometrySpec.Rule rule,int meta,BlockGetter world,BlockPos pos,BlockState self){
        if(!rule.connected())return boxes(rule,meta,world,pos,self);
        var c=rule.connectedCuboid();if(c==null||c.axisLocked()&&(meta<0||meta>5))return List.of();
        List<LegacyGeometry.Box> out=new ArrayList<>();out.add(LegacyGeometry.connectedCore(c,meta));
        for(int direction=0;direction<6;direction++){
            if(c.axisLocked()&&(direction==meta||direction==(meta^1)))continue;
            BlockPos target=pos.relative(LEGACY_DIRECTIONS[direction]);BlockState neighbour=world.getBlockState(target);
            var neighbourRule=rule(neighbour);LegacyGeometrySpec.ConnectedCuboid arm=c;
            boolean linked=false;
            if(neighbourRule!=null&&neighbourRule.connected()&&neighbour.hasProperty(ConvertedLegacyBlock.LEGACY_META)){
                var other=neighbourRule.connectedCuboid();int otherMeta=ConvertedLegacyBlock.legacyMeta(neighbour);
                if(!(c.axisLocked()&&!other.axisLocked())&&(!c.sameMetadataOnly()||meta==otherMeta)){
                    linked=true;if(c.size()>other.size())arm=other;
                }
            }else if(c.connectFullBlocks()&&neighbour.canOcclude()&&neighbour.isCollisionShapeFullBlock(world,target))linked=true;
            else if(c.connectWood()&&woodLike(neighbour))linked=true;
            else if(c.connectRock()&&rockLike(neighbour))linked=true;
            if(!linked)continue;
            LegacyGeometry.Box extension=LegacyGeometry.connectedArm(arm.minWidth(),arm.maxWidth(),arm.minHeight(),arm.maxHeight(),c.axisLocked(),meta,direction);
            if(extension!=null)out.add(extension);
        }
        return List.copyOf(out);
    }
    private static boolean woodLike(BlockState state){
        return state.is(BlockTags.LOGS)||state.is(BlockTags.PLANKS);
    }
    private static boolean rockLike(BlockState state){
        return state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(BlockTags.BASE_STONE_NETHER);
    }
    public static int paneMask(BlockGetter world,BlockPos pos,BlockState self){
        int mask=0;
        for(int i=0;i<4;i++){
            BlockPos target=pos.relative(HORIZONTAL[i]);BlockState neighbour=world.getBlockState(target);
            var source=rule(neighbour);
            // The inherited 1.7 pane predicate accepts this pane, opaque blocks and glass.
            // It does not connect every source pane family merely because both are thin.
            boolean opaque=source==null?neighbour.canOcclude()&&neighbour.isCollisionShapeFullBlock(world,target):source.opaque();
            if(neighbour.getBlock()==self.getBlock()||opaque||neighbour.is(Blocks.GLASS)||neighbour.is(Blocks.WHITE_STAINED_GLASS)
                    ||BuiltInRegistries.BLOCK.getKey(neighbour.getBlock()).getNamespace().equals("minecraft")
                    &&BuiltInRegistries.BLOCK.getKey(neighbour.getBlock()).getPath().endsWith("_stained_glass"))mask|=1<<i;
        }
        return mask;
    }
    private static int stairMeta(BlockState state){
        var rule=rule(state);
        if(rule!=null&&rule.stairs()&&state.hasProperty(ConvertedLegacyBlock.LEGACY_META))return ConvertedLegacyBlock.legacyMeta(state);
        if(state.getBlock() instanceof StairBlock){
            int facing=switch(state.getValue(StairBlock.FACING)){case EAST->0;case WEST->1;case SOUTH->2;case NORTH->3;default->-1;};
            return facing<0?-1:facing|(state.getValue(StairBlock.HALF)==Half.TOP?4:0);
        }
        return -1;
    }
    private static VoxelShape shape(List<LegacyGeometry.Box> boxes){
        VoxelShape cached=SHAPES.get(boxes);if(cached!=null)return cached;VoxelShape result=Shapes.empty();
        for(var b:boxes)result=Shapes.or(result,Shapes.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()));
        result=result.optimize();if(SHAPES.size()<4096)SHAPES.putIfAbsent(boxes,result);return result;
    }
    private static Map<String,LegacyGeometrySpec.Rule> load(){
        Map<String,LegacyGeometrySpec.Rule> result=new LinkedHashMap<>();Set<String> conflicts=new HashSet<>();
        for(var mod:FabricLoader.getInstance().getAllMods()){
            var path=mod.findPath(LegacyGeometrySpec.PATH);if(path.isEmpty())continue;
            try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
                var parsed=LegacyGeometrySpec.parse(JsonParser.parseReader(reader).getAsJsonObject());
                for(var e:parsed.entrySet()){
                    String[] id=e.getKey().split(":",2);
                    if(mod.findPath("assets/"+id[0]+"/blockstates/"+id[1]+".json").isEmpty()||conflicts.contains(e.getKey()))continue;
                    var old=result.putIfAbsent(e.getKey(),e.getValue());if(old!=null&&!old.equals(e.getValue())){result.remove(e.getKey());conflicts.add(e.getKey());}
                }
            }catch(Exception invalid){LegacyForgeBridge.LOGGER.warn("Ignoring invalid optional block geometry for {}: {}",mod.getMetadata().getId(),invalid.toString());}
        }
        LegacyForgeBridge.LOGGER.info("Loaded converted native block geometry: rules={}, conflictingIdentities={}",result.size(),conflicts.size());
        return Map.copyOf(result);
    }
}
