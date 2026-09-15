package dev.yinghuang.legacyforgebridge.protocol;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.FullMappings;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Reversible vanilla sound-identifier path recovered from the active ViaVersion pipeline. */
public final class ViaLegacySoundMappings {
    private static final String BACKWARDS_MAPPING_DATA = "com.viaversion.viabackwards.api.data.BackwardsMappingData";

    private ViaLegacySoundMappings() { }

    /**
     * Converts one modern built-in sound identifier down to the exact pre-1.9 name visible to
     * Forge 1.7.10, while retaining the inverse mapping path for a source callback replacement.
     */
    public static Optional<SoundPath> toLegacy(String modernIdentifier) {
        UserConnection connection = legacyConnection();
        if (connection == null) return Optional.empty();
        ProtocolInfo info = connection.getProtocolInfo();
        if (info == null || info.getPipeline() == null) return Optional.empty();
        List<Stage> stages = new ArrayList<>();
        for (Protocol<?, ?, ?, ?> protocol : info.getPipeline().pipes()) {
            MappingData data = protocol.getMappingData();
            if (data == null) continue;
            FullMappings sounds = data.getFullSoundMappings();
            if (sounds == null) continue;
            if (isBackwards(data)) {
                stages.add(new Stage(sounds::mappedIdentifier, sounds::identifier));
            } else {
                stages.add(new Stage(sounds::identifier, sounds::mappedIdentifier));
            }
        }
        return trace(modernIdentifier, stages);
    }

    static Optional<SoundPath> trace(String modernIdentifier, List<Stage> stages) {
        if (modernIdentifier == null || modernIdentifier.isBlank() || stages.isEmpty()) return Optional.empty();
        String current = namespaced(modernIdentifier);
        List<Stage> applied = new ArrayList<>();
        for (Stage stage : stages) {
            String next = stage.down().apply(current);
            if (next == null) return Optional.empty();
            current = next;
            applied.add(stage);
        }
        return Optional.of(new SoundPath(stripMinecraftNamespace(current), List.copyOf(applied)));
    }

    static Stage stageForTest(Function<String,String> down, Function<String,String> up) {
        return new Stage(down, up);
    }

    public static final class SoundPath {
        private final String legacyName;
        private final List<Stage> stages;

        private SoundPath(String legacyName, List<Stage> stages) {
            this.legacyName = legacyName;
            this.stages = stages;
        }

        public String legacyName() { return legacyName; }

        public Optional<String> toModern(String legacyReplacement) {
            if (legacyReplacement == null || legacyReplacement.isBlank()) return Optional.empty();
            String current = namespaced(legacyReplacement);
            List<Stage> reversed = new ArrayList<>(stages);
            Collections.reverse(reversed);
            for (Stage stage : reversed) {
                String next = stage.up().apply(current);
                if (next == null) return Optional.empty();
                current = next;
            }
            return Optional.of(current);
        }
    }

    static record Stage(Function<String,String> down, Function<String,String> up) { }

    private static UserConnection legacyConnection() {
        try {
            if (!Via.isLoaded()) return null;
            for (UserConnection connection : Via.getManager().getConnectionManager().getConnections()) {
                if (!connection.isClientSide() || connection.getChannel() == null || !connection.getChannel().isActive()) continue;
                ProtocolInfo info = connection.getProtocolInfo();
                ProtocolVersion serverVersion = info != null ? info.serverProtocolVersion() : null;
                if (serverVersion != null && LegacyProtocolVersions.isMinecraft1710(serverVersion.getVersion())) return connection;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Via can still be initializing while the client world is changing. Fail closed.
        }
        return null;
    }

    private static boolean isBackwards(MappingData data) {
        for (Class<?> type = data.getClass(); type != null; type = type.getSuperclass()) {
            if (BACKWARDS_MAPPING_DATA.equals(type.getName())) return true;
        }
        return false;
    }

    private static String namespaced(String identifier) {
        return identifier.indexOf(':') < 0 ? "minecraft:" + identifier : identifier;
    }

    private static String stripMinecraftNamespace(String identifier) {
        return identifier.startsWith("minecraft:") ? identifier.substring("minecraft:".length()) : identifier;
    }
}
