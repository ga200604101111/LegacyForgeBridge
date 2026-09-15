package dev.yinghuang.legacyforgebridge.protocol;

/**
 * Transport-version backend used by LegacyForgeBridge.
 *
 * <p>The Forge/FML bridge depends on this abstraction instead of directly depending on a
 * specific protocol translator implementation.</p>
 */
public interface ProtocolBackend {
    String id();

    boolean initialized();

    int currentProtocolId();

    String currentProtocolName();

    boolean isMinecraft1710Target();

    /**
     * Select the 1.7.6-1.7.10 protocol family for the next legacy connection and restore the
     * previous ViaFabricPlus target after disconnect.
     */
    void selectMinecraft1710ForNextConnection();
}
