package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Intentionally invisible renderer for the source dummy-chair actor. */
public final class ConvertedLegacySeatEntityRenderer
        extends EntityRenderer<ConvertedLegacySeatEntity, EntityRenderState> {
    public ConvertedLegacySeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
