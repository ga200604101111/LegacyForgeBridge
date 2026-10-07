package dev.yinghuang.legacyforgebridge.convert;

import java.util.Map;
import java.util.Optional;

/** Fixed vanilla 1.7.10 Enchantment static-field identities mapped to modern registry ids. */
public final class LegacyVanillaEnchantment1710 {
    private static final String OWNER = "net/minecraft/enchantment/Enchantment";
    private static final String DESC = "Lnet/minecraft/enchantment/Enchantment;";
    private static final Map<String,String> IDS = Map.ofEntries(
            entry("field_77332_c","minecraft:protection"), entry("protection","minecraft:protection"),
            entry("field_77329_d","minecraft:fire_protection"), entry("fireProtection","minecraft:fire_protection"),
            entry("field_77330_e","minecraft:feather_falling"), entry("featherFalling","minecraft:feather_falling"),
            entry("field_77327_f","minecraft:blast_protection"), entry("blastProtection","minecraft:blast_protection"),
            entry("field_77328_g","minecraft:projectile_protection"), entry("projectileProtection","minecraft:projectile_protection"),
            entry("field_77340_h","minecraft:respiration"), entry("respiration","minecraft:respiration"),
            entry("field_77341_i","minecraft:aqua_affinity"), entry("aquaAffinity","minecraft:aqua_affinity"),
            entry("field_92091_k","minecraft:thorns"), entry("thorns","minecraft:thorns"),
            entry("field_77338_j","minecraft:sharpness"), entry("sharpness","minecraft:sharpness"),
            entry("field_77339_k","minecraft:smite"), entry("smite","minecraft:smite"),
            entry("field_77336_l","minecraft:bane_of_arthropods"), entry("baneOfArthropods","minecraft:bane_of_arthropods"),
            entry("field_77337_m","minecraft:knockback"), entry("knockback","minecraft:knockback"),
            entry("field_77334_n","minecraft:fire_aspect"), entry("fireAspect","minecraft:fire_aspect"),
            entry("field_77335_o","minecraft:looting"), entry("looting","minecraft:looting"),
            entry("field_77349_p","minecraft:efficiency"), entry("efficiency","minecraft:efficiency"),
            entry("field_77348_q","minecraft:silk_touch"), entry("silkTouch","minecraft:silk_touch"),
            entry("field_77347_r","minecraft:unbreaking"), entry("unbreaking","minecraft:unbreaking"),
            entry("field_77346_s","minecraft:fortune"), entry("fortune","minecraft:fortune"),
            entry("field_77345_t","minecraft:power"), entry("power","minecraft:power"),
            entry("field_77344_u","minecraft:punch"), entry("punch","minecraft:punch"),
            entry("field_77343_v","minecraft:flame"), entry("flame","minecraft:flame"),
            entry("field_77342_w","minecraft:infinity"), entry("infinity","minecraft:infinity"),
            entry("field_151370_z","minecraft:luck_of_the_sea"), entry("luckOfTheSea","minecraft:luck_of_the_sea"),
            entry("field_151369_A","minecraft:lure"), entry("lure","minecraft:lure")
    );

    private LegacyVanillaEnchantment1710() { }

    public static Optional<String> resolve(LegacyRecipeAnalyzer.FieldValue field) {
        if (field == null || !OWNER.equals(field.owner()) || !DESC.equals(field.descriptor())) return Optional.empty();
        return Optional.ofNullable(IDS.get(field.name()));
    }

    private static Map.Entry<String,String> entry(String key,String value) {
        return Map.entry(key,value);
    }
}
