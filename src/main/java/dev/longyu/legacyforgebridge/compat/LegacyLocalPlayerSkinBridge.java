package dev.longyu.legacyforgebridge.compat;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.longyu.legacyforgebridge.mixin.client.MinecraftAccessor;
import dev.longyu.legacyforgebridge.network.FmlConnectionTrace;
import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Repairs the local player's skin source when a translated Forge 1.7.10 session provides an
 * incomplete synthetic PlayerInfo profile.
 *
 * <p>Old protocol translation can produce a valid local player entity while its modern
 * PlayerInfo/GameProfile is missing the packed {@code textures} property. Modern Minecraft then
 * resolves only a default skin for that one player even though the authenticated client profile
 * was already fetched from Mojang during startup. Remote players are deliberately untouched.</p>
 */
public final class LegacyLocalPlayerSkinBridge {
    public static final LegacyLocalPlayerSkinBridge INSTANCE = new LegacyLocalPlayerSkinBridge();

    private UUID cachedAuthenticatedProfileId;
    private Supplier<PlayerSkin> authenticatedSkinLookup;
    private boolean fallbackLogged;
    private boolean healthyProfileLogged;

    private LegacyLocalPlayerSkinBridge() {
    }

    /**
     * Returns a replacement skin only when the legacy local PlayerInfo is demonstrably incomplete.
     * Returning {@code null} means vanilla rendering must continue unchanged.
     */
    public synchronized PlayerSkin replacementFor(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target() || minecraft.player != player) {
            return null;
        }
        if (minecraft.getConnection() == null) {
            return null;
        }

        PlayerInfo sessionInfo = minecraft.getConnection().getPlayerInfo(player.getUUID());
        GameProfile sessionProfile = sessionInfo != null ? sessionInfo.getProfile() : player.getGameProfile();

        ProfileResult authenticatedResult = ((MinecraftAccessor) (Object) minecraft)
                .legacyforgebridge$getProfileFuture()
                .getNow(null);
        if (authenticatedResult == null || authenticatedResult.profile() == null) {
            return null;
        }

        GameProfile authenticatedProfile = authenticatedResult.profile();
        int sessionTextureCount = texturePropertyCount(sessionProfile);
        int authenticatedTextureCount = texturePropertyCount(authenticatedProfile);
        boolean profileIdMatches = sessionProfile != null
                && Objects.equals(sessionProfile.id(), authenticatedProfile.id());

        // A fully populated matching modern PlayerInfo is already correct. Do not replace it.
        if (profileIdMatches && sessionTextureCount > 0) {
            if (!healthyProfileLogged) {
                healthyProfileLogged = true;
                FmlConnectionTrace.INSTANCE.event(
                        "Legacy local-player skin profile is complete; vanilla skin lookup retained"
                                + " sessionTextures=" + sessionTextureCount
                                + " authenticatedTextures=" + authenticatedTextureCount
                                + " profileIdMatch=true"
                );
            }
            return null;
        }

        // If Mojang did not return a packed texture either, replacing the vanilla lookup would
        // merely trade one default skin for another. Keep vanilla behavior in that case.
        if (authenticatedTextureCount == 0) {
            if (!fallbackLogged) {
                fallbackLogged = true;
                FmlConnectionTrace.INSTANCE.event(
                        "Legacy local-player skin fallback unavailable: authenticated profile has no textures"
                                + " sessionTextures=" + sessionTextureCount
                                + " profileIdMatch=" + profileIdMatches
                );
            }
            return null;
        }

        if (authenticatedSkinLookup == null
                || !Objects.equals(cachedAuthenticatedProfileId, authenticatedProfile.id())) {
            cachedAuthenticatedProfileId = authenticatedProfile.id();
            authenticatedSkinLookup = minecraft.getSkinManager().createLookup(authenticatedProfile, false);
        }

        PlayerSkin replacement = authenticatedSkinLookup.get();
        if (!fallbackLogged) {
            fallbackLogged = true;
            PlayerSkin sessionSkin = sessionInfo == null ? null : sessionInfo.getSkin();
            FmlConnectionTrace.INSTANCE.event(
                    "Legacy local-player authenticated skin fallback enabled"
                            + " sessionTextures=" + sessionTextureCount
                            + " authenticatedTextures=" + authenticatedTextureCount
                            + " profileIdMatch=" + profileIdMatches
                            + " sessionModel=" + (sessionSkin == null ? "<none>" : sessionSkin.model())
                            + " authenticatedModel=" + replacement.model()
                            + " authenticatedSecure=" + replacement.secure()
                            + " authenticatedBody=" + replacement.body().texturePath()
            );
        }
        return replacement;
    }

    public synchronized void reset() {
        cachedAuthenticatedProfileId = null;
        authenticatedSkinLookup = null;
        fallbackLogged = false;
        healthyProfileLogged = false;
    }

    private int texturePropertyCount(GameProfile profile) {
        if (profile == null || profile.properties() == null) {
            return 0;
        }
        return profile.properties().get("textures").size();
    }
}
