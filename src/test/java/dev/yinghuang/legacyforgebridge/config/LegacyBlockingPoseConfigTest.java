package dev.yinghuang.legacyforgebridge.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBlockingPoseConfigTest {
    @Test void defaultsAreAnExactNoOpAndRoundTrip() {
        var defaults = LegacyBlockingPoseConfig.Settings.defaults();
        assertTrue(defaults.enabled());
        assertTrue(defaults.mirrorLeftHand());
        assertEquals(defaults, LegacyBlockingPoseConfig.parse(LegacyBlockingPoseConfig.document(defaults)));
        assertEquals(new LegacyBlockingPoseConfig.Vec3(0,0,0), defaults.firstPerson().translation());
        assertEquals(new LegacyBlockingPoseConfig.Vec3(0,0,0), defaults.thirdPerson().rotationDegrees());
        assertEquals(new LegacyBlockingPoseConfig.Vec3(1,1,1), defaults.firstPerson().scale());
    }

    @Test void oneGenericConfigControlsBothViewsAndMirrorsLeftHand() {
        var value = LegacyBlockingPoseConfig.parse(JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "swordBlockingPose": {
                    "enabled": true,
                    "mirrorLeftHand": true,
                    "firstPerson": {
                      "translation": [0.25, -0.5, 1.0],
                      "rotationDegrees": [10, 20, -30],
                      "scale": [1.1, 0.9, 1.0]
                    },
                    "thirdPerson": {
                      "translation": [-0.125, 0.0, 0.5],
                      "rotationDegrees": [-15, 45, 90],
                      "scale": [1.0, 1.0, 1.0]
                    }
                  }
                }
                """).getAsJsonObject());
        var left = value.firstPerson().mirrored();
        assertEquals(new LegacyBlockingPoseConfig.Vec3(-0.25F,-0.5F,1.0F), left.translation());
        assertEquals(new LegacyBlockingPoseConfig.Vec3(10,-20,30), left.rotationDegrees());
        assertEquals(value.firstPerson().scale(), left.scale());
        assertEquals(45, value.thirdPerson().rotationDegrees().y());
    }

    @Test void malformedOrDangerousValuesAreRejected() {
        rejects("{\"schemaVersion\":2,\"swordBlockingPose\":{}}");
        rejects(document("[0,0]", "[0,0,0]", "[1,1,1]"));
        rejects(document("[0,0,0]", "[0,361,0]", "[1,1,1]"));
        rejects(document("[0,0,0]", "[0,0,0]", "[0,1,1]"));
        rejects(document("[0,0,0]", "[0,0,0]", "[5,1,1]"));
        rejects(document("[0,0,0]", "[0,0,0]", "[1,1,1]").replace("\"thirdPerson\"", "\"unknown\":0,\"thirdPerson\""));
    }

    private static String document(String translation,String rotation,String scale) {
        String transform="{\"translation\":"+translation+",\"rotationDegrees\":"+rotation+",\"scale\":"+scale+"}";
        return "{\"schemaVersion\":1,\"swordBlockingPose\":{\"enabled\":true,\"mirrorLeftHand\":true,"
                +"\"firstPerson\":"+transform+",\"thirdPerson\":"+transform+"}}";
    }
    private static void rejects(String json) {
        assertThrows(RuntimeException.class,()->LegacyBlockingPoseConfig.parse(JsonParser.parseString(json).getAsJsonObject()));
    }
}
