package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVanillaEntityDataWatcher1710Test {

    @Test void wolfSchemaIsTransitiveAndKeepsPinned1710TypesAndDefaults() {
        var entries = LegacyVanillaEntityDataWatcher1710.inheritedForExternalBase(
                "net/minecraft/entity/passive/EntityWolf");
        assertEquals(List.of(6,7,8,9,10,11,12,16,17,18,19,20),
                entries.stream().map(LegacyVanillaEntityDataWatcher1710.Entry::index).toList());

        Map<Integer,LegacyVanillaEntityDataWatcher1710.Entry> byIndex =
                entries.stream().collect(Collectors.toMap(
                        LegacyVanillaEntityDataWatcher1710.Entry::index,
                        value -> value));

        assertEquals("float", byIndex.get(6).valueKind());
        assertEquals(1.0F, ((Number)byIndex.get(6).defaultValue()).floatValue());
        assertEquals("int", byIndex.get(12).valueKind());
        assertEquals(0, ((Number)byIndex.get(12).defaultValue()).intValue());
        assertEquals("string", byIndex.get(17).valueKind());
        assertEquals("", byIndex.get(17).defaultValue());
        assertEquals("float", byIndex.get(18).valueKind());
        assertEquals(1.0F, ((Number)byIndex.get(18).defaultValue()).floatValue());
        assertEquals(14, ((Number)byIndex.get(20).defaultValue()).byteValue());

        assertFalse(byIndex.containsKey(0));
        assertFalse(byIndex.containsKey(1));
    }

    @Test void zombieAndArrowKeepTheirDistinctVanillaLayouts() {
        var zombie = LegacyVanillaEntityDataWatcher1710.inheritedForExternalBase(
                "net/minecraft/entity/monster/EntityZombie");
        assertEquals(List.of(6,7,8,9,10,11,12,13,14),
                zombie.stream().map(LegacyVanillaEntityDataWatcher1710.Entry::index).toList());
        for (int index : List.of(12,13,14)) {
            var entry = zombie.stream().filter(value -> value.index() == index).findFirst().orElseThrow();
            assertEquals("byte", entry.valueKind());
            assertEquals((byte)0, ((Number)entry.defaultValue()).byteValue());
        }

        var arrow = LegacyVanillaEntityDataWatcher1710.inheritedForExternalBase(
                "net/minecraft/entity/projectile/EntityArrow");
        assertEquals(1, arrow.size());
        assertEquals(16, arrow.getFirst().index());
        assertEquals("byte", arrow.getFirst().valueKind());

        assertTrue(LegacyVanillaEntityDataWatcher1710.inheritedForExternalBase(
                "net/minecraft/entity/projectile/EntityThrowable").isEmpty());
    }

    @Test void onlyPinnedExternalBasesAreAdmitted() {
        assertTrue(LegacyVanillaEntityDataWatcher1710.supportsExternalBase(
                "net/minecraft/entity/monster/EntityMob"));
        assertTrue(LegacyVanillaEntityDataWatcher1710.supportsExternalBase(
                "net/minecraft/entity/Entity"));
        assertFalse(LegacyVanillaEntityDataWatcher1710.supportsExternalBase(
                "net/minecraft/entity/projectile/EntityFireball"));
        assertFalse(LegacyVanillaEntityDataWatcher1710.supportsExternalBase(
                "foreign/dependency/BaseEntity"));
    }

    @Test void platformGetterLookupUsesTheSameTransitiveSchema() {
        assertTrue(LegacyVanillaEntityDataWatcher1710.matchesPlatformAccess(
                "net/minecraft/entity/passive/EntityWolf", 12, "int"));
        assertTrue(LegacyVanillaEntityDataWatcher1710.matchesPlatformAccess(
                "net/minecraft/entity/EntityLivingBase", 8, "byte"));
        assertFalse(LegacyVanillaEntityDataWatcher1710.matchesPlatformAccess(
                "net/minecraft/entity/EntityLivingBase", 8, "int"));
        assertFalse(LegacyVanillaEntityDataWatcher1710.matchesPlatformAccess(
                "net/minecraft/entity/projectile/EntityThrowable", 16, "byte"));
    }
}
