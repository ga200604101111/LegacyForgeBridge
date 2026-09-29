package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyDurabilityItemPass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime catalogue for source-proven nonstandard legacy durability-bar presentation. */
public final class LegacyDurabilityPresentationRegistry {
    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private LegacyDurabilityPresentationRegistry(){}

    public record Rule(Identifier id,int durability,boolean alwaysShowBar,boolean inverseProgressBar){
        public Rule{
            if(id==null||durability<=0)throw new IllegalArgumentException("Invalid durability presentation rule");
        }
    }

    public static void loadMod(String modId){
        var container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null)return;
        var path=container.findPath(LegacyDurabilityItemPass.OUTPUT).orElse(null);
        if(path==null||!Files.isRegularFile(path))return;
        int loaded=0;
        try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();JsonArray values=root.getAsJsonArray("rules");if(values==null)return;
            for(JsonElement element:values){
                if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                if(!bool(value,"runtimeComplete"))continue;
                Identifier id=Identifier.parse(value.get("id").getAsString());if(!modId.equals(id.getNamespace()))continue;
                Rule rule=new Rule(id,value.get("durability").getAsInt(),bool(value,"alwaysShowBar"),bool(value,"inverseProgressBar"));
                Rule old=RULES.putIfAbsent(id,rule);if(old!=null&&!old.equals(rule))
                    throw new IllegalStateException("Conflicting durability presentation rule "+id);
                loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted durability presentation rules: mod={}, items={}",modId,loaded);
        }catch(Exception exception){
            LegacyForgeBridge.LOGGER.error("Failed to load converted durability presentation rules for {}",modId,exception);
        }
    }
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    static void clearForTests(){RULES.clear();}
    private static boolean bool(JsonObject value,String key){
        JsonElement element=value.get(key);return element!=null&&element.isJsonPrimitive()&&element.getAsBoolean();
    }
}
