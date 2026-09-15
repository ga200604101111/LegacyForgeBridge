package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.behavior.ConvertedBehaviorItem;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockActivationEffectsRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockActivationRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockPlacementRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyFuelRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacySingleInputProcessorRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.compat.LegacyStorageBlockRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Native registry adapter. Item-specific constants and source callbacks belong to the converted mod. */
public final class GeneratedModSupport {
    private static final Map<Identifier,Item> ITEMS=new ConcurrentHashMap<>();
    private static final Map<Identifier,Block> BLOCKS=new ConcurrentHashMap<>();
    private static final Set<String> ACTIVE_MODS=ConcurrentHashMap.newKeySet();
    private static final Map<String,int[]> COUNTS=new ConcurrentHashMap<>();
    private GeneratedModSupport() { }

    public static void beginMod(String modId){
        LegacyStackComponents.bootstrap();
        LegacyStorageBlockRegistry.loadMod(modId);
        LegacySingleInputProcessorRegistry.loadMod(modId);
        if(ACTIVE_MODS.add(modId))COUNTS.put(modId,new int[3]);
    }

    public static void registerBlock(String idValue,String descriptionKey){
        Identifier id=Identifier.parse(idValue);
        if(BuiltInRegistries.BLOCK.containsKey(id)){BLOCKS.put(id,BuiltInRegistries.BLOCK.getValue(id));return;}
        ResourceKey<Block> blockKey=ResourceKey.create(Registries.BLOCK,id);
        BlockBehaviour.Properties blockProperties=BlockBehaviour.Properties.of().setId(blockKey).overrideDescription(descriptionKey);
        boolean storage=LegacyStorageBlockRegistry.hasRule(id);
        boolean processor=LegacySingleInputProcessorRegistry.hasRule(id);
        if(storage&&processor)throw new IllegalStateException("Converted block has conflicting storage and processor rules: "+id);
        Block block=storage?new ConvertedLegacyStorageBlock(id,blockProperties)
                :processor?new ConvertedLegacyProcessorBlock(id,blockProperties)
                :new ConvertedLegacyBlock(id,blockProperties);
        Registry.register(BuiltInRegistries.BLOCK,blockKey,block);
        BLOCKS.put(id,block);
        if(storage)LegacyStorageBlockRegistry.registerType(id,block);
        if(processor)LegacySingleInputProcessorRegistry.registerType(id,block);

        if(!BuiltInRegistries.ITEM.containsKey(id)){
            ResourceKey<Item> itemKey=ResourceKey.create(Registries.ITEM,id);
            Item.Properties itemProperties=new Item.Properties().setId(itemKey).overrideDescription(descriptionKey)
                    .component(LegacyStackComponents.legacyMeta(),0);
            BlockItem blockItem=new BlockItem(block,itemProperties);
            Registry.register(BuiltInRegistries.ITEM,itemKey,blockItem);
            blockItem.registerBlocks(Item.BY_BLOCK,blockItem);
            ITEMS.put(id,blockItem);
        }else ITEMS.put(id,BuiltInRegistries.ITEM.getValue(id));
        int[] counts=COUNTS.get(id.getNamespace());if(counts!=null){counts[0]++;counts[1]++;}
    }

