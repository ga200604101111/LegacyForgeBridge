package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyOscillatingModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registers fully-proven oscillating blocks before generic generated content reaches registerBlock. */
public final class LegacyOscillatingModelBootstrap {
    private static final Map<Identifier,BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity>> TYPES=new ConcurrentHashMap<>();
    private LegacyOscillatingModelBootstrap() { }

    public static synchronized void bootstrapMod(String modId){
        LegacyStackComponents.bootstrap();
        LegacyOscillatingModelBlockRegistry.loadMod(modId);
        Map<Identifier,String> descriptions=descriptionKeys(modId);
        int blocks=0;
        for(var rule:LegacyOscillatingModelBlockRegistry.rules(modId)){
            Identifier id=rule.id();
            Block block;
            if(BuiltInRegistries.BLOCK.containsKey(id)){
                block=BuiltInRegistries.BLOCK.getValue(id);
                if(!(block instanceof ConvertedLegacyOscillatingModelBlock)){
                    throw new IllegalStateException("Oscillating runtime id already owned by another block: "+id);
                }
            }else{
                String description=descriptions.get(id);
                if(description==null||description.isBlank())throw new IllegalStateException("Missing generated description key for "+id);
                ResourceKey<Block> blockKey=ResourceKey.create(Registries.BLOCK,id);
                BlockBehaviour.Properties properties=BlockBehaviour.Properties.of().setId(blockKey).overrideDescription(description)
                        .noOcclusion().lightLevel(state->rule.lightEmission());
                block=new ConvertedLegacyOscillatingModelBlock(id,properties);
                Registry.register(BuiltInRegistries.BLOCK,blockKey,block);
            }
            registerType(id,block);
            if(!BuiltInRegistries.ITEM.containsKey(id)){
                String description=descriptions.get(id);
                ResourceKey<Item> itemKey=ResourceKey.create(Registries.ITEM,id);
                Item.Properties properties=new Item.Properties().setId(itemKey).overrideDescription(description)
                        .component(LegacyStackComponents.legacyMeta(),0);
                BlockItem item=new BlockItem(block,properties);
                Registry.register(BuiltInRegistries.ITEM,itemKey,item);
                item.registerBlocks(Item.BY_BLOCK,item);
            }
            blocks++;
        }
        if(blocks>0)LegacyForgeBridge.LOGGER.info("Pre-registered converted oscillating model blocks: mod={}, blocks={}",modId,blocks);
    }

    private static void registerType(Identifier id,Block block){
        if(TYPES.containsKey(id))return;
        if(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)){
            @SuppressWarnings("unchecked")
            BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity> existing=(BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity>)BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            TYPES.put(id,existing);return;
        }
        BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity> type=FabricBlockEntityTypeBuilder.create(ConvertedLegacyOscillatingModelBlockEntity::new,block).build();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,type);TYPES.put(id,type);
    }
    public static BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity> type(Identifier id){return id==null?null:TYPES.get(id);}
    public static BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity> requireType(Block block){Identifier id=BuiltInRegistries.BLOCK.getKey(block);BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity> type=TYPES.get(id);if(type==null)throw new IllegalStateException("No oscillating BlockEntityType for "+id);return type;}

    private static Map<Identifier,String> descriptionKeys(String modId){
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null)return Map.of();var path=container.findPath("legacyforgebridge/converted-content.json");if(path.isEmpty())return Map.of();
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();JsonArray blocks=root.getAsJsonArray("blocks");if(blocks==null)return Map.of();Map<Identifier,String> result=new HashMap<>();
            for(JsonElement element:blocks){if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();if(!value.has("id")||!value.has("descriptionKey"))continue;Identifier id=Identifier.parse(value.get("id").getAsString());result.put(id,value.get("descriptionKey").getAsString());}
            return result;
        }catch(Exception exception){LegacyForgeBridge.LOGGER.error("Failed to read generated block descriptions for {}",modId,exception);return Map.of();}
    }
}
