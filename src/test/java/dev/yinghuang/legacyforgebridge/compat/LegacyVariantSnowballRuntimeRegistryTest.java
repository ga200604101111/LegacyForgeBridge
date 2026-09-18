package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyVariantSnowballRuntimeRegistryTest {
    @AfterEach void clear() { LegacyVariantSnowballRuntimeRegistry.clearForTests(); }

    @Test
    void normalizedRuleParsesMetadataVariantsAndRegistersIdempotently() {
        JsonObject value = validRule();
        LegacyVariantSnowballRuntimeRegistry.Rule rule =
                LegacyVariantSnowballRuntimeRegistry.parseForTests(value);

        assertEquals(Identifier.parse("foreign:variant_ball"), rule.id());
        assertEquals(Identifier.parse("foreign:variant_ball_projectile"), rule.projectileId());
        assertEquals(LegacyVariantSnowballRuntimeRegistry.Effect.NONE, rule.variant(0).effect());
        assertEquals(2, rule.variant(0).baseDamage());
        assertEquals(LegacyVariantSnowballRuntimeRegistry.Effect.RANDOM_TELEPORT, rule.variant(7).effect());
        assertEquals(16D, rule.variant(7).horizontalRandomRadius());
        assertEquals(128, rule.variant(7).portalParticleCount());

        LegacyVariantSnowballRuntimeRegistry.registerForTests(rule);
        LegacyVariantSnowballRuntimeRegistry.registerForTests(rule);
        assertEquals(rule, LegacyVariantSnowballRuntimeRegistry.rule(rule.id()));
        assertEquals(1, LegacyVariantSnowballRuntimeRegistry.rules("foreign").size());
    }

    @Test
    void malformedOrPrematureRuntimeClaimsFailClosed() {
        JsonObject claimed = validRule();
        claimed.addProperty("runtimeImplementationWired", true);
        assertNull(LegacyVariantSnowballRuntimeRegistry.parseForTests(claimed));

        JsonObject wrongLaunch = validRule();
        wrongLaunch.addProperty("launchSound", "random.click");
        assertNull(LegacyVariantSnowballRuntimeRegistry.parseForTests(wrongLaunch));

        JsonObject wrongTeleport = validRule();
        wrongTeleport.getAsJsonArray("variants").get(1).getAsJsonObject()
                .addProperty("portalParticleCount", 127);
        assertNull(LegacyVariantSnowballRuntimeRegistry.parseForTests(wrongTeleport));

        var id = Identifier.parse("foreign:variant_ball");
        var projectile = Identifier.parse("foreign:variant_ball_projectile");
        var variants = java.util.Map.of(
                0, new LegacyVariantSnowballRuntimeRegistry.Variant(
                        0, 2, LegacyVariantSnowballRuntimeRegistry.Effect.NONE,
                        null, 0, 0, 0D, 0, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () ->
                new LegacyVariantSnowballRuntimeRegistry.Rule(
                        id, projectile, "random.bow", 0.5F, 0.4F, 0.4F, 0.8F,
                        true, true, variants, true, false));
    }

    private static JsonObject validRule() {
        return JsonParser.parseString("""
                {
                  "id": "foreign:variant_ball",
                  "projectileId": "foreign:variant_ball_projectile",
                  "adapter": "VARIANT_SNOWBALL",
                  "rendererAdapter": "THROWN_ITEM",
                  "runtimeCandidateReady": true,
                  "sourceSemanticsComplete": true,
                  "runtimeRuleReady": true,
                  "preRegistrationRuleLoadWired": true,
                  "runtimeImplementationWired": false,
                  "launchSound": "random.bow",
                  "launchVolume": 0.5,
                  "launchPitchNumerator": 0.4,
                  "launchPitchRandomScale": 0.4,
                  "launchPitchBase": 0.8,
                  "consumeOutsideCreative": true,
                  "serverAuthoritativeLaunch": true,
                  "variantCount": 2,
                  "variants": [
                    {
                      "metadata": 0,
                      "baseDamage": 2,
                      "effect": "NONE"
                    },
                    {
                      "metadata": 7,
                      "baseDamage": 2,
                      "effect": "RANDOM_TELEPORT",
                      "horizontalRandomRadius": 16.0,
                      "verticalRandomBound": 8,
                      "verticalRandomOffset": -4,
                      "portalParticleCount": 128,
                      "portalSound": "mob.endermen.portal"
                    }
                  ]
                }
                """).getAsJsonObject();
    }
}
