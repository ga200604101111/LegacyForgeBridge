package dev.yinghuang.legacyforgebridge.render;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyLiquidPresentationPass;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client registration for source-proven converted legacy liquid blocks. */
public final class ConvertedLiquidPresentationRuntime {
    private static final Set<Identifier> REGISTERED=ConcurrentHashMap.newKeySet();
    private ConvertedLiquidPresentationRuntime(){}

    public static void initializeMod(String modId){
        var container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null)return;
        var path=container.findPath(LegacyLiquidPresentationPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            if(integer(root,"schemaVersion",-1)!=1||!bool(root,"runtimeImplementationWired")
                    ||!"TRANSLUCENT".equals(string(root,"renderLayer",null)))return;
            JsonArray rules=root.getAsJsonArray("rules");if(rules==null)return;int count=0;
            for(JsonElement element:rules){
                if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                if(!bool(value,"translucent")||!bool(value,"collisionEmpty"))continue;
                Identifier id=Identifier.parse(string(value,"id",""));
                if(!modId.equals(id.getNamespace())||!BuiltInRegistries.BLOCK.containsKey(id)||!REGISTERED.add(id))continue;
                Block block=BuiltInRegistries.BLOCK.getValue(id);if(block==null){REGISTERED.remove(id);continue;}
                BlockRenderLayerMap.putBlock(block,ChunkSectionLayer.TRANSLUCENT);count++;
            }
            if(count>0)LegacyForgeBridge.LOGGER.info("Registered converted legacy liquid render layers: mod={}, blocks={}",modId,count);
        }catch(Exception exception){
            LegacyForgeBridge.LOGGER.error("Failed to load converted legacy liquid presentation for {}",modId,exception);
        }
    }

    private static String string(JsonObject object,String key,String fallback){JsonElement e=object.get(key);return e!=null&&e.isJsonPrimitive()?e.getAsString():fallback;}
    private static int integer(JsonObject object,String key,int fallback){JsonElement e=object.get(key);return e!=null&&e.isJsonPrimitive()?e.getAsInt():fallback;}
    private static boolean bool(JsonObject object,String key){JsonElement e=object.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
    static void clearForTests(){REGISTERED.clear();}
}
