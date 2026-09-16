package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySeatBedPresentationPass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client presentation payloads kept separate from the already-stable seat-bed gameplay schema. */
public final class LegacySeatBedPresentationRegistry {
    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private LegacySeatBedPresentationRegistry(){ }

    public record Part(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                       float pivotX,float pivotY,float pivotZ,float xRot,float yRot,float zRot,boolean mirror){
        public Part{
            if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0
                    ||!finite(x,y,z,pivotX,pivotY,pivotZ,xRot,yRot,zRot)){
                throw new IllegalArgumentException("Invalid seat-bed presentation part");
            }
        }
    }

    public record Rule(Identifier id,Identifier footTexture,Identifier headTexture,
                       int imageWidth,int imageHeight,int modelTextureWidth,int modelTextureHeight,float modelScale,
                       List<Part> parts,List<String> footParts,List<String> headParts,
                       List<Float> translateXByDirection,List<Float> translateZByDirection,List<Float> yawDegreesByDirection,
                       boolean expandedRenderBoundsProven){
        public Rule{
            parts=List.copyOf(parts);footParts=List.copyOf(footParts);headParts=List.copyOf(headParts);
            translateXByDirection=List.copyOf(translateXByDirection);translateZByDirection=List.copyOf(translateZByDirection);yawDegreesByDirection=List.copyOf(yawDegreesByDirection);
            if(id==null||footTexture==null||headTexture==null||footTexture.equals(headTexture)
                    ||imageWidth<=0||imageHeight<=0||modelTextureWidth<=0||modelTextureHeight<=0
                    ||Float.compare(modelScale,.0625F)!=0||parts.size()!=4||footParts.size()!=2||headParts.size()!=2
                    ||translateXByDirection.size()!=4||translateZByDirection.size()!=4||yawDegreesByDirection.size()!=4
                    ||!expandedRenderBoundsProven){
                throw new IllegalArgumentException("Invalid converted seat-bed presentation rule");
            }
            LinkedHashSet<String> fields=new LinkedHashSet<>();parts.forEach(p->fields.add(p.field()));
            if(fields.size()!=4)throw new IllegalArgumentException("Duplicate seat-bed presentation part");
            LinkedHashSet<String> rendered=new LinkedHashSet<>(footParts);
            if(rendered.size()!=2||!Collections.disjoint(rendered,headParts))throw new IllegalArgumentException("Invalid seat-bed presentation groups");
            rendered.addAll(headParts);if(!rendered.equals(fields))throw new IllegalArgumentException("Seat-bed presentation groups do not cover all parts");
            for(Float v:translateXByDirection)check(v);for(Float v:translateZByDirection)check(v);for(Float v:yawDegreesByDirection)check(v);
        }
    }

    public static void loadMod(String modId){
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null)return;
        var path=container.findPath(LegacySeatBedPresentationPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();if(integer(root,"schemaVersion",0)!=1)return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int loaded=0;
            for(JsonElement element:rules){
                if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                if(!bool(value,"presentationProofComplete")||!bool(value,"runtimeImplementationWired"))continue;
                Rule rule=parse(value);if(rule==null||!modId.equals(rule.id().getNamespace()))continue;
                Rule old=RULES.putIfAbsent(rule.id(),rule);if(old!=null&&!old.equals(rule))throw new IllegalStateException("Conflicting seat-bed presentation for "+rule.id());loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted seat-bed presentation rules: mod={}, rules={}",modId,loaded);
        }catch(Exception e){LegacyForgeBridge.LOGGER.error("Failed to load converted seat-bed presentation rules for {}",modId,e);}
    }

    public static List<Rule> rules(String namespace){
        return RULES.values().stream().filter(r->r.id().getNamespace().equals(namespace))
                .sorted(java.util.Comparator.comparing(r->r.id().toString())).toList();
    }

    static void installForTests(Rule rule){RULES.put(rule.id(),rule);}
    static void clearForTests(){RULES.clear();}
    static Rule parseForTests(JsonObject value){return parse(value);}

    private static Rule parse(JsonObject v){
        try{
            JsonArray a=v.getAsJsonArray("parts");if(a==null||a.size()!=4)return null;List<Part> parts=new ArrayList<>();
            for(JsonElement e:a){if(!e.isJsonObject())return null;JsonObject x=e.getAsJsonObject();parts.add(new Part(
                    required(x,"field"),integer(x,"u",-1),integer(x,"v",-1),decimal(x,"x",Float.NaN),decimal(x,"y",Float.NaN),decimal(x,"z",Float.NaN),
                    integer(x,"width",0),integer(x,"height",0),integer(x,"depth",0),decimal(x,"pivotX",0),decimal(x,"pivotY",0),decimal(x,"pivotZ",0),
                    decimal(x,"xRot",0),decimal(x,"yRot",0),decimal(x,"zRot",0),bool(x,"mirror")));
            }
            return new Rule(Identifier.parse(required(v,"id")),Identifier.parse(required(v,"footTexture")),Identifier.parse(required(v,"headTexture")),
                    integer(v,"imageWidth",0),integer(v,"imageHeight",0),integer(v,"modelTextureWidth",0),integer(v,"modelTextureHeight",0),decimal(v,"modelScale",0),
                    parts,strings(v,"footParts"),strings(v,"headParts"),floats(v,"translateXByDirection"),floats(v,"translateZByDirection"),floats(v,"yawDegreesByDirection"),bool(v,"expandedRenderBoundsProven"));
        }catch(RuntimeException invalid){return null;}
    }

    private static List<String> strings(JsonObject o,String k){JsonArray a=o.getAsJsonArray(k);if(a==null)return List.of();List<String> r=new ArrayList<>();for(JsonElement e:a){if(!e.isJsonPrimitive())return List.of();r.add(e.getAsString());}return r;}
    private static List<Float> floats(JsonObject o,String k){JsonArray a=o.getAsJsonArray(k);if(a==null)return List.of();List<Float> r=new ArrayList<>();for(JsonElement e:a){if(!e.isJsonPrimitive())return List.of();r.add(e.getAsFloat());}return r;}
    private static boolean finite(float... vs){for(float v:vs)if(!Float.isFinite(v))return false;return true;}
    private static void check(Float v){if(v==null||!Float.isFinite(v))throw new IllegalArgumentException("Invalid seat-bed presentation transform");}
    private static String required(JsonObject o,String k){JsonElement v=o.get(k);if(v==null||!v.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return v.getAsString();}
    private static int integer(JsonObject o,String k,int f){JsonElement v=o.get(k);return v!=null&&v.isJsonPrimitive()?v.getAsInt():f;}
    private static float decimal(JsonObject o,String k,float f){JsonElement v=o.get(k);return v!=null&&v.isJsonPrimitive()?v.getAsFloat():f;}
    private static boolean bool(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonPrimitive()&&v.getAsBoolean();}
}
