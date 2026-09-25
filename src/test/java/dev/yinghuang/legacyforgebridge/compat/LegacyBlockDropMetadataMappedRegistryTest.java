package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockDropRuntimeRulePass;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyBlockDropMetadataMappedRegistryTest {
    @AfterEach
    void clearRegistry() {
        LegacyBlockDropRuntimeRegistry.clearForTests();
    }

    @Test
    void metadataRuleMapsEveryLegacyBlockMetaAndRejectsMalformedTables() {
        JsonObject object = commonRule("fixture:mapped");
        object.addProperty("mode", LegacyBlockDropRuntimeRulePass.METADATA_MODE);
        object.addProperty("metadataIndependent", false);
        object.add("legacyDamageByBlockMeta", identityTable());

        var parsed = LegacyBlockDropRuntimeRegistry.parseRule(object);
        assertNotNull(parsed);
        assertFalse(parsed.metadataIndependent());
        for (int meta = 0; meta < 16; meta++) assertEquals(meta, parsed.legacyDamage(meta));

        JsonObject shortTable = object.deepCopy();
        shortTable.getAsJsonArray("legacyDamageByBlockMeta").remove(15);
        assertNull(LegacyBlockDropRuntimeRegistry.parseRule(shortTable));

        JsonObject outOfRange = object.deepCopy();
        outOfRange.getAsJsonArray("legacyDamageByBlockMeta").set(4, new com.google.gson.JsonPrimitive(16));
        assertNull(LegacyBlockDropRuntimeRegistry.parseRule(outOfRange));

        JsonObject fractional = object.deepCopy();
        fractional.getAsJsonArray("legacyDamageByBlockMeta").set(4, new com.google.gson.JsonPrimitive(4.5));
        assertNull(LegacyBlockDropRuntimeRegistry.parseRule(fractional));
    }

    @Test
    void installedMetadataRuleKeepsExactLookupTable() {
        Identifier id = Identifier.fromNamespaceAndPath("fixture", "mapped");
        var table = IntStream.range(0, 16).boxed().toList();
        LegacyBlockDropRuntimeRegistry.installForTests(id, table);
        var rule = LegacyBlockDropRuntimeRegistry.rule(id);
        assertNotNull(rule);
        assertFalse(rule.metadataIndependent());
        for (int meta = 0; meta < 16; meta++) assertEquals(meta, rule.legacyDamage(meta));
    }

    private static JsonObject commonRule(String id) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("dropKind", "SELF_BLOCK_ITEM");
        object.addProperty("quantity", 1);
        object.addProperty("legacyExplosionChanceMode", "inverse_explosion_radius");
        object.addProperty("normalSilkSelfDropProofComplete", true);
        object.addProperty("explosionSourceProofComplete", true);
        object.addProperty("sourceExplosionDestructionOverrideFree", true);
        object.addProperty("explosionDecayFormulaProofComplete", true);
        object.addProperty("explosionAffectedSetSourceProofComplete", true);
        return object;
    }

    private static JsonArray identityTable() {
        JsonArray values = new JsonArray();
        for (int meta = 0; meta < 16; meta++) values.add(meta);
        return values;
    }
}
