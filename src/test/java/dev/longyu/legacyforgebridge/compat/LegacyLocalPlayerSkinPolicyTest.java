package dev.longyu.legacyforgebridge.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyLocalPlayerSkinPolicyTest {
    @Test
    void keepsVanillaWhenMatchingSessionProfileAlreadyHasTextures() {
        assertFalse(LegacyLocalPlayerSkinPolicy.shouldUseAuthenticatedProfile(true, 1, 1));
    }

    @Test
    void fallsBackWhenMatchingSessionProfileLostItsTextureProperty() {
        assertTrue(LegacyLocalPlayerSkinPolicy.shouldUseAuthenticatedProfile(true, 0, 1));
    }

    @Test
    void fallsBackWhenLegacySessionUsesDifferentLocalProfileIdentity() {
        assertTrue(LegacyLocalPlayerSkinPolicy.shouldUseAuthenticatedProfile(false, 1, 1));
    }

    @Test
    void doesNotOverrideWhenAuthenticatedProfileHasNoTextureEither() {
        assertFalse(LegacyLocalPlayerSkinPolicy.shouldUseAuthenticatedProfile(false, 0, 0));
    }
}
