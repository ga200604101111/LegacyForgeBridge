package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Platform-level bridge for names that Forge 1.7.10 itself installs in
 * {@code OreDictionary.initVanillaEntries()}.
 *
 * <p>The old name list is a Forge platform contract, not a mod-specific table. Modern targets use
 * vanilla/Fabric conventional tags where an equivalent interoperability category exists. For old
 * categories with no safe modern category, the Forge-provided vanilla member is retained exactly.
 * Mod-owned registrations are unioned by {@link LegacyOreDictionaryIndex} on top of these seeds.</p>
 */
public final class LegacyOreDictionaryConventions1710 {
    private static final Map<String, String> TAGS;
    private static final Map<String, String> EXACT_ITEMS;

    static {
        Map<String, String> tags = new LinkedHashMap<>();

        // Forge 1.7 wood/tree categories.
        tags.put("logWood", "minecraft:logs");
        tags.put("plankWood", "minecraft:planks");
        tags.put("slabWood", "minecraft:wooden_slabs");
        tags.put("stairWood", "minecraft:wooden_stairs");
        tags.put("stickWood", "c:rods/wooden");
        tags.put("treeSapling", "minecraft:saplings");
        tags.put("treeLeaves", "minecraft:leaves");

        // Forge 1.7 ores.
        tags.put("oreGold", "c:ores/gold");
        tags.put("oreIron", "c:ores/iron");
        tags.put("oreLapis", "c:ores/lapis");
        tags.put("oreDiamond", "c:ores/diamond");
        tags.put("oreRedstone", "c:ores/redstone");
        tags.put("oreEmerald", "c:ores/emerald");
        tags.put("oreQuartz", "c:ores/quartz");
        tags.put("oreCoal", "c:ores/coal");

        // Storage blocks. Quartz is deliberately not generalized: modern convention tags do not
        // classify quartz block as a lossless storage block.
        tags.put("blockGold", "c:storage_blocks/gold");
        tags.put("blockIron", "c:storage_blocks/iron");
        tags.put("blockLapis", "c:storage_blocks/lapis");
        tags.put("blockDiamond", "c:storage_blocks/diamond");
        tags.put("blockRedstone", "c:storage_blocks/redstone");
        tags.put("blockEmerald", "c:storage_blocks/emerald");
        tags.put("blockCoal", "c:storage_blocks/coal");

        // Glass.
        tags.put("blockGlassColorless", "c:glass_blocks/colorless");
        tags.put("blockGlass", "c:glass_blocks");
        tags.put("paneGlassColorless", "c:glass_panes/colorless");
        tags.put("paneGlass", "c:glass_panes");

        // Ingots, nuggets, gems, dusts.
        tags.put("ingotIron", "c:ingots/iron");
        tags.put("ingotGold", "c:ingots/gold");
        tags.put("ingotBrick", "c:bricks/normal");
        tags.put("ingotBrickNether", "c:bricks/nether");
        tags.put("nuggetGold", "c:nuggets/gold");
        tags.put("gemDiamond", "c:gems/diamond");
        tags.put("gemEmerald", "c:gems/emerald");
        tags.put("gemQuartz", "c:gems/quartz");
        tags.put("gemLapis", "c:gems/lapis");
        tags.put("dustRedstone", "c:dusts/redstone");
        tags.put("dustGlowstone", "c:dusts/glowstone");
        tags.put("slimeball", "c:slime_balls");

        // Crops/material categories.
        tags.put("cropWheat", "c:crops/wheat");
        tags.put("cropPotato", "c:crops/potato");
        tags.put("cropCarrot", "c:crops/carrot");
        tags.put("stone", "c:stones");
        tags.put("cobblestone", "c:cobblestones");
        tags.put("sandstone", "c:sandstone/blocks");
        tags.put("sand", "c:sands");

        // Dyes. Forge 1.7 adds both generic dye and all 16 dye<Color> names.
        tags.put("dye", "c:dyes");
        String[][] dyes = {
                {"Black", "black"}, {"Red", "red"}, {"Green", "green"}, {"Brown", "brown"},
                {"Blue", "blue"}, {"Purple", "purple"}, {"Cyan", "cyan"}, {"LightGray", "light_gray"},
                {"Gray", "gray"}, {"Pink", "pink"}, {"Lime", "lime"}, {"Yellow", "yellow"},
                {"LightBlue", "light_blue"}, {"Magenta", "magenta"}, {"Orange", "orange"}, {"White", "white"}
        };
        for (String[] dye : dyes) tags.put("dye" + dye[0], "c:dyes/" + dye[1]);

        // Every vanilla music disc is a member and modern Minecraft already exposes the exact tag.
        tags.put("record", "minecraft:music_discs");
        TAGS = Map.copyOf(tags);

        Map<String, String> exact = new LinkedHashMap<>();
        exact.put("blockQuartz", "minecraft:quartz_block");
        exact.put("glowstone", "minecraft:glowstone");
        EXACT_ITEMS = Map.copyOf(exact);
    }

    private LegacyOreDictionaryConventions1710() { }

    public static Optional<JsonElement> ingredient(String oreName) {
        String tag = TAGS.get(oreName);
        if (tag != null) return Optional.of(new JsonPrimitive("#" + tag));
        String exact = EXACT_ITEMS.get(oreName);
        return exact == null ? Optional.empty() : Optional.of(new JsonPrimitive(exact));
    }

    static Map<String, String> tags() {
        return TAGS;
    }
}
