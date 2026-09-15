package dev.longyu.legacyforgebridge.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ViaLegacySoundMappingsTest {
    @Test void tracedSoundPathExposesLegacyNameAndReversesReplacementWithoutHardcodedSoundTable() {
        Map<String,String> newestToMiddle=Map.of(
                "minecraft:entity.player.hurt","minecraft:entity.player.hurt_old",
                "minecraft:entity.villager.ambient","minecraft:entity.villager.ambient_old");
        Map<String,String> middleToLegacy=Map.of(
                "minecraft:entity.player.hurt_old","minecraft:game.player.hurt",
                "minecraft:entity.villager.ambient_old","minecraft:mob.villager.idle");
        var first=stage(newestToMiddle);
        var second=stage(middleToLegacy);
        var path=ViaLegacySoundMappings.trace("minecraft:entity.player.hurt",List.of(first,second)).orElseThrow();
        assertEquals("game.player.hurt",path.legacyName());
        assertEquals("minecraft:entity.villager.ambient",path.toModern("mob.villager.idle").orElseThrow());
    }

    @Test void missingStageMappingFailsClosedInBothDirections() {
        var only=stage(Map.of("minecraft:a","minecraft:b"));
        assertTrue(ViaLegacySoundMappings.trace("minecraft:missing",List.of(only)).isEmpty());
        var path=ViaLegacySoundMappings.trace("minecraft:a",List.of(only)).orElseThrow();
        assertTrue(path.toModern("missing").isEmpty());
    }

    private static ViaLegacySoundMappings.Stage stage(Map<String,String> down){
        return ViaLegacySoundMappings.stageForTest(down::get,value->down.entrySet().stream()
                .filter(entry->entry.getValue().equals(value)).map(Map.Entry::getKey).findFirst().orElse(null));
    }
}
