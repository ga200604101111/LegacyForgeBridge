package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyOscillatingModelBlockPass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime catalogue for fully source-proven client-only oscillating decorative blocks. */
public final class LegacyOscillatingModelBlockRegistry {
    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private LegacyOscillatingModelBlockRegistry() { }

    public record Animation(int randomInitialBound,float stepDegrees,float lowerBoundDegrees,float upperBoundDegrees) {
        public Animation {
            if(randomInitialBound<=1||!Float.isFinite(stepDegrees)||stepDegrees<=0F
                    ||!Float.isFinite(lowerBoundDegrees)||!Float.isFinite(upperBoundDegrees)
                    ||upperBoundDegrees<=lowerBoundDegrees)throw new IllegalArgumentException("Invalid oscillating animation");
        }
    }
    public record Part(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                       float pivotX,float pivotY,float pivotZ,boolean mirror,
                       float baseXRot,float baseYRot,float baseZRot,boolean animated) {
        public Part {
            if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0
                    ||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)
                    ||!Float.isFinite(pivotX)||!Float.isFinite(pivotY)||!Float.isFinite(pivotZ)
                    ||!Float.isFinite(baseXRot)||!Float.isFinite(baseYRot)||!Float.isFinite(baseZRot))
                throw new IllegalArgumentException("Invalid oscillating model part");
        }
    }
    public record Presentation(Identifier texture,int modelTextureWidth,int modelTextureHeight,List<Part> parts,
                               String animatedPartField,float modelScale,float translateX,float translateY,float translateZ,
                               int metadataMask,float yawDegreesPerMeta,float yawOffsetDegrees,
                               float inventoryYawDegrees,float inventoryTranslateY,float inventoryScale,float inventoryDynamicAngleDegrees) {
        public Presentation {
            parts=List.copyOf(parts);
            if(texture==null||modelTextureWidth<=0||modelTextureHeight<=0||parts.isEmpty()
                    ||animatedPartField==null||animatedPartField.isBlank()||parts.stream().filter(Part::animated).count()!=1
                    ||parts.stream().noneMatch(part->part.animated()&&part.field().equals(animatedPartField))
                    ||!Float.isFinite(modelScale)||modelScale<=0F
                    ||!Float.isFinite(translateX)||!Float.isFinite(translateY)||!Float.isFinite(translateZ)
                    ||metadataMask<0||metadataMask>15||!Float.isFinite(yawDegreesPerMeta)||!Float.isFinite(yawOffsetDegrees)
                    ||!Float.isFinite(inventoryYawDegrees)||!Float.isFinite(inventoryTranslateY)
                    ||!Float.isFinite(inventoryScale)||inventoryScale<=0F||!Float.isFinite(inventoryDynamicAngleDegrees))
                throw new IllegalArgumentException("Invalid oscillating presentation");
        }
    }
    public record Rule(Identifier id,int[] placementMetaByYawQuadrant,int lightEmission,Animation animation,Presentation presentation) {
        public Rule {
            placementMetaByYawQuadrant=placementMetaByYawQuadrant.clone();
            if(id==null||placementMetaByYawQuadrant.length!=4||lightEmission<0||lightEmission>15||animation==null||presentation==null)
                throw new IllegalArgumentException("Invalid oscillating rule");
            for(int meta:placementMetaByYawQuadrant)if(meta<0||meta>15)throw new IllegalArgumentException("Invalid oscillating metadata mapping");
        }
        @Override public int[] placementMetaByYawQuadrant(){return placementMetaByYawQuadrant.clone();}
    }

    public static void loadMod(String modId){
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null)return;
        var path=container.findPath(LegacyOscillatingModelBlockPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();JsonArray values=root.getAsJsonArray("rules");if(values==null)return;int loaded=0;
            for(JsonElement element:values){if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                if(!bool(value,"animationProofComplete")||!bool(value,"presentationProofComplete")
                        ||!bool(value,"inventoryPresentationProofComplete")||!bool(value,"runtimeComplete"))continue;
                Rule rule=parse(value);if(rule==null||!rule.id().getNamespace().equals(modId))continue;
                Rule previous=RULES.putIfAbsent(rule.id(),rule);if(previous!=null&&!previous.equals(rule))throw new IllegalStateException("Conflicting oscillating rule for "+rule.id());loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted oscillating model runtime rules: mod={}, blocks={}",modId,loaded);
        }catch(Exception exception){LegacyForgeBridge.LOGGER.error("Failed to load converted oscillating model rules for {}",modId,exception);}
    }
    public static boolean hasRule(Identifier id){return id!=null&&RULES.containsKey(id);}
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    public static Rule requireRule(Identifier id){Rule rule=rule(id);if(rule==null)throw new IllegalStateException("No oscillating rule for "+id);return rule;}
    public static List<Rule> rules(String namespace){return RULES.values().stream().filter(rule->rule.id().getNamespace().equals(namespace)).sorted(Comparator.comparing(rule->rule.id().toString())).toList();}

    private static Rule parse(JsonObject value){
        try{
            Identifier id=Identifier.parse(required(value,"id"));JsonArray mapping=value.getAsJsonArray("placementMetaByYawQuadrant");if(mapping==null||mapping.size()!=4)return null;int[] metas=new int[4];for(int i=0;i<4;i++)metas[i]=mapping.get(i).getAsInt();
            JsonObject animationJson=object(value,"animation"),presentationJson=object(value,"presentation"),inventoryJson=object(value,"inventoryPresentation");if(animationJson==null||presentationJson==null||inventoryJson==null)return null;
            Animation animation=new Animation(integer(animationJson,"randomInitialBound"),decimal(animationJson,"stepDegrees"),decimal(animationJson,"lowerBoundDegrees"),decimal(animationJson,"upperBoundDegrees"));
            JsonArray partValues=presentationJson.getAsJsonArray("parts");if(partValues==null||partValues.isEmpty())return null;List<Part> parts=new ArrayList<>();for(JsonElement element:partValues){if(!element.isJsonObject())return null;JsonObject part=element.getAsJsonObject();parts.add(new Part(required(part,"field"),integer(part,"u"),integer(part,"v"),decimal(part,"x"),decimal(part,"y"),decimal(part,"z"),integer(part,"width"),integer(part,"height"),integer(part,"depth"),decimal(part,"pivotX"),decimal(part,"pivotY"),decimal(part,"pivotZ"),bool(part,"mirror"),decimal(part,"baseXRot"),decimal(part,"baseYRot"),decimal(part,"baseZRot"),bool(part,"animated")));}
            Presentation presentation=new Presentation(Identifier.parse(required(presentationJson,"texture")),integer(presentationJson,"modelTextureWidth"),integer(presentationJson,"modelTextureHeight"),parts,required(presentationJson,"animatedPartField"),decimal(presentationJson,"modelScale"),decimal(presentationJson,"translateX"),decimal(presentationJson,"translateY"),decimal(presentationJson,"translateZ"),integer(presentationJson,"metadataMask"),decimal(presentationJson,"yawDegreesPerMeta"),decimal(presentationJson,"yawOffsetDegrees"),decimal(inventoryJson,"yawDegrees"),decimal(inventoryJson,"translateY"),decimal(inventoryJson,"scale"),decimal(inventoryJson,"dynamicAngleDegrees"));
            return new Rule(id,metas,integer(value,"lightEmission"),animation,presentation);
        }catch(RuntimeException invalid){return null;}
    }
    private static JsonObject object(JsonObject value,String key){JsonElement e=value.get(key);return e!=null&&e.isJsonObject()?e.getAsJsonObject():null;}
    private static String required(JsonObject value,String key){JsonElement e=value.get(key);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return e.getAsString();}
    private static int integer(JsonObject value,String key){JsonElement e=value.get(key);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return e.getAsInt();}
    private static float decimal(JsonObject value,String key){JsonElement e=value.get(key);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return e.getAsFloat();}
    private static boolean bool(JsonObject value,String key){JsonElement e=value.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    static void clearForTests(){RULES.clear();}
}
