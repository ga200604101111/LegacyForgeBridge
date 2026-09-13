package dev.longyu.legacyforgebridge.convert.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
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
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registers loader-safe content emitted by the legacy conversion engine. */
public final class ConvertedContentRuntime {
    public static final String MANIFEST_PATH = "legacyforgebridge/converted-content.json";

    private ConvertedContentRuntime() {
    }

    public static void initialize() {
        List<Item> combat = new ArrayList<>();
        List<Item> ingredients = new ArrayList<>();
        int manifests = 0;
        int items = 0;
        int creativeTabs = 0;

        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            var path = container.findPath(MANIFEST_PATH);
            if (path.isEmpty()) {
                continue;
            }

            manifests++;
            try {
                JsonObject root = readJson(path.get());
                JsonArray contentItems = root.getAsJsonArray("items");
                if (contentItems == null) {
                    continue;
                }

                Map<Identifier, Item> manifestItems = new LinkedHashMap<>();
                for (JsonElement element : contentItems) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject definition = element.getAsJsonObject();
                    Item registered = registerItem(definition);
                    if (registered == null) {
                        continue;
                    }
                    items++;
                    Identifier itemId = Identifier.parse(requiredString(definition, "id"));
                    manifestItems.put(itemId, registered);

                    // A converted custom creative group owns the item exclusively. The old
                    // combat/ingredients fields remain supported for manifests produced before
                    // the generic creative-tab schema existed.
                    if (definition.has("creativeTab")) {
                        continue;
                    }
                    String tab = string(definition, "tab", "ingredients");
                    if (tab.equals("combat")) {
                        combat.add(registered);
                    } else {
                        ingredients.add(registered);
                    }
                }

                creativeTabs += registerCreativeTabs(root, manifestItems, container.getMetadata().getId());
            } catch (Exception exception) {
                LegacyForgeBridge.LOGGER.error(
                        "Failed to register converted legacy content from {}",
                        container.getMetadata().getId(),
                        exception
                );
            }
        }

        if (!combat.isEmpty()) {
            List<Item> snapshot = List.copyOf(combat);
            ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT)
                    .register(entries -> snapshot.forEach(entries::accept));
        }
        if (!ingredients.isEmpty()) {
            List<Item> snapshot = List.copyOf(ingredients);
            ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
                    .register(entries -> snapshot.forEach(entries::accept));
        }

        if (manifests > 0) {
            LegacyForgeBridge.LOGGER.info(
                    "Registered converted legacy content: manifests={}, items={}, creativeTabs={}",
                    manifests,
                    items,
                    creativeTabs
            );
        }
    }

    private static int registerCreativeTabs(
            JsonObject root,
            Map<Identifier, Item> manifestItems,
            String sourceModId
    ) {
        JsonArray definitions = root.getAsJsonArray("creativeTabs");
        if (definitions == null) {
            return 0;
        }

        int registeredCount = 0;
        for (JsonElement element : definitions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject definition = element.getAsJsonObject();
            Identifier id = Identifier.parse(requiredString(definition, "id"));
            if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id)) {
                LegacyForgeBridge.LOGGER.warn(
                        "Converted creative tab {} from {} already exists; keeping the existing tab",
                        id,
                        sourceModId
                );
                continue;
            }

            List<Item> entries = new ArrayList<>();
            JsonArray itemIds = definition.getAsJsonArray("items");
            if (itemIds != null) {
                for (JsonElement itemElement : itemIds) {
                    if (!itemElement.isJsonPrimitive()) {
                        continue;
                    }
                    Identifier itemId = Identifier.parse(itemElement.getAsString());
                    Item item = manifestItems.get(itemId);
                    if (item == null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                        item = (Item) BuiltInRegistries.ITEM.getValue(itemId);
                    }
                    if (item != null) {
                        entries.add(item);
                    }
                }
            }
            if (entries.isEmpty()) {
                LegacyForgeBridge.LOGGER.warn("Converted creative tab {} has no resolvable items; skipping it", id);
                continue;
            }

            Item icon = resolveIcon(definition, manifestItems, entries.getFirst());
            Component title = definition.has("titleKey")
                    ? Component.translatable(requiredString(definition, "titleKey"))
                    : Component.literal(string(definition, "title", id.toString()));
            List<Item> snapshot = List.copyOf(entries);
            CreativeModeTab tab = FabricItemGroup.builder()
                    .icon(() -> new ItemStack(icon))
                    .title(title)
                    .displayItems((params, output) -> snapshot.forEach(output::accept))
                    .build();
            ResourceKey<CreativeModeTab> key = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), id);
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
            registeredCount++;
        }
        return registeredCount;
    }

    private static Item resolveIcon(JsonObject definition, Map<Identifier, Item> manifestItems, Item fallback) {
        JsonElement iconElement = definition.get("icon");
        if (iconElement == null || !iconElement.isJsonPrimitive()) {
            return fallback;
        }
        try {
            Identifier iconId = Identifier.parse(iconElement.getAsString());
            Item item = manifestItems.get(iconId);
            if (item != null) {
                return item;
            }
            if (BuiltInRegistries.ITEM.containsKey(iconId)) {
                return (Item) BuiltInRegistries.ITEM.getValue(iconId);
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static Item registerItem(JsonObject definition) {
        Identifier id = Identifier.parse(requiredString(definition, "id"));
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            LegacyForgeBridge.LOGGER.warn("Converted item {} already exists; keeping the existing registry entry", id);
            return (Item) BuiltInRegistries.ITEM.getValue(id);
        }

        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        String description = string(definition, "descriptionKey", "item." + id.getNamespace() + "." + id.getPath());
        String kind = string(definition, "kind", "item");
        int durability = integer(definition, "durability", 0);

        Item.Properties properties = new Item.Properties()
                .setId(key)
                .overrideDescription(description);

        if (kind.equals("sword")) {
            float damage = number(definition, "attackDamage", 7.0F);
            float attackSpeed = number(definition, "attackSpeed", -2.4F);
            properties.sword(
                    ToolMaterial.DIAMOND,
                    damage - ToolMaterial.DIAMOND.attackDamageBonus(),
                    attackSpeed
            );
            if (durability > 0) {
                properties.durability(durability);
            }
        } else if (kind.equals("wing") || kind.equals("circle")) {
            EquipmentSlot slot = kind.equals("wing") ? EquipmentSlot.CHEST : EquipmentSlot.FEET;
            EquipmentSlotGroup group = kind.equals("wing") ? EquipmentSlotGroup.CHEST : EquipmentSlotGroup.FEET;
            double armor = number(definition, "armor", kind.equals("wing") ? 10.0F : 4.0F);
            Identifier modifierId = Identifier.fromNamespaceAndPath(
                    id.getNamespace(),
                    "converted/" + id.getPath() + "_armor"
            );
            ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
                    .add(
                            Attributes.ARMOR,
                            new AttributeModifier(modifierId, armor, AttributeModifier.Operation.ADD_VALUE),
                            group
                    )
                    .build();
            properties.equippable(slot).attributes(attributes);
            if (durability > 0) {
                properties.durability(durability);
            }
        }

        Item item = new Item(properties);
        Registry.register(BuiltInRegistries.ITEM, key, item);
        LegacyForgeBridge.LOGGER.debug("Registered converted legacy item {} kind={}", id, kind);
        return item;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing converted-content string: " + key);
        }
        return value.getAsString();
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static float number(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
    }
}
