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
    void normalizedRuleParsesProjectileRegistrationAndMetadataVariants() {
        JsonObject value = validRule();
        LegacyVariantSnowballRuntimeRegistry.Rule rule =
                LegacyVariantSnowballRuntimeRegistry.parseForTests(value);

        assertEquals(Identifier.parse("foreign:variant_ball"), rule.id());
        assertEquals(Identifier.parse("foreign:variant_ball_projectile"), rule.projectileId());
        assertEquals("variant_projectile", rule.legacyProjectileRegistryName());
        assertEquals(41, rule.legacyProjectileNumericId());
        assertEquals(64, rule.legacyTrackingRangeBlocks());
        assertEquals(4, rule.modernClientTrackingRangeChunks());
        assertEquals(10, rule.updateFrequency());
        assertEquals(0.25F, rule.width());
        assertEquals(0.25F, rule.height());
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
    void malformedRegistrationOrPrematureGameplayClaimsFailClosed() {
        JsonObject claimed = validRule();
        claimed.addProperty("projectileRuntimeWired", true);
        assertNull(LegacyVariantSnowballRuntimeRegistry.parseForTests(claimed));

        JsonObject wrongTracking = validRule();
        wrongTracking.addProperty("modernClientTrackingRangeChunks", 3);
        assertNull(LegacyVariantSnowballRuntimeRegistry.parseForTests(wrongTracking));

        JsonObject wrongDimensions = validRule();
        wrongDimensions.addProperty("width", 0.5F);
        assertNull(LegacyVariantSnowballRuntimeRegistry.parseForTests(wrongDimensions));

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
                        id, projectile, "variant_projectile", 41,
                        64, 3, 10, true, 0.25F, 0.25F,
                        "random.bow", 0.5F, 0.4F, 0.4F, 0.8F,
                        true, true, variants, true, true, true));
    }

    @Test
    void trackingRangeConversionMatchesEntityRuntimeConvention() {
        assertEquals(1, LegacyVariantSnowballRuntimeRegistry.trackingChunks(1));
        assertEquals(1, LegacyVariantSnowballRuntimeRegistry.trackingChunks(16));
        assertEquals(2, LegacyVariantSnowballRuntimeRegistry.trackingChunks(17));
        assertEquals(4, LegacyVariantSnowballRuntimeRegistry.trackingChunks(64));
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
                  "legacyProjectileRegistrationProven": true,
                  "projectileEntityTypeRegistrationWired": true,
                  "itemRuntimeWired": false,
                  "projectileRuntimeWired": false,
                  "projectileImpactRuntimeWired": false,
                  "rendererRuntimeWired": false,
                  "runtimeImplementationWired": false,
                  "legacyProjectileRegistryName": "variant_projectile",
                  "legacyProjectileNumericId": 41,
                  "legacyTrackingRangeBlocks": 64,
                  "modernClientTrackingRangeChunks": 4,
                  "updateFrequency": 10,
                  "velocityUpdates": true,
                  "width": 0.25,
                  "height": 0.25,
                  "mobCategory": "MISC",
                  "inheritedVanillaSnowballDimensions": true,
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