    public static void registerItem(String idValue,String kind,String descriptionKey,int durability,float attackDamage,float attackSpeed,float armor){
        Identifier id=Identifier.parse(idValue);
        if(BuiltInRegistries.ITEM.containsKey(id)){ITEMS.put(id,BuiltInRegistries.ITEM.getValue(id));return;}
        ResourceKey<Item> key=ResourceKey.create(Registries.ITEM,id);
        Item.Properties properties=new Item.Properties().setId(key).overrideDescription(descriptionKey)
                .component(LegacyStackComponents.legacyMeta(),0);
        var source=LegacyBehaviorRegistry.item(idValue);
        if(source!=null&&source.presentationOnly())source=null;
        if(source!=null){
            int count=source.item().maximumStackSize;
            if(count>=1&&count<=99)properties.stacksTo(count);
            if(source.item().durability>0)durability=source.item().durability;
        }
        if("sword".equals(kind)){
            properties.sword(ToolMaterial.DIAMOND,attackDamage-ToolMaterial.DIAMOND.attackDamageBonus(),attackSpeed);
            properties.attributes(legacyWeaponAttributes(attackDamage,attackSpeed));
        }
        if(source!=null&&source.hooks().contains("hit"))properties.component(DataComponents.WEAPON,new Weapon(0));
        int sourceSlot=source==null?-1:source.item().armorSlot;
        EquipmentSlot slot=switch(sourceSlot){case 0->EquipmentSlot.HEAD;case 1->EquipmentSlot.CHEST;case 2->EquipmentSlot.LEGS;case 3->EquipmentSlot.FEET;default->null;};
        if(slot==null){if("wing".equals(kind))slot=EquipmentSlot.CHEST;else if("circle".equals(kind))slot=EquipmentSlot.FEET;}
        if(slot!=null){
            EquipmentSlotGroup group=switch(slot){case HEAD->EquipmentSlotGroup.HEAD;case CHEST->EquipmentSlotGroup.CHEST;case LEGS->EquipmentSlotGroup.LEGS;default->EquipmentSlotGroup.FEET;};
            Identifier modifier=Identifier.fromNamespaceAndPath(id.getNamespace(),"converted/"+id.getPath()+"_armor");
            properties.equippable(slot).attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.ARMOR,new AttributeModifier(modifier,armor,AttributeModifier.Operation.ADD_VALUE),group).build());
        }
        if(durability>0)properties.durability(durability);
        Item item=new ConvertedBehaviorItem(properties);
        Registry.register(BuiltInRegistries.ITEM,key,item);
        ITEMS.put(id,item);
        int[] counts=COUNTS.get(id.getNamespace());if(counts!=null)counts[0]++;
    }

    static ItemAttributeModifiers legacyWeaponAttributes(float attackDamage,float attackSpeed){
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,attackDamage,AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,attackSpeed,AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND,ItemAttributeModifiers.Display.hidden())
                .build();
    }

    public static void registerCreativeTab(String idValue,String titleKey,String literalTitle,String iconValue,String[] itemValues){
        Identifier id=Identifier.parse(idValue);if(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id))return;
        List<Item> entries=new ArrayList<>();for(String value:itemValues){Item item=resolveItem(Identifier.parse(value));if(item!=null)entries.add(item);}
        if(entries.isEmpty())return;Item icon=resolveItem(Identifier.parse(iconValue));if(icon==null)icon=entries.getFirst();
        Item finalIcon=icon;List<Item> snapshot=List.copyOf(entries);
        Component title=titleKey==null||titleKey.isBlank()?Component.literal(literalTitle):Component.translatable(titleKey);
        CreativeModeTab tab=FabricItemGroup.builder().icon(()->new ItemStack(finalIcon)).title(title)
                .displayItems((params,output)->snapshot.forEach(output::accept)).build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(),id),tab);
        int[] counts=COUNTS.get(id.getNamespace());if(counts!=null)counts[2]++;
    }

    public static void finishMod(String modId){
        LegacyBlockPlacementRegistry.loadMod(modId);
        LegacyBlockActivationRegistry.loadMod(modId);
        LegacyBlockActivationEffectsRegistry.loadMod(modId);
        LegacyFuelRegistry.loadMod(modId);
        int[] counts=COUNTS.getOrDefault(modId,new int[3]);
        LegacyForgeBridge.LOGGER.info("Generated converted mod initialized: mod={}, generatedItems={}, generatedBlocks={}, generatedCreativeTabs={}, sourceBehaviors={}",modId,counts[0],counts[1],counts[2],LegacyBehaviorRegistry.itemCount(modId));
    }

    private static Item resolveItem(Identifier id){Item item=ITEMS.get(id);return item!=null?item:BuiltInRegistries.ITEM.containsKey(id)?BuiltInRegistries.ITEM.getValue(id):null;}
}
