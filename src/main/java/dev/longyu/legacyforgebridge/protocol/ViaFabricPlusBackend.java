package dev.longyu.legacyforgebridge.protocol;

import com.viaversion.viafabricplus.api.ViaFabricPlusBase;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.session.LegacySessionController;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * LegacyForgeBridge protocol backend backed by the public ViaFabricPlus API.
 *
 * <p>ViaFabricPlus remains an external Fabric mod. No ViaFabricPlus classes are shaded or
 * embedded into the LegacyForgeBridge JAR.</p>
 */
public final class ViaFabricPlusBackend implements ProtocolBackend {
    public static final ViaFabricPlusBackend INSTANCE = new ViaFabricPlusBackend();

    private volatile ViaFabricPlusBase platform;
    private volatile int currentProtocolId = -1;
    private volatile String currentProtocolName = "unknown";

    private ViaFabricPlusBackend() {
    }

    public synchronized void initialize(ViaFabricPlusBase platform) {
        Objects.requireNonNull(platform, "platform");
        if (this.platform != null) {
            if (this.platform != platform) {
                throw new IllegalStateException("ViaFabricPlus backend was initialized twice with different platform instances");
            }
            return;
        }

        this.platform = platform;
        updateTarget(platform.getTargetVersion());
        platform.registerOnChangeProtocolVersionCallback((oldVersion, newVersion) -> updateTarget(newVersion));

        LegacyForgeBridge.LOGGER.info(
                "ViaFabricPlus backend ready: runtime={}, API={}, target={} ({})",
                platform.getVersion(),
                platform.apiVersion(),
                currentProtocolName,
                currentProtocolId
        );
    }

    private void updateTarget(ProtocolVersion version) {
        if (version == null) {
            currentProtocolId = -1;
            currentProtocolName = "unknown";
        } else {
            currentProtocolId = version.getVersion();
            currentProtocolName = version.getName();
        }
        LegacySessionController.onTargetProtocolChanged(currentProtocolId, currentProtocolName);
    }

    @Override
    public String id() {
        return "viafabricplus";
    }

    @Override
    public boolean initialized() {
        return platform != null;
    }

    @Override
    public int currentProtocolId() {
        return currentProtocolId;
    }

    @Override
    public String currentProtocolName() {
        return currentProtocolName;
    }

    @Override
    public boolean isMinecraft1710Target() {
        return LegacyProtocolVersions.isMinecraft1710(currentProtocolId);
    }

    @Override
    public void selectMinecraft1710ForNextConnection() {
        requirePlatform().setTargetVersion(ProtocolVersion.v1_7_6, true);
        updateTarget(ProtocolVersion.v1_7_6);
    }

    /**
     * Returns whether an item exists in the 1.7.6-1.7.10 protocol family according to
     * ViaFabricPlus. This will be used by the v0.3 item boundary/packet guard.
     */
    public boolean itemExistsInMinecraft1710(Item item) {
        return requirePlatform().itemExists(item, ProtocolVersion.v1_7_6);
    }

    /**
     * Checks an ItemStack against the currently active ViaFabricPlus connection, including
     * version-sensitive data components.
     */
    public boolean itemStackExistsInCurrentConnection(ItemStack stack) {
        return requirePlatform().itemExistsInConnection(stack);
    }

    private ViaFabricPlusBase requirePlatform() {
        ViaFabricPlusBase value = platform;
        if (value == null) {
            throw new IllegalStateException("ViaFabricPlus backend has not been initialized yet");
        }
        return value;
    }
}
