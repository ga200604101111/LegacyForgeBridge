package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.google.gson.*;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.NumberTag;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyNbtByteIconSelectorPass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Immutable lookup for source-proven NBT-byte-selected item model definitions. */
public final class LegacyNbtByteIconSelectorCatalog {
    private record Rule(String fabricId,String key,int defaultIndex,List<String> models){
        private Rule{models=List.copyOf(models);}
    }
    private static volatile Map<Identifier,Rule> rules;

    private LegacyNbtByteIconSelectorCatalog(){}

    public static String itemModel(Identifier item,CompoundTag sourceTag){
        if(item==null)return null;Map<Identifier,Rule> snapshot=rules;
        if(snapshot==null)synchronized(LegacyNbtByteIconSelectorCatalog.class){
            if(rules==null)rules=load();snapshot=rules;
        }
        Rule rule=snapshot.get(item);
        if(rule==null||ConvertedModCatalog.isMarkedStale(rule.fabricId()))return null;
        int index=rule.defaultIndex();
        if(sourceTag!=null){
            NumberTag number=sourceTag.getNumberTag(rule.key());
            if(number!=null){
                int raw=number.asInt();
                if(raw>=0&&raw<rule.models().size())index=raw;
            }
        }
        return index>=0&&index<rule.models().size()?rule.models().get(index):null;
    }

    private static Map<Identifier,Rule> load(){
        Map<Identifier,Rule> found=new LinkedHashMap<>();Set<Identifier> conflicts=new HashSet<>();
        try{
            for(var mod:FabricLoader.getInstance().getAllMods()){
                String fabricId=mod.getMetadata().getId();
                if(ConvertedModCatalog.isMarkedStale(fabricId))continue;
                var path=mod.findPath(LegacyNbtByteIconSelectorPass.OUTPUT);if(path.isEmpty())continue;
                try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
                    JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
                    if(integer(root,"schemaVersion",-1)!=1)continue;
                    JsonArray values=root.getAsJsonArray("rules");if(values==null)continue;
                    for(JsonElement element:values){
                        if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                        if(!bool(value,"runtimeComplete"))continue;
                        Identifier id;try{id=Identifier.parse(required(value,"id"));}catch(RuntimeException invalid){continue;}
                        if(!fabricId.equals(id.getNamespace())||conflicts.contains(id))continue;
                        String key=required(value,"nbtKey");int def=integer(value,"defaultIndex",-1),count=integer(value,"variantCount",-1);
                        JsonArray rawModels=value.getAsJsonArray("itemModels");
                        if(key.isBlank()||count<2||count>32||def<0||def>=count||rawModels==null||rawModels.size()!=count)continue;
                        List<String> models=new ArrayList<>(count);boolean valid=true;
                        for(JsonElement raw:rawModels){
                            if(!raw.isJsonPrimitive()){valid=false;break;}
                            String model=raw.getAsString();String[] split=model.split(":",2);
                            if(split.length!=2||!model.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                                    ||mod.findPath("assets/"+split[0]+"/items/"+split[1]+".json").isEmpty()){valid=false;break;}
                            models.add(model);
                        }
                        if(!valid)continue;
                        Rule rule=new Rule(fabricId,key,def,models),prior=found.putIfAbsent(id,rule);
                        if(prior!=null&&!prior.equals(rule)){found.remove(id);conflicts.add(id);}
                    }
                }catch(Exception ignored){/* One optional corrupt selector file must not corrupt item identity. */}
            }
        }catch(Throwable ignored){return Map.of();}
        return Map.copyOf(found);
    }

    static synchronized void clearForTests(){rules=null;}
    private static String required(JsonObject value,String key){
        JsonElement element=value.get(key);if(element==null||!element.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);
        return element.getAsString();
    }
    private static int integer(JsonObject value,String key,int fallback){
        JsonElement element=value.get(key);return element!=null&&element.isJsonPrimitive()?element.getAsInt():fallback;
    }
    private static boolean bool(JsonObject value,String key){
        JsonElement element=value.get(key);return element!=null&&element.isJsonPrimitive()&&element.getAsBoolean();
    }
}
