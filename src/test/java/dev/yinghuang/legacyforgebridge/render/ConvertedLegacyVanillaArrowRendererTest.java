package dev.yinghuang.legacyforgebridge.render;

import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConvertedLegacyVanillaArrowRendererTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void stateUsesTheActualVanillaArrowContract() {
        assertInstanceOf(ArrowRenderState.class, new ConvertedLegacyOrientedProjectileRenderer.State());
    }

    @Test
    void cardinalDiagonalAndVerticalShotsKeepTheirRemoteDirection() {
        for (float yaw : new float[]{0F, 45F, 90F, 135F, 180F, -90F, -135F}) {
            for (float pitch : new float[]{-90F, -45F, 0F, 45F, 90F}) {
                var state = new ConvertedLegacyOrientedProjectileRenderer.State();
                ConvertedLegacyOrientedProjectileRenderer.copyPose(state, yaw, yaw, pitch, pitch, .5F);
                assertEquals(yaw, state.yRot, EPSILON);
                assertEquals(pitch, state.xRot, EPSILON);
            }
        }
    }

    @Test
    void trajectoryUsesInterpolatedPacketAnglesInsteadOfTheSpawnVelocity() {
        var state = new ConvertedLegacyOrientedProjectileRenderer.State();
        ConvertedLegacyOrientedProjectileRenderer.copyPose(state, 20F, 60F, 40F, -20F, .25F);
        assertEquals(30F, state.yRot, EPSILON);
        assertEquals(25F, state.xRot, EPSILON);
        ConvertedLegacyOrientedProjectileRenderer.copyPose(state, 20F, 60F, 40F, -20F, 1F);
        assertEquals(60F, state.yRot, EPSILON);
        assertEquals(-20F, state.xRot, EPSILON);
    }

    @Test
    void crossingAngleSeamTakesTheShortArcInBothDirections() {
        var state = new ConvertedLegacyOrientedProjectileRenderer.State();
        ConvertedLegacyOrientedProjectileRenderer.copyPose(state, 179F, -179F, 179F, -179F, .5F);
        assertEquals(180F, state.yRot, EPSILON);
        assertEquals(180F, state.xRot, EPSILON);
        ConvertedLegacyOrientedProjectileRenderer.copyPose(state, -179F, 179F, -179F, 179F, .5F);
        assertEquals(-180F, state.yRot, EPSILON);
        assertEquals(-180F, state.xRot, EPSILON);
    }

    @Test
    void landedArrowPreservesItsLastRemotePoseWithoutInventingShake() {
        var state = new ConvertedLegacyOrientedProjectileRenderer.State();
        state.shake = 12F;
        ConvertedLegacyOrientedProjectileRenderer.copyPose(state, -70F, -70F, -35F, -35F, .8F);
        assertEquals(-70F, state.yRot, EPSILON);
        assertEquals(-35F, state.xRot, EPSILON);
        assertEquals(0F, state.shake, EPSILON);
    }

    @Test
    void sourceEntityTextureIsNotReplacedByAnInventoryIcon() {
        Identifier texture = Identifier.parse("foreign:textures/entity/dart.png");
        assertSame(texture, ConvertedLegacyOrientedProjectileRenderer.textureOrDefault(texture));
        assertEquals(Identifier.withDefaultNamespace("textures/entity/projectiles/arrow.png"),
                ConvertedLegacyOrientedProjectileRenderer.textureOrDefault(null));
    }
}
