package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacySeatBedRegistryTest {
    @AfterEach void clear(){LegacySeatBedRegistry.clearForTests();}

    @Test void schemaThreeCoreRuleRequiresEverySourceAndRuntimeGate() {
        JsonObject value=validRule();LegacySeatBedRegistry.Rule rule=LegacySeatBedRegistry.parseForTests(value);
        assertNotNull(rule);assertEquals("sample:bed",rule.id().toString());assertEquals("sample:bed_item",rule.placementItemId().toString());
        assertEquals("ExampleMod",rule.legacyModId());assertEquals(23,rule.legacyModEntityTypeId());assertTrue(rule.coreRuntimeComplete());assertTrue(rule.remoteEntitySpawnRuntimeComplete());
        assertTrue(rule.timeAccelerationSourceProven());assertFalse(rule.timeAccelerationRuntimeComplete());assertFalse(rule.presentationRuntimeComplete());
        value.addProperty("seatEntityRuntimeComplete",false);assertNull(LegacySeatBedRegistry.parseForTests(value));
    }

    @Test void remoteSpawnLookupUsesLegacyModIdAndModLocalTypeIdOnly() {
        LegacySeatBedRegistry.Rule rule=LegacySeatBedRegistry.parseForTests(validRule());assertNotNull(rule);LegacySeatBedRegistry.installForTests(rule);
        assertSame(rule,LegacySeatBedRegistry.remoteSpawnRule("ExampleMod",23));
        assertSame(rule,LegacySeatBedRegistry.remoteSpawnRule("examplemod",23));
        assertNull(LegacySeatBedRegistry.remoteSpawnRule("ExampleMod",24));
        assertNull(LegacySeatBedRegistry.remoteSpawnRule("OtherMod",23));
    }

    @Test void timeOrPresentationCannotBeClaimedByCoreSchema() {
        JsonObject time=validRule();time.addProperty("timeAccelerationRuntimeComplete",true);assertNull(LegacySeatBedRegistry.parseForTests(time));
        JsonObject presentation=validRule();presentation.addProperty("presentationRuntimeComplete",true);assertNull(LegacySeatBedRegistry.parseForTests(presentation));
    }

    private static JsonObject validRule(){
        JsonObject value=new JsonObject();value.addProperty("id","sample:bed");value.addProperty("placementItemId","sample:bed_item");
        value.addProperty("legacyModId","ExampleMod");value.addProperty("legacyModEntityTypeId",23);value.addProperty("blockHeight",0.25F);
        value.addProperty("twoPartPlacementProven",true);value.addProperty("footOnlyTileProven",true);value.addProperty("sleepFallbackToSeatProven",true);
        value.addProperty("transientOccupancyProven",true);value.addProperty("seatLifecycleProven",true);value.addProperty("timeAccelerationSourceProven",true);
        value.addProperty("specialPresentationRequired",true);value.addProperty("twoPartPlacementRuntimeComplete",true);value.addProperty("sleepSeatRuntimeComplete",true);
        value.addProperty("seatEntityRuntimeComplete",true);value.addProperty("remoteEntitySpawnRuntimeComplete",true);
        value.addProperty("timeAccelerationRuntimeComplete",false);value.addProperty("presentationRuntimeComplete",false);return value;
    }
}
