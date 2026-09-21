package dev.yinghuang.legacyforgebridge.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConvertedGridPotPresentationRuntimeTest {
    @AfterEach void clear(){ ConvertedGridPotPresentationRuntime.clearForTests(); }

    @Test
    void parsesBoundedAdaptedPresentationRule() {
        JsonObject value = JsonParser.parseString("""
                {
                  "id":"foreign:grid","cellCarrierItemId":"minecraft:flower_pot","cellBodyWidth":0.33333334,"cellBodyHeight":0.375,
                  "gridOffsets":[-0.333,0.0,0.333],
                  "contentTranslateY":0.25,
                  "sourceContentScale":0.75,
                  "itemDisplayContext":"NONE",
                  "boundingBoxCentered":true,
                  "boundingBoxBottomAligned":true,
                  "exactLegacyGeometry":false
                }
                """).getAsJsonObject();
        var presentation = ConvertedGridPotPresentationRuntime.parseForTests(value);
        assertNotNull(presentation);
        assertEquals("foreign:grid", presentation.id().toString());
        assertEquals("minecraft:flower_pot",presentation.cellCarrierItemId().toString());
        assertEquals(1.0F/3.0F,presentation.cellBodyWidth(),0.0001F);
        assertEquals(0.375F,presentation.cellBodyHeight(),0.0001F);
        assertEquals(3, presentation.gridOffsets().size());
        assertEquals(-0.333F, presentation.gridOffsets().get(0), 0.0001F);
        assertEquals(0.25F, presentation.contentTranslateY(), 0.0001F);
        assertEquals(0.75F, presentation.sourceContentScale(), 0.0001F);
        assertTrue(presentation.boundingBoxCentered());
        assertTrue(presentation.boundingBoxBottomAligned());
        assertFalse(presentation.exactLegacyGeometry());
    }

    @Test
    void rejectsRulesThatPretendLegacyGeometryIsExactOrUseWrongDisplayContext() {
        JsonObject exact = JsonParser.parseString("""
                {"id":"foreign:grid","cellCarrierItemId":"minecraft:flower_pot","cellBodyWidth":0.33333334,"cellBodyHeight":0.375,"gridOffsets":[-0.333,0.0,0.333],"contentTranslateY":0.25,
                 "sourceContentScale":0.75,"itemDisplayContext":"NONE","boundingBoxCentered":true,
                 "boundingBoxBottomAligned":true,"exactLegacyGeometry":true}
                """).getAsJsonObject();
        assertNull(ConvertedGridPotPresentationRuntime.parseForTests(exact));

        JsonObject wrongContext = JsonParser.parseString("""
                {"id":"foreign:grid","cellCarrierItemId":"minecraft:flower_pot","cellBodyWidth":0.33333334,"cellBodyHeight":0.375,"gridOffsets":[-0.333,0.0,0.333],"contentTranslateY":0.25,
                 "sourceContentScale":0.75,"itemDisplayContext":"GUI","boundingBoxCentered":true,
                 "boundingBoxBottomAligned":true,"exactLegacyGeometry":false}
                """).getAsJsonObject();
        assertNull(ConvertedGridPotPresentationRuntime.parseForTests(wrongContext));
    }

    @Test
    void rejectsMalformedOffsetsAndTransforms() {
        JsonObject malformed = JsonParser.parseString("""
                {"id":"foreign:grid","cellCarrierItemId":"minecraft:flower_pot","cellBodyWidth":0.33333334,"cellBodyHeight":0.375,"gridOffsets":[0.0,0.333],"contentTranslateY":0.25,
                 "sourceContentScale":0.75,"itemDisplayContext":"NONE","boundingBoxCentered":true,
                 "boundingBoxBottomAligned":true,"exactLegacyGeometry":false}
                """).getAsJsonObject();
        assertNull(ConvertedGridPotPresentationRuntime.parseForTests(malformed));
    }
}
