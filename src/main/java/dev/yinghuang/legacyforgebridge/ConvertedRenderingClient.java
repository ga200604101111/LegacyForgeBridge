package dev.yinghuang.legacyforgebridge;

import dev.yinghuang.legacyforgebridge.render.ConvertedEquipmentRenderRuntime;
import dev.yinghuang.legacyforgebridge.render.GeneratedEquipmentSupport;
import dev.yinghuang.legacyforgebridge.render.LegacyObjModel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Client-only resource lifecycle, separate from network/Via bootstrap. */
public final class ConvertedRenderingClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ConvertedEquipmentRenderRuntime.initialize();
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(
                Identifier.fromNamespaceAndPath(LegacyForgeBridge.MOD_ID, "obj_cache"),
                (ResourceManagerReloadListener) resources -> {
                    LegacyObjModel.clearCache();
                    GeneratedEquipmentSupport.clearResourceCache();
                });
    }
}
