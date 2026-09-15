package dev.yinghuang.legacyforgebridge.convert.runtime;

import team.reborn.energy.api.base.SimpleEnergyStorage;

import java.util.function.LongConsumer;

/** Transaction-aware modern storage backed by the source legacy processor energy integer. */
public final class LegacyProcessorEnergyStorage extends SimpleEnergyStorage {
    private final LongConsumer finalCommit;

    public LegacyProcessorEnergyStorage(int capacity, LongConsumer finalCommit) {
        super(capacity, Integer.MAX_VALUE, 0);
        if (capacity <= 0) throw new IllegalArgumentException("Processor energy capacity must be positive");
        this.finalCommit = finalCommit;
    }

    /** Internal legacy ticking may intentionally drive the source field below zero. */
    public void setSourceAmount(int sourceAmount) {
        this.amount = sourceAmount;
    }

    @Override
    public long getAmount() {
        return Math.max(0L, Math.min(amount, capacity));
    }

    @Override
    protected void onFinalCommit() {
        finalCommit.accept(amount);
    }
}
