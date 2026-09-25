package dev.yinghuang.legacyforgebridge.compat;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.minecraft.item.DataItem;
import com.viaversion.viaversion.api.minecraft.item.Item;
import net.raphimc.vialegacy.api.remapper.LegacyItemRewriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the upstream implementation, not a reimplementation of the unknown-item fallback. */
class LegacyViaCarrierPolicyTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class Legacy1710Policy extends LegacyItemRewriter {
        Legacy1710Policy() {
            super(null, "lfb-1710-regression", null, null);
            // Exact non-existent vanilla ranges in ViaFabricPlus 4.4.15's 1.7.10 rewriter.
            // Forge is free to register mod items in those numeric slots.
            addNonExistentItemRange(165, 169);
            addNonExistentItemRange(179, 192);
        }
        @Override public String nbtTagName() { return "lfb-1710-regression"; }
    }

    @Test
    void restoreOnlyAfterTheFinalLegacyFallbackPreservesForgeSlotsAndMetadata() {
        Legacy1710Policy policy = new Legacy1710Policy();
        int previousStoneFallbacks = 0;
        int recovered = 0;
        for (int id = 165; id <= 233; id++) {
            for (int metadata = 0; metadata < 16; metadata++) {
                Item early = new DataItem(id, (byte) 1, (short) metadata, null);
                policy.handleItemToServer(null, early);
                if (early.identifier() == 1) previousStoneFallbacks++;

                String modern = "fixture:item_" + id;
                CompoundTag source = new CompoundTag();
                source.putString("source_note", "preserve me");
                CompoundTag marker = LegacyItemCarrierState.capture(source, modern, id, metadata);
                source.put(LegacyItemCarrierState.KEY, marker);
                Item carrier = new DataItem(339, (byte) 1, (short) 0, source);
                policy.handleItemToServer(null, carrier);
                assertEquals(339, carrier.identifier());
                CompoundTag saved = carrier.tag().getCompoundTag(LegacyItemCarrierState.KEY);
                assertTrue(LegacyItemCarrierState.matches(saved, modern, id));
                carrier.setIdentifier(LegacyItemCarrierState.legacyId(saved));
                carrier.setData((short) LegacyItemCarrierState.metadata(saved));
                LegacyItemCarrierState.restoreSourceTag(carrier.tag(), saved);
                assertEquals(id, carrier.identifier());
                assertEquals(metadata, (int) carrier.data());
                assertEquals("preserve me", carrier.tag().getString("source_note"));
                assertNull(carrier.tag().get(LegacyItemCarrierState.KEY));
                recovered++;
            }
        }
        assertEquals(304, previousStoneFallbacks);
        assertEquals(1104, recovered);
    }
}
