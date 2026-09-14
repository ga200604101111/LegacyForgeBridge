package dev.longyu.legacyforgebridge.convert.runtime;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConvertedLegacyBlockTest {
    @Test void raw1710MetadataHasSixteenOpaqueStatesAndRejectsOutOfRangeValues() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("fixture", "legacy_state"));
        ConvertedLegacyBlock block = new ConvertedLegacyBlock(BlockBehaviour.Properties.of().setId(key));

        assertEquals(16, block.getStateDefinition().getPossibleStates().size());
        assertEquals(0, ConvertedLegacyBlock.legacyMeta(block.defaultBlockState()));
        for (int meta = 0; meta < 16; meta++) {
            assertEquals(meta, ConvertedLegacyBlock.legacyMeta(
                    ConvertedLegacyBlock.withLegacyMeta(block.defaultBlockState(), meta)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ConvertedLegacyBlock.withLegacyMeta(block.defaultBlockState(), -1));
        assertThrows(IllegalArgumentException.class,
                () -> ConvertedLegacyBlock.withLegacyMeta(block.defaultBlockState(), 16));
    }
}
