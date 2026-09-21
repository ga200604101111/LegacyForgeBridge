package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyRandomDisplayParticlePass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Client-loaded catalogue for source-proven legacy randomDisplayTick particle rules. */
public final class LegacyRandomDisplayParticleRegistry {
    public record Rule(Identifier id,List<String> particles,float centerX,float centerY,float centerZ,float spreadX,float spreadZ){
        public Rule{
            particles=List.copyOf(particles);
            if(id==null||particles.isEmpty()||particles.size()>8||particles.stream().anyMatch(v->!LegacyParticle1710.supported(v))
                    ||!finite(centerX,centerY,centerZ,spreadX,spreadZ)||spreadX<0F||spreadZ<0F)
                throw new IllegalArgumentException("Invalid random display particle rule");
        }
    }
    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();
    private static final Set<String> LOADED=ConcurrentHashMap.newKeySet();
    private LegacyRandomDisplayParticleRegistry(){}

    public static void loadMod(String modId){
        if(modId==null||modId.isBlank()||!LOADED.add(modId))return;
        var container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null){LOADED.remove(modId);return;}
        var path=container.findPath(LegacyRandomDisplayParticlePass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            if(integer(root,"schemaVersion",-1)!=1||!bool(root,"runtimeImplementationWired"))return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int loaded=0;
            for(JsonElement element:rules){
                if(!element.isJsonObject())continue;Rule rule=parse(element.getAsJsonObject());
                if(rule==null||!modId.equals(rule.id().getNamespace()))continue;
                Rule old=RULES.putIfAbsent(rule.id(),rule);if(old!=null&&!old.equals(rule))throw new IllegalStateException("Conflicting random-display particle rule "+rule.id());
                loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted random-display particle rules: mod={}, blocks={}",modId,loaded);
        }catch(Exception exception){LOADED.remove(modId);throw new IllegalStateException("Failed to load random-display particles for "+modId,exception);}
    }
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    private static Rule parse(JsonObject v){
        try{
            Identifier id=Identifier.parse(v.get("id").getAsString());JsonArray raw=v.getAsJsonArray("particles");if(raw==null)return null;
            List<String> particles=new ArrayList<>();for(JsonElement e:raw)particles.add(e.getAsString());
            return new Rule(id,particles,decimal(v,"centerX"),decimal(v,"centerY"),decimal(v,"centerZ"),decimal(v,"spreadX"),decimal(v,"spreadZ"));
        }catch(RuntimeException invalid){return null;}
    }
    private static int integer(JsonObject o,String k,int d){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()?e.getAsInt():d;}
    private static float decimal(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsFloat();}
    private static boolean bool(JsonObject o,String k){JsonElement e=o.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    private static boolean finite(float... values){for(float v:values)if(!Float.isFinite(v))return false;return true;}
    static synchronized void clearForTests(){RULES.clear();LOADED.clear();}
}
