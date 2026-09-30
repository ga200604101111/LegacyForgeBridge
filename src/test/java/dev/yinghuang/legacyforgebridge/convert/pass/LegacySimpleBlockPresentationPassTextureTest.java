package dev.yinghuang.legacyforgebridge.convert.pass;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacySimpleBlockPresentationPassTextureTest {
    @TempDir Path temp;

    @Test
    void flatInventoryPrefersUniqueSourceItemSpriteOverWorldBlockTexture()throws Exception{
        Path staging=temp.resolve("staging");
        Path item=staging.resolve("assets/foreign/textures/items/bambooshoot.png");
        Path block=staging.resolve("assets/foreign/textures/blocks/blockbambooshoot.png");
        Files.createDirectories(item.getParent());Files.createDirectories(block.getParent());
        Files.write(item,new byte[]{1});Files.write(block,new byte[]{2});

        assertEquals("foreign:items/bambooshoot",
                LegacySimpleBlockPresentationPass.sourceFlatInventoryTexture(
                        staging,"blockbambooshoot","foreign/block/BlockBambooShoot"));
    }

    @Test
    void provenFlatSpriteIsMaterializedIntoModernItemTexturePath()throws Exception{
        Path staging=temp.resolve("materialized");
        Path source=staging.resolve("assets/bamboo/textures/items/bambooshoot.png");
        Files.createDirectories(source.getParent());Files.write(source,new byte[]{1,2,3});

        String sprite=LegacySimpleBlockPresentationPass.materializeFlatInventoryTexture(
                staging,"bamboomod","blockbambooshoot","bamboo:items/bambooshoot");

        assertEquals("bamboomod:item/lfb_flat/blockbambooshoot",sprite);
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(
                staging.resolve("assets/bamboomod/textures/item/lfb_flat/blockbambooshoot.png")));
    }

    @Test
    void ambiguousExactItemSpritesStayFailClosed()throws Exception{
        Path staging=temp.resolve("staging");
        Path first=staging.resolve("assets/foreign/textures/items/bambooshoot.png");
        Path second=staging.resolve("assets/other/textures/item/bambooshoot.png");
        Files.createDirectories(first.getParent());Files.createDirectories(second.getParent());
        Files.write(first,new byte[]{1});Files.write(second,new byte[]{2});

        assertNull(LegacySimpleBlockPresentationPass.sourceFlatInventoryTexture(
                staging,"blockbambooshoot","foreign/block/BlockBambooShoot"));
    }
}
