package dev.yinghuang.legacyforgebridge.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ConvertedLegacyNoOpEntityRendererTest {
    @Test void suppressesEveryRendererLevelVisualSideEffect() {
        EntityRenderState state = new EntityRenderState();
        state.displayFireAnimation = true;
        state.nameTag = Component.literal("legacy");
        state.nameTagAttachment = net.minecraft.world.phys.Vec3.ZERO;
        state.leashStates = new ArrayList<>();
        state.shadowRadius = 3.5F;

        ConvertedLegacyNoOpEntityRenderer.suppressVisualEffects(state);

        assertFalse(state.displayFireAnimation);
        assertNull(state.nameTag);
        assertNull(state.nameTagAttachment);
        assertNull(state.leashStates);
        assertEquals(0F, state.shadowRadius);
        assertTrue(state.shadowPieces.isEmpty());
    }
}
