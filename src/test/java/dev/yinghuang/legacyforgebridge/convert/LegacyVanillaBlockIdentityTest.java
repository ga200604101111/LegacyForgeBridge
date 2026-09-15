package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyVanillaBlockIdentityTest {
    @Test void exactForge1710BlockFieldsMapWithoutGuessing() {
        assertEquals("minecraft:farmland", LegacyVanillaBlockIdentity.modernBlockId(
                "net/minecraft/init/Blocks", "field_150458_ak", "Lnet/minecraft/block/Block;"));
        assertEquals("minecraft:grass_block", LegacyVanillaBlockIdentity.modernBlockId(
                "net/minecraft/init/Blocks", "field_150349_c", "Lnet/minecraft/block/BlockGrass;"));
        assertEquals("minecraft:dirt", LegacyVanillaBlockIdentity.modernBlockId(
                "net/minecraft/init/Blocks", "field_150346_d", "Lnet/minecraft/block/Block;"));
        assertEquals("minecraft:sand", LegacyVanillaBlockIdentity.modernBlockId(
                "net/minecraft/init/Blocks", "field_150354_m", "Lnet/minecraft/block/BlockSand;"));
        assertEquals("minecraft:water", LegacyVanillaBlockIdentity.modernBlockId(
                "net/minecraft/init/Blocks", "field_150355_j", "Lnet/minecraft/block/BlockStaticLiquid;"));
    }

    @Test void deobfuscatedAliasesAreAcceptedButForeignOrUnknownFieldsStayUnmapped() {
        assertEquals("minecraft:farmland", LegacyVanillaBlockIdentity.modernBlockId(
                "net/minecraft/init/Blocks", "farmland", "Lnet/minecraft/block/Block;"));
        assertNull(LegacyVanillaBlockIdentity.modernBlockId(
                "some/mod/Blocks", "field_150458_ak", "Lnet/minecraft/block/Block;"));
        assertNull(LegacyVanillaBlockIdentity.modernBlockId(
                "net/minecraft/init/Blocks", "field_999999_x", "Lnet/minecraft/block/Block;"));
        assertNull(LegacyVanillaBlockIdentity.modernBlockId(
                "net/minecraft/init/Blocks", "field_150458_ak", "Ljava/lang/Object;"));
    }
}
