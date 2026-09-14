package dev.yinghuang.legacyforgebridge.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Manifest-authorized factory bindings, atomically installed per physical Fabric mod. */
public final class LegacyRuntimeBindings<E, G> {
    public record Binding<T>(String fabricId, String target, T adapter) { }
    private final Map<LegacyRuntimeManifest.Identity, Binding<E>> entities = new HashMap<>();
    private final Map<LegacyRuntimeManifest.Identity, Binding<G>> guis = new HashMap<>();
    private final Set<String> owners = new HashSet<>();

    public synchronized void install(LegacyRuntimeManifest manifest, Map<String, E> entityAdapters,
                                     Map<String, G> guiAdapters) {
        // Validate both categories and ownership before changing any shared state.
        for (String owner : manifest.logicalMods()) {
            if (owners.contains(owner)) throw new IllegalArgumentException("Logical mod already owns runtime mappings: " + owner);
        }
        Map<LegacyRuntimeManifest.Identity, Binding<E>> newEntities = bind(manifest.fabricId(), manifest.entities(), entityAdapters);
        Map<LegacyRuntimeManifest.Identity, Binding<G>> newGuis = bind(manifest.fabricId(), manifest.guis(), guiAdapters);
        entities.putAll(newEntities);
        guis.putAll(newGuis);
        owners.addAll(manifest.logicalMods());
    }

    private static <T> Map<LegacyRuntimeManifest.Identity, Binding<T>> bind(String owner,
            Map<LegacyRuntimeManifest.Identity, String> declared, Map<String, T> adapters) {
        if (!new HashSet<>(declared.values()).equals(adapters.keySet())) {
            throw new IllegalArgumentException("Adapter factories must exactly cover their declared manifest identities");
        }
        Map<LegacyRuntimeManifest.Identity, Binding<T>> result = new HashMap<>();
        declared.forEach((identity, target) -> result.put(identity,
                new Binding<>(owner, target, Objects.requireNonNull(adapters.get(target), "Missing adapter"))));
        return result;
    }
    public synchronized Optional<Binding<E>> entity(String modId, int legacyTypeId) {
        return Optional.ofNullable(entities.get(new LegacyRuntimeManifest.Identity(modId, legacyTypeId)));
    }
    public synchronized Optional<Binding<G>> gui(String modId, int legacyGuiId) {
        return Optional.ofNullable(guis.get(new LegacyRuntimeManifest.Identity(modId, legacyGuiId)));
    }
}
