package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyRadialTesrPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRadialModelBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime catalogue for source-proven repeated radial TESR base presentation. */
public final class LegacyRadialModelBlockRegistry {
    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth,float pivotX,float pivotY,float pivotZ){
        public Cuboid{if(u<0||v<0||width<=0||height<=0||depth<=0||!finite(x,y,z,pivotX,pivotY,pivotZ))throw new IllegalArgumentException("Invalid radial cuboid");}
    }
    public record Pose(float xRot,float yRot,float zRot){public Pose{if(!finite(xRot,yRot,zRot))throw new IllegalArgumentException("Invalid radial pose");}}
    public record Rule(Identifier id,Identifier texture,int textureWidth,int textureHeight,Cuboid cuboid,List<Pose> poses,
                       float translateX,float translateY,float translateZ,int metadataMask,float yawDegreesPerMeta,
                       int lightEmission,float inventoryTranslateY,float inventoryScale,int conditionalModelCalls,boolean sourceComplete){
        public Rule{
            poses=List.copyOf(poses);
            if(id==null||texture==null||textureWidth<=0||textureHeight<=0||cuboid==null||poses.size()<2||poses.size()>16
                    ||!finite(translateX,translateY,translateZ,yawDegreesPerMeta,inventoryTranslateY,inventoryScale)||inventoryScale<=0F
                    ||metadataMask<0||metadataMask>15||lightEmission<0||lightEmission>15||conditionalModelCalls<0)
                throw new IllegalArgumentException("Invalid radial model rule");
            if(sourceComplete!=(conditionalModelCalls==0))throw new IllegalArgumentException("Radial completion flag mismatch");
        }
    }
    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private static final Map<Identifier,BlockEntityType<ConvertedLegacyRadialModelBlockEntity>> TYPES=new ConcurrentHashMap<>();
    private static final Set<String> LOADED=ConcurrentHashMap.newKeySet();
    private LegacyRadialModelBlockRegistry(){}

    public static void loadMod(String modId){
        if(modId==null||modId.isBlank()||!LOADED.add(modId))return;
        var container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null){LOADED.remove(modId);return;}
        var path=container.findPath(LegacyRadialTesrPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();if(integer(root,"schemaVersion",-1)!=1)return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int loaded=0;
            for(JsonElement element:rules){if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                if(!bool(value,"basePresentationRuntimeComplete"))continue;Rule rule=parse(value);
                if(rule==null||!modId.equals(rule.id().getNamespace()))continue;Rule old=RULES.putIfAbsent(rule.id(),rule);
                if(old!=null&&!old.equals(rule))throw new IllegalStateException("Conflicting radial model rule "+rule.id());loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted radial model rules: mod={}, blocks={}",modId,loaded);
        }catch(Exception exception){LOADED.remove(modId);throw new IllegalStateException("Failed to load radial model rules for "+modId,exception);}
    }

    public static boolean hasRule(Identifier id){return id!=null&&RULES.containsKey(id);}
    public static Rule requireRule(Identifier id){Rule rule=id==null?null:RULES.get(id);if(rule==null)throw new IllegalStateException("Missing radial model rule "+id);return rule;}
    public static Rule requireRule(Block block){return requireRule(BuiltInRegistries.BLOCK.getKey(block));}
    public static List<Rule> rules(String namespace){return RULES.values().stream().filter(r->namespace.equals(r.id().getNamespace())).sorted(Comparator.comparing(r->r.id().toString())).toList();}

    public static synchronized void registerType(Identifier id,Block block){
        if(!RULES.containsKey(id)||TYPES.containsKey(id))return;
        if(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)){
            @SuppressWarnings("unchecked") BlockEntityType<ConvertedLegacyRadialModelBlockEntity> existing=(BlockEntityType<ConvertedLegacyRadialModelBlockEntity>)BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            TYPES.put(id,existing);return;
        }
        BlockEntityType<ConvertedLegacyRadialModelBlockEntity> type=FabricBlockEntityTypeBuilder.create(ConvertedLegacyRadialModelBlockEntity::new,block).build();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,type);TYPES.put(id,type);
    }
    public static BlockEntityType<ConvertedLegacyRadialModelBlockEntity> requireType(Block block){
        Identifier id=BuiltInRegistries.BLOCK.getKey(block);BlockEntityType<ConvertedLegacyRadialModelBlockEntity> type=TYPES.get(id);
        if(type==null)throw new IllegalStateException("Missing radial BlockEntityType "+id);return type;
    }
    public static BlockEntityType<ConvertedLegacyRadialModelBlockEntity> type(Identifier id){return id==null?null:TYPES.get(id);}

    private static Rule parse(JsonObject v){
        try{
            Identifier id=Identifier.parse(required(v,"id")),texture=Identifier.parse(required(v,"texture"));JsonObject c=v.getAsJsonObject("cuboid");if(c==null)return null;
            Cuboid cube=new Cuboid(integer(c,"u",-1),integer(c,"v",-1),decimal(c,"x"),decimal(c,"y"),decimal(c,"z"),
                    integer(c,"width",0),integer(c,"height",0),integer(c,"depth",0),decimal(c,"pivotX"),decimal(c,"pivotY"),decimal(c,"pivotZ"));
            JsonArray raw=v.getAsJsonArray("poses");if(raw==null)return null;List<Pose> poses=new ArrayList<>();
            for(JsonElement e:raw){JsonObject p=e.getAsJsonObject();poses.add(new Pose(decimal(p,"xRot"),decimal(p,"yRot"),decimal(p,"zRot")));}
            return new Rule(id,texture,integer(v,"textureWidth",0),integer(v,"textureHeight",0),cube,poses,
                    decimal(v,"translateX"),decimal(v,"translateY"),decimal(v,"translateZ"),integer(v,"metadataMask",-1),decimal(v,"yawDegreesPerMeta"),
                    integer(v,"modernLightEmission",-1),decimal(v,"inventoryTranslateY"),decimal(v,"inventoryScale"),
                    integer(v,"conditionalModelCalls",-1),bool(v,"sourcePresentationComplete"));
        }catch(RuntimeException invalid){return null;}
    }
    private static String required(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsString();}
    private static int integer(JsonObject o,String k,int d){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsInt():d;}
    private static float decimal(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsFloat();}
    private static boolean bool(JsonObject o,String k){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    private static boolean finite(float... values){for(float v:values)if(!Float.isFinite(v))return false;return true;}
    static synchronized void clearForTests(){RULES.clear();TYPES.clear();LOADED.clear();}
}
