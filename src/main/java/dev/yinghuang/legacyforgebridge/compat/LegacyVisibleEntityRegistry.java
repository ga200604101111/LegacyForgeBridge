package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVisibleEntityPresentationPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVisualEntity;
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

/** Runtime catalogue for source-proven visible legacy Entity presentation families. */
public final class LegacyVisibleEntityRegistry {
    public enum Adapter { SLIDE_PANEL, TINTED_CUSHION, TRAY_ITEMS }

    public record Part(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                       float pivotX,float pivotY,float pivotZ,float xRot,float yRot,float zRot,boolean mirror) {
        public Part {
            if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0
                    ||!finite(x,y,z,pivotX,pivotY,pivotZ,xRot,yRot,zRot))throw new IllegalArgumentException("Invalid visible Entity model part");
        }
    }
    public record TextureVariant(int value,Identifier texture,boolean translucent) {
        public TextureVariant { if(value<0||texture==null)throw new IllegalArgumentException("Invalid visible Entity texture variant"); }
    }
    public record Rule(Identifier id,String legacyModId,int legacyNumericId,int trackingRange,int updateFrequency,
                       boolean velocityUpdates,float width,float height,Adapter adapter,int modelTextureWidth,int modelTextureHeight,
                       List<Part> parts,Identifier fixedTexture,Map<String,Integer> watcherIndices,
                       List<TextureVariant> textureVariants,List<Integer> palette,int itemWatcherBase,int itemWatcherCount) {
        public Rule {
            if(id==null||legacyModId==null||legacyModId.isBlank()||legacyNumericId<0||trackingRange<=0||updateFrequency<=0
                    ||!velocityUpdates||!(width>0F)||!(height>0F)||!finite(width,height)
                    ||adapter==null||modelTextureWidth<=0||modelTextureHeight<=0||parts==null||parts.isEmpty()||parts.size()>64)
                throw new IllegalArgumentException("Invalid visible Entity rule");
            parts=List.copyOf(parts);watcherIndices=Map.copyOf(watcherIndices==null?Map.of():watcherIndices);
            textureVariants=List.copyOf(textureVariants==null?List.of():textureVariants);
            palette=List.copyOf(palette==null?List.of():palette);
            for(Integer index:watcherIndices.values())if(index==null||index<0||index>31)throw new IllegalArgumentException("Watcher index outside legacy range");
            switch(adapter){
                case SLIDE_PANEL -> {
                    if(parts.size()!=1||fixedTexture!=null||textureVariants.size()<2
                            ||!watcherIndices.keySet().containsAll(Set.of("direction","mirror","texture")))
                        throw new IllegalArgumentException("Incomplete slide-panel rule");
                }
                case TINTED_CUSHION -> {
                    if(parts.size()!=1||fixedTexture==null||palette.size()!=16||!watcherIndices.containsKey("color"))
                        throw new IllegalArgumentException("Incomplete tinted-cushion rule");
                    for(Integer rgb:palette)if(rgb==null||rgb<0||rgb>0xFFFFFF)throw new IllegalArgumentException("Invalid cushion palette");
                }
                case TRAY_ITEMS -> {
                    if(fixedTexture==null||itemWatcherBase<0||itemWatcherCount!=5||itemWatcherBase+itemWatcherCount>32)
                        throw new IllegalArgumentException("Incomplete tray rule");
                }
            }
        }
        public int trackingChunks(){return trackingRange/16+(trackingRange%16==0?0:1);}
        public TextureVariant textureVariant(int value){for(TextureVariant variant:textureVariants)if(variant.value()==value)return variant;return null;}
    }
    private record RemoteKey(String modId,int entityId) {
        private RemoteKey { if(modId==null||modId.isBlank()||entityId<0)throw new IllegalArgumentException("Invalid visible Entity remote key"); }
    }

    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private static final Map<Identifier,EntityType<ConvertedLegacyVisualEntity>> TYPES=new ConcurrentHashMap<>();
    private static final Map<RemoteKey,Rule> REMOTE=new ConcurrentHashMap<>();
    private static final Set<String> LOADED=ConcurrentHashMap.newKeySet();

    private LegacyVisibleEntityRegistry(){}

    public static void loadMod(String modId){
        if(modId==null||modId.isBlank()||!LOADED.add(modId))return;
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if(container==null){LOADED.remove(modId);return;}
        var path=container.findPath(LegacyVisibleEntityPresentationPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            if(integer(root,"schemaVersion",-1)!=1||!bool(root,"runtimeImplementationWired"))return;
            String legacyModId=string(root,"legacyModId",null);if(legacyModId==null)return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int loaded=0;
            for(JsonElement element:rules){
                if(!element.isJsonObject())continue;Rule rule=parse(element.getAsJsonObject(),legacyModId);
                if(rule==null||!modId.equals(rule.id().getNamespace()))continue;install(rule);loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted visible Entity runtime: mod={}, rules={}",modId,loaded);
        }catch(Exception exception){
            LOADED.remove(modId);throw new IllegalStateException("Failed to load visible Entity runtime for "+modId,exception);
        }
    }

    public static List<Rule> rules(String namespace){
        List<Rule> out=new ArrayList<>();for(Rule rule:RULES.values())if(namespace.equals(rule.id().getNamespace()))out.add(rule);
        out.sort(Comparator.comparing(v->v.id().toString()));return List.copyOf(out);
    }
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    public static Rule remoteSpawnRule(String modId,int entityId){return modId==null||entityId<0?null:REMOTE.get(new RemoteKey(modId,entityId));}
    public static EntityType<ConvertedLegacyVisualEntity> type(Identifier id){return id==null?null:TYPES.get(id);}

    public static ConvertedLegacyVisualEntity create(Identifier id,Level level){
        Rule rule=rule(id);EntityType<ConvertedLegacyVisualEntity> type=type(id);
        return rule==null||type==null||level==null?null:new ConvertedLegacyVisualEntity(type,level,rule);
    }

    private static synchronized void install(Rule rule){
        Rule prior=RULES.putIfAbsent(rule.id(),rule);
        if(prior!=null&&!prior.equals(rule))throw new IllegalStateException("Conflicting visible Entity rule for "+rule.id());
        RemoteKey remoteKey=new RemoteKey(rule.legacyModId(),rule.legacyNumericId());
        Rule remotePrior=REMOTE.putIfAbsent(remoteKey,rule);
        if(remotePrior!=null&&!remotePrior.equals(rule))throw new IllegalStateException("Conflicting visible Entity remote identity "+remoteKey);
        if(TYPES.containsKey(rule.id()))return;
        if(BuiltInRegistries.ENTITY_TYPE.containsKey(rule.id()))throw new IllegalStateException("Visible Entity id already registered: "+rule.id());
        ResourceKey<EntityType<?>> key=ResourceKey.create(Registries.ENTITY_TYPE,rule.id());
        EntityType<ConvertedLegacyVisualEntity> type=EntityType.Builder.<ConvertedLegacyVisualEntity>of(
                (entityType,level)->new ConvertedLegacyVisualEntity(entityType,level,rule),MobCategory.MISC)
                .sized(rule.width(),rule.height()).clientTrackingRange(rule.trackingChunks()).updateInterval(rule.updateFrequency()).build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE,key,type);TYPES.put(rule.id(),type);
    }

    static Rule parseForTests(JsonObject value,String legacyModId){return parse(value,legacyModId);}
    private static Rule parse(JsonObject value,String legacyModId){
        try{
            Identifier id=Identifier.parse(required(value,"id"));Adapter adapter=Adapter.valueOf(required(value,"adapter"));
            List<Part> parts=new ArrayList<>();JsonArray pa=value.getAsJsonArray("parts");if(pa==null)return null;
            for(JsonElement e:pa){JsonObject p=e.getAsJsonObject();parts.add(new Part(required(p,"field"),integer(p,"u",-1),integer(p,"v",-1),
                    decimal(p,"x"),decimal(p,"y"),decimal(p,"z"),integer(p,"width",0),integer(p,"height",0),integer(p,"depth",0),
                    decimal(p,"pivotX"),decimal(p,"pivotY"),decimal(p,"pivotZ"),decimal(p,"xRot"),decimal(p,"yRot"),decimal(p,"zRot"),bool(p,"mirror")));}
            Map<String,Integer> watchers=new LinkedHashMap<>();JsonObject wo=value.has("watchers")?value.getAsJsonObject("watchers"):new JsonObject();
            wo.entrySet().forEach(e->watchers.put(e.getKey(),e.getValue().getAsInt()));
            List<TextureVariant> textures=new ArrayList<>();JsonArray ta=value.getAsJsonArray("textureVariants");
            if(ta!=null)for(JsonElement e:ta){JsonObject t=e.getAsJsonObject();textures.add(new TextureVariant(integer(t,"value",-1),Identifier.parse(required(t,"texture")),bool(t,"translucent")));}
            List<Integer> palette=new ArrayList<>();JsonArray ca=value.getAsJsonArray("palette");if(ca!=null)for(JsonElement e:ca)palette.add(e.getAsInt());
            Identifier fixed=value.has("fixedTexture")?Identifier.parse(value.get("fixedTexture").getAsString()):null;
            return new Rule(id,legacyModId,integer(value,"legacyNumericId",-1),integer(value,"trackingRange",0),integer(value,"updateFrequency",0),
                    bool(value,"velocityUpdates"),decimal(value,"width"),decimal(value,"height"),adapter,integer(value,"modelTextureWidth",0),integer(value,"modelTextureHeight",0),
                    parts,fixed,watchers,textures,palette,integer(value,"itemWatcherBase",-1),integer(value,"itemWatcherCount",0));
        }catch(RuntimeException invalid){return null;}
    }

    private static String required(JsonObject o,String k){String v=string(o,k,null);if(v==null)throw new IllegalArgumentException("Missing "+k);return v;}
    private static String string(JsonObject o,String k,String d){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsString():d;}
    private static int integer(JsonObject o,String k,int d){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsInt():d;}
    private static float decimal(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsFloat();}
    private static boolean bool(JsonObject o,String k){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    private static boolean finite(float... values){for(float v:values)if(!Float.isFinite(v))return false;return true;}
    static synchronized void clearForTests(){RULES.clear();TYPES.clear();REMOTE.clear();LOADED.clear();}
}
