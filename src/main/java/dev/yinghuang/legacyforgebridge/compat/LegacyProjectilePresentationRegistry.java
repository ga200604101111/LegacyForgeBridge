package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyProjectilePresentationPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRemoteProjectile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime catalogue for source-proven remote FML projectile presentation carriers. */
public final class LegacyProjectilePresentationRegistry {
    public enum Adapter { THROWN_ITEM, ORIENTED_ITEM }
    public enum BaseFamily { ARROW, THROWABLE }

    public record Rule(Identifier id,String legacyModId,int legacyNumericId,int trackingRange,int updateFrequency,
                       boolean velocityUpdates,float width,float height,BaseFamily baseFamily,Adapter adapter,
                       Identifier itemId,int metadataWatcherIndex,int metadataWatcherWireType,int metadataOffset,
                       int defaultItemMetadata,Identifier fixedTexture) {
        public Rule {
            if(id==null||legacyModId==null||legacyModId.isBlank()||legacyNumericId<0||trackingRange<=0||updateFrequency<=0
                    ||!velocityUpdates||!(width>0F)||!(height>0F)||!finite(width,height)||baseFamily==null||adapter==null||itemId==null
                    ||metadataWatcherIndex<-1||metadataWatcherWireType<-1||metadataWatcherWireType>6||defaultItemMetadata<0)
                throw new IllegalArgumentException("Invalid remote projectile rule");
            if(metadataWatcherIndex<0&&(metadataWatcherWireType!=-1||metadataOffset!=0||defaultItemMetadata!=0))
                throw new IllegalArgumentException("Invalid unselected projectile metadata rule");
        }
        public int trackingChunks(){return trackingRange/16+(trackingRange%16==0?0:1);}
        public int itemMetadata(Object watcherValue){
            if(metadataWatcherIndex<0)return defaultItemMetadata;
            if(!(watcherValue instanceof Number number))return defaultItemMetadata;
            return Math.max(0,number.intValue()+metadataOffset);
        }
    }
    private record RemoteKey(String modId,int entityId){
        private RemoteKey{if(modId==null||modId.isBlank()||entityId<0)throw new IllegalArgumentException("Invalid projectile remote key");}
    }

    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private static final Map<Identifier,EntityType<ConvertedLegacyRemoteProjectile>> TYPES=new ConcurrentHashMap<>();
    private static final Map<RemoteKey,Rule> REMOTE=new ConcurrentHashMap<>();
    private static final Set<String> LOADED=ConcurrentHashMap.newKeySet();
    private LegacyProjectilePresentationRegistry(){}

    public static void loadMod(String modId){
        if(modId==null||modId.isBlank()||!LOADED.add(modId))return;
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if(container==null){LOADED.remove(modId);return;}
        var path=container.findPath(LegacyProjectilePresentationPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            if(integer(root,"schemaVersion",-1)!=1||!bool(root,"runtimeImplementationWired"))return;
            String legacyModId=string(root,"legacyModId",null);if(legacyModId==null)return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int loaded=0;
            for(JsonElement element:rules){
                if(!element.isJsonObject())continue;Rule rule=parse(element.getAsJsonObject(),legacyModId);
                if(rule==null||!modId.equals(rule.id().getNamespace()))continue;install(rule);loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted remote projectile presentation: mod={}, rules={}",modId,loaded);
        }catch(Exception exception){
            LOADED.remove(modId);throw new IllegalStateException("Failed to load remote projectile presentation for "+modId,exception);
        }
    }

    public static List<Rule> rules(String namespace){
        List<Rule> out=new ArrayList<>();for(Rule rule:RULES.values())if(namespace.equals(rule.id().getNamespace()))out.add(rule);
        out.sort(Comparator.comparing(value->value.id().toString()));return List.copyOf(out);
    }
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    public static Rule remoteSpawnRule(String legacyModId,int legacyNumericId){
        return legacyModId==null||legacyNumericId<0?null:REMOTE.get(new RemoteKey(legacyModId,legacyNumericId));
    }
    public static EntityType<ConvertedLegacyRemoteProjectile> type(Identifier id){return id==null?null:TYPES.get(id);}
    public static ConvertedLegacyRemoteProjectile create(Identifier id,Level level){
        Rule rule=rule(id);EntityType<ConvertedLegacyRemoteProjectile> type=type(id);
        return rule==null||type==null||level==null?null:new ConvertedLegacyRemoteProjectile(type,level,rule);
    }

    private static synchronized void install(Rule rule){
        Rule prior=RULES.putIfAbsent(rule.id(),rule);
        if(prior!=null&&!prior.equals(rule))throw new IllegalStateException("Conflicting remote projectile rule "+rule.id());
        RemoteKey key=new RemoteKey(rule.legacyModId(),rule.legacyNumericId());
        Rule remotePrior=REMOTE.putIfAbsent(key,rule);
        if(remotePrior!=null&&!remotePrior.equals(rule))throw new IllegalStateException("Conflicting remote projectile identity "+key);
        if(TYPES.containsKey(rule.id()))return;
        if(BuiltInRegistries.ENTITY_TYPE.containsKey(rule.id()))throw new IllegalStateException("Remote projectile id already registered: "+rule.id());
        ResourceKey<EntityType<?>> resourceKey=ResourceKey.create(Registries.ENTITY_TYPE,rule.id());
        EntityType<ConvertedLegacyRemoteProjectile> type=EntityType.Builder.<ConvertedLegacyRemoteProjectile>of(
                (entityType,level)->new ConvertedLegacyRemoteProjectile(entityType,level,rule), MobCategory.MISC)
                .sized(rule.width(),rule.height()).clientTrackingRange(rule.trackingChunks()).updateInterval(rule.updateFrequency()).build(resourceKey);
        Registry.register(BuiltInRegistries.ENTITY_TYPE,resourceKey,type);TYPES.put(rule.id(),type);
    }

    static Rule parseForTests(JsonObject value,String legacyModId){return parse(value,legacyModId);}
    private static Rule parse(JsonObject value,String legacyModId){
        try{
            Identifier id=Identifier.parse(required(value,"id")),item=Identifier.parse(required(value,"itemId"));
            Identifier texture=value.has("fixedTexture")?Identifier.parse(value.get("fixedTexture").getAsString()):null;
            return new Rule(id,legacyModId,integer(value,"legacyNumericId",-1),integer(value,"trackingRange",0),
                    integer(value,"updateFrequency",0),bool(value,"velocityUpdates"),decimal(value,"width"),decimal(value,"height"),
                    BaseFamily.valueOf(required(value,"baseFamily")),Adapter.valueOf(required(value,"adapter")),item,
                    integer(value,"metadataWatcherIndex",-1),integer(value,"metadataWatcherWireType",-1),
                    integer(value,"metadataOffset",0),integer(value,"defaultItemMetadata",0),texture);
        }catch(RuntimeException invalid){return null;}
    }
    private static String required(JsonObject o,String k){String value=string(o,k,null);if(value==null)throw new IllegalArgumentException("Missing "+k);return value;}
    private static String string(JsonObject o,String k,String fallback){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsString():fallback;}
    private static int integer(JsonObject o,String k,int fallback){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsInt():fallback;}
    private static float decimal(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsFloat();}
    private static boolean bool(JsonObject o,String k){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    private static boolean finite(float... values){for(float value:values)if(!Float.isFinite(value))return false;return true;}
    static synchronized void clearForTests(){RULES.clear();TYPES.clear();REMOTE.clear();LOADED.clear();}
}
