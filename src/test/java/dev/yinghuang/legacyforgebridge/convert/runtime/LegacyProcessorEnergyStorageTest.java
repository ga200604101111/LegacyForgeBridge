package dev.yinghuang.legacyforgebridge.convert.runtime;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyProcessorEnergyStorageTest {
    @Test
    void negativeLegacyOvershootStillAcceptsCapacityDeltaAndTransactionsRollback() {
        AtomicLong committed = new AtomicLong(Long.MIN_VALUE);
        LegacyProcessorEnergyStorage storage = new LegacyProcessorEnergyStorage(500, committed::set);
        storage.setSourceAmount(-100);

        assertEquals(0, storage.getAmount());
        assertEquals(500, storage.getCapacity());
        assertTrue(storage.supportsInsertion());
        assertFalse(storage.supportsExtraction());

        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(600, storage.insert(1000, transaction));
            assertEquals(500, storage.getAmount());
        }
        assertEquals(0, storage.getAmount());
        assertEquals(Long.MIN_VALUE, committed.get());

        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(600, storage.insert(1000, transaction));
            transaction.commit();
        }
        assertEquals(500, storage.getAmount());
        assertEquals(500, committed.get());
    }
}
