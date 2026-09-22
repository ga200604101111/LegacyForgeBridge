package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.behavior.ConvertedBehaviorItem;
import dev.yinghuang.legacyforgebridge.behavior.ConvertedLegacyBowItem;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockActivationEffectsRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockActivationRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockPlacementRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyFoodItemRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyFuelRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyGridPotBlockRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyHeldItemVisibilityRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyInertModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyMicroBlockRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyMetadataRotatingTesrRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyPlantPlacementRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyPlantRuntimeRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyProjectilePresentationRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyRadialModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyRotatingAssemblyEntityRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacySingleInputProcessorRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.compat.LegacyStorageBlockRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyVariantSnowballRuntimeRegistry;
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
import net.minecraft.world.item.SnowballItem;
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
        LegacyOscillatingModelBootstrap.bootstrapMod(modId);
        LegacyVariantSnowballRuntimeRegistry.loadMod(modId);
        LegacyHeldItemVisibilityRegistry.loadMod(modId);
        LegacyProjectilePresentationRegistry.loadMod(modId);
        LegacyRotatingAssemblyEntityRegistry.loadMod(modId);
        LegacyPlantRuntimeRegistry.loadMod(modId);
        LegacyPlantPlacementRegistry.loadMod(modId);
        LegacyFoodItemRegistry.loadMod(modId);
        LegacyInertModelBlockRegistry.loadMod(modId);
        LegacyMetadataRotatingTesrRegistry.loadMod(modId);
        LegacyRadialModelBlockRegistry.loadMod(modId);
        LegacyStorageBlockRegistry.loadMod(modId);
        LegacySingleInputProcessorRegistry.loadMod(modId);
        LegacyGridPotBlockRegistry.loadMod(modId);
        LegacyMicroBlockRegistry.loadMod(modId);
        LegacySeatBedRegistry.loadMod(modId);
        if(ACTIVE_MODS.add(modId))COUNTS.put(modId,new int[3]);
    }

    public static void registerBlock(String idValue,String descriptionKey){
        Identifier id=Identifier.parse(idValue);
        if(BuiltInRegistries.BLOCK.containsKey(id)){BLOCKS.put(id,BuiltInRegistries.BLOCK.getValue(id));return;}
        ResourceKey<Block> blockKey=ResourceKey.create(Registries.BLOCK,id);
        boolean gridPot=LegacyGridPotBlockRegistry.hasRule(id);
        boolean microBlock=LegacyMicroBlockRegistry.hasRule(id);
        boolean seatBed=LegacySeatBedRegistry.hasBlockRule(id);
        BlockBehaviour.Properties blockProperties=BlockBehaviour.Properties.of().setId(blockKey).overrideDescription(descriptionKey);
        if(gridPot||microBlock||seatBed)blockProperties=blockProperties.dynamicShape().noOcclusion();
        boolean inert=LegacyInertModelBlockRegistry.hasRule(id);
        boolean radial=LegacyRadialModelBlockRegistry.hasRule(id);
        boolean metadataRotating=LegacyMetadataRotatingTesrRegistry.hasRule(id);
        boolean storage=LegacyStorageBlockRegistry.hasRule(id);
        boolean processor=LegacySingleInputProcessorRegistry.hasRule(id);
        if(processor)blockProperties=LegacySingleInputProcessorRegistry.applyBlockProperties(id,blockProperties);
        var plantRule=LegacyPlantRuntimeRegistry.rule(id);
        boolean plant=plantRule!=null&&LegacyPlantPlacementRegistry.plantTargetRuntimeReady(id);
        int families=(gridPot?1:0)+(microBlock?1:0)+(seatBed?1:0)+(inert?1:0)+(radial?1:0)+(metadataRotating?1:0)+(storage?1:0)+(processor?1:0)+(plant?1:0);
        if(families>1)throw new IllegalStateException("Converted block has conflicting specialized runtime rules: "+id);
        Block block=gridPot?new ConvertedLegacyGridPotBlock(id,blockProperties)
                :microBlock?new ConvertedLegacyMicroBlock(id,blockProperties)
                :seatBed?new ConvertedLegacySeatBedBlock(id,blockProperties)
                :inert?new ConvertedLegacyInertModelBlock(id,blockProperties)
                :radial?new ConvertedLegacyRadialModelBlock(id,blockProperties)
                :metadataRotating?new ConvertedLegacyMetadataRotatingBlock(id,blockProperties)
                :storage?new ConvertedLegacyStorageBlock(id,blockProperties)
                :processor?new ConvertedLegacyProcessorBlock(id,blockProperties)
                :plant?new ConvertedLegacyPlantBlock(id,blockProperties)
                :new ConvertedLegacyBlock(id,blockProperties);
        Registry.register(BuiltInRegistries.BLOCK,blockKey,block);
        BLOCKS.put(id,block);
        if(gridPot)LegacyGridPotBlockRegistry.registerType(id,block);
        if(microBlock)LegacyMicroBlockRegistry.registerType(id,block);
        if(seatBed)LegacySeatBedRegistry.registerType(id,block);
        if(inert)LegacyInertModelBlockRegistry.registerType(id,block);
        if(radial)LegacyRadialModelBlockRegistry.registerType(id,block);
        if(metadataRotating)LegacyMetadataRotatingTesrRegistry.registerType(id,block);
        if(storage)LegacyStorageBlockRegistry.registerType(id,block);
        if(processor)LegacySingleInputProcessorRegistry.registerType(id,block);

        if(!BuiltInRegistries.ITEM.containsKey(id)){
            ResourceKey<Item> itemKey=ResourceKey.create(Registries.ITEM,id);
            Item.Properties itemProperties=new Item.Properties().setId(itemKey).overrideDescription(descriptionKey)
                    .component(LegacyStackComponents.legacyMeta(),0);
            BlockItem blockItem=gridPot?new ConvertedLegacyGridPotBlockItem(block,itemProperties):new BlockItem(block,itemProperties);
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
        if("snowball".equals(kind))properties.stacksTo(16);
        if("bow".equals(kind))properties.stacksTo(1);
        var plantingRule=LegacyPlantPlacementRegistry.rule(id);
        var seatBedRule=LegacySeatBedRegistry.placementItemRule(id);
        var variantSnowballRule=LegacyVariantSnowballRuntimeRegistry.rule(id);
        var food=LegacyFoodItemRegistry.rule(id);
        if(plantingRule!=null&&plantingRule.adapter()==LegacyPlantPlacementRegistry.Adapter.SEED_FOOD){
            if(food!=null&&(food.nutrition()!=plantingRule.nutrition()
                    ||Float.compare(food.saturationModifier(),plantingRule.saturationModifier())!=0
                    ||food.alwaysEdible()))throw new IllegalStateException("Conflicting ItemSeedFood food proof for "+id);
            properties.food(LegacyPlantPlacementRegistry.foodProperties(plantingRule));
        }else if(food!=null)properties.food(LegacyFoodItemRegistry.foodProperties(food));
        var source=LegacyBehaviorRegistry.item(idValue);
        if(source!=null&&source.presentationOnly())source=null;
        if(source!=null){
            int count=source.item().maximumStackSize;
            if(count>=1&&count<=99)properties.stacksTo(count);
            if(source.item().durability>0)durability=source.item().durability;
        }
        if(variantSnowballRule!=null)properties.stacksTo(16);
        if(seatBedRule!=null)properties.stacksTo(1);
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
        boolean planting=LegacyPlantPlacementRegistry.hasRuntimeRule(id);
        boolean seatBedPlacement=seatBedRule!=null;
        boolean variantSnowball=variantSnowballRule!=null;
        if(planting&&"snowball".equals(kind))throw new IllegalStateException("Converted item has conflicting snowball and plant placement runtimes: "+id);
        if(seatBedPlacement&&(planting||"snowball".equals(kind)))throw new IllegalStateException("Converted item has conflicting seat-bed placement runtime: "+id);
        if(variantSnowball&&(planting||seatBedPlacement||"snowball".equals(kind)))
            throw new IllegalStateException("Converted item has conflicting variant-snowball runtime: "+id);
        if(planting&&source!=null&&source.hooks().stream().anyMatch(hook->!"identity".equals(hook)))
            throw new IllegalStateException("Source callback unexpectedly survived strict plant placement proof for "+id+": "+source.hooks());
        Item item;
        if(variantSnowball)item=new ConvertedLegacyVariantSnowballItem(properties,variantSnowballRule);
        else if("snowball".equals(kind))item=new SnowballItem(properties);
        else if(planting)item=new ConvertedLegacyPlantingItem(id,properties);
        else if(seatBedPlacement){
            Block target=BLOCKS.get(seatBedRule.id());
            if(!(target instanceof ConvertedLegacySeatBedBlock seatBlock))
                throw new IllegalStateException("Seat-bed placement item registered before specialized target block: "+id+" -> "+seatBedRule.id());
            item=new ConvertedLegacySeatBedItem(seatBlock,properties);
        }else if("bow".equals(kind))item=new ConvertedLegacyBowItem(properties);
        else item=new ConvertedBehaviorItem(properties);
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
        CreativeModeTab tab=FabricItemGroup.builder().icon(()->ConvertedStackPresentation.create(finalIcon,0)).title(title)
                .displayItems((params,output)->snapshot.forEach(item -> {
                    String itemId=BuiltInRegistries.ITEM.getKey(item).toString();
                    for(int metadata:ConvertedCreativeCatalog.metadata(itemId))output.accept(ConvertedStackPresentation.create(item,metadata));
                })).build();
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
