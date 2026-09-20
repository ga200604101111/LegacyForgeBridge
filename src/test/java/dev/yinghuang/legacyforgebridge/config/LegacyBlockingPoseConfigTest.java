package dev.yinghuang.legacyforgebridge.config;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBlockingPoseConfigTest {
    @Test void requestedDefaultsRoundTripWithoutScale() {
        var defaults = LegacyBlockingPoseConfig.Settings.defaults();
        assertTrue(defaults.enabled());
        assertTrue(defaults.mirrorLeftHand());
        assertEquals(new LegacyBlockingPoseConfig.Vec3(0, 0, 0), defaults.firstPerson().translation());
        assertEquals(new LegacyBlockingPoseConfig.Vec3(0, 20, 0), defaults.firstPerson().rotationDegrees());
        assertEquals(new LegacyBlockingPoseConfig.Vec3(-30, 40, 40), defaults.thirdPerson().rotationDegrees());
        var document = LegacyBlockingPoseConfig.document(defaults);
        assertEquals(2, document.get("schemaVersion").getAsInt());
        assertFalse(document.toString().contains("scale"));
        assertEquals(defaults, LegacyBlockingPoseConfig.parse(document));
    }

    @Test void oldScaleSchemaMigratesButScaleNoLongerAffectsRuntime() {
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
        assertEquals(new LegacyBlockingPoseConfig.Vec3(0.25F, -0.5F, 1.0F), value.firstPerson().translation());
        assertFalse(LegacyBlockingPoseConfig.document(value).toString().contains("scale"));
        var left = value.firstPerson().mirrored();
        assertEquals(new LegacyBlockingPoseConfig.Vec3(-0.25F, -0.5F, 1.0F), left.translation());
        assertEquals(new LegacyBlockingPoseConfig.Vec3(10, -20, 30), left.rotationDegrees());
    }

    @Test void firstPersonTranslationStillUsesScreenAxesAfterVanillaRotation() {
        PoseStack matrices = new PoseStack();
        matrices.mulPose(Axis.YP.rotationDegrees(90));
        new LegacyBlockingPoseConfig.Transform(
                new LegacyBlockingPoseConfig.Vec3(0.25F, -0.5F, 0.75F),
                new LegacyBlockingPoseConfig.Vec3(0, 0, 0)
        ).applyScreenSpace(matrices, false);
        Vector4f origin = matrices.last().pose().transform(new Vector4f(0, 0, 0, 1));
        assertEquals(0.25F, origin.x, 0.0001F);
        assertEquals(-0.5F, origin.y, 0.0001F);
        assertEquals(0.75F, origin.z, 0.0001F);
    }

    @Test void thirdPersonTranslationFollowsRotatedPlayerArmAxes() {
        var translation = new LegacyBlockingPoseConfig.Vec3(0.25F, -0.5F, 0.75F);
        var transform = new LegacyBlockingPoseConfig.Transform(
                translation,
                new LegacyBlockingPoseConfig.Vec3(0, 0, 0)
        );

        PoseStack expectedMatrices = new PoseStack();
        expectedMatrices.mulPose(Axis.YP.rotationDegrees(90));
        Vector4f expected = expectedMatrices.last().pose().transform(
                new Vector4f(translation.x(), translation.y(), translation.z(), 1));

        PoseStack actualMatrices = new PoseStack();
        actualMatrices.mulPose(Axis.YP.rotationDegrees(90));
        transform.applyArmLocalSpace(actualMatrices, false);
        Vector4f actual = actualMatrices.last().pose().transform(new Vector4f(0, 0, 0, 1));

        assertEquals(expected.x, actual.x, 0.0001F);
        assertEquals(expected.y, actual.y, 0.0001F);
        assertEquals(expected.z, actual.z, 0.0001F);
        assertNotEquals(translation.x(), actual.x, 0.0001F,
                "third-person translation must rotate with the player/arm transform");
    }

    @Test void malformedOrDangerousValuesAreRejected() {
        rejects("{\"schemaVersion\":3,\"swordBlockingPose\":{}}");
        rejects(document("[0,0]", "[0,0,0]"));
        rejects(document("[0,0,0]", "[0,181,0]"));
        rejects(document("[2.01,0,0]", "[0,0,0]"));
        rejects(document("[0,0,0]", "[0,0,0]").replace("\"thirdPerson\"", "\"unknown\":0,\"thirdPerson\""));
        rejects(document("[0,0,0]", "[0,0,0]").replace("\"rotationDegrees\":[0,0,0]", "\"rotationDegrees\":[0,0,0],\"scale\":[1,1,1]"));
    }

    private static String document(String translation, String rotation) {
        String transform = "{\"translation\":" + translation + ",\"rotationDegrees\":" + rotation + "}";
        return "{\"schemaVersion\":2,\"swordBlockingPose\":{\"enabled\":true,\"mirrorLeftHand\":true,"
                + "\"firstPerson\":" + transform + ",\"thirdPerson\":" + transform + "}}";
    }

    private static void rejects(String json) {
        assertThrows(RuntimeException.class,
                () -> LegacyBlockingPoseConfig.parse(JsonParser.parseString(json).getAsJsonObject()));
    }
}
