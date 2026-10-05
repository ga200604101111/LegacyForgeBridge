package dev.yinghuang.legacyforgebridge.rev255;
/** Per-object identity correlation; not vector/time matching and not a second packet queue. */
public interface S12PacketAccess {
    TraceStore.Stamp rev255$getStamp();
    void rev255$setStamp(TraceStore.Stamp stamp);
}
