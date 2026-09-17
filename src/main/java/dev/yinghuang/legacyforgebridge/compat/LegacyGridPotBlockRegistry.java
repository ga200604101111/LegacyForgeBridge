package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyGridPotBlockPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyGridPotBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime table for the proof-complete core of legacy 3x3 grid-pot BlockEntities. */
public final class LegacyGridPotBlockRegistry {
    private static final Map<Identifier, Rule> RULES = new ConcurrentHashMap<>();
    private static final Map<Identifier, BlockEntityType<ConvertedLegacyGridPotBlockEntity>> TYPES = new ConcurrentHashMap<>();

    private LegacyGridPotBlockRegistry() { }

    public record Rule(Identifier id, int cells, int gridWidth, float baseHeight, float cellHeight,
                       boolean placementCreatesCell, boolean emptyHandRemovalProven,
                       boolean selfItemAddsCellProven, boolean breakDropsEveryEnabledCell,
                       boolean normalBlockDropDisabled, boolean persistenceProven,
                       boolean dynamicCellShapeProven, boolean nonOpaqueProven,
                       boolean legacyInsertionPredicateProven,
                       boolean sourceProvenModContentInsertionWired,
                       Set<Identifier> sourceProvenInsertionBlockIds,
                       boolean contentInsertionRuntimeComplete,
                       boolean presentationRuntimeComplete) {
        public Rule {
            sourceProvenInsertionBlockIds = Set.copyOf(sourceProvenInsertionBlockIds == null ? Set.of() : sourceProvenInsertionBlockIds);
            if (id == null || cells != 9 || gridWidth != 3
                    || !(baseHeight > 0F && baseHeight <= 1F)
                    || !(cellHeight > baseHeight && cellHeight <= 1F)
                    || !placementCreatesCell || !emptyHandRemovalProven || !selfItemAddsCellProven
                    || !breakDropsEveryEnabledCell || !normalBlockDropDisabled || !persistenceProven
                    || !dynamicCellShapeProven || !nonOpaqueProven) {
                throw new IllegalArgumentException("Invalid converted grid-pot core rule");
            }
            if (sourceProvenModContentInsertionWired
                    && (!legacyInsertionPredicateProven || sourceProvenInsertionBlockIds.isEmpty())) {
                throw new IllegalArgumentException("Grid-pot positive insertion subset lacks source predicate/eligible identities");
            }
            if (!sourceProvenModContentInsertionWired && !sourceProvenInsertionBlockIds.isEmpty()) {
                throw new IllegalArgumentException("Grid-pot insertion identities present without runtime wiring");
            }
            if (contentInsertionRuntimeComplete || presentationRuntimeComplete) {
                throw new IllegalArgumentException("Full grid-pot insertion/presentation runtime not admitted by schema 1");
            }
        }

        public boolean insertionEligible(Identifier blockId) {
            return sourceProvenModContentInsertionWired && blockId != null && sourceProvenInsertionBlockIds.contains(blockId);
        }
    }

    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyGridPotBlockPass.OUTPUT);
        if (path.isEmpty()) return;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(root, "schemaVersion", 0) != 1) return;
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            int loaded = 0;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (!bool(value, "coreRuntimeComplete")) continue;
                Rule rule = parse(value);
                if (rule == null || !modId.equals(rule.id().getNamespace())) continue;
                Rule previous = RULES.putIfAbsent(rule.id(), rule);
                if (previous != null && !previous.equals(rule))
                    throw new IllegalStateException("Conflicting converted grid-pot rule for " + rule.id());
                loaded++;
            }
            if (loaded > 0) LegacyForgeBridge.LOGGER.info(
                    "Loaded converted grid-pot core rules: mod={}, rules={}", modId, loaded);
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted grid-pot rules for {}", modId, exception);
        }
    }

    public static boolean hasRule(Identifier id) { return id != null && RULES.containsKey(id); }
    public static Rule rule(Identifier id) { return id == null ? null : RULES.get(id); }

    public static Rule requireRule(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        Rule rule = RULES.get(id);
        if (rule == null) throw new IllegalStateException("No converted grid-pot rule registered for block " + id);
        return rule;
    }

    public static synchronized void registerType(Identifier id, Block block) {
        Rule rule = RULES.get(id);
        if (rule == null || TYPES.containsKey(id)) return;
        if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
            @SuppressWarnings("unchecked")
            BlockEntityType<ConvertedLegacyGridPotBlockEntity> existing =
                    (BlockEntityType<ConvertedLegacyGridPotBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            TYPES.put(id, existing);
            return;
        }
        BlockEntityType<ConvertedLegacyGridPotBlockEntity> type =
                FabricBlockEntityTypeBuilder.create(ConvertedLegacyGridPotBlockEntity::new, block).build();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
        TYPES.put(id, type);
    }

    public static BlockEntityType<ConvertedLegacyGridPotBlockEntity> requireType(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        BlockEntityType<ConvertedLegacyGridPotBlockEntity> type = TYPES.get(id);
        if (type == null) throw new IllegalStateException("No converted grid-pot BlockEntityType registered for " + id);
        return type;
    }

    static synchronized void clearForTests() { RULES.clear(); TYPES.clear(); }
    static Rule parseForTests(JsonObject value) { return parse(value); }

    private static Rule parse(JsonObject value) {
        try {
            String idValue = string(value, "id");
            if (idValue == null) return null;
            boolean subset = bool(value, "sourceProvenModContentInsertionWired");
            Set<Identifier> eligible = identifiers(value.get("sourceProvenInsertionBlockIds"));
            return new Rule(
                    Identifier.parse(idValue),
                    integer(value, "cells", 0), integer(value, "gridWidth", 0),
                    decimal(value, "baseHeight", 0F), decimal(value, "cellHeight", 0F),
                    bool(value, "placementCreatesCell"), bool(value, "emptyHandRemovalProven"),
                    bool(value, "selfItemAddsCellProven"), bool(value, "breakDropsEveryEnabledCell"),
                    bool(value, "normalBlockDropDisabled"), bool(value, "persistenceProven"),
                    bool(value, "dynamicCellShapeProven"), bool(value, "nonOpaqueProven"),
                    bool(value, "legacyInsertionPredicateProven"), subset, eligible,
                    bool(value, "contentInsertionRuntimeComplete"), bool(value, "presentationRuntimeComplete")
            );
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static Set<Identifier> identifiers(JsonElement element) {
        if (element == null || !element.isJsonArray()) return Set.of();
        LinkedHashSet<Identifier> values = new LinkedHashSet<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid grid-pot insertion id");
            if (!values.add(Identifier.parse(item.getAsString()))) throw new IllegalArgumentException("Duplicate grid-pot insertion id");
        }
        return Set.copyOf(values);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
    private static float decimal(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
    }
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }
}
