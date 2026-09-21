package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyMicroBlockContainerPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyMicroBlockBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime catalogue for source-proven N^3 legacy micro-block containers. */
public final class LegacyMicroBlockRegistry {
    private static final Map<Identifier,Rule> RULES = new ConcurrentHashMap<>();
    private static final Map<Identifier,BlockEntityType<ConvertedLegacyMicroBlockBlockEntity>> TYPES = new ConcurrentHashMap<>();

    private LegacyMicroBlockRegistry() { }

    public record Rule(Identifier id, String listNbtKey, String sizeNbtKey,
                       int minFieldSize, int maxFieldSize, int fallbackFieldSize,
                       boolean translucentPass, boolean dynamicCellCollision) {
        public Rule {
            if (id == null || listNbtKey == null || listNbtKey.isBlank()
                    || sizeNbtKey == null || sizeNbtKey.isBlank()
                    || minFieldSize < 1 || maxFieldSize < minFieldSize || maxFieldSize > 32
                    || fallbackFieldSize < minFieldSize || fallbackFieldSize > maxFieldSize
                    || !dynamicCellCollision) {
                throw new IllegalArgumentException("Invalid micro-block runtime rule");
            }
        }
        public int normalizeSize(int raw) {
            return raw >= minFieldSize && raw <= maxFieldSize ? raw : fallbackFieldSize;
        }
    }


    public record LegacyCell(int legacyItemId,int metadata) {
        public static final LegacyCell EMPTY=new LegacyCell(-1,0);
        public LegacyCell {
            if(legacyItemId < -1 || metadata < 0 || metadata > 65535)
                throw new IllegalArgumentException("Invalid legacy micro-block cell");
        }
        public boolean empty(){return legacyItemId<0;}
    }

    public record Snapshot(int fieldSize,List<LegacyCell> cells) {
        public Snapshot {
            if(fieldSize<1||fieldSize>16||cells==null||cells.size()!=fieldSize*fieldSize*fieldSize)
                throw new IllegalArgumentException("Invalid legacy micro-block snapshot");
            cells=List.copyOf(cells);
        }
        public static Snapshot empty(int fieldSize){
            return new Snapshot(fieldSize,java.util.Collections.nCopies(fieldSize*fieldSize*fieldSize,LegacyCell.EMPTY));
        }
        public int occupiedCount(){int count=0;for(LegacyCell cell:cells)if(cell!=null&&!cell.empty())count++;return count;}
    }

