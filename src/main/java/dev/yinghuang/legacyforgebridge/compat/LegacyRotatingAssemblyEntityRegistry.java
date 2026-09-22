package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyRotatingAssemblyEntityPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRotatingAssemblyEntity;
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

/** Runtime catalogue for source-proven large rotating legacy Entity assemblies. */
public final class LegacyRotatingAssemblyEntityRegistry {
    public enum Adapter { VARIABLE_Z_RADIAL, FLUID_X_RADIAL }
    public record Cuboid(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ,float xRot,float yRot,float zRot,boolean mirror){
        public Cuboid{
            if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0
                    ||!finite(x,y,z,pivotX,pivotY,pivotZ,xRot,yRot,zRot))
                throw new IllegalArgumentException("Invalid rotating assembly cuboid");
        }
    }
    public record Rule(Identifier id,String legacyModId,int legacyNumericId,int trackingRange,int updateFrequency,boolean velocityUpdates,
                       Adapter adapter,float modelScale,List<Cuboid> staticParts,List<Cuboid> repeatedPrimary,List<Cuboid> repeatedSecondary,
                       int directionWatcher,int sizeWatcher,int countWatcher,int textureWatcher,int reverseWatcher,
                       int directionDefault,int sizeDefault,int sizeMin,int sizeMax,int countDefault,int textureDefault,int reverseDefault,
                       int countBase,int countMax,int fixedRepeatCount,float secondaryPhaseDegrees,List<Identifier> textures,
                       boolean physicalCollision,boolean playerAttackRemoves,boolean randomInitialPhase){
        public Rule{
            staticParts=List.copyOf(staticParts);repeatedPrimary=List.copyOf(repeatedPrimary);
            repeatedSecondary=List.copyOf(repeatedSecondary);textures=List.copyOf(textures);
            if(id==null||legacyModId==null||legacyModId.isBlank()||legacyNumericId<0||trackingRange<=0||updateFrequency<=0
                    ||adapter==null||modelScale<=0F||staticParts.isEmpty()||repeatedPrimary.isEmpty()
                    ||directionWatcher<0||sizeWatcher<0||directionDefault<0||sizeDefault<sizeMin||sizeDefault>sizeMax
                    ||sizeMin<=0||sizeMax<sizeMin||textures.isEmpty()||!physicalCollision||!playerAttackRemoves||!randomInitialPhase)
                throw new IllegalArgumentException("Invalid rotating assembly rule");
            if(adapter==Adapter.VARIABLE_Z_RADIAL&&(countWatcher<0||textureWatcher<0||countBase<=0||countMax<countBase||textures.size()<2))
                throw new IllegalArgumentException("Incomplete variable radial assembly rule");
            if(adapter==Adapter.FLUID_X_RADIAL&&(reverseWatcher<0||fixedRepeatCount<=1))
                throw new IllegalArgumentException("Incomplete fluid radial assembly rule");
        }
        public int trackingChunks(){return trackingRange/16+(trackingRange%16==0?0:1);}
    }
    private record RemoteKey(String modId,int entityId){
        private RemoteKey{if(modId==null||modId.isBlank()||entityId<0)throw new IllegalArgumentException("Invalid rotating assembly remote key");}
    }

    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private static final Map<Identifier,EntityType<ConvertedLegacyRotatingAssemblyEntity>> TYPES=new ConcurrentHashMap<>();
    private static final Map<RemoteKey,Rule> REMOTE=new ConcurrentHashMap<>();
    private static final Set<String> LOADED=ConcurrentHashMap.newKeySet();
    private LegacyRotatingAssemblyEntityRegistry(){}

    public static void loadMod(String modId){
        if(modId==null||modId.isBlank()||!LOADED.add(modId))return;
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if(container==null){LOADED.remove(modId);return;}
        var path=container.findPath(LegacyRotatingAssemblyEntityPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            if(integer(root,"schemaVersion",-1)!=1||!bool(root,"runtimeImplementationWired"))return;
            String legacyModId=string(root,"legacyModId",null);if(legacyModId==null)return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int loaded=0;
            for(JsonElement element:rules){
                if(!element.isJsonObject())continue;Rule rule=parse(element.getAsJsonObject(),legacyModId);
                if(rule==null||!modId.equals(rule.id().getNamespace()))continue;install(rule);loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded rotating assembly Entity rules: mod={}, rules={}",modId,loaded);
        }catch(Exception exception){
            LOADED.remove(modId);throw new IllegalStateException("Failed to load rotating assembly Entity rules for "+modId,exception);
        }
    }

    public static List<Rule> rules(String namespace){
        return RULES.values().stream().filter(r->namespace.equals(r.id().getNamespace())).sorted(Comparator.comparing(r->r.id().toString())).toList();
    }
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    public static Rule remoteSpawnRule(String legacyModId,int legacyNumericId){
        return legacyModId==null||legacyNumericId<0?null:REMOTE.get(new RemoteKey(legacyModId,legacyNumericId));
    }
    public static EntityType<ConvertedLegacyRotatingAssemblyEntity> type(Identifier id){return id==null?null:TYPES.get(id);}
    public static ConvertedLegacyRotatingAssemblyEntity create(Identifier id,Level level){
        Rule rule=rule(id);EntityType<ConvertedLegacyRotatingAssemblyEntity> type=type(id);
        return rule==null||type==null||level==null?null:new ConvertedLegacyRotatingAssemblyEntity(type,level,rule);
    }

    private static synchronized void install(Rule rule){
        Rule prior=RULES.putIfAbsent(rule.id(),rule);if(prior!=null&&!prior.equals(rule))throw new IllegalStateException("Conflicting rotating assembly rule "+rule.id());
        RemoteKey key=new RemoteKey(rule.legacyModId(),rule.legacyNumericId());
        Rule remotePrior=REMOTE.putIfAbsent(key,rule);if(remotePrior!=null&&!remotePrior.equals(rule))throw new IllegalStateException("Conflicting rotating assembly remote identity "+key);
        if(TYPES.containsKey(rule.id()))return;
        if(BuiltInRegistries.ENTITY_TYPE.containsKey(rule.id()))throw new IllegalStateException("Rotating assembly id already registered: "+rule.id());
        ResourceKey<EntityType<?>> resourceKey=ResourceKey.create(Registries.ENTITY_TYPE,rule.id());
        EntityType<ConvertedLegacyRotatingAssemblyEntity> type=EntityType.Builder.<ConvertedLegacyRotatingAssemblyEntity>of(
                (entityType,level)->new ConvertedLegacyRotatingAssemblyEntity(entityType,level,rule),MobCategory.MISC)
                .sized(1F,1F).clientTrackingRange(rule.trackingChunks()).updateInterval(rule.updateFrequency()).build(resourceKey);
        Registry.register(BuiltInRegistries.ENTITY_TYPE,resourceKey,type);TYPES.put(rule.id(),type);
    }

    private static Rule parse(JsonObject value,String legacyModId){
        try{
            Identifier id=Identifier.parse(required(value,"id"));JsonArray rawTextures=value.getAsJsonArray("textures");if(rawTextures==null)return null;
            List<Identifier> textures=new ArrayList<>();for(JsonElement e:rawTextures)textures.add(Identifier.parse(e.getAsString()));
            return new Rule(id,legacyModId,integer(value,"legacyNumericId",-1),integer(value,"trackingRange",0),integer(value,"updateFrequency",0),
                    bool(value,"velocityUpdates"),Adapter.valueOf(required(value,"adapter")),decimal(value,"modelScale"),
                    parts(value,"staticParts"),parts(value,"repeatedPrimary"),parts(value,"repeatedSecondary"),
                    integer(value,"directionWatcher",-1),integer(value,"sizeWatcher",-1),integer(value,"countWatcher",-1),
                    integer(value,"textureWatcher",-1),integer(value,"reverseWatcher",-1),
                    integer(value,"directionDefault",0),integer(value,"sizeDefault",0),integer(value,"sizeMin",0),integer(value,"sizeMax",0),
                    integer(value,"countDefault",0),integer(value,"textureDefault",0),integer(value,"reverseDefault",0),
                    integer(value,"countBase",0),integer(value,"countMax",0),integer(value,"fixedRepeatCount",0),
                    decimal(value,"secondaryPhaseDegrees"),textures,bool(value,"physicalCollision"),bool(value,"playerAttackRemoves"),bool(value,"randomInitialPhase"));
        }catch(RuntimeException invalid){return null;}
    }
    private static List<Cuboid> parts(JsonObject root,String key){
        JsonArray raw=root.getAsJsonArray(key);if(raw==null)return List.of();List<Cuboid> out=new ArrayList<>();
        for(JsonElement e:raw){JsonObject c=e.getAsJsonObject();out.add(new Cuboid(required(c,"field"),integer(c,"u",-1),integer(c,"v",-1),
                decimal(c,"x"),decimal(c,"y"),decimal(c,"z"),integer(c,"width",0),integer(c,"height",0),integer(c,"depth",0),
                decimal(c,"pivotX"),decimal(c,"pivotY"),decimal(c,"pivotZ"),decimal(c,"xRot"),decimal(c,"yRot"),decimal(c,"zRot"),bool(c,"mirror")));}
        return List.copyOf(out);
    }
    private static String required(JsonObject o,String k){String value=string(o,k,null);if(value==null)throw new IllegalArgumentException("Missing "+k);return value;}
    private static String string(JsonObject o,String k,String fallback){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsString():fallback;}
    private static int integer(JsonObject o,String k,int fallback){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsInt():fallback;}
    private static float decimal(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsFloat();}
    private static boolean bool(JsonObject o,String k){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    private static boolean finite(float... values){for(float v:values)if(!Float.isFinite(v))return false;return true;}
    static synchronized void clearForTests(){RULES.clear();TYPES.clear();REMOTE.clear();LOADED.clear();}
}
