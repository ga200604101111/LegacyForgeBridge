package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyProjectilePresentationRegistryTest {
    @Test
    void fixedPresentationMetadataDoesNotRequireAWatcher() {
        var rule=new LegacyProjectilePresentationRegistry.Rule(
                Identifier.parse("foreign:projectile"),
                "foreign",42,64,2,true,.25F,.25F,
                LegacyProjectilePresentationRegistry.BaseFamily.ARROW,
                LegacyProjectilePresentationRegistry.Adapter.THROWN_ITEM,
                Identifier.parse("foreign:carrier"),
                -1,-1,0,3,null
        );
        assertEquals(3,rule.itemMetadata(null));
        assertEquals(3,rule.itemMetadata(99));
    }

    @Test
    void unselectedRuleStillRejectsDynamicWatcherState() {
        assertThrows(IllegalArgumentException.class,()->new LegacyProjectilePresentationRegistry.Rule(
                Identifier.parse("foreign:projectile"),
                "foreign",42,64,2,true,.25F,.25F,
                LegacyProjectilePresentationRegistry.BaseFamily.ARROW,
                LegacyProjectilePresentationRegistry.Adapter.THROWN_ITEM,
                Identifier.parse("foreign:carrier"),
                -1,0,0,3,null
        ));
    }
}
