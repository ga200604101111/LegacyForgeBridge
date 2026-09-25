package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyRadialTesrPass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Client runtime catalogue for source-proven metadata-selected sub-model groups of radial TESRs. */
public final class LegacyRadialConditionalModelRegistry {
    public enum Axis { X, Y, Z }

    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ) {
        public Cuboid {
            if(u<0||v<0||width<0||height<0||depth<0||(width==0&&height==0&&depth==0)
                    ||!finite(x,y,z,pivotX,pivotY,pivotZ))
                throw new IllegalArgumentException("Invalid conditional radial cuboid");
        }
    }
    public record Pose(float xRot,float yRot,float zRot) {
        public Pose { if(!finite(xRot,yRot,zRot))throw new IllegalArgumentException("Invalid conditional radial pose"); }
    }
    public record Animation(Axis axis,float degreesPerTick,int periodTicks,boolean randomizedPhase) {
        public Animation {
            if(axis==null||!Float.isFinite(degreesPerTick)||degreesPerTick==0F||periodTicks<=1)
                throw new IllegalArgumentException("Invalid conditional radial animation");
        }
    }
    public record Part(Cuboid cuboid,List<Pose> poses,Animation animation) {
        public Part {
            poses=List.copyOf(poses);
            if(cuboid==null||poses.isEmpty()||poses.size()>32)throw new IllegalArgumentException("Invalid conditional radial part");
        }
    }
    public record Group(int selectorValue,List<Part> parts) {
        public Group {
            parts=List.copyOf(parts);
            if(selectorValue<0||selectorValue>15||parts.isEmpty())throw new IllegalArgumentException("Invalid conditional radial group");
        }
    }
    public record Rule(Identifier id,int metadataShift,int selectorMask,List<Group> groups,int coveredModelCalls) {
        public Rule {
            groups=List.copyOf(groups);
            if(id==null||metadataShift<0||metadataShift>3||selectorMask<1||selectorMask>15
                    ||groups.isEmpty()||coveredModelCalls<=0)
                throw new IllegalArgumentException("Invalid conditional radial rule");
        }
        public Group groupForMeta(int legacyMeta) {
            int selector=(legacyMeta>>metadataShift)&selectorMask;
            for(Group group:groups)if(group.selectorValue()==selector)return group;
            return null;
        }
    }

    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private static final Set<String> LOADED=ConcurrentHashMap.newKeySet();

    private LegacyRadialConditionalModelRegistry(){}

    public static void loadMod(String modId) {
        if(modId==null||modId.isBlank()||!LOADED.add(modId))return;
        var container=FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if(container==null){LOADED.remove(modId);return;}
        var path=container.findPath(LegacyRadialTesrPass.CONDITIONAL_OUTPUT);
        if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            if(integer(root,"schemaVersion",-1)!=1)return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int loaded=0;
            for(JsonElement element:rules){
                if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                if(!bool(value,"conditionalPresentationRuntimeComplete"))continue;
                Rule rule=parse(value);
                if(rule==null||!modId.equals(rule.id().getNamespace()))continue;
                Rule old=RULES.putIfAbsent(rule.id(),rule);
                if(old!=null&&!old.equals(rule))throw new IllegalStateException("Conflicting conditional radial rule "+rule.id());
                loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded conditional radial model rules: mod={}, blocks={}",modId,loaded);
        }catch(Exception exception){
            LOADED.remove(modId);
            throw new IllegalStateException("Failed to load conditional radial model rules for "+modId,exception);
        }
    }

    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    public static List<Rule> rules(String namespace){
        return RULES.values().stream().filter(rule->namespace.equals(rule.id().getNamespace()))
                .sorted(Comparator.comparing(rule->rule.id().toString())).toList();
    }

    private static Rule parse(JsonObject value){
        try{
            Identifier id=Identifier.parse(required(value,"id"));
            int shift=integer(value,"metadataShift",-1),mask=integer(value,"selectorMask",-1);
            int covered=integer(value,"coveredModelCalls",-1);
            JsonArray rawGroups=value.getAsJsonArray("groups");if(rawGroups==null)return null;
            List<Group> groups=new ArrayList<>();
            for(JsonElement groupElement:rawGroups){
                if(!groupElement.isJsonObject())return null;JsonObject groupObject=groupElement.getAsJsonObject();
                int selector=integer(groupObject,"selectorValue",-1);
                JsonArray rawParts=groupObject.getAsJsonArray("parts");if(rawParts==null)return null;
                List<Part> parts=new ArrayList<>();
                for(JsonElement partElement:rawParts){
                    if(!partElement.isJsonObject())return null;JsonObject partObject=partElement.getAsJsonObject();
                    JsonObject c=partObject.getAsJsonObject("cuboid");if(c==null)return null;
                    Cuboid cuboid=new Cuboid(integer(c,"u",-1),integer(c,"v",-1),decimal(c,"x"),decimal(c,"y"),decimal(c,"z"),
                            integer(c,"width",-1),integer(c,"height",-1),integer(c,"depth",-1),
                            decimal(c,"pivotX"),decimal(c,"pivotY"),decimal(c,"pivotZ"));
                    JsonArray rawPoses=partObject.getAsJsonArray("poses");if(rawPoses==null)return null;
                    List<Pose> poses=new ArrayList<>();
                    for(JsonElement poseElement:rawPoses){
                        JsonObject pose=poseElement.getAsJsonObject();
                        poses.add(new Pose(decimal(pose,"xRot"),decimal(pose,"yRot"),decimal(pose,"zRot")));
                    }
                    Animation animation=null;
                    JsonObject animationObject=partObject.getAsJsonObject("animation");
                    if(animationObject!=null){
                        animation=new Animation(Axis.valueOf(required(animationObject,"axis")),
                                decimal(animationObject,"degreesPerTick"),integer(animationObject,"periodTicks",-1),
                                bool(animationObject,"randomizedPhase"));
                    }
                    parts.add(new Part(cuboid,poses,animation));
                }
                groups.add(new Group(selector,parts));
            }
            return new Rule(id,shift,mask,groups,covered);
        }catch(RuntimeException invalid){return null;}
    }

    private static String required(JsonObject object,String key){
        JsonElement element=object.get(key);
        if(element==null||!element.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);
        return element.getAsString();
    }
    private static int integer(JsonObject object,String key,int fallback){
        JsonElement element=object.get(key);return element!=null&&element.isJsonPrimitive()?element.getAsInt():fallback;
    }
    private static float decimal(JsonObject object,String key){
        JsonElement element=object.get(key);
        if(element==null||!element.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);
        return element.getAsFloat();
    }
    private static boolean bool(JsonObject object,String key){
        JsonElement element=object.get(key);return element!=null&&element.isJsonPrimitive()&&element.getAsBoolean();
    }
    private static boolean finite(float... values){for(float value:values)if(!Float.isFinite(value))return false;return true;}

    static synchronized void clearForTests(){RULES.clear();LOADED.clear();}
}
