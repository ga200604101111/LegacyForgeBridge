package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyMetadataRotatingTesrPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyMetadataRotatingBlockEntity;
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

/** Runtime catalogue for source-proven metadata-speed rotating TESR presentation. */
public final class LegacyMetadataRotatingTesrRegistry {
    public record Cuboid(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ,boolean mirror){
        public Cuboid{if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0||!finite(x,y,z,pivotX,pivotY,pivotZ))throw new IllegalArgumentException("Invalid rotating cuboid");}
    }
    public record Rule(Identifier id,Identifier texture,int textureWidth,int textureHeight,List<Cuboid> cuboids,
                       String animatedPart,float translateX,float translateY,float translateZ,
                       int metadataMask,float degreesPerMetadataPerTick){
        public Rule{
            cuboids=List.copyOf(cuboids);
            if(id==null||texture==null||textureWidth<=0||textureHeight<=0||cuboids.isEmpty()||animatedPart==null||animatedPart.isBlank()
                    ||metadataMask<1||metadataMask>15||!finite(translateX,translateY,translateZ,degreesPerMetadataPerTick)||degreesPerMetadataPerTick<=0F
                    ||cuboids.stream().noneMatch(c->c.field().equals(animatedPart)))throw new IllegalArgumentException("Invalid rotating TESR rule");
        }
    }
    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private static final Map<Identifier,BlockEntityType<ConvertedLegacyMetadataRotatingBlockEntity>> TYPES=new ConcurrentHashMap<>();
    private static final Set<String> LOADED=ConcurrentHashMap.newKeySet();
    private LegacyMetadataRotatingTesrRegistry(){}

    public static void loadMod(String modId){
        if(modId==null||modId.isBlank()||!LOADED.add(modId))return;
        var container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null){LOADED.remove(modId);return;}
        var path=container.findPath(LegacyMetadataRotatingTesrPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();if(integer(root,"schemaVersion",-1)!=1)return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int loaded=0;
            for(JsonElement element:rules){if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                if(!bool(value,"runtimeComplete"))continue;Rule rule=parse(value);if(rule==null||!modId.equals(rule.id().getNamespace()))continue;
                Rule prior=RULES.putIfAbsent(rule.id(),rule);if(prior!=null&&!prior.equals(rule))throw new IllegalStateException("Conflicting metadata rotating rule "+rule.id());loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded metadata-speed rotating TESR rules: mod={}, blocks={}",modId,loaded);
        }catch(Exception exception){LOADED.remove(modId);throw new IllegalStateException("Failed to load metadata rotating TESR rules for "+modId,exception);}
    }

    public static boolean hasRule(Identifier id){return id!=null&&RULES.containsKey(id);}
    public static Rule requireRule(Identifier id){Rule rule=id==null?null:RULES.get(id);if(rule==null)throw new IllegalStateException("Missing metadata rotating rule "+id);return rule;}
    public static Rule requireRule(Block block){return requireRule(BuiltInRegistries.BLOCK.getKey(block));}
    public static List<Rule> rules(String namespace){return RULES.values().stream().filter(r->namespace.equals(r.id().getNamespace())).sorted(Comparator.comparing(r->r.id().toString())).toList();}
    public static synchronized void registerType(Identifier id,Block block){
        if(!RULES.containsKey(id)||TYPES.containsKey(id))return;
        if(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)){
            @SuppressWarnings("unchecked") BlockEntityType<ConvertedLegacyMetadataRotatingBlockEntity> existing=(BlockEntityType<ConvertedLegacyMetadataRotatingBlockEntity>)BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            TYPES.put(id,existing);return;
        }
        BlockEntityType<ConvertedLegacyMetadataRotatingBlockEntity> type=FabricBlockEntityTypeBuilder.create(ConvertedLegacyMetadataRotatingBlockEntity::new,block).build();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,type);TYPES.put(id,type);
    }
    public static BlockEntityType<ConvertedLegacyMetadataRotatingBlockEntity> type(Identifier id){return id==null?null:TYPES.get(id);}
    public static BlockEntityType<ConvertedLegacyMetadataRotatingBlockEntity> requireType(Block block){var type=type(BuiltInRegistries.BLOCK.getKey(block));if(type==null)throw new IllegalStateException("Missing metadata rotating BlockEntityType");return type;}

    private static Rule parse(JsonObject value){
        try{
            Identifier id=Identifier.parse(required(value,"id")),texture=Identifier.parse(required(value,"texture"));JsonArray raw=value.getAsJsonArray("cuboids");if(raw==null)return null;List<Cuboid> cuboids=new ArrayList<>();
            for(JsonElement element:raw){JsonObject c=element.getAsJsonObject();cuboids.add(new Cuboid(required(c,"field"),integer(c,"u",-1),integer(c,"v",-1),decimal(c,"x"),decimal(c,"y"),decimal(c,"z"),
                    integer(c,"width",0),integer(c,"height",0),integer(c,"depth",0),decimal(c,"pivotX"),decimal(c,"pivotY"),decimal(c,"pivotZ"),bool(c,"mirror")));}
            return new Rule(id,texture,integer(value,"textureWidth",0),integer(value,"textureHeight",0),cuboids,required(value,"animatedPart"),
                    decimal(value,"translateX"),decimal(value,"translateY"),decimal(value,"translateZ"),integer(value,"metadataMask",-1),decimal(value,"degreesPerMetadataPerTick"));
        }catch(RuntimeException invalid){return null;}
    }
    private static String required(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsString();}
    private static int integer(JsonObject o,String k,int d){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsInt():d;}
    private static float decimal(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsFloat();}
    private static boolean bool(JsonObject o,String k){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    private static boolean finite(float... values){for(float v:values)if(!Float.isFinite(v))return false;return true;}
    static synchronized void clearForTests(){RULES.clear();TYPES.clear();LOADED.clear();}
}
