package dev.yinghuang.legacyforgebridge.runtime;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Loads only installed Fabric providers, never classes named by a packet or by a staged candidate. */
public final class LegacyRuntimeProviders {
    public static final String ENTRYPOINT = "legacyforgebridge-runtime";
    private LegacyRuntimeProviders() { }

    public static void load(LegacyRuntimeBindings<LegacyRuntimeProvider.EntityAdapter, LegacyRuntimeProvider.GuiAdapter> bindings) {
        Map<String, List<EntrypointContainer<LegacyRuntimeProvider>>> groups = new TreeMap<>();
        for (var entry : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, LegacyRuntimeProvider.class)) {
            groups.computeIfAbsent(entry.getProvider().getMetadata().getId(), ignored -> new ArrayList<>()).add(entry);
        }
        groups.forEach((fabricId, entries) -> {
            try {
                var path = entries.getFirst().getProvider().findPath(LegacyRuntimeManifest.PATH)
                        .orElseThrow(() -> new IllegalArgumentException("Missing embedded conversion manifest"));
                byte[] bytes;
                try (var stream = Files.newInputStream(path)) {
                    bytes = stream.readNBytes(LegacyRuntimeManifest.MAX_BYTES + 1);
                }
                if (bytes.length > LegacyRuntimeManifest.MAX_BYTES) throw new IOException("Conversion manifest exceeds size limit");
                var manifest = LegacyRuntimeManifest.parse(new String(bytes, StandardCharsets.UTF_8), fabricId);
                var registration = new LegacyRuntimeProvider.Registration();
                for (var entry : entries) entry.getEntrypoint().register(registration);
                bindings.install(manifest, registration.entities, registration.guis);
                LegacyForgeBridge.LOGGER.info("Installed legacy runtime adapters for {}: entities={}, guis={}",
                        fabricId, manifest.entities().size(), manifest.guis().size());
            } catch (Exception | LinkageError exception) {
                LegacyForgeBridge.LOGGER.error("LFB-RUNTIME-PROVIDER-REJECTED: {} (no bindings installed)", fabricId, exception);
            }
        });
    }
}
