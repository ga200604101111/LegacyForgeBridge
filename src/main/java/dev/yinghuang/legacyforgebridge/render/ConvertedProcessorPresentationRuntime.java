package dev.yinghuang.legacyforgebridge.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacySingleInputProcessorRegistry;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySingleInputProcessorPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyProcessorBlockEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only presentation catalogue and renderer registration for proven rotating processors. */
public final class ConvertedProcessorPresentationRuntime {
    private static final Map<Identifier,Presentation> BY_ID=new ConcurrentHashMap<>();
    private static final Map<Integer,Presentation> BY_KEY=new ConcurrentHashMap<>();
    private static final Set<Integer> COLLIDING_KEYS=ConcurrentHashMap.newKeySet();
    private static final Set<String> INITIALIZED_MODS=ConcurrentHashMap.newKeySet();

    private ConvertedProcessorPresentationRuntime() { }

    public record Widget(int x,int y,int u,int vStride,int width,int height,int frames) {
        public Widget {
            if(x<0||y<0||u<0||vStride<0||width<=0||height<=0||frames<=0)throw new IllegalArgumentException("Invalid processor GUI widget");
        }
    }

    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth) {
        public Cuboid {
            if(u<0||v<0||width<=0||height<=0||depth<=0)throw new IllegalArgumentException("Invalid processor cuboid");
        }
    }

    public record Presentation(Identifier id,int key,Identifier guiTexture,int guiTextureWidth,int guiTextureHeight,
                               int guiWidth,int guiHeight,Widget motion,Widget progress,
                               Identifier entityTexture,int modelTextureWidth,int modelTextureHeight,
                               Cuboid rotatingLower,Cuboid staticUpper,float renderScale,
                               boolean centeredAtBlock,boolean metadataDrivesRoll,boolean inventoryUsesZeroRotation) {
        public Presentation {
            if(id==null||guiTexture==null||entityTexture==null||motion==null||progress==null
                    ||rotatingLower==null||staticUpper==null||guiTextureWidth<=0||guiTextureHeight<=0
                    ||guiWidth<=0||guiHeight<=0||modelTextureWidth<=0||modelTextureHeight<=0
                    ||Float.compare(renderScale,0.0625F)!=0||!centeredAtBlock||!metadataDrivesRoll) {
                throw new IllegalArgumentException("Incomplete converted processor presentation");
            }
        }
    }

    /** Invoked by the converted mod's generated ClientModInitializer after its common content exists. */
    public static void initializeMod(String modId) {
        ConvertedLegacyProcessorParticles.install();
        if(modId==null||modId.isBlank()||!INITIALIZED_MODS.add(modId))return;
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if(container==null){
            INITIALIZED_MODS.remove(modId);
            LegacyForgeBridge.LOGGER.error("Processor presentation bootstrap could not resolve converted mod {}",modId);
            return;
        }
        var path=container.findPath(LegacySingleInputProcessorPass.OUTPUT);
        if(path.isEmpty())return;
        int screens=0,renderers=0;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray machines=root.getAsJsonArray("machines");
            if(machines==null)return;
            for(JsonElement element:machines){
                if(!element.isJsonObject())continue;
                JsonObject machine=element.getAsJsonObject();
                if(!bool(machine,"baseRuntimeComplete")||!bool(machine,"presentationProofComplete")
                        ||!bool(machine,"guiPresentationRuntimeComplete")||!bool(machine,"worldPresentationRuntimeComplete"))continue;
                Presentation presentation=parse(machine);
                if(presentation==null||!presentation.id().getNamespace().equals(modId))continue;
                Presentation prior=BY_ID.putIfAbsent(presentation.id(),presentation);
                if(prior!=null&&!prior.equals(presentation))throw new IllegalStateException("Conflicting converted processor presentation for "+presentation.id());
                registerKey(presentation);
                screens++;
                BlockEntityType<ConvertedLegacyProcessorBlockEntity> type=LegacySingleInputProcessorRegistry.type(presentation.id());
                if(type==null){
                    LegacyForgeBridge.LOGGER.error("Processor presentation {} has no registered converted BlockEntityType",presentation.id());
                    continue;
                }
                BlockEntityRenderers.register(type,context->new ConvertedLegacyProcessorRenderer(context,presentation));
                renderers++;
            }
        }catch(Exception exception){
            INITIALIZED_MODS.remove(modId);
            LegacyForgeBridge.LOGGER.error("Failed to initialize converted processor presentation for {}",modId,exception);
            return;
        }
        if(screens>0||renderers>0)LegacyForgeBridge.LOGGER.info(
                "Initialized converted processor presentation: mod={}, screens={}, worldRenderers={}",modId,screens,renderers);
    }

    public static Presentation presentation(int key){
        return key==0||COLLIDING_KEYS.contains(key)?null:BY_KEY.get(key);
    }

    public static Presentation presentation(Identifier id){return id==null?null:BY_ID.get(id);}

    private static void registerKey(Presentation presentation){
        int key=presentation.key();
        if(COLLIDING_KEYS.contains(key))return;
        Presentation prior=BY_KEY.putIfAbsent(key,presentation);
        if(prior!=null&&!prior.id().equals(presentation.id())){
            BY_KEY.remove(key);
            COLLIDING_KEYS.add(key);
            LegacyForgeBridge.LOGGER.error(
                    "Converted processor presentation key collision: key={}, first={}, second={}; exact GUI disabled for this key.",
                    key,prior.id(),presentation.id());
        }
    }

    private static Presentation parse(JsonObject machine){
        try{
            Identifier id=Identifier.parse(required(machine,"id"));
            JsonObject value=object(machine,"presentation");
            if(value==null)return null;
            Widget motion=widget(object(value,"motion"),"frames");
            Widget progress=widget(object(value,"progress"),"stages");
            Cuboid lower=cuboid(object(value,"rotatingLower"));
            Cuboid upper=cuboid(object(value,"staticUpper"));
            return new Presentation(id,LegacySingleInputProcessorRegistry.presentationKey(id),
                    Identifier.parse(required(value,"guiTexture")),integer(value,"guiTextureWidth"),integer(value,"guiTextureHeight"),
                    integer(value,"guiWidth"),integer(value,"guiHeight"),motion,progress,
                    Identifier.parse(required(value,"entityTexture")),integer(value,"modelTextureWidth"),integer(value,"modelTextureHeight"),
                    lower,upper,decimalFloat(value,"renderScale"),bool(value,"centeredAtBlock"),bool(value,"metadataDrivesRoll"),
                    bool(value,"inventoryUsesZeroRotation"));
        }catch(RuntimeException invalid){
            LegacyForgeBridge.LOGGER.error("Rejected malformed converted processor presentation: {}",invalid.getMessage());
            return null;
        }
    }

    private static Widget widget(JsonObject value,String frameKey){
        if(value==null)throw new IllegalArgumentException("Missing GUI widget");
        int frames=value.has(frameKey)?integer(value,frameKey):integer(value,"frames");
        return new Widget(integer(value,"x"),integer(value,"y"),integer(value,"u"),integer(value,"vStride"),
                integer(value,"width"),integer(value,"height"),frames);
    }

    private static Cuboid cuboid(JsonObject value){
        if(value==null)throw new IllegalArgumentException("Missing cuboid");
        return new Cuboid(integer(value,"u"),integer(value,"v"),decimalFloat(value,"x"),decimalFloat(value,"y"),decimalFloat(value,"z"),
                integer(value,"width"),integer(value,"height"),integer(value,"depth"));
    }

    private static JsonObject object(JsonObject value,String key){
        JsonElement element=value.get(key);return element!=null&&element.isJsonObject()?element.getAsJsonObject():null;
    }
    private static String required(JsonObject value,String key){
        JsonElement element=value.get(key);if(element==null||!element.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return element.getAsString();
    }
    private static int integer(JsonObject value,String key){
        JsonElement element=value.get(key);if(element==null||!element.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return element.getAsInt();
    }
    private static float decimalFloat(JsonObject value,String key){
        JsonElement element=value.get(key);if(element==null||!element.isJsonPrimitive())throw new IllegalArgumentException("Missing "+key);return element.getAsFloat();
    }
    private static boolean bool(JsonObject value,String key){
        JsonElement element=value.get(key);return element!=null&&element.isJsonPrimitive()&&element.getAsBoolean();
    }
}
