package dev.yinghuang.legacyforgebridge.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyGridPotBlockRegistry;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyGridPotPresentationRuntimePass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyGridPotBlockEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only renderer registration for admitted GridPot stored-content presentation rules. */
public final class ConvertedGridPotPresentationRuntime {
    private static final Map<Identifier,Presentation> BY_ID = new ConcurrentHashMap<>();
    private static final Set<String> INITIALIZED_MODS = ConcurrentHashMap.newKeySet();

    private ConvertedGridPotPresentationRuntime() { }

    public record Presentation(Identifier id, Identifier cellCarrierBlockId, float cellBodyWidth, float cellBodyHeight,
                               List<Float> gridOffsets, float contentTranslateY,
                               float sourceContentScale, boolean boundingBoxCentered,
                               boolean boundingBoxBottomAligned, boolean exactLegacyGeometry,
                               boolean flatInventorySourceProven,boolean flatInventoryModelWired,
                               boolean inventoryUsesSameCellModel) {
        public Presentation {
            gridOffsets = List.copyOf(gridOffsets);
            if (id == null || cellCarrierBlockId==null || !Float.isFinite(cellBodyWidth) || cellBodyWidth<=0F || cellBodyWidth>1F
                    || !Float.isFinite(cellBodyHeight) || cellBodyHeight<=0F || cellBodyHeight>1F
                    || gridOffsets.size() != 3 || !finite(gridOffsets)
                    || !Float.isFinite(contentTranslateY) || contentTranslateY < 0F || contentTranslateY > 1F
                    || !Float.isFinite(sourceContentScale) || sourceContentScale <= 0F || sourceContentScale > 1F
                    || !boundingBoxCentered || !boundingBoxBottomAligned || exactLegacyGeometry
                    || inventoryUsesSameCellModel) {
                throw new IllegalArgumentException("Invalid converted GridPot presentation runtime rule");
            }
        }
        private static boolean finite(List<Float> values) {
            for (Float value : values) if (value == null || !Float.isFinite(value)) return false;
            return true;
        }
    }

    public static void initializeMod(String modId) {
        if (modId == null || modId.isBlank() || !INITIALIZED_MODS.add(modId)) return;
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) {
            INITIALIZED_MODS.remove(modId);
            LegacyForgeBridge.LOGGER.error("GridPot presentation bootstrap could not resolve converted mod {}", modId);
            return;
        }
        var path = container.findPath(LegacyGridPotPresentationRuntimePass.OUTPUT);
        if (path.isEmpty()) return;
        int renderers = 0;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(root, "schemaVersion", 0) != 1
                    || !bool(root, "storedContentPresentationRuntimeWired")
                    || !"SOURCE_PROVEN_FLAT_ITEM_PLUS_BLOCK_CARRIER".equals(string(root, "adaptation"))) return;
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (!bool(value, "storedContentPresentationProven")
                        || !bool(value, "storedContentPresentationRuntimeWired")
                        || bool(value, "exactLegacyGeometry")) continue;
                Presentation presentation = parse(value);
                if (presentation == null || !modId.equals(presentation.id().getNamespace())) continue;
                Presentation previous = BY_ID.putIfAbsent(presentation.id(), presentation);
                if (previous != null && !previous.equals(presentation))
                    throw new IllegalStateException("Conflicting converted GridPot presentation for " + presentation.id());
                BlockEntityType<ConvertedLegacyGridPotBlockEntity> type = LegacyGridPotBlockRegistry.type(presentation.id());
                if (type == null) {
                    LegacyForgeBridge.LOGGER.error("GridPot presentation {} has no registered converted BlockEntityType", presentation.id());
                    continue;
                }
                BlockEntityRenderers.register(type, context -> new ConvertedLegacyGridPotRenderer(context, presentation));
                renderers++;
            }
        } catch (Exception exception) {
            INITIALIZED_MODS.remove(modId);
            LegacyForgeBridge.LOGGER.error("Failed to initialize converted GridPot presentation for {}", modId, exception);
            return;
        }
        if (renderers > 0) LegacyForgeBridge.LOGGER.info(
                "Initialized adapted converted GridPot stored-content presentation: mod={}, renderers={}", modId, renderers);
    }

    public static Presentation presentation(Identifier id) { return id == null ? null : BY_ID.get(id); }

    static Presentation parseForTests(JsonObject value) { return parse(value); }
    static void clearForTests() { BY_ID.clear(); INITIALIZED_MODS.clear(); }

    private static Presentation parse(JsonObject value) {
        try {
            Identifier id = Identifier.parse(required(value, "id"));
            JsonArray offsets = value.getAsJsonArray("gridOffsets");
            if (offsets == null || offsets.size() != 3) return null;
            List<Float> grid = new ArrayList<>(3);
            for (JsonElement offset : offsets) {
                if (!offset.isJsonPrimitive() || !offset.getAsJsonPrimitive().isNumber()) return null;
                grid.add(offset.getAsFloat());
            }
            if (!"NONE".equals(required(value, "itemDisplayContext"))) return null;
            return new Presentation(id, Identifier.parse(required(value,"cellCarrierBlockId")),
                    decimal(value,"cellBodyWidth"),decimal(value,"cellBodyHeight"),grid,
                    decimal(value, "contentTranslateY"), decimal(value, "sourceContentScale"),
                    bool(value, "boundingBoxCentered"), bool(value, "boundingBoxBottomAligned"),
                    bool(value, "exactLegacyGeometry"),bool(value,"flatInventorySourceProven"),
                    bool(value,"flatInventoryModelWired"),bool(value,"inventoryUsesSameCellModel"));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String string(JsonObject value, String key) {
        JsonElement element = value.get(key); return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
    private static String required(JsonObject value, String key) {
        String result = string(value, key); if (result == null || result.isBlank()) throw new IllegalArgumentException("Missing " + key); return result;
    }
    private static int integer(JsonObject value, String key, int fallback) {
        JsonElement element = value.get(key); return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }
    private static float decimal(JsonObject value, String key) {
        JsonElement element = value.get(key); if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException("Missing " + key); return element.getAsFloat();
    }
    private static boolean bool(JsonObject value, String key) {
        JsonElement element = value.get(key); return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
