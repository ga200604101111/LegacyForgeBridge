package dev.yinghuang.legacyforgebridge.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyOscillatingModelSpecialRendererCodecTest {
    @Test
    void partCodecKeepsAllSeventeenFieldsFlatAndRoundTrips() {
        JsonObject json=JsonParser.parseString("""
                {
                  "field":"arm",
                  "u":1,"v":2,
                  "x":-2.0,"y":-3.0,"z":-4.0,
                  "width":4,"height":5,"depth":6,
                  "pivot_x":7.0,"pivot_y":8.0,"pivot_z":9.0,
                  "mirror":true,
                  "base_x_rot":0.1,"base_y_rot":0.2,"base_z_rot":0.3,
                  "animated":true
                }
                """).getAsJsonObject();

        var part=ConvertedLegacyOscillatingModelSpecialRenderer.Part.CODEC.parse(JsonOps.INSTANCE,json)
                .getOrThrow(error->new AssertionError(error));
        assertEquals("arm",part.field());
        assertEquals(4,part.width());
        assertEquals(0.3F,part.baseZRot());
        assertTrue(part.mirror());
        assertTrue(part.animated());

        var encoded=ConvertedLegacyOscillatingModelSpecialRenderer.Part.CODEC.encodeStart(JsonOps.INSTANCE,part)
                .getOrThrow(error->new AssertionError(error)).getAsJsonObject();
        assertTrue(encoded.has("field"));
        assertTrue(encoded.has("pivot_z"));
        assertTrue(encoded.has("animated"));
        assertFalse(encoded.has("geometry"));
        assertFalse(encoded.has("pose"));
    }
}
