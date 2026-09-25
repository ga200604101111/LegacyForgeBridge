package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyInertModelBlockPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyInertModelBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime catalogue for source-proven inert fixed-cuboid legacy BlockContainer presentation. */
public final class LegacyInertModelBlockRegistry {
    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private static final Map<Identifier,BlockEntityType<ConvertedLegacyInertModelBlockEntity>> TYPES=new ConcurrentHashMap<>();
    private LegacyInertModelBlockRegistry() { }

    public record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ) {
        public Bounds { if(minX<0||minY<0||minZ<0||maxX>1||maxY>1||maxZ>1||minX>=maxX||minY>=maxY||minZ>=maxZ)throw new IllegalArgumentException("Invalid inert bounds"); }
    }
    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth) {
        public Cuboid { if(u<0||v<0||width<=0||height<=0||depth<=0||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Invalid inert cuboid"); }
    }
    public record Presentation(Identifier texture,int modelTextureWidth,int modelTextureHeight,List<Cuboid> cuboids,
                               float translateX,float translateY,float translateZ,int metadataMask,float yawDegreesPerMeta,
                               float inventoryTranslateY,float inventoryScale) {
        public Presentation { cuboids=List.copyOf(cuboids);if(texture==null||modelTextureWidth<=0||modelTextureHeight<=0||cuboids.isEmpty()||metadataMask<0||metadataMask>15||!Float.isFinite(yawDegreesPerMeta)||!Float.isFinite(inventoryScale)||inventoryScale<=0)throw new IllegalArgumentException("Invalid inert presentation"); }
    }
    public record Rule(Identifier id,Bounds bounds,int lightEmission,String orientation,Presentation presentation) {
        public Rule { if(id==null||bounds==null||lightEmission<0||lightEmission>15||orientation==null||orientation.isBlank()||presentation==null)throw new IllegalArgumentException("Invalid inert rule"); }
    }

    public static void loadMod(String modId){
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null)return;
        var path=container.findPath(LegacyInertModelBlockPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();JsonArray values=root.getAsJsonArray("rules");if(values==null)return;int loaded=0;
            for(JsonElement element:values){if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();if(!bool(value,"topologyProofComplete")||!bool(value,"presentationProofComplete")||!bool(value,"runtimeComplete"))continue;Rule rule=parse(value);if(rule==null||!rule.id().getNamespace().equals(modId))continue;Rule prior=RULES.putIfAbsent(rule.id(),rule);if(prior!=null&&!prior.equals(rule))throw new IllegalStateException("Conflicting inert model rule for "+rule.id());loaded++;}
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted inert model runtime rules: mod={}, blocks={}",modId,loaded);
        }catch(Exception exception){LegacyForgeBridge.LOGGER.error("Failed to load converted inert model rules for {}",modId,exception);}
    }
    public static boolean hasRule(Identifier id){return id!=null&&RULES.containsKey(id);}
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    public static Rule requireRule(Identifier id){Rule rule=rule(id);if(rule==null)throw new IllegalStateException("No inert model rule for "+id);return rule;}
    public static Rule requireRule(Block block){return requireRule(BuiltInRegistries.BLOCK.getKey(block));}
    public static List<Rule> rules(String namespace){return RULES.values().stream().filter(rule->rule.id().getNamespace().equals(namespace)).sorted(java.util.Comparator.comparing(rule->rule.id().toString())).toList();}

    public static synchronized void registerType(Identifier id,Block block){
        if(!RULES.containsKey(id)||TYPES.containsKey(id))return;
        if(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)){
            @SuppressWarnings("unchecked") BlockEntityType<ConvertedLegacyInertModelBlockEntity> existing=(BlockEntityType<ConvertedLegacyInertModelBlockEntity>)BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);TYPES.put(id,existing);return;
        }
        BlockEntityType<ConvertedLegacyInertModelBlockEntity> type=FabricBlockEntityTypeBuilder.create(ConvertedLegacyInertModelBlockEntity::new,block).build();Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,type);TYPES.put(id,type);
    }
    public static BlockEntityType<ConvertedLegacyInertModelBlockEntity> type(Identifier id){return id==null?null:TYPES.get(id);}
    public static BlockEntityType<ConvertedLegacyInertModelBlockEntity> requireType(Block block){BlockEntityType<ConvertedLegacyInertModelBlockEntity> type=type(BuiltInRegistries.BLOCK.getKey(block));if(type==null)throw new IllegalStateException("No inert BlockEntityType for "+BuiltInRegistries.BLOCK.getKey(block));return type;}

    private static Rule parse(JsonObject value){
        try{Identifier id=Identifier.parse(required(value,"id"));JsonObject bounds=object(value,"bounds"),presentation=object(value,"presentation");if(bounds==null||presentation==null)return null;JsonArray boxes=presentation.getAsJsonArray("cuboids");if(boxes==null||boxes.isEmpty())return null;List<Cuboid> cuboids=new ArrayList<>();for(JsonElement element:boxes){if(!element.isJsonObject())return null;JsonObject box=element.getAsJsonObject();cuboids.add(new Cuboid(integer(box,"u"),integer(box,"v"),decimal(box,"worldX"),decimal(box,"worldY"),decimal(box,"worldZ"),integer(box,"width"),integer(box,"height"),integer(box,"depth")));}
            return new Rule(id,new Bounds(decimal(bounds,"minX"),decimal(bounds,"minY"),decimal(bounds,"minZ"),decimal(bounds,"maxX"),decimal(bounds,"maxY"),decimal(bounds,"maxZ")),integer(value,"modernLightEmission"),required(value,"orientation"),new Presentation(Identifier.parse(required(presentation,"texture")),integer(presentation,"modelTextureWidth"),integer(presentation,"modelTextureHeight"),cuboids,decimal(presentation,"translateX"),decimal(presentation,"translateY"),decimal(presentation,"translateZ"),integer(presentation,"metadataMask"),decimal(presentation,"yawDegreesPerMeta"),decimal(presentation,"inventoryTranslateY"),decimal(presentation,"inventoryScale")));
        }catch(RuntimeException invalid){return null;}
    }
    private static JsonObject object(JsonObject value,String key){JsonElement e=value.get(key);return e!=null&&e.isJsonObject()?e.getAsJsonObject():null;}
    private static String required(JsonObject value,String key){JsonElement e=value.get(key);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return e.getAsString();}
    private static int integer(JsonObject value,String key){JsonElement e=value.get(key);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return e.getAsInt();}
    private static float decimal(JsonObject value,String key){JsonElement e=value.get(key);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return e.getAsFloat();}
    private static boolean bool(JsonObject value,String key){JsonElement e=value.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    static synchronized void clearForTests(){RULES.clear();TYPES.clear();}
}
