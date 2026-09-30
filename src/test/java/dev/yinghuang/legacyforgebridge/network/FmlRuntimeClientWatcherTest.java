package dev.yinghuang.legacyforgebridge.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FmlRuntimeClientWatcherTest {
    @Test
    void projectileWatcher16ByteIsAcceptedIndependentOfJavaBaseFamily(){
        assertTrue(FmlRuntimeClient.isLegacyProjectileBaseWatcher(
                new FmlRuntimeCodec.LegacyDataWatcherEntry(0,16,(byte)1)));
        assertTrue(FmlRuntimeClient.isLegacyProjectileBaseWatcher(
                new FmlRuntimeCodec.LegacyDataWatcherEntry(0,0,(byte)0)));
        assertTrue(FmlRuntimeClient.isLegacyProjectileBaseWatcher(
                new FmlRuntimeCodec.LegacyDataWatcherEntry(1,1,(short)300)));
        assertFalse(FmlRuntimeClient.isLegacyProjectileBaseWatcher(
                new FmlRuntimeCodec.LegacyDataWatcherEntry(1,16,(short)1)));
        assertFalse(FmlRuntimeClient.isLegacyProjectileBaseWatcher(
                new FmlRuntimeCodec.LegacyDataWatcherEntry(0,17,(byte)1)));
    }
}
