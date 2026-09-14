package dev.longyu.legacyforgebridge.convert.runtime;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.behavior.ConvertedBehaviorItem;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Native registry adapter. Item-specific constants and source callbacks belong to the converted mod. */
public final class GeneratedModSupport {
    private static final Map<Identifier,Item> ITEMS=new ConcurrentHashMap<>();
    private static final Set<String> ACTIVE_MODS=ConcurrentHashMap.newKeySet();
    private static final Map<String,int[]> COUNTS=new ConcurrentHashMap<>();
    private GeneratedModSupport() { }
    public static void beginMod(String modId){if(ACTIVE_MODS.add(modId))COUNTS.put(modId,new int[2]);}
    public static void registerItem(String idValue,String kind,String descriptionKey,int durability,float attackDamage,float attackSpeed,float armor){
        Identifier id=Identifier.parse(idValue);
        if(BuiltInRegistries.ITEM.containsKey(id)){ITEMS.put(id,BuiltInRegistries.ITEM.getValue(id));return;}
        ResourceKey<Item> key=ResourceKey.create(Registries.ITEM,id);
        Item.Properties properties=new Item.Properties().setId(key).overrideDescription(descriptionKey);
        var source=LegacyBehaviorRegistry.item(idValue);
        if(source!=null){
            int count=source.item().maximumStackSize;
            if(count>=1&&count<=99)properties.stacksTo(count);
            if(source.item().durability>0)durability=source.item().durability;
        }
        if("sword".equals(kind))properties.sword(ToolMaterial.DIAMOND,attackDamage-ToolMaterial.DIAMOND.attackDamageBonus(),attackSpeed);
        // The admitted source hit callback includes its own inherited sword wear.
        // Native postHurtEnemy must not apply a second durability charge.
        if(source!=null && source.hooks().contains("hit"))properties.component(DataComponents.WEAPON,new Weapon(0));
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
        Registry.register(BuiltInRegistries.ITEM,key,item);ITEMS.put(id,item);
        int[] counts=COUNTS.get(id.getNamespace());if(counts!=null)counts[0]++;
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
        int[] counts=COUNTS.get(id.getNamespace());if(counts!=null)counts[1]++;
    }
    public static void finishMod(String modId){
        int[] counts=COUNTS.getOrDefault(modId,new int[2]);
        LegacyForgeBridge.LOGGER.info("Generated converted mod initialized: mod={}, generatedItems={}, generatedCreativeTabs={}, sourceBehaviors={}",modId,counts[0],counts[1],LegacyBehaviorRegistry.itemCount(modId));
    }
    private static Item resolveItem(Identifier id){Item item=ITEMS.get(id);return item!=null?item:BuiltInRegistries.ITEM.containsKey(id)?BuiltInRegistries.ITEM.getValue(id):null;}
}