    public static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyMicroBlockContainerPass.OUTPUT);
        if (path.isEmpty()) return;
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(root, "schemaVersion", 0) != 1 || !bool(root, "runtimeImplementationWired")) return;
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null) return;
            int loaded = 0;
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (!bool(value, "runtimeComplete")) continue;
                Rule rule = parse(value);
                if (rule == null || !modId.equals(rule.id().getNamespace())) continue;
                Rule previous = RULES.putIfAbsent(rule.id(), rule);
                if (previous != null && !previous.equals(rule))
                    throw new IllegalStateException("Conflicting converted micro-block rule for " + rule.id());
                loaded++;
            }
            if (loaded > 0) LegacyForgeBridge.LOGGER.info(
                    "Loaded converted micro-block container rules: mod={}, rules={}", modId, loaded);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load converted micro-block rules for " + modId, exception);
        }
    }

    public static boolean hasRule(Identifier id) { return id != null && RULES.containsKey(id); }
    public static Rule rule(Identifier id) { return id == null ? null : RULES.get(id); }
    public static int count(){return RULES.size();}
    public static List<Rule> rules(String namespace) {
        List<Rule> result = new ArrayList<>();
        for (Rule rule : RULES.values()) if (namespace.equals(rule.id().getNamespace())) result.add(rule);
        result.sort(java.util.Comparator.comparing(value -> value.id().toString()));
        return List.copyOf(result);
    }

    public static Rule requireRule(Block block) {
        Rule rule = RULES.get(BuiltInRegistries.BLOCK.getKey(block));
        if (rule == null) throw new IllegalStateException("No converted micro-block rule for " + BuiltInRegistries.BLOCK.getKey(block));
        return rule;
    }

    public static synchronized void registerType(Identifier id, Block block) {
        Rule rule = RULES.get(id);
        if (rule == null || TYPES.containsKey(id)) return;
        if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
            @SuppressWarnings("unchecked")
            BlockEntityType<ConvertedLegacyMicroBlockBlockEntity> existing =
                    (BlockEntityType<ConvertedLegacyMicroBlockBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
            TYPES.put(id, existing);
            return;
        }
        BlockEntityType<ConvertedLegacyMicroBlockBlockEntity> type =
                FabricBlockEntityTypeBuilder.create(ConvertedLegacyMicroBlockBlockEntity::new, block).build();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
        TYPES.put(id, type);
    }

    public static BlockEntityType<ConvertedLegacyMicroBlockBlockEntity> type(Identifier id) {
        return id == null ? null : TYPES.get(id);
    }

    public static BlockEntityType<ConvertedLegacyMicroBlockBlockEntity> requireType(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        BlockEntityType<ConvertedLegacyMicroBlockBlockEntity> type = TYPES.get(id);
        if (type == null) throw new IllegalStateException("No converted micro-block BlockEntityType for " + id);
        return type;
    }

    public static BlockState resolveBlockState(int legacyItemId,int metadata) {
        if(legacyItemId<0)return null;
        Identifier identity=LegacyModItemRegistryMap.legacyIdentity(legacyItemId);
        if(identity==null)identity=LegacyModBlockRegistryMap.legacyIdentity(legacyItemId);
        if(identity==null)identity=LegacyModItemRegistryMap.legacyAnyIdentity(legacyItemId);
        if(identity==null)identity=LegacyModBlockRegistryMap.legacyAnyIdentity(legacyItemId);
        if(identity==null)return null;
        identity=modernVanillaBlock(identity,metadata);
        Block block=null;
        if(BuiltInRegistries.BLOCK.containsKey(identity))block=BuiltInRegistries.BLOCK.getValue(identity);
        if(block==null&&BuiltInRegistries.ITEM.containsKey(identity)){
            Item item=BuiltInRegistries.ITEM.getValue(identity);
            if(item instanceof BlockItem blockItem)block=blockItem.getBlock();
        }
        if(block==null)return null;
        BlockState state=block.defaultBlockState();
        if(state.hasProperty(ConvertedLegacyBlock.LEGACY_META))
            state=ConvertedLegacyBlock.withLegacyMeta(state,metadata&15);
        return state;
    }

    /** Source empty-state selector: ForgeDirection metadata DOWN/UP/NORTH/SOUTH/WEST/EAST. */
    public static LegacyGeometry.Box emptyFace(int metadata) {
        double e=.001D;
        return switch(metadata){
            case 0 -> new LegacyGeometry.Box(0,0,0,1,e,1);
            case 1 -> new LegacyGeometry.Box(0,1-e,0,1,1,1);
            case 2 -> new LegacyGeometry.Box(0,0,0,1,1,e);
            case 3 -> new LegacyGeometry.Box(0,0,1-e,1,1,1);
            case 4 -> new LegacyGeometry.Box(0,0,0,e,1,1);
            case 5 -> new LegacyGeometry.Box(1-e,0,0,1,1,1);
            default -> LegacyGeometry.FULL;
        };
    }

    private static Identifier modernVanillaBlock(Identifier source,int metadata) {
        if(!"minecraft".equals(source.getNamespace()))return source;
        String path=source.getPath();
        String[] dye={"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
        String[] wood={"oak","spruce","birch","jungle","acacia","dark_oak"};
        String modern=switch(path){
            case "grass" -> "grass_block";
            case "brick_block" -> "bricks";
            case "lit_pumpkin" -> "jack_o_lantern";
            case "web" -> "cobweb";
            case "waterlily" -> "lily_pad";
            case "reeds" -> "sugar_cane";
            case "wooden_door" -> "oak_door";
            case "wooden_pressure_plate" -> "oak_pressure_plate";
            case "wooden_button" -> "oak_button";
            case "fence" -> "oak_fence";
            case "fence_gate" -> "oak_fence_gate";
            case "stained_hardened_clay" -> dye[metadata&15]+"_terracotta";
            case "stained_glass" -> dye[metadata&15]+"_stained_glass";
            case "stained_glass_pane" -> dye[metadata&15]+"_stained_glass_pane";
            case "wool" -> dye[metadata&15]+"_wool";
            case "carpet" -> dye[metadata&15]+"_carpet";
            case "planks" -> wood[Math.min(metadata&7,5)]+"_planks";
            case "wooden_slab" -> wood[Math.min(metadata&7,5)]+"_slab";
            case "log" -> wood[metadata&3]+"_log";
            case "log2" -> wood[4+Math.min(metadata&1,1)]+"_log";
            case "leaves" -> wood[metadata&3]+"_leaves";
            case "leaves2" -> wood[4+Math.min(metadata&1,1)]+"_leaves";
            case "sand" -> (metadata&1)==1?"red_sand":"sand";
            case "dirt" -> switch(metadata&3){case 1->"coarse_dirt";case 2->"podzol";default->"dirt";};
            case "stonebrick" -> switch(metadata&3){case 1->"mossy_stone_bricks";case 2->"cracked_stone_bricks";case 3->"chiseled_stone_bricks";default->"stone_bricks";};
            case "monster_egg" -> switch(metadata&7){case 1->"infested_cobblestone";case 2->"infested_stone_bricks";case 3->"infested_mossy_stone_bricks";case 4->"infested_cracked_stone_bricks";case 5->"infested_chiseled_stone_bricks";default->"infested_stone";};
            default -> path;
        };
        try{return Identifier.fromNamespaceAndPath("minecraft",modern);}catch(RuntimeException invalid){return source;}
    }

    private static Rule parse(JsonObject value) {
        try {
            String id = string(value, "id");
            if (id == null) return null;
            return new Rule(
                    Identifier.parse(id),
                    required(value, "listNbtKey"),
                    required(value, "sizeNbtKey"),
                    integer(value, "minFieldSize", 0),
                    integer(value, "maxFieldSize", 0),
                    integer(value, "fallbackFieldSize", 0),
                    bool(value, "translucentPass"),
                    bool(value, "dynamicCellCollision")
            );
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String required(JsonObject value, String key) {
        String result = string(value, key);
        if (result == null || result.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return result;
    }
    private static String string(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
    private static int integer(JsonObject value, String key, int fallback) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }
    private static boolean bool(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    static synchronized void clearForTests() { RULES.clear(); TYPES.clear(); }
}
