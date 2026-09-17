package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlainEntityRegistryTest {
    @AfterEach void clear() { LegacyPlainEntityRegistry.clearForTests(); }

    @Test void acceptsOnlyFullyWiredNoOpPlainEntityRulesAndGatesRemoteSpawnFields() {
        JsonObject valid = JsonParser.parseString("""
                {
                  "id":"foreign:orb",
                  "generatedClass":"dev.yinghuang.legacyforgebridge.generated.foreign.entity.PlainEntity_orb_a",
                  "legacyModId":"foreign",
                  "legacyModEntityTypeId":23,
                  "width":0.5,
                  "height":0.75,
                  "legacyTrackingRangeBlocks":80,
                  "modernClientTrackingRangeChunks":5,
                  "updateFrequency":2,
                  "velocityUpdates":true,
                  "presentationAdapter":"NOOP_RENDERER",
                  "entityTypeRegistrationWired":true,
                  "clientRendererRegistrationWired":true,
                  "legacyWatcherBridgeWired":true,
                  "remoteEntitySpawnRuntimeComplete":true,
                  "runtimeImplementationWired":true,
                  "runtimeComplete":true
                }
                """).getAsJsonObject();
        LegacyPlainEntityRegistry.Rule rule = LegacyPlainEntityRegistry.parseForTests(valid);
        assertNotNull(rule);
        assertEquals("foreign:orb", rule.id().toString());
        assertEquals(5, rule.modernClientTrackingRangeChunks());
        assertEquals("foreign", rule.legacyModId());
        assertEquals(23, rule.legacyModEntityTypeId());
        assertTrue(rule.remoteEntitySpawnRuntimeComplete());
        assertTrue(rule.runtimeComplete());

        JsonObject invalidVelocity = valid.deepCopy();
        invalidVelocity.addProperty("velocityUpdates", false);
        assertNull(LegacyPlainEntityRegistry.parseForTests(invalidVelocity));

        JsonObject invalidAdapter = valid.deepCopy();
        invalidAdapter.addProperty("presentationAdapter", "CUSTOM_RENDERER");
        assertNull(LegacyPlainEntityRegistry.parseForTests(invalidAdapter));

        JsonObject invalidRemote = valid.deepCopy();
        invalidRemote.addProperty("legacyWatcherBridgeWired", false);
        assertNull(LegacyPlainEntityRegistry.parseForTests(invalidRemote));
    }

    @Test void oldCoreRuntimeRuleWithoutRemoteFieldsRemainsLoadableButRemoteSpawnStaysDisabled() {
        JsonObject legacy = JsonParser.parseString("""
                {
                  "id":"foreign:orb",
                  "generatedClass":"dev.yinghuang.legacyforgebridge.generated.foreign.entity.PlainEntity_orb_a",
                  "width":0.5,
                  "height":0.75,
                  "legacyTrackingRangeBlocks":80,
                  "modernClientTrackingRangeChunks":5,
                  "updateFrequency":2,
                  "velocityUpdates":true,
                  "presentationAdapter":"NOOP_RENDERER",
                  "entityTypeRegistrationWired":true,
                  "clientRendererRegistrationWired":true,
                  "runtimeImplementationWired":true,
                  "runtimeComplete":true
                }
                """).getAsJsonObject();
        LegacyPlainEntityRegistry.Rule rule = LegacyPlainEntityRegistry.parseForTests(legacy);
        assertNotNull(rule);
        assertFalse(rule.legacyWatcherBridgeWired());
        assertFalse(rule.remoteEntitySpawnRuntimeComplete());
        assertNull(rule.legacyModId());
        assertEquals(-1, rule.legacyModEntityTypeId());
    }
}
