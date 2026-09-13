package dev.longyu.legacyforgebridge.mixin.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.rewriter.text.ComponentRewriterBase;
import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;

/**
 * Keeps the original 1.7.10 translatable-component key while allowing ViaVersion to perform all
 * other component/protocol conversion work.
 *
 * <p>ViaVersion normally rewrites/removes translation keys as packets cross version boundaries;
 * some removed keys are replaced with an English fallback string. For a real 1.7.10 session LFB
 * owns the legacy language catalogue, so the key itself is the compatibility identity we need to
 * preserve. We therefore snapshot the key before each ViaVersion component rewriter and restore it
 * afterwards. Hover/click/NBT/component-format conversion is not bypassed.</p>
 */
@Mixin(value = ComponentRewriterBase.class, remap = false)
public abstract class ViaComponentTranslationMixin {
    @Unique
    private static final ThreadLocal<IdentityHashMap<JsonObject, String>> LFB_JSON_KEYS =
            ThreadLocal.withInitial(IdentityHashMap::new);
    @Unique
    private static final ThreadLocal<IdentityHashMap<CompoundTag, String>> LFB_NBT_KEYS =
            ThreadLocal.withInitial(IdentityHashMap::new);

    @Inject(method = "processJsonObject", at = @At("HEAD"), remap = false)
    private void legacyforgebridge$captureJsonKey(
            UserConnection connection,
            JsonObject object,
            CallbackInfo ci
    ) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            return;
        }

        JsonElement translate = object.get("translate");
        if (translate != null && translate.isJsonPrimitive() && translate.getAsJsonPrimitive().isString()) {
            LFB_JSON_KEYS.get().put(object, translate.getAsString());
        }
    }

    @Inject(method = "processJsonObject", at = @At("RETURN"), remap = false)
    private void legacyforgebridge$restoreJsonKey(
            UserConnection connection,
            JsonObject object,
            CallbackInfo ci
    ) {
        IdentityHashMap<JsonObject, String> keys = LFB_JSON_KEYS.get();
        String original = keys.remove(object);
        if (original != null) {
            object.addProperty("translate", original);
        }
        if (keys.isEmpty()) {
            LFB_JSON_KEYS.remove();
        }
    }

    @Inject(method = "processCompoundTag", at = @At("HEAD"), remap = false)
    private void legacyforgebridge$captureNbtKey(
            UserConnection connection,
            CompoundTag tag,
            CallbackInfo ci
    ) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            return;
        }

        StringTag translate = tag.getStringTag("translate");
        if (translate != null) {
            LFB_NBT_KEYS.get().put(tag, translate.getValue());
        }
    }

    @Inject(method = "processCompoundTag", at = @At("RETURN"), remap = false)
    private void legacyforgebridge$restoreNbtKey(
            UserConnection connection,
            CompoundTag tag,
            CallbackInfo ci
    ) {
        IdentityHashMap<CompoundTag, String> keys = LFB_NBT_KEYS.get();
        String original = keys.remove(tag);
        if (original != null) {
            StringTag translate = tag.getStringTag("translate");
            if (translate != null) {
                translate.setValue(original);
            }
        }
        if (keys.isEmpty()) {
            LFB_NBT_KEYS.remove();
        }
    }
}
