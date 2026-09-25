package dev.yinghuang.legacyforgebridge.convert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Version-locked Minecraft 1.7.10 Material harvest fast-path facts.
 *
 * <p>MCP 908's Material class initializes {@code field_76241_J} to {@code true};
 * {@code func_76221_f()} flips it to {@code false}; and {@code func_76229_l()} returns that field.
 * Forge 1.7.10 maps {@code func_76229_l()} to {@code isToolNotRequired()} and checks it before the
 * held-tool/player fallback in {@code ForgeHooks.canHarvestBlock}. This table therefore records only
 * exact vanilla static Material fields whose constructor chains are known for 1.7.10.</p>
 */
public final class LegacyMaterialHarvestRules1710 {
    public static final String MATERIAL_OWNER = "net/minecraft/block/material/Material";
    public static final String MATERIAL_DESCRIPTOR = "Lnet/minecraft/block/material/Material;";

    public record Rule(String srgFieldName, String namedMaterial, boolean toolNotRequired) { }

    private static final List<Rule> RULE_LIST = List.of(
            new Rule("field_151579_a", "air", true),
            new Rule("field_151577_b", "grass", true),
            new Rule("field_151578_c", "ground", true),
            new Rule("field_151575_d", "wood", true),
            new Rule("field_151576_e", "rock", false),
            new Rule("field_151573_f", "iron", false),
            new Rule("field_151574_g", "anvil", false),
            new Rule("field_151586_h", "water", true),
            new Rule("field_151587_i", "lava", true),
            new Rule("field_151584_j", "leaves", true),
            new Rule("field_151585_k", "plants", true),
            new Rule("field_151582_l", "vine", true),
            new Rule("field_151583_m", "sponge", true),
            new Rule("field_151580_n", "cloth", true),
            new Rule("field_151581_o", "fire", true),
            new Rule("field_151595_p", "sand", true),
            new Rule("field_151594_q", "circuits", true),
            new Rule("field_151593_r", "carpet", true),
            new Rule("field_151592_s", "glass", true),
            new Rule("field_151591_t", "redstoneLight", true),
            new Rule("field_151590_u", "tnt", true),
            new Rule("field_151589_v", "coral", true),
            new Rule("field_151588_w", "ice", true),
            new Rule("field_151598_x", "packedIce", true),
            new Rule("field_151597_y", "snow", false),
            new Rule("field_151596_z", "craftedSnow", false),
            new Rule("field_151570_A", "cactus", true),
            new Rule("field_151571_B", "clay", true),
            new Rule("field_151572_C", "gourd", true),
            new Rule("field_151566_D", "dragonEgg", true),
            new Rule("field_151567_E", "portal", true),
            new Rule("field_151568_F", "cake", true),
            new Rule("field_151569_G", "web", false),
            new Rule("field_76233_E", "piston", true)
    );
    private static final Map<String, Rule> RULES = index();

    private LegacyMaterialHarvestRules1710() { }

    public static Optional<Rule> lookup(String owner, String fieldName, String descriptor) {
        if (!MATERIAL_OWNER.equals(owner) || !MATERIAL_DESCRIPTOR.equals(descriptor) || fieldName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RULES.get(fieldName));
    }

    public static List<Rule> rules() {
        return RULE_LIST;
    }

    private static Map<String, Rule> index() {
        Map<String, Rule> values = new LinkedHashMap<>();
        for (Rule rule : RULE_LIST) {
            Rule previous = values.put(rule.srgFieldName(), rule);
            if (previous != null) throw new IllegalStateException("Duplicate 1.7.10 Material field " + rule.srgFieldName());
        }
        return Map.copyOf(values);
    }
}
