package dev.yinghuang.legacyforgebridge.convert;

import java.util.List;
import java.util.Optional;

/** Minecraft 1.7.10 vanilla Block identities that are safe for the first GridPot insertion runtime without metadata flattening. */
public final class LegacyGridPotVanillaInsertion1710 {
    public record Rule(String legacyRegistryName, int legacyRenderType, String modernId) {
        public Rule {
            if (legacyRegistryName == null || legacyRegistryName.isBlank()
                    || (legacyRenderType != 1 && legacyRenderType != 13 && legacyRenderType != 40)
                    || modernId == null || !modernId.startsWith("minecraft:")) {
                throw new IllegalArgumentException("Invalid 1.7.10 GridPot vanilla insertion rule");
            }
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("brown_mushroom", 1, "minecraft:brown_mushroom"),
            new Rule("red_mushroom", 1, "minecraft:red_mushroom"),
            new Rule("cactus", 13, "minecraft:cactus")
    );

    private LegacyGridPotVanillaInsertion1710() { }
    public static List<Rule> rules() { return RULES; }
    public static Optional<Rule> byLegacyRegistryName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return RULES.stream().filter(rule -> rule.legacyRegistryName().equals(name)).findFirst();
    }
}
