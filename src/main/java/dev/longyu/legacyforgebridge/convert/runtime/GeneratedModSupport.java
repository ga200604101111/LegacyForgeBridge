package dev.longyu.legacyforgebridge.convert.runtime;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stable low-level API used by code generated into converted mods.
 *
 * <p>This class does not discover mods and does not read conversion manifests. Mod-specific IDs,
 * values and grouping are bytecode constants in the generated candidate; this class only maps
 * those already-decided semantics onto the current Minecraft/Fabric registration API.</p>
 */
public final class GeneratedModSupport {
    private static final Map<Identifier, Item> ITEMS = new ConcurrentHashMap<>();
    private static final Set<String> ACTIVE_MODS = ConcurrentHashMap.newKeySet();
    private static final Map<String, int[]> COUNTS = new ConcurrentHashMap<>();

    private GeneratedModSupport() {
    }

    public static void beginMod(String modId) {
        if (!ACTIVE_MODS.add(modId)) {
            return;
        }
        COUNTS.put(modId, new int[2]);
    }

    public static void registerItem(
            String idValue,
            String kind,
            String descriptionKey,
            int durability,
            float attackDamage,
            float attackSpeed,
            float armor
    ) {
        Identifier id = Identifier.parse(idValue);
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            ITEMS.put(id, (Item) BuiltInRegistries.ITEM.getValue(id));
            return;
        }

        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties properties = new Item.Properties().setId(key).overrideDescription(descriptionKey);
        if ("sword".equals(kind)) {
            properties.sword(
                    ToolMaterial.DIAMOND,
                    attackDamage - ToolMaterial.DIAMOND.attackDamageBonus(),
                    attackSpeed
            );
            if (durability > 0) properties.durability(durability);
        } else if ("wing".equals(kind) || "circle".equals(kind)) {
            EquipmentSlot slot = "wing".equals(kind) ? EquipmentSlot.CHEST : EquipmentSlot.FEET;
            EquipmentSlotGroup group = "wing".equals(kind) ? EquipmentSlotGroup.CHEST : EquipmentSlotGroup.FEET;
            Identifier modifierId = Identifier.fromNamespaceAndPath(id.getNamespace(), "converted/" + id.getPath() + "_armor");
            ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
                    .add(Attributes.ARMOR, new AttributeModifier(modifierId, armor, AttributeModifier.Operation.ADD_VALUE), group)
                    .build();
            properties.equippable(slot).attributes(attributes);
            if (durability > 0) properties.durability(durability);
        }

        Item item = new Item(properties);
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ITEMS.put(id, item);
        int[] counts = COUNTS.get(id.getNamespace());
        if (counts != null) counts[0]++;
    }

    public static void registerCreativeTab(
            String idValue,
            String titleKey,
            String literalTitle,
            String iconValue,
            String[] itemValues
    ) {
        Identifier id = Identifier.parse(idValue);
        if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id)) return;

        List<Item> entries = new ArrayList<>();
        for (String value : itemValues) {
            Item item = resolveItem(Identifier.parse(value));
            if (item != null) entries.add(item);
        }
        if (entries.isEmpty()) return;

        Item icon = resolveItem(Identifier.parse(iconValue));
        if (icon == null) icon = entries.getFirst();
        Item finalIcon = icon;
        List<Item> snapshot = List.copyOf(entries);
        Component title = titleKey == null || titleKey.isBlank()
                ? Component.literal(literalTitle)
                : Component.translatable(titleKey);
        CreativeModeTab tab = FabricItemGroup.builder()
                .icon(() -> new ItemStack(finalIcon))
                .title(title)
                .displayItems((params, output) -> snapshot.forEach(output::accept))
                .build();
        ResourceKey<CreativeModeTab> key = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), id);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
        int[] counts = COUNTS.get(id.getNamespace());
        if (counts != null) counts[1]++;
    }

    public static void finishMod(String modId) {
        int[] counts = COUNTS.getOrDefault(modId, new int[2]);
        LegacyForgeBridge.LOGGER.info(
                "Generated converted mod initialized: mod={}, generatedItems={}, generatedCreativeTabs={}",
                modId,
                counts[0],
                counts[1]
        );
    }

    private static Item resolveItem(Identifier id) {
        Item item = ITEMS.get(id);
        if (item != null) return item;
        return BuiltInRegistries.ITEM.containsKey(id) ? (Item) BuiltInRegistries.ITEM.getValue(id) : null;
    }
}
