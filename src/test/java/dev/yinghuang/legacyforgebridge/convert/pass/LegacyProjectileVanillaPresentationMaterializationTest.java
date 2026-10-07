package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyProjectilePresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVanillaRegistry1710;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyProjectileVanillaPresentationMaterializationTest {
    @Test void fixedVanilla1710PresentationItemUsesDfuAndClearsLegacyRuntimeMetadata(){
        var rule=new LegacyProjectilePresentationAnalyzer.Rule(
                "foreign_orb","foreign/Orb","net/minecraft/client/renderer/entity/RenderSnowball",
                47,64,2,true,.25F,.25F,
                LegacyProjectilePresentationAnalyzer.BaseFamily.THROWABLE,
                LegacyProjectilePresentationAnalyzer.Adapter.THROWN_ITEM,
                LegacyVanillaRegistry1710.ITEMS_OWNER,"snowball",
                -1,-1,0,0,null,"test");

        var resolved=LegacyProjectilePresentationPass.resolvePresentationItem(
                rule,Map.of(),Map.of(),Set.of(),Set.of());
        assertNotNull(resolved);
        assertEquals("minecraft:snowball",resolved.modernId());
        assertEquals(0,resolved.runtimeMetadata());
        assertEquals("VANILLA_1710_DFU",resolved.sourceKind());
    }

    @Test void candidateOwnedItemResolutionRemainsUnchanged(){
        var rule=new LegacyProjectilePresentationAnalyzer.Rule(
                "foreign_orb","foreign/Orb","foreign/Renderer",
                47,64,2,true,.25F,.25F,
                LegacyProjectilePresentationAnalyzer.BaseFamily.THROWABLE,
                LegacyProjectilePresentationAnalyzer.Adapter.THROWN_ITEM,
                "foreign/Item","orb_item",-1,-1,0,3,null,"test");
        var resolved=LegacyProjectilePresentationPass.resolvePresentationItem(
                rule,Map.of("foreign/Item\u0000orb_item","foreign:orb_item"),Map.of(),Set.of(),Set.of());
        assertNotNull(resolved);
        assertEquals("foreign:orb_item",resolved.modernId());
        assertEquals(3,resolved.runtimeMetadata());
        assertEquals("CONVERTED_MOD_ITEM",resolved.sourceKind());
    }
}
