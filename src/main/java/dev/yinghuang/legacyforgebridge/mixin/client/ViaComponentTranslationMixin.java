package dev.yinghuang.legacyforgebridge.mixin.client;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.rewriter.text.ComponentRewriterBase;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyTranslationBridge;
import dev.yinghuang.legacyforgebridge.compat.ViaJsonObjectAccess;
import dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;

/**
 * Keeps selected 1.7.10 server-message translation keys intact while allowing ViaVersion to
 * perform every other component/protocol conversion normally.
 *
 * <p>ViaFabricPlus relocates Gson at runtime, so JSON callback parameters deliberately use
 * {@code @Coerce Object}. Static references to {@code com.google.gson.JsonObject} would compile
 * against the API but fail when Mixin applies to the relocated runtime class.</p>
 *
 * <p>Item/block/entity/container translation keys are deliberately not captured, so ViaVersion
 * can still map those to their modern identities and Minecraft 1.21.11 keeps rendering modern
 * content names.</p>
 */
@Mixin(value = ComponentRewriterBase.class, remap = false)
public abstract class ViaComponentTranslationMixin {
    @Unique
    private static final ThreadLocal<IdentityHashMap<Object, String>> LFB_JSON_KEYS =
            ThreadLocal.withInitial(IdentityHashMap::new);
    @Unique
    private static final ThreadLocal<IdentityHashMap<CompoundTag, String>> LFB_NBT_KEYS =
            ThreadLocal.withInitial(IdentityHashMap::new);
    @Unique
    private static volatile boolean legacyforgebridge$jsonAccessFailureLogged;

    @Inject(method = "processJsonObject", at = @At("HEAD"), remap = false)
    private void legacyforgebridge$captureJsonKey(
            UserConnection connection,
            @Coerce Object object,
            CallbackInfo ci
    ) {
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
            return;
        }

        String key;
        try {
            key = ViaJsonObjectAccess.getStringProperty(object, "translate");
        } catch (RuntimeException exception) {
            legacyforgebridge$logJsonAccessFailure(exception);
            return;
        }

        if (LegacyTranslationBridge.shouldPreserveKey(key)) {
            LFB_JSON_KEYS.get().put(object, key);
        }
    }

    @Inject(method = "processJsonObject", at = @At("RETURN"), remap = false)
    private void legacyforgebridge$restoreJsonKey(
            UserConnection connection,
            @Coerce Object object,
            CallbackInfo ci
    ) {
        IdentityHashMap<Object, String> keys = LFB_JSON_KEYS.get();
        String original = keys.remove(object);
        if (original != null) {
            try {
                ViaJsonObjectAccess.setStringProperty(object, "translate", original);
            } catch (RuntimeException exception) {
                legacyforgebridge$logJsonAccessFailure(exception);
            }
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
        if (translate != null && LegacyTranslationBridge.shouldPreserveKey(translate.getValue())) {
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
            tag.putString("translate", original);
        }
        if (keys.isEmpty()) {
            LFB_NBT_KEYS.remove();
        }
    }

    @Unique
    private static void legacyforgebridge$logJsonAccessFailure(RuntimeException exception) {
        if (legacyforgebridge$jsonAccessFailureLogged) {
            return;
        }
        synchronized (ViaComponentTranslationMixin.class) {
            if (legacyforgebridge$jsonAccessFailureLogged) {
                return;
            }
            legacyforgebridge$jsonAccessFailureLogged = true;
            LegacyForgeBridge.LOGGER.error(
                    "Legacy Via translation key preservation could not access ViaVersion's relocated Gson object; leaving Via's translation behavior unchanged for affected components",
                    exception
            );
        }
    }
}
