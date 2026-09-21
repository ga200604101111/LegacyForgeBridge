package dev.yinghuang.legacyforgebridge.convert.pass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyConnectedCuboidPresentationPassTest {
    @Test
    void legacyVanillaMaterialArraysMapToTheSameSpeciesTexture() {
        assertEquals("minecraft:block/oak_planks",LegacyConnectedCuboidPresentationPass.vanillaTexture("planks",0));
        assertEquals("minecraft:block/dark_oak_planks",LegacyConnectedCuboidPresentationPass.vanillaTexture("planks",5));

        assertEquals("minecraft:block/oak_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log",0));
        assertEquals("minecraft:block/spruce_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log",1));
        assertEquals("minecraft:block/birch_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log",2));
        assertEquals("minecraft:block/jungle_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log",3));
        assertEquals("minecraft:block/spruce_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log",5));

        assertEquals("minecraft:block/acacia_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log2",0));
        assertEquals("minecraft:block/dark_oak_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log2",1));
        assertEquals("minecraft:block/acacia_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log2",4));
        assertEquals("minecraft:block/dark_oak_log",LegacyConnectedCuboidPresentationPass.vanillaTexture("log2",5));
        assertNull(LegacyConnectedCuboidPresentationPass.vanillaTexture("stone",0));
    }
}
